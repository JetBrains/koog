<!-- Basic guidelines, should be refined -->

# Contributing Guidelines

One can contribute to the project by reporting issues or submitting changes via pull request.

## Reporting issues

Please use [GitHub issues](https://github.com/JetBrains/koog/issues) for filing feature requests and bug reports.

Questions about usage and general inquiries are better suited for StackOverflow or the [#koog-agentic-framework](https://kotlinlang.slack.com/messages/koog-agentic-framework/) channel in KotlinLang Slack.

## Submitting changes

Submit pull requests [here](https://github.com/JetBrains/koog/pulls).
However, please keep in mind that maintainers will have to support the resulting code of the project,
so do familiarize yourself with the following guidelines.

<!-- TODO: discuss git flow -->
<!-- TODO: align coding conventions with what the team is actually using -->

* All development (both new features and bug fixes) is performed in the `develop` branch.
    * The `main` branch contains the sources of the most recently released version.
    * Base your PRs against the `develop` branch.
    * The `develop` branch is pushed to the `main` branch during release.
    * Documentation in markdown files can be updated directly in the `main` branch,
      unless the documentation is in the source code, and the patch changes line numbers.
* If you make any code changes:
    * Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/reference/coding-conventions.html).
        * Use 4 spaces for indentation.
        * Use imports with '*'.
    * [Build the project](#building) to make sure it all works and passes the tests.
* If you fix a bug:
    * Write the test that reproduces the bug.
    * Fixes without tests are accepted only in exceptional circumstances if it can be shown that writing the
      corresponding test is too hard or otherwise impractical.
    * Follow the style of writing tests that is used in this project:
      name test functions as `testXxx`. Don't use backticks in test names.
* Comment on the existing issue if you want to work on it. Ensure that the issue not only describes a problem but also describes a solution that has received positive feedback. Propose a solution if none has been suggested.

## Documentation

The documentation is published on https://docs.koog.ai/. To propose changes or improvements to the documentation, go to the https://github.com/JetBrains/koog-docs repository.

## Building

This library is built with Gradle.

* Run `./gradlew build` to build. It also runs all the tests.
* Run `./gradlew <module>:check` to test the module you are looking at to speed
  things up during development.

You can import this project into IDEA, but you have to delegate build actions
to Gradle (in Preferences -> Build, Execution, Deployment -> Build Tools -> Gradle -> Build and run).
