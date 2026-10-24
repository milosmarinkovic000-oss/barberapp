package rs.termin.app.core.data.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rs.termin.app.core.domain.repository.ConnectivityMonitor
import javax.inject.Inject
import javax.inject.Singleton

/** Prati da li uređaj ima mrežu (za stanje „bez interneta“ na ekranima). */
@Singleton
class AndroidConnectivityMonitor @Inject constructor(
    @ApplicationContext context: Context,
) : ConnectivityMonitor {

    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val online = MutableStateFlow(currentlyOnline())
    override val isOnline: StateFlow<Boolean> = online

    init {
        manager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                online.value = true
            }

            override fun onLost(network: Network) {
                online.value = currentlyOnline()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        })
    }

    private fun currentlyOnline(): Boolean {
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
