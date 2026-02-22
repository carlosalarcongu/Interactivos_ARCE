package com.example.helloworld

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    // --- UI ---
    private lateinit var mainLayout: View
    private lateinit var containerSuperior: LinearLayout
    private lateinit var layoutExtraInfo: LinearLayout

    private lateinit var appTitle: TextView
    private lateinit var txtPrincipal: TextView
    private lateinit var lblPrincipal: TextView
    private lateinit var txtSecundario: TextView
    private lateinit var txtMeta: TextView
    private lateinit var txtFrase: TextView

    private lateinit var txtPasos: TextView
    private lateinit var txtCalorias: TextView
    private lateinit var txtAltitud: TextView

    private lateinit var textoGPS: TextView

    private lateinit var cardDatos: MaterialCardView
    private lateinit var cardMotivacion: MaterialCardView
    private lateinit var cardMap: MaterialCardView
    private lateinit var cardConfig: MaterialCardView

    private lateinit var btnCambiarModo: Button
    private lateinit var btnObjetivo: Button
    private lateinit var btnResetRuta: Button
    private lateinit var btnPerfil: ImageButton

    // Parámetros Configurables
    private lateinit var prefs: SharedPreferences
    private var pesoUsuario: Float = 70f
    private var zancadaUsuario: Float = 0.75f
    private var umbralCaida: Float = 20f
    private var vozActivada: Boolean = true
    private var vibracionActivada: Boolean = true

    // Sistema
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var locationManager: LocationManager
    private var tts: TextToSpeech? = null
    private var ttsListo = false

    private lateinit var map: MapView
    private var marcador: Marker? = null
    private var rutaOverlay: Polyline? = null

    private var primeraLocalizacion: Location? = null
    private var ultimaLocalizacion: Location? = null
    private var tiempoInicio: Long = 0L

    private var distanciaTotal: Float = 0f
    private var velocidadMaxima: Float = 0f
    private var velocidadActual: Float = 0f
    private var altitudActual: Double = 0.0

    private var metaDistanciaMetros: Int = 0
    private var metaTiempoMinutos: Int = 0
    private var metaAlcanzada = false

    private val frasesMotivadoras = listOf(
        "\"El dolor es temporal, la gloria es eterna.\"",
        "\"No te detengas cuando estés cansado.\"",
        "\"Tu único límite es tu mente.\"",
        "\"Suda hoy, brilla mañana.\"",
        "\"Corre con el corazón.\"",
        "\"Si fuera fácil, todos lo harían.\""
    )

    private var ultimoAvisoAccidente: Long = 0
    private val CHANNEL_ID = "canal_caidas"
    private var modoActualIndex = 0
    private val handlerMensajes = Handler(Looper.getMainLooper())
    private var tareaVoz: Runnable? = null
    private val handlerCronometro = Handler(Looper.getMainLooper())
    private var tareaCronometro: Runnable? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) iniciarGPS() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Configuration.getInstance().userAgentValue = packageName
            setContentView(R.layout.activity_main)

            prefs = getSharedPreferences("config_arce", Context.MODE_PRIVATE)
            cargarAjustes()

            inicializarVistas()
            inicializarSensores()
            inicializarMapa()
            inicializarVoz()
            inicializarNotificaciones()

            aplicarModo(modoActualIndex)
            iniciarCronometro()
            comprobarPermisos()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error inicio: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun cargarAjustes() {
        pesoUsuario = prefs.getFloat("peso", 70f)
        zancadaUsuario = prefs.getFloat("zancada", 0.75f)
        umbralCaida = prefs.getFloat("umbral", 20f)
        vozActivada = prefs.getBoolean("voz", true)
        vibracionActivada = prefs.getBoolean("vibracion", true)
    }

    private fun guardarAjustes() {
        prefs.edit().apply {
            putFloat("peso", pesoUsuario)
            putFloat("zancada", zancadaUsuario)
            putFloat("umbral", umbralCaida)
            putBoolean("voz", vozActivada)
            putBoolean("vibracion", vibracionActivada)
            apply()
        }
    }

    private fun inicializarVistas() {
        mainLayout = findViewById(R.id.mainLayout)
        containerSuperior = findViewById(R.id.containerSuperior)
        layoutExtraInfo = findViewById(R.id.layoutExtraInfo)

        appTitle = findViewById(R.id.appTitle)
        txtPrincipal = findViewById(R.id.txtPrincipal)
        lblPrincipal = findViewById(R.id.lblPrincipal)
        txtSecundario = findViewById(R.id.txtSecundario)
        txtMeta = findViewById(R.id.txtMeta)
        txtFrase = findViewById(R.id.txtFrase)

        txtPasos = findViewById(R.id.txtPasos)
        txtCalorias = findViewById(R.id.txtCalorias)
        txtAltitud = findViewById(R.id.txtAltitud)

        cardDatos = findViewById(R.id.cardDatos)
        cardConfig = findViewById(R.id.cardConfig)
        cardMotivacion = findViewById(R.id.cardMotivacion)
        cardMap = findViewById(R.id.cardMap)

        textoGPS = findViewById(R.id.textView)

        btnCambiarModo = findViewById(R.id.btnCambiarModo)
        btnObjetivo = findViewById(R.id.btnObjetivo)
        btnResetRuta = findViewById(R.id.btnResetRuta)
        btnPerfil = findViewById(R.id.btnPerfil)

        btnCambiarModo.setOnClickListener {
            modoActualIndex = (modoActualIndex + 1) % 3
            aplicarModo(modoActualIndex)
        }

        btnResetRuta.setOnClickListener { resetearDatos() }
        btnObjetivo.setOnClickListener { mostrarDialogoObjetivo() }
        btnPerfil.setOnClickListener { mostrarSubmenuConfig() }
    }

    private fun mostrarSubmenuConfig() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_config, null)
        val editPeso = dialogView.findViewById<EditText>(R.id.editPeso)
        val editZancada = dialogView.findViewById<EditText>(R.id.editZancada)
        val editUmbral = dialogView.findViewById<EditText>(R.id.editUmbral)
        val dialogSwitchVoz = dialogView.findViewById<SwitchCompat>(R.id.dialogSwitchVoz)
        val dialogSwitchVibracion = dialogView.findViewById<SwitchCompat>(R.id.dialogSwitchVibracion)

        editPeso.setText(pesoUsuario.toString())
        editZancada.setText(zancadaUsuario.toString())
        editUmbral.setText(umbralCaida.toString())
        dialogSwitchVoz.isChecked = vozActivada
        dialogSwitchVibracion.isChecked = vibracionActivada

        AlertDialog.Builder(this)
            .setTitle("Perfil y Seguridad")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                pesoUsuario = editPeso.text.toString().toFloatOrNull() ?: pesoUsuario
                zancadaUsuario = editZancada.text.toString().toFloatOrNull() ?: zancadaUsuario
                umbralCaida = editUmbral.text.toString().toFloatOrNull() ?: umbralCaida
                vozActivada = dialogSwitchVoz.isChecked
                vibracionActivada = dialogSwitchVibracion.isChecked
                
                guardarAjustes()
                
                // Actualizar servicios según los nuevos ajustes
                if (vozActivada) reiniciarVoz() else detenerVoz()
                
                actualizarTextosSegunModo()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun aplicarModo(index: Int) {
        val pesoSuperior: Float
        val pesoExtra: Float
        val pesoMap: Float
        val drawableFondo: Int

        when (index) {
            0 -> { // TRANQUILO
                btnCambiarModo.text = "TRANQUILO 🌿"
                drawableFondo = R.drawable.grad_tranquilo
                pesoSuperior = 0.35f
                pesoExtra = 0.15f
                pesoMap = 0.5f
                cardMotivacion.visibility = View.VISIBLE
                txtFrase.text = frasesMotivadoras.random()
            }
            1 -> { // RECUPERACIÓN
                btnCambiarModo.text = "RECUPERACIÓN 💙"
                drawableFondo = R.drawable.grad_recuperacion
                pesoSuperior = 0.2f
                pesoExtra = 0.0f
                pesoMap = 0.8f
                cardMotivacion.visibility = View.GONE
            }
            else -> { // ESFUERZO
                btnCambiarModo.text = "ESFUERZO 🔥"
                drawableFondo = R.drawable.grad_esfuerzo
                pesoSuperior = 0.5f
                pesoExtra = 0.2f
                pesoMap = 0.2f
                cardMotivacion.visibility = View.GONE
            }
        }

        try {
            mainLayout.background = ContextCompat.getDrawable(this, drawableFondo)
        } catch (e: Exception) {
            mainLayout.setBackgroundColor(Color.DKGRAY)
        }

        val pSup = containerSuperior.layoutParams as LinearLayout.LayoutParams
        pSup.weight = pesoSuperior
        containerSuperior.layoutParams = pSup

        val pExtra = layoutExtraInfo.layoutParams as LinearLayout.LayoutParams
        pExtra.weight = pesoExtra
        layoutExtraInfo.visibility = if (pesoExtra > 0) View.VISIBLE else View.GONE
        layoutExtraInfo.layoutParams = pExtra

        val pMap = cardMap.layoutParams as LinearLayout.LayoutParams
        pMap.weight = pesoMap
        cardMap.layoutParams = pMap

        actualizarTextosSegunModo()
    }

    private fun actualizarTextosSegunModo() {
        val minutos = ((System.currentTimeMillis() - tiempoInicio) / 60000).toInt()
        val pasosEstimados = (distanciaTotal / zancadaUsuario).toInt()
        val kcalEstimadas = (distanciaTotal * (pesoUsuario / 1500f)).toInt() 

        txtPasos.text = "$pasosEstimados"
        txtCalorias.text = "$kcalEstimadas"
        txtAltitud.text = "${altitudActual.toInt()} m"

        when (modoActualIndex) {
            1 -> {
                txtPrincipal.text = "%02d min".format(minutos)
                lblPrincipal.text = "TIEMPO PASEO"
                txtSecundario.text = "Distancia: ${"%.0f".format(distanciaTotal)} m"
            }
            2 -> {
                txtPrincipal.text = "%.1f".format(velocidadActual)
                lblPrincipal.text = "KM/H ACTUAL"
                txtSecundario.text = "Máx: ${"%.1f".format(velocidadMaxima)} | Dist: ${"%.0f".format(distanciaTotal)}m"
            }
            else -> {
                txtPrincipal.text = "%.1f".format(velocidadActual)
                lblPrincipal.text = "KM/H MEDIA"
                txtSecundario.text = "Distancia: ${"%.2f".format(distanciaTotal/1000)} km"
            }
        }
    }

    private fun iniciarCronometro() {
        tareaCronometro = object : Runnable {
            override fun run() {
                if (tiempoInicio > 0) {
                    actualizarTextosSegunModo()
                    verificarObjetivo()
                }
                handlerCronometro.postDelayed(this, 1000)
            }
        }
        handlerCronometro.post(tareaCronometro!!)
    }

    private fun mostrarDialogoObjetivo() {
        val opciones = arrayOf("Distancia (Metros)", "Tiempo (Minutos)")
        AlertDialog.Builder(this).setTitle("Objetivo").setSingleChoiceItems(opciones, -1) { d, w -> d.dismiss(); pedirValorObjetivo(w) }.show()
    }
    private fun pedirValorObjetivo(tipo: Int) {
        val input = EditText(this); input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this).setTitle("Introduce valor").setView(input).setPositiveButton("OK") { _, _ ->
            val v = input.text.toString().toIntOrNull() ?: 0
            if(tipo == 0) { metaDistanciaMetros = v; metaTiempoMinutos = 0; txtMeta.text = "Meta: $v m" }
            else { metaTiempoMinutos = v; metaDistanciaMetros = 0; txtMeta.text = "Meta: $v min" }
            txtMeta.visibility = View.VISIBLE; metaAlcanzada = false
        }.show()
    }
    private fun verificarObjetivo() {
        if (metaAlcanzada) return
        val m = ((System.currentTimeMillis() - tiempoInicio) / 60000).toInt()
        if ((metaDistanciaMetros > 0 && distanciaTotal >= metaDistanciaMetros) || (metaTiempoMinutos > 0 && m >= metaTiempoMinutos)) {
            metaAlcanzada = true; lanzarFelicitacion()
        }
    }
    private fun lanzarFelicitacion() {
        if(ttsListo) tts?.speak("Objetivo cumplido", TextToSpeech.QUEUE_FLUSH, null, null)
        AlertDialog.Builder(this).setTitle("¡FELICIDADES! 🏆").setMessage("Meta alcanzada").setPositiveButton("OK", null).show()
    }

    override fun onLocationChanged(location: Location) {
        if (!esUbicacionValida(location, ultimaLocalizacion)) return
        textoGPS.text = "GPS OK"
        altitudActual = location.altitude

        if (primeraLocalizacion == null) {
            primeraLocalizacion = location
            tiempoInicio = System.currentTimeMillis()
            if (::map.isInitialized) map.controller.setCenter(GeoPoint(location))
            reiniciarVoz()
        }

        if (ultimaLocalizacion != null) {
            val dist = ultimaLocalizacion!!.distanceTo(location)
            if (dist > 1.5) {
                distanciaTotal += dist
                val t = (location.time - ultimaLocalizacion!!.time) / 1000.0
                val v = if (t > 0) dist/t else 0.0
                velocidadActual = (v * 3.6).toFloat()
                if (velocidadActual > velocidadMaxima) velocidadMaxima = velocidadActual
            }
        }
        ultimaLocalizacion = location
        actualizarMapa(location)
    }

    private fun actualizarMapa(location: Location) {
        if (!::map.isInitialized) return
        val p = GeoPoint(location)
        marcador?.let { map.overlays.remove(it) }
        marcador = Marker(map).apply { position = p; title="Yo"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }
        map.overlays.add(marcador)
        rutaOverlay?.addPoint(p)
        map.invalidate()
    }

    private fun resetearDatos() {
        primeraLocalizacion = null; tiempoInicio = System.currentTimeMillis()
        distanciaTotal = 0f; velocidadMaxima = 0f; velocidadActual = 0f
        if (::map.isInitialized) {
            rutaOverlay?.setPoints(mutableListOf())
            map.invalidate()
        }
        metaDistanciaMetros = 0; metaTiempoMinutos = 0; txtMeta.visibility = View.GONE
        Toast.makeText(this, "Reset OK", Toast.LENGTH_SHORT).show()
    }

    private fun inicializarSensores() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    private fun inicializarMapa() {
        map = findViewById(R.id.map); map.setMultiTouchControls(true); map.controller.setZoom(19.0)
        rutaOverlay = Polyline().apply { outlinePaint.color = Color.CYAN; outlinePaint.strokeWidth = 10f }
        map.overlays.add(rutaOverlay)
    }
    private fun iniciarGPS() {
        try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, this) } catch (_: SecurityException) {}
    }
    private fun inicializarVoz() { tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) { tts?.language = Locale.forLanguageTag("es-ES"); ttsListo = true } } }
    private fun reiniciarVoz() { detenerVoz(); if(!vozActivada) return; tareaVoz = object : Runnable { override fun run() { if(ttsListo && primeraLocalizacion!=null) tts?.speak("Distancia: %.0f m".format(distanciaTotal), 0, null, null); handlerMensajes.postDelayed(this, 60000) } }; handlerMensajes.post(tareaVoz!!) }
    private fun detenerVoz() { tareaVoz?.let { handlerMensajes.removeCallbacks(it) } }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
            val mag = Math.sqrt((x*x + y*y + z*z).toDouble())
            if (mag > umbralCaida) {
                val ahora = System.currentTimeMillis()
                if (ahora - ultimoAvisoAccidente > 5000) {
                    if (vozActivada && ttsListo) tts?.speak("Caída", 0, null, null)
                    if (vibracionActivada) vibrar()
                    lanzarNotificacion(ultimaLocalizacion); ultimoAvisoAccidente = ahora
                }
            }
        }
    }

    private fun vibrar() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(1000)
        }
    }

    private fun inicializarNotificaciones() { if (Build.VERSION.SDK_INT >= 26) { getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "A", NotificationManager.IMPORTANCE_HIGH)) } }
    private fun lanzarNotificacion(loc: Location?) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${loc?.latitude},${loc?.longitude}"))
        val p = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("¡EMERGENCIA!").setContentIntent(p).setAutoCancel(true)
        try { NotificationManagerCompat.from(this).notify(1, b.build()) } catch (_: Exception) {}
    }
    private fun comprobarPermisos() {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissionLauncher.launch(p.toTypedArray())
    }

    private fun esUbicacionValida(nueva: Location, vieja: Location?): Boolean {
        if (vieja == null) return true
        if (nueva.accuracy > 30) return false
        val distancia = vieja.distanceTo(nueva)
        val tiempo = (nueva.time - vieja.time) / 1000.0
        if (tiempo <= 0) return false
        if (distancia / tiempo > 50.0) return false
        return true
    }
    
    override fun onResume() {
        super.onResume()
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, 3)
        if (::map.isInitialized) map.onResume()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        if (::map.isInitialized) map.onPause()
    }

    override fun onDestroy() {
        detenerVoz()
        handlerCronometro.removeCallbacksAndMessages(null)
        tts?.shutdown()
        if (::map.isInitialized) map.onDetach()
        super.onDestroy()
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}