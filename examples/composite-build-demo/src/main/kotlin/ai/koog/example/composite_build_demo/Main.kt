package ai.koog.example.composite_build_demo

import ai.koog.prompt.dsl.prompt

fun main() {
    val prompt = prompt("test-prompt") {
        system("Using symbols form Koog in composite build demo!")
    }

    println(prompt)
}
