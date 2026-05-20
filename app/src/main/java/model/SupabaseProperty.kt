package model

import kotlinx.serialization.Serializable

@Serializable
data class SupabaseProperty(
    val id: String? = null,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val availableSeats: Int,
    val image_url: String,
    val price: String? = null,
    val description: String? = null,
    val type: String? = null,
    val room_type: String? = null,
    val owner_id: String? = null,
    val contact_number: String? = null,
    val other_images: List<String>? = emptyList()
)