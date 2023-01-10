package com.example.moveup

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.BluetoothLeScanner
import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.example.moveup.databinding.FragmentConfigBinding
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONException
import org.json.JSONObject
import splitties.toast.toast
import java.util.concurrent.atomic.AtomicInteger


class ConfigFragment : Fragment() {

    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BasicViewModel by activityViewModels()

    //Ble
    private lateinit var scanner: BluetoothLeScanner
    private lateinit var mBluetooth: BluetoothAdapter
    private var isConnected = false
    private var isReceivingData = false
    private var bluetoothLeService: BluetoothLeService? = null
    private var gattCharacteristic: BluetoothGattCharacteristic? = null
    private var statusVibration = true

    private val mHandler: Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable

    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mBluetooth = BluetoothAdapter.getDefaultAdapter()

        scanner = mBluetooth.bluetoothLeScanner

        // BluetoothLe Service starten
        val gattServiceIntent = Intent(context, BluetoothLeService::class.java)
        // Service anbinden
        context?.bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)


        //Sensor nach 1s verbinden, wenn deviceAddress bekannt ist
        mRunnable = Runnable {

            if (viewModel.getDeviceAddress() != "") {
                bluetoothLeService!!.connect(viewModel.getDeviceAddress())
            }
        }
        mHandler.postDelayed(mRunnable, 1000)

        binding.buttonConnectSensor.setOnClickListener {
            bluetoothLeService!!.connect(viewModel.getDeviceAddress())
        }

        binding.buttonStart.setOnClickListener {

            val obj = JSONObject()
            // Werte setzen
            obj.put("STATUSKONFIG", true)

            // Senden
            if (gattCharacteristic != null) {
                gattCharacteristic!!.value = obj.toString().toByteArray()
                bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
            }

            //Start Countdown
            val handler = Handler()
            val n = AtomicInteger(10) // initialisiere mit 10.
            val counter: Runnable = object : Runnable {
                override fun run() {
                    //Textfeld mit aktuellem n füllen.
                    binding.textViewCountdown.text = Integer.toString(n.get())
                    //wenn n >= 1, sekündlich runterzählen
                    if (n.getAndDecrement() >= 1) {
                        handler.postDelayed(this, 1000)
                    } else {
                        //Textfeld verschwindet, wenn Countdown zu Ende
                        binding.textViewCountdown.visibility = View.INVISIBLE

                        val obj = JSONObject()
                        // Werte setzen
                        obj.put("STATUSKONFIG", false)

                        // Senden
                        if (gattCharacteristic != null) {
                            gattCharacteristic!!.value = obj.toString().toByteArray()
                            bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
                        }

                    }
                }
            }
            handler.postDelayed(counter, 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // BluetoothLE Service Anbindung
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            // Variable zum Zugriff auf die Service-Methoden
            bluetoothLeService = (service as BluetoothLeService.LocalBinder).getService()
            if (!bluetoothLeService!!.initialize()) {
                Log.e(ContentValues.TAG, "Unable to initialize Bluetooth")

            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothLeService = null
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
        Log.i(ContentValues.TAG, "connected")
        binding.textViewStatusBLE.text = getString(R.string.tv_status_ble, "verbunden")
        toast("connected")
        binding.buttonConnectSensor.visibility = View.INVISIBLE
    }

    private fun onDisconnect() {
        isConnected = false
        Log.i(ContentValues.TAG, "disconnected")
        binding.textViewStatusBLE.text = getString(R.string.tv_status_ble, "Nicht verbunden")
        toast("disconnected")
        binding.buttonConnectSensor.visibility = View.VISIBLE

    }

    private fun onGattCharacteristicDiscovered() {
        gattCharacteristic = bluetoothLeService?.getGattCharacteristic()
    }

    private fun onDataAvailable() {
        // neue Daten verfügbar
        Log.i(ContentValues.TAG, "Data available")
        val bytes: ByteArray = gattCharacteristic!!.value
        // byte[] to string
        val s = String(bytes)
        parseJSONData(s)
    }

    private fun parseJSONData(jsonString: String) {
        try {
            val obj = JSONObject(jsonString)
            //extrahieren des Objektes data

        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).supportActionBar!!.show()
        if (!mBluetooth.isEnabled) {
            val turnBTOn = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(turnBTOn, 1)
        }
        context?.registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
        if (bluetoothLeService != null && isConnected) {
            var result = bluetoothLeService!!.connect(viewModel.getDeviceAddress());
            Log.d(ContentValues.TAG, "Connect request result=" + result);
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothLeService!!.disconnect()
        bluetoothLeService!!.close()
        context?.unbindService(serviceConnection)
        bluetoothLeService = null
    }

    override fun onPause() {
        super.onPause()
        context?.unregisterReceiver(gattUpdateReceiver)
        if (isConnected) {
            bluetoothLeService!!.disconnect()
        }
    }
}
