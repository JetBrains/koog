package ai.koog.rag.vector.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MetadataFilterTest {

    @Test
    fun testBlankAndNullAreEmpty() {
        assertNull(MetadataFilter.compile(null).jsonContainment)
        assertNull(MetadataFilter.compile("").jsonContainment)
        assertNull(MetadataFilter.compile("   ").jsonContainment)
    }

    @Test
    fun testSingleStringTerm() {
        val c = MetadataFilter.compile("""tenant = "acme"""")
        assertEquals("""{"tenant":"acme"}""", c.jsonContainment.toString())
    }

    @Test
    fun testAndCombination() {
        val c = MetadataFilter.compile("""tenant = "acme" AND type = "doc"""")
        assertEquals("""{"tenant":"acme","type":"doc"}""", c.jsonContainment.toString())
    }

    @Test
    fun testDottedKeyBecomesNestedJson() {
        val c = MetadataFilter.compile("""owner.team = "core"""")
        assertEquals("""{"owner":{"team":"core"}}""", c.jsonContainment.toString())
    }

    @Test
    fun testNumericAndBooleanLiterals() {
        val c = MetadataFilter.compile("count = 42 AND active = true AND score = -1.5")
        val s = c.jsonContainment.toString()
        // Order is insertion-ordered; verify components individually.
        assertEquals("""{"count":42,"active":true,"score":-1.5}""", s)
    }

    @Test
    fun testContradictoryTermsRejected() {
        assertFailsWith<IllegalArgumentException> {
            MetadataFilter.compile("""tenant = "a" AND tenant = "b"""")
        }
    }

    @Test
    fun testUnsupportedOperatorRejected() {
        assertFailsWith<IllegalArgumentException> {
            MetadataFilter.compile("tenant != \"acme\"")
        }
    }

    @Test
    fun testUnterminatedStringRejected() {
        assertFailsWith<IllegalArgumentException> {
            MetadataFilter.compile("""tenant = "acme""")
        }
    }

    @Test
    fun testEmbeddedQuoteRejectsInjectionAttempts() {
        // A naive string concatenator would inject SQL here; we only produce a JSON literal.
        val c = MetadataFilter.compile("""tenant = "acme\"; DROP TABLE x; --"""")
        // The raw quote is escaped as part of the string value — no SQL is produced at all.
        assertEquals("""{"tenant":"acme\"; DROP TABLE x; --"}""", c.jsonContainment.toString())
    }
}
