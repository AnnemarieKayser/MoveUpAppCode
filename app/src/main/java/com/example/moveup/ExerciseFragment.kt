package com.example.moveup

import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.moveup.databinding.FragmentExerciseBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.component1
import org.json.JSONException
import org.json.JSONObject
import splitties.toast.toast
import java.text.SimpleDateFormat
import java.util.*


class ExerciseFragment : Fragment() {

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
  =======                Funktion                       =======
  =============================================================

  In diesem Fragment kann eine Challenge oder eine Bewegungspause gestartet werden
            - Für die Challenge kann der Benutzer eine Zielzeit eingeben, die
              er gerade Sitzen möchte
            - Die Zeit wird an den ESP32 thing übergeben und dort wird eine Stoppuhr gestartet
            - das Ende der Challenge wird zurück übergeben und in der Datenbank gespeichert

            - Bei einer Bewegungspause werden Videos aus der Datenbank geladen und
              in einem VideoView abgespielt
            - die Videos zeigen kurze Übungen, um den Rücken zu entlasten
            - Der Benutzer kann die Übungen so oft wie er möchte ausführen
*/

/*
  =============================================================
  =======                   Variablen                   =======
  =============================================================
*/


    private var _binding: FragmentExerciseBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BasicViewModel by activityViewModels()

    // === Bluetooth Low Energy === //
    private lateinit var mBluetooth: BluetoothAdapter
    private var isConnected = false
    private var bluetoothLeService: BluetoothLeService? = null
    private var gattCharacteristic: BluetoothGattCharacteristic? = null
    private var isReceivingData = false
    private var sensorStarted = true

    // === Handler-Runnable-Konstrukt === //
    private val mHandler: Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable

    // === Datenbank === //
    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storageReference: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private var storageRef = storageReference.reference
    private var data: UserDataExercise? = null

    // === Variablen für Challenge === //
    private var arrayChallenge = arrayListOf<Any?>()
    private var counterChallenge = 0
    private var challengeStarted = false
    private var statusChallenge = ""
    private var statusReceived = false

    // === Variablen für Bewegungspause === //
    private var counterMovementBreak = 0
    private var arrayMovementBreak = arrayListOf<Any?>()

    // === MediaController === //
    var mediaController: MediaController? = null
    var r = Random()
    private var counterVideoMax = 1
    private var counterShowVideo = 0

    // === Zeitvariablen === //
    private var time = 0
    private var hour = 0
    private var minute = 0

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
        _binding = FragmentExerciseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        // --- Initialisierung Arrays --- //
        for (i in 0 until 48) {
            arrayChallenge.add(i, 0)
        }

        for (i in 0 until 48) {
            arrayMovementBreak.add(i, 0)
        }

        // --- Daten aus der Datenbank laden --- //
        loadDbData()

        // --- Anzeige der abgeschlossenen Challenges --- //
        binding.textViewChallengesCompleted.text = getString(R.string.tv_challenge_completed, counterChallenge)

