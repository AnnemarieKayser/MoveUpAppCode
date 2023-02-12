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
import kotlin.math.roundToInt

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
    private var yAxisMax = 30

    //Circular-Progress-Bar
    private var timeMaxProgressBar = 60F
    private var progressTime: Float = 0F

    private val mHandler: Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable

    private var aaChartModel = AAChartModel()
    private var arrayBent = arrayOfNulls<Any>(48)
    private var arrayLeanBack = arrayOfNulls<Any>(48)
    private var arrayDynamic = arrayOfNulls<Any>(48)
    private var arrayStraight = arrayOfNulls<Any>(48)
    private var arrayChallenge = arrayOfNulls<Any>(48)
    private var arrayBentList = arrayListOf<Any?>()
    private var arrayStraightList = arrayListOf<Any?>()
    private var arrayLeanList = arrayListOf<Any?>()
    private var arrayDynamicList = arrayListOf<Any?>()
    private var arrayChallengeDb = arrayListOf<Any?>()
    private var arrayMovementBreakDb = arrayListOf<Any?>()
    private var arrayMovementBreak = arrayOfNulls<Any>(48)
    private var counterChallenge = 0
    private var counterMovement = 0


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
                bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, true)
                isReceivingData = true
                binding.buttonData.text = getString(R.string.bt_data_off)

                mRunnable = Runnable {
                    bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, false)
                    isReceivingData = false
                    binding.buttonData.text = getString(R.string.btn_data_graph)
                    insertDataInDb()
                }
                mHandler.postDelayed(mRunnable, 2000)

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
            progressBarWidth = 10f // in DP
            backgroundProgressBarWidth = 4f // in DP

            // Other
            roundBorder = true

            progressDirection = CircularProgressBar.ProgressDirection.TO_RIGHT
        }

        binding.circularProgressBar1.apply {
            // Set Progress Max
            progressMax = timeMaxProgressBar

            // Set ProgressBar Color
            progressBarColorStart = Color.GREEN
            progressBarColorEnd = Color.GREEN
            progressBarColorDirection = CircularProgressBar.GradientDirection.RIGHT_TO_LEFT

            // Set background ProgressBar Color
            backgroundProgressBarColor = Color.RED
            backgroundProgressBarColorDirection =
                CircularProgressBar.GradientDirection.TOP_TO_BOTTOM

            // Set Width
            progressBarWidth = 10f // in DP
            backgroundProgressBarWidth = 5f // in DP

            // Other
            //roundBorder = true

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
            arrayChallenge[i] = 0
        }
        for (i in 0 until 48) {
            arrayMovementBreak[i] = 0
        }
        for (i in 0 until 48) {
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
            .setCategories("00:00", "00:30", "01:00", "01:30", "02:00", "02:30", "03:00", "03:30", "04:00", "04:30", "05:00", "05:30",
                "06:00", "06:30", "07:00", "07:30", "08:00", "08:30", "09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "12:00",
                "12:30", "13:00", "13:30", "14:00", "14:30", "15:00", "15:30", "16:00", "16:30", "17:00", "17:30", "18:00",
                "18:30", "19:00", "19:30", "20:00", "20:30", "21:00", "21:30", "22:00", "22:30", "23:00", "23:30"
            )
            .setYAxisTitle("Minuten")
            .setYAxisMax(25f)
            .setScrollablePlotArea(
                AAScrollablePlotArea()
                    .opacity(0F)
                    .minWidth(600)
                    .scrollPositionX(1f)
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
        for (i in 0 until 48) {
            arrayChallenge[i] = 0
        }
        for (i in 0 until 48) {
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
            if (obj.has("statPos")) {
                statusPos = obj.getString("statPos").toString()
            }
            if (obj.has("bent")) {
                counterReminder = obj.getString("bent").toInt()
            }

            if (obj.has("lean")) {
                counterLeanBack = obj.getString("lean").toInt()
            }

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
                counterReminder = 0
                for (i in 0 until 48) {
                    counterReminder += arrayBent[i].toString().toInt()
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
                counterLeanBack = 0

                for (i in 0 until 48) {
                    counterLeanBack += arrayLeanBack[i].toString().toInt()
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


            binding.textViewNumberReminder.text = getString(R.string.tv_reminder, counterReminder)
            binding.textViewLeanBack.text = getString(R.string.tv_reminder_lean_back, counterLeanBack)

            binding.circularProgressBar1.apply {
                progressMax = counterReminder + progressTime
            }

            binding.circularProgressBar1.progress = progressTime

            val ratio = ((progressTime/(counterReminder + progressTime))*100).roundToInt()

            binding.textViewUprightBentStatus.text = getString(R.string.tv_upright_bent_status, ratio)


            val seriesArr = configureChartSeriesArray()
            binding.chartView.aa_onlyRefreshTheChartDataWithChartOptionsSeriesArray(seriesArr)

            if (progressTime < timeMaxProgressBar) {
                binding.circularProgressBar.progress = progressTime
                binding.textViewTime.text = getString(R.string.tv_time, progressTime, timeMaxProgressBar)
                binding.textViewProgressUpright.text = getString(R.string.tv_progress)

            } else {
                binding.circularProgressBar.progress = timeMaxProgressBar
                binding.textViewTime.text =
                    getString(R.string.tv_time, timeMaxProgressBar, timeMaxProgressBar)
                binding.textViewProgressUpright.text = getString(R.string.tv_progress_done)
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
                        }
                        if(progressTime < timeMaxProgressBar) {
                            binding.textViewTime.text = getString(R.string.tv_time, progressTime, timeMaxProgressBar)
                            binding.textViewProgressUpright.text = getString(R.string.tv_progress)

                        } else{
                            binding.circularProgressBar.progress = timeMaxProgressBar
                            binding.textViewProgressUpright.text = getString(R.string.tv_progress_done)
                            binding.textViewTime.text = getString(R.string.tv_time, timeMaxProgressBar, timeMaxProgressBar)
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
        mHandler.removeCallbacksAndMessages(null)
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

        for (i in 0 until 48) {
            arrayBentList.add(i, arrayBent[i])
        }

        for (i in 0 until 48) {
            arrayLeanList.add(i, arrayLeanBack[i])
        }

        for (i in 0 until 48) {
            arrayDynamicList.add(i, arrayDynamic[i])
        }

        for (i in 0 until 48) {
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

                        arrayBentList.clear()
                        arrayLeanList.clear()
                        arrayDynamicList.clear()
                        arrayStraightList.clear()

                        counterReminder = 0
                        counterLeanBack = 0
                        for (i in 0 until 48) {
                            counterReminder += arrayBent[i].toString().toInt()
                        }

                        for (i in 0 until 48) {
                            counterLeanBack += arrayLeanBack[i].toString().toInt()
                        }


                        binding.textViewNumberReminder.text = getString(R.string.tv_reminder, counterReminder)
                        binding.textViewLeanBack.text = getString(R.string.tv_reminder_lean_back, counterLeanBack)

                        binding.circularProgressBar1.apply {
                            progressMax = counterReminder + progressTime
                        }

                        binding.circularProgressBar1.progress = progressTime

                        val ratio = ((progressTime/(counterReminder + progressTime))*100).roundToInt()

                        binding.textViewUprightBentStatus.text = getString(R.string.tv_upright_bent_status, ratio)



                        if (progressTime < timeMaxProgressBar) {
                            binding.circularProgressBar.apply {
                                progressMax = timeMaxProgressBar
                            }
                            binding.textViewTime.text = getString(R.string.tv_time, progressTime, timeMaxProgressBar)
                            binding.textViewProgressUpright.text = getString(R.string.tv_progress)
                            binding.circularProgressBar.progress = progressTime
                        } else {
                            binding.circularProgressBar.progress = timeMaxProgressBar
                            binding.textViewTime.text = getString(R.string.tv_time, timeMaxProgressBar, timeMaxProgressBar)
                            binding.textViewProgressUpright.text = getString(R.string.tv_progress_done)
                        }

                    } else {
                        binding.textViewNumberReminder.text = getString(R.string.tv_reminder, 0)
                        binding.textViewLeanBack.text = getString(R.string.tv_reminder_lean_back, 0)
                        val seriesArr = configureChartSeriesArrayAfterLoadDb()
                        binding.chartView.aa_onlyRefreshTheChartDataWithChartOptionsSeriesArray(seriesArr)
                        binding.textViewProgressUpright.text = getString(R.string.tv_progress)
                        binding.circularProgressBar.apply {
                            progressMax = 0F
                            binding.textViewTime.text = getString(R.string.tv_time, 0F, 0F)
                        }
                        binding.circularProgressBar.progress = 0F
                    }
                    val seriesArr = configureChartSeriesArray()
                    binding.chartView.aa_onlyRefreshTheChartDataWithChartOptionsSeriesArray(seriesArr)

                } else {
                    Log.d(TAG, "FEHLER: Daten lesen ", task.exception)
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
                        arrayChallengeDb = dataExercise!!.getChallengeArray()
                        arrayMovementBreakDb = dataExercise!!.getMovementBreakArray()
                        counterChallenge = dataExercise!!.getChallenge()
                        counterMovement = dataExercise!!.getMovementBreak()

                        for (i in 0 until 48) {
                            arrayChallenge[i] = arrayChallengeDb[i]
                        }

                        for (i in 0 until 48) {
                            arrayMovementBreak[i] = arrayMovementBreakDb[i]
                        }

                        binding.textViewChallengeGraph.text = getString(R.string.tv_counter_challenge, counterChallenge)
                        binding.textViewMovementGraph.text = getString(R.string.tv_counter_movement, counterMovement)
                    }else{
                        val seriesArr = configureChartSeriesArrayExercise()
                        binding.chartView.aa_onlyRefreshTheChartDataWithChartOptionsSeriesArray(seriesArr)
                        binding.textViewChallengeGraph.text = getString(R.string.tv_counter_challenge, 0)
                        binding.textViewMovementGraph.text = getString(R.string.tv_counter_movement, 0)
                    }

                    val seriesArr = configureChartSeriesArray()
                    binding.chartView.aa_onlyRefreshTheChartDataWithChartOptionsSeriesArray(seriesArr)

                } else {
                    Log.d(ContentValues.TAG, "FEHLER: Daten lesen ", task.exception)
                }
            }
    }

    private fun loadDbExerciseData(date: String) {

    }
}