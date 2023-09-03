package com.badmanners.murglar.client.cli.network

import com.badmanners.murglar.lib.core.network.NetworkMiddleware
import com.badmanners.murglar.lib.core.network.NetworkRequest
import com.badmanners.murglar.lib.core.network.NetworkResponse
import com.badmanners.murglar.lib.core.network.ResponseConverter
import org.apache.http.HttpHeaders
import org.apache.http.HttpResponse
import org.apache.http.client.CookieStore
import org.apache.http.client.HttpClient
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.cookie.ClientCookie
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.client.LaxRedirectStrategy
import org.apache.http.impl.cookie.BasicClientCookie
import org.apache.http.protocol.BasicHttpContext
import java.io.File
import java.net.HttpCookie
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date


/**
 * [NetworkMiddleware] impl that uses Apache [HttpClient] under the hood.
 */
class ApacheHttpClientNetworkMiddleware(
    private val cookieStore: CookieStore = BinaryFilePersistedCookieStore(File("cookies.dat")),
    private val syncClient: CloseableHttpClient = httpClient(cookieStore)
) : NetworkMiddleware {

    companion object {
        var DEBUG = true

        fun httpClient(cookieStore: CookieStore): CloseableHttpClient = HttpClients.custom()
            .setDefaultCookieStore(cookieStore)
            .setRedirectStrategy(LaxRedirectStrategy.INSTANCE)
            .build()
    }

    override fun getCookies(domain: String): List<HttpCookie> = cookieStore.cookies.asSequence()
        .filterIsInstance<BasicClientCookie>()
        .filter { matchesDomain(domain, it) }
        .map { it.toJavaCookie() }
        .toList()

    override fun getCookie(domain: String, name: String): HttpCookie? = getCookies(domain).firstOrNull {
        it.name == name
    }

    override fun addCookie(cookie: HttpCookie) {
        val now = Instant.now()
        val domain = cookie.domain
        val normalizedDomain = when {
            domain.startsWith(".") -> domain.substring(1)
            else -> domain
        }
        val path = cookie.path
        val secure = cookie.secure
        val httpOnly = cookie.isHttpOnly

        val apacheCookie = BasicClientCookie(cookie.name, cookie.value)
        apacheCookie.domain = normalizedDomain
        apacheCookie.path = path
        apacheCookie.isSecure = secure
        apacheCookie.version = cookie.version
        apacheCookie.creationDate = Date.from(now)
        if (cookie.maxAge > 0)
            apacheCookie.expiryDate = Date.from(now.plusSeconds(cookie.maxAge))

        apacheCookie.setAttribute(ClientCookie.DOMAIN_ATTR, domain)
        apacheCookie.setAttribute(ClientCookie.PATH_ATTR, path)
        if (secure)
            apacheCookie.setAttribute(ClientCookie.SECURE_ATTR, null)
        if (httpOnly)
            apacheCookie.setAttribute("httponly", null)

        cookieStore.addCookie(apacheCookie)
    }

    override fun clearCookiesForDomains(vararg domains: String) = domains.forEach { domain ->
        cookieStore.cookies.asSequence()
            .filterIsInstance<BasicClientCookie>()
            .filter { matchesDomain(domain, it) }
            .forEach {
                it.expiryDate = Date(0)
                cookieStore.addCookie(it)
            }
    }

    override fun clearAllCookies() = cookieStore.clear()

    override fun <T> execute(request: NetworkRequest, converter: ResponseConverter<T>): NetworkResponse<T> {
        val context = BasicHttpContext()
        try {
            syncClient.execute(request.toHttpUriRequest(), context).use { response ->
                return toNetworkResponse(request, converter, response)
            }
        } finally {
            if (DEBUG)
                println("${ApacheHttpClientNetworkMiddleware::class.java.simpleName}: $context")
        }
    }

    private fun NetworkRequest.toHttpUriRequest(): HttpUriRequest {
        val builder = RequestBuilder.create(method)
            .setUri(url)

        builder.config = RequestConfig.custom()
            .setCircularRedirectsAllowed(true)
            .setRedirectsEnabled(followRedirects)
            .setCookieSpec(CookieSpecs.STANDARD)
            .build()

        if (hasUserAgent)
            builder.setHeader(HttpHeaders.USER_AGENT, userAgent)

        headers.forEach { builder.addHeader(it.key, it.value) }

        if (hasBody) {
            val contentType = contentType?.let { ContentType.create(it.mimeType, it.charset) }
            builder.entity = StringEntity(body, contentType)
        }

        parameters.forEach { builder.addParameter(it.key, it.value) }

        return builder.build()
    }

    private fun <T> toNetworkResponse(
        request: NetworkRequest,
        converter: ResponseConverter<T>,
        response: HttpResponse
    ): NetworkResponse<T> {
        val statusCode = response.statusLine.statusCode
        val reasonPhrase = response.statusLine.reasonPhrase
        val headers = response.allHeaders.map { Pair(it.name, it.value) }

        val result = response.entity
            ?.takeIf { !converter.dropBody }
            ?.let { entity ->
                entity.content?.let { stream ->
                    val charset = ContentType.get(entity)?.charset ?: StandardCharsets.UTF_8
                    converter.convert(stream, charset)
                }
            }
            ?: converter.emptyResult

        return NetworkResponse(request, result, headers, statusCode, reasonPhrase)
    }

    private fun matchesDomain(requestedDomain: String, cookie: BasicClientCookie): Boolean {
        val normalizedCookieDomain = cookie.domain
        val cookieDomain = cookie.getAttribute(ClientCookie.DOMAIN_ATTR) ?: normalizedCookieDomain
        return when {
            cookieDomain.startsWith(".") -> requestedDomain.endsWith(normalizedCookieDomain)
            else -> requestedDomain == normalizedCookieDomain
        }
    }

    private fun BasicClientCookie.toJavaCookie() = HttpCookie(name, value).apply {
        domain = getAttribute(ClientCookie.DOMAIN_ATTR) ?: this@toJavaCookie.domain

        path = this@toJavaCookie.path
        isHttpOnly = containsAttribute("httponly")
        secure = isSecure
        version = this@toJavaCookie.version

        maxAge = expiryDate?.let {
            it.toInstant().epochSecond - Instant.now().epochSecond
        } ?: -1
    }
}