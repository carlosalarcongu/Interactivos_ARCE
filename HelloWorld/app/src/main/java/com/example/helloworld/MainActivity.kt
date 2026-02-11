package com.example.helloworld

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val magnitud = Math.sqrt((x * x + y * y + z * z).toDouble())

            val umbralAccidente = 20.0

            if (magnitud > umbralAccidente) {
                //Toast.makeText(this, "¡Accidente detectado!", Toast.LENGTH_LONG).show()

            } else {
                visualizador.text = getString(R.string.accelerometer_data, x, y, z)
                visualizador.setTextColor(android.graphics.Color.BLACK) // Color normal
                visualizador.textSize = 14f // Tamaño normal
            }
        }
    }
}