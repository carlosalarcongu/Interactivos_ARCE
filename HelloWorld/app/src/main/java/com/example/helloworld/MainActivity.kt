package com.example.helloworld

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    // --- SENSORES (Acelerómetro) ---
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var inputUmbral: TextInputEditText
    private lateinit var visualizador: TextView
    private var ultimoAvisoAccidente: Long = 0

    // --- LOCALIZACIÓN (LocationManager) ---
    private lateinit var locationManager: LocationManager
    private lateinit var textoGPS: TextView

    // --- VARIABLES DE EQUIPO ---
    private lateinit var map: MapView
    private var marcador: Marker? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false

    // Alejandro (Voz y Velocidad)
    private var primeraLocalizacion: Location? = null
    private var ultimaLocalizacion: Location? = null
    private var tiempoInicio: Long = 0L
    private val handlerMensajes = Handler(Looper.getMainLooper())
    private var mensajesIniciados = false

    private val CHANNEL_ID = "canal_caidas"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración para el mapa de Esther
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)

        // Vincular Vistas
        inputUmbral = findViewById(R.id.inputUmbral)
        visualizador = findViewById(R.id.visualizador)
        textoGPS = findViewById(R.id.textView)

        // 1. Inicializar SensorManager y Acelerómetro (Basado en Diapositivas)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // 2. Inicializar LocationManager para el GPS
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        inicializarMapa()
        inicializarVoz()
        inicializarNotificaciones()
        comprobarPermisos()
    }

    private fun inicializarVoz() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("es", "ES"))
                ttsListo = true
            }
        }
    }

    // ==========================================
    // GESTIÓN DE LA CAÍDA (ACELERÓMETRO)
    // ==========================================

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val magnitud = Math.sqrt((x * x + y * y + z * z).toDouble())
            val umbral = inputUmbral.text.toString().toDoubleOrNull() ?: 20.0

            if (magnitud > umbral) {
                val ahora = System.currentTimeMillis()
                if (ahora - ultimoAvisoAccidente > 5000) {
                    // Mensaje personalizado solicitado
                    if (ttsListo) {
                        tts?.speak("Carlos eres un pringado te has caido", TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                    lanzarNotificacion(ultimaLocalizacion)
                    ultimoAvisoAccidente = ahora
                }
            } else {
                // Visualización en interfaz (Tasa UI ~60ms)
                visualizador.text = "X: %.2f | Y: %.2f | Z: %.2f".format(x, y, z)
            }
        }
    }

    // ==========================================
    // GESTIÓN DE UBICACIÓN (LOCALIZACIÓN)
    // ==========================================

    override fun onLocationChanged(location: Location) {
        textoGPS.text = "Lat: ${location.latitude}, Lon: ${location.longitude}"

        // Lógica de Alejandro para calcular velocidad
        if (primeraLocalizacion == null) {
            primeraLocalizacion = location
            tiempoInicio = System.currentTimeMillis()
            if (!mensajesIniciados) {
                mensajesIniciados = true
                iniciarTemporizadorVoz()
            }
        }
        ultimaLocalizacion = location
        actualizarMapa(location)
    }

    private fun iniciarTemporizadorVoz() {
        handlerMensajes.postDelayed(object : Runnable {
            override fun run() {
                lanzarMensajeVelocidad()
                handlerMensajes.postDelayed(this, 60000) // Cada 60 segundos
            }
        }, 60000)
    }

    private fun lanzarMensajeVelocidad() {
        val p1 = primeraLocalizacion
        val p2 = ultimaLocalizacion
        if (ttsListo && p1 != null && p2 != null) {
            val distancia = p1.distanceTo(p2)
            val tiempoSegundos = (System.currentTimeMillis() - tiempoInicio) / 1000.0
            val velocidad = if (tiempoSegundos > 0) (distancia / tiempoSegundos).toFloat() else 0f

            val texto = "Tu velocidad media es de ${String.format("%.2f", velocidad)} metros por segundo"
            tts?.speak(texto, TextToSpeech.QUEUE_ADD, null, null)
        }
    }

    // ==========================================
    // UTILIDADES (PERMISOS, MAPA, NOTIFICACIONES)
    // ==========================================

    private fun comprobarPermisos() {
        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                try {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this)
                } catch (_: SecurityException) {}
            }
        }
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    private fun inicializarMapa() {
        map = findViewById(R.id.map)
        map.controller.setZoom(17.0)
    }

    private fun actualizarMapa(location: Location) {
        val punto = GeoPoint(location.latitude, location.longitude)
        map.controller.setCenter(punto)
        marcador?.let { map.overlays.remove(it) }
        marcador = Marker(map).apply { position = punto }
        map.overlays.add(marcador)
        map.invalidate()
    }

    private fun inicializarNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CHANNEL_ID, "Alertas", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    private fun lanzarNotificacion(location: Location?) {
        val uri = Uri.parse("geo:${location?.latitude},${location?.longitude}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("¡EMERGENCIA!")
            .setContentText("Carlos se ha caído")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)

        try { NotificationManagerCompat.from(this).notify(1001, builder.build()) } catch (_: Exception) {}
    }

    // ==========================================
    // CICLO DE VIDA (GESTIÓN DE SENSORES)
    // ==========================================

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        map.onPause()
    }

    override fun onDestroy() {
        handlerMensajes.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}