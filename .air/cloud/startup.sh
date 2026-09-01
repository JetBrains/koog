#!/usr/bin/env bash
#
# Air cloud environment bootstrap for the Koog repository.
#
# Koog is a Kotlin Multiplatform library (JVM / JS / WasmJs / Android / iOS) built with
# Gradle 8.13. This script provisions everything the Gradle build needs on a bare Linux
# workspace:
#
#   * Temurin JDK 17            - the toolchain the build pins (jvmToolchain(17)); the image
#                                 ships JBR 25, which Gradle 8.13 cannot run on.
#   * Android command line SDK  - every multiplatform module applies com.android.library,
#                                 so Gradle fails at configuration time without an SDK.
#   * Gradle/JVM proxy settings - the workspace has no DNS; all egress goes through the
#                                 HTTP proxy in $HTTPS_PROXY, which the JVM does not pick
#                                 up from the environment on its own.
#   * Warm Gradle caches        - dependencies and compiled classes, so the first real task
#                                 starts from a warm build instead of a cold one.
#
# It runs on every boot in two modes, distinguished by $AIR_STARTUP_MODE:
#   warmup - the snapshot-baking run: do the expensive work and block on healthcheck.
#   task   - a real task run: only re-apply configuration, then return promptly.
#
# There is no server or UI to start: Koog is a library, so readiness means "the build and
# test toolchain actually works", which is what healthcheck asserts.

set -eu

# ---------------------------------------------------------------------------- settings --

JDK_FEATURE_VERSION=17
ANDROID_CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
ANDROID_PLATFORM="android-36"      # matches compileSdk in buildSrc/ai.kotlin.multiplatform
ANDROID_BUILD_TOOLS="36.0.0"

# Soft budget for the optional full-platform warm build. The outer setup system enforces
# its own startup timeout, so this stage is capped and best-effort: exceeding the budget
# leaves the caches partially warm, which is fine, and never fails startup.
ASSEMBLE_BUDGET_SECONDS=600

REPO_ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
LOCAL_DIR="$HOME/.local"
JDK_HOME="$LOCAL_DIR/jdks/temurin-$JDK_FEATURE_VERSION"
ANDROID_SDK="$LOCAL_DIR/android-sdk"
SDKMANAGER="$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager"
ENV_FILE="$HOME/.air-koog-env.sh"
ENV_MARKER="# >>> air koog env >>>"
GRADLE_USER_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"

if [ "${AIR_STARTUP_MODE:-}" = warmup ]; then WARMUP=1; else WARMUP=; fi

# --------------------------------------------------------------------------- utilities --

log()  { printf '[koog-setup] %s\n' "$*"; }
warn() { printf '[koog-setup][warn] %s\n' "$*" >&2; }
fail() { printf '[koog-setup][error] %s\n' "$*" >&2; exit 1; }

# Strips scheme and path off a proxy URL, leaving host:port.
proxy_host_port() {
    echo "${1:-}" | sed -e 's#^[a-zA-Z][a-zA-Z0-9+.-]*://##' -e 's#/.*$##' -e 's#^.*@##'
}

# Extracts a zip without relying on an `unzip` binary (the image has none, and there is
# no usable sudo to install one).
unzip_to() {
    zip_file="$1"
    dest_dir="$2"
    python3 - "$zip_file" "$dest_dir" <<'PY'
import os, stat, sys, zipfile
src, dest = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as zf:
    for info in zf.infolist():
        path = zf.extract(info, dest)
        mode = info.external_attr >> 16
        if mode:
            os.chmod(path, stat.S_IMODE(mode))
PY
}

# ------------------------------------------------------------------------------- proxy --

