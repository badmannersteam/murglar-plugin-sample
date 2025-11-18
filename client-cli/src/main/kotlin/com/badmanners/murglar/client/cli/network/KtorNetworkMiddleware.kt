package com.badmanners.murglar.client.cli.network

import com.badmanners.murglar.lib.core.network.NetworkMiddleware
import com.badmanners.murglar.lib.core.network.NetworkRequest
import com.badmanners.murglar.lib.core.network.NetworkResponse
import com.badmanners.murglar.lib.core.network.ResponseConversionException
import com.badmanners.murglar.lib.core.network.ResponseConverter
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpSendInterceptor
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.addCookie
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.client.request.basicAuth
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.http.userAgent
import io.ktor.http.withCharset
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.net.HttpCookie
import java.nio.charset.StandardCharsets
import java.text.Normalizer


class KtorNetworkMiddleware(
    private val cookiesStorage: EnhancedCookiesStorage,
    private val httpClient: HttpClient = httpClient(),
) : NetworkMiddleware {

    companion object {
        fun httpClient() = HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 30000
            }

            ContentEncoding()

            install(Logging) {
                level = LogLevel.ALL
            }
        }.applyInterceptors()

        fun HttpClient.configWithInterceptors(block: HttpClientConfig<*>.() -> Unit) =
            config(block).applyInterceptors()

        fun HttpClient.applyInterceptors() = apply {
            plugin(HttpSend).intercept(basicAuthFromUrlInterceptor)
        }

        val basicAuthFromUrlInterceptor: HttpSendInterceptor = { builder ->
            val user = builder.url.user
            val password = builder.url.password

            if (user != null) {
                builder.url.user = null
                builder.url.password = null
                builder.basicAuth(user, password ?: "")
            }

            execute(builder)
        }
    }

    override fun getCookies(domain: String): List<HttpCookie> = runBlocking {
        cookiesStorage.get(Url("https://$domain")).map(::toJavaCookie)
    }

    override fun getCookie(domain: String, name: String): HttpCookie? =
        getCookies(domain).firstOrNull { it.name == name }

    override fun addCookie(cookie: HttpCookie) {
        val ktorCookie = Cookie(
            name = cookie.name,
            value = cookie.value.normalize(),
            encoding = CookieEncoding.RAW,
            maxAge = cookie.maxAge.takeIf { it > 0 }?.toInt(),
            domain = cookie.domain,
            path = cookie.path,
            secure = cookie.secure,
            httpOnly = cookie.isHttpOnly
        )

        runBlocking {
            cookiesStorage.addCookie(cookie.domain, ktorCookie)
        }
    }

    override fun clearCookiesForDomains(vararg domains: String) = runBlocking {
        domains.forEach { cookiesStorage.clear(it) }
    }

    override fun clearAllCookies() = runBlocking { cookiesStorage.clearAll() }

    override suspend fun <T> execute(request: NetworkRequest, converter: ResponseConverter<T>): NetworkResponse<T> =
        createRequest(request).execute { response -> toResponse(request, converter, response) }

    private fun toJavaCookie(ktorCookie: Cookie) = HttpCookie(ktorCookie.name, ktorCookie.value).apply {
        maxAge = cookiesStorage.maxAgeOrExpires(ktorCookie) ?: -1
        domain = ktorCookie.domain
        path = ktorCookie.path
        secure = ktorCookie.secure
        isHttpOnly = ktorCookie.httpOnly
    }

    private suspend fun createRequest(request: NetworkRequest) = httpClient
        .configWithInterceptors {
            install(HttpCookies) {
                storage = cookiesStorage
            }

            followRedirects = request.followRedirects
        }
        .prepareRequest(rebuildUrl(request.url)) {
            method = HttpMethod.parse(request.method)

            request.headers.forEach {
                headers.append(it.key, it.value)
            }

            request.userAgent?.let(::userAgent)

            if (request.hasBody) {
                setBody(request.body)
                val contentType = request.contentType!!
                contentType(ContentType.parse(contentType.mimeType).withCharset(contentType.charset))

            } else if (method == HttpMethod.Get || method == HttpMethod.Put) {
                request.parameters.forEach {
                    parameter(it.key, it.value)
                }
            } else {
                setBody(FormDataContent(parameters {
                    request.parameters.forEach {
                        append(it.key, it.value)
                    }
                }))
            }
        }

    private suspend fun <T> toResponse(
        request: NetworkRequest,
        converter: ResponseConverter<T>,
        response: HttpResponse
    ): NetworkResponse<T> {
        val statusCode = response.status.value
        val message = response.status.description
        val headers = response.headers.entries().map { Pair(it.key, it.value.firstOrNull() ?: "") }

        val result = response.takeIf { !converter.dropBody }
            ?.body<InputStream>()
            ?.let { stream ->
                stream.use {
                    try {
                        val charset = response.charset() ?: StandardCharsets.UTF_8
                        converter.convert(stream, charset)
                    } catch (e: Exception) {
                        throw ResponseConversionException(request, e, headers, statusCode, message)
                    }
                }
            }
            ?: converter.emptyResult

        return NetworkResponse(request, result, headers, statusCode, message)
    }

    private fun rebuildUrl(url: String) = Url(url).let {
        URLBuilder(it.protocol, it.host, it.port, it.user, it.password, it.rawSegments, it.parameters, it.fragment,
            it.trailingQuery).build()
    }

    private fun String.normalize() = Normalizer.normalize(this, Normalizer.Form.NFD).filter { it < 256.toChar() }
}
