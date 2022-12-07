package com.example.moveup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.BluetoothLeScanner
import android.content.*
import android.content.ContentValues.TAG
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.moveup.databinding.FragmentGraphBinding
import com.github.aachartmodel.aainfographics.aachartcreator.*
import com.github.aachartmodel.aainfographics.aaoptionsmodel.AAScrollablePlotArea
import com.github.aachartmodel.aainfographics.aaoptionsmodel.AAStyle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONException
import org.json.JSONObject
import splitties.toast.toast
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class GraphFragment : Fragment() {

    private var _binding: FragmentGraphBinding? = null
    private val viewModel: BasicViewModel by activityViewModels()
    private val binding get() = _binding!!

    //Ble
    private lateinit var scanner: BluetoothLeScanner
    private lateinit var mBluetooth: BluetoothAdapter
    private var isConnected = false
    private var bluetoothLeService: BluetoothLeService? = null
    private var gattCharacteristic: BluetoothGattCharacteristic? = null
    private var statusPos = "Haltung gerade"
    private var counterReminder = 0
    private var counterLeanBack = 0
    private var counterReminderBefore = 0
    private var counterLeanBackBefore = 0
    private var time = 0
    private var data: UserData? = null

    private val mHandler : Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable

    private var aaChartModel = AAChartModel()
    private var arrayBent = arrayOfNulls<Any>(24)
    private var arrayLeanBack = arrayOfNulls<Any>(24)
    private var arrayDynamic = arrayOfNulls<Any>(24)
    private var arrayStraight = arrayOfNulls<Any>(24)

    //Datenbank
    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db : FirebaseFirestore by lazy { FirebaseFirestore.getInstance()  }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentGraphBinding.inflate(inflater, container, false)
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
            if (isAdded() && activity != null) {
                if (viewModel.getDeviceAddress() != "") {
                    bluetoothLeService!!.connect(viewModel.getDeviceAddress());
                    mRunnable = Runnable {
                        if (isConnected) {
                            bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, true);
                        }
                    }
                    mHandler.postDelayed(mRunnable, 1000)
                }
            }
        }
        mHandler.postDelayed(mRunnable, 1000)

        setUpAAChartView()

        //binding.progressBar.indicatorInset = 100
        binding.progressBar.progress = 100

        //loadDbData()


    }

    fun setUpAAChartView() {
        aaChartModel = configureAAChartModel()
        binding.chartView.aa_drawChartWithChartModel(aaChartModel)
    }

    private fun configureAAChartModel(): AAChartModel {
        val aaChartModel = configureChartBasicContent()
        aaChartModel.series(this.configureChartSeriesArray() as Array<Any>)
        return aaChartModel
    }

    private fun configureChartBasicContent(): AAChartModel {
        return AAChartModel.Builder(requireContext().applicationContext)
            .setChartType(AAChartType.Column)
            .setXAxisVisible(true)
            .setYAxisVisible(true)
            .setTitle(getString(R.string.chart_title))
            .setColorsTheme(arrayOf("#ce93d8", "#536dfe", "#7e57c2", "#81d4fa"))
            .setStacking(AAChartStackingType.Percent)
            .setTitleStyle(AAStyle.Companion.style("#FFFFFF"))
            .setAxesTextColor("#FFFFFF")
            .setBackgroundColor("#435359")
            .setCategories("00:00", "1:00", "2:00", "3:00", "4:00", "5:00", "6:00", "7:00", "8:00", "9:00",
                "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00" ,"18:00",
                "19:00", "20:00", "21:00", "22:00", "23:00", "24:00")
            .setScrollablePlotArea(
                AAScrollablePlotArea()
                    .minWidth(400)
                    .scrollPositionX(1f))
            .build()
    }


    private fun configureChartSeriesArray(): Array<AASeriesElement> {

        val kalender: Calendar = Calendar.getInstance()
        val zeitformat = SimpleDateFormat("HH")
        val hour = zeitformat.format(kalender.time)
        time = hour.toInt()

        arrayBent[time] = counterReminder
        arrayLeanBack[time] = counterLeanBack

        if(counterReminder != counterReminderBefore || counterLeanBack != counterLeanBackBefore) {
            insertDataInDb()
            counterReminderBefore = counterReminder
            counterLeanBackBefore = counterLeanBack
        }
        return arrayOf(
            AASeriesElement()
                .name("Aufrecht")
                .data(arrayStraight as Array<Any>),
            AASeriesElement()
                .name("zurückgelehnt")
                .data(arrayLeanBack as Array<Any>),
            AASeriesElement()
                .name("krumm")
                .data(arrayBent as Array<Any>),
            AASeriesElement()
                .name("dynamisch")
                .data(arrayDynamic as Array<Any>),
        )
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
        //binding.textViewConnected.setText(R.string.connected)
        Log.i(ContentValues.TAG, "connected")
        toast("connected")
    }

    private fun onDisconnect() {
        isConnected = false
        //binding.textViewConnected.setText(R.string.disconnected)
        Log.i(ContentValues.TAG, "disconnected")
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

    private fun parseJSONData(jsonString : String) {
        try {
            val obj = JSONObject(jsonString)
            //extrahieren des Objektes data

            statusPos = obj.getString("statusPosition").toString()

            counterReminder = obj.getString("counterReminder").toInt()

            counterLeanBack = obj.getString("counterLeanBack").toInt()

            binding.textViewNumberReminder.text = getString(R.string.tv_reminder, counterReminder)

            val seriesArr = configureChartSeriesArray()
            binding.chartView.aa_onlyRefreshTheChartDataWithChartOptionsSeriesArray(seriesArr)

            showDialog()

        } catch (e : JSONException) {
            e.printStackTrace()
        }
    }

    private fun showDialog() {
        if(statusPos == "Die Haltung ist krumm") {

            context?.let {
                MaterialAlertDialogBuilder(it)
                    .setTitle(resources.getString(R.string.title_alert_dialog))
                    .setMessage(resources.getString(R.string.message_alert_dialog))

                    .setPositiveButton(resources.getString(R.string.accept)) { dialog, which ->

                    }
                    .show()
            }
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
        if(isConnected) {
            bluetoothLeService!!.disconnect()
        }
    }

    private fun insertDataInDb() {

        // Weather Objekt mit Daten befüllen (ID wird automatisch ergänzt)
        val userData = UserData()
        userData.setHour(time)
        userData.setCounterBentBack(counterReminder)
        userData.setCounterLeanBack(counterLeanBack)

        // Schreibe Daten als Document in die Collection Messungen in DB;
        // Eine id als Document Name wird automatisch vergeben
        // Implementiere auch onSuccess und onFailure Listender
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection("Daten").document("1")
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
        db.collection("users").document(uid).collection("Messungen").document("1") // alle Einträge abrufen
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    data = task.result!!.toObject(UserData::class.java)
                    // Frage anzeigen
                    counterReminder = data!!.getCounterBentBack()
                    counterLeanBack = data!!.getCounterLeanBack()
                    time = data!!.getHour()

                    val seriesArr = configureChartSeriesArray()
                    binding.chartView.aa_onlyRefreshTheChartDataWithChartOptionsSeriesArray(seriesArr)

                } else {
                    Log.d(TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }
    }
}