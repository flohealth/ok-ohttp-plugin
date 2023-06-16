package health.flo.network.ohttp.client.interceptor

import health.flo.network.ohttp.client.HttpHeaders.FORCE_OHTTP_ENABLED_HEADER
import health.flo.network.ohttp.client.IsOhttpEnabledProvider
import okhttp3.Request
import java.io.IOException

internal class OhttpWrappingRequiredAnalyser constructor(
    private val isOhttpEnabled: IsOhttpEnabledProvider,
) {

    fun isOhttpRequired(request: Request): Boolean {
        val forceHeaderValue = request.header(FORCE_OHTTP_ENABLED_HEADER)
        return if (forceHeaderValue != null) {
            forceHeaderValue.toBooleanStrictOrNull()
                ?: throw IOException("Illegal value in $FORCE_OHTTP_ENABLED_HEADER header")
        } else {
            isOhttpEnabled.isEnabledBlocking()
        }
    }
}
