package health.flo.network.ohttp.client.config

import health.flo.network.ohttp.client.eqRequest
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class OhttpCryptoConfigProviderImplTest {

    private val staleConfig = OhttpCryptoConfig(byteArrayOf(0))
    private val responseConfig = OhttpCryptoConfig(byteArrayOf(42, 42))

    private val okhttpClientResponse: Response = mock {
        on { this.isSuccessful } doReturn true
        on { this.body } doReturn responseConfig.bytes.toResponseBody()
    }
    private val call: Call = mock {
        on { this.execute() } doAnswer { okhttpClientResponse }
    }
    private val okhttpClient: OkHttpClient = mock {
        on { this.newCall(any()) } doReturn call
    }

    private val configUrl: HttpUrl = mock()

    private val sut = OhttpCryptoConfigProviderImpl(
        okhttpClient,
        configUrl,
    )

    @Test
    fun `getConfig should throw IOException when crypto config request received with empty body`() {
        okhttpClientResponse.stub { on { this.body } doReturn null }

        assertThrows<IOException> {
            sut.getConfig()
        }
    }

    @Test
    fun `getConfig should throw IOException when crypto config request was unsuccessful`() {
        okhttpClientResponse.stub { on { this.isSuccessful } doReturn false }

        assertThrows<IOException> {
            sut.getConfig()
        }
    }

    @Test
    fun `getConfig should return downloaded crypto config when config request was successful`() {
        val expected = responseConfig
        val expectedRequest = Request.Builder()
            .get()
            .url(configUrl)
            .build()

        val actual = sut.getConfig()

        assertThat(actual.bytes).isEqualTo(expected.bytes)
        verify(okhttpClient, times(1)).newCall(eqRequest(expectedRequest))
    }

    @Test
    fun `getConfig should return cached crypto config when config is already loaded`() {
        repeat(3) { sut.getConfig() }

        verify(okhttpClient, times(1)).newCall(any())
    }

    @Test
    fun `exchangeStaleConfig should force crypto config reloading from backend & return refreshed config when request was successful`() {
        val expected = responseConfig
        val forceReloadCacheControl = CacheControl.Builder().maxAge(0, TimeUnit.MILLISECONDS).build()
        val expectedRequest = Request.Builder()
            .get()
            .url(configUrl)
            .cacheControl(forceReloadCacheControl)
            .build()

        val actual = sut.exchangeStaleConfig(staleConfig)

        assertThat(actual.bytes).isEqualTo(expected.bytes)
        verify(okhttpClient, times(1)).newCall(eqRequest(expectedRequest))
    }

    @Test
    fun `exchangeStaleConfig should return cached crypto config when passed stale config was already refreshed`() {
        val expected = responseConfig

        repeat(3) { sut.exchangeStaleConfig(staleConfig) }
        val actual = sut.exchangeStaleConfig(staleConfig)

        assertThat(actual.bytes).isEqualTo(expected.bytes)
        verify(okhttpClient, times(1)).newCall(any())
    }
}
