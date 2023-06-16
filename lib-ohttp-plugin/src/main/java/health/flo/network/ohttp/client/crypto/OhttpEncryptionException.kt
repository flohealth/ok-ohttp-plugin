package health.flo.network.ohttp.client.crypto

import okio.IOException

internal class OhttpEncryptionException(
    message: String,
    cause: Throwable? = null,
    val additionalData: Map<String, Any> = emptyMap(),
) : IOException(message, cause) {

    override fun toString(): String {
        return super.toString() + "\nadditionalData=$additionalData"
    }
}
