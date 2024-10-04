package com.strobel.emercast.db.models

import com.openapi.gen.android.dto.JurisdictionMarkerDTO
import com.openapi.gen.android.dto.JurisdictionMarkerKindEnumDTO

data class JurisdictionMarker (
    val authorityId: String,
    val authorityCreated: Long,
    val latitude: Float,
    val longitude: Float,
    val kind: String,
    val radiusMeters: Int
) {
    fun toOpenAPI(): JurisdictionMarkerDTO {
        return JurisdictionMarkerDTO(this.latitude, this.longitude, toJurisdictionMarkerKindEnumDTO(this.kind), this.radiusMeters)
    }

    private fun toJurisdictionMarkerKindEnumDTO(value: String): JurisdictionMarkerKindEnumDTO {
        return JurisdictionMarkerKindEnumDTO.cIRCLE
    }
}