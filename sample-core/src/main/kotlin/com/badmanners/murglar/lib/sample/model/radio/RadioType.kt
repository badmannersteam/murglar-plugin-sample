package com.badmanners.murglar.lib.sample.model.radio

import com.badmanners.murglar.lib.core.model.node.BaseNode
import com.badmanners.murglar.lib.core.utils.contract.Model


@Model
class RadioType(type: String, name: String) : BaseNode(type, name) {

    val type: String
        get() = nodeId
}
