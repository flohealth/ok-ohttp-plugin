@file:Suppress("ReplaceNotNullAssertionWithElvisReturn")

package health.flo.network.ohttp.client

import health.flo.network.bhttp.OkHttpBinarySerializer
import health.flo.network.bhttp.RequestBinaryData
import health.flo.network.ohttp.client.DataFactory.stubRequest
import health.flo.network.ohttp.client.DataFactory.stubResponse
import health.flo.network.ohttp.client.HttpHeaders.CONNECTION_HEADER
import health.flo.network.ohttp.client.HttpHeaders.CONNECTION_HEADER_VALUE_KEEP_ALIVE
import health.flo.network.ohttp.client.HttpHeaders.CONTENT_LENGTH_HEADER
import health.flo.network.ohttp.client.HttpHeaders.FORCE_OHTTP_ENABLED_HEADER
import health.flo.network.ohttp.client.HttpHeaders.HOST_HEADER
import health.flo.network.ohttp.client.HttpHeaders.OHTTP_REQUEST_CONTENT_TYPE_HEADER_VALUE
import health.flo.network.ohttp.client.HttpHeaders.OHTTP_RESPONSE_CONTENT_TYPE_HEADER_VALUE
import health.flo.network.ohttp.client.HttpHeaders.USER_AGENT_HEADER
import health.flo.network.ohttp.client.utils.UrlEncoder
import health.flo.network.ohttp.encapsulator.DecapsulationResult
import health.flo.network.ohttp.encapsulator.EncapsulationResult
import health.flo.network.ohttp.encapsulator.OhttpDecapsulator
import health.flo.network.ohttp.encapsulator.OhttpEncapsulator
import okhttp3.Cache
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.internal.http2.Header
import okhttp3.internal.toHeaderList
import okhttp3.internal.toHostHeader
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.presentation.Representation
import org.assertj.core.presentation.StandardRepresentation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.TimeUnit

private const val CONTENT_TYPE_HEADER = "Content-Type"

private const val MARKER_HEADER = "Marker-Header"
private const val USER_CONTENT_MARKER = "request with plain data created by user"

private const val CACHE_CONTROL_HEADER = "Cache-Control"
private const val VALID_CACHE_CONTROL_VALUE = "max-age=50209, private"

private const val E_TAG_HEADER = "ETag"
private const val IF_NON_MATCH_HEADER = "If-None-Match"
private const val E_TAG_HEADER_VALUE = "W/\"29e66de07b12a3c93c92433dde1b910b\""

private const val USER_AGENT_CONFIG = "Test ConfigServer User Agent"
private const val USER_AGENT_RELAY = "Test Relay User Agent"

internal class OhttpStackIntegrationTest {

    private val okhttpClientTimeout = Duration.ofSeconds(1)

    private val relayServer = MockWebServer()
    private val relayUrl get() = relayServer.url("/relay/")

    private val relayConfigServer = MockWebServer()
    private val relayConfigUrl get() = relayConfigServer.url("/relay-config/")

    private val targetServer = MockWebServer()
    private val targetUrl get() = targetServer.url("/flo-backend/")

    private var isOhttpEnabledValue = true
    private val isOhttpEnabled = IsOhttpEnabledProvider { isOhttpEnabledValue }

    private val deserializedResponseBody = byteArrayOf(77)
    private val deserializedResponse: Response = deserializedResponse()

    private val urlEncoder: UrlEncoder = mock {
        on { this.encodeUrlSafe(any()) } doAnswer { invocationOnMock ->
            "hashed" + (invocationOnMock.getArgument<String>(0)).toHttpUrl().encodedPath
        }
    }

    private var okHttpBinarySerializerDeserializedResponse = deserializedResponse
    private val okHttpBinarySerializer = mock<OkHttpBinarySerializer> {
        on { this.serialize(any()) } doReturn RequestBinaryData(byteArrayOf(100))
        on { this.deserialize(any(), any(), any()) } doAnswer { okHttpBinarySerializerDeserializedResponse }
    }

    private val ohttpDecapsulator = mock<OhttpDecapsulator> {
        on { this.decapsulateResponse(any()) } doReturn DecapsulationResult.Success(byteArrayOf(127))
    }
    private val ohttpEncapsulator = mock<OhttpEncapsulator> {
        on { this.encapsulateRequest(any(), any()) } doReturn
            EncapsulationResult.Success(byteArrayOf(0), ohttpDecapsulator)
    }

