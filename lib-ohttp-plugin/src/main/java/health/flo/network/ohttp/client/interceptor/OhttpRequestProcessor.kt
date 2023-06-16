package health.flo.network.ohttp.client.interceptor

import health.flo.network.ohttp.client.HttpHeaders.CONNECTION_HEADER
import health.flo.network.ohttp.client.HttpHeaders.CONNECTION_HEADER_VALUE_KEEP_ALIVE
import health.flo.network.ohttp.client.HttpHeaders.CONTENT_LENGTH_HEADER
import health.flo.network.ohttp.client.HttpHeaders.HOST_HEADER
import health.flo.network.ohttp.client.HttpHeaders.USER_AGENT_HEADER
import health.flo.network.ohttp.client.config.OhttpCryptoConfig
import health.flo.network.ohttp.client.config.OhttpCryptoConfigProvider
import health.flo.network.ohttp.client.interceptor.RefreshConfigHeadersHelper.addStaleConfigErrorHeader
import health.flo.network.ohttp.client.interceptor.RefreshConfigHeadersHelper.getRefreshConfigHash
import health.flo.network.ohttp.client.interceptor.RefreshConfigHeadersHelper.removeRefreshConfigHeader
import health.flo.network.ohttp.client.relay.OhttpRelayCallPacker
import health.flo.network.ohttp.client.relay.OhttpRelayCallUnpacker
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.toHostHeader
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

internal class OhttpRequestProcessor constructor(
    private val userAgent: String,
    private val ohttpRelayRequestPacker: OhttpRelayCallPacker,
    private val cryptoConfigProvider: OhttpCryptoConfigProvider,
) {

    fun process(request: Request, chain: Interceptor.Chain): Response {
        val cryptoConfig = getCryptoConfig(request)
        val cleanedRequest = removeRefreshConfigHeader(request)
        val unwrapFromOhttp = processWithCryptoConfig(cleanedRequest, cryptoConfig, chain)

        return unwrapFromOhttp
    }

    private fun getCryptoConfig(request: Request): OhttpCryptoConfig {
        val refreshConfigHash = getRefreshConfigHash(request)
        val cryptoConfig = if (refreshConfigHash == null) {
            cryptoConfigProvider.getConfig()
        } else {
            cryptoConfigProvider.exchangeStaleConfig(refreshConfigHash)
        }

        return cryptoConfig
    }

    private fun processWithCryptoConfig(
        request: Request,
        cryptoConfig: OhttpCryptoConfig,
        chain: Interceptor.Chain,
    ): Response {
        val (
            ohttpRequest: Request,
            unpacker: OhttpRelayCallUnpacker,
        ) = ohttpRelayRequestPacker.wrapToOhttp(request, cryptoConfig)

        val enrichedOhttpRequest = enrichWithNetworkHeaders(ohttpRequest, request)

        val unwrapFromOhttp = try {
            val ohttpResponse: Response = chain.proceed(enrichedOhttpRequest)
            when (ohttpResponse.code) {
                HTTP_OK -> {
                    unpacker.unwrapFromOhttp(ohttpResponse)
                        .newBuilder()
                        .replicateTimeAttributes(ohttpResponse)
                        .replicateHandshake(ohttpResponse)
                        .build()
                }
                HTTP_UNAUTHORIZED -> {
                    addStaleConfigErrorHeader(ohttpResponse, cryptoConfig.hashCode())
                }
                else -> ohttpResponse
            }
        } catch (error: Throwable) {
            unpacker.dispose()

            throw error
        }
        return unwrapFromOhttp
    }

    private fun enrichWithNetworkHeaders(ohttpRequest: Request, request: Request): Request {
        return ohttpRequest.newBuilder()
            .run {
                if (ohttpRequest.header(HOST_HEADER) != null) {
                    this
                } else {
                    this.addHeader(HOST_HEADER, ohttpRequest.url.toHostHeader())
                }
            }
            .run {
                if (ohttpRequest.header(CONNECTION_HEADER) != null) {
                    this
                } else {
                    val connection = request.header(CONNECTION_HEADER) ?: CONNECTION_HEADER_VALUE_KEEP_ALIVE
                    this.addHeader(CONNECTION_HEADER, connection)
                }
            }
            .run {
                if (ohttpRequest.header(CONTENT_LENGTH_HEADER) != null) {
                    this
                } else {
                    val contentLength = ohttpRequest.body?.contentLength()?.toString()
                    if (contentLength == null) this else this.addHeader(CONTENT_LENGTH_HEADER, contentLength)
                }
            }
            .run {
                removeHeader(USER_AGENT_HEADER)
                addHeader(USER_AGENT_HEADER, userAgent)
            }
            .build()
    }
}

private fun Response.Builder.replicateTimeAttributes(ohttpResponse: Response): Response.Builder {
    return this.receivedResponseAtMillis(ohttpResponse.receivedResponseAtMillis)
        .sentRequestAtMillis(ohttpResponse.sentRequestAtMillis)
}

private fun Response.Builder.replicateHandshake(ohttpResponse: Response): Response.Builder {
    return this.handshake(ohttpResponse.handshake)
}
