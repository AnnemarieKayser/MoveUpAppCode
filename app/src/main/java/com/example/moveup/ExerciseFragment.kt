package com.example.moveup

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.BluetoothLeScanner
import android.content.*
import com.example.moveup.databinding.FragmentExerciseBinding
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONException
import org.json.JSONObject
import splitties.toast.toast
import java.text.SimpleDateFormat
import java.util.*


class ExerciseFragment : Fragment() {

    private var _binding: FragmentExerciseBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BasicViewModel by activityViewModels()

    //Ble
    private lateinit var scanner: BluetoothLeScanner
    private lateinit var mBluetooth: BluetoothAdapter
    private var isConnected = false
    private var bluetoothLeService: BluetoothLeService? = null
    private var gattCharacteristic: BluetoothGattCharacteristic? = null
    private var challengeStarted = false
    private var isReceivingData = false


    private val mHandler: Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable

    //Datenbank
    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db : FirebaseFirestore by lazy { FirebaseFirestore.getInstance()  }
    private var data: UserDataExercise? = null
    private var counterChallenge = 0

    private var statusChallenge = ""
    private var time = 0
    private var hour = 0
    private var minute = 0


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentExerciseBinding.inflate(inflater, container, false)
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
                bluetoothLeService!!.connect(viewModel.getDeviceAddress());
            }
        }
        mHandler.postDelayed(mRunnable, 1000)

        binding.buttonGetData.setOnClickListener {
            if (isReceivingData) {
                bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, false)
                isReceivingData = false
                toast("keine daten empfangen")
                binding.buttonGetData.text = getString(R.string.btn_data_graph)
            } else {
                bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, true)
                isReceivingData = true
                toast("Daten empfangen")
                binding.buttonGetData.text = getString(R.string.bt_data_off)
            }
        }

        binding.buttonStartChallenge.setOnClickListener {

            val kalender: Calendar = Calendar.getInstance()
            var zeitformat = SimpleDateFormat("HH")
            var timeFormat = zeitformat.format(kalender.time)
            hour = timeFormat.toInt()

            zeitformat = SimpleDateFormat("mm")
            timeFormat = zeitformat.format(kalender.time)
            minute = timeFormat.toInt()


            val timeChallenge: String = binding.editTextChallengeTime.text.toString()
            time = timeChallenge.toInt()

            if (timeChallenge.isEmpty()) {
                binding.editTextChallengeTime.error = getString(R.string.required)
            } else {
                if (isConnected) {

                    val obj = JSONObject()
                    challengeStarted = !challengeStarted
                    // Werte setzen
                    if (challengeStarted) {
                        obj.put("CHALLENGE", "START")
                        obj.put("TIMECHALLENGE", time)
                        obj.put("STARTMESSUNG", "AUS")
                        obj.put("HOUR", hour)
                        obj.put("MINUTE", minute)
                        binding.buttonStartChallenge.text = getString(R.string.btn_stop_challenge)
                        toast("Start")
                    } else {
                        obj.put("CHALLENGE", "STOPP")
                        binding.buttonStartChallenge.text = getString(R.string.btn_start_challenge)
                    }

                    // Senden
                    if (gattCharacteristic != null) {
                        gattCharacteristic!!.value = obj.toString().toByteArray()
                        bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
                    }
                } else {
                    toast("Sensor ist nicht verbunden")
                }
            }
        }

        if(viewModel.getSavedDataChallenge()) {
            loadDbData()
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
        toast("connected")
    }

    private fun onDisconnect() {
        isConnected = false
        Log.i(ContentValues.TAG, "disconnected")
        toast("disconnected")
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

            statusChallenge = obj.getString("challenge").toString()

            toast(statusChallenge)

            if(statusChallenge == "geschafft") {

                context?.let {
                    MaterialAlertDialogBuilder(it)
                        .setTitle(resources.getString(R.string.title_alert_challenge))
                        .setMessage(resources.getString(R.string.message_alert_dialog_challenge))

                        .setPositiveButton(resources.getString(R.string.accept)) { dialog, which ->
                            counterChallenge++
                            insertDataInDb()
                        }
                        .show()
                }
            }

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

    private fun insertDataInDb() {

        viewModel.setSavedDataChallenge(true)

        val kalender: Calendar = Calendar.getInstance()
        val zeitformat = SimpleDateFormat("yyyy-MM-dd")
        val date = zeitformat.format(kalender.time)

        //Objekt mit Daten befüllen (ID wird automatisch ergänzt)
        val userData = UserDataExercise()
        userData.setChallenge(counterChallenge)

        // Schreibe Daten als Document in die Collection Messungen in DB;
        // Eine id als Document Name wird automatisch vergeben
        // Implementiere auch onSuccess und onFailure Listender
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

    fun loadDbData() {

        val kalender: Calendar = Calendar.getInstance()
        val zeitformat = SimpleDateFormat("yyyy-MM-dd")
        val date = zeitformat.format(kalender.time)

        // Einstiegspunkt für die Abfrage ist users/uid/Messungen
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection(date).document("Challenge")// alle Einträge abrufen
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    data = task.result!!.toObject(UserDataExercise::class.java)

                    counterChallenge = data!!.getChallenge()

                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }
    }

}