    private val cacheDirectory: File = Files.createTempDirectory("cache${System.currentTimeMillis()}").toFile()
    private val cache = Cache(cacheDirectory, 15_000_000L)

    private val request = Request.Builder()
        .url(targetUrl)
        .addHeader(MARKER_HEADER, USER_CONTENT_MARKER)
        .build()

    private val relayConfigServerResponse = MockResponse().setBody("response body with OHTTP Config")

    private val sut: OkHttpClient = createSut()

    @Test
    fun `request without OHTTP headers should be sent without modification`() {
        isOhttpEnabledValue = false

        targetServer.enqueue(MockResponse())

        sut.newCall(request).execute()
        val recordedRequest: RecordedRequest = targetServer.takeRequestWithTimeout()

        assertThat(recordedRequest.headers.sorted())
            .contains(Header(MARKER_HEADER, USER_CONTENT_MARKER))
    }

    @Test
    fun `request with OHTTP headers should be sent to relay with modification`() {
        val expectedTargetRequest = request.newBuilder()
            .addHeader(HOST_HEADER, targetUrl.toHostHeader())
            .build()
        // OHTTP Relay request headers should contain only expected items
        val expectedRequestHeaders = Headers.Builder()
            .add(CONTENT_TYPE_HEADER, OHTTP_REQUEST_CONTENT_TYPE_HEADER_VALUE)
            .add(HOST_HEADER, relayUrl.toHostHeader())
            .add(CONNECTION_HEADER, CONNECTION_HEADER_VALUE_KEEP_ALIVE)
            .add(CONTENT_LENGTH_HEADER, "1")
            .add(USER_AGENT_HEADER, USER_AGENT_RELAY)
            .build()
        val expectedResponse = deserializedResponse.newBuilder()
            .request(request)
            .build()
        val expectedResponseBody: ByteArray = deserializedResponseBody

        relayConfigServer.enqueue(relayConfigServerResponse)
        relayServer.enqueue(stubValidRelayResponse())

        val response = sut.newCall(request).execute()
        val responseBodyBytes = response.body!!.bytes()
        val recordedRequest: RecordedRequest = relayServer.takeRequestWithTimeout()

        verify(okHttpBinarySerializer).serialize(
            argWhere { actual ->
                val actualToMatch = actual.newBuilder()
                    // these headers are added by other parts of Okhttp chain. so, we don't check their content
                    .removeHeader("Accept-Encoding")
                    .removeHeader(CONNECTION_HEADER)
                    .removeHeader(USER_AGENT_HEADER)
                    .build()

                isRequestsParamsExceptBodyEqual(expectedTargetRequest, actualToMatch)
            },
        )

        assertAll(
            {
                assertThat(recordedRequest.headers.sorted())
                    .isEqualTo(expectedRequestHeaders.sorted())
            },
            {
                assertThat(response)
                    .usingComparator(isResponseParamsExceptBodyComparator())
                    .withRepresentation(responseRepresentation())
                    .isEqualTo(expectedResponse)
            },
            {
                assertThat(responseBodyBytes)
                    .usingComparator(byteArrayComparator())
                    .isEqualTo(expectedResponseBody)
            },
        )
    }

    @Test
    fun `request should be sent to relay when OHTTP forced by header`() {
        isOhttpEnabledValue = false
        val request = request.newBuilder()
            .addHeader(FORCE_OHTTP_ENABLED_HEADER, "true")
            .build()

        relayConfigServer.enqueue(relayConfigServerResponse)
        relayServer.enqueue(stubValidRelayResponse())

        sut.newCall(request).execute()

        assertThat(targetServer.requestCount).isEqualTo(0)
        assertThat(relayServer.requestCount).isEqualTo(1)
    }

    @Test
    fun `request with OHTTP headers should be returned from cache when it was already cached`() {
        okHttpBinarySerializerDeserializedResponse = deserializedResponse.newBuilder()
            .addHeader(CACHE_CONTROL_HEADER, VALID_CACHE_CONTROL_VALUE)
            .build()

        relayConfigServer.enqueue(relayConfigServerResponse)
        relayServer.enqueue(stubValidRelayResponse())
        relayServer.enqueue(stubValidRelayResponse())

        val sut = createSut(cacheForRelayRequests = cache)

        sut.newCall(request).execute().alsoReadBodyToTriggerResponseCaching()
        sut.newCall(request).execute()

        assertThat(relayServer.requestCount).isEqualTo(1)
    }

