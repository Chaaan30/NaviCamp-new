package com.capstone.navicamp

data class LocationItem(
    val locationID: String,
    val userID: String,
    val fullName: String,
    val floorLevel: String,
    val status: String,
    val latitude: Double,
    val longitude: Double,
    val dateTime: String,
    val officerName: String? = null,
    val deviceID: String,
    val assistanceType: String = "MANUAL" // "FALL_DETECTION" or "MANUAL"
)