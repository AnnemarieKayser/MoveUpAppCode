package com.example.moveup

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.BluetoothLeScanner
import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.moveup.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import org.json.JSONException
import org.json.JSONObject
import splitties.toast.toast
import java.text.SimpleDateFormat
import java.util.*

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val viewModel: BasicViewModel by activityViewModels()
    private val binding get() = _binding!!

    //Ble
    private lateinit var scanner: BluetoothLeScanner
    private lateinit var mBluetooth: BluetoothAdapter
    private var isConnected = false
    private var bluetoothLeService: BluetoothLeService? = null
    private var gattCharacteristic: BluetoothGattCharacteristic? = null
    private var sensorStarted = false
    private var hour = 0
    private var minute = 0

    private val mHandler: Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable

    //Circular-Progress-Bar
    private var timeMaxProgressBar = 0F
    private var progressTime: Float = 0F

    //Datenbank
    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var data: UserData? = null
    private var dataSetting: UserDataSetting? = null
    private var dataConfig: UserDataConfig? = null
    private var statusVibration = "VIBON"
    private var vibrationLength = 1000
    private var threshold = -50


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root


    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // With blank your fragment BackPressed will be disabled.
        }

        mBluetooth = BluetoothAdapter.getDefaultAdapter()

        scanner = mBluetooth.bluetoothLeScanner

        // BluetoothLe Service starten
        val gattServiceIntent = Intent(context, BluetoothLeService::class.java)
        // Service anbinden
        context?.bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)


        loadDbData()
        loadDbDataConfig()

        //Sensor nach 1s verbinden, wenn deviceAddress bekannt ist
        mRunnable = Runnable {

            if (viewModel.getDeviceAddress() != "") {
                bluetoothLeService!!.connect(viewModel.getDeviceAddress());
            }
        }
        mHandler.postDelayed(mRunnable, 1000)

        binding.buttonNavigate.setOnClickListener {
            if (isConnected) {
                findNavController().navigate(R.id.action_navigation_home_to_navigation_graph)
            } else {
                findNavController().navigate(R.id.action_navigation_home_to_navigation_bluetooth)
            }
        }

        binding.buttonConfigStartChallengeHome.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_navigation_exercise)
        }

        binding.buttonLogOutHome.setOnClickListener {
            mFirebaseAuth.signOut()
            val intent = Intent(getActivity(), LoginInActivity::class.java)
            getActivity()?.startActivity(intent)
        }

        binding.buttonStartSensor.setOnClickListener {

            if (isConnected) {
                val obj = JSONObject()
                sensorStarted = !sensorStarted

                val kalender: Calendar = Calendar.getInstance()
                var zeitformat = SimpleDateFormat("HH")
                var time = zeitformat.format(kalender.time)
                hour = time.toInt()

                zeitformat = SimpleDateFormat("mm")
                time = zeitformat.format(kalender.time)
                minute = time.toInt()

                // Werte setzen
                if (sensorStarted) {
                    obj.put("STARTMESSUNG", "AN")
                    obj.put("HOUR", hour)
                    obj.put("MINUTE", minute)
                    obj.put("VIBRATION", statusVibration)
                    obj.put("VIBLENGTH", vibrationLength)
                    obj.put("THRESHOLDBENTBACK", threshold)
                    binding.buttonStartSensor.text = getString(R.string.btn_stop_sensor)
                } else {
                    obj.put("STARTMESSUNG", "AUS")
                    binding.buttonStartSensor.text = getString(R.string.btn_start_sensor)
                }

                // Senden
                if (gattCharacteristic != null) {
                    gattCharacteristic!!.value = obj.toString().toByteArray()
                    bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
                }
            } else {
                toast("verbinde zunächst den Sensor")
            }
        }

        binding.circularProgressBar.apply {
            // Set Progress Max
            progressMax = timeMaxProgressBar

            // Set ProgressBar Color
            progressBarColorStart = Color.CYAN
            //progressBarColorEnd = Color.GREEN
            progressBarColorDirection = CircularProgressBar.GradientDirection.RIGHT_TO_LEFT

            // Set background ProgressBar Color
            backgroundProgressBarColor = Color.GRAY
            backgroundProgressBarColorDirection =
                CircularProgressBar.GradientDirection.TOP_TO_BOTTOM

            // Set Width
            progressBarWidth = 7f // in DP
            backgroundProgressBarWidth = 9f // in DP

            // Other
            roundBorder = true

            progressDirection = CircularProgressBar.ProgressDirection.TO_RIGHT
        }

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
        //binding.textViewConnected.setText(R.string.connected)
        Log.i(ContentValues.TAG, "connected")
        toast("connected")
        binding.textViewConnectSensor.text = getString(R.string.sensor_connected)
        binding.buttonNavigate.text = getString(R.string.btn_sensor_connected)
    }

    private fun onDisconnect() {
        isConnected = false
        //binding.textViewConnected.setText(R.string.disconnected)
        Log.i(ContentValues.TAG, "disconnected")
        toast("disconnected")
        binding.textViewConnectSensor.text = getString(R.string.tv_connect_sensor_main)
        binding.buttonNavigate.text = getString(R.string.btn_connect)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
    private fun loadDbDataConfig() {
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection("Einstellungen")
            .document("Konfiguration")// alle Einträge abrufen
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    dataConfig = task.result!!.toObject(UserDataConfig::class.java)

                    if (data != null) {
                        threshold = dataConfig!!.getThresholdBentBack()
                        toast(threshold.toString())
                    }

                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }
    }

    private fun loadDbData() {

        val kalender: Calendar = Calendar.getInstance()
        val zeitformat = SimpleDateFormat("yyyy-MM-dd")
        val date = zeitformat.format(kalender.time)

        // Einstiegspunkt für die Abfrage ist users/uid/Messungen
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection(date)
            .document("Daten") // alle Einträge abrufen
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    data = task.result!!.toObject(UserData::class.java)
                    // Frage anzeigen
                    if (data != null) {
                        progressTime = data!!.getProgressTime()
                        timeMaxProgressBar = data!!.getProgressTimeMax()

                        binding.circularProgressBar.apply {
                            progressMax = timeMaxProgressBar
                            binding.textViewHomeProgressTime.text =
                                getString(R.string.tv_time, progressTime, timeMaxProgressBar)
                        }
                        binding.circularProgressBar.progress = progressTime
                    }

                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }

        db.collection("users").document(uid).collection("Einstellungen")
            .document("Vibration")// alle Einträge abrufen
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    dataSetting = task.result!!.toObject(UserDataSetting::class.java)

                    if (data != null) {
                        vibrationLength = dataSetting!!.getVibrationLength()
                        statusVibration = dataSetting!!.getVibration()
                    }

                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }
    }
}