    @Test
    fun `request with OHTTP headers should be returned from cache when it was already cached when concurrent cacheable responses received from other endpoints`() {
        okHttpBinarySerializerDeserializedResponse = deserializedResponse().newBuilder()
            .addHeader(CACHE_CONTROL_HEADER, VALID_CACHE_CONTROL_VALUE)
            .request(request)
            .build()

        val targetEndpointUrl = targetServer.url("/some-endpoint-1")
        val requestToTargetEndpointWritesCache = Request.Builder()
            .url(targetEndpointUrl)
            .addHeader(MARKER_HEADER, USER_CONTENT_MARKER)
            .build()

        val anotherEndpointUrl = targetServer.url("/another-endpoint-2")
        val requestToAnotherEndpointWritesCache = Request.Builder()
            .url(anotherEndpointUrl)
            .addHeader(MARKER_HEADER, USER_CONTENT_MARKER)
            .build()

        val requestToTargetEndpointShouldBeReadFromCache = Request.Builder()
            .url(targetEndpointUrl)
            .addHeader(MARKER_HEADER, USER_CONTENT_MARKER)
            .build()

        relayConfigServer.enqueue(relayConfigServerResponse)
        repeat(7) {
            relayServer.enqueue(stubValidRelayResponse())
        }

        val sut = createSut(cacheForRelayRequests = cache)

        okHttpBinarySerializerDeserializedResponse = deserializedResponse().newBuilder()
            .addHeader(CACHE_CONTROL_HEADER, VALID_CACHE_CONTROL_VALUE)
            .request(requestToTargetEndpointWritesCache)
            .build()
        sut.newCall(requestToTargetEndpointWritesCache).execute().alsoReadBodyToTriggerResponseCaching()

        okHttpBinarySerializerDeserializedResponse = deserializedResponse().newBuilder()
            .addHeader(CACHE_CONTROL_HEADER, VALID_CACHE_CONTROL_VALUE)
            .request(requestToAnotherEndpointWritesCache)
            .build()
        sut.newCall(requestToAnotherEndpointWritesCache).execute().alsoReadBodyToTriggerResponseCaching()

        repeat(5) {
            okHttpBinarySerializerDeserializedResponse = deserializedResponse().newBuilder()
                .request(requestToAnotherEndpointWritesCache)
                .build()
            sut.newCall(requestToTargetEndpointShouldBeReadFromCache).execute().alsoReadBodyToTriggerResponseCaching()
        }

        assertThat(relayServer.requestCount).isEqualTo(2)
    }

    @Test
    fun `request should be sent with If-None-Match header equal to the ETag header of the previous response when previous request returned response with ETag header`() {
        okHttpBinarySerializerDeserializedResponse = deserializedResponse.newBuilder()
            .addHeader(E_TAG_HEADER, E_TAG_HEADER_VALUE)
            .request(request)
            .build()

        relayConfigServer.enqueue(relayConfigServerResponse)
        relayServer.enqueue(stubValidRelayResponse())
        relayServer.enqueue(stubValidRelayResponse())

        val sut = createSut(cacheForRelayRequests = cache)

        sut.newCall(request).execute().alsoReadBodyToTriggerResponseCaching()
        clearInvocations(okHttpBinarySerializer)
        sut.newCall(request).execute()

        verify(okHttpBinarySerializer).serialize(
            argWhere { request ->
                request.headers.contains(IF_NON_MATCH_HEADER to E_TAG_HEADER_VALUE)
            },
        )
    }

