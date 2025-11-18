package com.badmanners.murglar.lib.sample.model.album

import com.badmanners.murglar.lib.core.model.album.AlbumType
import com.badmanners.murglar.lib.core.model.album.BaseAlbum
import com.badmanners.murglar.lib.core.utils.contract.Model
import org.threeten.bp.LocalDate


@Model
class SampleAlbum(
    id: String,
    title: String,
    additionalInfo: String?,
    type: AlbumType,
    artistId: String?,
    artistName: String?,
    tracksCount: Int,
    releaseDate: LocalDate?,
    genre: String?,
    explicit: Boolean,
    smallCoverUrl: String?,
    bigCoverUrl: String?,
    serviceUrl: String,
    override val nodeType: String
) : BaseAlbum(
    id = id,
    title = title,
    description = additionalInfo,
    type = type,
    artistIds = listOfNotNull(artistId),
    artistNames = listOfNotNull(artistName),
    tracksCount = tracksCount,
    releaseDate = releaseDate,
    genre = genre,
    explicit = explicit,
    smallCoverUrl = smallCoverUrl,
    bigCoverUrl = bigCoverUrl,
    serviceUrl = serviceUrl
)
