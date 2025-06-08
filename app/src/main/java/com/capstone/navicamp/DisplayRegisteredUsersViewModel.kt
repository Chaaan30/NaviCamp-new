package com.capstone.navicamp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import com.capstone.navicamp.MySQLHelper

class DisplayRegisteredUsersViewModel : ViewModel() {
    private val _userData = MutableLiveData<List<UserData>>()
    val userData: LiveData<List<UserData>> = _userData

    fun fetchUsers(
        userType: String?,
        creationDateType: String?,
        selectedDate: Date?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val users = MySQLHelper.getVerifiedLocomotorUsersFiltered(
                userType,
                creationDateType,
                selectedDate
            )
            _userData.postValue(users)
        }
    }
}