package com.example.moveup

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import splitties.alertdialog.alertDialog
import splitties.alertdialog.okButton


class SplashScreen : AppCompatActivity() {

    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val mHandler: Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)


        // --- Check if there is a connection to wifi --- //
        // if there is no connection, an alertDialog is displayed and reminds
        // the user to connect his phone to Wifi
        // afterwards the app is closed
        if(!isNetworkAvailable(this)){
            // --- alertDialog --- //
            // reminder to turn on wifi
            alertDialog (
                title = getString(R.string.alert_dialog_title_reminder),
                message = getString(R.string.alert_dialog_message_wifi)) {
                okButton{
                    finish()
                }
            }.show()
        }
        else {
            mRunnable = Runnable {
                if (mFirebaseAuth.currentUser == null) {
                    val intent = Intent (this, LoginInActivity::class.java)
                    startActivity(intent)
                }
                else {
                    val intent = Intent (this, MainActivity::class.java)
                    startActivity(intent)
                }
            }
            mHandler.postDelayed(mRunnable, 2000)
        }
    }

    // === isNetworkAvailable === //
    // this function checks if there is a connection to wifi
    // it returns true, if a connection is available
    // otherwise it returns false
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (connectivityManager != null) {
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_CELLULAR")
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_WIFI")
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_ETHERNET")
                    return true
                }
            }
        }
        return false
    }

}