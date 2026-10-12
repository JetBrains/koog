package ai.koog.prompt.pii.regex

import ai.koog.prompt.pii.model.PiiType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegexPiiDetectorDefaultTest {
    @Test
    fun testDefaultProfileDetectsAllSupportedHighConfidenceTypes() = runTest {
        val detector = RegexPiiDetector.default()

        val sampleByType: List<Pair<PiiType, String>> = listOf(
            PiiType.EMAIL_ADDRESS to "john.doe@example.com",
            PiiType.PHONE_NUMBER to "(415) 555-1212",
            PiiType.US_SSN to "123-45-6789",
            PiiType.CREDIT_CARD to "4111 1111 1111 1111",
            PiiType.IP_ADDRESS to "192.168.1.1",
            PiiType.CRYPTO to "0x52908400098527886E0F7030069857D2E4169EE7",
            PiiType.IBAN_CODE to "GB82WEST12345698765432",
            PiiType.MAC_ADDRESS to "AA:BB:CC:DD:EE:FF",
            PiiType.US_ITIN to "912-70-1234",
            PiiType.US_MBI to "1EA4TE5MK73",
            PiiType.UK_NHS to "9434765919",
            PiiType.UK_NINO to "AB123456C",
            PiiType.ES_NIF to "12345678Z",
            PiiType.ES_NIE to "X1234567L",
            PiiType.IT_FISCAL_CODE to "RSSMRA85T10A562S",
            PiiType.IT_VAT_CODE to "12345678903",
            PiiType.PL_PESEL to "44051401458",
            PiiType.SG_NRIC_FIN to "S1234567D",
            PiiType.AU_ABN to "51 824 753 556",
            PiiType.AU_ACN to "004 085 616",
            PiiType.AU_MEDICARE to "2123456701",
            PiiType.IN_PAN to "ABCDE1234F",
            PiiType.IN_AADHAAR to "234123412346",
            PiiType.IN_VEHICLE_REGISTRATION to "KA01AB1234",
            PiiType.IN_VOTER to "ABC1234567",
            PiiType.IN_PASSPORT to "A1234567",
            PiiType.IN_GSTIN to "22AAAAA0000A1ZC",
            PiiType.FI_PERSONAL_IDENTITY_CODE to "131052-308T",
            PiiType.KR_BRN to "220-81-62517",
            PiiType.KR_RRN to "830422-1185601",
            PiiType.TH_TNIN to "1101700203450",
            PiiType.LOCATION to "123 Main Street",
            PiiType.US_PASSPORT to "AB1234567",
            PiiType.US_DRIVER_LICENSE to "A1234567",
            PiiType.LOCATION to "94105-1234",
            PiiType.US_BANK_NUMBER to "123456789012",
            PiiType.URL to "https://example.com/path?x=1",
            PiiType.DATE_TIME to "12/31/1990",
        )

        for ((expectedType, sample) in sampleByType) {
            val detections = detector.detect(sample)
            assertTrue(
                detections.any { it.type == expectedType },
                "Expected to detect type $expectedType in sample '$sample', got ${detections.map { it.type }}"
            )
        }
    }

    @Test
    fun testChecksumValidatorsRejectInvalidValues() = runTest {
        val detector = RegexPiiDetector.default()

        val validAndInvalidByType: List<Triple<PiiType, String, String>> = listOf(
            Triple(PiiType.IBAN_CODE, "GB82WEST12345698765432", "GB82WEST12345698765433"),
            Triple(PiiType.UK_NHS, "9434765919", "9434765918"),
            Triple(PiiType.ES_NIF, "12345678Z", "12345678A"),
            Triple(PiiType.ES_NIE, "X1234567L", "X1234567A"),
            Triple(PiiType.IT_VAT_CODE, "12345678903", "12345678904"),
            Triple(PiiType.PL_PESEL, "44051401458", "44051401459"),
            Triple(PiiType.SG_NRIC_FIN, "S1234567D", "S1234567A"),
            Triple(PiiType.AU_ABN, "51824753556", "51824753557"),
            Triple(PiiType.AU_ACN, "004085616", "004085617"),
            Triple(PiiType.AU_MEDICARE, "2123456701", "2123456711"),
            Triple(PiiType.IN_AADHAAR, "234123412346", "234123412347"),
            Triple(PiiType.IN_GSTIN, "22AAAAA0000A1ZC", "22AAAAA0000A1Z9"),
            Triple(PiiType.FI_PERSONAL_IDENTITY_CODE, "131052-308T", "131052-308A"),
            Triple(PiiType.KR_BRN, "2208162517", "2208162518"),
            Triple(PiiType.KR_RRN, "8304221185601", "8304221185602"),
            Triple(PiiType.TH_TNIN, "1101700203450", "1101700203451"),
        )

        for ((type, validSample, invalidSample) in validAndInvalidByType) {
            assertTrue(
                detector.detect(validSample).any { it.type == type },
                "Expected valid sample '$validSample' to match type $type"
            )
            assertFalse(
                detector.detect(invalidSample).any { it.type == type },
                "Expected invalid sample '$invalidSample' to be rejected for type $type"
            )
        }
    }

    @Test
    fun testCreditCardRequiresLuhnValidation() = runTest {
        val detector = RegexPiiDetector.default()

        val valid = detector.detect("Card: 4111 1111 1111 1111")
        assertTrue(valid.any { it.type == PiiType.CREDIT_CARD })

        val invalid = detector.detect("Card: 4111 1111 1111 1112")
        assertFalse(invalid.any { it.type == PiiType.CREDIT_CARD })
    }

    @Test
    fun testSsnValidityConstraints() = runTest {
        val detector = RegexPiiDetector.default()

        assertTrue(detector.detect("SSN: 123-45-6789").any { it.type == PiiType.US_SSN })
        assertFalse(detector.detect("SSN: 000-12-3456").any { it.type == PiiType.US_SSN })
        assertFalse(detector.detect("SSN: 666-12-3456").any { it.type == PiiType.US_SSN })
        assertFalse(detector.detect("SSN: 912-12-3456").any { it.type == PiiType.US_SSN })
        assertFalse(detector.detect("SSN: 123-00-3456").any { it.type == PiiType.US_SSN })
        assertFalse(detector.detect("SSN: 123-12-0000").any { it.type == PiiType.US_SSN })
    }

    @Test
    fun testDuplicateMatchesAreSuppressedBySpanAndType() = runTest {
        val emailRule = RegexPiiDetector.Rule(
            type = PiiType.EMAIL_ADDRESS,
            pattern = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
        )
        val detector = RegexPiiDetector(listOf(emailRule, emailRule))

        val detections = detector.detect("john.doe@example.com")
        assertEquals(1, detections.size)
        assertEquals(PiiType.EMAIL_ADDRESS, detections.single().type)
    }

    @Test
    fun testSpecificIdRulesPrecedeUsBankNumberWhenSpanCollides() = runTest {
        val detector = RegexPiiDetector.default()
        val input = "12345678903"

        val detections = detector
            .detect(input)
            .filter { it.start == 0 && it.endExclusive == input.length }

        assertTrue(detections.any { it.type == PiiType.IT_VAT_CODE })
        assertTrue(detections.any { it.type == PiiType.US_BANK_NUMBER })
        assertTrue(
            detections.indexOfFirst { it.type == PiiType.IT_VAT_CODE } <
                detections.indexOfFirst { it.type == PiiType.US_BANK_NUMBER }
        )
    }

    @Test
    fun testDefaultProfileDoesNotUseHeuristicTypes() = runTest {
        val detector = RegexPiiDetector.default()
        val types = detector
            .detect("John Doe met Alice at City Hospital and greeted Dr Brown.")
            .map { it.type }
            .toSet()

        assertFalse(types.contains(PiiType.PERSON))
        assertFalse(types.contains(PiiType.NRP))
        assertFalse(types.contains(PiiType.MEDICAL_LICENSE))
    }
}
