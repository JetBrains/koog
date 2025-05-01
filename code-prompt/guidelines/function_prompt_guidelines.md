Function calling has rapidly evolved into a mainstay of LLM-based agents. Yet, despite being widely implemented, it's surprisingly tricky to nail down prompt engineering for optimal function performance.

* How deeply should you detail your tool descriptions?
* Where is the best place to define instructions - in the function schema or in the overall system prompt?
* Do these practices vary significantly across OpenAI, Anthropic and Google models?

After analyzing official guides and community insights, here's the summary of best practices, pitfalls, and actionable recommendations you should consider when engineering prompts for LLM function calling.

🎓 0. The Intern Test: Can a Human Follow Your Prompt?

Before diving into function calling tips, start with the simplest sanity check recommended by both OpenAI and Anthropic:

Show your prompt (instructions, schemas, parameters) to a colleague unfamiliar with your task. Can they correctly follow the prompt? If they get confused - your LLM will too.

If your colleague asks clarifying questions, directly integrate those answers into your prompt. This quick human-friendliness check is the easiest and fastest way to level up your prompt quality. 🚀

🧩 1. Function Design & Description

The most important factor of successful function calling is providing extremely detailed descriptions. All major providers emphasize this point heavily:
🔹 Function names should follow the principle of least surprise - clear, intuitive, and using standard programming conventions. Use camel case or underscores, avoid spaces and dashes.
🔹 Function descriptions should be comprehensive and concrete:

