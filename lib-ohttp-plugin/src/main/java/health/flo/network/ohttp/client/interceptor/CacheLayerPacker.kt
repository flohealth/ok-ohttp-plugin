package health.flo.network.ohttp.client.interceptor

import health.flo.network.ohttp.client.HttpHeaders.HOST_HEADER
import health.flo.network.ohttp.client.HttpHeaders.OHTTP_ORIGINAL_URL_HEADER
import health.flo.network.ohttp.client.utils.UrlEncoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.toHostHeader

internal class CacheLayerPacker(
    private val urlEncoder: UrlEncoder,
) {

    fun packRequest(request: Request, relayUrl: HttpUrl, originalUrl: HttpUrl): Request {
        val urlForCaching = createUrlForCaching(originalUrl, relayUrl)

        return request.newBuilder()
            .addHeader(OHTTP_ORIGINAL_URL_HEADER, originalUrl.toString())
            .url(urlForCaching)
            .build()
    }

    private fun createUrlForCaching(originalUrl: HttpUrl, relayUrl: HttpUrl): HttpUrl {
        val path = "/" + urlEncoder.encodeUrlSafe(originalUrl.toString())

        return relayUrl.newBuilder()
            .encodedPath(path)
            .build()
    }

    fun isPackedRequest(preprocessedRequest: Request): IsOhttpPacked {
        val originalUrl: String? = getOriginalUrl(preprocessedRequest)

        return if (originalUrl != null) IsOhttpPacked.Yes(originalUrl = originalUrl) else IsOhttpPacked.No
    }

    private fun getOriginalUrl(preprocessedRequest: Request): String? {
        return preprocessedRequest.header(OHTTP_ORIGINAL_URL_HEADER)
    }

    fun restoreUserRequest(request: Request, originalUrl: String): Request {
        return request.newBuilder()
            // restoring original Request URL instead of Relay URL with hash
            .url(originalUrl)
            .removeHeader(OHTTP_ORIGINAL_URL_HEADER)
            .run {
                if (request.header(HOST_HEADER) != null) {
                    this
                        .removeHeader(HOST_HEADER)
                        .addHeader(HOST_HEADER, originalUrl.toHttpUrl().toHostHeader())
                } else {
                    this
                }
            }
            .build()
    }

    fun packResponse(builder: Response.Builder, originalUrl: String): Response.Builder {
        return builder.addHeader(OHTTP_ORIGINAL_URL_HEADER, originalUrl)
    }

    fun unpackResponse(response: Response): Response {
        return response
            .removeHeaderIfExist(headerName = OHTTP_ORIGINAL_URL_HEADER)
    }

    sealed interface IsOhttpPacked {

        data class Yes(
            val originalUrl: String,
        ) : IsOhttpPacked

        object No : IsOhttpPacked
    }
}

private fun Response.removeHeaderIfExist(headerName: String): Response {
    val hasHeader = this.header(headerName, null) != null

    return if (!hasHeader) {
        this
    } else {
        this.newBuilder()
            .removeHeader(headerName)
            .build()
    }
}
