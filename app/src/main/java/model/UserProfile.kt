package model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val role: String = "student",
    val profile_picture_url: String? = null,
    val gender: String? = null,
    val occupation_institution: String? = null,
    val present_address: String? = null,
    val permanent_address: String? = null,
    val nid_birth_cert: String? = null,
    val emergency_contact_name: String? = null,
    val emergency_contact_phone: String? = null,
    val stay_duration: String? = null,
    val dietary_preference: String? = null
)