    @Test
    fun `request with OHTTP headers should get OHTTP config from Config backend when no config`() {
        val request = stubRequest(targetUrl)
        // OHTTP Config request headers should contain only expected items
        val expected = Headers.Builder()
            .add(HOST_HEADER, relayConfigUrl.toHostHeader())
            .add(CONNECTION_HEADER, CONNECTION_HEADER_VALUE_KEEP_ALIVE)
            .add("Accept-Encoding: gzip")
            .add(USER_AGENT_HEADER, USER_AGENT_CONFIG)
            .build()

        relayConfigServer.enqueue(MockResponse())
        relayServer.enqueue(stubValidRelayResponse())

        sut.newCall(request).execute()

        val recordedRelayConfigRequest: RecordedRequest = relayConfigServer.takeRequestWithTimeout()

        assertThat(recordedRelayConfigRequest.headers.sorted())
            .isEqualTo(expected.sorted())
    }

    @Test
    fun `ETag received from one API endpoint shouldn't be added to requests to another API endpoint`() {
        okHttpBinarySerializerDeserializedResponse = deserializedResponse.newBuilder()
            .addHeader(E_TAG_HEADER, E_TAG_HEADER_VALUE)
            .request(request)
            .build()

        val targetUrl1 = targetServer.url("/some-endpoint-1")
        val request = Request.Builder()
            .url(targetUrl1)
            .addHeader(MARKER_HEADER, USER_CONTENT_MARKER)
            .build()

        val targetUrl2 = targetServer.url("/another-endpoint-2")
        val request2 = Request.Builder()
            .url(targetUrl2)
            .addHeader(MARKER_HEADER, USER_CONTENT_MARKER)
            .build()

        relayConfigServer.enqueue(relayConfigServerResponse)
        relayServer.enqueue(stubValidRelayResponse())
        relayServer.enqueue(stubValidRelayResponse())

        val sut = createSut(cacheForRelayRequests = cache)

        sut.newCall(request).execute().alsoReadBodyToTriggerResponseCaching()
        clearInvocations(okHttpBinarySerializer)
        sut.newCall(request2).execute()

        verify(okHttpBinarySerializer).serialize(
            argWhere { apiRequest ->
                !apiRequest.headers.contains(IF_NON_MATCH_HEADER to E_TAG_HEADER_VALUE)
            },
        )
    }

    @Test
    fun `ETag received from API endpoint should be added to further requests when concurrent ETags received from other endpoints responses`() {
        okHttpBinarySerializerDeserializedResponse = deserializedResponse().newBuilder()
            .addHeader(E_TAG_HEADER, E_TAG_HEADER_VALUE)
            .request(request)
            .build()

        val targetUrl1 = targetServer.url("/some-endpoint-1")
        val request1 = Request.Builder()
            .url(targetUrl1)
            .addHeader(MARKER_HEADER, USER_CONTENT_MARKER)
            .build()

        val targetUrl2 = targetServer.url("/another-endpoint-2")
        val request2 = Request.Builder()
            .url(targetUrl2)
            .addHeader(MARKER_HEADER, USER_CONTENT_MARKER)
            .build()

        val request3 = Request.Builder()
            .url(targetUrl1)
            .addHeader(MARKER_HEADER, USER_CONTENT_MARKER)
            .build()

        relayConfigServer.enqueue(relayConfigServerResponse)
        relayServer.enqueue(stubValidRelayResponse())
        relayServer.enqueue(stubValidRelayResponse())
        relayServer.enqueue(stubValidRelayResponse())

        val sut = createSut(cacheForRelayRequests = cache)

        okHttpBinarySerializerDeserializedResponse = deserializedResponse().newBuilder()
            .addHeader(E_TAG_HEADER, E_TAG_HEADER_VALUE)
            .request(request1)
            .build()
        sut.newCall(request1).execute().alsoReadBodyToTriggerResponseCaching()

        clearInvocations(okHttpBinarySerializer)

        okHttpBinarySerializerDeserializedResponse = deserializedResponse().newBuilder()
            .addHeader(E_TAG_HEADER, "other_E_TAG")
            .request(request2)
            .build()
        sut.newCall(request2).execute().alsoReadBodyToTriggerResponseCaching()

        verify(okHttpBinarySerializer).serialize(
            argWhere { apiRequest ->
                !apiRequest.headers.contains(IF_NON_MATCH_HEADER to E_TAG_HEADER_VALUE)
            },
        )

        clearInvocations(okHttpBinarySerializer)

        okHttpBinarySerializerDeserializedResponse = deserializedResponse().newBuilder()
            .addHeader(E_TAG_HEADER, E_TAG_HEADER_VALUE)
            .request(request3)
            .build()
        sut.newCall(request3).execute().alsoReadBodyToTriggerResponseCaching()

        verify(okHttpBinarySerializer).serialize(
            argWhere { apiRequest ->
                apiRequest.headers.contains(IF_NON_MATCH_HEADER to E_TAG_HEADER_VALUE)
            },
        )
    }

