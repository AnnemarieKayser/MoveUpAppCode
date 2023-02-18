package com.example.moveup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.*
import android.content.ContentValues.TAG
import android.content.Context.BIND_AUTO_CREATE
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.moveup.databinding.FragmentBluetoothBinding


class BluetoothFragment : Fragment() {

/*
   ======================================================================================
   ==========================           Einleitung             ==========================
   ======================================================================================
   Projektname: moveUP
   Autor: Annemarie Kayser
   Anwendung: Tragbares sensorbasiertes Messsystem zur Kontrolle des Sitzverhaltens;
              Ausgabe eines Hinweises, wenn eine krumme Haltung eingenommen oder sich lange Zeit nicht
              bewegt wurde, in Form von Vibration am Rücken. Messung des dynamischen und statischen
              Sitzverhaltens mithilfe von Gyroskopwerten.
   Bauteile: Verwendung des 6-Achsen-Beschleunigungssensors MPU 6050 in Verbindung mit dem Esp32 Thing;
             Datenübertragung zwischen dem Esp32 Thing und der App erfolgt via Bluetooth Low Energy.
             Ein Vibrationsmotor am Rücken gibt den Hinweis auf eine krumme Haltung oder sich zubewegen.
             Die Sensorik wurde in einem kleinen Gehäuse befestigt, welches mit einem Clip am Oberteil befestigt werden kann.
   Letztes Update: 18.02.2023

  ======================================================================================
*/

/*
  =============================================================
  =======                  Funktion                     =======
  =============================================================

  In diesem Fragment kann die Bluetooth Low Energy Verbindung zum Mikrocontroller
  hergestellt werden
          - Aufbauen/Beenden der Verbindung
          - Speichern der Mac-Adresse im BasicViewModel

*/

/*
  =============================================================
  =======                   Variablen                   =======
  =============================================================
*/


    private var _binding: FragmentBluetoothBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BasicViewModel by activityViewModels()

    // === Bluetooth Low Energy === //
    private lateinit var scanner: BluetoothLeScanner
    private lateinit var mBluetooth: BluetoothAdapter
    private var isScanning = false
    private var deviceIsSelected = false
    private var isConnected = false
    private var discoveredDevices = arrayListOf<String>()
    private lateinit var selectedDevice: String
    private var bluetoothLeService: BluetoothLeService? = null
    private var gattCharacteristic: BluetoothGattCharacteristic? = null
    private lateinit var adapter: ArrayAdapter<String>


/*
  =============================================================
  =======                                               =======
  =======         onCreateView & onViewCreated          =======
  =======                                               =======
  =============================================================
*/

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBluetoothBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- Deaktivierung Button für Verbindungsaufbau --- //
        binding.buttonConnect.isEnabled = false

        // --- Initialisierung Bluetooth-Adapter und Scanner --- //
        mBluetooth = BluetoothAdapter.getDefaultAdapter()
        scanner = mBluetooth.bluetoothLeScanner

        // --- BluetoothLe Service starten --- //
        val gattServiceIntent = Intent(context, BluetoothLeService::class.java)
        // --- Service anbinden --- //
        context?.bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE)

        // --- Start oder Stopp der Suche nach Geräten --- //
        binding.buttonScan.setOnClickListener {

            // Suche ist nicht gestartet
            if (!isScanning) {
                scanner.startScan(scanCallback)
                Log.i(TAG, "Starte Scan")
                isScanning = true
                binding.buttonScan.text = getString(R.string.btn_scan_stop)
            } else {
                // Suche ist gestartet
                scanner.stopScan(scanCallback)
                Log.i(TAG, "Stoppe Scan")
                isScanning = false
                binding.buttonScan.text = getString(R.string.btn_scan)
            }
        }

        // --- Herstellen oder Beenden der BLE-Verbindung zum ESP32 thing --- //
        binding.buttonConnect.setOnClickListener {

            if (isConnected) {
                // Verbindung wird beendet
                bluetoothLeService!!.disconnect()
                isConnected = false
                binding.textViewStatus.text = getString(R.string.bt_connect_off)
            } else {
                // Verbindung wird hergestellt
                bluetoothLeService!!.connect(viewModel.getDeviceAddress())
            }
        }

        // --- die "lvClickListener"-Funktion wird aufgerufen, wenn ein ListView-Item angeklickt wird --- //
        binding.listView.onItemClickListener = lvClickListener
    }

