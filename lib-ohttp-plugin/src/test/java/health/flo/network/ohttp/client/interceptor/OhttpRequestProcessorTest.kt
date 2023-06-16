package health.flo.network.ohttp.client.interceptor

import health.flo.network.ohttp.client.HttpHeaders.CONNECTION_HEADER
import health.flo.network.ohttp.client.HttpHeaders.CONNECTION_HEADER_VALUE_KEEP_ALIVE
import health.flo.network.ohttp.client.HttpHeaders.HOST_HEADER
import health.flo.network.ohttp.client.HttpHeaders.OHTTP_ERROR_STALE_CONFIG_HEADER
import health.flo.network.ohttp.client.HttpHeaders.OHTTP_REFRESH_STALE_CONFIG_HEADER
import health.flo.network.ohttp.client.HttpHeaders.USER_AGENT_HEADER
import health.flo.network.ohttp.client.config.OhttpCryptoConfig
import health.flo.network.ohttp.client.config.OhttpCryptoConfigProvider
import health.flo.network.ohttp.client.eqRequest
import health.flo.network.ohttp.client.isResponseParamsExceptBodyComparator
import health.flo.network.ohttp.client.relay.OhttpRelayCallPacker
import health.flo.network.ohttp.client.relay.OhttpRelayCallUnpacker
import okhttp3.CipherSuite
import okhttp3.Handshake
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.TlsVersion.TLS_1_3
import okhttp3.internal.toHostHeader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

private const val TEST_SEND_TIME = 1000L
private const val TEST_RECEIVE_TIME = 1050L

private const val USER_AGENT_HEADER_VALUE: String = "test User-Agent"

internal class OhttpRequestProcessorTest {

    private val cryptoConfig = OhttpCryptoConfig(byteArrayOf(42, 42))

    private val url = "https://example.com"

    private val request = Request.Builder()
        .url(url)
        .build()

    private val packedRequest = Request.Builder()
        .url(url)
        .build()

    private val handshake = Handshake.get(
        TLS_1_3,
        CipherSuite.TLS_AES_256_GCM_SHA384,
        emptyList(),
        emptyList(),
    )

    private val processorRequest = Request.Builder()
        .url(url)
        .addHeader(HOST_HEADER, url.toHttpUrl().toHostHeader())
        .addHeader(CONNECTION_HEADER, CONNECTION_HEADER_VALUE_KEEP_ALIVE)
        .addHeader(USER_AGENT_HEADER, USER_AGENT_HEADER_VALUE)
        .build()

    private val wrappedSuccessResponse = wrappedResponseBuilder()
        .code(HTTP_OK)
        .build()

    private val wrappedFailResponse = wrappedResponseBuilder()
        .code(HTTP_BAD_REQUEST)
        .build()

    private val staleCryptoConfigResponse = wrappedResponseBuilder()
        .code(HTTP_UNAUTHORIZED)
        .build()

    private val unwrappedResponse = Response.Builder()
        .request(processorRequest)
        .protocol(Protocol.HTTP_1_1)
        .message("")
        .code(HTTP_OK)
        .build()

    private var chainResponse = wrappedSuccessResponse
    private val chain = mock<Interceptor.Chain> {
        on { this.proceed(any()) } doAnswer { chainResponse }
    }

    private val unpacker: OhttpRelayCallUnpacker = mock {
        on { this.unwrapFromOhttp(wrappedSuccessResponse) } doReturn unwrappedResponse
    }

    private val packerResult = packedRequest to unpacker

    private val ohttpRelayRequestPacker: OhttpRelayCallPacker = mock {
        on { this.wrapToOhttp(eqRequest(request), eq(cryptoConfig)) } doReturn packerResult
    }

    private val cryptoConfigProvider: OhttpCryptoConfigProvider = mock {
        on { this.getConfig() } doReturn cryptoConfig
        on { this.exchangeStaleConfig(cryptoConfig.hashCode()) } doReturn cryptoConfig
    }

    private val sut = OhttpRequestProcessor(
        userAgent = USER_AGENT_HEADER_VALUE,
        ohttpRelayRequestPacker = ohttpRelayRequestPacker,
        cryptoConfigProvider = cryptoConfigProvider,
    )

