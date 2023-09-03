package com.badmanners.murglar.lib.sample

import com.badmanners.murglar.lib.core.localization.MessageException
import com.badmanners.murglar.lib.core.log.LoggerMiddleware
import com.badmanners.murglar.lib.core.model.node.NodeType
import com.badmanners.murglar.lib.core.model.tag.Lyrics
import com.badmanners.murglar.lib.core.model.tag.Lyrics.SyncedLyrics
import com.badmanners.murglar.lib.core.model.tag.Tags
import com.badmanners.murglar.lib.core.model.track.source.Bitrate
import com.badmanners.murglar.lib.core.model.track.source.Container
import com.badmanners.murglar.lib.core.model.track.source.Extension
import com.badmanners.murglar.lib.core.model.track.source.Source
import com.badmanners.murglar.lib.core.network.NetworkMiddleware
import com.badmanners.murglar.lib.core.network.NetworkRequest
import com.badmanners.murglar.lib.core.network.NetworkResponse
import com.badmanners.murglar.lib.core.network.ResponseConverters
import com.badmanners.murglar.lib.core.notification.NotificationMiddleware
import com.badmanners.murglar.lib.core.preference.ActionPreference
import com.badmanners.murglar.lib.core.preference.CopyPreference
import com.badmanners.murglar.lib.core.preference.Preference
import com.badmanners.murglar.lib.core.preference.PreferenceMiddleware
import com.badmanners.murglar.lib.core.service.BaseMurglar
import com.badmanners.murglar.lib.core.utils.MediaId
import com.badmanners.murglar.lib.core.utils.MurglarLibUtils
import com.badmanners.murglar.lib.core.utils.MurglarLibUtils.RUSSIAN
import com.badmanners.murglar.lib.core.utils.MurglarLibUtils.loadPaged
import com.badmanners.murglar.lib.core.utils.MurglarLibUtils.mask
import com.badmanners.murglar.lib.core.utils.MurglarLibUtils.normalize
import com.badmanners.murglar.lib.core.utils.MurglarLibUtils.prepareEntriesString
import com.badmanners.murglar.lib.core.utils.contract.WorkerThread
import com.badmanners.murglar.lib.core.utils.getBoolean
import com.badmanners.murglar.lib.core.utils.getBooleanOpt
import com.badmanners.murglar.lib.core.utils.getInt
import com.badmanners.murglar.lib.core.utils.getIntOpt
import com.badmanners.murglar.lib.core.utils.getJsonArray
import com.badmanners.murglar.lib.core.utils.getJsonArrayOpt
import com.badmanners.murglar.lib.core.utils.getJsonObject
import com.badmanners.murglar.lib.core.utils.getJsonObjectOpt
import com.badmanners.murglar.lib.core.utils.getLong
import com.badmanners.murglar.lib.core.utils.getString
import com.badmanners.murglar.lib.core.utils.getStringOpt
import com.badmanners.murglar.lib.core.utils.has
import com.badmanners.murglar.lib.core.utils.jsonObjectOpt
import com.badmanners.murglar.lib.core.utils.string
import com.badmanners.murglar.lib.sample.decrypt.SampleDecryptor
import com.badmanners.murglar.lib.sample.localization.SampleDefaultMessages
import com.badmanners.murglar.lib.sample.localization.SampleMessages
import com.badmanners.murglar.lib.sample.localization.SampleRuMessages
import com.badmanners.murglar.lib.sample.login.SampleLoginResolver
import com.badmanners.murglar.lib.sample.model.album.SampleAlbum
import com.badmanners.murglar.lib.sample.model.artist.SampleArtist
import com.badmanners.murglar.lib.sample.model.playlist.SamplePlaylist
import com.badmanners.murglar.lib.sample.model.radio.RadioLibrary
import com.badmanners.murglar.lib.sample.model.radio.RadioType
import com.badmanners.murglar.lib.sample.model.radio.SampleRadio
import com.badmanners.murglar.lib.sample.model.radio.SampleRadioUpdate
import com.badmanners.murglar.lib.sample.model.track.SampleTrack
import com.badmanners.murglar.lib.sample.node.SampleNodeResolver
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.threeten.bp.OffsetDateTime
import java.util.Locale.ENGLISH


