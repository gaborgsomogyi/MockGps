package com.lilstiffy.mockgps.ui.models

import com.lilstiffy.mockgps.model.LatLng

data class LocationEntry(
    var latLng: LatLng,
    var addressLine: String? = null,
)
