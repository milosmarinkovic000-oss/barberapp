package rs.termin.app.core.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.AppointmentSource
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.BookedService
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.model.InboxMessage
import rs.termin.app.core.domain.model.IntegrationState
import rs.termin.app.core.domain.model.Provider
import rs.termin.app.core.domain.model.Rating
import rs.termin.app.core.domain.model.Review
import rs.termin.app.core.domain.model.Role
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.SalonRules
import rs.termin.app.core.domain.model.Service
import rs.termin.app.core.domain.model.ServiceCategory
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.domain.model.TimeOff
import rs.termin.app.core.domain.model.TimeOffKind
import rs.termin.app.core.domain.model.TimeRange
import rs.termin.app.core.domain.model.UserProfile
import rs.termin.app.core.domain.model.WaitlistEntry
import rs.termin.app.core.domain.model.WeeklyHours
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// Ručno mapiranje (bez refleksije): brže i bezbedno za R8.

private fun Any?.asString(default: String = ""): String = this as? String ?: default
private fun Any?.asInt(default: Int = 0): Int = (this as? Number)?.toInt() ?: default
private fun Any?.asStrings(): List<String> = (this as? List<*>).orEmpty().filterIsInstance<String>()
private fun Any?.asInstant(): Instant? = (this as? Timestamp)?.toInstant()

fun parseTime(value: String): LocalTime? = runCatching {
    if (value == "24:00") LocalTime.MAX else LocalTime.parse(value)
}.getOrNull()

fun formatTime(time: LocalTime): String = if (time == LocalTime.MAX) "24:00" else "%02d:%02d".format(time.hour, time.minute)

fun parseHours(value: Any?): WeeklyHours {
    val map = value as? Map<*, *> ?: return WeeklyHours.Empty
    val days = map.mapNotNull { (key, ranges) ->
        val day = (key as? String)?.toIntOrNull()?.takeIf { it in 1..7 }?.let(DayOfWeek::of) ?: return@mapNotNull null
        day to (ranges as? List<*>).orEmpty().mapNotNull { r ->
            val m = r as? Map<*, *> ?: return@mapNotNull null
            val start = parseTime(m["start"].asString()) ?: return@mapNotNull null
            val end = parseTime(m["end"].asString()) ?: return@mapNotNull null
            TimeRange(start, end)
        }
    }.toMap()
    return WeeklyHours(days)
}

fun WeeklyHours.toFirestore(): Map<String, List<Map<String, String>>> =
    DayOfWeek.entries.associate { day ->
        day.value.toString() to on(day).map { mapOf("start" to formatTime(it.start), "end" to formatTime(it.end)) }
    }

fun parseRules(value: Any?): SalonRules {
    val m = value as? Map<*, *> ?: return SalonRules.Default
    val d = SalonRules.Default
    return SalonRules(
        cancelHours = m["cancelHours"].asInt(d.cancelHours),
        minNoticeMinutes = m["minNoticeMinutes"].asInt(d.minNoticeMinutes),
        horizonDays = m["horizonDays"].asInt(d.horizonDays),
        slotStepMin = m["slotStepMin"].asInt(d.slotStepMin),
        bufferMin = m["bufferMin"].asInt(d.bufferMin),
    )
}

fun SalonRules.toFirestore(): Map<String, Int> = mapOf(
    "cancelHours" to cancelHours,
    "minNoticeMinutes" to minNoticeMinutes,
    "horizonDays" to horizonDays,
    "slotStepMin" to slotStepMin,
    "bufferMin" to bufferMin,
)

private fun parseRating(value: Any?): Rating {
    val m = value as? Map<*, *> ?: return Rating.None
    return Rating((m["avg"] as? Number)?.toDouble() ?: 0.0, m["count"].asInt())
}

/** "#RRGGBB" → 0xFFRRGGBB */
fun parseHexColor(value: Any?): Long? {
    val text = (value as? String)?.removePrefix("#") ?: return null
    if (text.length != 6) return null
    return text.toLongOrNull(16)?.let { 0xFF000000L or it }
}

fun formatHexColor(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)

fun DocumentSnapshot.toSalon(): Salon {
    val geo = get("geo") as? GeoPoint
    return Salon(
        id = id,
        name = getString("name").orEmpty(),
        slug = getString("slug").orEmpty(),
        description = getString("description").orEmpty(),
        address = getString("address").orEmpty(),
        city = getString("city").orEmpty(),
        location = geo?.let { GeoLocation(it.latitude, it.longitude) },
        phone = getString("phone").orEmpty(),
        photos = get("photos").asStrings(),
        coverPhoto = getString("coverPhoto"),
        accentColor = parseHexColor(get("accentColor")),
        rating = parseRating(get("rating")),
        priceLevel = get("priceLevel").asInt(2),
        workingHours = parseHours(get("workingHours")),
        rules = parseRules(get("rules")),
        ownerUid = getString("ownerUid"),
        published = getBoolean("published") == true,
        categories = get("categories").asStrings().map(ServiceCategory::from).toSet(),
        minPrice = (get("minPrice") as? Number)?.toInt(),
        nextFreeAt = get("nextFreeAt").asInstant(),
    )
}

fun DocumentSnapshot.toService(): Service = Service(
    id = id,
    category = ServiceCategory.from(getString("category")),
    name = getString("name").orEmpty(),
    description = getString("description").orEmpty(),
    durationMin = get("durationMin").asInt(30),
    price = get("price").asInt(),
    stylistIds = get("stylistIds").asStrings(),
    active = getBoolean("active") != false,
    order = get("order").asInt(),
)

