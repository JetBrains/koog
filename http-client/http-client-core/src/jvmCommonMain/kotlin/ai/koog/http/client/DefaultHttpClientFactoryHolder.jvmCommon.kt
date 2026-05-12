package ai.koog.http.client

import java.util.ServiceLoader

internal actual fun loadKoogHttpClientFactories(): List<KoogHttpClient.Factory> =
    ServiceLoader.load(KoogHttpClient.Factory::class.java).toList()
