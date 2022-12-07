package com.example.moveup

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp
import java.util.*

class UserData {

    private var leanBack = 0
    private var hour = 0
    private var bentBack = 0
    private var straightBack = 0
    private var dynamicPhase = 0
    private var date: String? = null
    private var dateTimestamp: Date? = null

    // serverTimestamp soll automatisch vom Server gesetzt werden
    @ServerTimestamp
    private var serverTimestamp: Timestamp? = null

    fun getDateTimestamp(): Date? {
        return dateTimestamp
    }

    fun setDateTimestamp(dateTimestamp: Date?) {
        this.dateTimestamp = dateTimestamp
    }

    fun getServerTimestamp(): Timestamp? {
        return serverTimestamp
    }

    fun setServerTimestamp(serverTimestamp: Timestamp?) {
        this.serverTimestamp = serverTimestamp
    }

    fun getCounterLeanBack(): Int {
        return leanBack
    }

    fun setCounterLeanBack(leanBack: Int) {
        this.leanBack = leanBack
    }

    fun getHour(): Int {
        return hour
    }

    fun setHour(hour: Int) {
        this.hour = hour
    }

    fun getCounterStraightBack(): Int {
        return straightBack
    }

    fun setCounterStraightBack(straightBack: Int) {
        this.straightBack = straightBack
    }

    fun getCounterBentBack(): Int {
        return bentBack
    }

    fun setCounterBentBack(bentBack: Int) {
        this.bentBack = bentBack
    }

    fun getDynamicPhase(): Int {
        return dynamicPhase
    }

    fun setDynamicPhase(dynamicPhase: Int) {
        this.dynamicPhase = dynamicPhase
    }

    fun getDate(): String? {
        return date
    }

    fun setDate(date: String?) {
        this.date = date
    }


    override fun toString(): String {
        return "UserData{" +
                ", leanBack=" + leanBack +
                ", straightBack='" + straightBack + '\'' +
                ", bentback=" + bentBack +
                ", dynamicPhase=" + dynamicPhase +
                ", hour='" + hour + '\'' +
                ", dateTimestamp=" + dateTimestamp +
                '}'
    }
}