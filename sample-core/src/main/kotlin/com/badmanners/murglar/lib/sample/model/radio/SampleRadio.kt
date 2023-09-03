package com.badmanners.murglar.lib.sample.model.radio

import com.badmanners.murglar.lib.core.model.radio.BaseRadio
import com.badmanners.murglar.lib.core.utils.contract.Model


@Model
class SampleRadio private constructor(
    val type: String,
    val tag: String,
    name: String,
    coverUrl: String?,
    val queue: String
) : BaseRadio(
    id = "$type:$tag",
    name = name,
    summary = null,
    smallCoverUrl = coverUrl
) {

    constructor(type: String, tag: String, name: String, coverUrl: String?) : this(type, tag, name, coverUrl, "")

    val hasEmptyQueue: Boolean
        get() = queue.isEmpty()

    fun fromNewQueue(queue: String) = SampleRadio(type, tag, nodeName, smallCoverUrl, queue)

    companion object {
        fun fromTypeAndTag(type: String, tag: String) = SampleRadio(
            type = type,
            tag = tag,
            name = "",
            coverUrl = null
        )
    }
}
