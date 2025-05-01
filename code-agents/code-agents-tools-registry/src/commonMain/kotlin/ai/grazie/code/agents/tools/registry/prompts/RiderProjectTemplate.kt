package ai.grazie.code.agents.tools.registry.prompts

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode
import kotlinx.serialization.Transient

@Serializable
data class Template (
    val id: String,
    val name: String,
    val description: String,
    @Transient
    val tree: String? = null,
    @Transient
    val files: Map<String, String>? = null,
    
)

@Serializable
data class RiderProjectTemplate (
    val description: String,
    @EncodeDefault(Mode.ALWAYS)
    val templates: List<Template> = ProjectTemplates
)

val createStructureToolFormatPrompt = """
    Call "create_structure" tool to provide solution design and project templates for implementation. The arguments of the tool are:
    * "solution_name": Suggested name for the .NET solution.
    * "projects": List of selected projects to include into solution if format. Each project should be described in format TEMPLATE_ID:PROJECT_NAME:PROJECT_DESCRIPTION where
        * "TEMPLATE_ID" - template ID from list of available templates.
        * "PROJECT_NAME" - suggested project name. Commas (,) and colon (:) are not allowed in name.
        * "PROJECT_DESCRIPTION" - text explanation why this project template should be uses and what are additional solution-specific features should be added. Write a clear description with examples if necessary.
    * "question": A technical clarification single-choice question focusing on solution components, architecture, or specific technologies relevant to the solution. Ensure it encourages thoughtful input on the selection and implementation of templates.
    * "options": Provide 3 possible answers that are technically clear, concise (1-3 words each), and directly related to the question.
    """.trimIndent()

val createStructureSystemPrompt = """
    You are a helpful assistant for a .NET developer using the .NET SDK 8.0. 
    I will describe a solution I need template for, and your goal is to suggest suitable solution design and project templates for implementation.
    I will start with description, then I will add additional requirements and answer your questions. 
    Every time you need to align your suggestion with new information I provide.

    Request:
    For the input, you will be provided with following information:
    * Available templates: list of templates available for developer in format TEMPLATE_ID:TEMPLATE_NAME:TEMPLATE_DESCRIPTION
    * Previously proposed solution name: previously proposed solution name (if present) or this fild skipped
    * Previously proposed solution projects: previously proposed solution projects in format TEMPLATE_ID:PROJECT_NAME:PROJECT_DESCRIPTION (if present) or this fild skipped
    * Retry reason: the reason of asking to regenerate solution (if something went wrong with previous solution) or this fild skipped
    * Description: The task description provided by developer
    * Additions: The additional information provided by developer (from oldest to newest)
    * Questions: The list of clarification questions answered by developer (from oldest to newest)

    Response:
    * Suggest only the most relevant .NET project templates, explaining their purpose clearly.
    * Always recommend creating a separate unit test project.
    * Prefer modern technologies over obsolete ones (e.g., WPF over Windows Forms).
    * Use templates only from provided templates list
    * If present, use previously proposed solution name and projects as reference and modify them according to developer description, additions and question answers, otherwise base your answer on description
    * Always provide response by CALLING A TOOL:
    $createStructureToolFormatPrompt
""".trimIndent()


