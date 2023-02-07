package com.example.moveup

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.BluetoothLeScanner
import android.content.*
import android.content.ContentValues.TAG
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.moveup.databinding.FragmentGraphBinding
import com.github.aachartmodel.aainfographics.aachartcreator.AAChartModel
import com.github.aachartmodel.aainfographics.aachartcreator.AAChartStackingType
import com.github.aachartmodel.aainfographics.aachartcreator.AAChartType
import com.github.aachartmodel.aainfographics.aachartcreator.AASeriesElement
import com.github.aachartmodel.aainfographics.aaoptionsmodel.AAScrollablePlotArea
import com.github.aachartmodel.aainfographics.aaoptionsmodel.AAStyle
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
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
    private var data: UserData? = null
    private var dataExercise: UserDataExercise? = null
    private var isReceivingData = false

    //Circular-Progress-Bar
    private var timeMaxProgressBar = 60F
    private var progressTime: Float = 0F

    private val mHandler: Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable

    private var aaChartModel = AAChartModel()
    private var arrayBent = arrayOfNulls<Any>(24)
    private var arrayLeanBack = arrayOfNulls<Any>(24)
    private var arrayDynamic = arrayOfNulls<Any>(24)
    private var arrayStraight = arrayOfNulls<Any>(24)
    private var arrayChallenge = arrayOfNulls<Any>(24)
    private var arrayMovementBreak = arrayOfNulls<Any>(24)
    private var arrayBentList = arrayListOf<Any?>()
    private var arrayStraightList = arrayListOf<Any?>()
    private var arrayLeanList = arrayListOf<Any?>()
    private var arrayDynamicList = arrayListOf<Any?>()
    private var arrayChallengeDb = arrayListOf<Any?>()
    private var arrayMovementBreakDb = arrayListOf<Any?>()


    //Datenbank
    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var date = ""

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
                    bluetoothLeService!!.connect(viewModel.getDeviceAddress())
                }
            }
        }
        mHandler.postDelayed(mRunnable, 1000)

        binding.buttonData.setOnClickListener {
            if (isConnected) {
                if (isReceivingData) {
                    bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, false)
                    isReceivingData = false
                    binding.buttonData.text = getString(R.string.btn_data_graph)
                } else {
                    bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, true)
                    isReceivingData = true
                    binding.buttonData.text = getString(R.string.bt_data_off)
                    //Sensor nach 1s verbinden, wenn deviceAddress bekannt ist
                    /*mRunnable = Runnable {
                        insertDataInDb()
                    }
                    mHandler.postDelayed(mRunnable, 3000)*/

                    mRunnable = Runnable {
                        insertDataInDb()
                        bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, false)
                        isReceivingData = false
                        binding.buttonData.text = getString(R.string.btn_data_graph)
                    }
                    mHandler.postDelayed(mRunnable, 10000)

                }
            } else {
                toast("Verbinde zunächst den Sensor!")
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

        binding.buttonSetGoal.setOnClickListener {
            showDialogEditTime()
        }

        binding.buttonSelectDate.setOnClickListener {
            // Makes only dates from today forward selectable.
            val constraintsBuilder =
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointBackward.now())

            val datePicker =
                MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select date")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .setCalendarConstraints(constraintsBuilder.build())
                    .setTitleText("Wähle ein Datum aus")
                    .build()

            datePicker.show(parentFragmentManager, "tag")

            datePicker.addOnPositiveButtonClickListener {
                // Respond to positive button click.

                val calendar = Calendar.getInstance()
                calendar.timeInMillis = datePicker.selection!!
                val format = SimpleDateFormat("yyyy-MM-dd")
                date = format.format(calendar.time)

                binding.buttonSelectDate.text = date

                loadDbData(date)
                loadDbExerciseData(date)
            }
            datePicker.addOnNegativeButtonClickListener {
                // Respond to negative button click.
            }

        }


        for (i in 0 until 24) {
            arrayBent[i] = 0
        }
        for (i in 0 until 24) {
            arrayLeanBack[i] = 0
        }
        for (i in 0 until 24) {
            arrayDynamic[i] = 0
        }
        for (i in 0 until 24) {
            arrayChallenge[i] = 0
        }
        for (i in 0 until 24) {
            arrayMovementBreak[i] = 0
        }
        for (i in 0 until 24) {
            arrayStraight[i] = 0
        }

        setUpAAChartView()

        val kalender: Calendar = Calendar.getInstance()
        val zeitformat = SimpleDateFormat("yyyy-MM-dd")
        val date = zeitformat.format(kalender.time)

        loadDbData(date)
        loadDbExerciseData(date)
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
            .setTitle(getString(R.string.chart_title))
            .setColorsTheme(
                arrayOf(
                    "#ce93d8",
                    "#536dfe",
                    "#7e57c2",
                    "#d3b8ae",
                    "#CDBCCB",
                    "#BC1A53"
                )
            )
            .setStacking(AAChartStackingType.Normal)
            .setTitleStyle(AAStyle.Companion.style("#FFFFFF"))
            .setBackgroundColor("#682842")
            .setAxesTextColor("#FFFFFF")
            .setCategories(
                "00:00",
                "01:00",
                "02:00",
                "03:00",
                "04:00",
                "05:00",
                "06:00",
                "07:00",
                "08:00",
                "09:00",
                "10:00",
                "11:00",
                "12:00",
                "13:00",
                "14:00",
                "15:00",
                "16:00",
                "17:00",
                "18:00",
                "19:00",
                "20:00",
                "21:00",
                "22:00",
                "23:00",
                "24:00"
            )
            .setYAxisTitle("Minuten")
            .setScrollablePlotArea(
                AAScrollablePlotArea()
                    .opacity(0F)
                    .minWidth(400)
                    .scrollPositionX(20f)
            )
            .build()
    }


    private fun configureChartSeriesArray(): Array<AASeriesElement> {

        return arrayOf(
            AASeriesElement()
                .name("Aufrechte Phase")
                .data(arrayStraight as Array<Any>),
            AASeriesElement()
                .name("zurückgelehnte Phase")
                .data(arrayLeanBack as Array<Any>),
            AASeriesElement()
                .name("ungerade Haltung")
                .data(arrayBent as Array<Any>),
            AASeriesElement()
                .name("dynamische Phase")
                .data(arrayDynamic as Array<Any>),
            AASeriesElement()
                .name("Challenges")
                .data(arrayChallenge as Array<Any>),
            AASeriesElement()
                .name("Bewegungspausen")
                .data(arrayMovementBreak as Array<Any>),
        )
    }

    private fun configureChartSeriesArrayExercise(): Array<AASeriesElement> {
        for (i in 0 until 24) {
            arrayChallenge[i] = 0
        }
        for (i in 0 until 24) {
            arrayMovementBreak[i] = 0
        }
        return arrayOf(
            AASeriesElement()
                .name("Challenges")
                .data(arrayChallenge as Array<Any>),
            AASeriesElement()
                .name("Bewegungspausen")
                .data(arrayMovementBreak as Array<Any>),
        )
    }

    private fun configureChartSeriesArrayAfterLoadDb(): Array<AASeriesElement> {

        for (i in 0 until 24) {
            arrayBent[i] = 0
        }
        for (i in 0 until 24) {
            arrayLeanBack[i] = 0
        }
        for (i in 0 until 24) {
            arrayDynamic[i] = 0
        }
        for (i in 0 until 24) {
            arrayStraight[i] = 0
        }



        return arrayOf(
            AASeriesElement()
                .name("Aufrechte Haltung")
                .data(arrayStraight as Array<Any>),
            AASeriesElement()
                .name("zurückgelehnte Phase")
                .data(arrayLeanBack as Array<Any>),
            AASeriesElement()
                .name("ungerade Haltung")
                .data(arrayBent as Array<Any>),
            AASeriesElement()
                .name("dynamische Phase")
                .data(arrayDynamic as Array<Any>),
            AASeriesElement()
                .name("Challenges")
                .data(arrayChallenge as Array<Any>),
            AASeriesElement()
                .name("Bewegungspausen")
                .data(arrayMovementBreak as Array<Any>),
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
                Log.e(TAG, "Unable to initialize Bluetooth")

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
        toast("connected")
    }

    private fun onDisconnect() {
        isConnected = false
        toast("disconnected")
    }

    private fun onGattCharacteristicDiscovered() {
        gattCharacteristic = bluetoothLeService?.getGattCharacteristic()
    }

    private fun onDataAvailable() {
        // neue Daten verfügbar
        Log.i(TAG, "Data available")
        val bytes: ByteArray = gattCharacteristic!!.value
        // byte[] to string
        val s = String(bytes)
        parseJSONData(s)
    }

    private fun parseJSONData(jsonString: String) {
        try {
            val obj = JSONObject(jsonString)
            //extrahieren des Objektes data
            toast("Daten empfangen")
            statusPos = obj.getString("statusPosition").toString()

            counterReminder = obj.getString("counterReminder").toInt()

            counterLeanBack = obj.getString("counterLeanBack").toInt()

            //val progress = obj.getString("sittingStraightTime").toFloat()


            /*if (progress != progressTimeBefore) {

                progressTimeBefore = progress
            }*/

            val listdataBent = ArrayList<String>()
            val jArrayBent = obj.getJSONArray("arrayBentBack")
            if (jArrayBent != null) {
                for (i in 0 until jArrayBent.length()) {
                    listdataBent.add(jArrayBent.getString(i))
                }
            }

            // (&& listdataBent[i].toInt() != val1[i]) ausschnitt aus if schleife
            for (i in 0 until 24) {
                if (listdataBent[i].toInt() != 0) {
                    arrayBent[i] = listdataBent[i].toInt()/2
                    //arrayBent[i] = arrayBent[i].toString().toInt() + (listdataBent[i].toInt() / 2) - (val1[i] ?: 0)
                    //val1[i] = listdataBent[i].toInt() / 2
                }
            }

            val listdataLean = ArrayList<String>()
            val jArrayLean = obj.getJSONArray("arrayLeanBack")
            if (jArrayLean != null) {
                for (i in 0 until jArrayLean.length()) {
                    listdataLean.add(jArrayLean.getString(i))
                }
            }

            for (i in 0 until 24) {
                if (listdataLean[i].toInt() != 0) {
                    arrayLeanBack[i] = listdataLean[i].toInt()
                    //arrayLeanBack[i] = arrayLeanBack[i].toString().toInt() + listdataLean[i].toInt() - (val2[i] ?: 0)
                    //val2[i] = listdataLean[i].toInt()
                }
            }

            val listdataDynamic = ArrayList<String>()
            val jArrayDynamic = obj.getJSONArray("arrayCounterDynamic")
            if (jArrayDynamic != null) {
                for (i in 0 until jArrayDynamic.length()) {
                    listdataDynamic.add(jArrayDynamic.getString(i))
                }
            }

            for (i in 0 until 24) {
                if (listdataDynamic[i].toInt() != 0) {
                    arrayDynamic[i] = listdataDynamic[i].toInt()
                    //arrayDynamic[i] = arrayDynamic[i].toString().toInt() + listdataDynamic[i].toInt() - (val3[i] ?: 0)
                    //val3[i] = listdataDynamic[i].toInt()
                }
            }


            val listdataUpright = ArrayList<String>()
            val jArrayUpright = obj.getJSONArray("arraySittingStraight")
            if (jArrayUpright != null) {
                for (i in 0 until jArrayUpright.length()) {
                    listdataUpright.add(jArrayUpright.getString(i))
                }
            }

            for (i in 0 until 24) {
                if (listdataUpright[i].toInt() != 0) {
                    arrayStraight[i] = listdataUpright[i].toInt()
                    // arrayStraight[i] = arrayStraight[i].toString().toInt() + listdataUpright[i].toInt() - (val4[i] ?: 0)
                    //val4[i] = listdataUpright[i].toInt()
                }
            }
            progressTime = 0F

            for (i in 0 until 24) {
                progressTime += arrayStraight[i].toString().toInt()
            }


            binding.textViewNumberReminder.text = getString(R.string.tv_reminder, counterReminder)

            val seriesArr = configureChartSeriesArray()
            binding.chartView.aa_onlyRefreshTheChartDataWithChartOptionsSeriesArray(seriesArr)

            binding.circularProgressBar.progress = progressTime

            binding.circularProgressBar.apply {
                binding.textViewTime.text = getString(R.string.tv_time, progressTime, progressMax)
            }

            showDialog()



        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun showDialog() {
        if (statusPos == "krumm") {

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

    private fun showDialogEditTime() {
        //AlertDialog mit Text-Eingabe-Feld
        val editTextView = EditText(context)

        context?.let {
            MaterialAlertDialogBuilder(it)
                .setTitle(R.string.dialog_set_time_title)
                .setView(editTextView)
                .setNeutralButton(R.string.dialog_cancel) { dialog, which ->
                }
                .setPositiveButton(R.string.dialog_OK) { dialog, which ->
                    if (editTextView.text.toString().isEmpty()) {
                    } else {
                        timeMaxProgressBar = editTextView.text.toString().toFloat()
                        binding.circularProgressBar.apply {
                            progressMax = timeMaxProgressBar
                            binding.textViewTime.text =
                                getString(R.string.tv_time, progressTime, timeMaxProgressBar)
                        }
                        insertDataInDb()
                    }
                }
                .show()
        }
    }

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
            Log.d(TAG, "Connect request result=" + result)
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

        val kalender: Calendar = Calendar.getInstance()
        val zeitformat = SimpleDateFormat("yyyy-MM-dd")
        val date = zeitformat.format(kalender.time)

        for (i in 0 until 24) {
            arrayBentList.add(i, arrayBent[i].toString().toInt() / 2)
        }

        for (i in 0 until 24) {
            arrayLeanList.add(i, arrayLeanBack[i])
        }

        for (i in 0 until 24) {
            arrayDynamicList.add(i, arrayDynamic[i])
        }

        for (i in 0 until 24) {
            arrayStraightList.add(i, arrayStraight[i])
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
        arrayBentList.clear()
        arrayLeanList.clear()
        arrayDynamicList.clear()
        arrayStraightList.clear()
    }

    private fun loadDbData(date: String) {

        // Einstiegspunkt für die Abfrage ist users/uid/Messungen
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection(date)
            .document("Daten")// alle Einträge abrufen
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

                        for (i in 0 until 24) {
                            arrayBent[i] = arrayBentList[i]
                        }

                        for (i in 0 until 24) {
                            arrayLeanBack[i] = arrayLeanList[i]
                        }

                        for (i in 0 until 24) {
                            arrayDynamic[i] = arrayDynamicList[i]
                        }

                        for (i in 0 until 24) {
                            arrayStraight[i] = arrayStraightList[i]
                        }

                        mRunnable = Runnable {
                            val seriesArr = configureChartSeriesArray()
                            binding.chartView.aa_onlyRefreshTheChartDataWithChartOptionsSeriesArray(seriesArr)
                        }
                        mHandler.postDelayed(mRunnable, 1000)


                        arrayBentList.clear()
                        arrayLeanList.clear()
                        arrayDynamicList.clear()
                        arrayStraightList.clear()

                        binding.circularProgressBar.apply {
                            progressMax = timeMaxProgressBar
                            binding.textViewTime.text = getString(R.string.tv_time, progressTime, timeMaxProgressBar)
                        }
                        binding.circularProgressBar.progress = progressTime

                    } else {
                        val seriesArr = configureChartSeriesArrayAfterLoadDb()
                        binding.chartView.aa_onlyRefreshTheChartDataWithChartOptionsSeriesArray(
                            seriesArr
                        )
                        binding.circularProgressBar.apply {
                            progressMax = 0F
                            binding.textViewTime.text = getString(R.string.tv_time, 0F, 0F)
                        }
                        binding.circularProgressBar.progress = 0F
                    }

                } else {
                    Log.d(TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }
    }

    private fun loadDbExerciseData(date: String) {

        // Einstiegspunkt für die Abfrage ist users/uid/Messungen
        val uid = mFirebaseAuth.currentUser!!.uid
        //Daten zu Challenges und Bewegungspausen einlesen
        db.collection("users").document(uid).collection(date).document("Challenge")// alle Einträge abrufen
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    dataExercise = task.result!!.toObject(UserDataExercise::class.java)

                    if (dataExercise != null) {
                        arrayChallengeDb = dataExercise!!.getChallengeArray()
                        arrayMovementBreakDb = dataExercise!!.getMovementBreakArray()

                        for (i in 0 until 24) {
                            arrayChallenge[i] = arrayChallengeDb[i]
                        }

                        for (i in 0 until 24) {
                            arrayMovementBreak[i] = arrayMovementBreakDb[i]
                        }

                        val seriesArr = configureChartSeriesArray()
                        binding.chartView.aa_onlyRefreshTheChartDataWithChartOptionsSeriesArray(seriesArr)

                    } else {
                        val seriesArr = configureChartSeriesArrayExercise()
                        binding.chartView.aa_onlyRefreshTheChartDataWithChartOptionsSeriesArray(
                            seriesArr
                        )
                    }

                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }

    }
}