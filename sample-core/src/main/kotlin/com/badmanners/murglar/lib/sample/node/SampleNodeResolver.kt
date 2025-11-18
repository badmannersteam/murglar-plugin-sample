package com.badmanners.murglar.lib.sample.node

import com.badmanners.murglar.lib.core.model.event.PlayerEvent.TrackEnd
import com.badmanners.murglar.lib.core.model.event.PlayerEvent.TrackStart
import com.badmanners.murglar.lib.core.model.node.NamedPath
import com.badmanners.murglar.lib.core.model.node.Node
import com.badmanners.murglar.lib.core.model.node.Node.Companion.to
import com.badmanners.murglar.lib.core.model.node.NodeParameters.PagingType.ENDLESSLY_PAGEABLE
import com.badmanners.murglar.lib.core.model.node.NodeParameters.PagingType.NON_PAGEABLE
import com.badmanners.murglar.lib.core.model.node.NodeParameters.PagingType.PAGEABLE
import com.badmanners.murglar.lib.core.model.node.NodeType.ALBUM
import com.badmanners.murglar.lib.core.model.node.NodeType.ARTIST
import com.badmanners.murglar.lib.core.model.node.NodeType.AUDIOBOOK
import com.badmanners.murglar.lib.core.model.node.NodeType.AUDIOBOOK_PART
import com.badmanners.murglar.lib.core.model.node.NodeType.NODE
import com.badmanners.murglar.lib.core.model.node.NodeType.PLAYLIST
import com.badmanners.murglar.lib.core.model.node.NodeType.PODCAST
import com.badmanners.murglar.lib.core.model.node.NodeType.PODCAST_EPISODE
import com.badmanners.murglar.lib.core.model.node.NodeType.RADIO
import com.badmanners.murglar.lib.core.model.node.NodeType.TRACK
import com.badmanners.murglar.lib.core.model.node.Path
import com.badmanners.murglar.lib.core.model.node.RadioNodeUpdate
import com.badmanners.murglar.lib.core.model.radio.RadioSettingsUpdate
import com.badmanners.murglar.lib.core.node.BaseNodeResolver
import com.badmanners.murglar.lib.core.node.Directory
import com.badmanners.murglar.lib.core.node.EventConfig
import com.badmanners.murglar.lib.core.node.LikeConfig
import com.badmanners.murglar.lib.core.node.MappedEntity
import com.badmanners.murglar.lib.core.node.Root
import com.badmanners.murglar.lib.core.node.Search
import com.badmanners.murglar.lib.core.node.Track
import com.badmanners.murglar.lib.core.node.UnmappedEntity
import com.badmanners.murglar.lib.core.utils.pattern.PatternMatcher.match
import com.badmanners.murglar.lib.sample.SampleMurglar
import com.badmanners.murglar.lib.sample.localization.SampleMessages
import com.badmanners.murglar.lib.sample.model.album.SampleAlbum
import com.badmanners.murglar.lib.sample.model.artist.SampleArtist
import com.badmanners.murglar.lib.sample.model.playlist.SamplePlaylist
import com.badmanners.murglar.lib.sample.model.radio.RadioType
import com.badmanners.murglar.lib.sample.model.radio.SampleRadio
import com.badmanners.murglar.lib.sample.model.track.SampleTrack


