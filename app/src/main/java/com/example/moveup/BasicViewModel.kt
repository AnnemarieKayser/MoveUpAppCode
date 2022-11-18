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

    init {
        _discoveredDevices.value = mutableListOf()
        _deviceAddress.value = ""

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

    fun addDevice(termin: String) {
        if (!(_discoveredDevices.value?.contains(termin) ?: true)) {
            _discoveredDevices.value?.add(termin)
            _discoveredDevices.notifyObserver()
        }
    }

    // Extension Function, um Änderung in den Einträgen von Listen
    // dem Observer anzeigen zu können
    fun <T> MutableLiveData<T>.notifyObserver() {
        this.value = this.value
    }

}

