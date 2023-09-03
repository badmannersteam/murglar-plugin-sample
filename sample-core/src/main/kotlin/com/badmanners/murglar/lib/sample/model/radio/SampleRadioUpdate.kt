package com.badmanners.murglar.lib.sample.model.radio

import com.badmanners.murglar.lib.core.model.radio.RadioUpdate
import com.badmanners.murglar.lib.core.utils.contract.Model
import com.badmanners.murglar.lib.sample.model.track.SampleTrack


@Model
class SampleRadioUpdate(
    updatedRadio: SampleRadio,
    nextTracks: List<SampleTrack>
) : RadioUpdate<SampleRadio, SampleTrack>(updatedRadio, nextTracks)
