package com.badmanners.murglar.client.cli.network

import com.badmanners.murglar.lib.core.log.LoggerMiddleware
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.fillDefaults
import io.ktor.client.plugins.cookies.matches
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min


interface EnhancedCookiesStorage : CookiesStorage {

    fun maxAgeOrExpires(cookie: Cookie): Long?

    suspend fun clear(domain: String)

    suspend fun clearAll()
}

object StubCookiesStorage : EnhancedCookiesStorage {
    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {}
    override suspend fun get(requestUrl: Url) = emptyList<Cookie>()
    override fun maxAgeOrExpires(cookie: Cookie) = null
    override suspend fun clear(domain: String) {}
    override suspend fun clearAll() {}
    override fun close() {}
}

@OptIn(ExperimentalSerializationApi::class)
class JsonPersistedCookiesStorage(
    private val cookieFile: File,
    private val logger: LoggerMiddleware
) : EnhancedCookiesStorage {

    private val json = Json { prettyPrint = true }

    @Serializable
    private data class CookieWithTimestamp(
        @Serializable(with = CookieSerializer::class)
        val cookie: Cookie,
        val createdAt: Long
    )

    private val container: MutableList<CookieWithTimestamp> = mutableListOf()

    private val oldestCookie: AtomicLong = AtomicLong(0L)

    private val mutex = Mutex()

    private var saveJob: Job? = null
    private val saveMutex = Mutex()


    init {
        try {
            cookieFile.takeIf { it.exists() }?.inputStream()?.use {
                container.addAll(json.decodeFromStream<List<CookieWithTimestamp>>(it))
            }
        } catch (e: Exception) {
            logger.w("JsonPersistedCookiesStorage", "Can't load cookies!", e)
        }
    }

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        val now = getTimeMillis()
        if (now >= oldestCookie.get()) cleanup(now)

        return@withLock container.filter { it.cookie.matches(requestUrl) }.map { it.cookie }
    }

    override fun maxAgeOrExpires(cookie: Cookie): Long? = container
        .firstOrNull { it.cookie == cookie }
        .let { it?.cookie?.maxAgeOrExpires(it.createdAt) }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        if (cookie.name.isBlank())
            return

        editAndPersistLater {
            container.removeAll { (existingCookie, _) ->
                existingCookie.name == cookie.name && existingCookie.matches(requestUrl)
            }
            val createdAt = getTimeMillis()
            container.add(CookieWithTimestamp(cookie.fillDefaults(requestUrl), createdAt))

            cookie.maxAgeOrExpires(createdAt)?.let {
                if (oldestCookie.get() > it) {
                    oldestCookie.set(it)
                }
            }
        }
    }

    override suspend fun clear(domain: String) = editAndPersistLater {
        container.removeAll { (cookie, _) ->
            val cookieDomain = cookie.domain!!
            val normalizedCookieDomain = cookieDomain.removePrefix(".")
            when {
                cookieDomain.startsWith(".") -> domain.endsWith(normalizedCookieDomain)
                else -> domain == normalizedCookieDomain
            }
        }
    }

    override suspend fun clearAll() = editAndPersistLater { container.clear() }

    override fun close() {
        runBlocking { saveJob?.join() }
    }

    private suspend fun editAndPersistLater(action: () -> Unit) = mutex.withLock {
        saveJob?.cancel()

        action()

        @OptIn(DelicateCoroutinesApi::class)
        saveJob = GlobalScope.launch(Dispatchers.IO) {
            delay(1000)
            saveMutex.withLock {
                try {
                    cookieFile.parentFile?.mkdirs()
                    cookieFile.writeText(json.encodeToString<List<CookieWithTimestamp>>(container))
                } catch (e: Exception) {
                    logger.e("JsonPersistedCookiesStorage", "Can't save cookies!", e)
                }
            }
        }
    }

    private fun cleanup(timestamp: Long) {
        container.removeAll { (cookie, createdAt) ->
            val expires = cookie.maxAgeOrExpires(createdAt) ?: return@removeAll false
            expires < timestamp
        }

        val newOldest = container.fold(Long.MAX_VALUE) { acc, (cookie, createdAt) ->
            cookie.maxAgeOrExpires(createdAt)?.let { min(acc, it) } ?: acc
        }

        oldestCookie.set(newOldest)
    }

    private fun Cookie.maxAgeOrExpires(createdAt: Long): Long? =
        maxAge?.let { createdAt + it * 1000L } ?: expires?.timestamp
}