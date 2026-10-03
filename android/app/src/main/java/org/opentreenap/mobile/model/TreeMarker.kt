package org.opentreenap.mobile.model

data class TreeExtraField(
    val label: String,
    val value: String
)

data class ReverseGeocodeResult(
    val street: String?,
    val city: String?,
    val postalCode: String?,
    val formatted: String?,
    val provider: String? = null,
    val attribution: String? = null
)

data class TreeMarker(
    val plotId: Int,
    val treeId: Int?,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String?,
    val commonName: String? = null,
    val scientificName: String? = null,
    val speciesId: Int? = null,
    val address: String? = null,
    val addressStreet: String? = null,
    val addressCity: String? = null,
    val addressZip: String? = null,
    val diameter: Double? = null,
    val height: Double? = null,
    val customId: String? = null,
    val photoUrl: String? = null,
    val isMonumental: Boolean = false,
    val updatedAt: String? = null,
    val updatedBy: String? = null,
    val detailUrl: String? = null,
    val extraFields: List<TreeExtraField> = emptyList()
)