    @Test
    fun `request with OHTTP headers should get OHTTP config from Cache when it was already cached`() {
        val relayServerResponse = stubValidRelayResponse()
        val relayConfigServerResponse = relayConfigServerResponse.clone()
            .addHeader(CACHE_CONTROL_HEADER, VALID_CACHE_CONTROL_VALUE)
        val relayRequestsCount = 5
        val expectedConfigRequests = 1

        relayConfigServer.enqueue(relayConfigServerResponse)
        repeat(relayRequestsCount) {
            relayServer.enqueue(relayServerResponse)
        }

        repeat(relayRequestsCount) {
            val sut = createSut(cacheForConfigRequests = cache)
            sut.newCall(request).execute()
        }

        assertThat(relayServer.requestCount).isEqualTo(relayRequestsCount)
        assertThat(relayConfigServer.requestCount).isEqualTo(expectedConfigRequests)
    }

    @Test
    fun `request with OHTTP headers should refresh OHTTP config when Relay responded with Unauthorized`() {
        val staleConfigResponse = MockResponse()
            .setBody("stale OHTTP Config")
            .addHeader(CACHE_CONTROL_HEADER, VALID_CACHE_CONTROL_VALUE)
        val unauthorizedRelayResponse = stubValidRelayResponse().setResponseCode(HTTP_UNAUTHORIZED)
        val validConfigResponse = MockResponse()
            .setBody("valid OHTTP Config")
            .addHeader(CACHE_CONTROL_HEADER, VALID_CACHE_CONTROL_VALUE)
        val okRelayResponse = stubValidRelayResponse()

        relayConfigServer.enqueue(staleConfigResponse)
        relayServer.enqueue(unauthorizedRelayResponse)
        relayConfigServer.enqueue(validConfigResponse)
        relayServer.enqueue(okRelayResponse)

        sut.newCall(request).execute()

        assertThat(relayServer.requestCount).isEqualTo(2)
        assertThat(relayConfigServer.requestCount).isEqualTo(2)
    }

    @Test
    fun `failed relay response should be returned when request to relay failed`() {
        val failHeader = "relay-server 500"
        val relayResponse = MockResponse()
            .setResponseCode(500)
            .addHeader(MARKER_HEADER, failHeader)
        val expectedResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Server Error")
            .addHeader(MARKER_HEADER, failHeader)
            .addHeader("Content-Length", "0")
            .build()

        relayConfigServer.enqueue(relayConfigServerResponse)
        relayServer.enqueue(relayResponse)

        val response = sut.newCall(request).execute()

        assertThat(response)
            .usingComparator(isResponseParamsExceptBodyComparator())
            .withRepresentation(responseRepresentation())
            .isEqualTo(expectedResponse)
    }

    @Test
    fun `failed target server response should be returned when target server request failed`() {
        val targetServerResponse = deserializedResponse.newBuilder()
            .code(400)
            .message("Client Error")
            .build()
        okHttpBinarySerializerDeserializedResponse = targetServerResponse
        val expectedResponse = targetServerResponse.newBuilder()
            .request(request)
            .build()

        relayConfigServer.enqueue(relayConfigServerResponse)
        relayServer.enqueue(stubValidRelayResponse())

        val response = sut.newCall(request).execute()

        assertThat(response)
            .usingComparator(isResponseParamsExceptBodyComparator())
            .withRepresentation(responseRepresentation())
            .isEqualTo(expectedResponse)
    }

    @Test
    fun `request should throw IOException when request to OHTTP Config server failed`() {
        val relayConfigServerResponse = relayConfigServerResponse.clone()
            .setResponseCode(500)

        relayConfigServer.enqueue(relayConfigServerResponse)

        assertThrows<IOException> {
            sut.newCall(request).execute()
        }
    }

    @Test
    fun `request should throw IOException when OHTTP binary serialization throws exception`() {
        okHttpBinarySerializer.stub {
            on { this.serialize(any()) } doThrow AssertionError()
        }

        relayConfigServer.enqueue(relayConfigServerResponse)
        relayServer.enqueue(stubValidRelayResponse())

        assertThrows<IOException> {
            sut.newCall(request).execute()
        }
    }

