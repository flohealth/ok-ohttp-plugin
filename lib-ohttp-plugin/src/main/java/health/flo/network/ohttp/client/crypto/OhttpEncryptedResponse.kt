package health.flo.network.ohttp.client.crypto

import okhttp3.Protocol

internal data class OhttpEncryptedResponse(
    val protocol: Protocol,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OhttpEncryptedResponse

        if (protocol != other.protocol) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = protocol.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
