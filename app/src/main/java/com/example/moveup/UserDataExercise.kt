package com.example.moveup

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

class UserDataExercise {

    // Variablen zum Speichern der Daten zu Challenges und Bewegungspausen in der Datenbank
    private var challenge = 0
    private var movementBreak = 0
    private var arrayChallenge = arrayListOf<Any?>(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)
    private var arrayMovementBreak= arrayListOf<Any?>(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)

    // serverTimestamp soll automatisch vom Server gesetzt werden
    @ServerTimestamp
    private var serverTimestamp: Timestamp? = null


    fun getServerTimestamp(): Timestamp? {
        return serverTimestamp
    }

    fun setServerTimestamp(serverTimestamp: Timestamp?) {
        this.serverTimestamp = serverTimestamp
    }

    fun getChallenge(): Int {
        return challenge
    }

    fun setChallenge(challenge: Int) {
        this.challenge = challenge
    }

    fun getMovementBreak(): Int {
        return movementBreak
    }

    fun setMovementBreak(movementBreak: Int) {
        this.movementBreak = movementBreak
    }

    fun getChallengeArray(): ArrayList<Any?> {
        return arrayChallenge
    }

    fun setChallengeArray(arrayChallenge: ArrayList<Any?>) {
        this.arrayChallenge = arrayChallenge
    }

    fun getMovementBreakArray(): ArrayList<Any?> {
        return arrayMovementBreak
    }

    fun setMovementBreakArray(arrayMovementBreak: ArrayList<Any?>) {
        this.arrayMovementBreak = arrayMovementBreak
    }


    override fun toString(): String {
        return "UserDataChallenge{" +
                ", challenge='" + challenge +
                ", movementBreak='" + movementBreak +
                ", arrayChallenge='" + arrayChallenge +
                ", arrayMovementBreak='" + arrayMovementBreak +
                '}'
    }
}
