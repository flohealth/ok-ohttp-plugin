# Oblivious HTTP Implementation for OkHttp

This project is a set of OkHttp Interceptors that
brings [Oblivious HTTP](https://datatracker.ietf.org/doc/draft-ietf-ohai-ohttp/) support to Android apps
with [OkHttp client](https://github.com/square/okhttp). <br/>
Requests to an OHTTP Gateway are serialized into [Binary HTTP](https://datatracker.ietf.org/doc/rfc9292/) format
by [ok-bhttp](https://github.com/flohealth/ok-bhttp) and encapsulated/decapsulated
by [ok-ohttp-encapsulator](https://github.com/flohealth/ok-ohttp-encapsulator). <br />
It is compatible with the OHTTP [relay](https://github.com/cloudflare/privacy-gateway-relay) and
corresponding [server](https://github.com/cloudflare/privacy-gateway-server-go) implementations from Cloudflare. <br />

## Download

#### Declare Gradle dependencies
```kotlin
dependencies {
    implementation("com.github.flohealth:ok-ohttp-plugin:0.2.0")
}
```
#### Download artifacts
You can download the following artifacts: <br />
- ok-bhttp: [GitHub Releases](https://github.com/flohealth/ok-bhttp/releases) <br />
- ok-ohttp-encapsulator: [GitHub Releases](https://github.com/flohealth/ok-ohttp-encapsulator/releases) <br />
- ok-ohttp-plugin: [GitHub Releases](https://github.com/flohealth/ok-ohttp-plugin/releases) <br />

## Usage

#### Setup OkHttp client
```kotlin
import okhttp3.cache

val configRequestsCache: Cache

// provide your IsOhttpEnabledProvider implementation if you need to enable/disable OHTTP in runtime
val isOhttpEnabled: IsOhttpEnabledProvider = IsOhttpEnabledProvider { true }

val ohttpConfig = OhttpConfig(
     relayUrl = "https://example.com/ohttp-relay".toHttpUrl(), // relay server
     userAgent = "Minimal User Agent", // user agent for OHTTP requests to the relay server
     configServerConfig = OhttpConfig.ConfigServerConfig(
         configUrl = "https://example.com/ohttp-config".toHttpUrl(), // crypto config
         configCache = configRequestsCache,
     ),
)

val okHttpClient: OkHttpClient = OkHttpClient.Builder()
     .addInterceptor(myInterceptor) // add all your interceptors
     .addNetworkInterceptor(myNetworkInterceptor) // add all your network interceptors
     .setupOhttp( // setup OHTTP as the final step
         config=ohttpConfig,
         isOhttpEnabled = isOhttpEnabled,
     )

// use your OkHttpClient as usual
```

<br />

The `IsOhttpEnabledProvider` is called on every request; keep in mind the potential performance penalty during
implementation.

Call `setupOhttp` after adding any other interceptors.
Any Network Interceptor added after `setupOhttp` will modify not your API call request but the request to OHTTP Relay.
This could bring unexpected behavior in work with OHTTP Relay and expose unwanted information about the user.

If you build several OkHttp clients, we suggest creating a single instance of OhttpConfigurator and configuring all your
OkHttp clients with it.
This will reduce the amount of OHTTP CryptoConfig requests.

By the nature of Oblivious HTTP, you can't inspect OHTTP traffic using sniffers.
For debugging purposes, you can still use logs to see the requests & response content (
e.g. [OkHttp Logging Interceptor](https://github.com/square/okhttp/tree/master/okhttp-logging-interceptor))

Though all OHTTP requests are transformed into POST requests, user requests are still cached by the OkHttp cache.

## Limitations

As OHTTP Plugin significantly changes the client-server interaction and protocol, we can't prove that every feature of the HTTP protocol & OkHttp client will correctly work with OHTTP enabled.
Please perform proper testing for your cases before use.

All limitations of [ok-bhttp](https://github.com/flohealth/ok-bhttp) and [ok-ohttp-encapsulator](https://github.com/flohealth/ok-ohttp-encapsulator) are applied .

## License

Released under [**MIT License**](LICENSE.txt).
