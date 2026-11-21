
package com.example.plainsight

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var vistaCamara: PreviewView
    private lateinit var mensajeCamara: TextView
    private lateinit var detectorGestos: GestureDetector

    private var camara: Camera? = null
    private var cambiandoLinterna = false

    // Solicitar permiso de cámara
    private val pedirPermiso = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permitido ->

        if (permitido) {
            iniciarCamara()
        } else {
            Toast.makeText(
                this,
                "PlainSight necesita permiso de cámara",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        vistaCamara = findViewById(R.id.vistaCamara)
        mensajeCamara = findViewById(R.id.mensajeCamara)

        // Detectar gestos sobre la cámara
        detectorGestos = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onDown(e: MotionEvent): Boolean {
                    return true
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {

                    if (e1 == null) return false

                    val diferenciaX = e2.x - e1.x
                    val diferenciaY = e2.y - e1.y

                    val distanciaMinima =
                        100 * resources.displayMetrics.density

                    // Detectar deslizamiento hacia arriba
                    if (
                        diferenciaY < -distanciaMinima &&
                        abs(diferenciaY) > abs(diferenciaX) &&
                        abs(velocityY) > 300
                    ) {
                        alternarLinterna()
                        return true
                    }

                    return false
                }
            }
        )

        vistaCamara.setOnTouchListener { _, evento ->
            detectorGestos.onTouchEvent(evento)
            true
        }

        comprobarPermiso()
    }

    private fun comprobarPermiso() {

        val permiso = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        )

        if (permiso == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        } else {
            pedirPermiso.launch(Manifest.permission.CAMERA)
        }
    }

    private fun iniciarCamara() {

        val futuroCamara =
            ProcessCameraProvider.getInstance(this)

        futuroCamara.addListener({

            try {
                val proveedor = futuroCamara.get()

                val preview = Preview.Builder().build()

                preview.setSurfaceProvider(
                    vistaCamara.surfaceProvider
                )

                proveedor.unbindAll()

                camara = proveedor.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )

                mensajeCamara.text =
                    "Apunta hacia un documento"

            } catch (e: Exception) {

                mensajeCamara.text =
                    "No se pudo iniciar la cámara"
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // Encender o apagar linterna
    private fun alternarLinterna() {

        val camaraActual = camara ?: return

        // Comprobar si existe flash
        if (!camaraActual.cameraInfo.hasFlashUnit()) {

            mensajeCamara.text =
                "Linterna no disponible"

            Toast.makeText(
                this,
                "Este dispositivo no tiene flash disponible",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (cambiandoLinterna) return

        cambiandoLinterna = true

        val encender =
            camaraActual.cameraInfo.torchState.value != TorchState.ON

        val operacion =
            camaraActual.cameraControl.enableTorch(encender)

        operacion.addListener({

            cambiandoLinterna = false

            try {
                operacion.get()

                val mensaje = if (encender) {
                    "Linterna encendida"
                } else {
                    "Linterna apagada"
                }

                mensajeCamara.text = mensaje

                Toast.makeText(
                    this,
                    mensaje,
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {

                mensajeCamara.text =
                    "No se pudo cambiar la linterna"
            }

        }, ContextCompat.getMainExecutor(this))
    }
}
