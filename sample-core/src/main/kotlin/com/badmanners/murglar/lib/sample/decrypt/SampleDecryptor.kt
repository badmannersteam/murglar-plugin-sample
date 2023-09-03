package com.badmanners.murglar.lib.sample.decrypt

import com.badmanners.murglar.lib.core.decrypt.Decryptor
import com.badmanners.murglar.lib.core.log.LoggerMiddleware
import com.badmanners.murglar.lib.core.model.track.source.Source
import com.badmanners.murglar.lib.core.utils.MurglarLibUtils.toMD5HexString
import com.badmanners.murglar.lib.sample.model.track.SampleTrack
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min


class SampleDecryptor(private val logger: LoggerMiddleware) : Decryptor<SampleTrack> {

    companion object {
        private const val SUB_CHUNK_SIZE = 2048
        private const val CHUNK_SIZE = SUB_CHUNK_SIZE * 3
        private const val BLOWFISH_SECRET = "some magic key"
        private const val LATIN_CHARSET = "ISO-8859-1"
    }

    override fun isEncrypted(track: SampleTrack, source: Source) = true

    override val decryptionChunkSize = CHUNK_SIZE

    override fun decrypt(content: ByteArray, offset: Int, length: Int, track: SampleTrack, source: Source): ByteArray {
        val cipher = buildCipher(track.id)
        val result = ByteArrayOutputStream(length)
        try {
            var i = 0
            var position = offset
            while (position < offset + length) {
                val chunkSize = min(offset + length - position, SUB_CHUNK_SIZE)
                var chunk = content.copyOfRange(position, position + chunkSize)

                if (i % 3 == 0 && chunkSize >= SUB_CHUNK_SIZE) {
                    try {
                        chunk = cipher.doFinal(chunk)
                    } catch (e: GeneralSecurityException) {
                        logger.w("SampleDecryptor", e)
                    }
                }

                result.write(chunk)
                position += chunkSize
                i++
            }
        } catch (e: IOException) {
            logger.e("SampleDecryptor", e)
        }

        return result.toByteArray()
    }

    private fun buildCipher(trackId: String) = Cipher.getInstance("Blowfish/CBC/NoPadding").apply {
        val keySpec = SecretKeySpec(getBlowfishKey(trackId), "Blowfish")
        val ivParam = IvParameterSpec(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))
        init(Cipher.DECRYPT_MODE, keySpec, ivParam)
    }

    private fun getBlowfishKey(trackId: String): ByteArray = buildString {
        val hash = trackId.toByteArray(charset(LATIN_CHARSET)).toMD5HexString()
        for (i in 0..15)
            append((hash[i].code xor hash[i + 16].code xor BLOWFISH_SECRET[i].code).toChar())
    }.toByteArray(charset(LATIN_CHARSET))
}
