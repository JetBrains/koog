package ai.koog.utils.system

import io.kotest.matchers.shouldBe
import platform.Foundation.NSUserDefaults.Companion.standardUserDefaults
import kotlin.test.Test

class UserDefaultsSystemConfigReaderTest {

    companion object {
        init {
            val defaults = standardUserDefaults()
            defaults.setObject("ho-ho", forKey = "FOO_PROP")
        }
    }

    private val sublect = UserDefaultsSystemConfigReader.shared

    @Test
    fun testReadValue() {
        sublect.getConfigVariable("FOO_PROP") shouldBe "ho-ho"
    }

    @Test
    fun testReadMissingValue() {
        sublect.getConfigVariable("BAR_PROP") shouldBe null
    }
}
