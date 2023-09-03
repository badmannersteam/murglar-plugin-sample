package com.badmanners.murglar.client.cli.network

import org.apache.http.client.CookieStore
import org.apache.http.cookie.Cookie
import org.apache.http.impl.client.BasicCookieStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Date


/**
 * [CookieStore] impl that stores cookies in passed file in binary format (Java serialized [BasicCookieStore]).
 */
class BinaryFilePersistedCookieStore(private val cookieFile: File) : CookieStore {

    private val delegate: CookieStore = try {
        FileInputStream(cookieFile).use { fileInputStream ->
            ObjectInputStream(fileInputStream).use { dataInputStream ->
                dataInputStream.readObject() as CookieStore
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        BasicCookieStore()
    }

    override fun addCookie(cookie: Cookie) = update { it.addCookie(cookie) }

    override fun getCookies(): List<Cookie> = delegate.cookies

    override fun clearExpired(date: Date): Boolean = update { it.clearExpired(date) }

    override fun clear() = update { it.clear() }

    private fun <T> update(updater: (CookieStore) -> T): T = updater.invoke(delegate).also { persist() }

    private fun persist() = FileOutputStream(cookieFile).use { fileOutputStream ->
        ObjectOutputStream(fileOutputStream).use { dataOutputStream ->
            dataOutputStream.writeObject(delegate)
        }
    }
}