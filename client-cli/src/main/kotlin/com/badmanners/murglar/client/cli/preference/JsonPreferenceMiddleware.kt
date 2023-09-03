package com.badmanners.murglar.client.cli.preference

import com.badmanners.murglar.lib.core.preference.PreferenceMiddleware
import com.badmanners.murglar.lib.core.utils.boolean
import com.badmanners.murglar.lib.core.utils.float
import com.badmanners.murglar.lib.core.utils.int
import com.badmanners.murglar.lib.core.utils.long
import com.badmanners.murglar.lib.core.utils.string
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors


/**
 * [PreferenceMiddleware] impl that stores preferences in passed file as JSON.
 */
class JsonPreferenceMiddleware(
    private val preferencesFile: File
) : PreferenceMiddleware {

    private val json = Json { prettyPrint = true }

    private val executor = Executors.newSingleThreadScheduledExecutor()

    private val cache: MutableMap<String, JsonElement> = load()


    override fun getKeys(): Set<String> = cache.keys

    override fun contains(key: String) = cache.containsKey(key)

    override fun getString(key: String, defValue: String) = cache[key]?.string ?: defValue
    override fun setString(key: String, value: String) = update { cache[key] = JsonPrimitive(value) }

    override fun getInt(key: String, defValue: Int) = cache[key]?.int ?: defValue
    override fun setInt(key: String, value: Int) = update { cache[key] = JsonPrimitive(value) }

    override fun getLong(key: String, defValue: Long) = cache[key]?.long ?: defValue
    override fun setLong(key: String, value: Long) = update { cache[key] = JsonPrimitive(value) }

    override fun getFloat(key: String, defValue: Float) = cache[key]?.float ?: defValue
    override fun setFloat(key: String, value: Float) = update { cache[key] = JsonPrimitive(value) }

    override fun getBoolean(key: String, defValue: Boolean) = cache[key]?.boolean ?: defValue
    override fun setBoolean(key: String, value: Boolean) = update { cache[key] = JsonPrimitive(value) }

    override fun remove(key: String) = update { cache.remove(key) }

    override fun clearAll() = update { cache.clear() }


    private fun load() =
        runCatching { json.decodeFromStream<MutableMap<String, JsonElement>>(preferencesFile.inputStream()) }
            .getOrDefault(HashMap())
            .let { Collections.synchronizedMap(it) }

    private fun update(updater: () -> Unit) {
        updater()
        executor.execute {
            preferencesFile.writeText(json.encodeToString(cache))
        }
    }
}