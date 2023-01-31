package com.example.moveup

import android.app.PendingIntent.getActivity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth

class LoginInActivity : AppCompatActivity() {

    private var viewPager: ViewPager? = null
    private var tabLayout: TabLayout? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_in)

        tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        viewPager = findViewById<ViewPager>(R.id.viewPager)

        tabLayout!!.addTab(tabLayout!!.newTab().setText(R.string.tv_login))
        tabLayout!!.addTab(tabLayout!!.newTab().setText(R.string.tv_signin))
        tabLayout!!.tabGravity = TabLayout.GRAVITY_FILL

        val adapter = MyAdapter(this, supportFragmentManager, tabLayout!!.tabCount)
        viewPager!!.adapter = adapter

        viewPager!!.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))

        tabLayout!!.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewPager!!.currentItem = tab.position
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {

            }
            override fun onTabReselected(tab: TabLayout.Tab) {

            }
        })
    }
}