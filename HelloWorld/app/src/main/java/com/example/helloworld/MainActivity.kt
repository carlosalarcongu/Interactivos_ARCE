package com.example.helloworld

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    // --- 1. VARIABLES DEL SISTEMA (Tu código antiguo + Integración) ---

    // Sensores (Acelerómetro)
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var inputUmbral: TextInputEditText
    private lateinit var visualizador: TextView
    private var ultimoAvisoAccidente: Long = 0

    // GPS (Responsabilidad de Carlos)
    private lateinit var locationManager: LocationManager
    private lateinit var textoGPS: TextView // Para mostrar coords en tu tarjeta

    // --- 2. VARIABLES PARA EL EQUIPO (Placeholders) ---

    // ESTHER (Mapa)
    private lateinit var map: MapView

    // ALEJANDRO (Voz y Velocidad)
    private var tts: TextToSpeech? = null
    private var primeraLocalizacion: Location? = null
    private var ultimaLocalizacion: Location? = null
    private var tiempoInicio: Long = 0L

    // RAÚL (Notificaciones)
    // (Aquí irían variables de canales de notificación si hicieran falta globales)

    // --- 3. GESTIÓN DE PERMISOS (Carlos) ---
    private val permisoGPSLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            iniciarGPS()
        } else {
            Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // IMPORTANTE PARA ESTHER: Configuración OSMDroid antes de cargar el XML
        // Evita que el mapa se bloquee al intentar descargar tiles
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_main)

        // Inicializar Vistas (Vinculando con tu XML nuevo)
        inputUmbral = findViewById(R.id.inputUmbral)
        visualizador = findViewById(R.id.visualizador)
        textoGPS = findViewById(R.id.textView) // Tu TextView del GPS

        // --- INICIALIZAR SENSORES (Tu código antiguo) ---
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // --- INICIALIZAR MÓDULOS DEL EQUIPO ---
        inicializarMapaEsther()
        inicializarVozAlejandro()
        inicializarNotificacionesRaul()

        // --- INICIALIZAR GPS (Tu responsabilidad principal) ---
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        comprobarPermisoGPS()
    }

    // ==========================================
    // PARTE 1: GESTIÓN DEL GPS (CARLOS)
    // ==========================================

    private fun comprobarPermisoGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            iniciarGPS()
        } else {
            permisoGPSLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun iniciarGPS() {
        try {
            // Actualización agresiva (para que funcione bien en tu S25)
            // Pedimos tanto a GPS (Satélite) como a NETWORK (Wifi/Antenas)
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, this)

            Toast.makeText(this, "Buscando satélites...", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Error de seguridad GPS", Toast.LENGTH_SHORT).show()
        }
    }

    // ESTE ES EL MÉTODO "DIRECTOR DE ORQUESTA"
    override fun onLocationChanged(location: Location) {
        // 1. Actualizar tu interfaz (Carlos)
        textoGPS.text = "Lat: ${location.latitude}\nLon: ${location.longitude}"

        // 2. Guardar datos globales para Alejandro
        if (primeraLocalizacion == null) {
            primeraLocalizacion = location
            tiempoInicio = System.currentTimeMillis()
        }
        ultimaLocalizacion = location

        // 3. Llamar a ESTHER: "Mueve el mapa"
        actualizarMapaEsther(location)

        // 4. Llamar a ALEJANDRO: "Calcula y habla"
        gestionarVelocidadAlejandro(location)
    }

    // ==========================================
    // PARTE 2: ACELERÓMETRO (TU CÓDIGO ANTIGUO)
    // ==========================================

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val magnitud = Math.sqrt((x * x + y * y + z * z).toDouble())
            val umbralAccidente = inputUmbral.text.toString().toDoubleOrNull() ?: 20.0

            if (magnitud > umbralAccidente) {
                val ahora = System.currentTimeMillis()
                if (ahora - ultimoAvisoAccidente > 5000) {
                    Toast.makeText(this, "¡ACCIDENTE DETECTADO! (Llamando a Raúl...)", Toast.LENGTH_LONG).show()
                    lanzarNotificacionRaul() // Conectamos con el módulo de Raúl
                    ultimoAvisoAccidente = ahora
                }
            } else {
                visualizador.text = "X: %.2f | Y: %.2f | Z: %.2f".format(x, y, z)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ==========================================
    // PARTE 3: ZONA DE INTEGRACIÓN (PLACEHOLDERS)
    // ==========================================

    private fun inicializarMapaEsther() {
        // TODO: Esther configurará aquí el MapView
        try {
            map = findViewById(R.id.map)
            map.setMultiTouchControls(true)
            map.controller.setZoom(15.0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun actualizarMapaEsther(location: Location) {
        // TODO: Esther implementará el movimiento del marcador
        try {
            val punto = GeoPoint(location.latitude, location.longitude)
            map.controller.setCenter(punto)
            // Aquí Esther añadirá el código del Marker
        } catch (_: Exception) {}
    }

    private fun inicializarVozAlejandro() {
        // Inicializamos TTS para evitar fugas de memoria, Alejandro lo configurará
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "ES")
            }
        }
    }

    private fun gestionarVelocidadAlejandro(location: Location) {
        // TODO: Alejandro calculará aquí la velocidad
    }

    private fun inicializarNotificacionesRaul() {
        // TODO: Raúl creará el canal de notificaciones
    }

    private fun lanzarNotificacionRaul() {
        // TODO: Raúl disparará la notificación de caída aquí
    }

    // ==========================================
    // CICLO DE VIDA (LIMPIEZA)
    // ==========================================

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        map.onResume() // Necesario para OSMDroid
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        map.onPause() // Necesario para OSMDroid
    }

    override fun onDestroy() {
        // Limpiamos el TTS para que no se quede hablando solo (Petición de Alejandro)
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}