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

    private val _terminSelected = MutableLiveData<String>()
    val terminSelected: LiveData<String>
        get() = _terminSelected

    init {
        _discoveredDevices.value = mutableListOf()
        _terminSelected.value = "Testtermin selected"
    }


    fun getDeviceList(): List<String>? {
        return _discoveredDevices.value
    }
    fun getTerminSelected(): String {
        return _terminSelected.value.toString()
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