    @Test
    fun `process should wrap request in OHTTP & pass it to chain with added network headers`() {
        sut.process(request, chain)

        verify(chain).proceed(eqRequest(processorRequest))
    }

    @Test
    fun `process should unwrap OHTTP response when it succeed with 200 OK`() {
        chainResponse = wrappedSuccessResponse

        val response = sut.process(request, chain)

        assertThat(response)
            .usingComparator(isResponseParamsExceptBodyComparator())
            .isEqualTo(unwrappedResponse)
    }

    @Test
    fun `process should not unwrap OHTTP response & return errors as-is when it failed with HTTP code`() {
        chainResponse = wrappedFailResponse

        val response = sut.process(request, chain)

        assertThat(response)
            .usingComparator(isResponseParamsExceptBodyComparator())
            .isEqualTo(wrappedFailResponse)
    }

    @Test
    fun `process should not unwrap OHTTP response & return response as-is when when it succeed with code different from 200 OK`() {
        val responseNon200 = wrappedSuccessResponse.newBuilder().code(204).build()
        chainResponse = responseNon200
        val expected = responseNon200

        val response = sut.process(request, chain)

        assertThat(response).isEqualTo(expected)
    }

    @Test
    fun `process should throw error further when request packing throws`() {
        whenever(ohttpRelayRequestPacker.wrapToOhttp(any(), any())).doThrow(AssertionError())

        assertThrows<AssertionError> {
            sut.process(request, chain)
        }
    }

    @Test
    fun `process should throw error further when response unpacking throws`() {
        chainResponse = wrappedSuccessResponse

        whenever(unpacker.unwrapFromOhttp(any())).doThrow(AssertionError())

        assertThrows<AssertionError> {
            sut.process(request, chain)
        }
    }

    @Test
    fun `process should dispose unpacker when response unpacking failed`() {
        chainResponse = wrappedSuccessResponse

        whenever(unpacker.unwrapFromOhttp(any())).doThrow(AssertionError())

        assertThrows<AssertionError> {
            sut.process(request, chain)
        }

        verify(unpacker).dispose()
    }

    @Test
    fun `process should return error response with stale crypto config hash when request failed with 401 HTTP code`() {
        val expected = staleCryptoConfigResponse.newBuilder()
            .addHeader(OHTTP_ERROR_STALE_CONFIG_HEADER, cryptoConfig.hashCode().toString())
            .build()

        chainResponse = staleCryptoConfigResponse

        val response = sut.process(request, chain)

        assertThat(response)
            .usingComparator(isResponseParamsExceptBodyComparator())
            .isEqualTo(expected)
    }

    @Test
    fun `process should refresh stale config before request when refresh-header added to request`() {
        val requestWithRefreshHeader = request.newBuilder()
            .addHeader(OHTTP_REFRESH_STALE_CONFIG_HEADER, cryptoConfig.hashCode().toString())
            .build()
        val expected = wrappedSuccessResponse

        chainResponse = wrappedSuccessResponse

        val response = sut.process(requestWithRefreshHeader, chain)

        verify(cryptoConfigProvider).exchangeStaleConfig(cryptoConfig.hashCode())
        assertThat(response)
            .usingComparator(isResponseParamsExceptBodyComparator())
            .isEqualTo(expected)
    }

    @Test
    fun `process should replicate handshake from the relay response`() {
        chainResponse = wrappedSuccessResponse

        val response = sut.process(request, chain)

        assertThat(response.handshake).isEqualTo(handshake)
    }

    @Test
    fun `process should replicate time attributes from the relay response`() {
        chainResponse = wrappedSuccessResponse

        val response = sut.process(request, chain)

        assertThat(response.sentRequestAtMillis).isEqualTo(TEST_SEND_TIME)
        assertThat(response.receivedResponseAtMillis).isEqualTo(TEST_RECEIVE_TIME)
    }

    private fun wrappedResponseBuilder() = Response.Builder()
        .request(processorRequest)
        .protocol(Protocol.HTTP_1_1)
        .handshake(handshake)
        .sentRequestAtMillis(TEST_SEND_TIME)
        .receivedResponseAtMillis(TEST_RECEIVE_TIME)
        .message("")
}
