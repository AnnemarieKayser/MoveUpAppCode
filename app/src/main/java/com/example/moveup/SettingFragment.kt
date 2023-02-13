package com.example.moveup

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.moveup.databinding.FragmentSettingBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONException
import org.json.JSONObject
import splitties.toast.toast
import java.util.concurrent.atomic.AtomicInteger

class SettingFragment : Fragment() {


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

  In diesem Fragment können Einstellungen des Systems vorgenommen werden
            - Ein- und Ausstellen der Vibration
            - Einstellen der Länge der Vibration
            - Konfiguration des Sensors auf die individuelle Haltung

*/

/*
  =============================================================
  =======                   Variables                   =======
  =============================================================
*/

    private var _binding: FragmentSettingBinding? = null
    private val viewModel: BasicViewModel by activityViewModels()
    private val binding get() = _binding!!

    // === Bluetooth Low Energy === //
    private lateinit var mBluetooth: BluetoothAdapter
    private var isConnected = false
    private var isReceivingData = false
    private var bluetoothLeService: BluetoothLeService? = null
    private var gattCharacteristic: BluetoothGattCharacteristic? = null
    private var statusVibration = "VIBON"
    private var vibrationLength = 1000

    // === Konfiguration === //
    private var thresholdBent = -50
    private var thresholdLean = 30

    // === Handler-Runnable-Konstrukt === //
    private val handler: Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable

    // === Datenbank === //
    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var data: UserDataSetting? = null

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
        _binding = FragmentSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- Deaktivierung der Buttons --- //
        binding.buttonStart.isEnabled = false
        binding.buttonStart2.isEnabled = false
        binding.buttonStart3.isEnabled = false
        binding.buttonGetConfigData.isEnabled = false

        // --- Initialisierung Bluetooth-Adapter --- //
        mBluetooth = BluetoothAdapter.getDefaultAdapter()