class SampleMurglar(
    id: String,
    preferences: PreferenceMiddleware,
    network: NetworkMiddleware,
    notifications: NotificationMiddleware,
    logger: LoggerMiddleware
) : BaseMurglar<SampleTrack, SampleMessages>(
    id, ICON_URL, MESSAGES, preferences, network, notifications, logger, SampleDecryptor(logger)
) {

    companion object {
        /**
         * Must be used only for [MediaId.build], don't pass it to the [BaseMurglar] constructor directly!
         */
        const val SERVICE_ID = "sample"

        private const val ICON_URL =
            "https://play-lh.googleusercontent.com/aFWiT2lTa9CYBpyPjfgfNHd0r5puwKRGj2rHpdPTNrz2N9LXgN_MbLjePd1OTc0E8Rl1"

        private val MESSAGES = mapOf(
            ENGLISH to SampleDefaultMessages,
            RUSSIAN to SampleRuMessages
        )

        const val SAMPLE_DOMAIN = "https://sample.com"
        const val API_DOMAIN = "$SAMPLE_DOMAIN/api"
        const val API_V2_DOMAIN = "$SAMPLE_DOMAIN/api/v2"
    }

    override val loginResolver = SampleLoginResolver(preferences, network, notifications, this, messages)

    override val nodeResolver = SampleNodeResolver(this, messages)

    override val murglarPreferences: List<Preference>
        get() = mutableListOf<Preference>().apply {
            if (loginResolver.isLogged)
                this += CopyPreference(
                    id = "token",
                    title = messages.copyToken,
                    getter = loginResolver::oauthToken,
                    displayGetter = { loginResolver.oauthToken.mask(5, 5) }
                )

            this += ActionPreference(
                id = "action",
                title = "Hello world!",
                summary = "Shows hello world",
                action = { notifications.longNotify("Hello world!") },
                needConfirmation = true,
                confirmationText = "You will see a message!"
            )

            //more preferences
        }

    override val possibleFormats = listOf(
        Extension.UNKNOWN to Bitrate.B_UNKNOWN,
        Extension.MP3 to Bitrate.B_320,
        Extension.MP3 to Bitrate.B_192
    )

    @WorkerThread
    override fun onCreate() {
        if (!loginResolver.isLogged)
            return

        try {
            loginResolver.updateUser()
        } catch (e: MessageException) {
            notifications.longNotify(e.message!!)
        }
    }

    @WorkerThread
    override fun resolveSourceForUrl(track: SampleTrack, source: Source): Source {
        val request = getRequest("${API_V2_DOMAIN}/track/${track.id}/download").build()
        val result = network.execute(request, ResponseConverters.asJsonObject()).result

        val actualCodec = result.getString("codec")
        val requiredCodec = source.extension.value
        check(actualCodec == requiredCodec) {
            "${messages.sourceUrlUnavailable} Wrong codec - required '$requiredCodec', actual '$actualCodec'!"
        }

        val actualBitrate = result.getInt("bitrate")
        val requiredBitrate = source.bitrate.value
        check(actualBitrate == requiredBitrate) {
            "${messages.sourceUrlUnavailable} Wrong bitrate - required '$requiredBitrate', actual '$actualBitrate'!"
        }
        check(!result.getBoolean("preview")) {
            "${messages.sourceUrlUnavailable} Got preview version of track!"
        }

        val url = result.getStringOpt("url") ?: error(messages.sourceUrlUnavailable)

        return source.copyWithNewUrl(url)
    }

    override fun hasLyrics(track: SampleTrack) = track.hasLyrics

    @WorkerThread
    override fun getLyrics(track: SampleTrack): Lyrics {
        val request = trackRequest(track.id, track.albumId)
        val response = network.execute(request, ResponseConverters.asJsonObject())

        val result = response.result.getJsonObject("result")

        return when (result.getStringOpt("type")) {
            "plain" -> Lyrics(result.getString("lyrics"))

            "synced" -> {
                val lyrics = result.getString("lyrics")

                // [00:18.52] Line 1\n
                // [00:22.82] Line 2\n
                val plain = lyrics.replace("\\[.+?] ".toRegex(), "")

                val lines = lyrics.split('\n').map {
                    val startMillis = it.substring(1, 3).toLong() * 60000 +
                            it.substring(4, 6).toLong() * 1000 +
                            it.substring(7, 9).toLong() * 10
                    val line = it.substring(11)

                    SyncedLyrics.Line(startMillis, null, line)
                }
                val synced = SyncedLyrics(lines, track.artistNames[0], track.title, track.albumName)

                Lyrics(plain, synced)
            }

            else -> error(messages.trackHasNoLyrics)
        }
    }

    @WorkerThread
    override fun getTags(track: SampleTrack): Tags {
        val tags = Tags.Builder()
            .title(track.title)
            .subtitle(track.subtitle)
            .artists(track.artistNames)
            .genre(track.genre)
            .explicit(track.explicit)
            .gain(track.gain)
            .peak(track.peak)
            .url(track.serviceUrl)
            .mediaId(track.mediaId)

        if (!track.hasAlbum)
            return tags.createTags()

        tags.album(track.albumName)
            .albumArtist(track.albumArtist)
            .trackNumber(track.indexInAlbum)
            .diskNumber(track.volumeNumber)
            .releaseDate(track.albumReleaseDate)

        val volumesAndTracks = mutableMapOf<Int, Int>()
        getAlbumTracks(track.albumId!!).forEach {
            val tracks = volumesAndTracks[it.volumeNumber] ?: 0
            volumesAndTracks[it.volumeNumber!!] = tracks + 1
        }
        tags.totalDisks(volumesAndTracks.keys.size)
            .totalTracks(volumesAndTracks[track.volumeNumber])

        return tags.createTags()
    }

    @WorkerThread
    override fun getTracksByMediaIds(mediaIds: List<String>): List<SampleTrack> = mediaIds
        .map { MediaId.getIds(it).first() }
        .loadPaged(1000) {
            val request = postRequest("$API_DOMAIN/track-entries")
                .addParameter("entries", it.prepareEntriesString())
                .build()
            val response = network.execute(request, ResponseConverters.asJsonArray())
            response.result.toTracks()
        }


    //region query methods
    @WorkerThread
    fun getMyTracks(page: Int): List<SampleTrack> {
        loginResolver.checkLogged()
        val request = getRequest("${API_DOMAIN}/library")
            .addParameter("owner", loginResolver.username)
            .addParameter("filter", "tracks")
            .addParameter("page", page)
            .build()
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonArray("tracks").toTracks()
    }

    @WorkerThread
    fun getMyAlbums(): List<SampleAlbum> {
        loginResolver.checkLogged()
        val request = getRequest("${API_DOMAIN}/library")
            .addParameter("owner", loginResolver.username)
            .addParameter("filter", "albums")
            .build()
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonArray("albums").toAlbums()
    }

    @WorkerThread
    fun getMyArtists(): List<SampleArtist> {
        loginResolver.checkLogged()
        val request = getRequest("${API_DOMAIN}/library")
            .addParameter("owner", loginResolver.username)
            .addParameter("filter", "artists")
            .build()
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonArray("artists").toArtists()
    }

    @WorkerThread
    fun getMyPlaylists(): List<SamplePlaylist> {
        loginResolver.checkLogged()
        val request = getRequest("${API_DOMAIN}/library")
            .addParameter("owner", loginResolver.username)
            .addParameter("filter", "playlists")
            .build()
        val result = network.execute(request, ResponseConverters.asJsonObject()).result
        return result.getJsonArray("playlists").toPlaylists()
    }

    @WorkerThread
    fun getMyPodcasts(): List<SampleAlbum> = getMyPodcastsAndAudiobooks(NodeType.PODCAST)

    @WorkerThread
    fun getMyAudiobooks(): List<SampleAlbum> = getMyPodcastsAndAudiobooks(NodeType.AUDIOBOOK)

    @WorkerThread
    private fun getMyPodcastsAndAudiobooks(contentType: String): List<SampleAlbum> {
        loginResolver.checkLogged()
        val request = getRequest("${API_DOMAIN}/library")
            .addParameter("owner", loginResolver.username)
            .addParameter("filter", contentType)
            .build()
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonArray("albums").toAlbums()
    }

    @WorkerThread
    fun getMyHistory(page: Int): List<SampleTrack> {
        loginResolver.checkLogged()
        val request = getRequest("${API_DOMAIN}/library")
            .addParameter("owner", loginResolver.username)
            .addParameter("filter", "history")
            .addParameter("page", page)
            .build()
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonArray("tracks").toTracks()
    }

    @WorkerThread
    fun getRecommendationsPlaylists(): List<SamplePlaylist> {
        loginResolver.checkLogged()
        val request = getRequest("${API_DOMAIN}/main")
            .addParameter("what", "home")
            .build()
        val response = network.execute(request, ResponseConverters.asJsonObject())

        val blocks = response.result.getJsonArrayOpt("blocks") ?: return emptyList()

        return blocks.asSequence()
            .map { it.jsonObject }
            .filter { it.getString("type") == "personal-playlists" }
            .map { it.getJsonArray("entities").toRecommendationsPlaylists() }
            .flatten()
            .toList()
    }

    @WorkerThread
    fun searchTracks(query: String, page: Int): List<SampleTrack> {
        val request = searchRequest("tracks", query, page)
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonObjectOpt("tracks")?.getJsonArrayOpt("items")?.toTracks() ?: emptyList()
    }

    @WorkerThread
    fun searchAlbums(query: String, page: Int): List<SampleAlbum> {
        val request = searchRequest("albums", query, page)
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonObjectOpt("albums")?.getJsonArrayOpt("items")?.toAlbums() ?: emptyList()
    }

    @WorkerThread
    fun searchArtists(query: String, page: Int): List<SampleArtist> {
        val request = searchRequest("artists", query, page)
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonObjectOpt("artists")?.getJsonArrayOpt("items")?.toArtists() ?: emptyList()
    }

    @WorkerThread
    fun searchPlaylists(query: String, page: Int): List<SamplePlaylist> {
        val request = searchRequest("playlists", query, page)
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonObjectOpt("playlists")?.getJsonArrayOpt("items")?.toPlaylists() ?: emptyList()
    }

    @WorkerThread
    fun searchPodcasts(query: String, page: Int): List<SampleAlbum> =
        searchPodcastsAndAudiobooks(query, page, NodeType.PODCAST)

    @WorkerThread
    fun searchAudiobooks(query: String, page: Int): List<SampleAlbum> =
        searchPodcastsAndAudiobooks(query, page, NodeType.AUDIOBOOK)

    @WorkerThread
    fun searchPodcastsAndAudiobooks(query: String, page: Int, contentType: String): List<SampleAlbum> {
        val request = searchRequest(contentType, query, page)
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonObjectOpt(contentType)
            ?.getJsonArrayOpt("items")
            ?.toAlbums()
            ?: emptyList()
    }

    @WorkerThread
    fun getArtist(artistId: String): SampleArtist {
        val request = artistRequest(artistId, "")
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonObject("artist").toArtist()
    }

    @WorkerThread
    fun getArtistPopularTracks(artistId: String): List<SampleTrack> {
        val request = artistRequest(artistId, "tracks")
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonArray("tracks").toTracks()
    }

    @WorkerThread
    fun getArtistAlbums(artistId: String): List<SampleAlbum> = getArtistAlbums(artistId, "albums")

    @WorkerThread
    fun getArtistCompilations(artistId: String): List<SampleAlbum> = getArtistAlbums(artistId, "alsoAlbums")

    private fun getArtistAlbums(artistId: String, albumsField: String): List<SampleAlbum> {
        val request = artistRequest(artistId, "albums")
        val result = network.execute(request, ResponseConverters.asJsonObject()).result
        return result.getJsonArray(albumsField).toAlbums()
    }

    @WorkerThread
    fun getArtistPlaylists(artistId: String): List<SamplePlaylist> {
        val request = artistRequest(artistId, "")
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonArray("playlists").toPlaylists()
    }

    @WorkerThread
    fun getArtistSimilarArtists(artistId: String): List<SampleArtist> {
        val request = artistRequest(artistId, "")
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonArray("allSimilar").toArtists()
    }

    @WorkerThread
    fun getAlbum(albumId: String): SampleAlbum {
        val request = albumRequest(albumId)
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.toAlbum() ?: error("Can't get album $albumId!")
    }

    @WorkerThread
    fun getAlbumTracks(albumId: String): List<SampleTrack> {
        val request = albumRequest(albumId)
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonArray("tracks").toTracks()
    }

    @WorkerThread
    fun getPlaylist(ownerLogin: String, playlistId: String): SamplePlaylist {
        val request = playlistRequest(ownerLogin, playlistId)
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonObject("playlist").toPlaylist() ?: error("Can't get playlist $playlistId!")
    }

    @WorkerThread
    fun getPlaylistTracks(ownerLogin: String, playlistId: String, page: Int): List<SampleTrack> {
        val request = playlistRequest(ownerLogin, playlistId, page)
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.getJsonObject("playlist").getJsonArray("tracks").toTracks()
    }

    @WorkerThread
    fun getTrack(trackId: String, albumId: String?): SampleTrack {
        val request = trackRequest(trackId, albumId)
        val response = network.execute(request, ResponseConverters.asJsonObject())
        val track = response.result.getJsonObject("track")
        check(track.getBooleanOpt("available") ?: false) { "Track unavailable!" }
        return track.toTrack()
    }

    fun getTrackRadio(track: SampleTrack) = SampleRadio("track", track.id, "${messages.radio}: ${track.title}", null)

    fun getAlbumRadio(album: SampleAlbum) = SampleRadio("album", album.id, "${messages.radio}: ${album.title}", null)

    fun getArtistRadio(artist: SampleArtist) =
        SampleRadio("artist", artist.id, "${messages.radio}: ${artist.name}", null)

    fun getPlaylistRadio(playlist: SamplePlaylist) =
        SampleRadio(
            "playlist",
            "${playlist.ownerId}_${playlist.id}",
            "${messages.radio}: ${playlist.title}",
            null
        )

    private lateinit var radioLibrary: RadioLibrary

    @WorkerThread
    fun getRadioLibrary(): RadioLibrary {
        if (::radioLibrary.isInitialized)
            return radioLibrary

        loginResolver.checkLogged()
        val request = getRequest("${API_DOMAIN}/radio-library").build()
        val response = network.execute(request, ResponseConverters.asJsonObject())

        val types = response.result.getJsonObject("types").values.mapNotNull { it.jsonObjectOpt }

        val typesWithFullInfo = types.asSequence()
            .filter { it.has("children") && it.has("id") && it.has("name") }
            .toList()

        val radioTypes = typesWithFullInfo.map { RadioType(it.getString("id"), it.getString("name")) }

        val typesToRadios = typesWithFullInfo.associateBy({ it.getString("id") }, {
            it.getJsonArray("children").map { it.jsonObject.toRadio() }
        })

        radioLibrary = RadioLibrary(radioTypes, typesToRadios)
        return radioLibrary
    }

    @WorkerThread
    fun getRadioNextTracks(radio: SampleRadio): SampleRadioUpdate {
        val request = getRequest("${API_V2_DOMAIN}/radio/${radio.type}/${radio.tag}/tracks")
            .addParameter("queue", radio.queue)
            .build()
        val response = network.execute(request, ResponseConverters.asJsonObject())
        val tracks = response.result.getJsonArray("tracks").toTracks()
        if (radio.hasEmptyQueue)
            reportRadioStart(radio, tracks[0])
        val queue = tracks.joinToString(",") { it.fullId() }
        val updatedRadio = radio.fromNewQueue(queue)
        return SampleRadioUpdate(updatedRadio, tracks)
    }

    @WorkerThread
    fun addTrackToFavorite(track: SampleTrack) {
        loginResolver.checkLogged()
        check(track.nodeType == NodeType.TRACK) { "Only tracks can be added to favorite!" }
        val builder = changeLibraryRequest("add")
            .addParameter("trackId", track.id)
        executeLibraryRequest(builder)
    }

    @WorkerThread
    fun removeTrackFromFavorite(track: SampleTrack) {
        loginResolver.checkLogged()
        check(track.nodeType == NodeType.TRACK) { "Only tracks can be removed from favorite!" }
        val builder = changeLibraryRequest("remove")
            .addParameter("trackId", track.id)
        executeLibraryRequest(builder)
    }

    @WorkerThread
    fun addAlbumToFavorite(album: SampleAlbum) {
        loginResolver.checkLogged()
        val builder = changeLibraryRequest("add")
            .addParameter("albumId", album.id)
        executeLibraryRequest(builder)
    }

    @WorkerThread
    fun removeAlbumFromFavorite(album: SampleAlbum) {
        loginResolver.checkLogged()
        val builder = changeLibraryRequest("remove")
            .addParameter("albumId", album.id)
        executeLibraryRequest(builder)
    }

    @WorkerThread
    fun addPlaylistToFavorite(playlist: SamplePlaylist) {
        loginResolver.checkLogged()
        val builder = changeLibraryRequest("add")
            .addParameter("playlistId", playlist.id)
            .addParameter("ownerId", playlist.ownerId)
        executeLibraryRequest(builder)
    }

    @WorkerThread
    fun removePlaylistFromFavorite(playlist: SamplePlaylist) {
        loginResolver.checkLogged()
        val builder = changeLibraryRequest("remove")
            .addParameter("playlistId", playlist.id)
            .addParameter("ownerId", playlist.ownerId)
        executeLibraryRequest(builder)
    }

    @WorkerThread
    fun addArtistToFavorite(artist: SampleArtist) {
        loginResolver.checkLogged()
        val builder = changeLibraryRequest("add")
            .addParameter("artistId", artist.id)
        executeLibraryRequest(builder)
    }

    @WorkerThread
    fun removeArtistFromFavorite(artist: SampleArtist) {
        loginResolver.checkLogged()
        val builder = changeLibraryRequest("remove")
            .addParameter("artistId", artist.id)
        executeLibraryRequest(builder)
    }

    @WorkerThread
    fun reportTrackStart(track: SampleTrack) {
        loginResolver.checkLogged()
        val url = "${API_V2_DOMAIN}/track/${track.fullId()}/feedback/start"
        val builder = postRequest(url)
            .addParameter("timestamp", System.currentTimeMillis())
        executeLibraryRequest(builder)
    }

    @WorkerThread
    fun reportTrackEnd(track: SampleTrack, endTimeMs: Int) {
        loginResolver.checkLogged()
        val url = "${API_V2_DOMAIN}/track/${track.fullId()}/feedback/end"
        val builder = postRequest(url)
            .addParameter("timestamp", System.currentTimeMillis())
            .addParameter("totalPlayed", endTimeMs)
        executeLibraryRequest(builder)
    }

    @WorkerThread
    fun reportRadioStart(radio: SampleRadio, track: SampleTrack) {
        loginResolver.checkLogged()
        val url = "${API_V2_DOMAIN}/radio/${radio.type}/${radio.tag}/feedback/radioStarted/${track.fullId()}"
        val builder = postRequest(url)
            .addParameter("timestamp", System.currentTimeMillis())
        executeLibraryRequest(builder)
    }

    @WorkerThread
    fun reportRadioTrackStart(radio: SampleRadio, track: SampleTrack) {
        loginResolver.checkLogged()
        val url = "${API_V2_DOMAIN}/radio/${radio.type}/${radio.tag}/feedback/trackStarted/${track.fullId()}"
        val builder = postRequest(url)
            .addParameter("timestamp", System.currentTimeMillis())
            .addParameter("totalPlayed", 0)
            .addParameter("trackId", track.id)
        if (track.hasAlbum)
            builder.addParameter("albumId", track.albumId!!)
        executeLibraryRequest(builder)
    }

    @WorkerThread
    fun reportRadioTrackEnd(radio: SampleRadio, track: SampleTrack, endTimeMs: Int) {
        loginResolver.checkLogged()
        val url = "${API_V2_DOMAIN}/radio/${radio.type}/${radio.tag}/feedback/trackFinished/${track.fullId()}"
        val builder = postRequest(url)
            .addParameter("timestamp", System.currentTimeMillis())
            .addParameter("totalPlayed", endTimeMs)
            .addParameter("trackId", track.id)
        if (track.hasAlbum)
            builder.addParameter("albumId", track.albumId!!)
        executeLibraryRequest(builder)
    }
    //endregion query methods

    @WorkerThread
    fun loadUsername(): String? {
        val request = getRequest("${API_DOMAIN}/me").build()
        val response = network.execute(request, ResponseConverters.asJsonObject())
        return response.result.takeIf { response.isSuccessful }?.getString("username")
    }

    private fun executeLibraryRequest(builder: NetworkRequest.Builder) {
        val response = network.execute(builder.build(), ResponseConverters.asJsonObject())
        val result = response.result
        when {
            result.has("success") -> result.getBoolean("success")
            result.has("result") -> result.getString("result").equals("ok", ignoreCase = true)
            else -> false
        }.let {
            check(it) { "Request failed: '$response'!" }
        }
    }

    private fun JsonArray.toTracks(): List<SampleTrack> = asSequence()
        .map { it.jsonObject }
        .filter { it.getBooleanOpt("available") ?: false }
        .map { it.toTrack() }
        .toList()

    private fun JsonArray.toAlbums(): List<SampleAlbum> = mapNotNull { it.jsonObject.toAlbum() }

    private fun JsonArray.toArtists(): List<SampleArtist> = map { it.jsonObject.toArtist() }

    private fun JsonArray.toPlaylists(): List<SamplePlaylist> = mapNotNull { it.jsonObject.toPlaylist() }

    private fun JsonArray.toRecommendationsPlaylists(): List<SamplePlaylist> =
        mapNotNull { it.jsonObject.getJsonObject("data").toPlaylist() }

    private fun JsonObject.toPlaylist(): SamplePlaylist? {
        val id = getStringOpt("id") ?: return null
        val title = getString("title").normalize()
        val description = getStringOpt("description")?.take(500)

        val rawPlaylistCoverUrl = getJsonObjectOpt("cover")?.let { cover ->
            cover.getStringOpt("type")?.let { type ->
                when (type) {
                    "pic" -> cover.getString("uri")
                    "mosaic" -> cover.getJsonArray("itemsUri")[0].string
                    else -> null
                }
            }
        }
        val smallCoverUrl = rawPlaylistCoverUrl.convertRawCover(false)
        val bigCoverUrl = rawPlaylistCoverUrl.convertRawCover(true)

        val trackCount = getInt("trackCount")

        val ownerLogin = getJsonObject("owner").getString("login")
        val ownerId = getJsonObject("owner").getString("id")

        val serviceUrl = "$SAMPLE_DOMAIN/users/$ownerLogin/playlists/$id"

        return SamplePlaylist(
            id, title, description, smallCoverUrl, bigCoverUrl, trackCount, ownerLogin, ownerId, serviceUrl
        )
    }

    private fun JsonObject.toArtist(): SampleArtist {
        val id = getString("id")
        val name = getString("name").normalize()

        val rawCoverUrl = getJsonObjectOpt("cover")?.getStringOpt("uri")
        val smallCoverUrl = rawCoverUrl.convertRawCover(false)
        val bigCoverUrl = rawCoverUrl.convertRawCover(true)

        var albumsCount = 0
        var tracksCount = 0
        getJsonObjectOpt("counts")?.let {
            albumsCount = it.getInt("directAlbums")
            tracksCount = it.getInt("tracks")
        }

        val serviceUrl = "$SAMPLE_DOMAIN/artist/$id"

        return SampleArtist(id, name, smallCoverUrl, bigCoverUrl, albumsCount, tracksCount, serviceUrl)
    }

    private fun JsonObject.toAlbum(): SampleAlbum? {
        val albumId = getStringOpt("id") ?: return null
        val album = getStringOpt("title")?.normalize() ?: return null
        val albumAdditionalInfo = getStringOpt("version")
        val releaseDate = getStringOpt("releaseDate")?.let(OffsetDateTime::parse)?.toLocalDate()

        val rawAlbumCoverUrl = getStringOpt("coverUri")
        val smallCoverUrl = rawAlbumCoverUrl.convertRawCover(false)
        val bigCoverUrl = rawAlbumCoverUrl.convertRawCover(true)

        val tracksCount = getIntOpt("trackCount") ?: 0

        val firstArtist = getJsonArrayOpt("artists")?.firstOrNull()?.jsonObject
        val artistId = firstArtist?.getString("id")
        val artistName = firstArtist?.getString("name")?.normalize()

        val genre = getStringOpt("genre")

        val type = when (getStringOpt("type")) {
            "podcast" -> NodeType.PODCAST
            "audiobook" -> NodeType.AUDIOBOOK
            else -> NodeType.ALBUM
        }
        val explicit = getStringOpt("contentWarning") == "explicit"
        val serviceUrl = "$SAMPLE_DOMAIN/album/$albumId"

        return SampleAlbum(
            albumId, album, albumAdditionalInfo, releaseDate, smallCoverUrl, bigCoverUrl, tracksCount,
            artistId, artistName, genre, type, explicit, serviceUrl
        )
    }

    private fun JsonObject.toTrack(): SampleTrack {
        val trackId = getString("id")
        val title = getString("title").normalize()
        val additionalInfo = getStringOpt("version")

        val artists = getJsonArray("artists").map { it.jsonObject.toArtist() }

        val artistIds = artists.map { it.id }
        val artistNames = artists.map { it.name }

        val albumObject = getJsonObject("album")
        val album = albumObject.toAlbum()
        val indexInAlbum = albumObject.getJsonObject("trackPosition").getInt("index")
        val volumeNumber = albumObject.getJsonObject("trackPosition").getInt("volume")

        val duration = getLong("durationMs")

        val explicit = getStringOpt("contentWarning")?.contains("explicit") ?: false

        val lyricsId = getJsonObjectOpt("lyricsInfo")?.getStringOpt("lyricsId")

        var gain: String? = null
        var peak: String? = null
        getJsonObjectOpt("normalization")?.let {
            gain = it.getString("gain")
            peak = it.getString("peak")
        }

        val hqTag = "${Extension.MP3} ${Bitrate.B_320.text}"
        val hqSource = Source("mp3_320", null, hqTag, Extension.MP3, Container.PROGRESSIVE, Bitrate.B_320)
        val lqTag = "${Extension.MP3} ${Bitrate.B_192.text}"
        val lqSource = Source("mp3_192", null, lqTag, Extension.MP3, Container.PROGRESSIVE, Bitrate.B_192)
        val sources = listOf(hqSource, lqSource)

        val type = when (getStringOpt("type")) {
            "podcast-episode" -> NodeType.PODCAST_EPISODE
            "audiobook" -> NodeType.AUDIOBOOK_PART
            else -> NodeType.TRACK
        }

        val mediaId = MediaId.build(SERVICE_ID, trackId)

        val rawCoverUrl = getStringOpt("coverUri")
        val smallCoverUrl = rawCoverUrl.convertRawCover(false)
            ?: album?.smallCoverUrl
            ?: artists.firstOrNull()?.smallCoverUrl
        val bigCoverUrl = rawCoverUrl.convertRawCover(true)
            ?: album?.bigCoverUrl
            ?: artists.firstOrNull()?.bigCoverUrl

        val serviceUrl = when {
            album != null -> "$SAMPLE_DOMAIN/album/${album.id}/track/$trackId"
            else -> "$SAMPLE_DOMAIN/track/$trackId"
        }

        return SampleTrack(
            trackId, title, additionalInfo, artistIds, artistNames, album?.id, album?.title, album?.releaseDate,
            album?.artistName, album?.description, indexInAlbum, volumeNumber, duration, album?.genre,
            explicit, lyricsId, gain, peak, sources, type, mediaId, smallCoverUrl, bigCoverUrl, serviceUrl
        )
    }

    private fun JsonObject.toRadio(): SampleRadio {
        val radioType = getJsonObject("id").getString("type")
        val tag = getJsonObject("id").getString("tag")
        val name = getString("name")
        val coverUrl = getStringOpt("fullImageUrl") ?: getJsonObjectOpt("icon")?.getString("imageUrl")
        return SampleRadio(radioType, tag, name, coverUrl.convertRawCover(false))
    }

    private fun String?.convertRawCover(big: Boolean) = this?.let {
        "https://${it.replace("%%", if (big) "1000x1000" else "200x200")}"
    }

    private fun isSignUpdateNeeded(response: NetworkResponse<JsonObject>) = response.statusCode == 403

    private fun SampleTrack.fullId() = trackFullId(id, albumId)

    private fun trackFullId(trackId: String, albumId: String?) = when {
        albumId != null -> "$trackId:$albumId"
        else -> trackId
    }

    private fun trackRequest(trackId: String, albumId: String?) = getRequest("${API_DOMAIN}/track")
        .addParameter("track", trackFullId(trackId, albumId))
        .build()

    private fun albumRequest(albumId: String) = getRequest("${API_DOMAIN}/album")
        .addParameter("album", albumId)
        .build()

    private fun artistRequest(artistId: String, what: String) =
        getRequest("${API_DOMAIN}/artist")
            .addParameter("artist", artistId)
            .addParameter("what", what)
            .build()

    private fun playlistRequest(ownerLogin: String, playlistId: String, page: Int? = null) =
        getRequest("${API_DOMAIN}/playlist")
            .addParameter("owner", ownerLogin)
            .addParameter("id", playlistId)
            .apply { page?.let { addParameter("page", it) } }
            .build()

    private fun searchRequest(type: String, query: String, page: Int) = getRequest("${API_DOMAIN}/music-search")
        .addParameter("text", query)
        .addParameter("type", type)
        .addParameter("page", page)
        .build()

    private fun changeLibraryRequest(act: String) = postRequest("${API_DOMAIN}/change-library")
        .addParameter("act", act)

    private fun getRequest(url: String) = request("GET", url)

    private fun postRequest(url: String) = request("POST", url)

    private fun request(method: String, url: String): NetworkRequest.Builder {
        val builder = NetworkRequest.Builder()
            .method(method)
            .url(url)
            .addHeaders(baseHeaders())
            .userAgent(MurglarLibUtils.CHROME_USER_AGENT)

        when {
            url.startsWith(API_DOMAIN) -> builder.addParameter("lang", locale.language)
            url.startsWith(API_V2_DOMAIN) -> builder.addParameter("__t", System.currentTimeMillis())
        }

        return builder
    }

    private fun baseHeaders() = listOf(
        "Accept-Language" to locale.toLanguageTag(),
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "X-Requested-With" to "XMLHttpRequest"
    )
}