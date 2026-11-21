package com.example.plainsight

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.Locale
import kotlin.math.abs
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions


class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var rutaUltimaFoto: String? = null
    private lateinit var botonCapturar: ImageButton
    private lateinit var vistaCamara: PreviewView
    private lateinit var mensajeCamara: TextView
    private lateinit var detectorGestos: GestureDetector
    private lateinit var voz: TextToSpeech
    private lateinit var historial: HistorialDbHelper

    private val reconocedor by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val etiquetador by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.DEFAULT_OPTIONS
        )
    }

    private var camara: Camera? = null
    private var captura: ImageCapture? = null
    private var microfono: SpeechRecognizer? = null
    private var vozDisponible = false
    private var tomandoFoto = false
    private var cambiandoLinterna = false
    private var textoActual = ""
    private var escuchando = false


    // La foto queda pendiente hasta e  scuchar una confirmación afirmativa.
    private data class EscaneoPendiente(
        val foto: File,
        val texto: String,
        val carpeta: String
    )
    private var pendiente: EscaneoPendiente? = null
    private var documentosEnVista: List<DocumentoEscaneado> = emptyList()
    private var idUltimaFrase: String? = null
    private var alFinalizarVoz: (() -> Unit)? = null

    private val pedirPermisoCamara = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permitido ->
        if (permitido) iniciarCamara()
        else Toast.makeText(this, "Permiso de cámara necesario", Toast.LENGTH_LONG).show()
    }

    private val pedirPermisoMicrofono = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permitido ->
        if (permitido) escucharComando()
        else hablar("Necesito permiso de micrófono para escuchar tus comandos.")
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vistaCamara = findViewById(R.id.vistaCamara)
        mensajeCamara = findViewById(R.id.mensajeCamara)
        botonCapturar = findViewById(R.id.botonCapturar)

        // El botón del ojo sustituye al doble toque para capturar.
        botonCapturar.setImageResource(R.drawable.boton_1_trim)
        botonCapturar.setOnClickListener {
            botonCapturar.isEnabled = false
            botonCapturar.setImageResource(R.drawable.boton_2_trim)
            tomarFotoYLeer()

            // Parpadeo: vuelve al ojo abierto después de 300 ms.
            botonCapturar.postDelayed({
                botonCapturar.setImageResource(R.drawable.boton_1_trim)
                botonCapturar.isEnabled = true
            }, 300L)
        }
        // Ocultamos el texto en pantalla; todo se comunica mediante voz.
        mensajeCamara.visibility = View.GONE

        historial = HistorialDbHelper(this)
        textoActual = historial.ultimos(1).firstOrNull()?.texto.orEmpty()
        // Descartar capturas temporales sin confirmar de sesiones anteriores.
        File(cacheDir, "escaneos_pendientes").listFiles()?.forEach { it.delete() }
        voz = TextToSpeech(this, this)


        detectorGestos = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onDown(e: MotionEvent): Boolean = true

                // Mantener pulsado: escuchar comandos de voz
                override fun onLongPress(e: MotionEvent) {
                    vistaCamara.performHapticFeedback(
                        HapticFeedbackConstants.LONG_PRESS
                    )
                    solicitarEscucha()
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

                    // DESLIZAR A LA DERECHA: GALERÍA
                    if (
                        diferenciaX > distanciaMinima &&
                        abs(diferenciaX) > abs(diferenciaY) &&
                        abs(velocityX) > 300
                    ) {
                        val intent = Intent(
                            this@MainActivity,
                            GaleriaActivity::class.java
                        )

                        startActivity(intent)
                        return true
                    }

                    // DESLIZAR HACIA ARRIBA: LINTERNA
                    if (
                        diferenciaY < -distanciaMinima &&
                        abs(diferenciaY) > abs(diferenciaX) &&
                        abs(velocityY) > 300
                    ) {
                        Toast.makeText(
                            this@MainActivity,
                            "Deslizamiento hacia arriba detectado",
                            Toast.LENGTH_SHORT
                        ).show()

                        alternarLinterna()
                        return true
                    }

                    return false
                }
            }
        )

