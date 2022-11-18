package com.example.moveup
import android.R
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
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

    private var _binding: FragmentBluetoothBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BasicViewModel by activityViewModels()
    private lateinit var adapter: ArrayAdapter<String>

    //Ble
    private lateinit var scanner: BluetoothLeScanner
    private lateinit var mBluetooth: BluetoothAdapter
    private var isScanning = false
    private var deviceIsSelected = false
    private var discoveredDevices = arrayListOf<String>()
    private lateinit var selectedDevice: String


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

        binding.buttonScan.setOnClickListener {

            if (!isScanning) { // Suche ist nicht gestartet
                scanner.startScan(scanCallback)
                Log.i(TAG, "Starte Scan")
                isScanning = true
                binding.buttonScan.text = "Scan stopp"
            } else {                        // Suche ist gestartet
                scanner.stopScan(scanCallback)
                Log.i(TAG, "Stoppe Scan")
                isScanning = false
                binding.buttonScan.text = "Scan start"
            }
        }

        binding.buttonConnect.setOnClickListener {

        }

        binding.buttonLed.setOnClickListener {

        }

        binding.listView.onItemClickListener = lvClickListener

    }

    private val lvClickListener =
        AdapterView.OnItemClickListener { parent, view, position, id ->
            // Gerät aus dem Listview auswählen
            if (isScanning) {
                scanner.stopScan(scanCallback)
                isScanning = false
                binding.buttonScan.text = "Scannen"
            }
            selectedDevice = (view as TextView).text.toString()
            viewModel.setDeviceAddress(selectedDevice.substring(selectedDevice.length - 17))
            binding.textViewSelectedDevice.text = selectedDevice
            deviceIsSelected = true
            binding.buttonConnect.isEnabled = true
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
                R.layout.simple_list_item_1,   // Layout zur Darstellung der ListItems
                viewModel.getDeviceList()!!
            )           // Liste, die Dargestellt werden soll

            // Adapter an den ListView koppeln
            binding.listView.adapter = adapter

            // Mittels Observer den Adapter über Änderungen in der Liste informieren
            viewModel.discoveredDevices.observe(viewLifecycleOwner) { adapter.notifyDataSetChanged() }

        }
    }

    override fun onResume() {
        super.onResume()
        if (!mBluetooth.isEnabled) {
            val turnBTOn = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(turnBTOn, 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.stopScan(scanCallback)
    }
}