package com.example.moveup

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.moveup.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import splitties.toast.toast

class MainActivity : AppCompatActivity() {


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

  Diese Activity verwaltet das HomeFragment, GraphFragment, ExerciseFragment
  und SettingFragment. Über eine bottomNavigationBar kann zwischen den Fragments
  gewechselt werden.

*/

/*
  =============================================================
  =======                   Variables                   =======
  =============================================================
*/

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val viewModel: BasicViewModel by viewModels()

    // === Bluetooth Low Energy === //
    private lateinit var mBluetooth: BluetoothAdapter
    private val DEVICEADDRESS = "address"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- sharedPreferences einlesen --- //
        readSharedPreferences()

        // --- Bluetooth-Zugriff überprüfen --- //
        checkBTPermission()

        // --- Überprüfen, ob BLE unterstützt wird --- //
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            toast(getString(R.string.ble_not_supported))
            finish()
        }

        // --- Überprüfen, ob Bluetooth verfügbar ist --- //
        mBluetooth = BluetoothAdapter.getDefaultAdapter()
        if(mBluetooth == null)
        {
            toast(getString(R.string.bt_not_available))
            finish();
        }

        // --- Layout --- //
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Initialisierung Toolbar --- //
        setSupportActionBar(binding.toolbar)

        // --- Initialisierung bottomNavigationView --- //
        val navView: BottomNavigationView = binding.navView

        // --- Initialisierung navController --- //
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        // Übergabe jeder Menü-ID als eine Gruppe von Ids, da jedes
        // Menü als Ziel der obersten Ebene betrachtet werden sollte.
        appBarConfiguration = AppBarConfiguration(navController.graph)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_graph, R.id.navigation_exercise, R.id.navigation_setting
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

    }

/*
  =============================================================
  =======                                               =======
  =======                   Funktionen                  =======
  =======                                               =======
  =============================================================
*/

    // === onCreateOptionsMenu === //
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    // === onOptionsItemSelected === //
    // Hier werden Klicks auf Elemente der Aktionsleiste behandelt
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.mainActionBluetooth -> {
                // Wechsel zum BluetoothFragment
                findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.navigation_bluetooth)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // === onSupportNavigateUp === //
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        val navView: BottomNavigationView = binding.navView
        navView.visibility = View.VISIBLE
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    // === onResume === //
    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        if (!mBluetooth.isEnabled) {
            val turnBTOn = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(turnBTOn, 1)
        }
    }

    // === onPause === //
    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause")
        writeSharedPreferences()
    }

    // === checkBTPermission === //
    // Überprüfen, ob Zugriff auf Bluetooth erlaubt ist
    private fun checkBTPermission() {
        var permissionCheck = checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION")
        permissionCheck += checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION")
        if (permissionCheck != 0) {
            requestPermissions(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION), 1001)
        }
    }

    // === writeSharedPreferences === //
    // Speichern der Daten in den sharedPreferences
    private fun writeSharedPreferences() {
        Log.i(TAG, "writeSharedPreferences")
        val sp = getPreferences(Context.MODE_PRIVATE)
        val edit = sp.edit()

        val address = viewModel.getDeviceAddress()
        val day = viewModel.getDate()
        val statusMeas = viewModel.getStatusMeasurment()

        edit.putString(DEVICEADDRESS, address)
        edit.putString("DAY", day)
        edit.putBoolean("STATUSMEAS", statusMeas)
        edit.commit()
    }

    // === readSharedPreferences === //
    // Einlesen der Daten aus den sharedPreferences
    private fun readSharedPreferences() {
        Log.i(TAG, "readSharedPreferences")
        val sp = getPreferences(Context.MODE_PRIVATE)

        viewModel.setDeviceAddress(sp.getString(DEVICEADDRESS, "").toString())
        viewModel.setDate(sp.getString("DAY", "").toString())
        viewModel.setStatusMeasurment(sp.getBoolean("STATUSMEAS", false))
    }

}

