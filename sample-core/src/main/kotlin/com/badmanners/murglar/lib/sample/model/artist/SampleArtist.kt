package com.badmanners.murglar.lib.sample.model.artist

import com.badmanners.murglar.lib.core.model.artist.BaseArtist
import com.badmanners.murglar.lib.core.utils.contract.Model


@Model
class SampleArtist(
    id: String,
    name: String,
    smallCoverUrl: String?,
    bigCoverUrl: String?,
    val albumsCount: Int,
    val tracksCount: Int,
    serviceUrl: String
) : BaseArtist(
    id = id,
    name = name,
    smallCoverUrl = smallCoverUrl,
    bigCoverUrl = bigCoverUrl,
    serviceUrl = serviceUrl
)