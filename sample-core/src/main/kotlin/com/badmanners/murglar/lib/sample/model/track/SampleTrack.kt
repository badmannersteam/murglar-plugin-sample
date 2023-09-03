package com.badmanners.murglar.lib.sample.model.track

import com.badmanners.murglar.lib.core.model.track.BaseTrack
import com.badmanners.murglar.lib.core.model.track.source.Source
import com.badmanners.murglar.lib.core.utils.contract.Model
import org.threeten.bp.LocalDate


@Model
class SampleTrack(
    id: String,
    title: String,
    additionalInfo: String?,
    artistIds: List<String>,
    artistNames: List<String>,
    albumId: String?,
    albumName: String?,
    albumReleaseDate: LocalDate?,
    val albumArtist: String?,
    val albumAdditionalInfo: String?,
    indexInAlbum: Int?,
    volumeNumber: Int?,
    durationMs: Long,
    genre: String?,
    explicit: Boolean,
    val lyricsId: String?,
    gain: String?,
    peak: String?,
    sources: List<Source>,
    override val nodeType: String,
    mediaId: String,
    smallCoverUrl: String?,
    bigCoverUrl: String?,
    serviceUrl: String
) : BaseTrack(
    id = id,
    title = title,
    subtitle = additionalInfo,
    artistIds = artistIds,
    artistNames = artistNames,
    albumId = albumId,
    albumName = albumName,
    albumReleaseDate = albumReleaseDate,
    indexInAlbum = indexInAlbum,
    volumeNumber = volumeNumber,
    durationMs = durationMs,
    genre = genre,
    explicit = explicit,
    gain = gain,
    peak = peak,
    sources = sources,
    mediaId = mediaId,
    smallCoverUrl = smallCoverUrl,
    bigCoverUrl = bigCoverUrl,
    serviceUrl = serviceUrl
) {
    val hasAlbumArtist: Boolean
        get() = !albumArtist.isNullOrEmpty()

    val hasAlbumAdditionalInfo: Boolean
        get() = !albumAdditionalInfo.isNullOrEmpty()

    val hasLyrics: Boolean
        get() = !lyricsId.isNullOrEmpty()
}
