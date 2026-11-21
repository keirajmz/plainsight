package com.example.plainsight

import java.text.Normalizer
import java.util.Locale

object ClasificadorDocumentos {

    // Normalizar palabras para facilitar la búsqueda
    fun normalizar(texto: String): String {
        return Normalizer.normalize(
            texto.lowercase(Locale.forLanguageTag("es-MX")),
            Normalizer.Form.NFD
        ).replace("\\p{M}+".toRegex(), "")
    }

    // Palabras que ayudan a identificar documentos
    private val reglasTexto = linkedMapOf(
        "Menús" to mapOf(
            "menu" to 5,
            "carta de alimentos" to 5,
            "entradas" to 2,
            "postres" to 2,
            "platos fuertes" to 4,
            "platillos" to 3,
            "bebidas" to 1
        ),

        "Recibos y facturas" to mapOf(
            "factura" to 6,
            "ticket" to 5,
            "recibo" to 5,
            "subtotal" to 4,
            "rfc" to 3,
            "iva" to 2,
            "comprobante de pago" to 5
        ),

        "Documentos escolares" to mapOf(
            "tarea" to 4,
            "examen" to 5,
            "universidad" to 3,
            "asignatura" to 4,
            "calificacion" to 4,
            "actividad academica" to 4
        ),

        "Medicamentos" to mapOf(
            "medicamento" to 6,
            "tabletas" to 3,
            "dosis" to 3,
            "capsulas" to 3,
            "contraindicaciones" to 5
        )
    )

    // Etiquetas visuales que puede proporcionar ML Kit
    private val reglasVisuales = mapOf(
        "Menús" to mapOf(
            "menu" to 5,
            "restaurant" to 2,
            "food" to 1,
            "dish" to 1
        ),

        "Recibos y facturas" to mapOf(
            "receipt" to 5,
            "invoice" to 5
        ),

        "Documentos escolares" to mapOf(
            "book" to 2,
            "notebook" to 3,
            "textbook" to 4
        ),

        "Medicamentos" to mapOf(
            "medicine" to 5,
            "pill" to 3,
            "drug" to 3
        )
    )

    // Detectar categoría usando texto y etiquetas visuales
    fun detectar(
        texto: String,
        etiquetas: List<String> = emptyList()
    ): String {

        val contenido = normalizar(texto)

        val etiquetasNormalizadas =
            etiquetas.map { normalizar(it) }.toSet()

        val resultados = mutableMapOf<String, Int>()

        for ((categoria, palabras) in reglasTexto) {

            var puntuacion = 0

            for ((palabra, puntos) in palabras) {
                val palabraNormalizada = normalizar(palabra)

                val patron = Regex(
                    "(?<![a-z0-9])" +
                            Regex.escape(palabraNormalizada) +
                            "(?![a-z0-9])"
                )

                if (patron.containsMatchIn(contenido)) {
                    puntuacion += puntos
                }
            }

            // Combinar evidencia visual
            val reglasImagen =
                reglasVisuales[categoria].orEmpty()

            for ((etiqueta, puntos) in reglasImagen) {
                if (etiqueta in etiquetasNormalizadas) {
                    puntuacion += puntos
                }
            }

            resultados[categoria] = puntuacion
        }

        val mejor = resultados.maxByOrNull { it.value }

        // Si no hay evidencia suficiente, no adivinar
        return if (mejor != null && mejor.value >= 3) {
            mejor.key
        } else {
            "Sin clasificar"
        }
    }

    // Nombres seguros para las carpetas del teléfono
    fun nombreDirectorio(carpeta: String): String {
        return when (carpeta) {
            "Menús" -> "menus"
            "Recibos y facturas" -> "recibos_y_facturas"
            "Documentos escolares" -> "documentos_escolares"
            "Medicamentos" -> "medicamentos"
            else -> "sin_clasificar"
        }
    }
}