val ProjectTemplates = listOf<Template>(
    Template(
        id = "blazor_server",
        name = "ASP.NET Core Blazor Server",
        description = "Server-side Blazor interactive UI.",
        tree = """
            /ASP.NET Core Blazor Server
            ├── Pages
            │   ├── _Host.cshtml
            │   ├── Counter.razor
            │   └── Index.razor
            ├── Shared
            │   └── MainLayout.razor
            ├── Program.cs
            ├── BlazorServer.csproj
            └── appsettings.json
        """.trimIndent(),
        files = mapOf(
            "Pages/_Host.cshtml" to """
                @page "/"
                @namespace BlazorServer.Pages
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Blazor Server App</title>
                </head>
                <body>
                    <app>
                        <component type="typeof(App)" render-mode="ServerPrerendered" />
                    </app>
                </body>
                </html>
            """.trimIndent(),
            "Pages/Counter.razor" to """
                @page "/counter"
                <h3>Counter</h3>
                <p>Current count: @currentCount</p>
                <button class="btn btn-primary" @onclick="IncrementCount">Click me</button>

                @code {
                    private int currentCount = 0;
                    private void IncrementCount() => currentCount++;
                }
            """.trimIndent(),
            "Pages/Index.razor" to """
                @page "/"
                <h3>Welcome to Blazor Server!</h3>
                <p>This is the default Blazor Server template.</p>
            """.trimIndent(),
            "Shared/MainLayout.razor" to """
                @inherits LayoutComponentBase
                <div class="main">
                    @Body
                </div>
            """.trimIndent(),
            "Program.cs" to """
                var builder = WebApplication.CreateBuilder(args);
                builder.Services.AddRazorPages();
                var app = builder.Build();

                app.MapRazorPages();
                app.Run();
            """.trimIndent(),
            "BlazorServer.csproj" to """
                <Project Sdk="Microsoft.NET.Sdk.Web">
                  <PropertyGroup>
                    <TargetFramework>net7.0</TargetFramework>
                    <ImplicitUsings>enable</ImplicitUsings>
                    <Nullable>enable</Nullable>
                  </PropertyGroup>
                </Project>
            """.trimIndent(),
            "appsettings.json" to """
                {
                  "Logging": {
                    "LogLevel": {
                      "Default": "Information",
                      "Microsoft.AspNetCore": "Warning"
                    }
                  }
                }
            """.trimIndent()
        )
    ),
    Template(
        id = "classlib",
        name = "Class Library",
        description = "Reusable .NET class libraries.",
        tree = """
            /Class Library
            ├── Class1.cs
            └── ClassLib.csproj
        """.trimIndent(),
        files = mapOf(
            "Class1.cs" to """
                namespace ClassLib
                {
                    public class Class1
                    {
                        public void DoSomething()
                        {
                            // Add your implementation here.
                        }
                    }
                }
            """.trimIndent(),
            "ClassLib.csproj" to """
                <Project Sdk="Microsoft.NET.Sdk">
                  <PropertyGroup>
                    <TargetFramework>net7.0</TargetFramework>
                    <ImplicitUsings>enable</ImplicitUsings>
                    <Nullable>enable</Nullable>
                  </PropertyGroup>
                </Project>
            """.trimIndent()
        )
    ),
    Template(
        id = "worker",
        name = "Worker Service",
        description = "Long-running background worker process.",
        tree = """
            /Worker Service
            ├── Worker.cs
            ├── Program.cs
            └── WorkerService.csproj
        """.trimIndent(),
        files = mapOf(
            "Worker.cs" to """
                using Microsoft.Extensions.Hosting;

                namespace WorkerService
                {
                    public class Worker : BackgroundService
                    {
                        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
                        {
                            while (!stoppingToken.IsCancellationRequested)
                            {
                                Console.WriteLine("Worker running at: {0}", DateTimeOffset.Now);
                                await Task.Delay(1000, stoppingToken);
                            }
                        }
                    }
                }
            """.trimIndent(),
            "Program.cs" to """
                using Microsoft.Extensions.DependencyInjection;
                using Microsoft.Extensions.Hosting;

                var builder = Host.CreateDefaultBuilder(args)
                    .ConfigureServices(services ->
                    {
                        services.AddHostedService<WorkerService.Worker>();
                    });

                builder.Build().Run();
            """.trimIndent(),
            "WorkerService.csproj" to """
                <Project Sdk="Microsoft.NET.Sdk.Worker">
                  <PropertyGroup>
                    <TargetFramework>net7.0</TargetFramework>
                    <ImplicitUsings>enable</ImplicitUsings>
                    <Nullable>enable</Nullable>
                  </PropertyGroup>
                </Project>
            """.trimIndent()
        )
    ),
    Template(
        id = "nunit",
        name = "NUnit Test Project",
        description = "Unit testing project template.",
        tree = """
            /NUnit Test Project
            ├── Tests
            │   └── SampleTest.cs
            ├── NUnitTestProject.csproj
            └── appsettings.json
        """.trimIndent(),
        files = mapOf(
            "Tests/SampleTest.cs" to """
                using NUnit.Framework;

                namespace NUnitTestProject.Tests
                {
                    [TestFixture]
                    public class SampleTest
                    {
                        [Test]
                        public void TestExample()
                        {
                            Assert.AreEqual(2, 1 + 1);
                        }
                    }
                }
            """.trimIndent(),
            "NUnitTestProject.csproj" to """
                <Project Sdk="Microsoft.NET.Sdk">
                  <PropertyGroup>
                    <TargetFramework>net7.0</TargetFramework>
                    <ImplicitUsings>enable</ImplicitUsings>
                    <Nullable>enable</Nullable>
                  </PropertyGroup>
                  <ItemGroup>
                    <PackageReference Include="NUnit" Version="3.13.3" />
                    <PackageReference Include="NUnit3TestAdapter" Version="4.3.1" />
                    <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.6.0" />
                  </ItemGroup>
                </Project>
            """.trimIndent(),
            "appsettings.json" to """
                {
                  "Logging": {
                    "LogLevel": {
                      "Default": "Information",
                      "Microsoft": "Warning"
                    }
                  }
                }
            """.trimIndent()
        )
    ),
    Template(
        id = "webapi",
        name = "ASP.NET Core Web API",
        description = "A RESTful Web API built using ASP.NET Core.",
        tree = """
            /ASP.NET Core Web API
            ├── Controllers
            │   └── WeatherForecastController.cs
            ├── Program.cs
            ├── WebApi.csproj
            └── appsettings.json
        """.trimIndent(),
        files = mapOf(
            "Controllers/WeatherForecastController.cs" to """
                using Microsoft.AspNetCore.Mvc;

                namespace WebApi.Controllers
                {
                    [ApiController]
                    [Route("api/[controller]")]
                    public class WeatherForecastController : ControllerBase
                    {
                        private static readonly string[] Summaries = new[]
                        {
                            "Freezing", "Bracing", "Chilly", "Cool", "Mild", "Warm", "Balmy", "Hot", "Sweltering", "Scorching"
                        };

                        private readonly ILogger<WeatherForecastController> _logger;

                        public WeatherForecastController(ILogger<WeatherForecastController> logger)
                        {
                            _logger = logger;
                        }

                        [HttpGet]
                        public IEnumerable<WeatherForecast> Get()
                        {
                            val rng = Random()
                            return (1..5).map { index ->
                                WeatherForecast(
                                    Date = DateTime.now().plusDays(index.toLong()),
                                    TemperatureC = rng.nextInt(-20, 55),
                                    Summary = Summaries[rng.nextInt(Summaries.size)]
                                )
                            }
                        }
                    }

                    data class WeatherForecast(
                        val Date: LocalDateTime,
                        val TemperatureC: Int,
                        val Summary: String
                    )
                }
            """.trimIndent(),
            "Program.cs" to """
                val builder = WebApplication.CreateBuilder(args)

                // Add services to the container.
                builder.Services.addControllers()

                val app = builder.build()

                // Configure the HTTP request pipeline.
                if (app.environment.isDevelopment) {
                    app.useDeveloperExceptionPage()
                }

                app.useHttpsRedirection()

                app.mapControllers()

                app.run()
            """.trimIndent(),
            "WebApi.csproj" to """
                <Project Sdk="Microsoft.NET.Sdk.Web">
                  <PropertyGroup>
                    <TargetFramework>net7.0</TargetFramework>
                    <ImplicitUsings>enable</ImplicitUsings>
                    <Nullable>enable</Nullable>
                  </PropertyGroup>
                </Project>
            """.trimIndent(),
            "appsettings.json" to """
                {
                  "Logging": {
                    "LogLevel": {
                      "Default": "Information",
                      "Microsoft.AspNetCore": "Warning"
                    }
                  }
                }
            """.trimIndent()
        )
    ),
    Template(
        id = "console",
        name = "Console Application",
        description = "A simple .NET console application.",
        tree = """
            /Console Application
            ├── Program.cs
            └── ConsoleApp.csproj
        """.trimIndent(),
        files = mapOf(
            "Program.cs" to """
                using System;

                namespace ConsoleApp
                {
                    public class Program
                    {
                        public static void main(String[] args)
                        {
                            Console.WriteLine("Hello, World!");
                        }
                    }
                }
            """.trimIndent(),
            "ConsoleApp.csproj" to """
                <Project Sdk="Microsoft.NET.Sdk">
                  <PropertyGroup>
                    <TargetFramework>net7.0</TargetFramework>
                    <ImplicitUsings>enable</ImplicitUsings>
                    <Nullable>enable</Nullable>
                  </PropertyGroup>
                </Project>
            """.trimIndent()
        )
    )
)


