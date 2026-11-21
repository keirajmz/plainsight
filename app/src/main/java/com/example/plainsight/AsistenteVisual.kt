package com.example.plainsight
import java.io.File
class AsistenteVisual {
    data class ConsultaVisual(
        val imagen: File,
        val pregunta: String
    )

    object AsistenteVisual {

        fun prepararConsulta(
            rutaFoto: String?,
            pregunta: String
        )
        :
                ConsultaVisual? {

            if (rutaFoto.isNullOrBlank()) return null

            val archivo = File(rutaFoto)

            if (!archivo.isFile || !archivo.canRead()) {
                return null
            }

            if (pregunta.isBlank()) return null

            return ConsultaVisual(
                imagen = archivo,
                pregunta = pregunta.trim()
            )
        }
    }
}