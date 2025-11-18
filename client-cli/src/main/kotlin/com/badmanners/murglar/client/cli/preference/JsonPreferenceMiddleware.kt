package com.badmanners.murglar.client.cli.preference

import com.badmanners.murglar.lib.core.log.LoggerMiddleware
import com.badmanners.murglar.lib.core.preference.PreferenceMiddleware
import com.badmanners.murglar.lib.core.utils.boolean
import com.badmanners.murglar.lib.core.utils.float
import com.badmanners.murglar.lib.core.utils.int
import com.badmanners.murglar.lib.core.utils.long
import com.badmanners.murglar.lib.core.utils.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


/**
 * [PreferenceMiddleware] impl that stores preferences in passed file as JSON.
 */
class JsonPreferenceMiddleware(
    private val preferencesFile: File,
    private val logger: LoggerMiddleware
) : PreferenceMiddleware {

    private val json = Json { prettyPrint = true }

    private val executor = Executors.newSingleThreadScheduledExecutor()

    private val cache: MutableMap<String, JsonElement> = load()

    private val lock = ReentrantLock()


    override fun getKeys(): Set<String> = cache.keys

    override fun contains(key: String) = cache.containsKey(key)

    override fun <S : String?> getString(key: String, defValue: S): S = (cache[key]?.string ?: defValue) as S
    override fun setString(key: String, value: String?) = updateValue(key) { JsonPrimitive(value) }

    override fun <I : Int?> getInt(key: String, defValue: I): I = (cache[key]?.int ?: defValue) as I
    override fun setInt(key: String, value: Int?) = updateValue(key) { JsonPrimitive(value) }

    override fun <L : Long?> getLong(key: String, defValue: L): L = (cache[key]?.long ?: defValue) as L
    override fun setLong(key: String, value: Long?) = updateValue(key) { JsonPrimitive(value) }

    override fun <F : Float?> getFloat(key: String, defValue: F): F = (cache[key]?.float ?: defValue) as F
    override fun setFloat(key: String, value: Float?) = updateValue(key) { JsonPrimitive(value) }

    override fun <B : Boolean?> getBoolean(key: String, defValue: B): B = (cache[key]?.boolean ?: defValue) as B
    override fun setBoolean(key: String, value: Boolean?) = updateValue(key) { JsonPrimitive(value) }

    override fun remove(key: String) = update { cache.remove(key) }

    override fun clearAll() = update { cache.clear() }


    private fun load() = try {
        when {
            preferencesFile.exists() -> preferencesFile.inputStream().use {
                json.decodeFromStream<MutableMap<String, JsonElement>>(it)
            }

            else -> mutableMapOf()
        }
    } catch (e: Exception) {
        logger.w("JsonPreferenceMiddleware", "Can't load preferences!", e)
        mutableMapOf()
    }.let { Collections.synchronizedMap(it) }

    private fun updateValue(key: String, newValue: () -> JsonElement) {
        lock.withLock {
            newValue().also {
                logger.d("JsonPreferenceMiddleware", "New value '$it' for key '$key'")
                when {
                    it == JsonNull -> update { cache.remove(key) }
                    cache[key] != it -> update { cache[key] = it }
                }
            }
        }
    }

    private fun update(updater: () -> Unit) = lock.withLock {
        updater()
        val encoded = json.encodeToString(cache)
        executor.execute {
            try {
                preferencesFile.parentFile?.mkdirs()
                preferencesFile.writeText(encoded)
            } catch (e: Exception) {
                logger.e("JsonPreferenceMiddleware", "Can't save preferences!", e)
            }
        }
    }
}