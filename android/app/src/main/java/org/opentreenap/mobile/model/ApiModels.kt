package org.opentreenap.mobile.model

data class ApiUser(
    val id: Int,
    val username: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null
)

data class SpeciesItem(
    val id: Int,
    val commonName: String,
    val scientificName: String,
    val value: String
)


data class InstancePermissions(
    val canAddTree: Boolean,
    val canEditTree: Boolean,
    val canEditTreePhoto: Boolean
)
