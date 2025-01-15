package com.capstone.navicamp

import kotlinx.serialization.Serializable

class user_data {
}
@Serializable
data class User(
    val userName: String,
    val userType: String,
    val fullName: String,
    val email: String,
    val contactNumber: Long,
    val password: String,
    val createdOn: String,
    val updatedOn: String
)