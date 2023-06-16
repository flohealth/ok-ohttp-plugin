package health.flo.network.ohttp.client.interceptor

import health.flo.network.ohttp.client.DataFactory.stubResponse
import health.flo.network.ohttp.client.HttpHeaders.FORCE_OHTTP_ENABLED_HEADER
import health.flo.network.ohttp.client.HttpHeaders.OHTTP_ERROR_STALE_CONFIG_HEADER
import health.flo.network.ohttp.client.HttpHeaders.OHTTP_ORIGINAL_URL_HEADER
import health.flo.network.ohttp.client.eqRequest
import health.flo.network.ohttp.client.interceptor.RefreshConfigHeadersHelper.addRefreshConfigHeader
import health.flo.network.ohttp.client.isResponseParamsExceptBodyComparator
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

internal class OhttpPreprocessingInterceptorTest {

    private val relayUrl = "https://relay.flo.health.test".toHttpUrl()
    private val targetUrl = "https://flo.health.test/path".toHttpUrl()

    private val request = Request.Builder()
        .url(targetUrl)
        .addHeader("Other-Header", "other_value")
        .addHeader(FORCE_OHTTP_ENABLED_HEADER, "true")
        .addHeader(FORCE_OHTTP_ENABLED_HEADER, "false")
        .addHeader(FORCE_OHTTP_ENABLED_HEADER, "some invalid value")
        .build()

    private val requestCleanedFromHeaders = Request.Builder()
        .url(targetUrl)
        .addHeader("Other-Header", "other_value")
        .build()

    private val requestTransformedForOhttp = Request.Builder()
        .url(relayUrl)
        .addHeader("Other-Header", "other_value")
        .addHeader(OHTTP_ORIGINAL_URL_HEADER, targetUrl.toString())
        .build()

    private var ohttpWrappingRequiredAnalyserResult = true
    private val ohttpWrappingRequiredAnalyser: OhttpWrappingRequiredAnalyser = mock {
        on { this.isOhttpRequired(request) } doAnswer { ohttpWrappingRequiredAnalyserResult }
    }

    private var chainRequest = request
    private val chainResponse = stubResponse()
    private val chain = mock<Interceptor.Chain> {
        on { this.request() } doAnswer { chainRequest }
        on { this.proceed(any()) } doAnswer { chainResponse }
    }

    private val cacheLayerPacker: CacheLayerPacker = mock {
        on {
            this.packRequest(eqRequest(requestCleanedFromHeaders), eq(relayUrl), eq(targetUrl))
        } doReturn requestTransformedForOhttp
        on { this.unpackResponse(any()) } doAnswer { chainResponse }
    }

    private val sut = OhttpPreprocessingInterceptor(
        ohttpWrappingRequiredAnalyser,
        relayUrl,
        cacheLayerPacker,
    )

    @Test
    fun `intercept should pass to chain request with replaced force OHTTP headers when reasons for OHTTP-wrapping not found`() {
        val expectedRequest = requestCleanedFromHeaders
        ohttpWrappingRequiredAnalyserResult = false
        val expectedResponse = chainResponse

        val response = sut.intercept(chain)

        verify(chain).proceed(eqRequest(expectedRequest))
        assertThat(response).isEqualTo(expectedResponse)
    }

    @Test
    fun `intercept should modify request for OHTTP and return response for original request when reasons for OHTTP-wrapping was found`() {
        // intercept should pass to chain request with
        // replaced force OHTTP headers, url replaced to relay, set target endpoint as header
        // & return response with request set as original request
        ohttpWrappingRequiredAnalyserResult = true
        val expectedRequest = requestTransformedForOhttp
        val expectedResponse = chainResponse.newBuilder()
            .request(chainRequest)
            .build()

        val response = sut.intercept(chain)

        verify(chain).proceed(eqRequest(expectedRequest))
        assertThat(response)
            .usingComparator(isResponseParamsExceptBodyComparator())
            .isEqualTo(expectedResponse)
    }

    @Test
    fun `intercept should retry request for OHTTP with 'refresh crypto config' directive when request failed due to stale config`() {
        ohttpWrappingRequiredAnalyserResult = true
        val staleCryptoConfigHash = 42
        val responseStale = stubResponse()
            .newBuilder()
            .code(HTTP_UNAUTHORIZED)
            .addHeader(OHTTP_ERROR_STALE_CONFIG_HEADER, staleCryptoConfigHash.toString())
            .build()
        val responseOk = stubResponse()
        val expectedRequestForStale = requestTransformedForOhttp
        val expectedRequestForOk = addRefreshConfigHeader(requestTransformedForOhttp, staleCryptoConfigHash.toString())
        val expectedResponse = responseOk.newBuilder()
            .request(chainRequest)
            .build()

        chain.stub {
            on { this.proceed(any()) } doReturnConsecutively (listOf(responseStale, responseOk))
        }

        val response = sut.intercept(chain)

        verify(chain).proceed(eqRequest(expectedRequestForStale))
        verify(chain).proceed(eqRequest(expectedRequestForOk))
        assertThat(response)
            .usingComparator(isResponseParamsExceptBodyComparator())
            .isEqualTo(expectedResponse)
    }
}
