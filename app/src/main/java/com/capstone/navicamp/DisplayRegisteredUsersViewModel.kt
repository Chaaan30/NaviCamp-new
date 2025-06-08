package com.capstone.navicamp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DisplayRegisteredUsersViewModel : ViewModel() {
    private val _userData = MutableLiveData<List<UserData>>()
    val userData: LiveData<List<UserData>> = _userData

    fun fetchUsers() {
        viewModelScope.launch(Dispatchers.IO) {
            val users = MySQLHelper.getAllVerifiedUsers()
            _userData.postValue(users)
        }
    }
}