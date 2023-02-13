package com.example.moveup

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter


class MyAdapter(private val myContext: Context, fm: FragmentManager, internal var totalTabs: Int) : FragmentPagerAdapter(fm) {

    // === getItem === //
    // Anzeige des ausgewählten Tabs
    override fun getItem(position: Int): Fragment {
        when (position) {
            0 -> {
                return LoginTabFragment()
            }
            1 -> {
                return SigninTabFragment()
            }

            else -> return LoginTabFragment()
        }
    }

    // === getCount === //
    // Dies zählt die Gesamtzahl der Tabs
    override fun getCount(): Int {
        return totalTabs
    }
}