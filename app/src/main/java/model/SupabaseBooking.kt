package model

import kotlinx.serialization.Serializable

@Serializable
data class SupabaseBooking(
    val id: String? = null,
    val user_id: String,
    val user_name: String,
    val user_phone: String,
    val user_email: String? = null,
    val property_id: String,
    val property_name: String,
    val seat_count: Int,
    val move_in_date: String,
    val notes: String? = null,
    val property_owner_id: String? = null,
    val status: String = "pending",
    
    // Identity & Verification
    val nid_birth_cert: String? = null,
    val id_card_url: String? = null,
    val guardian_name: String? = null,
    
    // Contact & Emergency
    val permanent_address: String? = null,
    val emergency_contact_name: String? = null,
    val emergency_contact_relation: String? = null,
    val emergency_contact_phone: String? = null,
    
    // Accommodation Specifics
    val stay_duration: String? = null,
    val preferred_room_type: String? = null,
    val institution_workplace: String? = null,
    
    // Preferences
    val gender: String? = null,
    val dietary_preference: String? = null,
    val smoking_habit: String? = null
)