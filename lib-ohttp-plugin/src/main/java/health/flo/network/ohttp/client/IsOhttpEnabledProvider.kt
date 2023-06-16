package health.flo.network.ohttp.client

fun interface IsOhttpEnabledProvider {

    fun isEnabledBlocking(): Boolean
}
