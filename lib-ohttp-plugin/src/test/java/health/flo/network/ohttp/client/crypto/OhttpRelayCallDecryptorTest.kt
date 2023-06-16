package health.flo.network.ohttp.client.crypto

import health.flo.network.bhttp.OkHttpBinarySerializer
import health.flo.network.bhttp.ResponseBinaryData
import health.flo.network.ohttp.client.DataFactory.stubByteData
import health.flo.network.ohttp.client.DataFactory.stubRequest
import health.flo.network.ohttp.client.crypto.OhttpRelayCallEncryptor.OhttpRelayCallDecryptor
import health.flo.network.ohttp.encapsulator.DecapsulationResult
import health.flo.network.ohttp.encapsulator.OhttpDecapsulator
import okhttp3.Protocol
import okhttp3.Protocol.HTTP_1_1
import okhttp3.Response
import okio.IOException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class OhttpRelayCallDecryptorTest {

    private val oHttpDecapsulator: OhttpDecapsulator = mock()
    private val binarySerializer: OkHttpBinarySerializer = mock()
    private val request = stubRequest()

    private val sut = OhttpRelayCallDecryptor(
        oHttpDecapsulator,
        binarySerializer,
        request,
    )

    @Test
    fun `decrypt should return Response when response decapsulated successfully`() {
        val encryptedResponse = stubOhttpEncryptedResponse()
        val serializedRequest = stubByteData()
        val decapsulationResult = stubDecapsulationResultSuccess(serializedRequest)
        val response = stubResponse()

        mockOhttpDecapsulator(encryptedResponse.data, decapsulationResult)
        mockBinarySerializer(serializedRequest, response)

        val actual = sut.decrypt(encryptedResponse)

        assertThat(actual).isEqualTo(response)
    }

    @Test
    fun `decrypt should throw IOException when response decapsulated with error`() {
        val encryptedResponse = stubOhttpEncryptedResponse()
        val decapsulationResult = stubDecapsulationResultFailure()

        mockOhttpDecapsulator(encryptedResponse.data, decapsulationResult)

        assertThrows<IOException> {
            sut.decrypt(encryptedResponse)
        }
    }

    @Test
    fun `decrypt should throw IOException when OHTTP context is disposed`() {
        val encryptedResponse = stubOhttpEncryptedResponse()
        val decapsulationResult = DecapsulationResult.Disposed

        mockOhttpDecapsulator(encryptedResponse.data, decapsulationResult)

        assertThrows<IOException> {
            sut.decrypt(encryptedResponse)
        }
    }

    private fun mockBinarySerializer(data: ByteArray, response: Response) {
        whenever(
            binarySerializer.deserialize(
                ResponseBinaryData(data),
                response.protocol,
                request,
            ),
        ) doReturn response
    }

    private fun mockOhttpDecapsulator(
        data: ByteArray,
        result: DecapsulationResult,
    ) {
        whenever(oHttpDecapsulator.decapsulateResponse(data)) doReturn result
    }

    private fun stubDecapsulationResultSuccess(
        decapsulatedResponse: ByteArray = stubByteData(),
    ) = DecapsulationResult.Success(
        data = decapsulatedResponse,
    )

    private fun stubDecapsulationResultFailure(
        message: String = "test_message",
        additionalData: Map<String, Any> = emptyMap(),
    ) = DecapsulationResult.Failure(
        message = message,
        additionalData = additionalData,
    )

    private fun stubOhttpEncryptedResponse() = OhttpEncryptedResponse(
        protocol = stubProtocol(),
        data = stubByteData(),
    )

    private fun stubProtocol(): Protocol = HTTP_1_1

    private fun stubResponse(): Response = Response.Builder()
        .request(stubRequest())
        .protocol(stubProtocol())
        .code(200)
        .message("OK")
        .build()
}
