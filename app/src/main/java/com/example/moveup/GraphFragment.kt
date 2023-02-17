package com.example.moveup

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
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

class GraphFragment : Fragment() {

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

  In diesem Fragment werden die Daten zum Sitzverhalten ausgewertet und dargestellt
        - In einem Graphen werden Daten zu der Rückenhaltung und zum dynamischen/statischen Sitzverhalten
          angezeigt
        - Es kann eine Zielzeit angegeben werden, die am Tag gerade gesessen werden möchte
        - der Fortschritt wird in einer ProgressBar angezeigt
        - Das Verhältnis von gerader zu ungerader Haltung wird in einer ProgressBar angezeigt

*/

/*
  =============================================================
  =======                   Variables                   =======
  =============================================================
*/


    private var _binding: FragmentGraphBinding? = null
    private val viewModel: BasicViewModel by activityViewModels()
    private val binding get() = _binding!!

    // === Bluetooth Low Energy === //
    private lateinit var mBluetooth: BluetoothAdapter
    private var isConnected = false
    private var bluetoothLeService: BluetoothLeService? = null
    private var gattCharacteristic: BluetoothGattCharacteristic? = null
    private var isReceivingData = false

    // === Circular-Progress-Bar === //
    private var timeMaxProgressBar = 60F
    private var progressTime: Float = 0F

    // === Handler-Runnable-Konstrukt === //
    private val mHandler: Handler by lazy { Handler() }
    private lateinit var mRunnable: Runnable

    // === Graph === //
    private var aaChartModel = AAChartModel()
    private var arrayBent = arrayOfNulls<Any>(48)
    private var arrayLeanBack = arrayOfNulls<Any>(48)
    private var arrayDynamic = arrayOfNulls<Any>(48)
    private var arrayStraight = arrayOfNulls<Any>(48)
    private var arrayChallenge = arrayOfNulls<Any>(48)
    private var arrayMovementBreak = arrayOfNulls<Any>(48)

    // === Counter === //
    private var counterReminder = 0
    private var counterLeanBack = 0
    private var counterChallenge = 0
    private var counterMovement = 0

    // === Datenbank === //
    private val mFirebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var data: UserData? = null
    private var dataExercise: UserDataExercise? = null
    private var arrayBentList = arrayListOf<Any?>()
    private var arrayStraightList = arrayListOf<Any?>()
    private var arrayLeanList = arrayListOf<Any?>()
    private var arrayDynamicList = arrayListOf<Any?>()
    private var arrayChallengeDb = arrayListOf<Any?>()
    private var arrayMovementBreakDb = arrayListOf<Any?>()
    private var date = ""



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
        _binding = FragmentGraphBinding.inflate(inflater, container, false)
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
            if (isAdded && activity != null) {
                if (viewModel.getDeviceAddress() != "") {
                    bluetoothLeService!!.connect(viewModel.getDeviceAddress())
                }
            }
        }
        mHandler.postDelayed(mRunnable, 1000)

        // --- Empfang von Daten vom ESP32 thing für 2s aktivieren --- //
        // Daten werden aktualisiert
        binding.buttonData.setOnClickListener {
            // Daten empfangen aktiviert, wenn Sensor verbunden ist
            if (isConnected) {
                bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, true)
                isReceivingData = true
                binding.buttonData.text = getString(R.string.bt_data_off)

                mRunnable = Runnable {
                    // Daten empfangen deaktiviert
                    bluetoothLeService!!.setCharacteristicNotification(gattCharacteristic!!, false)
                    isReceivingData = false
                    binding.buttonData.text = getString(R.string.btn_data_graph)
                    // Speichern der Daten in der Datenbank
                    insertDataInDb()
                }
                mHandler.postDelayed(mRunnable, 2000)

            } else {
                toast("Verbinde zunächst den Sensor!")
            }
        }

        // --- Konfiguration CircularProgressBar --- //
        // Anzeige des Fortschritts mit gerader Haltung
        binding.circularProgressBar.apply {
            // Progress Max
            progressMax = timeMaxProgressBar

            // ProgressBar Farbe
            progressBarColorStart = Color.parseColor("#94af76")
            progressBarColorEnd = Color.YELLOW

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

        // --- Konfiguration CircularProgressBar --- //
        // Anzeige des Verhältnisses zwischen gerader und ungerader Haltung
        binding.circularProgressBar1.apply {
            // Progress Max
            progressMax = timeMaxProgressBar

            // ProgressBar Farbe
            progressBarColorStart = Color.parseColor("#8CD34F")
            progressBarColorEnd = Color.parseColor("#8CD34F")

            // Farbgradient
            progressBarColorDirection = CircularProgressBar.GradientDirection.RIGHT_TO_LEFT

            // Hintergrundfarbe
            backgroundProgressBarColor = Color.RED
            backgroundProgressBarColorDirection = CircularProgressBar.GradientDirection.TOP_TO_BOTTOM

            // Weite der ProgressBar
            progressBarWidth = 10f
            backgroundProgressBarWidth = 5f

            roundBorder = true

            progressDirection = CircularProgressBar.ProgressDirection.TO_RIGHT
        }

        // --- Anzeige AlertDialog --- //
        // Festlegen der Zeit, die man gerade sitzen möchte
        binding.buttonSetGoal.setOnClickListener {
            showDialogEditTime()
        }

        // --- Anzeige eines DatePickers, um Datum auszuwählen --- //
        // Daten zu diesem Datum werden anschließend in dem Graphen angezeigt
        binding.buttonSelectDate.setOnClickListener {
            // Nur zurückliegende Daten können ausgewählt werden
            val constraintsBuilder = CalendarConstraints.Builder().setValidator(DateValidatorPointBackward.now())

            // Aufbau des DatePickers
            val datePicker =
                MaterialDatePicker.Builder.datePicker()
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .setCalendarConstraints(constraintsBuilder.build())
                    .setTitleText(getString(R.string.date_picker_title))
                    .build()

            datePicker.show(parentFragmentManager, "tag")

            datePicker.addOnPositiveButtonClickListener {
                // Klick auf positiven Button
                // ausgewähltes Datum formatieren
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = datePicker.selection!!
                val format = SimpleDateFormat("yyyy-MM-dd")
                date = format.format(calendar.time)

                binding.buttonSelectDate.text = date

                // Daten zu dem ausgewählten Tag werden geladen und angezeigt
                loadDbData(date)
            }
            datePicker.addOnNegativeButtonClickListener {
                // Klick auf negativen Button
                // datePicker wird geschlossen
            }
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
            arrayChallenge[i] = 0
        }
        for (i in 0 until 48) {
            arrayMovementBreak[i] = 0
        }
        for (i in 0 until 48) {
            arrayStraight[i] = 0
        }

        // --- Initialisierung und Konfiguration des Graphen --- //
        setUpAAChartView()

        // --- Einlesen des aktuellen Datums --- //
        val kalender: Calendar = Calendar.getInstance()
        val zeitformat = SimpleDateFormat("yyyy-MM-dd")
        val date = zeitformat.format(kalender.time)

        // --- Einlesen der Daten aus Datenbank --- //
        loadDbData(date)
    }


