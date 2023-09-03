package com.badmanners.murglar.lib.sample.model.album

import com.badmanners.murglar.lib.core.model.album.BaseAlbum
import com.badmanners.murglar.lib.core.utils.contract.Model
import org.threeten.bp.LocalDate


@Model
class SampleAlbum(
    id: String,
    title: String,
    additionalInfo: String?,
    releaseDate: LocalDate?,
    smallCoverUrl: String?,
    bigCoverUrl: String?,
    tracksCount: Int,
    artistId: String?,
    artistName: String?,
    genre: String?,
    override val nodeType: String,
    explicit: Boolean,
    serviceUrl: String
) : BaseAlbum(
    id = id,
    title = title,
    description = additionalInfo,
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
