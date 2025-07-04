package codeEngine

class Dummy {
    var name: String

    init {
        name = "Dummy"
    }

    fun printName() {
        println("Name: " + name)
    }

    class NestedDummyClass {
        var nestedName: String = "NestedDummy"

        fun printNestedName2() {
            println("Nested Name: " + nestedName)
        }
    }
}