/*
  =============================================================
  =======                                               =======
  =======                   Funktionen                  =======
  =======                                               =======
  =============================================================
*/

    // === setUpAAChartView === //
    // https://github.com/AAChartModel/AAChartCore-Kotlin
    fun setUpAAChartView() {
        aaChartModel = configureAAChartModel()
        binding.chartView.aa_drawChartWithChartModel(aaChartModel)
    }

    // === configureAAChartModel === //
    // https://github.com/AAChartModel/AAChartCore-Kotlin
    private fun configureAAChartModel(): AAChartModel {
        val aaChartModel = configureChartBasicContent()
        aaChartModel.series(this.configureChartSeriesArray() as Array<Any>)
        return aaChartModel
    }

    // === configureChartBasicContent === //
    // https://github.com/AAChartModel/AAChartCore-Kotlin
    private fun configureChartBasicContent(): AAChartModel {
        return AAChartModel.Builder(requireContext().applicationContext)
            .setChartType(AAChartType.Column)
            .setXAxisVisible(true)
            .setTitle(getString(R.string.chart_title))
            .setColorsTheme(arrayOf("#699638", "#BEFCA4", "#EEFF05","#345428", "#a7e810", "#0a6e09" ))
            .setStacking(AAChartStackingType.Normal)
            .setTitleStyle(AAStyle.Companion.style("#FFFFFF"))
            .setBackgroundColor("#182015")
            .setAxesTextColor("#FFFFFF")
            .setCategories("00:00", "00:30", "01:00", "01:30", "02:00", "02:30", "03:00", "03:30", "04:00", "04:30", "05:00", "05:30",
                "06:00", "06:30", "07:00", "07:30", "08:00", "08:30", "09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "12:00",
                "12:30", "13:00", "13:30", "14:00", "14:30", "15:00", "15:30", "16:00", "16:30", "17:00", "17:30", "18:00",
                "18:30", "19:00", "19:30", "20:00", "20:30", "21:00", "21:30", "22:00", "22:30", "23:00", "23:30"
            )
            .setYAxisTitle("Minuten")
            .setYAxisMax(30f)
            .setScrollablePlotArea(
                AAScrollablePlotArea()
                    .minWidth(600)
                    .scrollPositionX(1f))
            .build()
    }


    // === configureChartSeriesArray === //
    // Initialisierung Datentyp und Kategorien
    // https://github.com/AAChartModel/AAChartCore-Kotlin
    private fun configureChartSeriesArray(): Array<AASeriesElement> {

        return arrayOf(
            AASeriesElement()
                .name("Aufrecht")
                .data(arrayStraight as Array<Any>),
            AASeriesElement()
                .name("zurückgelehnt")
                .data(arrayLeanBack as Array<Any>),
            AASeriesElement()
                .name("ungerade")
                .data(arrayBent as Array<Any>),
            AASeriesElement()
                .name("dynamisch")
                .data(arrayDynamic as Array<Any>),
            AASeriesElement()
                .name("Challenges")
                .data(arrayChallenge as Array<Any>),
            AASeriesElement()
                .name("Bewegungspausen")
                .data(arrayMovementBreak as Array<Any>),
        )
    }

    // === configureChartSeriesArrayExercise === //
    // wird angesprungen, wenn keine Daten zu einem ausgewählten Datum vorhanden sind
    // https://github.com/AAChartModel/AAChartCore-Kotlin
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

    // === configureChartSeriesArrayAfterLoadDb === //
    // wird angesprungen, wenn keine Daten zu einem ausgewählten Datum vorhanden sind
    // https://github.com/AAChartModel/AAChartCore-Kotlin
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

    // === serviceConnection === //
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
        toast("connected")
    }

    // === onDisconnect === //
    private fun onDisconnect() {
        isConnected = false
        toast("disconnected")
    }

    // === onGattCharacteristicDiscovered === //
    private fun onGattCharacteristicDiscovered() {
        gattCharacteristic = bluetoothLeService?.getGattCharacteristic()
    }

    // === onDataAvailable === //
    private fun onDataAvailable() {
        // neue Daten verfügbar
        Log.i(TAG, "Data available")
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
            // Anschließend werden die Daten auf das Array, welches im Graph angezeigt wird, übertragen
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

                // gesamte Anzahl an Minuten an einem Tag mit ungerader Haltung wird berechnet
                counterReminder = 0
                for (i in 0 until 48) {
                    counterReminder += arrayBent[i].toString().toInt()
                }
                binding.textViewNumberReminder.text = getString(R.string.tv_reminder, counterReminder)
            }


            // Abfrage, ob das jeweilige Array in dem empfangenen Objekt dabei ist
            // Daten im JSONArray werden auf eine ArrayList übertragen
            // Anschließend werden die Daten auf das Array, welches im Graph angezeigt wird, übertragen
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

                // gesamte Anzahl an Minuten an einem Tag mit zurückgelehnter Haltung wird berechnet
                counterLeanBack = 0
                for (i in 0 until 48) {
                    counterLeanBack += arrayLeanBack[i].toString().toInt()
                }
                binding.textViewLeanBack.text = getString(R.string.tv_reminder_lean_back, counterLeanBack)
            }


            // Abfrage, ob das jeweilige Array in dem empfangenen Objekt dabei ist
            // Daten im JSONArray werden auf eine ArrayList übertragen
            // Anschließend werden die Daten auf das Array, welches im Graph angezeigt wird, übertragen
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
            // Anschließend werden die Daten auf das Array, welches im Graph angezeigt wird, übertragen
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

            // progressMax wird aktualisiert
            binding.circularProgressBar1.apply {
                progressMax = counterReminder + progressTime
            }
            // Aktualisierung der Anzeige
            binding.circularProgressBar1.progress = progressTime

            // Verhältnis zwischen gerader und ungerader Haltung wird berechnet und angezeigt
            val ratio = ((progressTime/(counterReminder + progressTime))*100)
            binding.textViewUprightBentStatus.text = getString(R.string.tv_upright_bent_status, ratio) + "%"

            // Aktualisierung des Graphen
            val seriesArr = configureChartSeriesArray()
            binding.chartView.aa_onlyRefreshTheChartDataWithChartOptionsSeriesArray(seriesArr)

            // Aktualisierung der Anzeige der Zeit mit gerader Haltung
            if (progressTime < timeMaxProgressBar) {
                binding.circularProgressBar.progress = progressTime
                binding.textViewTime.text = getString(R.string.tv_time, progressTime, timeMaxProgressBar)
                binding.textViewProgressUpright.text = getString(R.string.tv_progress)
            } else {
                binding.circularProgressBar.progress = timeMaxProgressBar
                binding.textViewTime.text = getString(R.string.tv_time, timeMaxProgressBar, timeMaxProgressBar)
                binding.textViewProgressUpright.text = getString(R.string.tv_progress_done)
            }

        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    // === showDialogEditTime === //
    // AlertDialog mit Text-Eingabe-Feld
    // Eingabe der Zeit die man am Tag gerade sitzen möchte
    private fun showDialogEditTime() {

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

                        // Aktualisierung der Anzeige
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
                        // Speichern der Daten
                        insertDataInDb()
                    }
                }
                .show()
        }
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
            Log.d(TAG, "Connect request result=" + result)
        }
    }

    // === onDestroy === //
    override fun onDestroy() {
        super.onDestroy()
        bluetoothLeService!!.disconnect()
        bluetoothLeService!!.close()
        context?.unbindService(serviceConnection)
        bluetoothLeService = null
        mHandler.removeCallbacksAndMessages(null)
    }

    // === onPause === //
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
        arrayBentList.clear()
        arrayLeanList.clear()
        arrayDynamicList.clear()
        arrayStraightList.clear()
    }

    // === loadDbData === //
    // Einlesen der Daten aus der Datenbank
    private fun loadDbData(date: String) {

        // Einstiegspunkt für die Abfrage ist users/uid/date/Daten
        val uid = mFirebaseAuth.currentUser!!.uid
        db.collection("users").document(uid).collection(date)
            .document("Daten")//
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

                        // Überschreiben der Daten in die Arrays für die Anzeige im Graphen
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

                        // Löschen der Daten aus den ListArrays
                        arrayBentList.clear()
                        arrayLeanList.clear()
                        arrayDynamicList.clear()
                        arrayStraightList.clear()

                        // Berechnung der gesamten Zeit an ungerader Haltung an einem Tag
                        counterReminder = 0
                        for (i in 0 until 48) {
                            counterReminder += arrayBent[i].toString().toInt()
                        }
                        binding.textViewNumberReminder.text = getString(R.string.tv_reminder, counterReminder)


                        // Berechnung der gesamten Zeit an zurückgelehnter Haltung an einem Tag
                        counterLeanBack = 0
                        for (i in 0 until 48) {
                            counterLeanBack += arrayLeanBack[i].toString().toInt()
                        }
                        binding.textViewLeanBack.text = getString(R.string.tv_reminder_lean_back, counterLeanBack)

                        // Berechnung der gesamten Zeit an gerader Haltung an einem Tag
                        progressTime = 0F
                        for (i in 0 until 48) {
                            progressTime += arrayStraight[i].toString().toInt()
                        }

                        // Berechnung progressMax
                        binding.circularProgressBar1.apply {
                            progressMax = counterReminder + progressTime
                        }

                        // Aktualisierung der Anzeige
                        binding.circularProgressBar1.progress = progressTime


                        // Verhältnis zwischen gerader und ungerader Haltung wird berechnet und angezeigt
                        val ratio = ((progressTime / (counterReminder + progressTime)) * 100)
                        binding.textViewUprightBentStatus.text = getString(R.string.tv_upright_bent_status, ratio) + "%"

                        // ProgressBar mit Anzeige der gesamten Zeit an gerader Haltung wird aktualisiert
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
                        // wenn die Daten gleich null sind, werden alle Anzeigen auf null gesetzt
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

        // Daten zu Challenges und Bewegungspausen einlesen
        // Einstiegspunkt für die Abfrage ist users/uid/date/Challenge
        db.collection("users").document(uid).collection(date)
            .document("Challenge")
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Datenbankantwort in Objektvariable speichern
                    dataExercise = task.result!!.toObject(UserDataExercise::class.java)

                    // Daten werden den Variablen zugewiesen, wenn diese ungleich null sind
                    if (dataExercise != null) {
                        arrayChallengeDb = dataExercise!!.getChallengeArray()
                        arrayMovementBreakDb = dataExercise!!.getMovementBreakArray()
                        counterChallenge = dataExercise!!.getChallenge()
                        counterMovement = dataExercise!!.getMovementBreak()

                        // Überschreiben der Daten in die Arrays für die Anzeige im Graphen
                        for (i in 0 until 48) {
                            arrayChallenge[i] = arrayChallengeDb[i]
                        }

                        for (i in 0 until 48) {
                            arrayMovementBreak[i] = arrayMovementBreakDb[i]
                        }

                        // Anzeige aktualisieren
                        binding.textViewChallengeGraph.text = getString(R.string.tv_counter_challenge, counterChallenge)
                        binding.textViewMovementGraph.text = getString(R.string.tv_counter_movement, counterMovement)
                    }else{
                        // wenn die Daten gleich null sind, werden alle Anzeigen auf null gesetzt
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


    // === onDestroyView === //
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}