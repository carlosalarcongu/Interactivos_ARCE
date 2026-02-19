package com.example.helloworld

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    // --- VARIABLES UI ---
    private lateinit var mainLayout: View
    private lateinit var containerSuperior: LinearLayout
    private lateinit var layoutExtraInfo: LinearLayout
    private lateinit var txtPrincipal: TextView
    private lateinit var lblPrincipal: TextView
    private lateinit var txtSecundario: TextView
    private lateinit var txtPasos: TextView
    private lateinit var txtCalorias: TextView
    private lateinit var txtAltitud: TextView
    private lateinit var visualizador: TextView
    private lateinit var textoGPS: TextView
    private lateinit var inputUmbral: EditText
    private lateinit var cardMap: MaterialCardView
    private lateinit var txtMeta: TextView
    private lateinit var txtFrase: TextView
    private lateinit var cardMotivacion: MaterialCardView
    private lateinit var switchVoz: SwitchCompat
    private lateinit var switchVibracion: SwitchCompat

    // --- SENSORES Y SISTEMA ---
    private lateinit var prefs: SharedPreferences
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var locationManager: LocationManager
    private var tts: TextToSpeech? = null
    private var ttsListo = false

    // --- LOGICA ---
    private var primeraLocalizacion: Location? = null
    private var ultimaLocalizacion: Location? = null
    private var tiempoInicio: Long = 0L
    private var distanciaTotal: Float = 0f
    private var velocidadActual: Float = 0f
    private var modoActualIndex = 0
    private var metaDistanciaMetros: Int = 0
    private var metaTiempoMinutos: Int = 0
    private var metaAlcanzada = false
    private val CHANNEL_ID = "canal_caidas"

    private val handlerVoz = Handler(Looper.getMainLooper())
    private var runnableVoz: Runnable? = null
    private val handlerCronometro = Handler(Looper.getMainLooper())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) iniciarGPS() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)

        try {
            prefs = getSharedPreferences("config_app", Context.MODE_PRIVATE)
            inicializarVistas()
            inicializarSensores()
            inicializarMapa()
            inicializarVoz()
            inicializarNotificaciones()
            aplicarModo(0)
            iniciarCronometro()
            comprobarPermisos()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun inicializarVistas() {
        mainLayout = findViewById(R.id.mainLayout)
        containerSuperior = findViewById(R.id.containerSuperior)
        layoutExtraInfo = findViewById(R.id.layoutExtraInfo)
        txtPrincipal = findViewById(R.id.txtPrincipal)
        lblPrincipal = findViewById(R.id.lblPrincipal)
        txtSecundario = findViewById(R.id.txtSecundario)
        txtPasos = findViewById(R.id.txtPasos)
        txtCalorias = findViewById(R.id.txtCalorias)
        txtAltitud = findViewById(R.id.txtAltitud)
        visualizador = findViewById(R.id.visualizador)
        textoGPS = findViewById(R.id.textView)
        inputUmbral = findViewById(R.id.inputUmbral)
        cardMap = findViewById(R.id.cardMap)
        txtMeta = findViewById(R.id.txtMeta)
        txtFrase = findViewById(R.id.txtFrase)
        cardMotivacion = findViewById(R.id.cardMotivacion)
        switchVoz = findViewById(R.id.switchVoz)
        switchVibracion = findViewById(R.id.switchVibracion)

        findViewById<Button>(R.id.btnCambiarModo).setOnClickListener {
            modoActualIndex = (modoActualIndex + 1) % 3
            aplicarModo(modoActualIndex)
        }
        findViewById<Button>(R.id.btnResetRuta).setOnClickListener { resetearDatos() }
        findViewById<Button>(R.id.btnObjetivo).setOnClickListener { mostrarDialogoObjetivo() }
        findViewById<ImageButton>(R.id.btnConfig).setOnClickListener { mostrarDialogoConfiguracion() }

        switchVoz.setOnCheckedChangeListener { _, isChecked -> if(isChecked) reiniciarVoz() else detenerVoz() }
    }

    private fun inicializarSensores() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private fun inicializarMapa() {
        val map = findViewById<org.osmdroid.views.MapView>(R.id.map)
        map.setMultiTouchControls(true)
        map.controller.setZoom(18.0)
    }

    private fun inicializarVoz() {
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) { tts?.language = Locale("es", "ES"); ttsListo = true } }
    }

    private fun iniciarGPS() {
        try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this) } catch (e: SecurityException) {}
    }

    private fun comprobarPermisos() {
        requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    private fun resetearDatos() {
        primeraLocalizacion = null
        distanciaTotal = 0f
        txtMeta.visibility = View.GONE
        Toast.makeText(this, "Reset completado", Toast.LENGTH_SHORT).show()
    }

    private fun mostrarDialogoObjetivo() {
        val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle("Meta en Metros").setView(input).setPositiveButton("OK") { _, _ ->
            metaDistanciaMetros = input.text.toString().toIntOrNull() ?: 0
            txtMeta.text = "Meta: $metaDistanciaMetros m"
            txtMeta.visibility = View.VISIBLE
            metaAlcanzada = false
        }.show()
    }

    private fun mostrarDialogoConfiguracion() {
        Toast.makeText(this, "Configuración abierta", Toast.LENGTH_SHORT).show()
    }

    private fun iniciarCronometro() {
        handlerCronometro.post(object : Runnable {
            override fun run() {
                val m = if (tiempoInicio > 0) ((System.currentTimeMillis() - tiempoInicio) / 60000).toInt() else 0
                txtPasos.text = "${(distanciaTotal / 0.75).toInt()}"
                handlerCronometro.postDelayed(this, 1000)
            }
        })
    }

    private fun aplicarModo(index: Int) {
        val colores = intArrayOf(Color.GREEN, Color.BLUE, Color.RED)
        mainLayout.setBackgroundColor(colores[index])
        cardMotivacion.visibility = if (index == 2) View.VISIBLE else View.GONE
    }

    private fun reiniciarVoz() {
        runnableVoz = object : Runnable {
            override fun run() {
                if (ttsListo) tts?.speak("Distancia: ${distanciaTotal.toInt()} metros", TextToSpeech.QUEUE_ADD, null, null)
                handlerVoz.postDelayed(this, 60000)
            }
        }
        handlerVoz.post(runnableVoz!!)
    }

    private fun detenerVoz() { runnableVoz?.let { handlerVoz.removeCallbacks(it) } }

    private fun inicializarNotificaciones() {
        if (Build.VERSION.SDK_INT >= 26) {
            val chan = NotificationChannel(CHANNEL_ID, "Alertas", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            visualizador.text = "X: ${event.values[0].toInt()} Y: ${event.values[1].toInt()} Z: ${event.values[2].toInt()}"
        }
    }

    override fun onLocationChanged(location: Location) {
        if (primeraLocalizacion == null) { primeraLocalizacion = location; tiempoInicio = System.currentTimeMillis() }
        ultimaLocalizacion = location
        textoGPS.text = "Lat: ${location.latitude.toInt()} Lon: ${location.longitude.toInt()}"
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    override fun onDestroy() { super.onDestroy(); tts?.shutdown() }
}