package ai.koog.gradle.tests

import org.jetbrains.kotlin.gradle.targets.js.KotlinJsPlatformTestRun
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.testing.karma.KotlinKarma

enum class Browser(internal val function: KotlinKarma.() -> Unit) {
    CHROME(KotlinKarma::useChrome),
    CHROME_HEADLESS(KotlinKarma::useChromeHeadless),
    FIREFOX(KotlinKarma::useFirefox),
    FIREFOX_HEADLESS(KotlinKarma::useFirefoxHeadless)
}

internal fun KotlinJsPlatformTestRun.configureBrowsers(
    localBrowser: Browser = Browser.CHROME_HEADLESS,
    ciBrowser: Browser = Browser.CHROME_HEADLESS
) {
    val project = this.target.project
    val logger = project.logger
    val task = executionTask.get()
    task.useKarma {
        val currentBrowser = if (project.hasProperty("ci")) ciBrowser else localBrowser

        task.doFirst {
            logger.lifecycle("For project ${project.name} task ${task.name} used browser ${currentBrowser.name}")
        }
        currentBrowser.function(this)
    }
}

/**
 * @param localBrowser browser for local testing. If you are working with gpu — change to `CHROME`
 * @param ciBrowser browser for testing on CI. Don't change this setting if you're not sure.
 */
fun KotlinJsTargetDsl.configureTests(
    localBrowser: Browser = Browser.CHROME_HEADLESS,
    ciBrowser: Browser = Browser.FIREFOX
) {
    for (testType in listOf(TestType.DEFAULT)) {
        testRuns.maybeCreate(testType.shortName).configureAllExecutions {
            filter { configureFilter(testType) }

            configureBrowsers(localBrowser, ciBrowser)
        }
    }
}
