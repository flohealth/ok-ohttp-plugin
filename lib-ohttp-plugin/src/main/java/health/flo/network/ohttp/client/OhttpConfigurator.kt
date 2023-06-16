package health.flo.network.ohttp.client

import health.flo.network.bhttp.OkHttpBinarySerializer
import health.flo.network.ohttp.client.config.OhttpCryptoConfigProvider
import health.flo.network.ohttp.client.config.OhttpCryptoConfigProviderImpl
import health.flo.network.ohttp.client.crypto.OhttpRelayCallEncryptor
import health.flo.network.ohttp.client.interceptor.CacheLayerPacker
import health.flo.network.ohttp.client.interceptor.OhttpNetworkInterceptor
import health.flo.network.ohttp.client.interceptor.OhttpPreprocessingInterceptor
import health.flo.network.ohttp.client.interceptor.OhttpRequestProcessor
import health.flo.network.ohttp.client.interceptor.OhttpWrappingRequiredAnalyser
import health.flo.network.ohttp.client.interceptor.UserAgentHeaderInterceptor
import health.flo.network.ohttp.client.relay.OhttpRelayCallPacker
import health.flo.network.ohttp.client.utils.Md5UrlEncoder
import health.flo.network.ohttp.client.utils.UrlEncoder
import health.flo.network.ohttp.encapsulator.OhttpEncapsulator
import okhttp3.Cache
import okhttp3.OkHttpClient
import kotlin.time.toJavaDuration
import health.flo.network.ohttp.encapsulator.ohttpEncapsulator as createOhttpEncapsulator

class OhttpConfigurator private constructor(
    private val preprocessingInterceptor: OhttpPreprocessingInterceptor,
    private val networkInterceptor: OhttpNetworkInterceptor,
) {

    /**
     * OHTTP setup must be called after all other Interceptors added
     */
    fun setupOhttp(
        clientBuilder: OkHttpClient.Builder,
    ): OkHttpClient {
        return clientBuilder
            .addInterceptor(preprocessingInterceptor)
            .addNetworkInterceptor(networkInterceptor)
            .build()
    }

    companion object {

        fun newBuilder(
            config: OhttpConfig,
            isOhttpEnabled: IsOhttpEnabledProvider,
        ): Builder =
            Builder(
                config = config,
                isOhttpEnabled = isOhttpEnabled,
            )
    }

    class Builder(
        private val config: OhttpConfig,
        private val isOhttpEnabled: IsOhttpEnabledProvider,
    ) {

        private val ohttpPreprocessingInterceptor: () -> OhttpPreprocessingInterceptor = {
            OhttpPreprocessingInterceptor(
                ohttpWrappingRequiredAnalyser = ohttpWrappingRequiredAnalyser(),
                relayUrl = config.relayUrl,
                cacheLayerPacker = cacheLayerPacker(),
            )
        }

        private val ohttpNetworkInterceptor: () -> OhttpNetworkInterceptor = {
            OhttpNetworkInterceptor(
                cacheLayerPacker = cacheLayerPacker(),
                ohttpRequestProcessor = ohttpRequestProcessor(),
            )
        }

        private val ohttpWrappingRequiredAnalyser: () -> OhttpWrappingRequiredAnalyser = {
            OhttpWrappingRequiredAnalyser(
                isOhttpEnabled = createIsOhttpEnabled(),
            )
        }

        private val createIsOhttpEnabled: () -> IsOhttpEnabledProvider = { isOhttpEnabled }

        private val cacheLayerPacker: () -> CacheLayerPacker = {
            CacheLayerPacker(urlEncoder = urlEncoder())
        }

        private var urlEncoder: () -> UrlEncoder = { Md5UrlEncoder() }

        private val ohttpRequestProcessor: () -> OhttpRequestProcessor = {
            OhttpRequestProcessor(
                userAgent = config.userAgent,
                ohttpRelayRequestPacker = ohttpRelayRequestPacker(),
                cryptoConfigProvider = cryptoConfigProvider(),
            )
        }

        private val ohttpRelayRequestPacker: () -> OhttpRelayCallPacker = {
            OhttpRelayCallPacker(
                relayUrl = config.relayUrl,
                encryptor = OhttpRelayCallEncryptor(binarySerializer(), ohttpEncapsulator()),
            )
        }

        private var binarySerializer: () -> OkHttpBinarySerializer = {
            OkHttpBinarySerializer.create()
        }

        private var ohttpEncapsulator: () -> OhttpEncapsulator = {
            createOhttpEncapsulator()
        }

        private val cryptoConfigProvider: () -> OhttpCryptoConfigProvider = {
            OhttpCryptoConfigProviderImpl(
                okhttpClient = ohttpConfigOkHttpClient(),
                ohttpConfigUrl = config.configServerConfig.configUrl,
            )
        }

        private val ohttpConfigOkHttpClient: () -> OkHttpClient = {
            val cfg = config.configServerConfig
            val cache: Cache? = cfg.configCache

            OkHttpClient.Builder()
                .connectTimeout(cfg.connectTimeout.toJavaDuration())
                .readTimeout(cfg.readTimeout.toJavaDuration())
                .writeTimeout(cfg.writeTimeout.toJavaDuration())
                .run { if (cache != null) this.cache(cache) else this }
                .retryOnConnectionFailure(cfg.retryOnConnectionFailure)
                .addInterceptor(ohttpConfigUserAgentHeaderInterceptor())
                .build()
        }

        private val ohttpConfigUserAgentHeaderInterceptor: () -> UserAgentHeaderInterceptor = {
            UserAgentHeaderInterceptor(ohttpConfigUserAgent())
        }

        private val ohttpConfigUserAgent: () -> String = {
            config.configServerConfig.userAgent ?: config.userAgent
        }

        fun urlEncoder(urlEncoder: UrlEncoder): Builder {
            this.urlEncoder = { urlEncoder }
            return this
        }

        fun okHttpBinarySerializer(serializer: OkHttpBinarySerializer): Builder {
            binarySerializer = { serializer }
            return this
        }

        fun ohttpEncapsulator(encapsulator: OhttpEncapsulator): Builder {
            ohttpEncapsulator = { encapsulator }
            return this
        }

        fun build(): OhttpConfigurator {
            return OhttpConfigurator(
                preprocessingInterceptor = ohttpPreprocessingInterceptor(),
                networkInterceptor = ohttpNetworkInterceptor(),
            )
        }
    }
}

/**
 * OHTTP setup must be called after all other Interceptors added
 */
fun OkHttpClient.Builder.setupOhttp(
    config: OhttpConfig,
    isOhttpEnabled: IsOhttpEnabledProvider,
    configure: OhttpConfigurator.Builder.() -> Unit = {},
): OkHttpClient {
    val configurator = OhttpConfigurator
        .newBuilder(config, isOhttpEnabled)
        .apply(configure)
        .build()

    return this.setupOhttp(configurator)
}

/**
 * OHTTP setup must be called after all other Interceptors added
 */
fun OkHttpClient.Builder.setupOhttp(
    configurator: OhttpConfigurator,
): OkHttpClient {
    return configurator.setupOhttp(this)
}
