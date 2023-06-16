package health.flo.network.ohttp.client.interceptor

import health.flo.network.ohttp.client.HttpHeaders.USER_AGENT_HEADER
import health.flo.network.ohttp.client.executeInterception
import health.flo.network.ohttp.client.isRequestsParamsExceptBodyEqual
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val USER_AGENT_HEADER_VALUE: String = "test User-Agent"

internal class UserAgentHeaderInterceptorTest {

    private val url = "https://example.com"

    private val sut = UserAgentHeaderInterceptor(userAgent = USER_AGENT_HEADER_VALUE)

    @Test
    internal fun `intercept should add User-Agent header in request`() {
        val request = Request.Builder().url(url).build()
        val expected: Request = Request.Builder()
            .url(url)
            .addHeader(USER_AGENT_HEADER, USER_AGENT_HEADER_VALUE)
            .build()

        sut.executeInterception(request) { interceptedRequest ->
            assertTrue(isRequestsParamsExceptBodyEqual(interceptedRequest, expected))
        }
    }
}
