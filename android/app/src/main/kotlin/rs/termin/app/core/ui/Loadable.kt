package rs.termin.app.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import rs.termin.app.core.data.firebase.ImageUrls

/** Stanje podataka na ekranu: učitavanje, podaci ili greška (sa „Pokušaj ponovo“). */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val error: Throwable) : Loadable<Nothing>

    fun valueOrNull(): T? = (this as? Ready<T>)?.value
}

/** Pokreće blok i vraća rezultat kao [Loadable]; otkazivanje korutine se prosleđuje dalje. */
suspend fun <T> loadable(block: suspend () -> T): Loadable<T> = try {
    Loadable.Ready(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Loadable.Failed(e)
}

/** URL-ovi slika iz Cloud Storage-a (emulator ili produkcija). */
val LocalImageUrls = staticCompositionLocalOf<ImageUrls> { error("ImageUrls nije postavljen") }