# Everything outbound goes through the workspace proxy and DNS is not available, so the
# JVM needs the proxy as system properties. Rewritten on every boot because the proxy
# address is assigned per environment and must not be baked into the snapshot stale.
configure_proxy() {
    hostport=$(proxy_host_port "${HTTPS_PROXY:-${https_proxy:-}}")
    PROXY_HOST=$(echo "$hostport" | cut -d: -f1)
    PROXY_PORT=$(echo "$hostport" | cut -d: -f2)

    if [ -z "$PROXY_HOST" ] || [ -z "$PROXY_PORT" ] || [ "$PROXY_HOST" = "$PROXY_PORT" ]; then
        warn "no usable HTTPS_PROXY found; assuming direct network access"
        PROXY_HOST=
        PROXY_PORT=
        GRADLE_PROXY_OPTS=
        return 0
    fi

    log "routing JVM/Gradle traffic through proxy $PROXY_HOST:$PROXY_PORT"
    GRADLE_PROXY_OPTS="-Dhttp.proxyHost=$PROXY_HOST -Dhttp.proxyPort=$PROXY_PORT"
    GRADLE_PROXY_OPTS="$GRADLE_PROXY_OPTS -Dhttps.proxyHost=$PROXY_HOST -Dhttps.proxyPort=$PROXY_PORT"
    GRADLE_PROXY_OPTS="$GRADLE_PROXY_OPTS -Dhttp.nonProxyHosts=localhost|127.0.0.1"

    mkdir -p "$GRADLE_USER_DIR"
    props="$GRADLE_USER_DIR/gradle.properties"
    # Keep any hand-written properties, replace only our managed block.
    if [ -f "$props" ]; then
        sed '/^# >>> air koog proxy >>>$/,/^# <<< air koog proxy <<<$/d' "$props" > "$props.tmp"
        mv "$props.tmp" "$props"
    fi
    cat >> "$props" <<EOF
# >>> air koog proxy >>>
# Managed by .air/cloud/startup.sh - regenerated on every boot.
systemProp.http.proxyHost=$PROXY_HOST
systemProp.http.proxyPort=$PROXY_PORT
systemProp.https.proxyHost=$PROXY_HOST
systemProp.https.proxyPort=$PROXY_PORT
systemProp.http.nonProxyHosts=localhost|127.0.0.1
systemProp.https.nonProxyHosts=localhost|127.0.0.1
# <<< air koog proxy <<<
EOF
}

# --------------------------------------------------------------------------------- JDK --

install_jdk() {
    if [ -x "$JDK_HOME/bin/javac" ]; then
        log "Temurin JDK $JDK_FEATURE_VERSION already installed at $JDK_HOME"
        return 0
    fi

    log "installing Temurin JDK $JDK_FEATURE_VERSION (image default is JBR 25, unsupported by Gradle 8.13)"
    api="https://api.adoptium.net/v3/assets/latest/$JDK_FEATURE_VERSION/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse"
    url=$(curl -fsSL "$api" | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["binary"]["package"]["link"])') \
        || fail "could not resolve a Temurin $JDK_FEATURE_VERSION download URL from $api"
    log "downloading $url"

    staging="$LOCAL_DIR/jdks/.staging-$$"
    rm -rf "$staging"
    mkdir -p "$staging"
    curl -fsSL "$url" -o "$staging/jdk.tar.gz" || fail "JDK download failed"
    tar -xzf "$staging/jdk.tar.gz" -C "$staging" || fail "JDK archive could not be extracted"
    extracted=$(find "$staging" -maxdepth 1 -mindepth 1 -type d | head -1)
    [ -n "$extracted" ] || fail "JDK archive did not contain a directory"
    rm -rf "$JDK_HOME"
    mv "$extracted" "$JDK_HOME"
    rm -rf "$staging"

    [ -x "$JDK_HOME/bin/javac" ] || fail "JDK install did not produce $JDK_HOME/bin/javac"
    log "installed $("$JDK_HOME/bin/java" -version 2>&1 | head -1)"
}

# ----------------------------------------------------------------------- Android SDK ----

