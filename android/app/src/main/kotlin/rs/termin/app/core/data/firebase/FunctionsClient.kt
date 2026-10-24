package rs.termin.app.core.data.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import rs.termin.app.core.domain.model.BookingError
import rs.termin.app.core.domain.model.PolicyReason
import rs.termin.app.core.domain.model.Slot
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Poziv callable funkcija sa prevodom grešaka u [BookingError]. */
@Singleton
class FunctionsClient @Inject constructor(private val functions: FirebaseFunctions) {

    suspend fun call(name: String, data: Map<String, Any?>): Map<String, Any?> {
        try {
            val result = functions.getHttpsCallable(name).apply { setTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
                .call(data)
                .await()
            @Suppress("UNCHECKED_CAST")
            return (result.getData() as? Map<String, Any?>).orEmpty()
        } catch (e: FirebaseFunctionsException) {
            throw mapFunctionsError(e.code, e.details, e.message)
        } catch (e: FirebaseNetworkException) {
            throw BookingError.Offline
        } catch (e: IOException) {
            throw BookingError.Offline
        }
    }

    /** Za čitanja: ponovni pokušaj sa postepenim odlaganjem (0,5 s, 1 s, 2 s) kad nema mreže. */
    suspend fun callWithRetry(name: String, data: Map<String, Any?>, attempts: Int = 3): Map<String, Any?> {
        var wait = 500L
        repeat(attempts - 1) {
            try {
                return call(name, data)
            } catch (e: BookingError.Offline) {
                delay(wait)
                wait *= 2
            }
        }
        return call(name, data)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 20L
    }
}

/** Prevod grešaka servera (details.code) u domenske greške. */
fun mapFunctionsError(code: FirebaseFunctionsException.Code, details: Any?, message: String?): BookingError {
    val map = details as? Map<*, *>
    return when (map?.get("code") as? String) {
        "SLOT_TAKEN" -> BookingError.SlotTaken(parseSlots(map["slots"]))
        "POLICY_VIOLATION" -> BookingError.PolicyViolation(
            reason = PolicyReason.from(map["reason"] as? String),
            salonPhone = map["salonPhone"] as? String,
            deadline = (map["deadline"] as? Number)?.toLong()?.let(Instant::ofEpochMilli),
        )
        "NOT_ALLOWED" -> BookingError.NotAllowed
        "VALIDATION" -> BookingError.Validation
        "NOT_FOUND" -> BookingError.NotFound
        "RATE_LIMITED" -> BookingError.RateLimited
        "INTEGRATION_NOT_CONFIGURED" -> BookingError.IntegrationNotConfigured
        else -> when (code) {
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            -> BookingError.Offline
            FirebaseFunctionsException.Code.UNAUTHENTICATED,
            FirebaseFunctionsException.Code.PERMISSION_DENIED,
            -> BookingError.NotAllowed
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> BookingError.RateLimited
            FirebaseFunctionsException.Code.NOT_FOUND -> BookingError.NotFound
            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> BookingError.Validation
            else -> BookingError.Unknown(message)
        }
    }
}

fun parseSlots(value: Any?): List<Slot> = (value as? List<*>).orEmpty().mapNotNull { item ->
    val m = item as? Map<*, *> ?: return@mapNotNull null
    val start = (m["start"] as? Number)?.toLong() ?: return@mapNotNull null
    Slot(Instant.ofEpochMilli(start), (m["stylistIds"] as? List<*>).orEmpty().filterIsInstance<String>())
}
