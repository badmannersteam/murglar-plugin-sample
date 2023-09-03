package com.badmanners.murglar.lib.sample.model.playlist

import com.badmanners.murglar.lib.core.model.playlist.BasePlaylist
import com.badmanners.murglar.lib.core.utils.contract.Model


@Model
class SamplePlaylist(
    id: String,
    title: String,
    description: String?,
    smallCoverUrl: String?,
    bigCoverUrl: String?,
    tracksCount: Int,
    val ownerLogin: String,
    val ownerId: String,
    serviceUrl: String
) : BasePlaylist(
    id = id,
    title = title,
    description = description,
    tracksCount = tracksCount,
    explicit = false,
    smallCoverUrl = smallCoverUrl,
    bigCoverUrl = bigCoverUrl,
    serviceUrl = serviceUrl
)