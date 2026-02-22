This is a Kotlin Multiplatform project targeting Web.

* [/koogelis](./koogelis/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./koogelis/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/webApp](./webApp) contains web React application. It uses the Kotlin/JS library produced
  by the [koogelis](./koogelis) module.

* [/demo](./demo) contains Node script with examples of agent configuration with custom logger, custom persistence provider, MCP server and optional AbortSignal for cancellation.

### Prerequisites
In order to run your agents with example code you need either a local LLM (llama3.2:1b) or an API key for Gemini models.

### Build and Run Web Application in your browser

To build and run the development version of the web app, use the run configuration from the run widget
in your IDE's toolbar or run it directly from the terminal:
1. Install [Node.js](https://nodejs.org/en/download) (which includes `npm`)
2. Build Kotlin/JS shared code:
    - on macOS/Linux
      ```shell
      ./gradlew :koogelis:jsNodeDevelopmentLibraryDistribution
      ```
    - on Windows
      ```shell
      .\gradlew.bat :koogelis:jsNodeDevelopmentLibraryDistribution
      ```
3. Build and run the web application
   ```shell
   npm install
   npm run start
   ```
4. Open the link from the terminal in your browser and press the button. Disclaimer: LLM-generated jokes can be not funny.

### Build and Run Node script with Koog-JS as an external library

To build and run the development version of the web app, use the run configuration from the run widget
in your IDE's toolbar or run it directly from the terminal:
1. Install [Node.js](https://nodejs.org/en/download) (which includes `npm`)
2. Build Kotlin/JS koogelis code (also there is jsNodeProductionLibraryDistribution which results in smaller size due to DCE):
    - on macOS/Linux
      ```shell
      ./gradlew :koogelis:jsNodeDevelopmentLibraryDistribution
      ```
    - on Windows
      ```shell
      .\gradlew.bat :koogelis:jsNodeDevelopmentLibraryDistribution
      ```
3. Build the npm package
   ```shell
   cd koogelis/build/dist/js/developmentLibrary
   npm pack
   ```
4. Copy the resulting tgz-file (koogelis-0.0.1.tgz) to the demo folder
5. Install the package
   ```shell
   cd demo
   npm install koogelis-0.0.1.tgz
   ```
6. Modify the `invokeAgent.mjs` script for your configuration and use cases
7. Run the demo script inside the demo directory
   ```shell
   node invokeAgent.mjs "Tell me the name of Lithuanian potato casserole."
   ```
