package com.example.moveup

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.BluetoothLeScanner
import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.moveup.databinding.FragmentSettingBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONException
import org.json.JSONObject
import splitties.toast.toast
import java.text.SimpleDateFormat
import java.util.*

class SettingFragment : Fragment() {

    private var _binding: FragmentSettingBinding? = null
    private val viewModel: BasicViewModel by activityViewModels()
    private val binding get() = _binding!!

    //Ble
    private lateinit var scanner: BluetoothLeScanner
    private lateinit var mBluetooth: BluetoothAdapter
    private var isConnected = false
    private var bluetoothLeService: BluetoothLeService? = null
    private var gattCharacteristic: BluetoothGattCharacteristic? = null
    private var statusVibration = "VIBON"
    private var vibrationLength = 1000


    private val mHandler: Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable

    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var data: UserDataSetting? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentSettingBinding.inflate(inflater, container, false)
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

        binding.buttonLogOut.setOnClickListener {
            mFirebaseAuth.signOut()
            val intent = Intent(getActivity(), LoginInActivity::class.java)
            getActivity()?.startActivity(intent)
        }

        binding.buttonConfig.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_setting_to_configFragment)
        }

        loadDbData()


        // To listen for a switch's checked/unchecked state changes
        binding.switchVibration.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                toast(getString(R.string.vibration_on))

                statusVibration = "VIBON"

                val obj = JSONObject()
                // Werte setzen
                obj.put("VIBRATION", statusVibration)
                obj.put("VIBLENGTH", vibrationLength)
                // Senden
                if (gattCharacteristic != null) {
                    gattCharacteristic!!.value = obj.toString().toByteArray()
                    bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
                }
            } else {
                toast(getString(R.string.vibration_off))

                statusVibration = "VIBOFF"

                val obj = JSONObject()
                // Werte setzen
                obj.put("VIBRATION", statusVibration)

                // Senden
                if (gattCharacteristic != null) {
                    gattCharacteristic!!.value = obj.toString().toByteArray()
                    bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
                }
            }
            insertDataInDb()
        }

        binding.toggleButton.addOnButtonCheckedListener { toggleButton, checkedId, isChecked ->
            val obj = JSONObject()

            if (isChecked) {
                //Check which radio button is selected
                when (checkedId) {
                    R.id.buttonVibShort -> {
                        vibrationLength = 500
                        //Sending the selected mode to the ESP via BLE
                        obj.put("VIBLENGTH", vibrationLength)

                        // send
                        if (gattCharacteristic != null) {
                            gattCharacteristic!!.value = obj.toString().toByteArray()
                            bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
                        }
                    }

                    R.id.buttonVibMedium -> {

                        vibrationLength = 1000

                        //Sending the selected mode to the ESP via BLE
                        obj.put("VIBLENGTH", vibrationLength)

                        // send
                        if (gattCharacteristic != null) {
                            gattCharacteristic!!.value = obj.toString().toByteArray()
                            bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
                        }
                    }

                    R.id.buttonVibLong -> {

                        vibrationLength = 2000

                        obj.put("VIBLENGTH", vibrationLength)

                        // send
                        if (gattCharacteristic != null) {
                            gattCharacteristic!!.value = obj.toString().toByteArray()
                            bluetoothLeService!!.writeCharacteristic(gattCharacteristic)
                        }
                    }
                }
            }
            insertDataInDb()
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
    }

    private fun onDisconnect() {
        isConnected = false
        //binding.textViewConnected.setText(R.string.disconnected)
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

    private fun insertDataInDb() {

        //Objekt mit Daten befüllen (ID wird automatisch ergänzt)
        val userData = UserDataSetting()
        userData.setVibration(statusVibration)
        userData.setVibrationLength(vibrationLength)

        // Schreibe Daten als Document in die Collection Messungen in DB;
        // Eine id als Document Name wird automatisch vergeben
        // Implementiere auch onSuccess und onFailure Listender
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

    fun loadDbData() {

        // Einstiegspunkt für die Abfrage ist users/uid/Messungen
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection("Einstellungen").document("Vibration")// alle Einträge abrufen
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    data = task.result!!.toObject(UserDataSetting::class.java)

                    if(data != null) {
                        vibrationLength = data!!.getVibrationLength()
                        statusVibration = data!!.getVibration()

                        binding.switchVibration.isChecked = statusVibration == "VIBON"

                        if(vibrationLength == 500){
                            binding.toggleButton.check(R.id.buttonVibShort)
                        }
                        if(vibrationLength == 1000){
                            binding.toggleButton.check(R.id.buttonVibMedium)
                        }
                        if(vibrationLength == 2000){
                            binding.toggleButton.check(R.id.buttonVibLong)
                        }

                    }

                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }
    }

}