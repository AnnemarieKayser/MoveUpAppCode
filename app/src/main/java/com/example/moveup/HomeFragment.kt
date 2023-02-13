package com.example.moveup

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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


class HomeFragment : Fragment() {


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

  Dieses Fragment zeigt einen ersten Überblick über die Funktionen der App
  und dient zur Navigation
            - Start/Stopp der Messung
            - Anzeige ProgressBar mit Zeit an gerader Haltung
            - Aktualisierung der Daten
            - Ausloggen
*/

/*
  =============================================================
  =======                   Variables                   =======
  =============================================================
*/


    private var _binding: FragmentHomeBinding? = null
    private val viewModel: BasicViewModel by activityViewModels()
    private val binding get() = _binding!!

    // === Bluetooth Low Energy === //
    private lateinit var mBluetooth: BluetoothAdapter
    private var isConnected = false
    private var bluetoothLeService: BluetoothLeService? = null
    private var gattCharacteristic: BluetoothGattCharacteristic? = null
    private var sensorStarted = false
    private var isReceivingData = false

    // === Zeitvariablen === //
    private var hour = 0
    private var minute = 0

    // === Handler-Runnable-Konstrukt === //
    private val mHandler: Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable

    // === Circular-Progress-Bar === //
    private var timeMaxProgressBar = 60F
    private var progressTime: Float = 0F

    // === Datenbank === //
    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var data: UserData? = null
    private var dataExercise: UserDataExercise? = null
    private var dataSetting: UserDataSetting? = null
    private var dataConfig: UserDataConfig? = null


    // === Variablen für Datenbank === //
    private var statusVibration = "VIBON"
    private var vibrationLength = 1000
    private var thresholdBent = -30
    private var thresholdLean = 20
    private var counterReminder = 0
    private var counterLeanBack = 0
    private var configData = false
    private var arrayStraight = arrayOfNulls<Any>(48)
    private var arrayBent = arrayOfNulls<Any>(48)
    private var arrayLeanBack = arrayOfNulls<Any>(48)
    private var arrayDynamic = arrayOfNulls<Any>(48)
    private var arrayMovementBreak = arrayOfNulls<Any>(48)
    private var arrayBentList = arrayListOf<Any?>()
    private var arrayLeanList = arrayListOf<Any?>()
    private var arrayDynamicList = arrayListOf<Any?>()
    private var arrayMovementBreakDb = arrayListOf<Any?>()
    private var arrayStraightList = arrayListOf<Any?>()


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
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

       // --- Deaktivierung des Zurück-Buttons --- //
       requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {}

        // --- Initialisierung Bluetooth-Adapter --- //
        mBluetooth = BluetoothAdapter.getDefaultAdapter()