        // --- BluetoothLe Service starten --- //
        val gattServiceIntent = Intent(context, BluetoothLeService::class.java)
        // --- Service anbinden --- //
        context?.bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)


        // --- Sensor nach 1s verbinden, wenn deviceAddress bekannt und gespeichert ist --- //
        mRunnable = Runnable {

            if (viewModel.getDeviceAddress() != "") {
                bluetoothLeService!!.connect(viewModel.getDeviceAddress());
            }
        }
        handler.postDelayed(mRunnable, 1000)

        // --- ESP32 thing verbinden --- //
        binding.buttonConnectSensor.setOnClickListener {
            bluetoothLeService!!.connect(viewModel.getDeviceAddress())
        }

        // --- Einlesen der Daten aus der Datenbank --- //
        loadDbData()

        // --- Ausloggen und starten der LogInActivity --- //
        binding.buttonLogOut.setOnClickListener {
            mFirebaseAuth.signOut()
            val intent = Intent(getActivity(), LoginInActivity::class.java)
            getActivity()?.startActivity(intent)
        }

        // --- Änderung des Status des switch-Schalters (aktiviert/deaktiviert) --- //
        // Senden der Einstellung an ESP32 thing
        binding.switchVibration.setOnCheckedChangeListener { buttonView, isChecked ->
            val obj = JSONObject()

            if (isChecked) {
                toast(getString(R.string.vibration_on))
                vibrationLength = 1000
                statusVibration = "VIBON"

            } else {
                toast(getString(R.string.vibration_off))
                statusVibration = "VIBOFF"
                vibrationLength = 0
            }

            obj.put("VIBRATION", statusVibration)
            obj.put("VIBLENGTH", vibrationLength)

            // Senden der Daten
            if (gattCharacteristic != null) {
                gattCharacteristic!!.value = obj.toString().toByteArray()
                bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
            }

            // Speichern der Einstellung
            insertDataInDb()
        }

        // --- Änderung des Status des toggle-Buttons --- //
        // Senden der Einstellung an ESP32 thing
        binding.toggleButton.addOnButtonCheckedListener { toggleButton, checkedId, isChecked ->
            val obj = JSONObject()

            if (isChecked) {
                // Überprüfen, welcher Button ausgewählt wurde
                when (checkedId) {
                    R.id.buttonVibShort -> {
                        vibrationLength = 500
                    }

                    R.id.buttonVibMedium -> {
                        vibrationLength = 1000
                    }

                    R.id.buttonVibLong -> {
                        vibrationLength = 2000
                    }
                }
            }
            obj.put("VIBLENGTH", vibrationLength)

            // Daten senden
            if (gattCharacteristic != null) {
                gattCharacteristic!!.value = obj.toString().toByteArray()
                bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
            }

            // Speichern der Einstellung
            insertDataInDb()
        }

        // --- Starten der Konfiguration --- //
        // 10 Sekunden eine gerade Haltung einnehmen
        binding.buttonStart.setOnClickListener {

            // --- Beenden des Countdowns, falls noch einer läuft --- //
            handler.removeCallbacksAndMessages(null)


            val obj = JSONObject()
            // Werte setzen
            obj.put("STATUSKONFIG", true)
            obj.put("START", true)
            obj.put("STARTMESSUNG", "AUS")

            // Senden der Daten
            if (gattCharacteristic != null) {
                gattCharacteristic!!.value = obj.toString().toByteArray()
                bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
            }

            // Start Countdown
            val n = AtomicInteger(10) // initialisiere mit 10.
            val counter: Runnable = object : Runnable {
                override fun run() {
                    //Textfeld mit aktuellem n füllen.
                    binding.textViewCountdown.text = Integer.toString(n.get())
                    //wenn n >= 1, sekündlich runterzählen
                    if (n.getAndDecrement() >= 1) {
                        handler.postDelayed(this, 1000)
                    } else {
                        binding.textViewCountdown.text = getString(R.string.tv_countdown)
                        binding.buttonStart2.isEnabled = true
                    }
                }
            }
            handler.postDelayed(counter, 0)
        }

        // --- Starten der Konfiguration --- //
        // 10 Sekunden eine ungerade Haltung einnehmen
        binding.buttonStart2.setOnClickListener {

            // --- Beenden des Countdowns, falls noch einer läuft --- //
            handler.removeCallbacksAndMessages(null)

            val obj = JSONObject()
            // Werte setzen
            obj.put("STATUSKONFIG2", true)
            obj.put("START", true)


            // Senden der Daten
            if (gattCharacteristic != null) {
                gattCharacteristic!!.value = obj.toString().toByteArray()
                bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
            }

            //Start Countdown
            val n = AtomicInteger(10) // initialisiere mit 10.
            val counter: Runnable = object : Runnable {
                override fun run() {
                    //Textfeld mit aktuellem n füllen.
                    binding.textViewCountdown2.text = Integer.toString(n.get())
                    //wenn n >= 1, sekündlich runterzählen
                    if (n.getAndDecrement() >= 1) {
                        handler.postDelayed(this, 1000)
                    } else {
                        binding.textViewCountdown2.text = getString(R.string.tv_countdown)
                        binding.buttonStart3.isEnabled = true
                    }
                }
            }
            handler.postDelayed(counter, 0)
        }

        // --- Starten der Konfiguration --- //
        // 10 Sekunden eine zurückgelehnte Haltung einnehmen
        binding.buttonStart3.setOnClickListener {

            // --- Beenden des Countdowns, falls noch einer läuft --- //
            handler.removeCallbacksAndMessages(null)

            val obj = JSONObject()
            // Werte setzen
            obj.put("STATUSKONFIG3", true)
            obj.put("START", true)

            // Senden der Daten
            if (gattCharacteristic != null) {
                gattCharacteristic!!.value = obj.toString().toByteArray()
                bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
            }

            //Start Countdown
            val n = AtomicInteger(10) // initialisiere mit 10.
            val counter: Runnable = object : Runnable {
                override fun run() {
                    //Textfeld mit aktuellem n füllen.
                    binding.textViewCountdown3.text = Integer.toString(n.get())
                    //wenn n >= 1, sekündlich runterzählen
                    if (n.getAndDecrement() >= 1) {
                        handler.postDelayed(this,1000)
                    } else {

                        binding.textViewCountdown3.text = getString(R.string.tv_countdown)
                    }
                }
            }
            handler.postDelayed(counter, 0)
        }

        // --- Daten empfangen vom ESP32 thing aktivieren/deaktivieren --- //
        binding.buttonGetConfigData.setOnClickListener {
            if (isReceivingData) {
                bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, false)
                isReceivingData = false
                binding.buttonGetConfigData.text = getString(R.string.btn_data_graph)
            } else {
                bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, true)
                isReceivingData = true
                binding.buttonGetConfigData.text = getString(R.string.bt_data_off)
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

    // === insertConfigDataInDb === //
    private fun insertConfigDataInDb() {

        //Objekt mit Daten befüllen (ID wird automatisch ergänzt)
        val userData = UserDataConfig()
        userData.setThresholdBentBack(thresholdBent)
        userData.setThresholdLeanBack(thresholdLean)

        // Speichern der Daten in der Datenbank
        // Daten werden benutzerspezifisch gespeichert
        // Speicherpfad users/uid/date/Einstellungen/Konfiguration
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection("Einstellungen").document("Konfiguration")
            .set(userData)
            .addOnSuccessListener { documentReference ->
                toast(getString(R.string.save))
            }
            .addOnFailureListener { e ->
                toast(getString(R.string.not_save))
            }
    }


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
        binding.textViewStatusBLE.text = getString(R.string.tv_status_ble, "verbunden")
        binding.buttonConnectSensor.visibility = View.INVISIBLE
        binding.buttonStart.isEnabled = true
        binding.buttonGetConfigData.isEnabled = true
    }

    // === onDisconnect === //
    private fun onDisconnect() {
        isConnected = false
        Log.i(ContentValues.TAG, "disconnected")
        binding.buttonConnectSensor.visibility = View.VISIBLE
        binding.buttonStart.isEnabled = false
        binding.buttonStart2.isEnabled = false
        binding.buttonStart3.isEnabled = false
        binding.buttonGetConfigData.isEnabled = false
        binding.textViewStatusBLE.text = getString(R.string.tv_status_ble, "Nicht verbunden")
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

            // Konfigurationsdaten empfangen
            thresholdBent = obj.getString("thresholdBentBack").toInt()
            thresholdLean = obj.getString("thresholdLeanBack").toInt()
            toast(thresholdBent.toString() + thresholdLean.toString())

            // Daten speichern
            insertConfigDataInDb()

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
        context?.registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
        if (bluetoothLeService != null && isConnected) {
            var result = bluetoothLeService!!.connect(viewModel.getDeviceAddress());
            Log.d(ContentValues.TAG, "Connect request result=" + result);
        }

    }

    // === onDestroy === //
    // Ble-Verbindung beenden
    override fun onDestroy() {
        super.onDestroy()
        bluetoothLeService!!.disconnect()
        bluetoothLeService!!.close()
        context?.unbindService(serviceConnection)
        bluetoothLeService = null
    }

    // === onPause === //
    // countdown beenden
    override fun onPause() {
        super.onPause()
        context?.unregisterReceiver(gattUpdateReceiver)
        if (isConnected) {
            bluetoothLeService!!.disconnect()
        }
        handler.removeCallbacksAndMessages(null)
    }

    // === insertDataInDb === //
    private fun insertDataInDb() {

        //Objekt mit Daten befüllen (ID wird automatisch ergänzt)
        val userData = UserDataSetting()
        userData.setVibration(statusVibration)
        userData.setVibrationLength(vibrationLength)

        // Speichern der Daten in der Datenbank
        // Daten werden benutzerspezifisch gespeichert
        // Speicherpfad users/uid/date/Einstellungen/Vibration
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection("Einstellungen").document("Vibration")
            .set(userData)
            .addOnSuccessListener { documentReference ->
                toast(getString(R.string.save))
            }
            .addOnFailureListener { e ->
                toast(getString(R.string.not_save))
            }
    }

    // === loadDbData === //
    // Einlesen der Daten aus der Datenbank
    fun loadDbData() {

        // Einstiegspunkt für die Abfrage ist users/uid/Einstellungen/Vibration
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection("Einstellungen")
            .document("Vibration")
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    data = task.result!!.toObject(UserDataSetting::class.java)

                    // Daten werden den Variablen zugewiesen, wenn diese ungleich null sind
                    if (data != null) {
                        vibrationLength = data!!.getVibrationLength()
                        statusVibration = data!!.getVibration()

                        // Anzeige anpassen
                        binding.switchVibration.isChecked = statusVibration == "VIBON"

                        if (vibrationLength == 500) {
                            binding.toggleButton.check(R.id.buttonVibShort)
                        }
                        if (vibrationLength == 1000) {
                            binding.toggleButton.check(R.id.buttonVibMedium)
                        }
                        if (vibrationLength == 2000) {
                            binding.toggleButton.check(R.id.buttonVibLong)
                        }
                    }

                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }
    }

}