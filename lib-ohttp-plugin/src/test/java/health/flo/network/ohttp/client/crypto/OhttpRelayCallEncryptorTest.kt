package health.flo.network.ohttp.client.crypto

import health.flo.network.bhttp.OkHttpBinarySerializer
import health.flo.network.bhttp.RequestBinaryData
import health.flo.network.ohttp.client.DataFactory.stubByteData
import health.flo.network.ohttp.client.DataFactory.stubRequest
import health.flo.network.ohttp.client.config.OhttpCryptoConfig
import health.flo.network.ohttp.encapsulator.EncapsulationResult
import health.flo.network.ohttp.encapsulator.OhttpDecapsulator
import health.flo.network.ohttp.encapsulator.OhttpEncapsulator
import okhttp3.Request
import okio.IOException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import health.flo.network.ohttp.encapsulator.OhttpCryptoConfig as EncapsulatorCryptoConfig

internal class OhttpRelayCallEncryptorTest {

    private val testConfig = stubCryptoConfig()

    private val binarySerializer: OkHttpBinarySerializer = mock()
    private val ohttpEncapsulator: OhttpEncapsulator = mock()

    private val sut = OhttpRelayCallEncryptor(
        binarySerializer,
        ohttpEncapsulator,
    )

    @Test
    fun `encrypt should return EncryptionResult#Success when request encapsulated successfully`() {
        val request = stubRequest()
        val serializedRequest = stubByteData()
        val cryptoConfig: EncapsulatorCryptoConfig = testConfig.bytes
        val encapsulatedRequest = stubByteData()
        val encapsulationResult = stubEncapsulationResultSuccess(encapsulatedRequest)

        mockBinarySerializer(request, serializedRequest)
        mockOhttpEncapsulator(serializedRequest, cryptoConfig, encapsulationResult)

        val actual = sut.encrypt(request, testConfig)

        assertThat(actual).isInstanceOf(EncryptionResult::class.java)
    }

    @Test
    fun `encrypt should throw IOException when request encapsulated with error`() {
        val request = stubRequest()
        val serializedRequest = stubByteData()
        val cryptoConfig: EncapsulatorCryptoConfig = testConfig.bytes
        val encapsulationResult = stubEncapsulationResultFailure()

        mockBinarySerializer(request, serializedRequest)
        mockOhttpEncapsulator(serializedRequest, cryptoConfig, encapsulationResult)

        assertThrows<IOException> {
            sut.encrypt(request, testConfig)
        }
    }

    private fun mockBinarySerializer(request: Request, serialized: ByteArray) {
        whenever(binarySerializer.serialize(request)) doReturn RequestBinaryData(serialized)
    }

    private fun mockOhttpEncapsulator(
        data: ByteArray,
        config: EncapsulatorCryptoConfig,
        result: EncapsulationResult,
    ) {
        whenever(ohttpEncapsulator.encapsulateRequest(data, config)) doReturn result
    }

    private fun stubCryptoConfig() = OhttpCryptoConfig(stubByteData())

    private fun stubEncapsulationResultSuccess(
        data: ByteArray = stubByteData(),
        decapsulator: OhttpDecapsulator = mock(),
    ): EncapsulationResult = EncapsulationResult.Success(
        data = data,
        decapsulator = decapsulator,
    )

    private fun stubEncapsulationResultFailure(
        message: String = "test_message",
        additionalData: Map<String, Any> = emptyMap(),
    ): EncapsulationResult = EncapsulationResult.Failure(
        message = message,
        additionalData = additionalData,
    )
}
