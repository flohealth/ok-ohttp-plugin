package health.flo.network.gradle

import com.android.build.api.dsl.LibraryExtension

data class SdkConfig(
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int,
)

private val defaultSdkConfig = SdkConfig(
    minSdk = 26,
    targetSdk = 31,
    compileSdk = 33,
)

fun LibraryExtension.configureSdk(sdkConfig: SdkConfig = defaultSdkConfig) {
    compileSdk = sdkConfig.compileSdk
    defaultConfig {
        minSdk = sdkConfig.minSdk
    }
}
