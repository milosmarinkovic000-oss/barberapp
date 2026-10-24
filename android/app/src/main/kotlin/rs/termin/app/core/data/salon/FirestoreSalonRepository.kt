package rs.termin.app.core.data.salon

import com.firebase.geofire.GeoFireUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import rs.termin.app.core.data.firebase.snapshots
import rs.termin.app.core.data.firebase.toReview
import rs.termin.app.core.data.firebase.toSalon
import rs.termin.app.core.data.firebase.toService
import rs.termin.app.core.data.firebase.toStylist
import rs.termin.app.core.domain.distanceKm
import rs.termin.app.core.domain.model.AvailabilityFilter
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.model.Review
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.SalonListItem
import rs.termin.app.core.domain.model.SearchFilters
import rs.termin.app.core.domain.model.Service
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.repository.SalonPage
import rs.termin.app.core.domain.repository.SalonRepository
import java.text.Normalizer
import java.time.Clock
import java.time.LocalDate
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import com.firebase.geofire.GeoQueryBounds
import com.firebase.geofire.GeoLocation as GeoFireLocation

/** Mala slova, bez dijakritika (isto kao na serveru za ključne reči pretrage). */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.lowercase().replace("đ", "dj"), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

fun searchTokens(text: String): List<String> =
    normalizeForSearch(text).split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }.map { it.take(MAX_TOKEN) }

private const val MAX_TOKEN = 15

@Singleton
class FirestoreSalonRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
) : SalonRepository {

    private val salons get() = firestore.collection("salons")
    private fun published(): Query = salons.whereEqualTo("published", true)

    override fun observeSalon(salonId: String): Flow<Salon?> =
        salons.document(salonId).snapshots().map { if (it.exists()) it.toSalon() else null }

    override fun observeServices(salonId: String, includeInactive: Boolean): Flow<List<Service>> =
        salons.document(salonId).collection("services").snapshots().map { snap ->
            snap.documents.map { it.toService() }.filter { includeInactive || it.active }.sortedBy { it.order }
        }

    override fun observeStylists(salonId: String, includeInactive: Boolean): Flow<List<Stylist>> =
        salons.document(salonId).collection("stylists").snapshots().map { snap ->
            snap.documents.map { it.toStylist(salonId) }.filter { includeInactive || it.active }.sortedBy { it.order }
        }

    override fun observeReviews(salonId: String, limit: Long): Flow<List<Review>> =
        salons.document(salonId).collection("reviews")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .snapshots()
            .map { snap -> snap.documents.map { it.toReview() } }

    override suspend fun topSalons(limit: Long, cursor: Any?): SalonPage {
        var query = published().orderBy("rating.avg", Query.Direction.DESCENDING).limit(limit)
        if (cursor is DocumentSnapshot) query = query.startAfter(cursor)
        val snap = query.get().await()
        return SalonPage(snap.documents.map { it.toSalon() }, snap.documents.lastOrNull()?.takeIf { snap.size() >= limit })
    }

    override suspend fun freeToday(limit: Long): List<Salon> {
        val now = clock.instant()
        val endOfDay = LocalDate.now(clock.withZone(BelgradeZone)).plusDays(1).atStartOfDay(BelgradeZone).toInstant()
        return published()
            .whereGreaterThanOrEqualTo("nextFreeAt", Timestamp(Date.from(now)))
            .whereLessThan("nextFreeAt", Timestamp(Date.from(endOfDay)))
            .orderBy("nextFreeAt")
            .limit(limit)
            .get().await()
            .documents.map { it.toSalon() }
    }

    override suspend fun nearby(center: GeoLocation, radiusKm: Double): List<SalonListItem> = coroutineScope {
        val bounds: List<GeoQueryBounds> = GeoFireUtils.getGeoHashQueryBounds(GeoFireLocation(center.latitude, center.longitude), radiusKm * 1000)
        val results = bounds.map { b ->
            async { published().orderBy("geohash").startAt(b.startHash).endAt(b.endHash).get().await().documents }
        }.awaitAll().flatten().distinctBy { it.id }
        results.map { it.toSalon() }
            .mapNotNull { salon -> salon.location?.let { SalonListItem(salon, distanceKm(center, it)) } }
            .filter { (it.distanceKm ?: Double.MAX_VALUE) <= radiusKm }
            .sortedBy { it.distanceKm }
    }

    override suspend fun search(filters: SearchFilters, location: GeoLocation?): List<SalonListItem> {
        val tokens = searchTokens(filters.query)
        val primary = tokens.maxByOrNull { it.length }
        val query = when {
            primary != null -> published().whereArrayContains("searchKeywords", primary)
            filters.category != null -> published().whereArrayContains("categories", filters.category.key)
            else -> published()
        }.orderBy("rating.avg", Query.Direction.DESCENDING).limit(SEARCH_LIMIT)
        val salonsFound = query.get().await().documents.map { it.toSalon() }
        val today = LocalDate.now(clock.withZone(BelgradeZone))
        return salonsFound
            .asSequence()
            .filter { salon ->
                val haystack = normalizeForSearch("${salon.name} ${salon.address} ${salon.city}")
                tokens.all { token -> token == primary || haystack.contains(token) }
            }
            .filter { filters.category == null || filters.category in it.categories }
            .filter { filters.maxPrice == null || (it.minPrice ?: Int.MAX_VALUE) <= filters.maxPrice }
            .filter { filters.minRating == null || it.rating.average >= filters.minRating }
            .filter { salon ->
                val next = salon.nextFreeAt?.atZone(BelgradeZone)?.toLocalDate()
                when (filters.availability) {
                    AvailabilityFilter.Any -> true
                    AvailabilityFilter.Today -> next == today
                    AvailabilityFilter.Tomorrow -> next != null && !next.isAfter(today.plusDays(1))
                }
            }
            .map { salon -> SalonListItem(salon, location?.let { loc -> salon.location?.let { distanceKm(loc, it) } }) }
            .filter { item -> filters.maxDistanceKm == null || (item.distanceKm != null && item.distanceKm <= filters.maxDistanceKm) }
            .toList()
    }

    override suspend fun salonsByIds(ids: Collection<String>): List<Salon> = coroutineScope {
        ids.distinct().chunked(IN_LIMIT).map { chunk ->
            async { published().whereIn(FieldPath.documentId(), chunk).get().await().documents.map { it.toSalon() } }
        }.awaitAll().flatten()
    }

    override suspend fun salonIdForSlug(slugOrId: String): String? {
        val byId = runCatching { salons.document(slugOrId).get().await() }.getOrNull()
        if (byId != null && byId.exists()) return byId.id
        return published().whereEqualTo("slug", slugOrId).limit(1).get().await().documents.firstOrNull()?.id
    }

    private companion object {
        const val SEARCH_LIMIT = 20L
        const val IN_LIMIT = 10
    }
}
