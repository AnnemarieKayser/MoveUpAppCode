package com.example.moveup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BasicViewModel : ViewModel() {

    // Speichern der gefundenen Geräte
    private val _discoveredDevices = MutableLiveData<MutableList<String>>()
    val discoveredDevices: LiveData<MutableList<String>>
        get() = _discoveredDevices

    // Speichern der Mac-Adresse des verbundenen ESP32 thing
    private val _deviceAddress = MutableLiveData<String>()
    val deviceAddress: LiveData<String>
        get() = _deviceAddress

    // Speichern des aktuellen Datums
    private val _date = MutableLiveData<String>()
    val date: LiveData<String>
        get() = _date

    // Speichern der eingegebenen Zeit für eine Challenge
    private val _timeChallenge = MutableLiveData<Int>()
    val timeChallenge: LiveData<Int>
        get() = _timeChallenge

    // Speichern des Status der Messung
    private val _statusMeasurment = MutableLiveData<Boolean>()
    val statusMeasurment: LiveData<Boolean>
        get() = _statusMeasurment


    // Initialisierung der Variablen
    init {
        _discoveredDevices.value = mutableListOf()
        _deviceAddress.value = ""
        _date.value = ""
        _timeChallenge.value = 0
        _statusMeasurment.value = false
    }


    // === getter- und setter-Funktionen === //
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

    fun getStatusMeasurment(): Boolean {
        return _statusMeasurment.value!!
    }

    fun setStatusMeasurment(statusMeasurment: Boolean) {
        _statusMeasurment.value = statusMeasurment
    }

    // Extension Function, um Änderung in den Einträgen von Listen
    // dem Observer anzeigen zu können
    fun <T> MutableLiveData<T>.notifyObserver() {
        this.value = this.value
    }

}

