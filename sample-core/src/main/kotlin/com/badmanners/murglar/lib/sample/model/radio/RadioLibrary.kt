package com.badmanners.murglar.lib.sample.model.radio

import com.badmanners.murglar.lib.core.utils.contract.Model


@Model
class RadioLibrary(
    val types: List<RadioType>,
    private val typesToRadio: Map<String, List<SampleRadio>>
) {
    fun getTypeRadios(type: RadioType) = getTypeRadios(type.type)

    fun getTypeRadios(type: String) = typesToRadio[type]!!
}