/*
  =============================================================
  =======                                               =======
  =======                   Funktionen                  =======
  =======                                               =======
  =============================================================
*/

    // === lvClickListener === //
    // Wenn ein Item aus der Liste ausgewählt wird, wird die Suche nach
    // Geräten beendet und der Button zum Verbindungsaufbau wird aktiviert
    @SuppressLint("MissingPermission")
    private val lvClickListener =
        AdapterView.OnItemClickListener { parent, view, position, id ->
            // Gerät aus dem Listview auswählen
            // Scanning wird gestoppt
            if (isScanning) {
                scanner.stopScan(scanCallback)
                isScanning = false
                binding.buttonScan.text = getString(R.string.btn_scan)
            }
            selectedDevice = (view as TextView).text.toString()
            // Speichern der Mac-Adresse im viewModel
            viewModel.setDeviceAddress(selectedDevice.substring(selectedDevice.length - 17))
            binding.textViewSelectedDevice.text = selectedDevice
            deviceIsSelected = true
            // Button wird aktiviert
            binding.buttonConnect.isEnabled = true
        }

    // === serviceConnection === //
    // BluetoothLE Service Anbindung
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            // Variable zum Zugriff auf die Service-Methoden
            bluetoothLeService = (service as BluetoothLeService.LocalBinder).getService()
            if (!bluetoothLeService!!.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth")
            }
        }
        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothLeService = null
        }
    }

    // === scanCallback === //
    // Die Scan-Ergebnisse werden via einer Callback-Funktion empfangen
    // gefundene Geräte werden dem ListView hinzugefügt
    // Die Liste wird aktualisiert
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Wenn Devicename nicht ESP32 enthält, mache nichts
            if (result.device.name == null) return
            if (!result.device.name.contains("ESP32")) return

            val deviceInfo = """${result.device.name} ${result.device.address}""".trimIndent()
            Log.i(TAG, "DeviceFound: $deviceInfo")

            // gefundenes Gerät der Liste im viewModel hinzufügen, wenn es noch nicht aufgeführt ist
            if (!discoveredDevices.contains(deviceInfo)) {
                viewModel.addDevice(deviceInfo)
            }

            // Adapter für den ListView
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1,
                viewModel.getDeviceList()!!)

            // Adapter an den ListView koppeln
            binding.listView.adapter = adapter

            // Mittels Observer den Adapter über Änderungen in der Liste informieren
            viewModel.discoveredDevices.observe(viewLifecycleOwner) { adapter.notifyDataSetChanged() }
        }
    }

    // === makeGattUpdateIntentFilter === //
    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_ESP32_CHARACTERISTIC_DISCOVERED)
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
        return intentFilter
    }

    // === gattUpdateReceiver === //
    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            when (action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> onConnect()
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> onDisconnect()
                BluetoothLeService.ACTION_GATT_ESP32_CHARACTERISTIC_DISCOVERED
                -> onGattCharacteristicDiscovered()
            }
        }
    }

    // === onConnect === //
    private fun onConnect() {
        isConnected = true
        binding.textViewStatus.setText(R.string.connected)
        Log.i(TAG, "connected")
    }

    // === onDisconnect === //
    private fun onDisconnect() {
        isConnected = false
        binding.textViewStatus.setText(R.string.disconnected)
        Log.i(TAG, "disconnected")
    }

    // === onGattCharacteristicDiscovered === //
    private fun onGattCharacteristicDiscovered() {
        gattCharacteristic = bluetoothLeService?.getGattCharacteristic()
    }

    // === onResume === //
    override fun onResume() {
        super.onResume()
        if (!mBluetooth.isEnabled) {
            val turnBTOn = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(turnBTOn, 1)
        }
        context?.registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
        if (bluetoothLeService != null && isConnected) {
            var result = bluetoothLeService!!.connect(viewModel.getDeviceAddress())
            Log.d(TAG, "Connect request result=" + result)
        }
    }

    // === onDestroy === //
    // Scan nach Geräten wird beendet
    // BLE-Verbindung zum ESP32 thing wird beendet
    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        scanner.stopScan(scanCallback)
        bluetoothLeService!!.disconnect()
        bluetoothLeService!!.close()
        context?.unbindService(serviceConnection)
        bluetoothLeService = null
    }

    // === onPause === //
    override fun onPause() {
        super.onPause()
        context?.unregisterReceiver(gattUpdateReceiver)
    }

    // === onDestroyView === //
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}