package com.manjit.pocketmind.data.models.download

import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Privacy guard: the app may only talk to Hugging Face, and only over HTTPS.
 *
 * Installed as a NETWORK interceptor, so it also checks every redirect hop (Hugging Face
 * redirects downloads to its CDN, e.g. `cas-bridge.xethub.hf.co`). Any other host fails the
 * request before a single byte is sent. This turns "we only contact the model host" from a
 * promise into something the code enforces.
 */
object HostAllowlist : Interceptor {
    private val exactHosts = setOf("huggingface.co")
    private val hostSuffixes = listOf(".huggingface.co", ".hf.co")

    fun isAllowed(url: HttpUrl): Boolean =
        url.isHttps && (url.host in exactHosts || hostSuffixes.any { url.host.endsWith(it) })

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!isAllowed(url)) throw IOException("Blocked request to non-allowlisted host: ${url.host}")
        return chain.proceed(chain.request())
    }
}
