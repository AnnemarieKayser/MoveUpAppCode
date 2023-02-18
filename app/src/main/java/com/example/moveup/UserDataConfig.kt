package com.example.moveup

class UserDataConfig {

    // Variablen zum Speichern der Konfigurationswerte in der Datenbank
    private var thresholdBentBack = -30
    private var thresholdLeanBack = 20

    fun getThresholdBentBack(): Int {
        return thresholdBentBack
    }

    fun setThresholdBentBack(thresholdBentBack: Int) {
        this.thresholdBentBack = thresholdBentBack
    }

    fun getThresholdLeanBack(): Int {
        return thresholdLeanBack
    }

    fun setThresholdLeanBack(thresholdLeanBack: Int) {
        this.thresholdLeanBack = thresholdLeanBack
    }

    override fun toString(): String {
        return "UserDataConfig{" +
                ", thresholdBentBack='" + thresholdBentBack +
                ", thresholdLeanBack='" + thresholdLeanBack +
                '}'
    }
}