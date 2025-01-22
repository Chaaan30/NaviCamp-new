package com.capstone.navicamp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IncidentLogViewModel : ViewModel() {
    private val _pendingItems = MutableLiveData<List<LocationItem>>()
    private val _incidentData = MutableLiveData<List<List<String>>>()
    val incidentData: LiveData<List<List<String>>> get() = _incidentData

    // Method to fetch data from the database
    fun fetchIncidentData() {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                MySQLHelper.getIncidentData() // Fetch data from MySQLHelper
            }
            _incidentData.postValue(data) // Update LiveData with fetched data
        }
    }

}