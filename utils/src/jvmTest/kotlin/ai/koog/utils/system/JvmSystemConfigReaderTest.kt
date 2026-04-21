package ai.koog.utils.system

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class JvmSystemConfigReaderTest {

    companion object {
        init {
            System.setProperty("FOO_PROP", "42")
            System.setProperty("bar.prop", "43")
        }
    }

    private val subject = systemConfigReader()

    @Test
    fun `getConfig should read from env`() {
        subject.getConfigVariable("HOME") shouldBe System.getenv("HOME")
    }

    @Test
    fun `getConfig should read from system property`() {
        subject.getConfigVariable("FOO_PROP") shouldBe "42"
    }

    @Test
    fun `getConfig should read from normalized system property`() {
        subject.getConfigVariable("BAR_PROP") shouldBe "43"
    }

    @Test
    fun `getConfig should skip unknown entry`() {
        subject.getConfigVariable("ZELIBOBA") shouldBe null
    }
}
