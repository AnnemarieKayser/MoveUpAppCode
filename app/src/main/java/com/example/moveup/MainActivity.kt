package com.example.moveup

import android.Manifest
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

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val viewModel: BasicViewModel by viewModels()

    //Ble
    private lateinit var mBluetooth: BluetoothAdapter

    private val DEVICEADDRESS = "address"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        readSharedPreferences()
        checkBTPermission()

        if (!packageManager.hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE))
        {
            toast(getString(R.string.ble_not_supported))
            finish()
        }

        mBluetooth = BluetoothAdapter.getDefaultAdapter()
        if(mBluetooth == null)
        {
            toast(getString(R.string.bt_not_available))
            finish();
        }


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_content_main)

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(navController.graph)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_graph, R.id.navigation_setting
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.mainActionBluetooth -> {
                findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.action_navigation_home_to_navigation_bluetooth)
                val navView: BottomNavigationView = binding.navView
                navView.visibility = View.GONE
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        val navView: BottomNavigationView = binding.navView
        navView.visibility = View.VISIBLE
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onResume() {
        super.onResume()
        if (!mBluetooth.isEnabled) {
            val turnBTOn = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(turnBTOn, 1)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause")
        writeSharedPreferences()
    }

    private fun checkBTPermission() {
        var permissionCheck = checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION")
        permissionCheck += checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION")
        if (permissionCheck != 0) {
            requestPermissions(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION), 1001)
        }
    }

    private fun writeSharedPreferences() {
        Log.i(TAG, "writeSharedPreferences")
        // speicher die Terminliste
        val sp = getPreferences(Context.MODE_PRIVATE)
        val edit = sp.edit()
        val address = viewModel.getDeviceAddress()
        edit.putString(DEVICEADDRESS, address)
        edit.commit()
    }

    private fun readSharedPreferences() {
        Log.i(TAG, "readSharedPreferences")
        // Termine wieder einlesen
        val sp = getPreferences(Context.MODE_PRIVATE)
        viewModel.setDeviceAddress(sp.getString(DEVICEADDRESS, "").toString())
    }

   /* override fun onBackPressed() {
        // Do Here what ever you want do on back press;
    }*/
}

