package com.example.moveup

class UserDataSetting {


    private var vibration = "VIBON"
    private var vibrationLength = 1500

    fun getVibration(): String {
        return vibration
    }

    fun setVibration(vibration: String) {
        this.vibration = vibration
    }

    fun getVibrationLength(): Int {
        return vibrationLength
    }

    fun setVibrationLength(vibrationLength: Int) {
        this.vibrationLength = vibrationLength
    }

    override fun toString(): String {
        return "UserDataEinstellungen{" +
                ", vibration='" + vibration +
                ", vibrationLength='" + vibrationLength +
                '}'
    }
}