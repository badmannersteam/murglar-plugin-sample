package com.badmanners.murglar.client.cli.preference

import com.badmanners.murglar.lib.core.preference.PreferenceMiddleware

/**
 * [PreferenceMiddleware] impl that stores preferences in memory.
 */
class InMemoryPreferenceMiddleware : PreferenceMiddleware {

    private val preferences = LinkedHashMap<String, Any>()

    override fun getKeys() = preferences.keys

    override fun contains(key: String) = preferences.containsKey(key)

    override fun getString(key: String, defValue: String) = preferences.getOrDefault(key, defValue) as String
    override fun setString(key: String, value: String) = preferences.set(key, value)

    override fun getInt(key: String, defValue: Int) = preferences.getOrDefault(key, defValue) as Int
    override fun setInt(key: String, value: Int) = preferences.set(key, value)

    override fun getLong(key: String, defValue: Long) = preferences.getOrDefault(key, defValue) as Long
    override fun setLong(key: String, value: Long) = preferences.set(key, value)

    override fun getFloat(key: String, defValue: Float) = preferences.getOrDefault(key, defValue) as Float
    override fun setFloat(key: String, value: Float) = preferences.set(key, value)

    override fun getBoolean(key: String, defValue: Boolean) = preferences.getOrDefault(key, defValue) as Boolean
    override fun setBoolean(key: String, value: Boolean) = preferences.set(key, value)

    override fun remove(key: String) {
        preferences.remove(key)
    }

    override fun clearAll() = preferences.clear()
}