package rs.termin.app.core.domain

import rs.termin.app.core.domain.model.Role
import rs.termin.app.core.domain.repository.AuthState

/** Prijavljeni vlasnik ili frizer i salon kojim upravlja. */
data class StaffSession(val uid: String, val salonId: String, val stylistId: String?, val isOwner: Boolean) {
    /** Vlasnik vidi ceo salon; frizer samo svoje termine. */
    val stylistFilter: String? get() = if (isOwner) null else stylistId

    fun canManageStylist(id: String): Boolean = isOwner || id == stylistId
}

fun AuthState.staffSession(): StaffSession? {
    val signedIn = this as? AuthState.SignedIn ?: return null
    val salonId = signedIn.salonId ?: return null
    val owner = Role.Owner in signedIn.roles
    if (!owner && Role.Stylist !in signedIn.roles) return null
    return StaffSession(signedIn.uid, salonId, signedIn.stylistId, owner)
}
