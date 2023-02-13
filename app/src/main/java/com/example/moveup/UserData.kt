package com.example.moveup

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp
import kotlin.collections.ArrayList

class UserData {

    private var progressTime = 0F
    private var progressTimeMax = 60F
    private var arrayDynamicPhase = arrayListOf<Any?>(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)
    private var arrayBentBack = arrayListOf<Any?>(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)
    private var arrayLeanBack = arrayListOf<Any?>(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)
    private var arrayUpright = arrayListOf<Any?>(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)


    // serverTimestamp soll automatisch vom Server gesetzt werden
    @ServerTimestamp
    private var serverTimestamp: Timestamp? = null


    fun getServerTimestamp(): Timestamp? {
        return serverTimestamp
    }

    fun setServerTimestamp(serverTimestamp: Timestamp?) {
        this.serverTimestamp = serverTimestamp
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

    fun getArrayUpright(): ArrayList<Any?> {
        return arrayUpright
    }

    fun setArrayUpright(arrayUpright: ArrayList<Any?>) {
        this.arrayUpright= arrayUpright
    }


    override fun toString(): String {
        return "UserData{" +
                ", dynamicPhase='" + arrayDynamicPhase +
                ", progressTime='" + progressTime +
                ", progressTimeMax='" + progressTimeMax +
                ", arrayBentBack='" + arrayBentBack +
                ", arrayLeanBack='" + arrayLeanBack +
                ", arrayUpright='" + arrayUpright +
                '}'
    }
}