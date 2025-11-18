package com.badmanners.murglar.client.cli.network

import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.util.date.GMTDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


object CookieSerializer : KSerializer<Cookie> {
    override val descriptor: SerialDescriptor = CookieSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Cookie) =
        encoder.encodeSerializableValue(CookieSurrogate.serializer(), value.toSurrogate())

    override fun deserialize(decoder: Decoder) =
        decoder.decodeSerializableValue(CookieSurrogate.serializer()).toCookie()
}

@Serializable
@SerialName("Cookie")
private data class CookieSurrogate(
    val name: String,
    val value: String,
    val encoding: CookieEncoding,
    val maxAge: Int?,
    val expires: Long?,
    val domain: String?,
    val path: String?,
    val secure: Boolean,
    val httpOnly: Boolean,
    val extensions: Map<String, String?>
) {
    fun toCookie() = Cookie(name, value, encoding, maxAge, GMTDate(expires), domain, path, secure, httpOnly, extensions)
}

private fun Cookie.toSurrogate() = CookieSurrogate(
    name, value, encoding, maxAge, expires?.timestamp, domain, path, secure, httpOnly, extensions
)