install_android_sdk() {
    if [ -x "$SDKMANAGER" ] && [ -d "$ANDROID_SDK/platforms/$ANDROID_PLATFORM" ]; then
        log "Android SDK with $ANDROID_PLATFORM already installed at $ANDROID_SDK"
        return 0
    fi

    if [ ! -x "$SDKMANAGER" ]; then
        log "installing Android command line tools"
        staging="$LOCAL_DIR/.android-staging-$$"
        rm -rf "$staging"
        mkdir -p "$staging" "$ANDROID_SDK/cmdline-tools"
        curl -fsSL "$ANDROID_CMDLINE_TOOLS_URL" -o "$staging/cmdline-tools.zip" \
            || fail "Android command line tools download failed"
        unzip_to "$staging/cmdline-tools.zip" "$staging" \
            || fail "Android command line tools archive could not be extracted"
        rm -rf "$ANDROID_SDK/cmdline-tools/latest"
        mv "$staging/cmdline-tools" "$ANDROID_SDK/cmdline-tools/latest"
        chmod +x "$ANDROID_SDK/cmdline-tools/latest/bin/"* 2>/dev/null || true
        rm -rf "$staging"
        [ -x "$SDKMANAGER" ] || fail "sdkmanager not found at $SDKMANAGER after install"
    fi

    log "accepting Android SDK licenses"
    yes | JAVA_HOME="$JDK_HOME" "$SDKMANAGER" --licenses > /tmp/android-licenses.log 2>&1 || true
    tail -1 /tmp/android-licenses.log || true

    log "installing platform-tools, platforms;$ANDROID_PLATFORM, build-tools;$ANDROID_BUILD_TOOLS"
    JAVA_HOME="$JDK_HOME" "$SDKMANAGER" --install \
        "platform-tools" "platforms;$ANDROID_PLATFORM" "build-tools;$ANDROID_BUILD_TOOLS" \
        || fail "Android SDK package installation failed"

    [ -d "$ANDROID_SDK/platforms/$ANDROID_PLATFORM" ] \
        || fail "Android platform $ANDROID_PLATFORM missing after install"
}

# -------------------------------------------------------------------- shell environment --

# The launch runs this script as a child process, so plain exports die with it. The agent
# instead inherits a fresh login shell, so persist the variables in a file and source that
# file from the login shell profile and from .bashrc.
write_env_file() {
    log "writing shell environment to $ENV_FILE"
    cat > "$ENV_FILE" <<EOF
# Managed by .air/cloud/startup.sh - regenerated on every boot. Do not edit.
# Sourced from both the login profile and .bashrc; apply the exports only once.
[ -n "\${_AIR_KOOG_ENV_APPLIED:-}" ] && return 0
_AIR_KOOG_ENV_APPLIED=1
export JAVA_HOME="$JDK_HOME"
export ANDROID_HOME="$ANDROID_SDK"
export ANDROID_SDK_ROOT="$ANDROID_SDK"
export PATH="\$JAVA_HOME/bin:$ANDROID_SDK/cmdline-tools/latest/bin:$ANDROID_SDK/platform-tools:\$PATH"
EOF
    if [ -n "${GRADLE_PROXY_OPTS:-}" ]; then
        cat >> "$ENV_FILE" <<EOF
export GRADLE_OPTS="\${GRADLE_OPTS:-} $GRADLE_PROXY_OPTS"
EOF
    fi

    # A login shell reads only the FIRST profile file that exists, so hook into that one.
    profile=
    for candidate in "$HOME/.bash_profile" "$HOME/.bash_login" "$HOME/.profile"; do
        if [ -f "$candidate" ]; then profile="$candidate"; break; fi
    done
    if [ -z "$profile" ]; then
        profile="$HOME/.profile"
        touch "$profile"
    fi

    for rc in "$profile" "$HOME/.bashrc"; do
        [ -f "$rc" ] || touch "$rc"
        if ! grep -qF "$ENV_MARKER" "$rc"; then
            cat >> "$rc" <<EOF

$ENV_MARKER
[ -f "$ENV_FILE" ] && . "$ENV_FILE"
# <<< air koog env <<<
EOF
            log "hooked $ENV_FILE into $rc"
        fi
    done
}