    @Test
    fun `request should throw IOException when OHTTP binary deserialization throws exception`() {
        okHttpBinarySerializer.stub {
            on { this.deserialize(any(), any(), any()) } doThrow AssertionError()
        }

        relayConfigServer.enqueue(relayConfigServerResponse)
        relayServer.enqueue(stubValidRelayResponse())

        assertThrows<IOException> {
            sut.newCall(request).execute()
        }
    }

    @Test
    fun `request should throw IOException when OHTTP request encapsulation throws exception`() {
        ohttpEncapsulator.stub {
            on { this.encapsulateRequest(any(), any()) } doThrow AssertionError()
        }

        relayConfigServer.enqueue(relayConfigServerResponse)
        relayServer.enqueue(stubValidRelayResponse())

        assertThrows<IOException> {
            sut.newCall(request).execute()
        }
    }

    @Test
    fun `request should throw IOException when OHTTP request decapsulation throws exception`() {
        ohttpDecapsulator.stub {
            on { this.decapsulateResponse(any()) } doThrow AssertionError()
        }

        relayConfigServer.enqueue(relayConfigServerResponse)
        relayServer.enqueue(stubValidRelayResponse())

        assertThrows<IOException> {
            sut.newCall(request).execute()
        }
    }

    @AfterEach
    fun afterEachTest() {
        targetServer.shutdown()
        relayConfigServer.shutdown()
        relayServer.shutdown()
    }

    private fun createSut(
        cacheForConfigRequests: Cache? = null,
        cacheForRelayRequests: Cache? = null,
    ): OkHttpClient {
        val ohttpConfig = OhttpConfig(
            relayUrl = relayUrl,
            userAgent = USER_AGENT_RELAY,
            configServerConfig = OhttpConfig.ConfigServerConfig(
                configUrl = relayConfigUrl,
                userAgent = USER_AGENT_CONFIG,
                configCache = cacheForConfigRequests,
            ),
        )

        return OkHttpClient.Builder()
            .callTimeout(okhttpClientTimeout)
            .connectTimeout(okhttpClientTimeout)
            .readTimeout(okhttpClientTimeout)
            .writeTimeout(okhttpClientTimeout)
            .run { if (cacheForRelayRequests == null) this else this.cache(cache) }
            .setupOhttp(
                config = ohttpConfig,
                isOhttpEnabled = isOhttpEnabled,
            ) {
                urlEncoder(urlEncoder)
                okHttpBinarySerializer(okHttpBinarySerializer)
                ohttpEncapsulator(ohttpEncapsulator)
            }
    }

    private fun deserializedResponse(): Response {
        return stubResponse().newBuilder()
            .body(deserializedResponseBody.toResponseBody())
            .addHeader(MARKER_HEADER, "response deserialized by OkHttpBinarySerializer")
            .build()
    }
}

private fun Response.alsoReadBodyToTriggerResponseCaching(): Response {
    this.body!!.bytes()
    return this
}

private fun stubValidRelayResponse(): MockResponse {
    return MockResponse()
        .addHeader(CONTENT_TYPE_HEADER, OHTTP_RESPONSE_CONTENT_TYPE_HEADER_VALUE)
        .setBody("body with OHTTP-encrypted response from target server")
}

private fun Headers.sorted(): List<Header> {
    return this.toHeaderList().sortedBy(Header::toString)
}

private fun MockWebServer.takeRequestWithTimeout(): RecordedRequest {
    return this.takeRequest(1, TimeUnit.SECONDS)!!
}

fun byteArrayComparator(): Comparator<ByteArray> {
    return Comparator { bytes1, bytes2 ->
        bytes1.contentEquals(bytes2).asComparatorResult()
    }
}

private fun responseRepresentation(): Representation {
    return object : StandardRepresentation() {
        override fun toStringOf(obj: Any?): String {
            return if (obj is Response) {
                super.toStringOf(obj) +
                    "\n headers: \n" +
                    obj.headers.toString()
            } else {
                super.toStringOf(obj)
            }
        }
    }
}

private fun Boolean.asComparatorResult(): Int = if (this) 0 else 1
