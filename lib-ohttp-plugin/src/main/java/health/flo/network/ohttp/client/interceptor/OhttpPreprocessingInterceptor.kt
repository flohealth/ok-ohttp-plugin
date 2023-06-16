package health.flo.network.ohttp.client.interceptor

import health.flo.network.ohttp.client.HttpHeaders.FORCE_OHTTP_ENABLED_HEADER
import health.flo.network.ohttp.client.interceptor.RefreshConfigHeadersHelper.addRefreshConfigHeader
import health.flo.network.ohttp.client.interceptor.RefreshConfigHeadersHelper.getStaleConfigErrorValue
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

internal class OhttpPreprocessingInterceptor internal constructor(
    private val ohttpWrappingRequiredAnalyser: OhttpWrappingRequiredAnalyser,
    private val relayUrl: HttpUrl,
    private val cacheLayerPacker: CacheLayerPacker,
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val ohttpWrappingRequired = isOhttpWrappingRequired(originalRequest)
        val cleanedRequest = removeLocalOhttpHeaders(originalRequest)

        val handledResponse: Response = if (ohttpWrappingRequired) {
            val relayRequest = cacheLayerPacker.packRequest(
                request = cleanedRequest,
                relayUrl = relayUrl,
                originalUrl = originalRequest.url,
            )

            val chainResponse = chain.proceed(relayRequest)

            val response = when (chainResponse.code) {
                HTTP_UNAUTHORIZED -> {
                    val staleConfigHash = getStaleConfigErrorValue(chainResponse)
                    if (staleConfigHash != null) {
                        val recoverRequest = addRefreshConfigHeader(relayRequest, staleConfigHash)

                        chainResponse.body?.close()
                        chain.proceed(recoverRequest)
                    } else {
                        chainResponse
                    }
                }
                else -> chainResponse
            }

            response.let(cacheLayerPacker::unpackResponse)
                .newBuilder()
                .request(originalRequest)
                .build()
        } else {
            chain.proceed(cleanedRequest)
        }

        return handledResponse
    }

    private fun isOhttpWrappingRequired(request: Request): Boolean {
        return ohttpWrappingRequiredAnalyser.isOhttpRequired(request)
    }

    private fun removeLocalOhttpHeaders(request: Request): Request {
        return request.newBuilder()
            .removeHeader(FORCE_OHTTP_ENABLED_HEADER)
            .build()
    }
}
