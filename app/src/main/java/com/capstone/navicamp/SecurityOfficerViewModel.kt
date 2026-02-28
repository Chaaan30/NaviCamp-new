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

    private val _userCount = MutableLiveData<Int>()
    val userCount: LiveData<Int> get() = _userCount

    private val _deviceCount = MutableLiveData<Int>()
    val deviceCount: LiveData<Int> get() = _deviceCount


    fun fetchPendingItems(currentOfficerName: String? = null) {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                MySQLHelper.getPendingItems(currentOfficerName)
            }
            _pendingItems.postValue(items)
        }
    }

    fun fetchUserCount() {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                MySQLHelper.getTotalPwdUserCount()
            }
            _userCount.postValue(count)
        }
    }

    fun fetchDeviceCount() {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                MySQLHelper.getInUseWheelchairCount()
            }
            _deviceCount.postValue(count)
        }
    }

}