<!-- Basic guidelines, should be refined -->

# Contributing Guidelines

One can contribute to the project by reporting issues or submitting changes via pull request.

## Reporting issues

Please use [GitHub issues](https://github.com/JetBrains/koog-docs/issues) for filing feature requests and bug reports.

Questions about usage and general inquiries are better suited for StackOverflow <!-- TODO: are we going to track it? --> or the <!-- TODO: channel name --> channel in KotlinLang Slack.

## Submitting changes

Submit pull requests [here](https://github.com/JetBrains/koog-docs/pulls).
However, please keep in mind that maintainers will have to support the resulting code of the project,
so do familiarize yourself with the following guidelines.

<!-- TODO: discuss git flow -->
<!-- TODO: align coding conventions with what the team is actually using -->

* All development (both new features and bug fixes) is performed in the `develop` branch.
    * The `main` branch contains the sources of the most recently released version.
    * Base your PRs against the `develop` branch.
    * The `develop` branch is pushed to the `main` branch during release.
* If you want to add code examples to a topic:
  *  Consider checking that the code is compiling with the latest Koog version.
  *  Inspect your code for problems and make sure it is well formatted.
  *  Comment on the existing issue if you want to work on it. Ensure that the issue not only describes a problem but also describes a solution that has received positive feedback. Propose a solution if none has been suggested.

## Koog framework changes

To propose changes in the Koog framework, please go to https://github.com/JetBrains/koog.