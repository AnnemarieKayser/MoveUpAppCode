package com.example.moveup

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import com.google.firebase.auth.FirebaseAuth


class SplashScreen : AppCompatActivity() {

    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val mHandler: Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

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