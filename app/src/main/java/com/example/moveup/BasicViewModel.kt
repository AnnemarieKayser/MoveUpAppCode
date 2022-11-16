package com.example.moveup

import androidx.lifecycle.ViewModel

class BasicViewModel : ViewModel() {

    private var _someTestData = "Hello ViewModel"
    val someTestData: String
        get() = _someTestData
}
