package rs.termin.app.feature.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.core.domain.canCustomerChange
import rs.termin.app.core.domain.canReview
import rs.termin.app.core.domain.cancellationDeadline
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.repository.AppointmentRepository
import rs.termin.app.core.domain.repository.BookingRepository
import rs.termin.app.core.domain.repository.ConnectivityMonitor
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.usecase.CancelAppointmentUseCase
import rs.termin.app.core.domain.usecase.CancelResult
import rs.termin.app.core.ui.Loadable
import java.time.Clock
import java.time.Instant

data class AppointmentDetail(val appointment: Appointment, val salon: Salon?)

data class AppointmentDetailUiState(
    val detail: Loadable<AppointmentDetail?> = Loadable.Loading,
    val now: Instant = Instant.EPOCH,
    val confirmCancel: Boolean = false,
    val callSalonPhone: String? = null,
    val canceling: Boolean = false,
    val canceled: Boolean = false,
    val reviewRating: Int = 0,
    val reviewText: String = "",
    val submittingReview: Boolean = false,
    val reviewSent: Boolean = false,
    val error: Throwable? = null,
    val online: Boolean = true,
) {
    val canChange: Boolean
        get() {
            val d = detail.valueOrNull() ?: return false
            val salon = d.salon ?: return false
            return canCustomerChange(d.appointment, salon.rules, now)
        }
    val deadline: Instant?
        get() {
            val d = detail.valueOrNull() ?: return null
            val salon = d.salon ?: return null
            return cancellationDeadline(d.appointment.start, salon.rules)
        }
    val canReview: Boolean get() = detail.valueOrNull()?.appointment?.let(::canReview) == true && !reviewSent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = AppointmentDetailViewModel.Factory::class)
class AppointmentDetailViewModel @AssistedInject constructor(
    @Assisted private val appointmentId: String,
    appointments: AppointmentRepository,
    salons: SalonRepository,
    private val booking: BookingRepository,
    private val cancelAppointment: CancelAppointmentUseCase,
    connectivity: ConnectivityMonitor,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(appointmentId: String): AppointmentDetailViewModel
    }

    private val local = MutableStateFlow(AppointmentDetailUiState(now = clock.instant()))

    private val retry = MutableStateFlow(0)

    private val detail = retry.flatMapLatest { appointments.observeAppointment(appointmentId) }.flatMapLatest { appointment ->
        if (appointment == null) {
            flowOf<Loadable<AppointmentDetail?>>(Loadable.Ready(null))
        } else {
            salons.observeSalon(appointment.salonId)
                .catch { emit(null) }
                .map { salon -> Loadable.Ready(AppointmentDetail(appointment, salon)) }
        }
    }.onStart { emit(Loadable.Loading) }.catch { emit(Loadable.Failed(it)) }

    val uiState: StateFlow<AppointmentDetailUiState> = combine(local, detail, connectivity.isOnline) { s, d, online ->
        s.copy(detail = d, online = online, now = clock.instant())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), local.value)

    fun retry() = retry.update { it + 1 }

    fun requestCancel() {
        val state = uiState.value
        if (!state.canChange) {
            local.update { it.copy(callSalonPhone = state.detail.valueOrNull()?.appointment?.salonPhone) }
        } else {
            local.update { it.copy(confirmCancel = true) }
        }
    }

    fun dismissDialogs() = local.update { it.copy(confirmCancel = false, callSalonPhone = null) }

    fun cancel() {
        val detail = uiState.value.detail.valueOrNull() ?: return
        val salon = detail.salon ?: return
        local.update { it.copy(confirmCancel = false, canceling = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = cancelAppointment(detail.appointment, salon.rules)) {
                    CancelResult.Canceled -> local.update { it.copy(canceled = true) }
                    is CancelResult.CallSalon -> local.update { it.copy(callSalonPhone = result.phone) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = e) }
            } finally {
                local.update { it.copy(canceling = false) }
            }
        }
    }

    fun setRating(rating: Int) = local.update { it.copy(reviewRating = rating) }

    fun setReviewText(text: String) = local.update { it.copy(reviewText = text.take(MAX_REVIEW)) }

    fun submitReview() {
        val state = uiState.value
        if (state.reviewRating !in 1..5 || state.submittingReview) return
        local.update { it.copy(submittingReview = true, error = null) }
        viewModelScope.launch {
            try {
                booking.submitReview(appointmentId, state.reviewRating, state.reviewText.trim())
                local.update { it.copy(reviewSent = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = e) }
            } finally {
                local.update { it.copy(submittingReview = false) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val MAX_REVIEW = 1000
    }
}