        // --- BluetoothLe Service starten --- //
        val gattServiceIntent = Intent(context, BluetoothLeService::class.java)
        // --- Service anbinden --- //
        context?.bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)


        // --- Sensor nach 1s verbinden, wenn deviceAddress bekannt und gespeichert ist --- //
        mRunnable = Runnable {
            if (viewModel.getDeviceAddress() != "") {
                bluetoothLeService!!.connect(viewModel.getDeviceAddress())
            }
        }
        mHandler.postDelayed(mRunnable, 1000)


        // --- Navigation zu BluetoothFragment oder Starten der Messung --- //
        binding.buttonNavigate.setOnClickListener {
            if (isConnected) {
                // AlertDialog
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

                    // Einlesen der aktuellen Stunde und Minute
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
                        // Übergabe Start der Messung
                        obj.put("STARTMESSUNG", "AN")

                        // Übergabe der aktuellen Stunde und Minute, um Uhr im Code des
                        // Embedded System zu starten, um Daten zu der entsprechenden Zeit in
                        // einem Array hinzuzufügen und zu speichern
                        obj.put("HOUR", hour)
                        obj.put("MINUTE", minute)

                        // Übergabe der eingestellten Vibration
                        obj.put("VIBRATION", statusVibration)
                        obj.put("VIBLENGTH", vibrationLength)

                        // Übergabe der Konfigurationsdaten
                        obj.put("THRESHOLDBENTBACK", thresholdBent)
                        obj.put("THRESHOLDLEANBACK", thresholdLean)
                        binding.buttonNavigate.text = getString(R.string.btn_stop_sensor)
                    } else {
                        // Übergabe Stopp der Messung
                        obj.put("STARTMESSUNG", "AUS")
                        binding.buttonNavigate.text = getString(R.string.btn_start_sensor)
                    }

                    // Speichern des aktuellen Status der Messung im viewModel
                    viewModel.setStatusMeasurment(sensorStarted)

                    // Senden der Daten
                    if (gattCharacteristic != null) {
                        gattCharacteristic!!.value = obj.toString().toByteArray()
                        bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
                    }
                }
            } else {
                findNavController().navigate(R.id.action_navigation_home_to_navigation_bluetooth)
            }
        }

        // --- Navigation zu ExerciseFragment --- //
        binding.buttonConfigStartChallengeHome.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_navigation_exercise)
        }

        // --- Ausloggen --- //
        // Öffnen der LogInActivity
        binding.buttonLogOutHome.setOnClickListener {
            mFirebaseAuth.signOut()
            val intent = Intent(getActivity(), LoginInActivity::class.java)
            getActivity()?.startActivity(intent)
        }

        // --- Daten empfangen vom ESP32 thing für 2 Sekunden --- //
        binding.buttonGetDataHome.setOnClickListener {
            // wenn der ESP32 thing verbunden ist, werden die Daten empfangen
            if (isConnected) {
                // Daten empfangen aktiviert
                bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, true)
                isReceivingData = true
                binding.buttonGetDataHome.text = getString(R.string.bt_data_off)

                mRunnable = Runnable {
                    // Daten empfangen deaktiviert
                    bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, false)
                    isReceivingData = false
                    binding.buttonGetDataHome.text = getString(R.string.btn_data_graph)
                    // Speichern der Daten
                    insertDataInDb()

                    //Anzeige eines AlertDialogs, wenn der Benutzer sich über einen längeren Zeitraum nicht bewegt hat
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

        // --- Messung starten/stoppen --- //
        // Abfrage, ob der Sensor bereits konfiguriert wurde
        // wenn Nein, Messung wird nicht gestartet
        // wenn Ja und Sensor verbunden, Daten werden an ESP32 thing gesendet
        binding.buttonStartSensor.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_navigation_setting)

        }


        // --- Konfiguration CircularProgressBar --- //
        // Anzeige des Fortschritts mit gerader Haltung
        binding.circularProgressBar.apply {
            // Progress Max
            progressMax = timeMaxProgressBar

            // ProgressBar Farbe
            progressBarColorStart = Color.MAGENTA

            // Farbgradient
            progressBarColorDirection = CircularProgressBar.GradientDirection.RIGHT_TO_LEFT

            // Hintergrundfarbe
            backgroundProgressBarColor = Color.GRAY
            backgroundProgressBarColorDirection = CircularProgressBar.GradientDirection.TOP_TO_BOTTOM

            // Weite der ProgressBar
            progressBarWidth = 10f
            backgroundProgressBarWidth = 4f

            roundBorder = true

            progressDirection = CircularProgressBar.ProgressDirection.TO_RIGHT
        }


        // --- Initialisierung Arrays --- //
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

        // --- Einlesen der Daten aus Datenbank --- //
        loadDbData()
    }

