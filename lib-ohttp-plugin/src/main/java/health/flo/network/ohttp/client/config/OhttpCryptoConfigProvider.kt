package health.flo.network.ohttp.client.config

import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.contracts.contract

internal interface OhttpCryptoConfigProvider {

    /**
     * Blocking call providing Encryption Config to cypher OHTTP calls
     */
    fun getConfig(): OhttpCryptoConfig

    fun exchangeStaleConfig(staleCryptoConfig: OhttpCryptoConfig): OhttpCryptoConfig

    fun exchangeStaleConfig(staleCryptoConfigHash: Int): OhttpCryptoConfig
}

private val forceNetworkCacheControl: CacheControl = CacheControl.Builder().maxAge(0, TimeUnit.MILLISECONDS).build()

internal class OhttpCryptoConfigProviderImpl constructor(
    private val okhttpClient: OkHttpClient,
    private val ohttpConfigUrl: HttpUrl,
) : OhttpCryptoConfigProvider {

    private var cryptoConfig: AtomicReference<OhttpCryptoConfig?> = AtomicReference()

    override fun getConfig(): OhttpCryptoConfig = obtainFreshConfig(staleConfig = null)

    override fun exchangeStaleConfig(staleCryptoConfig: OhttpCryptoConfig): OhttpCryptoConfig =
        obtainFreshConfig(staleCryptoConfig)

    override fun exchangeStaleConfig(staleCryptoConfigHash: Int): OhttpCryptoConfig {
        val currentConfig = cryptoConfig.get()
        val staleConfig = if (currentConfig.hashCode() == staleCryptoConfigHash) currentConfig else null

        return obtainFreshConfig(staleConfig)
    }

    private fun obtainFreshConfig(staleConfig: OhttpCryptoConfig?): OhttpCryptoConfig {
        val configFast = cryptoConfig.get()

        return if (isFreshConfig(candidate = configFast, stale = staleConfig)) {
            configFast
        } else {
            synchronized(this) {
                val configSynchronized = cryptoConfig.get()
                if (isFreshConfig(candidate = configSynchronized, stale = staleConfig)) {
                    configSynchronized
                } else {
                    val forceUpdate = staleConfig != null
                    loadConfigAndApply(forceUpdate = forceUpdate)
                }
            }
        }
    }

    private fun isFreshConfig(
        candidate: OhttpCryptoConfig?,
        stale: OhttpCryptoConfig?,
    ): Boolean {
        contract {
            returns(true) implies (candidate != null)
        }

        val noStaleConfigCurrently = stale == null
        val candidateIsFresh = stale != null && candidate != stale

        return candidate != null && (noStaleConfigCurrently || candidateIsFresh)
    }

    private fun loadConfigAndApply(forceUpdate: Boolean): OhttpCryptoConfig {
        val loadedConfig = loadConfig(forceUpdate)
        cryptoConfig.set(loadedConfig)

        return loadedConfig
    }

    @Synchronized
    private fun loadConfig(forceUpdate: Boolean = false): OhttpCryptoConfig {
        val response = okhttpClient
            .newCall(
                Request.Builder()
                    .get()
                    .url(ohttpConfigUrl)
                    .forceNetwork(forceUpdate)
                    .build(),
            )
            .execute()

        return if (response.isSuccessful) {
            val responseBody = response.body
                ?: throw IOException("Empty OHTTP crypto config received!")

            OhttpCryptoConfig(responseBody.bytes())
        } else {
            throw IOException("OHTTP crypto config request failed!")
        }
    }
}

private fun Request.Builder.forceNetwork(force: Boolean): Request.Builder =
    if (force) this.cacheControl(forceNetworkCacheControl) else this
