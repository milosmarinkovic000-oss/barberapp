package rs.termin.app.core.data.integrations

import rs.termin.app.core.data.firebase.FunctionsClient
import rs.termin.app.core.domain.model.Provider
import rs.termin.app.core.domain.repository.IntegrationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FunctionsIntegrationRepository @Inject constructor(private val client: FunctionsClient) : IntegrationRepository {

    override suspend fun serverStatus(): Map<Provider, Boolean> {
        val result = client.callWithRetry("getIntegrationStatus", emptyMap())
        return Provider.entries.associateWith { result[it.key] == true }
    }

    override suspend fun startOAuth(provider: Provider, salonId: String, stylistId: String): String {
        val result = client.call("startIntegrationOAuth", mapOf("provider" to provider.key, "salonId" to salonId, "stylistId" to stylistId))
        return result["url"] as String
    }

    override suspend fun setEnabled(provider: Provider, salonId: String, stylistId: String, enabled: Boolean) {
        client.call(
            "setIntegrationEnabled",
            mapOf("provider" to provider.key, "salonId" to salonId, "stylistId" to stylistId, "enabled" to enabled),
        )
    }

    override suspend fun disconnect(provider: Provider, salonId: String, stylistId: String) {
        client.call("disconnectIntegration", mapOf("provider" to provider.key, "salonId" to salonId, "stylistId" to stylistId))
    }
}
