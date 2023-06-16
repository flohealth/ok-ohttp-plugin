package health.flo.network.ohttp.client.relay

import health.flo.network.ohttp.client.crypto.OhttpRelayCallEncryptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import java.io.IOException

private const val CONTENT_TYPE_HEADER = "Content-Type"
private const val OHTTP_RESPONSE_CONTENT_TYPE_HEADER_VALUE = "message/ohttp-res"

internal class OhttpRelayCallUnpackerTest {

    private val responseBodyBytes = byteArrayOf(1, 42)
    private val responseBody: ResponseBody = mock {
        on { this.bytes() } doReturn responseBodyBytes
    }

    private val unwrappedResponse = mock<Response>()
    private val decryptor: OhttpRelayCallEncryptor.OhttpRelayCallDecryptor = mock {
        on { decrypt(any()) }.doReturn(unwrappedResponse)
    }

    private val protocol: Protocol = mock()
    private val responseWithEncryptedBody = mock<Response> {
        on { this.body } doReturn responseBody
        on { this.headers(CONTENT_TYPE_HEADER) } doReturn listOf(OHTTP_RESPONSE_CONTENT_TYPE_HEADER_VALUE)
        on { this.protocol } doReturn protocol
    }

    private val sut = OhttpRelayCallUnpacker(
        decryptor,
    )

    @Test
    fun `unwrapFromOhttp should return unwrapped response`() {
        val response = sut.unwrapFromOhttp(responseWithEncryptedBody)

        verify(decryptor).decrypt(any())
        assertThat(response).isEqualTo(unwrappedResponse)
    }

    @Test
    fun `unwrapFromOhttp should throw any Exception type further when decryptor decapsulate throws`() {
        decryptor.stub {
            on { this.decrypt(any()) } doThrow AssertionError()
        }

        assertThrows<AssertionError> {
            sut.unwrapFromOhttp(responseWithEncryptedBody)
        }
    }

    @Test
    fun `unwrapFromOhttp should throw IOException when response body is null`() {
        responseWithEncryptedBody.stub {
            on { this.body } doReturn null
        }

        assertThrows<IOException> {
            sut.unwrapFromOhttp(responseWithEncryptedBody)
        }
    }

    @Test
    fun `unwrapFromOhttp should throw IOException when response has wrong Content-Type`() {
        responseWithEncryptedBody.stub {
            on { this.headers(CONTENT_TYPE_HEADER) } doReturn listOf("WRONG Content-Type")
        }

        assertThrows<IOException> {
            sut.unwrapFromOhttp(responseWithEncryptedBody)
        }
    }
}
