package kr.ac.suwon.arsagent.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumberDisplayTest {
    @Test fun mobileAndInternetNumbersAreFormattedWhileTyping() {
        val cases = mapOf(
            "" to "", "0" to "0", "010" to "010", "0101" to "010-1",
            "0101234" to "010-1234", "01012345" to "010-1234-5",
            "01012345678" to "010-1234-5678", "0701" to "070-1",
            "07012345678" to "070-1234-5678",
        )
        cases.forEach { (raw, expected) -> assertEquals(raw, expected, formatPhone(raw)) }
    }

    @Test fun serviceAndLocalNumbersUseTheirOwnGrouping() {
        val cases = mapOf(
            "131" to "131", "112" to "112", "1588" to "1588",
            "15881" to "1588-1", "15881300" to "1588-1300",
            "021" to "02-1", "021234567" to "02-123-4567",
            "0212345678" to "02-1234-5678", "0311234567" to "031-123-4567",
            "03112345678" to "031-1234-5678",
        )
        cases.forEach { (raw, expected) -> assertEquals(raw, expected, formatPhone(raw)) }
    }

    @Test fun formattedInputIsIdempotentAndSpecialDialStringsAreNotGrouped() {
        assertEquals("010-1234-5678", formatPhone(" 010-1234-5678 "))
        assertEquals("070-1234-5678", formatPhone("070 1234 5678"))
        assertEquals("*123456#", formatPhone("*123456#"))
        assertEquals("1234567890123", formatPhone("1234567890123"))
    }
}
