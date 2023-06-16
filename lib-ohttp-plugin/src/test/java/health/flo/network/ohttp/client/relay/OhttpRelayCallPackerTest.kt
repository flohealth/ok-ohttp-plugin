package health.flo.network.ohttp.client.relay

import health.flo.network.ohttp.client.config.OhttpCryptoConfig
import health.flo.network.ohttp.client.crypto.EncryptionResult
import health.flo.network.ohttp.client.crypto.OhttpEncryptedRequest
import health.flo.network.ohttp.client.crypto.OhttpEncryptedResponse
import health.flo.network.ohttp.client.crypto.OhttpRelayCallEncryptor
import health.flo.network.ohttp.client.isRequestsParamsExceptBodyEqual
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.IOException

private const val CONTENT_TYPE_HEADER = "Content-Type"
private const val OHTTP_REQUEST_CONTENT_TYPE_HEADER_VALUE = "message/ohttp-req"
private const val OHTTP_RESPONSE_CONTENT_TYPE_HEADER_VALUE: String = "message/ohttp-res"

internal class OhttpRelayCallPackerTest {

    private val cryptoConfig = OhttpCryptoConfig(byteArrayOf(42, 42))

    private val relayUrl: HttpUrl = "https://example.com".toHttpUrl()

    private val request = Request.Builder()
        .url("https://example.com")
        .build()

    private val stubResponse = Response.Builder()
        .request(request)
        .protocol(Protocol.QUIC)
        .code(4242)
        .message("test message")
        .build()

    private val decryptedResponse = Response.Builder()
        .request(request)
        .protocol(Protocol.QUIC)
        .code(4242)
        .message("test message")
        .body("Decrypted Response".toResponseBody())
        .build()

    private val decryptor: OhttpRelayCallEncryptor.OhttpRelayCallDecryptor = mock {
        on { this.decrypt(any()) } doReturn decryptedResponse
    }

    private val encryptedRequest = OhttpEncryptedRequest(byteArrayOf(42))
    private val encryptor: OhttpRelayCallEncryptor = mock {
        on { this.encrypt(request, cryptoConfig) } doReturn EncryptionResult(encryptedRequest, decryptor)
    }

    private val sut = OhttpRelayCallPacker(
        relayUrl,
        encryptor,
    )

    @Test
    fun `wrapToOhttp should return wrapped request and decryptor`() {
        val expectedRequest = Request.Builder()
            .url(relayUrl)
            .addHeader(
                name = CONTENT_TYPE_HEADER,
                value = OHTTP_REQUEST_CONTENT_TYPE_HEADER_VALUE,
            )
            .post(encryptedRequest.bytes.toRequestBody())
            .build()

        val (wrappedRequest, _) = sut.wrapToOhttp(request, cryptoConfig)

        assertThat(isRequestsParamsExceptBodyEqual(wrappedRequest, expectedRequest)).isTrue
        assertThat(wrappedRequest.body!!.toByteArray()).isEqualTo(expectedRequest.body!!.toByteArray())
    }

    @Test
    fun `unwrapFromOhttp should throw IOException when response body is empty`() {
        val response = stubResponse.newBuilder()
            .addHeader(CONTENT_TYPE_HEADER, OHTTP_RESPONSE_CONTENT_TYPE_HEADER_VALUE)
            .build()

        val (_, unpacker) = sut.wrapToOhttp(request, cryptoConfig)

        assertThrows<IOException> {
            unpacker.unwrapFromOhttp(response)
        }
    }

    @Test
    fun `unwrapFromOhttp should throw IOException when proper Content-Type header is not found`() {
        val response = stubResponse.newBuilder()
            .body("Encrypted Response Body".toResponseBody())
            .build()

        val (_, unpacker) = sut.wrapToOhttp(request, cryptoConfig)

        assertThrows<IOException> {
            unpacker.unwrapFromOhttp(response)
        }
    }

    @Test
    fun `unwrapFromOhttp should throw IOException when proper Content-Type header is not ohttp`() {
        val response = stubResponse.newBuilder()
            .body("Encrypted Response Body".toResponseBody())
            .addHeader(CONTENT_TYPE_HEADER, "application/json")
            .build()

        val (_, unpacker) = sut.wrapToOhttp(request, cryptoConfig)

        assertThrows<IOException> {
            unpacker.unwrapFromOhttp(response)
        }
    }

    @Test
    fun `unwrapFromOhttp should decrypt the response with decryptor obtained during encryption`() {
        val responseBodyString = "Encrypted Response Body"
        val response = stubResponse
            .newBuilder()
            .body(responseBodyString.toResponseBody())
            .addHeader(CONTENT_TYPE_HEADER, OHTTP_RESPONSE_CONTENT_TYPE_HEADER_VALUE)
            .build()

        val (_, unpacker) = sut.wrapToOhttp(request, cryptoConfig)
        val actual = unpacker.unwrapFromOhttp(response)

        verify(decryptor).decrypt(
            OhttpEncryptedResponse(
                response.protocol,
                responseBodyString.toResponseBody().bytes(),
            ),
        )
        assertThat(actual).isEqualTo(decryptedResponse)
    }

    @Test
    fun `dispose should dispose decryptor`() {
        val (_, unpacker) = sut.wrapToOhttp(request, cryptoConfig)
        unpacker.dispose()

        verify(decryptor).dispose()
    }
}

private fun RequestBody.toByteArray(): ByteArray {
    val buffer = Buffer()
    this.writeTo(buffer)
    return buffer.readByteArray()
}