class SampleNodeResolver(
    murglar: SampleMurglar,
    messages: SampleMessages
) : BaseNodeResolver<SampleMurglar, SampleMessages>(murglar, messages) {

    override val configurations = listOf(
        Root(
            pattern = "myTracks",
            name = messages::myTracks,
            paging = PAGEABLE,
            hasSubdirectories = false,
            isOwn = true,
            contentNodeType = TRACK,
            nodeContentSupplier = ::getMyTracks
        ),
        Root(
            pattern = "myAlbums",
            name = messages::myAlbums,
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            isOwn = true,
            contentNodeType = ALBUM,
            nodeContentSupplier = ::getMyAlbums
        ),
        Root(
            pattern = "myArtists",
            name = messages::myArtists,
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            isOwn = true,
            contentNodeType = ARTIST,
            nodeContentSupplier = ::getMyArtists
        ),
        Root(
            pattern = "myPlaylists",
            name = messages::myPlaylists,
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            isOwn = true,
            contentNodeType = PLAYLIST,
            nodeContentSupplier = ::getMyPlaylists
        ),
        Root(
            pattern = "myPodcasts",
            name = messages::myPodcasts,
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            isOwn = true,
            contentNodeType = PODCAST,
            nodeContentSupplier = ::getMyPodcasts
        ),
        Root(
            pattern = "myAudiobooks",
            name = messages::myAudiobooks,
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            isOwn = true,
            contentNodeType = AUDIOBOOK,
            nodeContentSupplier = ::getMyAudiobooks
        ),
        Root(
            pattern = "myHistoryTracks",
            name = messages::myHistoryTracks,
            paging = ENDLESSLY_PAGEABLE,
            hasSubdirectories = false,
            isOwn = true,
            contentNodeType = TRACK,
            nodeContentSupplier = ::getMyHistory
        ),
        Root(
            pattern = "recommendations",
            name = messages::recommendations,
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            isOwn = false,
            contentNodeType = PLAYLIST,
            nodeContentSupplier = ::getRecommendations
        ),
        Root(
            pattern = "radio",
            name = messages::radio,
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            isOwn = false,
            contentNodeType = NODE,
            nodeContentSupplier = ::getRadioTypes
        ),

        Search(
            pattern = "searchTracks",
            name = messages::tracksSearch,
            hasSubdirectories = false,
            contentNodeType = TRACK,
            nodeContentSupplier = ::searchTracks
        ),
        Search(
            pattern = "searchAlbums",
            name = messages::albumsSearch,
            hasSubdirectories = true,
            contentNodeType = ALBUM,
            nodeContentSupplier = ::searchAlbums
        ),
        Search(
            pattern = "searchArtists",
            name = messages::artistsSearch,
            hasSubdirectories = true,
            contentNodeType = ARTIST,
            nodeContentSupplier = ::searchArtists
        ),
        Search(
            pattern = "searchPlaylists",
            name = messages::playlistsSearch,
            hasSubdirectories = true,
            contentNodeType = PLAYLIST,
            nodeContentSupplier = ::searchPlaylists
        ),
        Search(
            pattern = "searchPodcasts",
            name = messages::podcastsSearch,
            hasSubdirectories = true,
            contentNodeType = PODCAST,
            nodeContentSupplier = ::searchPodcasts
        ),
        Search(
            pattern = "searchAudiobooks",
            name = messages::audiobooksSearch,
            hasSubdirectories = true,
            contentNodeType = AUDIOBOOK,
            nodeContentSupplier = ::searchAudiobooks
        ),

        Track(
            pattern = "*/track-<trackId>",
            relatedPaths = ::getTrackRelatedPaths,
            like = LikeConfig(rootNodePath("myTracks"), ::likeTrack),
            urlPatterns = listOf(
                "*sample.com/album/<albumId:\\d+>/track/<trackId:\\d+>*",
                "*sample.com/track/<trackId:\\d+>*"
            ),
            events = listOf(
                EventConfig(TrackStart::class) { to<SampleTrack>().handleStartEvent() },
                EventConfig(TrackEnd::class) { to<SampleTrack>().handleEndEvent(it.endTimeMs) }
            ),
            nodeSupplier = ::getTrack
        ),

        Track(
            pattern = "*/podcast_episode-<trackId>",
            type = PODCAST_EPISODE,
            relatedPaths = ::getTrackRelatedPaths,
            nodeSupplier = ::getTrack
        ),

        Track(
            pattern = "*/audiobook_part-<trackId>",
            type = AUDIOBOOK_PART,
            relatedPaths = ::getTrackRelatedPaths,
            nodeSupplier = ::getTrack
        ),

        MappedEntity(
            pattern = "*/album-<albumId>",
            paging = NON_PAGEABLE,
            hasSubdirectories = false,
            type = ALBUM,
            contentNodeType = TRACK,
            relatedPaths = ::getAlbumRelatedPaths,
            like = LikeConfig(rootNodePath("myAlbums"), ::likeAlbum),
            urlPatterns = listOf("*sample.com/album/<albumId:\\d+>*"),
            nodeSupplier = ::getAlbum,
            nodeContentSupplier = ::getAlbumTracks
        ),

        Directory(
            pattern = "*/artist-<artistId>/popularTracks",
            paging = NON_PAGEABLE,
            hasSubdirectories = false,
            contentNodeType = TRACK,
            nodeContentSupplier = ::getArtistPopularTracks
        ),
        Directory(
            pattern = "*/artist-<artistId>/albums",
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            contentNodeType = ALBUM,
            nodeContentSupplier = ::getArtistAlbums
        ),
        Directory(
            pattern = "*/artist-<artistId>/compilations",
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            contentNodeType = ALBUM,
            nodeContentSupplier = ::getArtistCompilations
        ),
        Directory(
            pattern = "*/artist-<artistId>/playlists",
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            contentNodeType = PLAYLIST,
            nodeContentSupplier = ::getArtistPlaylists
        ),
        Directory(
            pattern = "*/artist-<artistId>/similarArtists",
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            contentNodeType = ARTIST,
            nodeSupplier = ::getArtistSimilarArtistsSubdirectory,
            nodeContentSupplier = ::getArtistSimilarArtists
        ),
        MappedEntity(
            pattern = "*/artist-<artistId>",
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            type = ARTIST,
            contentNodeType = NODE,
            relatedPaths = ::getArtistRelatedPaths,
            like = LikeConfig(rootNodePath("myArtists"), ::likeArtist),
            urlPatterns = listOf("*sample.com/artist/<artistId:\\d+>*"),
            nodeSupplier = ::getArtist,
            nodeContentSupplier = ::getArtistSubdirectories
        ),

        MappedEntity(
            pattern = "*/playlist_owner-<ownerId>_id-<playlistId>",
            paging = PAGEABLE,
            hasSubdirectories = false,
            type = PLAYLIST,
            contentNodeType = TRACK,
            relatedPaths = ::getPlaylistRelatedPaths,
            like = LikeConfig(rootNodePath("myPlaylists"), ::likePlaylist),
            urlPatterns = listOf("*sample.com/users/<ownerId>/playlists/<playlistId:\\d+>*"),
            nodeSupplier = ::getPlaylist,
            nodeContentSupplier = ::getPlaylistTracks
        ),

        Directory(
            pattern = "*/type-<type>",
            paging = NON_PAGEABLE,
            hasSubdirectories = true,
            contentNodeType = RADIO,
            nodeContentSupplier = ::getRadioTypeRadios
        ),
        MappedEntity(
            pattern = "*/radio_type-<radioType>_tag-<radioTag>",
            paging = ENDLESSLY_PAGEABLE,
            hasSubdirectories = false,
            type = RADIO,
            contentNodeType = TRACK,
            nodeSupplier = ::getRadio,
            radioContentSupplier = ::getRadioTracks
        ),

        UnmappedEntity(
            type = PODCAST,
            like = LikeConfig(rootNodePath("myPodcasts"), ::likeAlbum)
        ),
        UnmappedEntity(
            type = AUDIOBOOK,
            like = LikeConfig(rootNodePath("myAudiobooks"), ::likeAlbum)
        ),
    )

    @Suppress("UNCHECKED_CAST")
    override suspend fun getTracksByMediaIds(mediaIds: List<String>): List<SampleTrack> =
        murglar.getTracksByMediaIds(mediaIds).convertSampleTracks(unmappedPath()) as List<SampleTrack>


    private suspend fun getMyTracks(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getMyTracks(page!!).convertSampleTracks(parentPath)

    private suspend fun getMyAlbums(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getMyAlbums().convertAlbums(parentPath)

    private suspend fun getMyArtists(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getMyArtists().convertArtists(parentPath)

    private suspend fun getMyPlaylists(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getMyPlaylists().convertSamplePlaylists(parentPath)

    private suspend fun getMyPodcasts(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getMyPodcasts().convertAlbums(parentPath)

    private suspend fun getMyAudiobooks(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getMyAudiobooks().convertAlbums(parentPath)

    private suspend fun getMyHistory(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getMyHistory(page!!).convertSampleTracks(parentPath)

    private suspend fun getRecommendations(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getRecommendationsPlaylists().convertSamplePlaylists(parentPath)

    private suspend fun getRadioTypeRadios(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getRadioLibrary().getTypeRadios(params["type"]!!).convert(::radioNodePath, parentPath)

    private suspend fun getRadioTypes(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getRadioLibrary().types.convert(::radioTypeNodePath, parentPath)

    private suspend fun searchTracks(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.searchTracks(params.getQuery(), page!!).convertSampleTracks(parentPath)

    private suspend fun searchAlbums(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.searchAlbums(params.getQuery(), page!!).convertAlbums(parentPath)

    private suspend fun searchArtists(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.searchArtists(params.getQuery(), page!!).convertArtists(parentPath)

    private suspend fun searchPlaylists(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.searchPlaylists(params.getQuery(), page!!).convertSamplePlaylists(parentPath)

    private suspend fun searchPodcasts(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.searchPodcasts(params.getQuery(), page!!).convertAlbums(parentPath)

    private suspend fun searchAudiobooks(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.searchAudiobooks(params.getQuery(), page!!).convertAlbums(parentPath)

    private suspend fun getAlbumTracks(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getAlbumTracks(params["albumId"]!!).convertSampleTracks(parentPath)

    private suspend fun getArtistPopularTracks(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getArtistPopularTracks(params["artistId"]!!).convertSampleTracks(parentPath)

    private suspend fun getArtistAlbums(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getArtistAlbums(params["artistId"]!!).convertAlbums(parentPath)

    private suspend fun getArtistCompilations(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getArtistCompilations(params["artistId"]!!).convertAlbums(parentPath)

    private suspend fun getArtistPlaylists(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getArtistPlaylists(params["artistId"]!!).convertSamplePlaylists(parentPath)

    private suspend fun getArtistSimilarArtists(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getArtistSimilarArtists(params["artistId"]!!).convertArtists(parentPath)

    private fun getArtistSubdirectories(parentPath: Path, page: Int?, params: Map<String, String>) = listOf(
        subdirectoryNode("popularTracks", messages.popularTracks, parentPath),
        subdirectoryNode("albums", messages.albums, parentPath),
        subdirectoryNode("compilations", messages.compilations, parentPath),
        subdirectoryNode("playlists", messages.playlists, parentPath)
    )

    private suspend fun getPlaylistTracks(parentPath: Path, page: Int?, params: Map<String, String>) =
        murglar.getPlaylistTracks(params["ownerId"]!!, params["playlistId"]!!, page!!).convertSampleTracks(parentPath)

    private suspend fun getRadioTracks(
        node: Node,
        settingsUpdate: RadioSettingsUpdate?,
        params: Map<String, String>
    ): RadioNodeUpdate {
        val radio = node.to<SampleRadio>()
        val radioUpdate = murglar.getRadioNextTracks(radio, settingsUpdate)
        val updatedRadio = radioUpdate.updatedRadio.apply { withNodeAttributes(node) }
        val tracks = radioUpdate.nextTracks.convertSampleTracks(node.nodePath)
        return RadioNodeUpdate(updatedRadio, tracks)
    }

    private suspend fun getArtist(parentPath: Path, params: Map<String, String>) =
        murglar.getArtist(params["artistId"]!!).convertArtist(parentPath)

    private fun getArtistSimilarArtistsSubdirectory(parentPath: Path, params: Map<String, String>) = subdirectoryNode(
        "similarArtists", messages.similarArtists, parentPath.child("artist-${params["artistId"]}")
    )

    private suspend fun getAlbum(parentPath: Path, params: Map<String, String>) =
        murglar.getAlbum(params["albumId"]!!).convertAlbum(parentPath)

    private suspend fun getPlaylist(parentPath: Path, params: Map<String, String>) =
        murglar.getPlaylist(params["ownerId"]!!, params["playlistId"]!!).convertSamplePlaylist(parentPath)

    private suspend fun getTrack(parentPath: Path, params: Map<String, String>) =
        murglar.getTrack(params["trackId"]!!, params["albumId"]).convertSampleTrack(parentPath)

    private suspend fun getRadio(parentPath: Path, params: Map<String, String>): Node {
        val radioType = params["radioType"]!!
        val radioTag = params["radioTag"]!!
        val radio = when (radioType) {
            "track" -> murglar.getTrackRadio(murglar.getTrack(radioTag, null))
            "album" -> murglar.getAlbumRadio(murglar.getAlbum(radioTag))
            "artist" -> murglar.getArtistRadio(murglar.getArtist(radioTag))
            "playlist" -> {
                val ids = radioTag.split('_')
                murglar.getPlaylistRadio(murglar.getPlaylist(ids[0], ids[1]))
            }

            else -> error("Unknown radio type '$radioType'!")
        }
        return radio.convert(::radioNodePath, parentPath)
    }


    private fun getTrackRelatedPaths(node: Node): List<NamedPath> {
        val track = node.to<SampleTrack>()
        val paths = mutableListOf<NamedPath>()

        if (track.hasAlbum) {
            val albumPath = unmappedPath().child("album-${track.albumId}")
            paths += NamedPath(track.albumName!!, ALBUM, albumPath)
        }

        for (i in track.artistNames.indices) {
            val artistPath = unmappedPath().child("artist-${track.artistIds[i]}")
            paths += NamedPath(track.artistNames[i], ARTIST, artistPath)
        }

        if (node.nodeType == TRACK) {
            val radioPath = unmappedPath().child("radio_type-track_tag-${track.id}")
            paths += NamedPath("${messages.radio}: ${track.title}", RADIO, radioPath)
        }

        return paths
    }

    private fun getAlbumRelatedPaths(node: Node): List<NamedPath> {
        val album = node.to<SampleAlbum>()
        val paths = mutableListOf<NamedPath>()

        if (album.hasArtist) {
            val artistPath = unmappedPath().child("artist-${album.artistId}")
            paths += NamedPath(album.artistName!!, ARTIST, artistPath)
        }

        if (node.nodeType == ALBUM) {
            val radioPath = unmappedPath().child("radio_type-album_tag-${album.id}")
            paths += NamedPath("${messages.radio}: ${album.title}", RADIO, radioPath)
        }

        return paths
    }

    private fun getArtistRelatedPaths(node: Node): List<NamedPath> {
        val artist = node.to<SampleArtist>()
        val radioPath = unmappedPath().child("radio_type-artist_tag-${artist.id}")
        val radioNamedPath = NamedPath("${messages.radio}: ${artist.name}", RADIO, radioPath)

        val similarArtistsPath = unmappedPath().child("artist-${artist.id}/similarArtists")
        val similarArtistsNamedPath = NamedPath(messages.similarArtists, NODE, similarArtistsPath)

        return listOf(radioNamedPath, similarArtistsNamedPath)
    }

    private fun getPlaylistRelatedPaths(node: Node): List<NamedPath> {
        val playlist = node.to<SamplePlaylist>()
        val radioPath = unmappedPath().child("radio_type-playlist_tag-${playlist.ownerId}_${playlist.id}")
        val radioNamedPath = NamedPath("${messages.radio}: ${playlist.title}", RADIO, radioPath)

        return listOf(radioNamedPath)
    }


    private suspend fun likeTrack(node: Node, like: Boolean) {
        val track = node.to<SampleTrack>()
        if (like)
            murglar.addTrackToFavorite(track)
        else
            murglar.removeTrackFromFavorite(track)
    }

    private suspend fun likeAlbum(node: Node, like: Boolean) {
        val album = node.to<SampleAlbum>()
        if (like)
            murglar.addAlbumToFavorite(album)
        else
            murglar.removeAlbumFromFavorite(album)
    }

    private suspend fun likeArtist(node: Node, like: Boolean) {
        val artist = node.to<SampleArtist>()
        if (like)
            murglar.addArtistToFavorite(artist)
        else
            murglar.removeArtistFromFavorite(artist)
    }

    private suspend fun likePlaylist(node: Node, like: Boolean) {
        val playlist = node.to<SamplePlaylist>()
        if (like)
            murglar.addPlaylistToFavorite(playlist)
        else
            murglar.removePlaylistFromFavorite(playlist)
    }


    private suspend fun SampleTrack.handleStartEvent() {
        if (isRadioRelated())
            murglar.reportRadioTrackStart(radioFromTrack(), this)
        murglar.reportTrackStart(this)
    }

    private suspend fun SampleTrack.handleEndEvent(endTimeMs: Int) {
        murglar.reportTrackEnd(this, endTimeMs)
        if (isRadioRelated())
            murglar.reportRadioTrackEnd(radioFromTrack(), this, endTimeMs)
    }


    private fun SampleTrack.isRadioRelated() = match("*/radio_type-<type>_tag-<tag>/*", nodePath.toString()) != null

    private fun SampleTrack.radioFromTrack(): SampleRadio {
        val path = nodePath.toString()
        val params = match("*/radio_type-<type>_tag-<tag>/*", path)
        checkNotNull(params) { "Matching failed for path '$path'!" }
        return SampleRadio.fromTypeAndTag(params["type"]!!, params["tag"]!!)
    }

    private fun List<SampleTrack>.convertSampleTracks(parentPath: Path) = convert(::trackNodePath, parentPath)

    private fun SampleTrack.convertSampleTrack(parentPath: Path) = convert(::trackNodePath, parentPath)

    private fun trackNodePath(parentPath: Path, track: SampleTrack): Path =
        parentPath.child("${track.nodeType}-${track.id}")

    private fun List<SamplePlaylist>.convertSamplePlaylists(parentPath: Path) = convert(::playlistNodePath, parentPath)

    private fun SamplePlaylist.convertSamplePlaylist(parentPath: Path) = convert(::playlistNodePath, parentPath)

    private fun playlistNodePath(parentPath: Path, playlist: SamplePlaylist): Path =
        parentPath.child("playlist_owner-${playlist.ownerId}_id-${playlist.id}")

    private fun radioTypeNodePath(parentPath: Path, radioType: RadioType): Path =
        parentPath.child("type-${radioType.type}")

    private fun radioNodePath(parentPath: Path, radio: SampleRadio): Path =
        parentPath.child("radio_type-${radio.type}_tag-${radio.tag}")
}
