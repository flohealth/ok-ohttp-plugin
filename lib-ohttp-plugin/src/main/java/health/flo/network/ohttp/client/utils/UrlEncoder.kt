package health.flo.network.ohttp.client.utils

import android.util.Base64
import android.util.Base64.NO_PADDING
import android.util.Base64.NO_WRAP
import android.util.Base64.URL_SAFE
import java.security.MessageDigest

private const val MD_5 = "MD5"
private const val URL_SAFE_FLAGS: Int = URL_SAFE or NO_PADDING or NO_WRAP

interface UrlEncoder {

    fun encodeUrlSafe(stringToEncode: String): String
}

internal class Md5UrlEncoder : UrlEncoder {

    override fun encodeUrlSafe(stringToEncode: String): String {
        return Base64.encodeToString(
            /* input = */ createMd5Hash(stringToEncode),
            /* flags = */ URL_SAFE_FLAGS,
        )
    }

    private fun createMd5Hash(data: String): ByteArray {
        return MessageDigest.getInstance(MD_5)
            .digest(data.toByteArray())
    }
}
