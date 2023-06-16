package health.flo.network.ohttp.client

object HttpHeaders {

    const val FORCE_OHTTP_ENABLED_HEADER: String = "X-Local-Force-Ohttp-Enabled"
    const val FORCE_OHTTP_ENABLED_HEADER_ALWAYS_ENABLED: String = "$FORCE_OHTTP_ENABLED_HEADER: true"
    const val FORCE_OHTTP_ENABLED_HEADER_ALWAYS_DISABLED: String = "$FORCE_OHTTP_ENABLED_HEADER: false"

    internal const val OHTTP_ORIGINAL_URL_HEADER: String = "X-Local-Ohttp-Original-Request-Url"

    const val CONTENT_TYPE_HEADER: String = "Content-Type"
    const val OHTTP_REQUEST_CONTENT_TYPE_HEADER_VALUE: String = "message/ohttp-req"
    const val OHTTP_RESPONSE_CONTENT_TYPE_HEADER_VALUE: String = "message/ohttp-res"

    const val HOST_HEADER: String = "Host"
    const val CONNECTION_HEADER: String = "Connection"
    const val CONNECTION_HEADER_VALUE_KEEP_ALIVE: String = "Keep-Alive"
    const val CONTENT_LENGTH_HEADER: String = "Content-Length"

    const val USER_AGENT_HEADER: String = "User-Agent"

    internal const val OHTTP_ERROR_STALE_CONFIG_HEADER: String = "X-Local-Ohttp-Error-Stale-Config"
    internal const val OHTTP_REFRESH_STALE_CONFIG_HEADER: String = "X-Local-Ohttp-Refresh-Stale-Config"
}
