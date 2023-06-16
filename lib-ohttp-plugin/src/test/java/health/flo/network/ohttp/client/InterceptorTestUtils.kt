package health.flo.network.ohttp.client

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Protocol.HTTP_2
import okhttp3.Request
import okhttp3.Response
import okhttp3.Response.Builder
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

fun stubChain(
    request: Request,
    response: Response,
    handledRequests: MutableList<Request?>,
): Chain {
    val chain: Chain = mock {
        on { request() } doReturn request
        on { proceed(any()) } doAnswer { invocationOnMock: InvocationOnMock ->
            handledRequests.add(invocationOnMock.getArgument<Request>(0))
            response
        }
    }
    return chain
}

fun Interceptor.executeInterception(request: Request, onResult: (Request) -> Unit) {
    executeInterception(request, stubResponse(request).build(), onResult, null)
}

fun Interceptor.executeInterception(request: Request, response: Response, onRequest: (Request) -> Unit) {
    executeInterception(request, response, onRequest, null)
}

fun Interceptor.executeInterception(
    request: Request,
    response: Response,
    onRequest: ((Request) -> Unit)?,
    onResponse: ((Response) -> Unit)?,
) {
    val interceptedRequests = mutableListOf<Request?>()
    val chain: Chain = stubChain(request, response, interceptedRequests)

    val resultingResponse = this.intercept(chain)

    val interceptedRequest: Request = interceptedRequests[0]!!
    onRequest?.invoke(interceptedRequest)
    onResponse?.invoke(resultingResponse)
}

fun eqRequest(request: Request): Request = argWhere { actual -> isRequestsParamsExceptBodyEqual(request, actual) }

fun isRequestsParamsExceptBodyEqualComparator(): Comparator<Request> {
    return Comparator { request1, request2 ->
        if (isRequestsParamsExceptBodyEqual(request1, request2)) 0 else -1
    }
}

fun eqResponse(response: Response): Response = argWhere { actual -> isResponseParamsExceptBodyEqual(response, actual) }

/**
 * Request doesn't have equal because it could be mutable.
 * Method checks only immutable request's fields equality.
 *
 */
fun isRequestsParamsExceptBodyEqual(request1: Request, request2: Request): Boolean {
    val headers1 = request1.headers.sorted()
    val headers2 = request2.headers.sorted()
    return request1.url == request2.url
        && request1.method == request2.method
        && headers1 == headers2
}

fun isResponseParamsExceptBodyComparator(): Comparator<Response> {
    return Comparator { response1, response2 ->
        if (isResponseParamsExceptBodyEqual(response1, response2)) 0 else -1
    }
}

fun isResponseParamsExceptBodyEqual(response1: Response, response2: Response): Boolean {
    val headers1 = response1.headers.sorted()
    val headers2 = response2.headers.sorted()
    return isRequestsParamsExceptBodyEqual(response1.request, response2.request)
        && response1.code == response2.code
        && response1.protocol == response2.protocol
        && response1.message == response2.message
        && headers1 == headers2
}

private fun Headers.sorted(): List<Pair<String, List<String>>> {
    return this
        .toMultimap()
        .toList()
        .sortedBy { (key, value) -> key + value }
}

fun stubResponse(request: Request): Builder {
    return Builder()
        .request(request)
        .protocol(HTTP_2)
        .code(200)
        .message("test request message")
}
