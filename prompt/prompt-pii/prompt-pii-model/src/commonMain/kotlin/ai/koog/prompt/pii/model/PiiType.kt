package ai.koog.prompt.pii.model

import kotlinx.serialization.Serializable

/**
 * Enumerates PII categories aligned with Microsoft Presidio entity types:
 * https://microsoft.github.io/presidio/supported_entities/
 */
@Serializable
public enum class PiiType {
    CREDIT_CARD,
    CRYPTO,
    DATE_TIME,
    EMAIL_ADDRESS,
    IBAN_CODE,
    IP_ADDRESS,
    MAC_ADDRESS,
    NRP,
    LOCATION,
    PERSON,
    PHONE_NUMBER,
    MEDICAL_LICENSE,
    URL,
    US_BANK_NUMBER,
    US_DRIVER_LICENSE,
    US_ITIN,
    US_MBI,
    US_PASSPORT,
    US_SSN,
    UK_NHS,
    UK_NINO,
    ES_NIF,
    ES_NIE,
    IT_FISCAL_CODE,
    IT_DRIVER_LICENSE,
    IT_VAT_CODE,
    IT_PASSPORT,
    IT_IDENTITY_CARD,
    PL_PESEL,
    SG_NRIC_FIN,
    SG_UEN,
    AU_ABN,
    AU_ACN,
    AU_TFN,
    AU_MEDICARE,
    IN_PAN,
    IN_AADHAAR,
    IN_VEHICLE_REGISTRATION,
    IN_VOTER,
    IN_PASSPORT,
    IN_GSTIN,
    FI_PERSONAL_IDENTITY_CODE,
    KR_DRIVER_LICENSE,
    KR_FRN,
    KR_PASSPORT,
    KR_BRN,
    KR_RRN,
    TH_TNIN,
    OTHER;

    /**
     * Token used inside anonymization tags.
     */
    public val tagToken: String
        get() = name.lowercase()
}

/**
 * Utility for mapping provider-specific type labels to [PiiType].
 */
public object PiiTypeMapper {
    private val aliases: Map<String, PiiType> = buildMap {
        for (type in PiiType.entries) {
            if (type == PiiType.OTHER) continue
            put(type.name.lowercase(), type)
        }

        // Common short labels
        put("email", PiiType.EMAIL_ADDRESS)
        put("e_mail", PiiType.EMAIL_ADDRESS)
        put("phone", PiiType.PHONE_NUMBER)
        put("mobile", PiiType.PHONE_NUMBER)
        put("msisdn", PiiType.PHONE_NUMBER)
        put("ssn", PiiType.US_SSN)
        put("social_security_number", PiiType.US_SSN)
        put("credit_card_number", PiiType.CREDIT_CARD)
        put("card_number", PiiType.CREDIT_CARD)
        put("pan", PiiType.CREDIT_CARD)
        put("wallet_address", PiiType.CRYPTO)
        put("crypto_wallet", PiiType.CRYPTO)
        put("iban", PiiType.IBAN_CODE)
        put("ip", PiiType.IP_ADDRESS)
        put("ipv4", PiiType.IP_ADDRESS)
        put("ipv6", PiiType.IP_ADDRESS)
        put("mac", PiiType.MAC_ADDRESS)
        put("person", PiiType.PERSON)
        put("full_name", PiiType.PERSON)
        put("name", PiiType.PERSON)
        put("address", PiiType.LOCATION)
        put("street_address", PiiType.LOCATION)
        put("zip", PiiType.LOCATION)
        put("zipcode", PiiType.LOCATION)
        put("zip_code", PiiType.LOCATION)
        put("postal_code", PiiType.LOCATION)
        put("postal", PiiType.LOCATION)
        put("bank_account_number", PiiType.US_BANK_NUMBER)
        put("account_number", PiiType.US_BANK_NUMBER)
        put("passport", PiiType.US_PASSPORT)
        put("passport_number", PiiType.US_PASSPORT)
        put("driver_license", PiiType.US_DRIVER_LICENSE)
        put("drivers_license", PiiType.US_DRIVER_LICENSE)
        put("driver_license_number", PiiType.US_DRIVER_LICENSE)
    }

    /**
     * Normalizes a detector-provided label.
     */
    public fun defaultNormalizeTypeLabel(label: String): String =
        label
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

    /**
     * Maps a detector-provided label into [PiiType].
     */
    public fun fromLabel(
        label: String,
        normalizeType: (String) -> String = ::defaultNormalizeTypeLabel
    ): PiiType {
        val normalized: String = normalizeType(label)
        return aliases[normalized] ?: PiiType.OTHER
    }
}
