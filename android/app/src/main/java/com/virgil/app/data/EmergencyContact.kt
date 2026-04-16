package com.virgil.app.data

data class EmergencyContact(
    val name: String,
    val phone: String,
    val isPrimary: Boolean = false,
)
