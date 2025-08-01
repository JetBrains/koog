package ai.koog.agents.example.banking.tools

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

const val bankingAssistantSystemPrompt = """
You are a banking assistant interacting with a user (userId=123).
Your goal is to understand the user's request and determine whether it can be fulfilled using the available tools.

If the task can be accomplished with the provided tools, proceed accordingly, 
at the end of the conversation respond with: "Task completed successfully." 
If the task cannot be performed with the tools available, respond with: "Can't perform the task." 
"""

@Serializable
data class Contact(val id: Int, val name: String, val surname: String? = null, val phoneNumber: String)

val contactList = listOf(
    Contact(100, "Alice", "Smith", "+1 415 555 1234"),
    Contact(101, "Bob", "Johnson", "+49 151 23456789"),
    Contact(102, "Charlie", "Williams", "+36 20 123 4567"),
    Contact(103, "Daniel", "Anderson", "+46 70 123 45 67"),
    Contact(104, "Daniel", "Garcia", "+34 612 345 678"),
)
val contactMap = contactList.associateBy { it.id }

@LLMDescription("Tools for money transfer operations")
class MoneyTransferTools : ToolSet {

    @Tool
    @LLMDescription(
        """
            Transfers a specified amount (in the sender's default currency) to a recipient contact by their id.
            Fails if the recipient is not a valid contact or if the user lacks sufficient funds.
            When sending money to a contact, if there are multiple contacts with the same name:
            1. First use getContacts to retrieve all contacts
            2. Filter the contacts to find those with matching names
            3. If multiple matches are found, use the chooseRecipient tool with the list of matching contacts to ask the user to pick the correct recipient
            4. Use the selected recipient's ID when sending money
        """
    )
    fun sendMoney(
        @LLMDescription("The unique identifier of the user initiating the transfer.")
        senderId: Int,

        @LLMDescription("The amount to be transferred, specified in the user's default currency.")
        amount: Int,

        @LLMDescription("The unique identifier of the recipient contact.")
        recipientId: Int,

        @LLMDescription("A brief description or reason for the transaction.")
        purpose: String
    ): String {
        val recipient = contactMap[recipientId] ?: return "Invalid recipient."
        println("=======")
        println(
            "Sending $amount EUR to ${recipient.name} ${recipient.surname} (${recipient.phoneNumber}) with the purpose: \"$purpose\"."
        )
        println("Please confirm the transaction by entering 'yes' or 'no'.")
        println("=======")
        val confirmation = readln()
        return if (confirmation.lowercase() in setOf(
                "yes",
                "y"
            )
        ) {
            "Money was sent."
        } else {
            "Money transfer wasn't confirmed by the user"
        }
    }

    @Tool
    @LLMDescription("Retrieves the list of contacts associated with the user identified by their ID.")
    fun getContacts(
        @LLMDescription("The unique identifier of the user whose contact list is being retrieved.")
        userId: Int
    ): String {
        return contactList.joinToString(separator = "\n") {
            "${it.id}: ${it.name} ${it.surname ?: ""} (${it.phoneNumber})"
        }
    }

    @Tool
    @LLMDescription("Retrieves the current balance of the user identified by their ID.")
    fun getBalance(
        @LLMDescription("The unique identifier of the user whose balance is being retrieved.")
        userId: Int
    ): String {
        return "Balance: 200.0 EUR"
    }

    @Tool
    @LLMDescription("Retrieves the default currency associated with the user identified by their ID.")
    fun getDefaultCurrency(
        @LLMDescription("The unique identifier of the user whose default currency is being retrieved.")
        userId: Int
    ): String {
        return "EUR"
    }

    @Tool
    @LLMDescription(
        "Retrieves the exchange rate between two currencies, specified by their 3-letter ISO codes (e.g., \"USD\", \"EUR\")."
    )
    fun getExchangeRate(
        @LLMDescription(
            "The 3-letter ISO currency code representing the base currency you want to convert from (e.g., \"USD\" for US Dollar, \"EUR\" for Euro)."
        )
        from: String,

        @LLMDescription(
            "The 3-letter ISO currency code representing the target currency you want to convert to (e.g., \"GBP\" for British Pound, \"JPY\" for Japanese Yen)."
        )
        to: String
    ): String {
        val rate = when (from to to) {
            "EUR" to "USD" -> "1.1"
            "EUR" to "GBP" -> "0.86"
            "GBP" to "EUR" -> "1.16"
            "USD" to "EUR" -> "0.9"
            else -> "No information about exchange rate available."
        }
        return rate
    }

    @Tool
    @LLMDescription("Asks the user to pick the correct recipient when a contact name is confusing or can't be found.")
    fun chooseRecipient(
        @LLMDescription("The name of the contact that couldn't be found or is ambiguous.")
        confusingRecipientName: String
    ): String {
        println("=======")

        // Find contacts that might match the confusing name
        val possibleMatches = contactList.filter {
            it.name.contains(confusingRecipientName, ignoreCase = true) ||
                (it.surname?.contains(confusingRecipientName, ignoreCase = true) ?: false)
        }
        if (possibleMatches.isEmpty()) {
            println(
                "No contact named $confusingRecipientName was found. Here are all available contacts—please choose one:"
            )
        } else {
            println(
                "I found several contacts named $confusingRecipientName. Please choose a recipient from the list below:"
            )
        }

        val contactsToChooseFrom = possibleMatches.ifEmpty { contactList }
        contactsToChooseFrom.forEachIndexed { index, contact ->
            println("${index + 1}. ${contact.name} ${contact.surname ?: ""} (${contact.phoneNumber})")
        }

        println("Enter the index of the contact you want to choose:")
        println("=======")

        val contactIndex = readln().toIntOrNull()
            ?: throw IllegalArgumentException("Invalid input.")

        val selectedContact = contactsToChooseFrom.getOrNull(contactIndex - 1)
            ?: throw IllegalArgumentException("Invalid input.")

        println(
            "You selected ${selectedContact.name} ${selectedContact.surname ?: ""} (${selectedContact.phoneNumber})."
        )
        return "Selected contact: ${selectedContact.id}: ${selectedContact.name} ${selectedContact.surname ?: ""} (${selectedContact.phoneNumber})."
    }
}

/**
 * Example of how to use the MoneyTransferTools with a chat agent.
 */
fun main() = runBlocking {
    val apiKey = ApiKeyService.openAIApiKey // Your OpenAI API key

    val toolRegistry = ToolRegistry {
        tools(MoneyTransferTools().asTools())
    }

    val agent = AIAgent(
        executor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = bankingAssistantSystemPrompt,
        temperature = 0.0,
        toolRegistry = toolRegistry
    )

    println("Banking Assistant started")
    val message = "Send 25 euros to Daniel for dinner at the restaurant."
//     You can try other messages like:
//     transfer messages
//        "Send 50 euros to Alice for the concert tickets"
//        "What's my current balance?"
//     analysis messages
//        "How much have I spent on restaurants this month?"
//         "What's my maximum check at a restaurant this month?"
//         "How much I spent on groceries in the first week of May?"
//         "What's my total spending on entertainment in May?"

    val result = agent.run(message)
    println(result)
}
