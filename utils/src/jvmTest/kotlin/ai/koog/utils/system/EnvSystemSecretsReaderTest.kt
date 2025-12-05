package ai.koog.utils.system

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EnvSystemSecretsReaderTest {

    private val subject = systemSecretsReader()

    @Test
    fun `getSecret should read from env`() {
        subject.getSecret("HOME") shouldBe System.getenv("HOME")
    }

    @Test
    fun `getSecret should skip unknown entry`() {
        subject.getSecret("ZELIBOBA") shouldBe null
    }
}
