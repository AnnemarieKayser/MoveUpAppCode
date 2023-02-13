package com.example.moveup


import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout

class LoginInActivity : AppCompatActivity() {


/*
 ======================================================================================
 ==========================          Einleitung              ==========================
 ======================================================================================
 Projektname: moveUP
 Autor: Annemarie Kayser
 Anwendung: Tragbares sensorbasiertes Messsystem zur Kontrolle des Sitzverhaltens;
            Ausgabe eines Hinweises, wenn eine krumme Haltung eingenommen wurde, in Form von Vibration
            am Rücken. Messung des dynamischen und statischen Sitzverhaltens mithilfe von Gyroskopwerten.
 Bauteile: Verwendung des 6-Achsen-Beschleunigungssensors MPU 6050 in Verbindung mit dem Esp32 Thing;
           Verbindung zwischen dem Esp32 Thing und einem Smartphone erfolgt via Bluetooth Low Energy.
           Ein Vibrationsmotor am Rücken gibt den Hinweis auf eine krumme Haltung.
           Die Sensorik wurde in einem kleinen Gehäuse befestigt, welches mit einem Clip am Oberteil befestigt werden kann.
 Letztes Update: 07.02.2023

======================================================================================
*/

/*
  =============================================================
  =======              Function Activity                =======
  =============================================================

  Diese Activity verwaltet das LoginTabFragment und das SignInTabFragment.
  Über ein tabLayout kann zwischen diesen beiden Fragments gewechselt werden.

*/

/*
  =============================================================
  =======                   Variables                   =======
  =============================================================
*/

    private var viewPager: ViewPager? = null
    private var tabLayout: TabLayout? = null

/*
  =============================================================
  =======                                               =======
  =======                   onCreate                    =======
  =======                                               =======
  =============================================================
*/


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_in)

        // --- Initialisierung der Layout-Komponenten --- //
        tabLayout = findViewById(R.id.tab_layout)
        viewPager = findViewById(R.id.viewPager)

        // --- Überschrift der Tabs --- //
        tabLayout!!.addTab(tabLayout!!.newTab().setText(R.string.tv_login))
        tabLayout!!.addTab(tabLayout!!.newTab().setText(R.string.tv_signin))
        tabLayout!!.tabGravity = TabLayout.GRAVITY_FILL

        // --- Anbindung des Adapters --- //
        val adapter = MyAdapter(this, supportFragmentManager, tabLayout!!.tabCount)
        viewPager!!.adapter = adapter

        viewPager!!.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))

        // --- Anzeige des ausgewählten Tabs --- //
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