package com.capstone.navicamp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecurityOfficerViewModel : ViewModel() {
    private val _pendingItems = MutableLiveData<List<LocationItem>>()
    val pendingItems: LiveData<List<LocationItem>> get() = _pendingItems

    fun fetchPendingItems() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                MySQLHelper.getPendingItems()
            }
            _pendingItems.postValue(items)
        }
    }
}