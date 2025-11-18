package com.badmanners.murglar.lib.sample.model.playlist

import com.badmanners.murglar.lib.core.model.playlist.BasePlaylist
import com.badmanners.murglar.lib.core.utils.contract.Model


@Model
class SamplePlaylist(
    id: String,
    title: String,
    description: String?,
    tracksCount: Int,
    smallCoverUrl: String?,
    bigCoverUrl: String?,
    serviceUrl: String,
    val ownerLogin: String,
    val ownerId: String
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