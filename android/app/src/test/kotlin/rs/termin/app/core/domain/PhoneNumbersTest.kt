package rs.termin.app.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumbersTest {
    @Test
    fun `domaci formati postaju E164`() {
        assertEquals("+381601234567", PhoneNumbers.toE164("060 123 4567"))
        assertEquals("+381601234567", PhoneNumbers.toE164("060/123-4567"))
        assertEquals("+381601234567", PhoneNumbers.toE164("60 123 4567"))
        assertEquals("+381601234567", PhoneNumbers.toE164("+381 60 123 4567"))
        assertEquals("+381601234567", PhoneNumbers.toE164("00381601234567"))
        assertEquals("+381112345678", PhoneNumbers.toE164("011 234 5678"))
    }

    @Test
    fun `strani broj sa plusom ostaje`() {
        assertEquals("+4915123456789", PhoneNumbers.toE164("+49 151 23456789"))
    }

    @Test
    fun `neispravni brojevi`() {
        assertNull(PhoneNumbers.toE164(""))
        assertNull(PhoneNumbers.toE164("123"))
        assertNull(PhoneNumbers.toE164("0060123"))
        assertNull(PhoneNumbers.toE164("+12"))
    }

    @Test
    fun `prikaz broja`() {
        assertEquals("+381 60 123 4567", PhoneNumbers.display("+381601234567"))
        assertEquals("+381 11 234 5678", PhoneNumbers.display("+381112345678"))
        assertEquals("+4915123456789", PhoneNumbers.display("+4915123456789"))
    }
}
