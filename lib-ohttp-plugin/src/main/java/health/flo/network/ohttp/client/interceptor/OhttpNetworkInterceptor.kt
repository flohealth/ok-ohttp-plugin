package health.flo.network.ohttp.client.interceptor

import health.flo.network.ohttp.client.interceptor.CacheLayerPacker.IsOhttpPacked
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

internal class OhttpNetworkInterceptor internal constructor(
    private val cacheLayerPacker: CacheLayerPacker,
    private val ohttpRequestProcessor: OhttpRequestProcessor,
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val cachedRequest = chain.request()

        val isOhttp: IsOhttpPacked = cacheLayerPacker.isPackedRequest(cachedRequest)

        return when (isOhttp) {
            is IsOhttpPacked.No -> {
                proceedWithPlainRequest(chain, cachedRequest)
            }

            is IsOhttpPacked.Yes -> {
                try {
                    proceedWithOhttpRequest(cachedRequest, isOhttp.originalUrl, chain)
                } catch (error: IOException) {
                    throw error
                } catch (error: Throwable) {
                    // non-IOExceptions throwing will lead to FATAL crashes
                    throw IOException(error)
                }
            }
        }
    }

    private fun proceedWithPlainRequest(
        chain: Interceptor.Chain,
        request: Request,
    ): Response =
        chain.proceed(request)

    private fun proceedWithOhttpRequest(
        cachedRequest: Request,
        userRequestUrl: String,
        chain: Interceptor.Chain,
    ): Response {
        val userRequest = cacheLayerPacker.restoreUserRequest(cachedRequest, userRequestUrl)
        val response: Response = ohttpRequestProcessor.process(userRequest, chain)
        val responseReadyForCaching = response.newBuilder()
            .request(cachedRequest)
            .run { cacheLayerPacker.packResponse(builder = this, originalUrl = userRequestUrl) }
            .build()

        return responseReadyForCaching
    }
}