* Clearly state what the function does, when it should (and shouldn't) be used
* Detail expected inputs, outputs, and limitations
* Aim for at least 3-4 sentences for each function (Anthropic's specific recommendation)
* Explicitly state what the function does NOT do or provide
* Document edge cases, especially common failure conditions

Compare these function descriptions:
🔴 Poor description
```
description = """Gets the stock price for a ticker."""

tool = {
    "name": "get_stock_price",
    "description": description,
    "parameters": {
        "type": "object",
        "properties": {
            "ticker": {
                "type": "string"
            }
        },
        "required": ["ticker"]
    }
}
```
🟢 Strong description
```
description = """Retrieves the current stock price for a given ticker symbol.
The ticker symbol must be a valid symbol for a publicly traded company on a major US stock exchange like NYSE or NASDAQ.
The tool will return the latest trade price in USD.
It should be used when the user asks about the current or most recent price of a specific stock.
It will not provide any other information about the stock or company."""

tool = {
    "name": "get_stock_price",
    "description": description,
    "parameters": {
        "type": "object",
        "properties": {
            "ticker": {
            "type": "string",
            "description": "The stock ticker symbol, e.g. AAPL for Apple Inc."
            }
        },
        "required": ["ticker"]
    }
}
```
💡 Note: While 3–4 sentences are recommended as the minimum, don't hesitate to provide much longer descriptions for intricate or highly contextual tools. Complex functions in real-world usage often have multi-paragraph descriptions -sometimes spanning 40-50 sentences - to clearly delineate behavior and edge cases.

🎯 2. Parameter Design Best Practices

Strongly typed parameters significantly reduce hallucinations:

* Use specific types (integer, boolean) rather than generic ones (number, string) when possible
* Implement enums for parameters with a finite set of valid values
* Include detailed format examples within parameter descriptions

For example:
🔴 Weak
```
"location": {
    "type": "string",
    "description": "The departure airport"
}
```
🟢 Strong
```
"location": {
    "type": "string",
    "description": "Use the 3-character IATA airport code to represent the airport. For example, SJC or SFO. Don't use the city name."
}
```
🌟 Use enum when applicable
```
"sort_order": {
    "type": "string",
    "enum": ["ascending", "descending"],
    "description": "The order to sort results (ascending = lowest to highest, descending = highest to lowest)"
}
```

🌐 3. Model-Specific Considerations

When implementing function calling with different LLM providers (OpenAI, Anthropic Claude, Google Gemini, or Microsoft Azure services), it's easy to assume the same prompting approach applies across the board. However, official guidelines and practical experience demonstrate differences worth keeping in mind.

🔸 OpenAI: Clearly separate "WHEN" from "HOW"

OpenAI consistently emphasizes clearly separating when the model should invoke a function, from how the function works:
System Messages are for WHEN:
Define explicit contexts and conditions for function calling exclusively in the system message.

Only call get_weather when users explicitly ask about weather conditions.
If they mention wanting to go outside or planning activities, ask if they want weather information first.

Function Descriptions are for HOW:
Schemas should just carefully specify how the function operates and what each argument expects - not when it's relevant.

Gets current weather data for a location.
Returns temperature in Celsius and weather conditions.

🗣️ OpenAI employee explicitly advise against mixing these up:

A general rule for instructing the model whether or not to call a function is to do that in system prompt (instructions for Assistant), whereas description in the function definition is better suited to tell the model how to call the function.

brianz-oai, OpenAI Staff

Additionally, OpenAI recommends:

* Keep the number of functions small for higher accuracy: Aim for fewer than 20 functions at any one time, though this is just a soft suggestion.
* Combine functions if they're always called together: If you always execute get_location() before mark_location(), merge them into a single get_and_mark_location() function.
* Reduce unnecessary parameters: If your backend already knows certain data (e.g., order IDs from context), don't force the model to provide this via function parameters. Handle it programmatically instead.
* Fine-tuning: For complex scenarios with large function sets, consider fine-tuning for function calling.

🔸 Anthropic Claude: Detailed Descriptions over Examples

Anthropic strongly agrees with the general principle from section 1 about making your function descriptions extremely detailed. However, Anthropic adds another interesting nuance - examples aren’t as important as you might think:

* Prioritize descriptions over examples: while you can include examples of how to use a tool in its description or in the accompanying prompt, this is less important than having a clear and comprehensive explanation of the tool’s purpose and parameters. Only add examples after you’ve fully fleshed out the description.

🔸 Azure OpenAI (Microsoft services): Explicit Guardrails Against Hallucinations

While Azure OpenAI uses OpenAI's models under the hood, official Microsoft Azure guides propose additional specific advice you won't find in OpenAI’s general docs:

* Explicitly remind the model: Only use the functions you have been provided with., added to your system prompt to reduce unrequested or hallucinated function calls.
* Explicit instruction to clarify ambiguous queries: Azure suggests directly instructing the model (via system prompt) to Don't make assumptions about what values to use with functions. Ask for clarification if a user request is ambiguous. when the user provides incomplete information.


🚨 4. Pitfalls to Avoid

1. ❌ Too many similar functions - Models perform better with a focused set of distinct functions
2. ❌ Ambiguous responsibilities - Unclear when one function should be used versus another
3. ❌ Schema-prompt redundancy - Repeating function schema details in the system prompt is unnecessary. Schemas are implicitly injected into the model prompt automatically - no need to repeat schema details explicitly.
4. ❌ Parameter hallucination - Failing to properly constrain parameters leads to invalid function calls
5. ❌ High temperature - Setting the model temperature close to zero universally improves function-calling accuracy by reducing output randomness.


🌟 5. Validation Strategy: Schema vs. System Prompt

An insightful discussion from OpenAI staff highlights two common approaches for validating function inputs (e.g., ensuring returns are limited to products actually bought by the user):

🔹 Approach 1: Schema-based Dynamic Enums

Here, validations are embedded directly within the function schema using a dynamically-generated enum constraint:
```
user_products = ["A", "B", "C"]

tool = {
    "name": "return_product",
    "description": "Process a product return for an item the user has purchased",
    "parameters": {
        "properties": {
            "product_id": {
                "type": "string",
                "enum": user_products,
                "description": "ID of the product to return"
            }
        }
    }
}
```
🔹 Approach 2: Contextual Validation via System Prompt

Here, contextual validation instructions in the system prompt specify explicitly allowed values while the schema remains generic without enum constraints:

user_products = ["A", "B", "C"]

system_message = f"""You are a return processing assistant.
The user has purchased the following products: {', '.join(user_products)}.
Only process returns for products they have actually purchased."""
```
tool = {
    "name": "return_product",
    "description": "Process a product return for an item the user has purchased",
    "parameters": {
        "properties": {
            "product_id": {
                "type": "string",
                "description": "ID of the product to return"
            }
        }
    }
}
```
According to OpenAI staff, empirical evidence suggests this Approach 2 with contextual validation significantly reduces hallucinations compared to schema-only constraints.

💡 Important considerations:

* This guidance mainly applies to OpenAI models. Other providers (like Anthropic or Google) may behave differently, so always test accordingly.
* A combined approach is typically best: use schema enums to strictly limit inputs, and also provide extra validation information within your system prompt. This dual strategy helps models make fewer errors and provide clearer responses to users.


🧠 6. Advanced Techniques

🔀 Dynamic Function Definitions

For optimal performance, consider dynamically generating function definitions based on context:

def get_available_functions(user_data):

    functions = []
    
    if user_data.get("is_logged_in"):
        functions.append({
            "name": "view_orders",
            "description": "View the user's past orders",
            "parameters": { /* ... */ }
        })
    
    if user_data.get("has_active_subscription"):
        functions.append({
            "name": "manage_subscription",
            "description": "View or modify the user's subscription settings",
            "parameters": { /* ... */ }
        })
    
    return functions

Dynamic function definitions can improve context-awareness but may add complexity. Consider the trade-offs. ⚠️

🛠️ Testing and Iteration

All providers recommend using their playgrounds or sandboxes to test and refine function calling implementations. Iterative testing is essential for identifying and addressing edge cases.

🙅‍♀️ When NOT to use functions?

Function calling works best for precise, structured API/tool use. Avoid for:

* Free-flow conversations with no clear functions required
* Pure creative or reasoning tasks without external APIs/tools


:outbox_tray: 7. Using XML for Function Call Observations

Anthropic and OpenAI recommend using clear delimiters, such as XML-like tags, to effectively structure LLM communication. This approach provides several key benefits:

* Clarity: Clearly separate prompt sections, ensuring consistent structure.
* Accuracy: Reduce errors resulting from misinterpretation of prompt sections by the LLM.
* Flexibility: Easily identify, add, modify, or remove prompt parts without rewriting large sections.
* Parseability: An LLM returning results wrapped in XML-like tags simplifies extracting data from responses via straightforward post-processing.

This XML strategy is especially useful for defining structured observations returned to the LLM after executing function calls. While function definitions typically leverage JSON schema, we have full flexibility to craft the observation format to best match the use-case.

📝 XML Observation Formatting

When responding to an LLM-triggered get_stock_price function request, observations can be returned structured with XML-like tags:
```
<stock>
    <ticker>AAPL</ticker>
    <currency>USD</currency>
    <price>192.40</price>
</stock>
```
This self-documenting attribute approach may improve the precision and robustness of your function-calling workflows.

💡 Pro Tip: You can enhance XML tags by using meaningful attributes. For example, defined attributes help precisely annotate and contextualize values without extra unstructured explanations:
<price currency="USD">192.40</price>


💬 8 . Final Thoughts

Prompt engineering for function calling looks easy - but is actually FULL of subtleties. Clear schemas, context-rich prompts, thoughtful parameter design, and iterative testing transform capability into reliability 💪.
Hope this helps and makes your next AI implementation smoother. Feel free to ping me if you have questions or insights!

Happy AI engineering! 🚀