/*
  =============================================================
  =======                                               =======
  =======                   Funktionen                  =======
  =======                                               =======
  =============================================================
*/

    // === serviceConnection === //
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
                BluetoothLeService.ACTION_DATA_AVAILABLE -> onDataAvailable()
            }
        }
    }

    // === OnConnect === //
    private fun onConnect() {
        isConnected = true
        Log.i(ContentValues.TAG, "connected")
        toast("connected")
        binding.textViewConnectSensor.text = getString(R.string.sensor_connected)
        binding.buttonNavigate.text = getString(R.string.btn_start_sensor)
        // --- Einlesen des aktuellen Status der Messung --- //
        if (viewModel.getStatusMeasurment()) {
            binding.buttonNavigate.text = getString(R.string.btn_stop_sensor)
            sensorStarted = viewModel.getStatusMeasurment()
        } else {
            binding.buttonNavigate.text = getString(R.string.btn_start_sensor)
            sensorStarted = viewModel.getStatusMeasurment()
        }
    }

    // === onDisconnect === //
    private fun onDisconnect() {
        isConnected = false
        Log.i(ContentValues.TAG, "disconnected")
        toast("disconnected")
        binding.textViewConnectSensor.text = getString(R.string.tv_connect_sensor_main)
        binding.buttonNavigate.text = getString(R.string.btn_connect)
    }

    // === onGattCharacteristicDiscovered === //
    private fun onGattCharacteristicDiscovered() {
        gattCharacteristic = bluetoothLeService?.getGattCharacteristic()
    }

    // === onDataAvailable === //
    private fun onDataAvailable() {
        // neue Daten verfügbar
        Log.i(ContentValues.TAG, "Data available")
        val bytes: ByteArray = gattCharacteristic!!.value
        // byte[] to string
        val s = String(bytes)
        parseJSONData(s)
    }

    // === parseJSONData === //
    private fun parseJSONData(jsonString: String) {
        try {
            val obj = JSONObject(jsonString)
            //extrahieren des Objektes data

            // Abfrage, ob das jeweilige Array in dem empfangenen Objekt dabei ist
            // Daten im JSONArray werden auf eine ArrayList übertragen
            // Anschließend werden die Daten auf ein ArrayOfNulls übertragen
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

            // Abfrage, ob das jeweilige Array in dem empfangenen Objekt dabei ist
            // Daten im JSONArray werden auf eine ArrayList übertragen
            // Anschließend werden die Daten auf ein ArrayOfNulls übertragen
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

            // Abfrage, ob das jeweilige Array in dem empfangenen Objekt dabei ist
            // Daten im JSONArray werden auf eine ArrayList übertragen
            // Anschließend werden die Daten auf ein ArrayOfNulls übertragen
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

            // Abfrage, ob das jeweilige Array in dem empfangenen Objekt dabei ist
            // Daten im JSONArray werden auf eine ArrayList übertragen
            // Anschließend werden die Daten auf ein ArrayOfNulls übertragen
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

                // gesamte Anzahl an Minuten an einem Tag mit gerader Haltung wird berechnet
                progressTime = 0F
                for (i in 0 until 48) {
                    progressTime += arrayStraight[i].toString().toInt()
                }
            }

            // Aktualisierung der Anzeige der Zeit mit gerader Haltung
            if (progressTime <= timeMaxProgressBar) {
                binding.circularProgressBar.progress = progressTime
                binding.textViewHomeProgressTime.text = getString(R.string.tv_time, progressTime, timeMaxProgressBar)
                binding.textViewProgress.text = getString(R.string.tv_progress)

            } else {
                binding.circularProgressBar.progress = timeMaxProgressBar
                binding.textViewHomeProgressTime.text = getString(R.string.tv_time, timeMaxProgressBar, timeMaxProgressBar)
                binding.textViewProgress.text = getString(R.string.tv_progress_done)
            }

        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    // === onDestroyView === //
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // === onResume === //
    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).supportActionBar!!.show()
        if (!mBluetooth.isEnabled) {
            val turnBTOn = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(turnBTOn, 1)
        }
        context?.registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
        if (bluetoothLeService != null && isConnected) {
            var result = bluetoothLeService!!.connect(viewModel.getDeviceAddress())
            Log.d(ContentValues.TAG, "Connect request result=" + result)
        }
    }

    // === onDestroy === //
    // Beenden der Bluetooth-Verbindung
    override fun onDestroy() {
        super.onDestroy()
        bluetoothLeService!!.disconnect()
        bluetoothLeService!!.close()
        context?.unbindService(serviceConnection)
        bluetoothLeService = null
        mHandler.removeCallbacksAndMessages(null)
    }

    // === onPause === //
    // Beenden der Bluetooth-Verbindung
    override fun onPause() {
        super.onPause()
        context?.unregisterReceiver(gattUpdateReceiver)
        if (isConnected) {
            bluetoothLeService!!.disconnect()
        }
    }

    // === insertDataInDb === //
    // Speichern der Daten in der Datenbank
    // Daten werden benutzerspezifisch gespeichert
    // Daten werden zu dem aktuellen Datum gespeichert
    private fun insertDataInDb() {

        // Einlesen des aktuellen Datums
        val kalender: Calendar = Calendar.getInstance()
        val zeitformat = SimpleDateFormat("yyyy-MM-dd")
        val date = zeitformat.format(kalender.time)

        // Daten aus Arrays für Graphen werden in ArrayLists überschrieben, da
        // diese nur in die Datenbank geschrieben werden können
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
        userData.setProgressTime(progressTime)
        userData.setProgressTimeMax(timeMaxProgressBar)
        userData.setArrayBentBack(arrayBentList)
        userData.setArrayLeanBack(arrayLeanList)
        userData.setArrayDynamicPhase(arrayDynamicList)
        userData.setArrayUpright(arrayStraightList)

        // Speicherpfad users/uid/date/Daten
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection(date).document("Daten")
            .set(userData)
            .addOnSuccessListener { documentReference ->
                toast(getString(R.string.save))
            }
            .addOnFailureListener { e ->
                toast(getString(R.string.not_save))
            }

        // Löschen der Daten aus Listen
        arrayStraightList.clear()
        arrayBentList.clear()
        arrayLeanList.clear()
        arrayDynamicList.clear()
    }

    // === loadDbData === //
    // Einlesen der Daten aus der Datenbank
    private fun loadDbData() {

        // Einlesen des aktuellen Datums
        val kalender: Calendar = Calendar.getInstance()
        val zeitformat = SimpleDateFormat("yyyy-MM-dd")
        val date = zeitformat.format(kalender.time)

        // Einstiegspunkt für die Abfrage ist users/uid/date/Daten
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection(date)
            .document("Daten") // alle Einträge abrufen
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    data = task.result!!.toObject(UserData::class.java)

                    // Daten werden den Variablen zugewiesen, wenn diese ungleich null sind
                    if (data != null) {
                        progressTime = data!!.getProgressTime()
                        timeMaxProgressBar = data!!.getProgressTimeMax()
                        arrayBentList = data!!.getArrayBentBack()
                        arrayLeanList = data!!.getArrayLeanBack()
                        arrayDynamicList = data!!.getArrayDynamicPhase()
                        arrayStraightList = data!!.getArrayUpright()

                        // Überschreiben der Daten in die ArraysOfNulls
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

                        // ProgressBar mit Anzeige der gesamten Zeit an gerader Haltung wird aktualisiert
                        if (progressTime < timeMaxProgressBar) {

                            binding.circularProgressBar.apply {
                                progressMax = timeMaxProgressBar
                            }

                            binding.textViewHomeProgressTime.text = getString(R.string.tv_time, progressTime, timeMaxProgressBar)
                            binding.circularProgressBar.progress = progressTime
                            binding.textViewProgress.text = getString(R.string.tv_progress)
                        } else {
                            binding.textViewHomeProgressTime.text = getString(R.string.tv_time, timeMaxProgressBar, timeMaxProgressBar)
                            binding.circularProgressBar.progress = timeMaxProgressBar
                            binding.textViewProgress.text = getString(R.string.tv_progress_done)
                        }

                        // Löschen der Daten aus Listen
                        arrayBentList.clear()
                        arrayLeanList.clear()
                        arrayDynamicList.clear()
                        arrayStraightList.clear()
                    } else{
                        // wenn die Daten gleich null sind, wird die Anzeige auf null gesetzt
                        binding.textViewHomeProgressTime.text = getString(R.string.tv_time, 0F, 0F)
                    }
                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }


        // Daten zu Einstellung der Vibration werden eingelesen
        // Einstiegspunkt für die Abfrage ist users/uid/date/Einstellungen/Vibration
        db.collection("users").document(uid).collection("Einstellungen")
            .document("Vibration")
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    dataSetting = task.result!!.toObject(UserDataSetting::class.java)

                    // Daten werden den Variablen zugewiesen, wenn diese ungleich null sind
                    if (dataSetting != null) {
                        vibrationLength = dataSetting!!.getVibrationLength()
                        statusVibration = dataSetting!!.getVibration()
                    }

                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }

        // Daten zur Konfiguration des Sensors werden eingelesen
        // Einstiegspunkt für die Abfrage ist users/uid/date/Einstellungen/Konfiguration
        db.collection("users").document(uid).collection("Einstellungen")
            .document("Konfiguration")
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    dataConfig = task.result!!.toObject(UserDataConfig::class.java)

                    // Daten werden den Variablen zugewiesen, wenn diese ungleich null sind
                    if (dataConfig != null) {
                        configData = true
                        thresholdBent = dataConfig!!.getThresholdBentBack()
                        thresholdLean = dataConfig!!.getThresholdLeanBack()
                    }
                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }

        // Daten zu Challenges und Bewegungspausen einlesen
        // Einstiegspunkt für die Abfrage ist users/uid/date/Challenge
        db.collection("users").document(uid).collection(date)
            .document("Challenge")// alle Einträge abrufen
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    dataExercise = task.result!!.toObject(UserDataExercise::class.java)

                    // Daten werden den Variablen zugewiesen, wenn diese ungleich null sind
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
