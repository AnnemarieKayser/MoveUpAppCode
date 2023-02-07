package com.example.moveup

class UserDataConfig {

    private var thresholdBentBack = -40

    fun getThresholdBentBack(): Int {
        return thresholdBentBack
    }

    fun setThresholdBentBack(thresholdBentBack: Int) {
        this.thresholdBentBack = thresholdBentBack
    }

    override fun toString(): String {
        return "UserDataConfig{" +
                ", thresholdBentBack='" + thresholdBentBack +
                '}'
    }
}