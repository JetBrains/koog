package codeEngine

class TestGenerator {
    fun createTest() {
        val abc = "abc"
        var sum = 0

        for (c in abc) {
            sum += c.toInt()
            println("Char $\c to Int:" + c.toInt())
        }

        println("Sum of char values: " + sum)

        val list = listOf(1, 2, 3, 4, 5).shuffled() //Exif1
        println("Shuffled list: " + list)

        when {
            sum > 300 -> println("Sum is greater than 300")
            sum < 300 -> println("Sum is less than 300")
            else -> println("Sum is equal to 300")
        }
    }
}
