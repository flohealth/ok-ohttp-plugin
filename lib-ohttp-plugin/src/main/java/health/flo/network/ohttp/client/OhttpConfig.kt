package health.flo.network.ohttp.client

import okhttp3.Cache
import okhttp3.HttpUrl
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class OhttpConfig(
    val relayUrl: HttpUrl,
    val configServerConfig: ConfigServerConfig,
    val userAgent: String,
) {

    data class ConfigServerConfig(
        val configUrl: HttpUrl,
        val userAgent: String? = null,
        val configCache: Cache?,
        val connectTimeout: Duration = 60.seconds,
        val readTimeout: Duration = 60.seconds,
        val writeTimeout: Duration = 60.seconds,
        val retryOnConnectionFailure: Boolean = true,
    )
}
