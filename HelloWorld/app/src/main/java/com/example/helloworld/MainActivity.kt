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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var lat = 0.0
    private var lon = 0.0
    private lateinit var locationManager: LocationManager

    override fun onLocationChanged(location: Location) {
        lat = location.latitude
        lon = location.longitude
        val visualizer = findViewById<TextView>(R.id.localizacion)
        visualizer.text = "Latitud: $lat, Longitud: $lon"

    }

    private fun iniciarGPS() {
        try {
            locationManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, 2000, 1f, this )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private val permisoGPSLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { concedido ->
            if (concedido) {
                iniciarGPS()
            } else {
                Toast.makeText(
                    this,
                    "Permiso de ubicación denegado",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        comprobarPermisoGPS()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // We don't need to do anything here for this example
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            val visualizador = findViewById<TextView>(R.id.visualizador)
            visualizador.text = getString(R.string.accelerometer_data, event.values[0], event.values[1], event.values[2])
        }
    }

    private fun comprobarPermisoGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            iniciarGPS()
        } else {
            permisoGPSLauncher.launch( Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}