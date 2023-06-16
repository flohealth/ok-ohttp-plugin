package health.flo.network.ohttp.client.crypto

internal class EncryptionResult(
    val data: OhttpEncryptedRequest,
    val decryptor: OhttpRelayCallEncryptor.OhttpRelayCallDecryptor,
)
