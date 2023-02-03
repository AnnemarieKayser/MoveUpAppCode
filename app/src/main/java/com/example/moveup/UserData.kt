package com.example.moveup

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp
import java.util.*
import kotlin.collections.ArrayList

class UserData {

    private var counterLeanBack = 0
    private var progressTime = 0F
    private var progressTimeMax = 0F
    private var hour = 0
    private var counterBentBack = 0
    private var counterStraightBack = 0
    private var arrayDynamicPhase = arrayListOf<Any?>(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)
    private var arrayBentBack = arrayListOf<Any?>(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)
    private var arrayLeanBack = arrayListOf<Any?>(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)

    // serverTimestamp soll automatisch vom Server gesetzt werden
    @ServerTimestamp
    private var serverTimestamp: Timestamp? = null


    fun getServerTimestamp(): Timestamp? {
        return serverTimestamp
    }

    fun setServerTimestamp(serverTimestamp: Timestamp?) {
        this.serverTimestamp = serverTimestamp
    }

    fun getCounterLeanBack(): Int {
        return counterLeanBack
    }

    fun setCounterLeanBack(counterLeanBack: Int) {
        this.counterLeanBack = counterLeanBack
    }

    fun getHour(): Int {
        return hour
    }

    fun setHour(hour: Int) {
        this.hour = hour
    }

    fun getCounterStraightBack(): Int {
        return counterStraightBack
    }

    fun setCounterStraightBack(counterStraightBack: Int) {
        this.counterStraightBack = counterStraightBack
    }

    fun getCounterBentBack(): Int {
        return counterBentBack
    }

    fun setCounterBentBack(counterBentBack: Int) {
        this.counterBentBack = counterBentBack
    }

    fun getProgressTime(): Float {
        return progressTime
    }

    fun setProgressTime(progressTime: Float) {
        this.progressTime = progressTime
    }

    fun getProgressTimeMax(): Float {
        return progressTimeMax
    }

    fun setProgressTimeMax(progressTimeMax: Float) {
        this.progressTimeMax = progressTimeMax
    }

    fun getArrayBentBack(): ArrayList<Any?> {
        return arrayBentBack
    }

    fun setArrayBentBack(arrayBentBack: ArrayList<Any?>) {
        this.arrayBentBack = arrayBentBack
    }

    fun getArrayLeanBack(): ArrayList<Any?> {
        return arrayLeanBack
    }

    fun setArrayLeanBack(arrayLeanBack: ArrayList<Any?>) {
        this.arrayLeanBack = arrayLeanBack
    }

    fun getArrayDynamicPhase(): ArrayList<Any?> {
        return arrayDynamicPhase
    }

    fun setArrayDynamicPhase(arrayDynamicPhase: ArrayList<Any?>) {
        this.arrayDynamicPhase = arrayDynamicPhase
    }


    override fun toString(): String {
        return "UserData{" +
                ", counterLeanBack='" + counterLeanBack +
                ", counterStraightBack='" + counterStraightBack +
                ", counterBentBack='" + counterBentBack +
                ", dynamicPhase='" + arrayDynamicPhase +
                ", hour='" + hour +
                ", progressTime='" + progressTime +
                ", progressTimeMax='" + progressTimeMax +
                ", arrayBentBack='" + arrayBentBack +
                ", arrayLeanBack='" + arrayLeanBack +
                '}'
    }
}