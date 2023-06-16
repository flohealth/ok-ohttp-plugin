package health.flo.network.ohttp.client

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

internal object DataFactory {

    val TEST_URL = "https://flo.health".toHttpUrl()

    fun stubRequest(url: HttpUrl = TEST_URL): Request = Request.Builder()
        .url(url)
        .build()

    fun stubByteData() = ByteArray(0)

    fun stubResponse(request: Request = stubRequest()): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
    }
}
