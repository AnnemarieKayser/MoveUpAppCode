package com.example.moveup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BasicViewModel : ViewModel() {

    private var _someTestData = "Hello ViewModel"
    val someTestData: String
        get() = _someTestData

    private val _terminList = MutableLiveData<MutableList<String>>()
    val terminList: LiveData<MutableList<String>>
        get() = _terminList

    private val _terminSelected = MutableLiveData<String>()
    val terminSelected: LiveData<String>
        get() = _terminSelected

    init {
        _terminList.value = mutableListOf("Testtermin 1", "Testtermin 2")
        _terminSelected.value = "Testtermin selected"
    }


    fun getTerminList(): List<String>? {
        return _terminList.value
    }
    fun getTerminSelected(): String {
        return _terminSelected.value.toString()
    }

    // Extension Function, um Änderung in den Einträgen von Listen
    // dem Observer anzeigen zu können
    fun <T> MutableLiveData<T>.notifyObserver() {
        this.value = this.value
    }
}

