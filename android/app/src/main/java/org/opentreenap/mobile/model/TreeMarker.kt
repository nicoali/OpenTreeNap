package org.opentreenap.mobile.model

data class TreeMarker(
    val plotId: Int,
    val treeId: Int?,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String?
)