# Apply the same variables to this script's own process, for the Gradle runs below.
apply_env() {
    JAVA_HOME="$JDK_HOME"; export JAVA_HOME
    ANDROID_HOME="$ANDROID_SDK"; export ANDROID_HOME
    ANDROID_SDK_ROOT="$ANDROID_SDK"; export ANDROID_SDK_ROOT
    PATH="$JAVA_HOME/bin:$ANDROID_SDK/cmdline-tools/latest/bin:$ANDROID_SDK/platform-tools:$PATH"; export PATH
    if [ -n "${GRADLE_PROXY_OPTS:-}" ]; then
        GRADLE_OPTS="${GRADLE_OPTS:-} $GRADLE_PROXY_OPTS"; export GRADLE_OPTS
    fi
}

# ------------------------------------------------------------------------- warm caches --

warm_gradle_caches() {
    cd "$REPO_ROOT"
    chmod +x gradlew

    # Compiles main + test sources for the JVM target across every module. This is the
    # everyday dev loop (./gradlew jvmTest) and pulls down the bulk of the dependencies.
    log "warming Gradle caches: ./gradlew jvmTestClasses"
    if ! ./gradlew jvmTestClasses; then
        fail "./gradlew jvmTestClasses failed - the environment cannot build the project"
    fi
    log "JVM sources compiled"

    # Best effort: the remaining targets (Android AAR, JS, WasmJs) are a lot slower and are
    # not needed by most tasks, so they run under a soft budget and never fail startup.
    log "warming remaining platforms: ./gradlew assemble (soft budget ${ASSEMBLE_BUDGET_SECONDS}s)"
    if timeout --signal=TERM --kill-after=60 "$ASSEMBLE_BUDGET_SECONDS" ./gradlew assemble; then
        log "all platforms assembled"
    else
        warn "assemble did not finish within ${ASSEMBLE_BUDGET_SECONDS}s; JS/Wasm/Android caches are only partially warm"
        # A killed Gradle can leave a daemon holding the build lock behind.
        ./gradlew --stop > /dev/null 2>&1 || true
    fi
}

# ------------------------------------------------------------------------- healthcheck --

# Asserts the environment really works the way a Koog task needs it to: the pinned JDK is
# in place, the Android SDK Gradle configuration depends on is present, and the build can
# actually compile and RUN tests. Returns non-zero on failure so startup exits non-zero.
healthcheck() {
    log "healthcheck: verifying toolchain"

    [ -x "$JDK_HOME/bin/java" ] || { warn "healthcheck: $JDK_HOME/bin/java missing"; return 1; }
    java_version=$("$JDK_HOME/bin/java" -version 2>&1 | head -1)
    case "$java_version" in
        *\"17.*) log "healthcheck: $java_version" ;;
        *) warn "healthcheck: expected a Java 17 runtime, got: $java_version"; return 1 ;;
    esac

    [ -d "$ANDROID_SDK/platforms/$ANDROID_PLATFORM" ] \
        || { warn "healthcheck: Android platform $ANDROID_PLATFORM missing"; return 1; }
    log "healthcheck: Android SDK present ($ANDROID_SDK)"

    cd "$REPO_ROOT"

    # Own the readiness waiting: a warm build stage may still be shutting down and holding
    # the Gradle build lock. Keep polling until it is free (the outer system owns timeouts).
    while [ -n "$(pgrep -f 'org.gradle.launcher.GradleMain|GradleWrapperMain' || true)" ]; do
        log "healthcheck: waiting for an in-flight Gradle build to finish"
        sleep 10
    done

    # The real assertion: Gradle configures every module (which needs JDK 17 + Android SDK
    # + working dependency downloads) and executes a JVM test suite end to end.
    log "healthcheck: running ./gradlew :utils:jvmTest :prompt:prompt-markdown:jvmTest"
    if ./gradlew :utils:jvmTest :prompt:prompt-markdown:jvmTest; then
        log "healthcheck: build and test smoke passed"
        return 0
    fi

    warn "healthcheck: the Gradle test smoke failed - see the output above"
    return 1
}

# -------------------------------------------------------------------------------- main --

log "starting (mode=${AIR_STARTUP_MODE:-task}, repo=$REPO_ROOT)"

configure_proxy
install_jdk
install_android_sdk
write_env_file
apply_env

if [ -n "$WARMUP" ]; then
    warm_gradle_caches
    healthcheck
    log "warmup complete"
else
    log "task mode: toolchain configured, skipping the warm build"
fi
