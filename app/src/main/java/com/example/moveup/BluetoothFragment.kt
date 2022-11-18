package com.example.moveup
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
import org.json.JSONObject
import splitties.toast.toast


class BluetoothFragment : Fragment() {

    private var _binding: FragmentBluetoothBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BasicViewModel by activityViewModels()
    private lateinit var adapter: ArrayAdapter<String>

    //Ble
    private lateinit var scanner: BluetoothLeScanner
    private lateinit var mBluetooth: BluetoothAdapter
    private var isScanning = false
    private var deviceIsSelected = false
    private var isConnected = false
    private var isOnLED = false
    private var discoveredDevices = arrayListOf<String>()
    private lateinit var selectedDevice: String
    private var bluetoothLeService: BluetoothLeService? = null
    private var gattCharacteristic: BluetoothGattCharacteristic? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBluetoothBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonConnect.isEnabled = false
        binding.buttonLed.isEnabled = false


        mBluetooth = BluetoothAdapter.getDefaultAdapter()

        scanner = mBluetooth.bluetoothLeScanner

        // BluetoothLe Service starten
        val gattServiceIntent = Intent(context, BluetoothLeService::class.java)
        // Service anbinden
        context?.bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE)

        binding.buttonScan.setOnClickListener {

            if (!isScanning) { // Suche ist nicht gestartet
                scanner.startScan(scanCallback)
                Log.i(TAG, "Starte Scan")
                isScanning = true
                binding.buttonScan.text = getString(R.string.btn_scan_stop)
            } else {                        // Suche ist gestartet
                scanner.stopScan(scanCallback)
                Log.i(TAG, "Stoppe Scan")
                isScanning = false
                binding.buttonScan.text = getString(R.string.btn_scan)
            }
        }

        binding.buttonConnect.setOnClickListener {
            // Button Logik und connect bzw disconnect
            if (isConnected) {
                bluetoothLeService!!.disconnect()
                isConnected = false
                binding.textViewStatus.text = getString(R.string.bt_connect_off)
            } else {
                bluetoothLeService!!.connect(viewModel.getDeviceAddress())
            }
        }

        binding.buttonLed.setOnClickListener {
            val obj = JSONObject()
            isOnLED = !isOnLED
            // Werte setzen
            if (isOnLED) {
                binding.buttonLed.text = getString(R.string.bt_led_off)
                binding.textViewLed.text = getString(R.string.led_on)
                obj.put("LED", "H")
            } else {
                binding.buttonLed.text = getString(R.string.bt_led_on)
                binding.textViewLed.text = getString(R.string.led_off)
                obj.put("LED", "L")
            }

            // Senden
            if (gattCharacteristic != null) {
                gattCharacteristic!!.value = obj.toString().toByteArray()
                bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
            }
        }

        binding.listView.onItemClickListener = lvClickListener

    }

    private val lvClickListener =
        AdapterView.OnItemClickListener { parent, view, position, id ->
            // Gerät aus dem Listview auswählen
            if (isScanning) {
                scanner.stopScan(scanCallback)
                isScanning = false
                binding.buttonScan.text = getString(R.string.btn_scan)
            }
            selectedDevice = (view as TextView).text.toString()
            viewModel.setDeviceAddress(selectedDevice.substring(selectedDevice.length - 17))
            binding.textViewSelectedDevice.text = selectedDevice
            deviceIsSelected = true
            binding.buttonConnect.isEnabled = true
        }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Wenn Devicename nicht ESP32 enthält, mache nichts
            if (result.device.name == null) return
            if (!result.device.name.contains("ESP32")) return

            val deviceInfo = """${result.device.name} ${result.device.address}""".trimIndent()
            Log.i(TAG, "DeviceFound: $deviceInfo")

            // gefundenes Gerät der Liste hinzufügen, wenn es noch nicht aufgeführt ist
            if (!discoveredDevices.contains(deviceInfo)) {
                viewModel.addDevice(deviceInfo)
            }

            // Adapter für den ListView
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,   // Layout zur Darstellung der ListItems
                viewModel.getDeviceList()!!
            )           // Liste, die Dargestellt werden soll

            // Adapter an den ListView koppeln
            binding.listView.adapter = adapter

            // Mittels Observer den Adapter über Änderungen in der Liste informieren
            viewModel.discoveredDevices.observe(viewLifecycleOwner) { adapter.notifyDataSetChanged() }

        }
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_ESP32_CHARACTERISTIC_DISCOVERED)
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
        return intentFilter
    }

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            when (action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> onConnect()
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> onDisconnect()
                BluetoothLeService.ACTION_GATT_ESP32_CHARACTERISTIC_DISCOVERED
                -> onGattCharacteristicDiscovered()
                BluetoothLeService.ACTION_DATA_AVAILABLE -> onDataAvailable()
            }
        }
    }

    private fun onConnect() {
        isConnected = true
        binding.textViewStatus.setText(R.string.connected)
        binding.buttonLed.isEnabled = true
        Log.i(TAG, "connected")
    }

    private fun onDisconnect() {
        isConnected = false
        binding.textViewStatus.setText(R.string.disconnected)
        binding.buttonLed.isEnabled = false
        Log.i(TAG, "disconnected")
    }

    private fun onGattCharacteristicDiscovered() {
        gattCharacteristic = bluetoothLeService?.getGattCharacteristic()
    }

    private fun onDataAvailable() {
        // neue Daten verfügbar
        Log.i(TAG, "Data available")
        val bytes: ByteArray = gattCharacteristic!!.value
        // byte[] to string
        val s = String(bytes)
        //parseJSONData(s)
    }


    override fun onResume() {
        super.onResume()
        if (!mBluetooth.isEnabled) {
            val turnBTOn = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(turnBTOn, 1)
        }
        context?.registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
        if (bluetoothLeService != null && isConnected) {
            var result = bluetoothLeService!!.connect(viewModel.getDeviceAddress());
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.stopScan(scanCallback)
        bluetoothLeService!!.disconnect()
        bluetoothLeService!!.close()
        context?.unbindService(serviceConnection)
        bluetoothLeService = null
    }

    override fun onPause() {
        super.onPause()
        context?.unregisterReceiver(gattUpdateReceiver)
    }

}