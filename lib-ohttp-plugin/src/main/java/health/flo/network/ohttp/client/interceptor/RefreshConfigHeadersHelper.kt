package health.flo.network.ohttp.client.interceptor

import okhttp3.Request
import okhttp3.Response
import health.flo.network.ohttp.client.HttpHeaders.OHTTP_ERROR_STALE_CONFIG_HEADER as ERROR_STALE_CONFIG
import health.flo.network.ohttp.client.HttpHeaders.OHTTP_REFRESH_STALE_CONFIG_HEADER as REFRESH_STALE_CONFIG

object RefreshConfigHeadersHelper {

    fun addStaleConfigErrorHeader(
        ohttpResponse: Response,
        configHash: Int,
    ): Response {
        return ohttpResponse.newBuilder()
            .addHeader(ERROR_STALE_CONFIG, configHash.toString())
            .build()
    }

    fun getStaleConfigErrorValue(response: Response): String? {
        return response.header(ERROR_STALE_CONFIG, null)
    }

    fun addRefreshConfigHeader(request: Request, staleConfigHash: String): Request {
        return request.newBuilder()
            .addHeader(REFRESH_STALE_CONFIG, staleConfigHash)
            .build()
    }

    fun getRefreshConfigHash(request: Request): Int? {
        return request.header(REFRESH_STALE_CONFIG)?.toIntOrNull()
    }

    fun removeRefreshConfigHeader(request: Request): Request {
        return request.newBuilder()
            .removeHeader(REFRESH_STALE_CONFIG)
            .build()
    }
}
