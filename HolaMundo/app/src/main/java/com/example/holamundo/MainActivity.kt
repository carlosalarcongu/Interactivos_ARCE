package com.example.holamundo // Asegúrate de que esto coincide con TU paquete

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SensorDashboard()
                }
            }
        }
    }
}

@SuppressLint("MissingPermission") // Suprimimos la alerta porque gestionamos permisos en el UI
@Composable
fun SensorDashboard() {
    val context = LocalContext.current

    // --- ESTADOS DE LA UI ---
    // Variables para guardar los valores que se muestran en pantalla
    var gyroX by remember { mutableStateOf(0f) }
    var gyroY by remember { mutableStateOf(0f) }
    var gyroZ by remember { mutableStateOf(0f) }
    // Cambia los 0.0 por null para saber si tenemos datos o no
    var latitud by remember { mutableStateOf<Double?>(null) }
    var longitud by remember { mutableStateOf<Double?>(null) }

    // Variables temporales (valores reales en tiempo real, aunque no se muestren)
    var realTimeGyroX by remember { mutableStateOf(0f) }
    var realTimeGyroY by remember { mutableStateOf(0f) }
    var realTimeGyroZ by remember { mutableStateOf(0f) }
    var realTimeLat by remember { mutableStateOf(0.0) }
    var realTimeLong by remember { mutableStateOf(0.0) }

    // Control de modos
    var isAutoUpdate by remember { mutableStateOf(true) } // true = Auto, false = Manual

    // --- CONFIGURACIÓN DE SENSORES ---

    // 1. GIROSCOPIO
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    // Siempre guardamos el valor real
                    realTimeGyroX = it.values[0]
                    realTimeGyroY = it.values[1]
                    realTimeGyroZ = it.values[2]

                    // Si estamos en modo AUTO, actualizamos la UI inmediatamente
                    if (isAutoUpdate) {
                        gyroX = realTimeGyroX
                        gyroY = realTimeGyroY
                        gyroZ = realTimeGyroZ
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // 2. GPS (LOCATION) - MODO AGRESIVO / STANDALONE
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permiso concedido, el LaunchedEffect se reactivará si usamos una variable de estado,
        // pero por simplicidad, reinicia la app si es la primera vez.
    }

    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            // --- AQUÍ ESTÁ EL TRUCO PARA EL S25 ---
            // Configuración "Agresiva" para despertar el GPS sin Google Maps
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000) // Actualiza cada 1 seg
                .setMinUpdateDistanceMeters(0f) // Notifica aunque no te muevas (importante para el arranque)
                .setWaitForAccurateLocation(false) // No esperes a la perfección, dame datos YA
                .setMaxUpdateDelayMillis(100) // No retrases datos para ahorrar batería
                .build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    // Iteramos sobre todas las localizaciones, no solo la última
                    for (location in result.locations) {
                        realTimeLat = location.latitude
                        realTimeLong = location.longitude

                        // Actualización forzada inmediata en pantalla
                        if (isAutoUpdate) {
                            latitud = realTimeLat
                            longitud = realTimeLong
                        }
                    }
                }
            }

            // Solicitamos actualizaciones constantes
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    // --- INTERFAZ DE USUARIO (UI) ---
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("DASHBOARD SENSORES", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(30.dp))

        // Tarjeta de Giroscopio
        DataCard(title = "Giroscopio", data = "X: ${"%.2f".format(gyroX)}\nY: ${"%.2f".format(gyroY)}\nZ: ${"%.2f".format(gyroZ)}")

        Spacer(modifier = Modifier.height(16.dp))

        // Tarjeta de GPS
        // Tarjeta de GPS Inteligente
        val textoGPS = if (latitud != null && longitud != null) {
            "Lat: ${"%.4f".format(latitud)}\nLon: ${"%.4f".format(longitud)}"
        } else {
            "Buscando satélites...\n(Acércate a una ventana)"
        }

        DataCard(title = "GPS", data = textoGPS)

        Spacer(modifier = Modifier.height(40.dp))

        // Botones de Control
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { isAutoUpdate = true },
                colors = ButtonDefaults.buttonColors(containerColor = if(isAutoUpdate) Color.Green else Color.Gray)
            ) {
                Text("Auto")
            }

            Button(
                onClick = { isAutoUpdate = false },
                colors = ButtonDefaults.buttonColors(containerColor = if(!isAutoUpdate) Color.Red else Color.Gray)
            ) {
                Text("Manual")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botón de Actualización Manual (Solo visible si NO estamos en Auto)
        if (!isAutoUpdate) {
            Button(
                onClick = {
                    // Al hacer click, copiamos los valores "reales" a los "visibles"
                    gyroX = realTimeGyroX
                    gyroY = realTimeGyroY
                    gyroZ = realTimeGyroZ
                    latitud = realTimeLat
                    longitud = realTimeLong
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ACTUALIZAR DATOS AHORA")
            }
        }
    }
}

@Composable
fun DataCard(title: String, data: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color.Blue)
            Spacer(modifier = Modifier.height(8.dp))
            Text(data, fontSize = 18.sp)
        }
    }
}