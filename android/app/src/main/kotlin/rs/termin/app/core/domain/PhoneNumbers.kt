package rs.termin.app.core.domain

/** Telefonski brojevi u Srbiji → E.164 (+381…). */
object PhoneNumbers {
    private const val COUNTRY = "381"

    /**
     * Prihvata „60 123 4567“, „060/123-4567“, „+381 60 1234567“ ili „00381…“.
     * Vraća null ako broj nije ispravan.
     */
    fun toE164(input: String): String? {
        val trimmed = input.trim()
        val digits = trimmed.filter { it.isDigit() }
        val national = when {
            trimmed.startsWith("+") && digits.startsWith(COUNTRY) -> digits.removePrefix(COUNTRY)
            trimmed.startsWith("+") -> return if (digits.length in 8..15) "+$digits" else null
            digits.startsWith("00$COUNTRY") -> digits.removePrefix("00$COUNTRY")
            digits.startsWith("0") -> digits.removePrefix("0")
            else -> digits
        }
        if (national.length !in 8..10 || national.startsWith("0")) return null
        return "+$COUNTRY$national"
    }

    /** Za prikaz: +381601234567 → +381 60 123 4567 */
    fun display(e164: String): String {
        if (!e164.startsWith("+$COUNTRY")) return e164
        val national = e164.removePrefix("+$COUNTRY")
        if (national.length < 8) return e164
        val area = national.take(2)
        val rest = national.drop(2)
        val split = if (rest.length > 4) "${rest.dropLast(4)} ${rest.takeLast(4)}" else rest
        return "+$COUNTRY $area $split"
    }
}
