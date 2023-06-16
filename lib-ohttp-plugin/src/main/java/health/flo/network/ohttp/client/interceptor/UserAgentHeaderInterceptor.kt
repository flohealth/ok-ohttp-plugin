package health.flo.network.ohttp.client.interceptor

import health.flo.network.ohttp.client.HttpHeaders.USER_AGENT_HEADER
import okhttp3.Interceptor
import okhttp3.Response

internal class UserAgentHeaderInterceptor(
    private val userAgent: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
            .newBuilder()
            .addHeader(USER_AGENT_HEADER, userAgent)
            .build()

        return chain.proceed(request)
    }
}
