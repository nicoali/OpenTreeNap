package org.opentreenap.mobile.model

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
    val diameter: Double? = null,
    val height: Double? = null,
    val customId: String? = null,
    val photoUrl: String? = null,
    val isMonumental: Boolean = false
)
