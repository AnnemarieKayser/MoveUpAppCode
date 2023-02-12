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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var isReceivingData = false
    private var hour = 0
    private var minute = 0

    private val mHandler: Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable

    //Circular-Progress-Bar
    private var timeMaxProgressBar = 60F
    private var progressTime: Float = 0F

    //Datenbank
    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var data: UserData? = null
    private var dataExercise: UserDataExercise? = null
    private var dataSetting: UserDataSetting? = null
    private var dataConfig: UserDataConfig? = null
    private var statusVibration = "VIBON"
    private var vibrationLength = 1000
    private var thresholdBent = -30
    private var thresholdLean = 20
    private var configData = false
    private var arrayStraightList = arrayListOf<Any?>()
    private var arrayStraight = arrayOfNulls<Any>(48)
    private var arrayBent = arrayOfNulls<Any>(48)
    private var arrayLeanBack = arrayOfNulls<Any>(48)
    private var arrayDynamic = arrayOfNulls<Any>(48)
    private var arrayBentList = arrayListOf<Any?>()
    private var arrayLeanList = arrayListOf<Any?>()
    private var arrayDynamicList = arrayListOf<Any?>()
    private var arrayMovementBreakDb = arrayListOf<Any?>()
    private var arrayMovementBreak = arrayOfNulls<Any>(48)
    private var counterReminder = 0
    private var counterLeanBack = 0


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

        binding.buttonGetDataHome.setOnClickListener {
            if (isConnected) {
                bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, true)
                isReceivingData = true
                binding.buttonGetDataHome.text = getString(R.string.bt_data_off)

                mRunnable = Runnable {
                    bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, false)
                    isReceivingData = false
                    binding.buttonGetDataHome.text = getString(R.string.btn_data_graph)
                    insertDataInDb()

                    val kalender: Calendar = Calendar.getInstance()
                    var zeitformat = SimpleDateFormat("HH")
                    var time = zeitformat.format(kalender.time)
                    hour = time.toInt()

                    if (arrayDynamic[hour*2].toString().toInt() < 3 && arrayMovementBreak[hour*2].toString().toInt() == 0) {

                            if(arrayDynamic[hour*2 - 1].toString().toInt() < 3 && arrayMovementBreak[hour*2 - 1].toString().toInt() == 0) {

                                context?.let {
                                    MaterialAlertDialogBuilder(it)
                                        .setTitle(resources.getString(R.string.title_alert_dialog))
                                        .setMessage(resources.getString(R.string.message_alert_dialog))
                                        .setNegativeButton(resources.getString(R.string.dialog_cancel)) { dialog, which ->

                                        }
                                        .setPositiveButton(resources.getString(R.string.change_to_exercise)) { dialog, which ->
                                            findNavController().navigate(R.id.action_navigation_home_to_navigation_exercise)
                                        }
                                        .show()
                                }
                            }
                        }
                    }
                mHandler.postDelayed(mRunnable, 2000)
            } else {
                toast("verbinde zunächst den Sensor")
            }
        }

        if (viewModel.getStatusMeasurment()) {
            binding.buttonStartSensor.text = getString(R.string.btn_stop_sensor)
            sensorStarted = viewModel.getStatusMeasurment()
        } else {
            binding.buttonStartSensor.text = getString(R.string.btn_start_sensor)
            sensorStarted = viewModel.getStatusMeasurment()
        }

        binding.buttonStartSensor.setOnClickListener {

            if (!configData) {
                context?.let {
                    MaterialAlertDialogBuilder(it)
                        .setTitle(resources.getString(R.string.title_alert_dialog))
                        .setMessage(resources.getString(R.string.message_alert_dialog_config))
                        .setNegativeButton(resources.getString(R.string.dialog_cancel)) { dialog, which ->

                        }
                        .setPositiveButton(resources.getString(R.string.change_to_config)) { dialog, which ->
                            findNavController().navigate(R.id.action_navigation_home_to_navigation_setting)
                        }
                        .show()
                }
            } else {
                if (isConnected) {

                    val kalender: Calendar = Calendar.getInstance()
                    var zeitformat = SimpleDateFormat("HH")
                    var time = zeitformat.format(kalender.time)
                    hour = time.toInt()

                    zeitformat = SimpleDateFormat("mm")
                    time = zeitformat.format(kalender.time)
                    minute = time.toInt()

                    val obj = JSONObject()
                    sensorStarted = !sensorStarted


                    // Werte setzen
                    if (sensorStarted) {
                        obj.put("STARTMESSUNG", "AN")
                        obj.put("HOUR", hour)
                        obj.put("MINUTE", minute)
                        obj.put("VIBRATION", statusVibration)
                        obj.put("VIBLENGTH", vibrationLength)
                        obj.put("THRESHOLDBENTBACK", thresholdBent)
                        obj.put("THRESHOLDLEANBACK", thresholdLean)
                        binding.buttonStartSensor.text = getString(R.string.btn_stop_sensor)
                    } else {
                        obj.put("STARTMESSUNG", "AUS")
                        binding.buttonStartSensor.text = getString(R.string.btn_start_sensor)
                    }

                    viewModel.setStatusMeasurment(sensorStarted)

                    // Senden
                    if (gattCharacteristic != null) {
                        gattCharacteristic!!.value = obj.toString().toByteArray()
                        bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
                    }
                } else {
                    toast("verbinde zunächst den Sensor")
                }
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

        for (i in 0 until 48) {
            arrayBent[i] = 0
        }
        for (i in 0 until 48) {
            arrayLeanBack[i] = 0
        }
        for (i in 0 until 48) {
            arrayDynamic[i] = 0
        }
        for (i in 0 until 48) {
            arrayMovementBreak[i] = 0
        }
        for (i in 0 until 48) {
            arrayStraight[i] = 0
        }

        loadDbData()

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

            if (obj.has("bent")) {

                counterReminder = obj.getString("bent").toInt()
            }

            if (obj.has("lean")) {

                counterLeanBack = obj.getString("lean").toInt()
            }

            toast(counterLeanBack.toString())


            val listdataBent = ArrayList<String>()
            if (obj.has("arrBent")) {
                val jArrayBent = obj.getJSONArray("arrBent")
                    for (i in 0 until jArrayBent.length()) {
                        listdataBent.add(jArrayBent.getString(i))
                    }


                for (i in 0 until 48) {
                    if (listdataBent[i].toInt() != 0) {
                        arrayBent[i] = listdataBent[i].toInt() / 2
                    }
                }
            }

            val listdataLean = ArrayList<String>()
            if (obj.has("arrLean")) {

                val jArrayLean = obj.getJSONArray("arrLean")
                for (i in 0 until jArrayLean.length()) {
                    listdataLean.add(jArrayLean.getString(i))
                }


                for (i in 0 until 48) {
                    if (listdataLean[i].toInt() != 0) {
                        arrayLeanBack[i] = listdataLean[i].toInt() / 2
                    }
                }
            }

            val listdataDynamic = ArrayList<String>()
            if (obj.has("arrDynamic")) {

                val jArrayDynamic = obj.getJSONArray("arrDynamic")
                for (i in 0 until jArrayDynamic.length()) {
                    listdataDynamic.add(jArrayDynamic.getString(i))
                }


                for (i in 0 until 48) {
                    if (listdataDynamic[i].toInt() != 0) {
                        arrayDynamic[i] = listdataDynamic[i].toInt()
                    }
                }
            }
            val listdataUpright = ArrayList<String>()
            if (obj.has("arrStraight")) {

                val jArrayUpright = obj.getJSONArray("arrStraight")
                for (i in 0 until jArrayUpright.length()) {
                    listdataUpright.add(jArrayUpright.getString(i))
                }


                for (i in 0 until 48) {
                    if (listdataUpright[i].toInt() != 0) {
                        arrayStraight[i] = listdataUpright[i].toInt()
                    }
                }

                progressTime = 0F

                for (i in 0 until 48) {
                    progressTime += arrayStraight[i].toString().toInt()
                }
            }


            if (progressTime <= timeMaxProgressBar) {
                binding.circularProgressBar.progress = progressTime
                binding.textViewHomeProgressTime.text =
                    getString(R.string.tv_time, progressTime, timeMaxProgressBar)
                binding.textViewProgress.text = getString(R.string.tv_progress)

            } else {
                binding.circularProgressBar.progress = timeMaxProgressBar
                binding.textViewHomeProgressTime.text =
                    getString(R.string.tv_time, timeMaxProgressBar, timeMaxProgressBar)
                binding.textViewProgress.text = getString(R.string.tv_progress_done)
            }


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
        toast("onDestroy")
        mHandler.removeCallbacksAndMessages(null)
    }

    override fun onPause() {
        super.onPause()
        context?.unregisterReceiver(gattUpdateReceiver)
        if (isConnected) {
            bluetoothLeService!!.disconnect()
        }
        toast("Onpause")
    }

    private fun insertDataInDb() {

        val kalender: Calendar = Calendar.getInstance()
        val zeitformat = SimpleDateFormat("yyyy-MM-dd")
        val date = zeitformat.format(kalender.time)


        for (i in 0 until 48) {
            arrayStraightList.add(i, arrayStraight[i])
        }

        for (i in 0 until 48) {
            arrayBentList.add(i, arrayBent[i])
        }

        for (i in 0 until 48) {
            arrayLeanList.add(i, arrayLeanBack[i])
        }

        for (i in 0 until 48) {
            arrayDynamicList.add(i, arrayDynamic[i])
        }


        //Objekt mit Daten befüllen (ID wird automatisch ergänzt)
        val userData = UserData()
        userData.setCounterBentBack(counterReminder)
        userData.setCounterLeanBack(counterLeanBack)
        userData.setProgressTime(progressTime)
        userData.setProgressTimeMax(timeMaxProgressBar)
        userData.setArrayBentBack(arrayBentList)
        userData.setArrayLeanBack(arrayLeanList)
        userData.setArrayDynamicPhase(arrayDynamicList)
        userData.setArrayUpright(arrayStraightList)

        // Schreibe Daten als Document in die Collection Messungen in DB;
        // Eine id als Document Name wird automatisch vergeben
        // Implementiere auch onSuccess und onFailure Listender
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection(date).document("Daten")
            .set(userData)
            .addOnSuccessListener { documentReference ->
                toast(getString(R.string.save))
            }
            .addOnFailureListener { e ->
                toast(getString(R.string.not_save))
            }
        arrayStraightList.clear()
        arrayBentList.clear()
        arrayLeanList.clear()
        arrayDynamicList.clear()
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

                        counterReminder = data!!.getCounterBentBack()
                        counterLeanBack = data!!.getCounterLeanBack()
                        progressTime = data!!.getProgressTime()
                        timeMaxProgressBar = data!!.getProgressTimeMax()
                        arrayBentList = data!!.getArrayBentBack()
                        arrayLeanList = data!!.getArrayLeanBack()
                        arrayDynamicList = data!!.getArrayDynamicPhase()
                        arrayStraightList = data!!.getArrayUpright()

                        for (i in 0 until 48) {
                            arrayBent[i] = arrayBentList[i]
                        }

                        for (i in 0 until 48) {
                            arrayLeanBack[i] = arrayLeanList[i]
                        }

                        for (i in 0 until 48) {
                            arrayDynamic[i] = arrayDynamicList[i]
                        }

                        for (i in 0 until 48) {
                            arrayStraight[i] = arrayStraightList[i]
                        }


                        for (i in 0 until 48) {
                            arrayStraight[i] = arrayStraightList[i]
                        }

                        if (progressTime < timeMaxProgressBar) {
                            binding.circularProgressBar.apply {
                                progressMax = timeMaxProgressBar
                            }
                            binding.textViewHomeProgressTime.text =
                                getString(R.string.tv_time, progressTime, timeMaxProgressBar)
                            binding.circularProgressBar.progress = progressTime
                            binding.textViewProgress.text = getString(R.string.tv_progress)
                        } else {
                            binding.textViewHomeProgressTime.text =
                                getString(R.string.tv_time, timeMaxProgressBar, timeMaxProgressBar)
                            binding.circularProgressBar.progress = timeMaxProgressBar
                            binding.textViewProgress.text = getString(R.string.tv_progress_done)
                        }
                        arrayBentList.clear()
                        arrayLeanList.clear()
                        arrayDynamicList.clear()
                        arrayStraightList.clear()
                    } else{
                        binding.textViewHomeProgressTime.text = getString(R.string.tv_time, 0F, 0F)
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

                    if (dataSetting != null) {
                        //toast("Vibrationsdaten")
                        vibrationLength = dataSetting!!.getVibrationLength()
                        statusVibration = dataSetting!!.getVibration()
                    }

                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }

        db.collection("users").document(uid).collection("Einstellungen")
            .document("Konfiguration")// alle Einträge abrufen
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    //toast("Config")
                    // Datenbankantwort in Objektvariable speichern
                    dataConfig = task.result!!.toObject(UserDataConfig::class.java)

                    if (dataConfig != null) {
                        toast("config data")
                        configData = true
                        thresholdBent = dataConfig!!.getThresholdBentBack()
                        thresholdLean = dataConfig!!.getThresholdLeanBack()
                        toast(thresholdBent.toString() + thresholdLean.toString())

                    }

                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }

        // Einstiegspunkt für die Abfrage ist users/uid/Messungen
        //val uid = mFirebaseAuth.currentUser!!.uid
        //Daten zu Challenges und Bewegungspausen einlesen
        db.collection("users").document(uid).collection(date)
            .document("Challenge")// alle Einträge abrufen
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    dataExercise = task.result!!.toObject(UserDataExercise::class.java)

                    if (dataExercise != null) {
                        arrayMovementBreakDb = dataExercise!!.getMovementBreakArray()

                        for (i in 0 until 48) {
                            arrayMovementBreak[i] = arrayMovementBreakDb[i]
                        }
                    }


                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }
    }
}
