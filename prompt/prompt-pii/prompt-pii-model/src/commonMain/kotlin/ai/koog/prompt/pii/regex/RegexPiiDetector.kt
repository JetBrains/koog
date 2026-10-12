package ai.koog.prompt.pii.regex

import ai.koog.prompt.pii.model.PiiDetection
import ai.koog.prompt.pii.model.PiiDetector
import ai.koog.prompt.pii.model.PiiType

/**
 * Deterministic regex/checksum detector with a production-usable
 * default high-confidence profile (no heuristics).
 */
public class RegexPiiDetector(
    private val rules: List<Rule>,
) : PiiDetector {
    private data class DetectionKey(
        val start: Int,
        val endExclusive: Int,
        val type: PiiType
    )

    /**
     * A single regex detection rule.
     */
    public data class Rule(
        public val type: PiiType,
        public val pattern: Regex,
        public val validator: ((match: MatchResult, text: String) -> Boolean)? = null,
    )

    override suspend fun detect(text: String): List<PiiDetection> {
        val detections: MutableList<PiiDetection> = mutableListOf()
        val seen: MutableSet<DetectionKey> = mutableSetOf()

        for (rule in rules) {
            for (match in rule.pattern.findAll(text)) {
                if (rule.validator != null && !rule.validator.invoke(match, text)) continue

                val start: Int = match.range.first
                val endExclusive: Int = match.range.last + 1
                val key = DetectionKey(start, endExclusive, rule.type)
                if (!seen.add(key)) continue

                detections += PiiDetection(
                    start = start,
                    endExclusive = endExclusive,
                    type = rule.type
                )
            }
        }

        return detections
    }

    /**
     * Default regex implementation
     */
    public companion object {
        /**
         * Default deterministic regex profile aligned to high-confidence Presidio entity formats.
         */
        public fun default(): RegexPiiDetector = RegexPiiDetector(
            rules = listOf(
                Rule(
                    type = PiiType.EMAIL_ADDRESS,
                    pattern = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
                ),
                Rule(
                    type = PiiType.PHONE_NUMBER,
                    pattern = Regex(
                        """(?:\+?1[-.\s]?)?\(?([0-9]{3})\)?[-.\s]?([0-9]{3})[-.\s]?([0-9]{4})(?:\s?(?:ext|x|extension)\.?\s?(\d+))?"""
                    )
                ),
                Rule(
                    type = PiiType.US_SSN,
                    pattern = Regex("""\b(?!000|666|9\d{2})\d{3}-?(?!00)\d{2}-?(?!0000)\d{4}\b""")
                ),
                Rule(
                    type = PiiType.CREDIT_CARD,
                    pattern = Regex("""\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b"""),
                    validator = { match, _ -> isValidLuhn(match.value.filter { it.isDigit() }) }
                ),
                Rule(
                    type = PiiType.IP_ADDRESS,
                    pattern = Regex("""\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b""")
                ),
                Rule(
                    type = PiiType.CRYPTO,
                    pattern = Regex(
                        """\b(?:0x[a-fA-F0-9]{40}|bc1[ac-hj-np-z02-9]{25,39}|[13][a-km-zA-HJ-NP-Z1-9]{25,34})\b""",
                        RegexOption.IGNORE_CASE
                    )
                ),
                Rule(
                    type = PiiType.IBAN_CODE,
                    pattern = Regex("""\b[A-Z]{2}\d{2}[A-Z0-9]{11,30}\b"""),
                    validator = { match, _ -> isValidIban(match.value) }
                ),
                Rule(
                    type = PiiType.MAC_ADDRESS,
                    pattern = Regex(
                        """\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b|\b(?:[0-9A-Fa-f]{4}\.){2}[0-9A-Fa-f]{4}\b"""
                    )
                ),
                Rule(
                    type = PiiType.US_ITIN,
                    pattern = Regex("""\b9\d{2}[- ]?(7\d|8[0-8]|9[0-2]|9[4-9])[- ]?\d{4}\b""")
                ),
                Rule(
                    type = PiiType.US_MBI,
                    pattern = Regex("""\b[1-9][ACEHJKLMNPRTUVWXY][0-9ACEHJKLMNPRTUVWXY][0-9][ACEHJKLMNPRTUVWXY][0-9ACEHJKLMNPRTUVWXY][0-9][ACEHJKLMNPRTUVWXY][ACEHJKLMNPRTUVWXY][0-9]{2}\b""")
                ),
                Rule(
                    type = PiiType.UK_NHS,
                    pattern = Regex("""\b\d{3}[ -]?\d{3}[ -]?\d{4}\b"""),
                    validator = { match, _ -> isValidUkNhs(match.value) }
                ),
                Rule(
                    type = PiiType.UK_NINO,
                    pattern = Regex("""\b(?!BG|GB|KN|NK|NT|TN|ZZ)[A-CEGHJ-PR-TW-Z]{2}\d{6}[A-D]\b""")
                ),
                Rule(
                    type = PiiType.ES_NIF,
                    pattern = Regex("""\b\d{8}[A-Z]\b"""),
                    validator = { match, _ -> isValidEsNif(match.value) }
                ),
                Rule(
                    type = PiiType.ES_NIE,
                    pattern = Regex("""\b[XYZ]\d{7}[A-Z]\b"""),
                    validator = { match, _ -> isValidEsNie(match.value) }
                ),
                Rule(
                    type = PiiType.IT_FISCAL_CODE,
                    pattern = Regex("""\b[A-Z]{6}[0-9LMNPQRSTUV]{2}[A-EHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]\b""")
                ),
                Rule(
                    type = PiiType.IT_VAT_CODE,
                    pattern = Regex("""\b\d{11}\b"""),
                    validator = { match, _ -> isValidItVatCode(match.value) }
                ),
                Rule(
                    type = PiiType.PL_PESEL,
                    pattern = Regex("""\b\d{11}\b"""),
                    validator = { match, _ -> isValidPlPesel(match.value) }
                ),
                Rule(
                    type = PiiType.SG_NRIC_FIN,
                    pattern = Regex("""\b[STFG]\d{7}[A-Z]\b"""),
                    validator = { match, _ -> isValidSgNricFin(match.value) }
                ),
                Rule(
                    type = PiiType.AU_ABN,
                    pattern = Regex("""\b\d{2}\s?\d{3}\s?\d{3}\s?\d{3}\b"""),
                    validator = { match, _ -> isValidAuAbn(match.value) }
                ),
                Rule(
                    type = PiiType.AU_ACN,
                    pattern = Regex("""\b\d{3}\s?\d{3}\s?\d{3}\b"""),
                    validator = { match, _ -> isValidAuAcn(match.value) }
                ),
                Rule(
                    type = PiiType.AU_MEDICARE,
                    pattern = Regex("""\b\d{4}\s?\d{5}\s?\d\b"""),
                    validator = { match, _ -> isValidAuMedicare(match.value) }
                ),
                Rule(
                    type = PiiType.IN_PAN,
                    pattern = Regex("""\b[A-Z]{5}\d{4}[A-Z]\b""")
                ),
                Rule(
                    type = PiiType.IN_AADHAAR,
                    pattern = Regex("""\b\d{4}\s?\d{4}\s?\d{4}\b"""),
                    validator = { match, _ -> isValidAadhaar(match.value) }
                ),
                Rule(
                    type = PiiType.IN_VEHICLE_REGISTRATION,
                    pattern = Regex("""\b[A-Z]{2}[ -]?\d{2}[ -]?[A-Z]{1,3}[ -]?\d{1,4}\b""")
                ),
                Rule(
                    type = PiiType.IN_VOTER,
                    pattern = Regex("""\b[A-Z]{3}\d{7}\b""")
                ),
                Rule(
                    type = PiiType.IN_PASSPORT,
                    pattern = Regex("""\b[A-Z]\d{7}\b""")
                ),
                Rule(
                    type = PiiType.IN_GSTIN,
                    pattern = Regex("""\b\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]\b"""),
                    validator = { match, _ -> isValidInGstin(match.value) }
                ),
                Rule(
                    type = PiiType.FI_PERSONAL_IDENTITY_CODE,
                    pattern = Regex("""\b\d{6}[+\-A]\d{3}[0-9A-Y]\b"""),
                    validator = { match, _ -> isValidFiPersonalIdentityCode(match.value) }
                ),
                Rule(
                    type = PiiType.KR_BRN,
                    pattern = Regex("""\b\d{3}-?\d{2}-?\d{5}\b"""),
                    validator = { match, _ -> isValidKrBrn(match.value) }
                ),
                Rule(
                    type = PiiType.KR_RRN,
                    pattern = Regex("""\b\d{6}-?[1-8]\d{6}\b"""),
                    validator = { match, _ -> isValidKrRrn(match.value) }
                ),
                Rule(
                    type = PiiType.TH_TNIN,
                    pattern = Regex("""\b\d{13}\b"""),
                    validator = { match, _ -> isValidThTnin(match.value) }
                ),
                Rule(
                    type = PiiType.LOCATION,
                    pattern = Regex(
                        """\b\d+\s+[A-Za-z0-9\s,.-]+(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Lane|Ln|Drive|Dr|Court|Ct|Circle|Cir|Way|Place|Pl|Parkway|Pkwy|Terrace|Ter)\b""",
                        RegexOption.IGNORE_CASE
                    )
                ),
                Rule(
                    type = PiiType.US_PASSPORT,
                    pattern = Regex("""\b[A-Z]{2}[0-9]{7}\b""")
                ),
                Rule(
                    type = PiiType.US_DRIVER_LICENSE,
                    pattern = Regex("""\b[A-Z]{1,2}[0-9]{6,9}\b""")
                ),
                Rule(
                    type = PiiType.LOCATION,
                    pattern = Regex("""\b\d{5}(?:-\d{4})?\b""")
                ),
                Rule(
                    type = PiiType.US_BANK_NUMBER,
                    pattern = Regex("""\b\d{8,17}\b""")
                ),
                Rule(
                    type = PiiType.URL,
                    pattern = Regex("""https?://[-\w.]+(?::[0-9]+)?(?:/[\w/_.]*(?:\?[\w&=%.]*)?(?:#[\w.]*)?)?""")
                ),
                Rule(
                    type = PiiType.DATE_TIME,
                    pattern = Regex("""\b(?:0[1-9]|1[0-2])[-/](?:0[1-9]|[12][0-9]|3[01])[-/](?:19|20)\d{2}\b""")
                ),
            )
        )

        private const val ES_NIF_CHECKSUM: String = "TRWAGMYFPDXBNJZSQVHLCKE"
        private const val FI_PIC_CHECKSUM: String = "0123456789ABCDEFHJKLMNPRSTUVWXY"
        private const val BASE36_ALPHABET: String = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private val VERHOEFF_D: Array<IntArray> = arrayOf(
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
            intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
            intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
            intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
            intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
            intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
            intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2),
            intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
            intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
            intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
        )
        private val VERHOEFF_P: Array<IntArray> = arrayOf(
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
            intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
            intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
            intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
            intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
            intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
            intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
            intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8)
        )

        private fun isValidLuhn(number: String): Boolean {
            if (number.length < 12) return false

            var sum = 0
            var doubleDigit = false

            for (idx in number.length - 1 downTo 0) {
                val ch = number[idx]
                if (!ch.isDigit()) return false

                var digit = ch - '0'
                if (doubleDigit) {
                    digit *= 2
                    if (digit > 9) digit -= 9
                }
                sum += digit
                doubleDigit = !doubleDigit
            }

            return sum % 10 == 0
        }

        private fun isValidIban(value: String): Boolean {
            val iban = value.filter { it.isLetterOrDigit() }.uppercase()
            if (iban.length !in 15..34) return false
            if (!iban.take(2).all { it in 'A'..'Z' }) return false
            if (!iban.drop(2).all { it.isDigit() || it in 'A'..'Z' }) return false

            val rearranged: String = iban.drop(4) + iban.take(4)
            var remainder = 0
            for (ch in rearranged) {
                if (ch.isDigit()) {
                    remainder = (remainder * 10 + (ch - '0')) % 97
                } else {
                    val numeric: Int = ch.code - 'A'.code + 10
                    remainder = (remainder * 100 + numeric) % 97
                }
            }
            return remainder == 1
        }

        private fun isValidUkNhs(value: String): Boolean {
            val digits = value.filter(Char::isDigit)
            if (digits.length != 10) return false

            val checkDigit: Int = digits.last() - '0'
            val sum: Int = (0 until 9).sumOf { index ->
                val weight: Int = 10 - index
                (digits[index] - '0') * weight
            }
            val expectedCheckDigit: Int = when (
                val remainder: Int = 11 - (sum % 11)
            ) {
                11 -> 0
                10 -> return false
                else -> remainder
            }
            return checkDigit == expectedCheckDigit
        }

        private fun isValidEsNif(value: String): Boolean {
            val input = value.uppercase()
            if (!Regex("""\d{8}[A-Z]""").matches(input)) return false
            val number: Int = input.take(8).toIntOrNull() ?: return false
            val expectedLetter: Char = ES_NIF_CHECKSUM[number % 23]
            return input.last() == expectedLetter
        }

        private fun isValidEsNie(value: String): Boolean {
            val input = value.uppercase()
            if (!Regex("""[XYZ]\d{7}[A-Z]""").matches(input)) return false
            val prefix: Char = when (input.first()) {
                'X' -> '0'
                'Y' -> '1'
                'Z' -> '2'
                else -> return false
            }
            val numeric: Int = (prefix + input.substring(1, 8)).toIntOrNull() ?: return false
            val expectedLetter: Char = ES_NIF_CHECKSUM[numeric % 23]
            return input.last() == expectedLetter
        }

        private fun isValidItVatCode(value: String): Boolean {
            val digits = value.filter(Char::isDigit)
            if (digits.length != 11) return false

            var sum = 0
            for (index in 0 until 10) {
                var digit: Int = digits[index] - '0'
                if (index % 2 == 1) {
                    digit *= 2
                    if (digit > 9) digit -= 9
                }
                sum += digit
            }
            val expected: Int = (10 - (sum % 10)) % 10
            return expected == (digits.last() - '0')
        }

        private fun isValidPlPesel(value: String): Boolean {
            val digits = value.filter(Char::isDigit)
            if (digits.length != 11) return false
            if (!isValidPeselDate(digits)) return false

            val weights: IntArray = intArrayOf(1, 3, 7, 9, 1, 3, 7, 9, 1, 3)
            val sum: Int = weights.indices.sumOf { index ->
                (digits[index] - '0') * weights[index]
            }
            val checksum: Int = (10 - (sum % 10)) % 10
            return checksum == (digits.last() - '0')
        }

        private fun isValidPeselDate(digits: String): Boolean {
            val year: Int = digits.take(2).toIntOrNull() ?: return false
            val monthCode: Int = digits.substring(2, 4).toIntOrNull() ?: return false
            val day: Int = digits.substring(4, 6).toIntOrNull() ?: return false

            val (century, month) = when (monthCode) {
                in 1..12 -> 1900 to monthCode
                in 21..32 -> 2000 to monthCode - 20
                in 41..52 -> 2100 to monthCode - 40
                in 61..72 -> 2200 to monthCode - 60
                in 81..92 -> 1800 to monthCode - 80
                else -> return false
            }

            return isValidDate(century + year, month, day)
        }

        private fun isValidSgNricFin(value: String): Boolean {
            val input = value.uppercase()
            if (!Regex("""[STFG]\d{7}[A-Z]""").matches(input)) return false

            val prefix: Char = input.first()
            val digits: String = input.substring(1, 8)
            val check: Char = input.last()
            val weights: IntArray = intArrayOf(2, 7, 6, 5, 4, 3, 2)

            var sum = 0
            for (index in digits.indices) {
                sum += (digits[index] - '0') * weights[index]
            }
            if (prefix == 'T' || prefix == 'G') sum += 4

            val lookup: String = if (prefix == 'S' || prefix == 'T') "JZIHGFEDCBA" else "XWUTRQPNMLK"
            return check == lookup[sum % 11]
        }

        private fun isValidAuAbn(value: String): Boolean {
            val digits = value.filter(Char::isDigit)
            if (digits.length != 11) return false

            val weights: IntArray = intArrayOf(10, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19)
            var sum = 0
            for (index in digits.indices) {
                var digit: Int = digits[index] - '0'
                if (index == 0) {
                    if (digit == 0) return false
                    digit -= 1
                }
                sum += digit * weights[index]
            }
            return sum % 89 == 0
        }

        private fun isValidAuAcn(value: String): Boolean {
            val digits = value.filter(Char::isDigit)
            if (digits.length != 9) return false

            val weights: IntArray = intArrayOf(8, 7, 6, 5, 4, 3, 2, 1)
            val sum: Int = (0 until 8).sumOf { index ->
                (digits[index] - '0') * weights[index]
            }
            val checksum: Int = (10 - (sum % 10)) % 10
            return checksum == (digits.last() - '0')
        }

        private fun isValidAuMedicare(value: String): Boolean {
            val digits = value.filter(Char::isDigit)
            if (digits.length != 10) return false

            val weights: IntArray = intArrayOf(1, 3, 7, 9, 1, 3, 7, 9)
            val sum: Int = weights.indices.sumOf { index ->
                (digits[index] - '0') * weights[index]
            }
            return (sum % 10) == (digits[8] - '0')
        }

        private fun isValidAadhaar(value: String): Boolean {
            val digits = value.filter(Char::isDigit)
            if (digits.length != 12) return false

            var checksum = 0
            val reversed = digits.reversed()
            for (index in reversed.indices) {
                val digit = reversed[index] - '0'
                checksum = VERHOEFF_D[checksum][VERHOEFF_P[index % 8][digit]]
            }
            return checksum == 0
        }

        private fun isValidInGstin(value: String): Boolean {
            val gstin: String = value.filter { it.isLetterOrDigit() }.uppercase()
            if (gstin.length != 15) return false

            val expected: Char = calculateGstinCheckChar(gstin.take(14))
            return expected == gstin.last()
        }

        private fun calculateGstinCheckChar(first14: String): Char {
            var factor = 2
            var sum = 0

            for (ch in first14.reversed()) {
                val codePoint: Int = BASE36_ALPHABET.indexOf(ch)
                if (codePoint < 0) return '?'
                val product: Int = codePoint * factor
                sum += (product / 36) + (product % 36)
                factor = if (factor == 2) 1 else 2
            }

            return BASE36_ALPHABET[(36 - (sum % 36)) % 36]
        }

        private fun isValidFiPersonalIdentityCode(value: String): Boolean {
            val input = value.uppercase()
            if (!Regex("""\d{6}[+\-A]\d{3}[0-9A-Y]""").matches(input)) return false

            val day: Int = input.take(2).toIntOrNull() ?: return false
            val month: Int = input.substring(2, 4).toIntOrNull() ?: return false
            val yearPart: Int = input.substring(4, 6).toIntOrNull() ?: return false
            val separator: Char = input[6]
            val individual: String = input.substring(7, 10)
            val individualNumber: Int = individual.toIntOrNull() ?: return false

            val century: Int = when (separator) {
                '+' -> 1800
                '-' -> 1900
                'A' -> 2000
                else -> return false
            }
            val year: Int = century + yearPart
            if (!isValidDate(year, month, day)) return false
            if (individualNumber !in 2..899) return false

            val checksumInput: Int = (input.take(6) + individual).toIntOrNull() ?: return false
            val expected: Char = FI_PIC_CHECKSUM[checksumInput % 31]
            return input.last() == expected
        }

        private fun isValidKrBrn(value: String): Boolean {
            val digits = value.filter(Char::isDigit)
            if (digits.length != 10) return false

            val weights: IntArray = intArrayOf(1, 3, 7, 1, 3, 7, 1, 3, 5)
            var sum = 0
            for (index in 0 until 9) {
                sum += (digits[index] - '0') * weights[index]
            }
            sum += ((digits[8] - '0') * 5) / 10
            val expected: Int = (10 - (sum % 10)) % 10
            return expected == (digits.last() - '0')
        }

        private fun isValidKrRrn(value: String): Boolean {
            val digits = value.filter(Char::isDigit)
            if (digits.length != 13) return false

            val yearPart: Int = digits.take(2).toIntOrNull() ?: return false
            val month: Int = digits.substring(2, 4).toIntOrNull() ?: return false
            val day: Int = digits.substring(4, 6).toIntOrNull() ?: return false
            val categoryDigit: Int = digits[6] - '0'
            val century: Int = when (categoryDigit) {
                1, 2, 5, 6 -> 1900
                3, 4, 7, 8 -> 2000
                9, 0 -> 1800
                else -> return false
            }
            if (!isValidDate(century + yearPart, month, day)) return false

            val weights: IntArray = intArrayOf(2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5)
            val sum: Int = weights.indices.sumOf { index ->
                (digits[index] - '0') * weights[index]
            }
            val expected: Int = (11 - (sum % 11)) % 10
            return expected == (digits.last() - '0')
        }

        private fun isValidThTnin(value: String): Boolean {
            val digits = value.filter(Char::isDigit)
            if (digits.length != 13) return false

            var sum = 0
            for (index in 0 until 12) {
                sum += (digits[index] - '0') * (13 - index)
            }
            val expected: Int = (11 - (sum % 11)) % 10
            return expected == (digits.last() - '0')
        }

        private fun isValidDate(year: Int, month: Int, day: Int): Boolean {
            if (month !in 1..12) return false
            if (day < 1) return false
            val maxDay: Int = when (month) {
                1, 3, 5, 7, 8, 10, 12 -> 31
                4, 6, 9, 11 -> 30
                2 -> if (isLeapYear(year)) 29 else 28
                else -> return false
            }
            return day <= maxDay
        }

        private fun isLeapYear(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }
}
