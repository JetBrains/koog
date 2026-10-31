package ai.koog.prompt.pii.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PiiTypeMapperTest {
    @Test
    fun testKnownLabelsMapToEnum() {
        assertEquals(PiiType.PERSON, PiiTypeMapper.fromLabel("person"))
        assertEquals(PiiType.EMAIL_ADDRESS, PiiTypeMapper.fromLabel("email_address"))
        assertEquals(PiiType.PHONE_NUMBER, PiiTypeMapper.fromLabel("phone"))
        assertEquals(PiiType.US_SSN, PiiTypeMapper.fromLabel("ssn"))
        assertEquals(PiiType.CREDIT_CARD, PiiTypeMapper.fromLabel("card_number"))
        assertEquals(PiiType.CRYPTO, PiiTypeMapper.fromLabel("wallet_address"))
        assertEquals(PiiType.LOCATION, PiiTypeMapper.fromLabel("zip"))
        assertEquals(PiiType.LOCATION, PiiTypeMapper.fromLabel("zipcode"))
        assertEquals(PiiType.LOCATION, PiiTypeMapper.fromLabel("zip_code"))
        assertEquals(PiiType.LOCATION, PiiTypeMapper.fromLabel("postal_code"))
        assertEquals(PiiType.LOCATION, PiiTypeMapper.fromLabel("postal"))
        assertEquals(PiiType.US_BANK_NUMBER, PiiTypeMapper.fromLabel("US_BANK_NUMBER"))
        assertEquals(PiiType.IT_VAT_CODE, PiiTypeMapper.fromLabel("it-vat-code"))
    }

    @Test
    fun testUnknownLabelsMapToOther() {
        assertEquals(PiiType.OTHER, PiiTypeMapper.fromLabel("favorite_color"))
        assertEquals(PiiType.OTHER, PiiTypeMapper.fromLabel("custom:unknown type"))
    }
}
