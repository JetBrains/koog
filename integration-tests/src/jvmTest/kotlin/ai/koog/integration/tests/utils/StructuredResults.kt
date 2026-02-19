package ai.koog.integration.tests.utils

import kotlinx.serialization.Serializable

class StructuredResults {
    @Serializable
    class CalculationResult(val result: Int, val operation: String?)

    @Serializable
    class PersonInfo(val name: String?, val age: Int, val hobbies: MutableList<String?>?)
}
