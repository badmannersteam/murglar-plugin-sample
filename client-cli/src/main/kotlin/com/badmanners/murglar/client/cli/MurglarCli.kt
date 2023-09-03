package com.badmanners.murglar.client.cli

import com.badmanners.murglar.client.cli.logger.StdLogger
import com.badmanners.murglar.client.cli.network.ApacheHttpClientNetworkMiddleware
import com.badmanners.murglar.client.cli.notification.StdNotificationMiddleware
import com.badmanners.murglar.client.cli.preference.JsonPreferenceMiddleware
import com.badmanners.murglar.lib.core.model.node.Node.Companion.toTrack
import com.badmanners.murglar.lib.core.model.track.BaseTrack
import com.badmanners.murglar.lib.core.model.track.source.Source
import com.badmanners.murglar.lib.core.network.NetworkRequest
import com.badmanners.murglar.lib.core.network.ResponseConverters.asByteArray
import com.badmanners.murglar.lib.core.service.Murglar
import com.badmanners.murglar.lib.sample.SampleMurglar
import com.badmanners.murglar.lib.sample.login.SampleLoginResolver
import com.badmanners.murglar.lib.sample.model.track.SampleTrack
import java.io.File


fun main() {
    MurglarCli.start()
}

object MurglarCli {

    private val network = ApacheHttpClientNetworkMiddleware()
    private val notifications = StdNotificationMiddleware()
    private val preferences = JsonPreferenceMiddleware(File("preferences.json"))
    private val logger = StdLogger()

    fun start() {
        val murglar = SampleMurglar("sample", preferences, network, notifications, logger)

        val token = "***token***"
        val credentials = mapOf(SampleLoginResolver.OAUTH_TOKEN_CREDENTIAL to token)
        murglar.loginResolver.apply {
            if (!isLogged)
                credentialsLogin(SampleLoginResolver.TOKEN_LOGIN_VARIANT, credentials)
        }

        val myTracks = murglar.getMyTracks(0)
        myTracks.first().download(murglar)

        val foundTracks = murglar.searchTracks("skillet comatose", 0)
        foundTracks.first().download(murglar)

        val nodeResolver = murglar.nodeResolver

        val playlistFromUrl = nodeResolver.getNodeFromUrl("https://sample.com/playlist/123456")
        val albumContent = nodeResolver.getNodeContent(playlistFromUrl.nodePath, null)
        albumContent.first().toTrack<SampleTrack>().download(murglar)

        val rootNodes = nodeResolver.getRootNodes(true)
        val myTracksNode = rootNodes.first()
        val myTracksNodeContent = nodeResolver.getNodeContent(myTracksNode.nodePath, 0)
        myTracksNodeContent.first().toTrack<SampleTrack>().download(murglar)

        // call what you want from murglar/nodeResolver
    }

    private fun <T : BaseTrack> T.download(murglar: Murglar<T>, source: Source = sources.first()) {
        val tags = murglar.getTags(this)

        val lyrics = when {
            murglar.hasLyrics(this) -> murglar.getLyrics(this).plain
            else -> null
        }

        val resolvedSource = murglar.resolveSourceForUrl(this, source)

        println(tags)
        println()
        println(lyrics)
        println()
        sources.forEach { println(it) }
        println()
        println(resolvedSource)

        val file = File("download", "$tag.${source.tag}.${resolvedSource.extension.value}")
        file.parentFile.mkdirs()

        val url = resolvedSource.url!!

        val contentRequest = NetworkRequest.Builder().url(url).build()
        val result = network.execute(contentRequest, asByteArray()).result

        val decryptor = murglar.decryptor
        val decryptedContent = when (decryptor.isEncrypted(this, source)) {
            true -> decryptor.decrypt(result, 0, result.size, this, resolvedSource)
            false -> result
        }

        file.writeBytes(decryptedContent)
    }
}