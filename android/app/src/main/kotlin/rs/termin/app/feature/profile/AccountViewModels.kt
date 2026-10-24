package rs.termin.app.feature.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.core.domain.PhoneNumbers
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.model.UserProfile
import rs.termin.app.core.domain.repository.LocationProvider
import rs.termin.app.core.domain.repository.UserRepository
import rs.termin.app.core.ui.CityCenters
import javax.inject.Inject

private suspend fun <T> attempt(onError: (Exception) -> Unit, block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    onError(e)
    null
}

// ---------------- Izmena profila ----------------

data class EditProfileUiState(
    val profile: UserProfile? = null,
    val name: String = "",
    val saving: Boolean = false,
    val uploading: Boolean = false,
    val saved: Boolean = false,
    val error: Throwable? = null,
) {
    val canSave: Boolean get() = name.trim().length >= 2 && name.trim() != profile?.name && !saving
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(private val users: UserRepository) : ViewModel() {
    private val local = MutableStateFlow(EditProfileUiState())

    val uiState: StateFlow<EditProfileUiState> = combine(local, users.observeProfile().catch { emit(null) }) { s, profile ->
        s.copy(profile = profile)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), local.value)

    init {
        viewModelScope.launch {
            val profile = users.observeProfile().catch { emit(null) }.filterNotNull().first()
            local.update { if (it.name.isEmpty()) it.copy(name = profile.name) else it }
        }
    }

    fun onName(value: String) = local.update { it.copy(name = value.take(MAX_NAME), saved = false) }

    fun save() {
        if (!uiState.value.canSave) return
        local.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            attempt({ e -> local.update { it.copy(error = e) } }) {
                users.updateName(local.value.name.trim())
                local.update { it.copy(saved = true) }
            }
            local.update { it.copy(saving = false) }
        }
    }

    fun onPhoto(uri: Uri) {
        local.update { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            attempt({ e -> local.update { it.copy(error = e) } }) { users.updatePhoto(uri) }
            local.update { it.copy(uploading = false) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val MAX_NAME = 60
    }
}

// ---------------- Brisanje naloga ----------------

data class DeleteAccountUiState(
    val profile: UserProfile? = null,
    val confirmation: String = "",
    val deleting: Boolean = false,
    val error: Throwable? = null,
)

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(private val users: UserRepository) : ViewModel() {
    private val local = MutableStateFlow(DeleteAccountUiState())

    val uiState: StateFlow<DeleteAccountUiState> = combine(local, users.observeProfile().catch { emit(null) }) { s, profile ->
        s.copy(profile = profile)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), local.value)

    fun onConfirmation(value: String) = local.update { it.copy(confirmation = value.take(MAX_CONFIRM)) }

    /** Briše nalog na serveru; posle toga se korisnik odjavljuje i aplikacija prelazi na prijavu. */
    fun delete(expectedWord: String) {
        val state = local.value
        if (state.deleting || !state.confirmation.trim().equals(expectedWord, ignoreCase = true)) return
        local.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            attempt({ e -> local.update { it.copy(error = e) } }) { users.deleteAccount() }
            local.update { it.copy(deleting = false) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val MAX_CONFIRM = 20
    }
}

// ---------------- Novi salon ----------------

data class CreateSalonUiState(
    val name: String = "",
    val city: String = DEFAULT_CITY,
    val address: String = "",
    val phone: String = "",
    val location: GeoLocation = CityCenters.Default,
    /** Korisnik je sam postavio pin ili koristio svoju lokaciju (grad ga više ne pomera). */
    val locationChosen: Boolean = false,
    val locating: Boolean = false,
    val submitting: Boolean = false,
    val showErrors: Boolean = false,
    val createdSalonId: String? = null,
    val error: Throwable? = null,
) {
    val nameValid: Boolean get() = name.trim().length >= 2
    val addressValid: Boolean get() = address.trim().length >= 3
    val phoneE164: String? get() = PhoneNumbers.toE164(phone)
    val valid: Boolean get() = nameValid && addressValid && city.isNotBlank() && phoneE164 != null

    companion object {
        const val DEFAULT_CITY = "Beograd"
    }
}

@HiltViewModel
class CreateSalonViewModel @Inject constructor(
    private val users: UserRepository,
    private val location: LocationProvider,
) : ViewModel() {
    private val state = MutableStateFlow(CreateSalonUiState())
    val uiState: StateFlow<CreateSalonUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val phone = users.observeProfile().catch { emit(null) }.first()?.phone
            if (phone != null) state.update { if (it.phone.isEmpty()) it.copy(phone = PhoneNumbers.display(phone)) else it }
        }
    }

    fun onName(value: String) = state.update { it.copy(name = value.take(MAX_FIELD)) }

    fun onCity(value: String) = state.update { s ->
        val center = CityCenters.forCity(value)
        s.copy(city = value.take(MAX_FIELD), location = if (!s.locationChosen && center != null) center else s.location)
    }

    fun onAddress(value: String) = state.update { it.copy(address = value.take(MAX_FIELD)) }

    fun onPhone(value: String) = state.update { it.copy(phone = value.take(MAX_PHONE)) }

    fun onLocation(value: GeoLocation) = state.update { it.copy(location = value, locationChosen = true) }

    fun useMyLocation() {
        state.update { it.copy(locating = true) }
        viewModelScope.launch {
            val current = attempt({ e -> state.update { it.copy(error = e) } }) { location.currentLocation() }
            state.update { s -> if (current != null) s.copy(location = current, locationChosen = true, locating = false) else s.copy(locating = false) }
        }
    }

    fun submit() {
        val s = state.value
        if (!s.valid) {
            state.update { it.copy(showErrors = true) }
            return
        }
        val phone = s.phoneE164 ?: return
        state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val id = attempt({ e -> state.update { it.copy(error = e) } }) {
                users.createSalon(s.name.trim(), s.city.trim(), s.address.trim(), phone, s.location)
            }
            state.update { it.copy(submitting = false, createdSalonId = id) }
        }
    }

    private companion object {
        const val MAX_FIELD = 80
        const val MAX_PHONE = 20
    }
}
