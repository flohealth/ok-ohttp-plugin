package health.flo.network.ohttp.client.crypto

import health.flo.network.bhttp.OkHttpBinarySerializer
import health.flo.network.bhttp.ResponseBinaryData
import health.flo.network.ohttp.client.config.OhttpCryptoConfig
import health.flo.network.ohttp.encapsulator.DecapsulationResult
import health.flo.network.ohttp.encapsulator.EncapsulationResult
import health.flo.network.ohttp.encapsulator.OhttpDecapsulator
import health.flo.network.ohttp.encapsulator.OhttpEncapsulator
import okhttp3.Request
import okhttp3.Response
import okio.IOException

internal class OhttpRelayCallEncryptor(
    private val binarySerializer: OkHttpBinarySerializer,
    private val ohttpEncapsulator: OhttpEncapsulator,
) {

    @Throws(IOException::class)
    fun encrypt(request: Request, cryptoConfig: OhttpCryptoConfig): EncryptionResult {
        val serializedRequest = binarySerializer.serialize(request)
        val encapsulationResult = ohttpEncapsulator.encapsulateRequest(serializedRequest.data, cryptoConfig.bytes)

        return when (encapsulationResult) {
            is EncapsulationResult.Success -> createSuccessResult(encapsulationResult, request)
            is EncapsulationResult.Failure -> throwOhttpEncapsulationException(encapsulationResult)
        }
    }

    private fun createSuccessResult(
        encapsulationResult: EncapsulationResult.Success,
        request: Request,
    ): EncryptionResult {
        return EncryptionResult(
            data = OhttpEncryptedRequest(encapsulationResult.data),
            decryptor = OhttpRelayCallDecryptor(
                encapsulationResult.decapsulator,
                binarySerializer,
                request,
            ),
        )
    }

    private fun throwOhttpEncapsulationException(encapsulationResult: EncapsulationResult.Failure): Nothing {
        throw OhttpEncapsulationException(
            message = encapsulationResult.message,
            additionalData = encapsulationResult.additionalData,
        )
    }

    internal class OhttpRelayCallDecryptor(
        private val oHttpDecapsulator: OhttpDecapsulator,
        private val binarySerializer: OkHttpBinarySerializer,
        private val request: Request,
    ) {

        @Throws(IOException::class)
        fun decrypt(encodedResponse: OhttpEncryptedResponse): Response {
            val decapsulationResult = oHttpDecapsulator.decapsulateResponse(encodedResponse.data)

            return when (decapsulationResult) {
                is DecapsulationResult.Success -> binarySerializer.deserialize(
                    ResponseBinaryData(decapsulationResult.data),
                    encodedResponse.protocol,
                    request,
                )
                is DecapsulationResult.Failure -> throwOhttpEncapsulationException(decapsulationResult)
                DecapsulationResult.Disposed -> throwOhttpDisposedException()
            }
        }

        fun dispose() {
            oHttpDecapsulator.dispose()
        }

        private fun throwOhttpEncapsulationException(decapsulationResult: DecapsulationResult.Failure): Nothing {
            throw OhttpEncapsulationException(
                message = decapsulationResult.message,
                additionalData = decapsulationResult.additionalData,
            )
        }

        private fun throwOhttpDisposedException(): Nothing {
            throw OhttpEncapsulationException(
                message = "OHTTP Context already disposed.",
            )
        }
    }
}
