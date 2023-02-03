package com.example.moveup

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

class UserDataExercise {


    private var challenge = 0

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


    override fun toString(): String {
        return "UserDataChallenge{" +
                ", challenge='" + challenge +
                '}'
    }
}