fun DocumentSnapshot.toStylist(salonId: String): Stylist {
    val integrations = (get("integrations") as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
        val provider = Provider.from(key as? String ?: "") ?: return@mapNotNull null
        val m = value as? Map<*, *> ?: return@mapNotNull null
        provider to IntegrationState(
            connected = m["connected"] == true,
            enabled = m["enabled"] == true,
            accountLabel = m["accountLabel"].asString(),
        )
    }.toMap()
    return Stylist(
        id = id,
        salonId = salonId,
        name = getString("name").orEmpty(),
        photoPath = getString("photoPath"),
        bio = getString("bio").orEmpty(),
        specialties = get("specialties").asStrings(),
        portfolio = get("portfolio").asStrings(),
        uid = getString("uid"),
        workingHours = get("workingHours")?.let(::parseHours),
        breaks = parseHours(get("breaks")),
        active = getBoolean("active") != false,
        order = get("order").asInt(),
        color = parseHexColor(get("color")),
        rating = parseRating(get("rating")),
        nextFreeAt = get("nextFreeAt").asInstant(),
        integrations = integrations,
    )
}

fun DocumentSnapshot.toReview(): Review {
    val reply = get("reply") as? Map<*, *>
    return Review(
        id = id,
        authorName = getString("authorName").orEmpty(),
        stylistName = getString("stylistName").orEmpty(),
        rating = get("rating").asInt(5),
        text = getString("text").orEmpty(),
        reply = reply?.get("text") as? String,
        createdAt = get("createdAt").asInstant() ?: Instant.EPOCH,
    )
}

fun DocumentSnapshot.toAppointment(): Appointment {
    val start = get("start").asInstant() ?: Instant.EPOCH
    val end = get("end").asInstant() ?: start
    return Appointment(
        id = id,
        salonId = getString("salonId").orEmpty(),
        salonName = getString("salonName").orEmpty(),
        salonPhone = getString("salonPhone").orEmpty(),
        salonAddress = getString("salonAddress").orEmpty(),
        stylistId = getString("stylistId").orEmpty(),
        stylistName = getString("stylistName").orEmpty(),
        customerUid = getString("customerUid"),
        customerName = getString("customerName").orEmpty(),
        customerPhone = getString("customerPhone"),
        services = (get("services") as? List<*>).orEmpty().mapNotNull { s ->
            val m = s as? Map<*, *> ?: return@mapNotNull null
            BookedService(m["id"].asString(), m["name"].asString(), m["price"].asInt(), m["durationMin"].asInt())
        },
        start = start,
        end = end,
        durationMin = get("durationMin").asInt(),
        totalPrice = get("totalPrice").asInt(),
        status = AppointmentStatus.from(getString("status")),
        holdExpiresAt = get("holdExpiresAt").asInstant(),
        note = getString("note").orEmpty(),
        source = AppointmentSource.from(getString("source")),
        anyStylist = getBoolean("anyStylist") == true,
        reviewed = getBoolean("reviewed") == true,
        dateKey = getString("dateKey").orEmpty(),
    )
}

fun DocumentSnapshot.toUserProfile(): UserProfile = UserProfile(
    uid = id,
    name = getString("name").orEmpty(),
    phone = getString("phone"),
    email = getString("email"),
    photoPath = getString("photoPath"),
    roles = parseRoles(get("roles").asStrings()),
    salonId = getString("salonId"),
    stylistId = getString("stylistId"),
    favoriteSalonIds = get("favoriteSalonIds").asStrings().toSet(),
    favoriteStylistKeys = get("favoriteStylistKeys").asStrings().toSet(),
    language = getString("language") ?: "sr",
    notificationsEnabled = getBoolean("notificationsEnabled") != false,
)

fun parseRoles(values: List<String>): Set<Role> = buildSet {
    add(Role.Customer)
    if ("owner" in values) add(Role.Owner)
    if ("stylist" in values) add(Role.Stylist)
}

fun DocumentSnapshot.toInboxMessage(): InboxMessage {
    val data = get("data") as? Map<*, *>
    return InboxMessage(
        id = id,
        type = getString("type").orEmpty(),
        title = getString("title").orEmpty(),
        body = getString("body").orEmpty(),
        read = getBoolean("read") == true,
        createdAt = get("createdAt").asInstant() ?: Instant.now(),
        appointmentId = data?.get("appointmentId") as? String,
        salonId = data?.get("salonId") as? String,
        dateKey = data?.get("dateKey") as? String,
        serviceIds = data?.get("serviceIds").asStrings(),
        stylistId = data?.get("stylistId") as? String,
    )
}

fun DocumentSnapshot.toWaitlistEntry(): WaitlistEntry = WaitlistEntry(
    id = id,
    salonId = getString("salonId").orEmpty(),
    salonName = getString("salonName").orEmpty(),
    date = runCatching { LocalDate.parse(getString("dateKey")) }.getOrDefault(LocalDate.MIN),
    serviceNames = get("serviceNames").asStrings(),
    status = getString("status").orEmpty(),
)

fun DocumentSnapshot.toTimeOff(stylistId: String): TimeOff = TimeOff(
    id = id,
    stylistId = stylistId,
    start = get("start").asInstant() ?: Instant.EPOCH,
    end = get("end").asInstant() ?: Instant.EPOCH,
    kind = TimeOffKind.from(getString("kind")),
    reason = getString("reason").orEmpty(),
)
