package com.capstone.navicamp.api

import retrofit2.http.GET
import retrofit2.Call

data class IncidentModel(
    val alertID: String,
    val userID: String,
    val deviceID: String,
    val locationID: String,
    val status: String,
    val alertDateTime: String,
    val alertDescription: String,
    val officerResponded: String?,
    val resolvedOn: String?
)

interface IncidentApiService {
    @GET("api/incidents")
    fun getIncidents(): Call<List<IncidentModel>>
}