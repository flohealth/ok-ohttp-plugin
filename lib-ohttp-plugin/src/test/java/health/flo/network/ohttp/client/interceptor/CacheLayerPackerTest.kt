package health.flo.network.ohttp.client.interceptor

import health.flo.network.ohttp.client.DataFactory.stubResponse
import health.flo.network.ohttp.client.HttpHeaders.HOST_HEADER
import health.flo.network.ohttp.client.HttpHeaders.OHTTP_ORIGINAL_URL_HEADER
import health.flo.network.ohttp.client.isRequestsParamsExceptBodyEqualComparator
import health.flo.network.ohttp.client.isResponseParamsExceptBodyComparator
import health.flo.network.ohttp.client.utils.UrlEncoder
import health.flo.test.extensions.toDynamicTests
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.internal.toHostHeader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

internal class CacheLayerPackerTest {

    private val relayUrl = "https://relay.example.com".toHttpUrl()
    private val targetUrl = "https://target-api.example.com/target-path".toHttpUrl()

    private val requestPlain = Request.Builder()
        .url(targetUrl)
        .addHeader("Other-Header", "other_value")
        .build()

    private val requestToRelay = Request.Builder()
        .url(relayUrl)
        .addHeader("Other-Header", "other_value")
        .addHeader(OHTTP_ORIGINAL_URL_HEADER, targetUrl.toString())
        .build()

    private val urlEncoder: UrlEncoder = mock()

    private val sut = CacheLayerPacker(urlEncoder)

    @Test
    fun `packRequest should transform user request with Relay URL and original URL hash to satisfy requirements of network interceptor and cache`() {
        val originalUrlHash = "original-url-hash"
        val expectedUrl = relayUrl.newBuilder().encodedPath("/$originalUrlHash").build()
        val expected = requestToRelay.newBuilder().url(expectedUrl).build()

        urlEncoder.stub {
            on { this.encodeUrlSafe(targetUrl.toString()) } doReturn originalUrlHash
        }

        val actual = sut.packRequest(requestPlain, relayUrl, targetUrl)

        assertThat(actual)
            .usingComparator(isRequestsParamsExceptBodyEqualComparator())
            .isEqualTo(expected)
    }

    @TestFactory
    fun `isPackedRequest should answer whether the request is transformed for OHTTP processing`(): Collection<DynamicTest> {
        val data = listOf(
            requestToRelay to CacheLayerPacker.IsOhttpPacked.Yes(targetUrl.toString()),
            requestPlain to CacheLayerPacker.IsOhttpPacked.No,
        )
        return data.toDynamicTests { (input, expected) ->

            val actualResult = sut.isPackedRequest(input)

            assertThat(actualResult).isEqualTo(expected)
        }
    }

    @Test
    fun `restoreUserRequest should revert preprocessing transformation and return request as it was before transformation`() {
        val actual = sut.restoreUserRequest(requestToRelay, targetUrl.toString())

        assertThat(actual)
            .usingComparator(isRequestsParamsExceptBodyEqualComparator())
            .isEqualTo(requestPlain)
    }

    @Test
    fun `restoreUserRequest should apply original URL as Host header`() {
        val requestWithHost = requestToRelay.newBuilder()
            .addHeader(HOST_HEADER, relayUrl.toHostHeader())
            .build()
        val expectedRequest = requestPlain.newBuilder()
            .addHeader(HOST_HEADER, targetUrl.toHostHeader())
            .build()

        val actual = sut.restoreUserRequest(requestWithHost, targetUrl.toString())

        assertThat(actual)
            .usingComparator(isRequestsParamsExceptBodyEqualComparator())
            .isEqualTo(expectedRequest)
    }

    @Test
    fun `packResponse should add original URL to response`() {
        val originalUrl = "original-url"
        val response = stubResponse()
        val expected = response.newBuilder()
            .addHeader(OHTTP_ORIGINAL_URL_HEADER, originalUrl)
            .build()

        val actual = sut.packResponse(response.newBuilder(), originalUrl)

        assertThat(actual.build())
            .usingComparator(isResponseParamsExceptBodyComparator())
            .isEqualTo(expected)
    }

    @Test
    fun `unpackResponse should remove original URL from response`() {
        val originalUrl = "original-url"
        val response = stubResponse()
        val packedResponse = response.newBuilder()
            .addHeader(OHTTP_ORIGINAL_URL_HEADER, originalUrl)
            .build()
        val expected = response

        val actual = sut.unpackResponse(packedResponse)

        assertThat(actual)
            .usingComparator(isResponseParamsExceptBodyComparator())
            .isEqualTo(expected)
    }
}
