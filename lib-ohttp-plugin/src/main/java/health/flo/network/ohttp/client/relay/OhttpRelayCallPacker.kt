package health.flo.network.ohttp.client.relay

import health.flo.network.ohttp.client.HttpHeaders.CONTENT_TYPE_HEADER
import health.flo.network.ohttp.client.HttpHeaders.OHTTP_REQUEST_CONTENT_TYPE_HEADER_VALUE
import health.flo.network.ohttp.client.HttpHeaders.OHTTP_RESPONSE_CONTENT_TYPE_HEADER_VALUE
import health.flo.network.ohttp.client.config.OhttpCryptoConfig
import health.flo.network.ohttp.client.crypto.OhttpEncryptedResponse
import health.flo.network.ohttp.client.crypto.OhttpRelayCallEncryptor
import health.flo.network.ohttp.client.crypto.OhttpRelayCallEncryptor.OhttpRelayCallDecryptor
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

internal class OhttpRelayCallPacker(
    private val relayUrl: HttpUrl,
    private val encryptor: OhttpRelayCallEncryptor,
) {

    fun wrapToOhttp(request: Request, cryptoConfig: OhttpCryptoConfig): Pair<Request, OhttpRelayCallUnpacker> {
        val encapsulationResult = encryptor.encrypt(request, cryptoConfig)

        val requestEncapsulated = Request.Builder()
            .url(relayUrl)
            .addHeader(
                name = CONTENT_TYPE_HEADER,
                value = OHTTP_REQUEST_CONTENT_TYPE_HEADER_VALUE,
            )
            .post(encapsulationResult.data.bytes.toRequestBody())
            .build()

        return requestEncapsulated to OhttpRelayCallUnpacker(encapsulationResult.decryptor)
    }
}

internal class OhttpRelayCallUnpacker(
    private val decryptor: OhttpRelayCallDecryptor,
) {

    fun unwrapFromOhttp(response: Response): Response {
        val responseBody = response.body
            ?: throw IOException("Empty OHTTP response body")

        val headers = response.headers(CONTENT_TYPE_HEADER)
        val isOhttpResponse = headers.contains(OHTTP_RESPONSE_CONTENT_TYPE_HEADER_VALUE)

        if (!isOhttpResponse) {
            throw IOException("$OHTTP_RESPONSE_CONTENT_TYPE_HEADER_VALUE $CONTENT_TYPE_HEADER header not found!")
        }

        val encryptedResponse = OhttpEncryptedResponse(
            protocol = response.protocol,
            data = responseBody.bytes(),
        )

        return decryptor.decrypt(encryptedResponse)
    }

    fun dispose() = decryptor.dispose()
}
