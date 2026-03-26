# Default task
.PHONY: all
all: clean format lint test knit

# Show available targets
.PHONY: help
help:
	@echo "Available targets:"
	@echo "  make all                 - Run clean, format, lint, test, and knit"
	@echo "  make format              - Format code with ktlintFormat"
	@echo "  make lint                - Run detekt and ktlintCheck"
	@echo "  make test                - Run unit tests (JVM) and check ABI"
	@echo "  make it                  - Run integration tests"
	@echo "  make ios-test            - Run iOS simulator tests"
	@echo "  make knit                - Generate and verify doc code snippets"
	@echo "  make clean               - Clean the project"
	@echo "  make publish             - Publish to Maven Local"
	@echo "  make build-compose-example - Build Compose demo app (runs publish first)"
	@echo "  make pom                 - Generate POM files for JVM publications"
	@echo "  make apidocs             - Generate API docs with Dokka"

# Format the code
.PHONY: format
format:
	./gradlew ktlintFormat

# Lint the code
.PHONY: lint
lint:
	./gradlew detekt ktlintCheck

# Publish to Maven Local (clears previous koog artifacts to ensure a clean publish)
.PHONY: publish
publish:
	rm -rf ~/.m2/repository/ai/koog
	./gradlew publishToMavenLocal

# Build the Compose demo app (publishes to Maven Local first)
.PHONY: build-compose-example
build-compose-example: publish
	cd examples/demo-compose-app && \
	./gradlew assemble

# Run unit tests
.PHONY: test
test:
	./gradlew checkLegacyAbi jvmTest :integration-tests:jvmTestClasses --exclude-task :integration-tests:jvmTest

# Run integration tests
.PHONY: it
it:
	./gradlew :integration-tests:jvmIntegrationTest

# Run iOS simulator tests
.PHONY: ios-test
ios-test:
	./gradlew iosSimulatorArm64Test --exclude-task :integration-tests:jvmTest

# Clean the project
.PHONY: clean
clean:
	./gradlew clean

# Generate POM files for JVM publications
.PHONY: pom
pom:
	./gradlew generatePomFileForJvmPublication

# Generate and verify documentation code snippets
.PHONY: knit
knit:
	@echo "🧶 Starting knit generation..."
	@FILES_BEFORE_KNIT=$$(find docs/src/ -name "*.kt" 2>/dev/null | wc -l || echo "0"); \
	echo "Files before knit: $$FILES_BEFORE_KNIT"; \
	./gradlew :docs:knit; \
	FILES_AFTER_KNIT=$$(find docs/src/ -name "*.kt" 2>/dev/null | wc -l || echo "0"); \
	echo "Files after knit: $$FILES_AFTER_KNIT"; \
	KNIT_GENERATED_FILES=$$((FILES_AFTER_KNIT - FILES_BEFORE_KNIT)); \
	echo "Knit generated $$KNIT_GENERATED_FILES files"; \
	echo "Starting assemble..."; \
	./gradlew :docs:assemble

# Generate API documentation with Dokka
.PHONY: apidocs
apidocs:
	./gradlew dokkaGenerate -Pdokka.jvmOnly=true
