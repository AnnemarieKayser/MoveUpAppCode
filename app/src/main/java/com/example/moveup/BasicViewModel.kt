package com.example.moveup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BasicViewModel : ViewModel() {

    private var _someTestData = "Hello ViewModel"
    val someTestData: String
        get() = _someTestData

    private val _discoveredDevices = MutableLiveData<MutableList<String>>()
    val discoveredDevices: LiveData<MutableList<String>>
        get() = _discoveredDevices

    private val _deviceAddress = MutableLiveData<String>()
    val deviceAddress: LiveData<String>
        get() = _deviceAddress

    private val _date = MutableLiveData<String>()
    val date: LiveData<String>
        get() = _date

    private val _timeChallenge = MutableLiveData<Int>()
    val timeChallenge: LiveData<Int>
        get() = _timeChallenge

    private val _savedData = MutableLiveData<Boolean>()
    val savedData: LiveData<Boolean>
        get() = _savedData

    private val _savedDataChallenge = MutableLiveData<Boolean>()
    val savedDataChallenge: LiveData<Boolean>
        get() = _savedDataChallenge

    init {
        _discoveredDevices.value = mutableListOf()
        _deviceAddress.value = ""
        _date.value = ""
        _savedData.value = false
        _savedDataChallenge.value = false
        _timeChallenge.value = 0
    }


    fun getDeviceList(): List<String>? {
        return _discoveredDevices.value
    }

    fun getDeviceAddress(): String {
        return _deviceAddress.value.toString()
    }

    fun setDeviceAddress(address: String) {
        _deviceAddress.value = address
    }

    fun addDevice(device: String) {
        if (!(_discoveredDevices.value?.contains(device) ?: true)) {
            _discoveredDevices.value?.add(device)
            _discoveredDevices.notifyObserver()
        }
    }

    fun getDate(): String {
        return _date.value.toString()
    }

    fun setDate(date: String) {
        _date.value = date
    }

    fun getTimeChallenge(): Int {
        return _timeChallenge.value!!
    }

    fun setTimeChallenge(timeChallenge: Int) {
        _timeChallenge.value = timeChallenge
    }

    fun getSavedData(): Boolean {
        return _savedData.value!!
    }

    fun setSavedData(savedData: Boolean) {
        _savedData.value = savedData
    }

    fun getSavedDataChallenge(): Boolean {
        return _savedDataChallenge.value!!
    }

    fun setSavedDataChallenge(savedDataChallenge: Boolean) {
        _savedDataChallenge.value = savedDataChallenge
    }

    // Extension Function, um Änderung in den Einträgen von Listen
    // dem Observer anzeigen zu können
    fun <T> MutableLiveData<T>.notifyObserver() {
        this.value = this.value
    }

}

