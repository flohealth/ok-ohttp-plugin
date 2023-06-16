package health.flo.network.ohttp.client.interceptor

import health.flo.network.ohttp.client.HttpHeaders.FORCE_OHTTP_ENABLED_HEADER
import health.flo.network.ohttp.client.IsOhttpEnabledProvider
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.io.IOException

internal class OhttpWrappingRequiredAnalyserTest {

    private val url = "https://example.com"

    private var isOhttpEnabledResult = false
    private val isOhttpEnabled: IsOhttpEnabledProvider = mock {
        on { this.isEnabledBlocking() } doAnswer { isOhttpEnabledResult }
    }

    private val sut = OhttpWrappingRequiredAnalyser(isOhttpEnabled)

    @Test
    fun `isOhttpRequired should return true when it's force disabled by header`() {
        isOhttpEnabledResult = false
        val request = Request.Builder()
            .url(url)
            .addHeader(FORCE_OHTTP_ENABLED_HEADER, "true")
            .build()
        val expected = true

        val actual = sut.isOhttpRequired(request)

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `isOhttpRequired should return false when it's force enabled by header`() {
        isOhttpEnabledResult = true
        val request = Request.Builder()
            .url(url)
            .addHeader(FORCE_OHTTP_ENABLED_HEADER, "false")
            .build()
        val expected = false

        val actual = sut.isOhttpRequired(request)

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `isOhttpRequired should throw IOException when illegal value in header`() {
        val request = Request.Builder()
            .url(url)
            .addHeader(FORCE_OHTTP_ENABLED_HEADER, "wrong value")
            .build()

        assertThrows<IOException> {
            sut.isOhttpRequired(request)
        }
    }

    @Test
    fun `isOhttpRequired should return true when OHTTP enabled on system level`() {
        isOhttpEnabledResult = true
        val request = Request.Builder()
            .url(url)
            .build()
        val expected = true

        val actual = sut.isOhttpRequired(request)

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `isOhttpRequired should return false when OHTTP disabled on system level`() {
        isOhttpEnabledResult = false
        val request = Request.Builder()
            .url(url)
            .build()
        val expected = false

        val actual = sut.isOhttpRequired(request)

        assertThat(actual).isEqualTo(expected)
    }
}