        // --- Empfang von Daten vom ESP32 thing aktivieren/deaktivieren --- //
        binding.buttonGetData.setOnClickListener {
            // Daten empfangen deaktiviert
            if (isReceivingData) {
                bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, false)
                isReceivingData = false
                binding.buttonGetData.text = getString(R.string.btn_data_graph)
            } else {
                // Daten empfangen aktiviert
                bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, true)
                isReceivingData = true
                binding.buttonGetData.text = getString(R.string.bt_data_off)
            }
        }

        // --- Starten einer Challenge --- //
        binding.buttonStartChallenge.setOnClickListener {

            // Eingegebene Zeit wird eingelesen und der Variablen time zugewiesen
            val timeChallenge: String = binding.editTextChallengeTime.text.toString()
            time = timeChallenge.toInt()

            // Speichern der Zeit im viewModel
            viewModel.setTimeChallenge(time)

            // Überprüfen, ob eine Zeit eingegeben wurde
            if (timeChallenge.isEmpty()) {
                binding.editTextChallengeTime.error = getString(R.string.required)
            } else {
                // wenn der Sensor verbunden ist, werden die Daten gesendet
                if (isConnected) {
                    statusReceived = false

                    val obj = JSONObject()
                    challengeStarted = !challengeStarted
                    // Werte setzen
                    if (challengeStarted) {
                        obj.put("CHALLENGE", "START")
                        obj.put("TIMECHALLENGE", time)
                        binding.buttonStartChallenge.text = getString(R.string.btn_stop_challenge)
                    } else {
                        obj.put("CHALLENGE", "STOPP")
                        binding.buttonStartChallenge.text = getString(R.string.btn_start_challenge)
                    }

                    // Senden der Daten
                    if (gattCharacteristic != null) {
                        gattCharacteristic!!.value = obj.toString().toByteArray()
                        bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
                    }
                } else {
                    toast("Sensor ist nicht verbunden")
                }
            }
        }

        // --- Einlesen des aktuellen Status der Messung --- //
        if (viewModel.getStatusMeasurment()) {
            binding.buttonStopStart.text = getString(R.string.btn_pause)
            sensorStarted = viewModel.getStatusMeasurment()
        } else {
            binding.buttonStopStart.text = getString(R.string.btn_start_sensor)
            sensorStarted = viewModel.getStatusMeasurment()
        }

        // --- Messung starten oder stoppen --- //
        binding.buttonStopStart.setOnClickListener {

            // wenn der Sensor verbunden ist, werden die Daten gesendet
            if (isConnected) {

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
                    obj.put("STARTMESSUNG", "AN")
                    // Übergabe der aktuellen Stunde und Minute, um Uhr im Code des
                    // Embedded System zu starten, um Daten zu der entsprechenden Zeit in
                    // einem Array hinzuzufügen und zu speichern
                    obj.put("HOUR", hour)
                    obj.put("MINUTE", minute)

                    binding.buttonStopStart.text = getString(R.string.btn_pause)
                } else {
                    obj.put("STARTMESSUNG", "AUS")

                    binding.buttonStopStart.text = getString(R.string.btn_start_sensor)
                }

                // Speichern des aktuellen Status der Messung im viewModel
                viewModel.setStatusMeasurment(sensorStarted)

                // Senden der Daten
                if (gattCharacteristic != null) {
                    gattCharacteristic!!.value = obj.toString().toByteArray()
                    bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
                }
            } else {
                toast("verbinde zunächst den Sensor")
            }
        }

        // --- Initialisierung MediaController --- //
        if (mediaController == null) {
            mediaController = MediaController(activity)
            mediaController!!.setAnchorView(binding.videoView)
        }
        binding.videoView.setMediaController(mediaController)

        // --- Nachdem ein Video zuende gespielt wurde, wird diese Funktion aufgerufen --- //
        binding.videoView.setOnCompletionListener {
            // Das Video wird fünf mal abgespielt
            // Anschließend kann ein neues geladen werden
            counterShowVideo++
            if(counterShowVideo < 6) {
                binding.videoView.start()
            }else {
                toast("Starte neues Video")
            }
        }

        // --- Abfrage der Anzahl an Videos in der Datenbank --- //
        storageRef.child("Video").listAll().addOnSuccessListener { (items) ->
                counterVideoMax = items.size
            }
            .addOnFailureListener {
                toast(getString(R.string.error_video))
            }

        // --- Anzeige AlertDialog mit Erklärung zu Bewegungspause --- //
        binding.buttonShowDirections.setOnClickListener {
            context?.let {
                MaterialAlertDialogBuilder(it)
                    .setTitle(resources.getString(R.string.title_alert_dialog))
                    .setMessage(resources.getString(R.string.message_alert_dialog_exercise))
                    .setPositiveButton(resources.getString(R.string.accept)) { dialog, which ->
                    }
                    .show()
            }
        }

        // --- Laden und Abspielen eines Videos --- //
        // Zufälliges Video wird aus der Datenbank geladen und im VideoView abgespielt
        // Anzahl der Bewegungspausen wird um eins hochzählt und in der Datenbank gespeichert
        binding.buttonGetVideo.setOnClickListener {

            // Einlesen der aktuellen Stunde und Minute, um die durchgeführte Bewegungspause
            // zu der entsprechenden Uhrzeit zu speichern
            val kalender: Calendar = Calendar.getInstance()
            var zeitformat = SimpleDateFormat("HH")
            var time = zeitformat.format(kalender.time)
            hour = time.toInt()

            zeitformat = SimpleDateFormat("mm")
            time = zeitformat.format(kalender.time)
            minute = time.toInt()

            // Umwandeln der Stunde in halbe Stunden
            if(minute > 29){
                hour = hour * 2 + 1
            }

            if(minute < 30){
                hour *= 2
            }

            // Zurücksetzen der Anzahl an Wiederholungen des Videos
            counterShowVideo = 0

            // Hochzählen der Bewegungspausen
            counterMovementBreak++

            // Speichern der Anzahl in der Datenbank
            arrayMovementBreak[hour] = arrayMovementBreak[hour].toString().toInt() + 1
            insertDataInDb()

            // Initialisierung ProgressDialog
            val progressDialog = ProgressDialog(activity)
            progressDialog.setTitle(getString(R.string.progress_dialog_title))
            progressDialog.setMessage(getString(R.string.progress_dialog_message))
            progressDialog.setCancelable(false)
            progressDialog.show()

            // zufälliges Laden eines Videos
            val videoID = r.nextInt(counterVideoMax - 1) + 1

            storageRef.child("Video/" + videoID + ".mp4").downloadUrl.addOnSuccessListener {

                // ProgressDialog beenden
                if (progressDialog.isShowing) {
                    progressDialog.dismiss()
                }

                //  Uri-Adresse einlesen
                val uri: Uri = Uri.parse(it.toString())

                binding.videoView.setVideoURI(uri)

                // Video starten
                binding.videoView.requestFocus()
                binding.videoView.start()

            }
                .addOnFailureListener {
                    toast(getString(R.string.error_video))
                }
        }
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

    // === onConnect === //
    private fun onConnect() {
        isConnected = true
        Log.i(ContentValues.TAG, "connected")
        toast("connected")
    }

    // === onDisconnect === //
    private fun onDisconnect() {
        isConnected = false
        Log.i(ContentValues.TAG, "disconnected")
        toast("disconnected")
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
            //Daten einlesen und Variablen zuweisen
            statusChallenge = obj.getString("challenge").toString()

            // Challenge beendet
            if (statusChallenge == "geschafft" && !statusReceived) {

                statusReceived = true

                val obj = JSONObject()
                // Werte setzen
                obj.put("CHALLENGERECEIVED", true)

                // Senden der Daten
                if (gattCharacteristic != null) {
                    gattCharacteristic!!.value = obj.toString().toByteArray()
                    bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
                }

                // Einlesen der aktuellen Stunde und Minute, um die durchgeführte Bewegungspause
                // zu der entsprechenden Uhrzeit zu speichern
                val kalender: Calendar = Calendar.getInstance()
                var zeitformat = SimpleDateFormat("HH")
                var time = zeitformat.format(kalender.time)
                hour = time.toInt()

                zeitformat = SimpleDateFormat("mm")
                time = zeitformat.format(kalender.time)
                minute = time.toInt()

                // Umwandeln der Stunde in halbe Stunden
                if(minute > 29){
                    hour = hour * 2 + 1
                }

                if(minute < 30){
                    hour *= 2
                }

                // Hochzählen der abgeschlossenen Challenges
                counterChallenge++

                // Speichern der Daten
                arrayChallenge[hour] = arrayChallenge[hour].toString().toInt() + viewModel.getTimeChallenge()
                insertDataInDb()

                // Aktualisierung Anzeige
                binding.textViewChallengesCompleted.text = getString(R.string.tv_challenge_completed, counterChallenge)
                binding.buttonStartChallenge.text = getString(R.string.btn_start_challenge)

                // Zeit im viewModel auf null setzen
                viewModel.setTimeChallenge(0)

                // Anzeige AlertDialog
                context?.let {
                    MaterialAlertDialogBuilder(it)
                        .setTitle(resources.getString(R.string.title_alert_challenge))
                        .setMessage(resources.getString(R.string.message_alert_dialog_challenge))

                        .setPositiveButton(resources.getString(R.string.accept)) { dialog, which ->

                        }
                        .show()
                }
            }

        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    // === insertDataInDb === //
    // Daten werden zu dem aktuellen Tag gespeichert
    // Daten werden benutzerspezifisch gespeichert
    // Daten werden in der Datenbank in dem Dokument "Challenges" gespeichert
    private fun insertDataInDb() {

        // Einlesen des aktuellen Datums
        val kalender: Calendar = Calendar.getInstance()
        val zeitformat = SimpleDateFormat("yyyy-MM-dd")
        val date = zeitformat.format(kalender.time)

        // Objekt mit Daten befüllen (ID wird automatisch ergänzt)
        val userData = UserDataExercise()
        userData.setChallenge(counterChallenge)
        userData.setChallengeArray(arrayChallenge)
        userData.setMovementBreak(counterMovementBreak)
        userData.setMovementBreakArray(arrayMovementBreak)

        // Speicherpfad: users/uid/date/Challenges
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection(date).document("Challenge")
            .set(userData)
            .addOnSuccessListener { documentReference ->
                toast(getString(R.string.save))
            }
            .addOnFailureListener { e ->
                toast(getString(R.string.not_save))
            }
    }

    // === loadDbData === //
    // Daten werden aus der Datenbank geladen
    fun loadDbData() {

        // Einlesen des aktuellen Datums
        val kalender: Calendar = Calendar.getInstance()
        val zeitformat = SimpleDateFormat("yyyy-MM-dd")
        val date = zeitformat.format(kalender.time)

        // Einstiegspunkt für die Abfrage ist users/uid/date/Challenge
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection(date)
            .document("Challenge")
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    data = task.result!!.toObject(UserDataExercise::class.java)

                    // Daten werden den Variablen zugewiesen, wenn diese ungleich null sind
                    if (data != null) {
                        counterChallenge = data!!.getChallenge()
                        counterMovementBreak = data!!.getMovementBreak()
                        arrayChallenge = data!!.getChallengeArray()
                        arrayMovementBreak = data!!.getMovementBreakArray()

                        // Aktualisierung Anzeige
                        binding.textViewChallengesCompleted.text =
                            getString(R.string.tv_challenge_completed, counterChallenge)
                    }

                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
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
    // Ble-Verbindung wird beendet
    override fun onDestroy() {
        super.onDestroy()
        bluetoothLeService!!.disconnect()
        bluetoothLeService!!.close()
        context?.unbindService(serviceConnection)
        bluetoothLeService = null
    }

    // === onPause === //
    // Ble-Verbindung wird beendet
    override fun onPause() {
        super.onPause()
        context?.unregisterReceiver(gattUpdateReceiver)
        if (isConnected) {
            bluetoothLeService!!.disconnect()
        }
    }

}