package health.flo.network.ohttp.client.interceptor

import health.flo.network.ohttp.client.DataFactory.stubResponse
import health.flo.network.ohttp.client.HttpHeaders.OHTTP_ORIGINAL_URL_HEADER
import health.flo.network.ohttp.client.eqRequest
import health.flo.network.ohttp.client.interceptor.CacheLayerPacker.IsOhttpPacked.No
import health.flo.network.ohttp.client.interceptor.CacheLayerPacker.IsOhttpPacked.Yes
import health.flo.network.ohttp.client.isResponseParamsExceptBodyComparator
import health.flo.network.ohttp.client.isResponseParamsExceptBodyEqual
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

internal class OhttpNetworkInterceptorTest {

    private val relayUrl = "https://relay.example.com"
    private val targetUrl = "https://example.com/path"

    private val requestPlain = Request.Builder()
        .url(relayUrl)
        .addHeader("Other-Header", "other_value")
        .build()

    private val requestToRelay = Request.Builder()
        .url(relayUrl)
        .addHeader("Other-Header", "other_value")
        .addHeader(OHTTP_ORIGINAL_URL_HEADER, targetUrl)
        .build()

    private val requestToRelayWithRestoredUrl = Request.Builder()
        .url(targetUrl)
        .addHeader("Other-Header", "other_value")
        .build()

    private var chainRequest = requestToRelay
    private val chainResponse = mock<Response>()
    private val chain = mock<Interceptor.Chain> {
        on { this.request() } doAnswer { chainRequest }
        on { this.proceed(any()) } doAnswer { chainResponse }
    }

    private var ohttpRequestProcessorResult = stubResponse()
    private val ohttpRequestProcessor: OhttpRequestProcessor = mock {
        on { this.process(any(), any()) } doAnswer { ohttpRequestProcessorResult }
    }

    private val cacheLayerPacker: CacheLayerPacker = mock {
        on { this.isPackedRequest(requestPlain) } doReturn No
        on { this.isPackedRequest(requestToRelay) } doReturn Yes(targetUrl)
        on {
            this.restoreUserRequest(eqRequest(requestToRelay), eq(targetUrl))
        } doReturn requestToRelayWithRestoredUrl
    }

    private val sut = OhttpNetworkInterceptor(
        cacheLayerPacker,
        ohttpRequestProcessor,
    )

    @Test
    fun `intercept should not wrap request and proceed with plain request with cleaned OHTTP headers when no reasons for OHTTP-wrapping found`() {
        chainRequest = requestPlain
        val expectedRequest = requestPlain
        val expectedResponse = chainResponse

        val response = sut.intercept(chain)

        verify(chain).proceed(eqRequest(expectedRequest))
        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `intercept should pass request with cleaned OHTTP headers to OHTTP processor when reasons for OHTTP-wrapping was found`() {
        chainRequest = requestToRelay
        val expectedRequest = requestToRelayWithRestoredUrl

        cacheLayerPacker.stub {
            on { this.packResponse(any(), any()) } doAnswer { invocation -> invocation.getArgument(0) }
        }

        sut.intercept(chain)

        verify(ohttpRequestProcessor).process(eqRequest(expectedRequest), eq(chain))
    }

    @Test
    fun `intercept should return OHTTP response packed for cache layer & with cached response attached`() {
        chainRequest = requestToRelay
        val ohttpRequestProcessorResponse = stubResponse(mock())
        ohttpRequestProcessorResult = ohttpRequestProcessorResponse
        val responseBeforePacking = ohttpRequestProcessorResponse.newBuilder()
            .request(requestToRelay)
            .build()
        val expectedResponse = responseBeforePacking.newBuilder()
            .addHeader(OHTTP_ORIGINAL_URL_HEADER, targetUrl)
            .build()

        cacheLayerPacker.stub {
            on { this.packResponse(any(), any()) } doReturn expectedResponse.newBuilder()
        }

        val response = sut.intercept(chain)

        verify(cacheLayerPacker).packResponse(
            builder = argThat {
                val actualResponseToPack = this.build()

                actualResponseToPack.request == requestToRelay &&
                    isResponseParamsExceptBodyEqual(actualResponseToPack, responseBeforePacking)
            },
            originalUrl = eq(targetUrl),
        )
        assertThat(response)
            .usingComparator(isResponseParamsExceptBodyComparator())
            .isEqualTo(expectedResponse)
    }

    @Test
    fun `intercept should throw IOException when OHTTP processing fails with non-IOException`() {
        chainRequest = requestToRelay
        val error = AssertionError()

        whenever(ohttpRequestProcessor.process(any(), any())).doThrow(error)

        assertThrows<IOException> {
            sut.intercept(chain)
        }
    }

    @Test
    fun `intercept should re-throw IOException when OHTTP processing fails with IOException`() {
        chainRequest = requestToRelay
        val ioException = IOException()

        whenever(ohttpRequestProcessor.process(any(), any())).doAnswer { throw ioException }

        assertThrows<IOException> {
            sut.intercept(chain)
        }
    }
}