// Detectar los gestos sobre la cámara
        vistaCamara.setOnTouchListener { _, evento ->
            detectorGestos.onTouchEvent(evento)
            true
        }

        comprobarPermisoCamara()
    }


    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val idioma = voz.setLanguage(Locale.forLanguageTag("es-MX"))
        vozDisponible = idioma != TextToSpeech.LANG_MISSING_DATA &&
                idioma != TextToSpeech.LANG_NOT_SUPPORTED
        if (!vozDisponible) {
            val respaldo = voz.setLanguage(Locale.forLanguageTag("es"))
            vozDisponible = respaldo != TextToSpeech.LANG_MISSING_DATA &&
                    respaldo != TextToSpeech.LANG_NOT_SUPPORTED
        }
        voz.setSpeechRate(0.95f)

        // Solo iniciamos el micrófono una vez que termina de hablar la pregunta.
        voz.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit

            override fun onDone(utteranceId: String) {
                runOnUiThread {
                    if (utteranceId == idUltimaFrase) {
                        idUltimaFrase = null
                        val accion = alFinalizarVoz
                        alFinalizarVoz = null
                        accion?.invoke()
                    }
                }
            }

            override fun onError(utteranceId: String) {
                runOnUiThread {
                    if (utteranceId == idUltimaFrase) {
                        idUltimaFrase = null
                        alFinalizarVoz = null
                    }
                }
            }
        })
        hablar(
            "Hola, soy PlainSight. Presiona en la parte central inferior de la pantalla para escanear un documento. " +
                    "Mantén pulsada la cámara para darme un comando. " +
                    "Desliza a la derecha para consultar tu historial."
        )
    }

    private fun hablar(texto: String, despues: (() -> Unit)? = null) {
        if (!vozDisponible || texto.isBlank()) return
        alFinalizarVoz = despues
        val partes = dividirTexto(texto)
        val prefijo = "ps_${System.nanoTime()}"
        idUltimaFrase = if (despues != null) "${prefijo}_${partes.lastIndex}" else null
        partes.forEachIndexed { indice, parte ->
            voz.speak(
                parte,
                if (indice == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                "${prefijo}_$indice"
            )
        }
    }

    private fun dividirTexto(texto: String, maximo: Int = 2800): List<String> {
        val partes = mutableListOf<String>()
        var restante = texto.trim()
        while (restante.isNotEmpty()) {
            if (restante.length <= maximo) {
                partes.add(restante)
                break
            }
            val ultimoEspacio = restante.lastIndexOf(' ', maximo - 1)
            val corte = if (ultimoEspacio > maximo / 2) ultimoEspacio + 1 else maximo
            partes.add(restante.substring(0, corte).trim())
            restante = restante.substring(corte).trim()
        }
        return partes
    }

    private fun comprobarPermisoCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) iniciarCamara()
        else pedirPermisoCamara.launch(Manifest.permission.CAMERA)
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            try {
                val proveedor = futuro.get()
                val vistaPrevia = Preview.Builder().build().also {
                    it.setSurfaceProvider(vistaCamara.surfaceProvider)
                }
                val capturaFotos = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                proveedor.unbindAll()
                camara = proveedor.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    vistaPrevia,
                    capturaFotos
                )
                captura = capturaFotos
            } catch (e: Exception) {
                hablar("No pude iniciar la cámara.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun tomarFotoYLeer() {
        if (pendiente != null) {
            hablar("Primero responde si quieres guardar la fotografía anterior: sí o no.",
                ::solicitarEscucha)
            return
        }
        if (tomandoFoto) {
            hablar("Todavía estoy procesando el documento anterior.")
            return
        }
        val capturador = captura ?: run {
            hablar("La cámara todavía no está lista.")
            return
        }
        val carpeta = File(cacheDir, "escaneos_pendientes")
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            hablar("No pude preparar una fotografía temporal.")
            return
        }
        val archivo = File(carpeta, "escaneo_${System.currentTimeMillis()}.jpg")
        val opciones = ImageCapture.OutputFileOptions.Builder(archivo).build()
        alFinalizarVoz = null
        idUltimaFrase = null
        voz.stop()
        tomandoFoto = true

        capturador.takePicture(
            opciones,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                    analizarFotografia(archivo)
                }

                override fun onError(error: ImageCaptureException) {
                    tomandoFoto = false
                    archivo.delete()
                    hablar("No pude tomar la fotografía. Inténtalo de nuevo.")
                }
            }
        )
    }


    private fun analizarFotografia(archivo: File) {
        try {
            val imagen = InputImage.fromFilePath(
                this,
                Uri.fromFile(archivo)
            )

            reconocedor.process(imagen)
                .addOnSuccessListener { resultado ->
                    analizarEtiquetas(
                        archivo,
                        imagen,
                        resultado.text.trim()
                    )
                }
                .addOnFailureListener {
                    analizarEtiquetas(archivo, imagen, "")
                }

        } catch (e: Exception) {
            guardarYLeerAutomaticamente(
                archivo,
                "",
                emptyList()
            )
            tomandoFoto = false
        }
    }

    private fun analizarEtiquetas(
        archivo: File,
        imagen: InputImage,
        texto: String
    ) {

        try {
            etiquetador.process(imagen)
                .addOnSuccessListener { resultados ->

                    val etiquetas = resultados
                        .filter { it.confidence >= 0.65f }
                        .map { it.text }

                    // Clasificar, guardar y leer el documento
                    guardarYLeerAutomaticamente(
                        archivo,
                        texto,
                        etiquetas
                    )
                }
                .addOnFailureListener {

                    // Guardar aunque falle el análisis visual
                    guardarYLeerAutomaticamente(
                        archivo,
                        texto,
                        emptyList()
                    )
                }
                .addOnCompleteListener {
                    tomandoFoto = false
                }

        } catch (e: Exception) {
            guardarYLeerAutomaticamente(
                archivo,
                texto,
                emptyList()
            )
            tomandoFoto = false
        }
    }


    private fun proponerGuardado(foto: File, texto: String) {
        guardarYLeerAutomaticamente(foto, texto)
    }

    // Clasificar, guardar y leer sin pedir confirmación
    private fun guardarYLeerAutomaticamente(
        fotoTemporal: File,
        texto: String,
        etiquetas: List<String> = emptyList()
    ) {
        // Identificar la categoría del documento
        val carpeta = ClasificadorDocumentos.detectar(
            texto,
            etiquetas
        )

        // Preparar el documento para guardarlo
        pendiente = EscaneoPendiente(
            fotoTemporal,
            texto,
            carpeta
        )

        // Guardarlo directamente, sin preguntar
        guardarConfirmado()
    }

    private fun interpretarConfirmacion(frase: String) {
        val normalizada = ClasificadorDocumentos.normalizar(frase).trim()
        when {
            normalizada == "si" || normalizada == "claro" ||
                    normalizada == "confirmar" || normalizada == "guardar" ||
                    normalizada == "si guardala" || normalizada == "si guardar" ->
                guardarConfirmado()

            normalizada == "no" || normalizada == "cancelar" ||
                    normalizada == "no guardar" -> cancelarGuardado()

            normalizada.contains("sin clasificar") -> guardarConfirmado("Sin clasificar")

            else -> hablar(
                "No entendí la respuesta. Di sí para guardar o no para descartar la fotografía.",
                ::solicitarEscucha
            )
        }
    }

    private fun guardarConfirmado(carpetaElegida: String? = null) {
        val actual = pendiente ?: return
        val categoria = carpetaElegida ?: actual.carpeta
        val subcarpeta = File(
            File(filesDir, "escaneos"),
            ClasificadorDocumentos.nombreDirectorio(categoria)
        )
        try {
            if (!subcarpeta.exists() && !subcarpeta.mkdirs()) {
                hablar("No pude crear esa carpeta. La fotografía sigue pendiente.")
                return
            }
            val destino = File(subcarpeta, actual.foto.name)
            if (actual.foto != destino) {
                // Si falla mover, copiar al almacenamiento privado permanente.
                if (!actual.foto.renameTo(destino)) {
                    actual.foto.copyTo(destino, overwrite = false)
                    actual.foto.delete()
                }
                // Permite reintentar si la base de datos falla.
                pendiente = actual.copy(foto = destino)
            }

            try {
                historial.guardar(actual.texto, destino.absolutePath, categoria)
                rutaUltimaFoto = destino.absolutePath
            } catch (e: Exception) {
                // No se elimina la foto: así evitamos perder el escaneo.
                hablar("La foto está en el teléfono, pero no pude registrarla en el historial.")
                return
            }
            pendiente = null
            documentosEnVista = emptyList()
            textoActual = actual.texto
            if (textoActual.isBlank()) {
                hablar("Fotografía guardada en $categoria. No pude reconocer texto para leer.")
            } else {
                hablar("Fotografía guardada en $categoria. Ahora te leo el texto: $textoActual")
            }
        } catch (e: Exception) {
            hablar("No pude guardar la fotografía. Mantén pulsado y vuelve a responder.")
        }
    }

    private fun cancelarGuardado() {
        val actual = pendiente ?: return
        actual.foto.delete()
        pendiente = null
        hablar("Entendido. No guardé esa fotografía en el historial.")
    }

    private fun solicitarEscucha() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) escucharComando()
        else pedirPermisoMicrofono.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun escucharComando() {
        if (escuchando) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            hablar("Este teléfono no tiene un servicio de reconocimiento de voz disponible.")
            return
        }
        // Evita que el micrófono reconozca nuestra propia narración.
        idUltimaFrase = null
        alFinalizarVoz = null
        voz.stop()
        if (microfono == null) {
            microfono = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit

                    override fun onError(error: Int) {
                        escuchando = false
                        if (pendiente != null) {
                            hablar("No escuché la respuesta. Mantén pulsada la cámara y di sí o no.")
                        } else if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ) hablar("No te escuché bien. Mantén pulsado e inténtalo otra vez.")
                        else hablar("No pude escuchar el comando. Intenta nuevamente.")
                    }

                    override fun onResults(results: Bundle?) {
                        escuchando = false
                        val texto = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (texto.isNullOrBlank()) hablar("No entendí el comando.")
                        else procesarComando(texto)
                    }
                })
            }
        }
        val intento = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            escuchando = true
            microfono?.startListening(intento)
        } catch (e: Exception) {
            escuchando = false
            hablar("No pude activar el micrófono.")
        }
    }

    private fun procesarComando(fraseOriginal: String) {
        val frase = ClasificadorDocumentos.normalizar(fraseOriginal)
        if (pendiente != null) {
            interpretarConfirmacion(frase)
            return
        }

        val numeroDocumento = Regex(
            "(?:lee|leer) (?:el )?documento (uno|dos|tres|cuatro|cinco|[1-5])"
        ).find(frase)?.groupValues?.getOrNull(1)

        when {
            frase.contains("escanea") || frase.contains("escanear") ||
                    frase.contains("toma una foto") || frase.contains("tomar foto") -> {
                tomarFotoYLeer()
            }
            frase.contains("detente") || frase.contains("para de leer") ||
                    frase.contains("silencio") || frase.contains("pausa") -> {
                voz.stop()
                hablar("Lectura detenida.")
            }
            frase.startsWith("abre carpeta ") || frase.startsWith("abrir carpeta ") ||
                    frase.startsWith("carpeta ") -> {
                val solicitada = frase.substringAfter("carpeta ").trim()
                abrirCarpeta(solicitada)
            }
            frase.startsWith("busca ") || frase.startsWith("buscar ") -> {
                val termino = frase.substringAfter(' ').trim()
                buscarEnHistorial(termino)
            }
            numeroDocumento != null -> {
                val numero = when (numeroDocumento) {
                    "uno" -> 1
                    "dos" -> 2
                    "tres" -> 3
                    "cuatro" -> 4
                    "cinco" -> 5
                    else -> numeroDocumento.toIntOrNull() ?: 1
                }
                leerDocumentoDelHistorial(numero)
            }
            frase.contains("historial") -> anunciarHistorial()
            frase.contains("repite") || frase.contains("lee otra vez") -> {
                if (textoActual.isBlank()) hablar("No tengo texto para repetir.")
                else hablar(textoActual)
            }
            frase.contains("lee el ultimo") || frase.contains("ultimo documento") -> {
                leerDocumentoDelHistorial(1)
            }
            frase.contains("ayuda") || frase.contains("que puedes hacer") -> {
                hablar(
                    "Puedo escanear, leer y guardar documentos. " +
                            "Puedes decir escanea, repite, detente, historial, " +
                            "buscar seguido de una palabra, abrir carpeta menús, " +
                            "o leer documento dos."
                )
            }

            else -> {
                val esPreguntaVisual =
                    frase.startsWith("que ") ||
                            frase.startsWith("de que ") ||
                            frase.startsWith("cual ") ||
                            frase.startsWith("cuanto ") ||
                            frase.startsWith("cuantos ") ||
                            frase.startsWith("como es ") ||
                            frase.startsWith("donde esta ") ||
                            frase.contains("que hay en la foto") ||
                            frase.contains("que ves")

                if (esPreguntaVisual) {
                    consultarFotografiaActual(fraseOriginal)
                } else {
                    hablar(
                        "No reconozco esa instrucción. " +
                                "Puedes decir ayuda para conocer los comandos."
                    )
                }
            }

        }
    }


    private fun consultarFotografiaActual(pregunta: String) {

        // Recuperar la última fotografía disponible
        val ruta = rutaUltimaFoto
            ?: historial.ultimos(1).firstOrNull()?.rutaFoto

        // La fotografía y la pregunta ya están preparadas.
        // Aquí conectaremos el modelo de inteligencia visual.
        hablar(
            "He recibido tu pregunta sobre la fotografía. " +
                    "Todavía necesito conectar el análisis visual " +
                    "para darte una respuesta basada en la imagen."
        )
    }


    private fun anunciarHistorial() {
        documentosEnVista = historial.ultimos(5)
        val carpetas = historial.carpetas()
        if (carpetas.isEmpty()) {
            hablar("Tu historial todavía está vacío.")
            return
        }
        val resumen = carpetas.joinToString(". ") { (nombre, cantidad) ->
            "$nombre: $cantidad fotografías"
        }
        hablar("Estas son tus carpetas: $resumen. Puedes decir abre carpeta menús " +
                "o buscar seguido de una palabra.")
    }

    private fun abrirCarpeta(nombre: String) {
        val carpeta = historial.carpetas().map { it.first }
            .firstOrNull {
                ClasificadorDocumentos.normalizar(it)
                    .contains(ClasificadorDocumentos.normalizar(nombre))
            }
        if (carpeta == null || nombre.isBlank()) {
            hablar("No encontré esa carpeta. Di historial para escuchar las carpetas.")
            return
        }
        val documentos = historial.porCarpeta(carpeta)
        documentosEnVista = documentos
        val resumen = documentos.mapIndexed { indice, documento ->
            "Documento ${indice + 1}: ${titulo(documento.texto)}"
        }.joinToString(". ")
        hablar("Carpeta $carpeta. $resumen. Puedes decir leer documento uno, dos, tres, cuatro o cinco.")
    }

    private fun buscarEnHistorial(termino: String) {
        if (termino.isBlank()) {
            hablar("Dime qué palabra quieres buscar.")
            return
        }
        val encontrados = historial.buscar(termino, 5)
        documentosEnVista = encontrados
        if (encontrados.isEmpty()) {
            hablar("No encontré documentos con la palabra $termino.")
        } else {
            val resumen = encontrados.mapIndexed { indice, documento ->
                "Documento ${indice + 1}: ${titulo(documento.texto)}"
            }.joinToString(". ")
            hablar("Encontré ${encontrados.size} documentos. $resumen. Puedes decir leer documento uno.")
        }
    }

    private fun leerDocumentoDelHistorial(numero: Int) {
        val lista = documentosEnVista.ifEmpty { historial.ultimos(5) }
        val documento = lista.getOrNull(numero - 1)
        if (documento == null) {
            hablar("No existe ese número de documento en la lista actual.")
            return
        }
        textoActual = documento.texto
        if (textoActual.isBlank()) hablar("Esta fotografía no tiene texto reconocido.")
        else hablar("Leyendo documento $numero. $textoActual")
    }

    private fun titulo(texto: String): String =
        texto.lineSequence().firstOrNull { it.isNotBlank() }
            ?.trim()?.take(60)?.replace('\n', ' ') ?: "Fotografía sin texto reconocido"

    private fun alternarLinterna() {
        val camaraActual = camara ?: return
        if (!camaraActual.cameraInfo.hasFlashUnit()) {
            hablar("Este teléfono no tiene linterna disponible.")
            return
        }
        if (cambiandoLinterna) return
        cambiandoLinterna = true
        val encender = camaraActual.cameraInfo.torchState.value != TorchState.ON
        val operacion = camaraActual.cameraControl.enableTorch(encender)
        operacion.addListener({
            cambiandoLinterna = false
            try {
                operacion.get()
                hablar(if (encender) "Linterna encendida" else "Linterna apagada")
            } catch (e: Exception) {
                hablar("No pude cambiar la linterna.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        // Una captura sin respuesta no entra al historial.
        pendiente?.foto?.takeIf { it.parentFile == File(cacheDir, "escaneos_pendientes") }?.delete()
        microfono?.destroy()
        reconocedor.close()
        etiquetador.close()
        historial.close()
        if (::voz.isInitialized) {
            voz.stop()
            voz.shutdown()
        }
        super.onDestroy()
    }
}
