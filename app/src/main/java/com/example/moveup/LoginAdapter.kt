package com.example.moveup

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter


class MyAdapter(private val myContext: Context, fm: FragmentManager, internal var totalTabs: Int) : FragmentPagerAdapter(fm) {

    // this is for fragment tabs
    override fun getItem(position: Int): Fragment {
        when (position) {
            0 -> {
                //val loginFragment: LoginTabFragment = LoginTabFragment()
                //  val homeFragment: HomeFragment = HomeFragment()
                return LoginTabFragment()
            }
            1 -> {
                //val signinFragment: SigninTabFragment = SigninTabFragment()
                return SigninTabFragment()
            }

            else -> return LoginTabFragment()
        }
    }

    // this counts total number of tabs
    override fun getCount(): Int {
        return totalTabs
    }
}