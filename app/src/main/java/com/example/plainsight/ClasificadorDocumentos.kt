package com.example.plainsight

import java.text.Normalizer
import java.util.Locale

/**
 * Clasificación inicial por palabras clave del texto reconocido.
 * No es aún un modelo de IA visual: las fotos sin texto identificable
 * quedan en «Sin clasificar».
 */
object ClasificadorDocumentos {
    fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(Locale.forLanguageTag("es-MX")), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")

    fun detectar(texto: String): String {
        val contenido = normalizar(texto)
        if (contenido.isBlank()) return "Sin clasificar"

        // Los pesos más altos son señales más específicas.
        val reglas = linkedMapOf(
            "Menús" to mapOf(
                "menu" to 4, "carta de alimentos" to 4,
                "entradas" to 2, "postres" to 2, "platos fuertes" to 3,
                "bebidas" to 1, "platillos" to 3, "desayunos" to 2
            ),
            "Recibos y facturas" to mapOf(
                "factura" to 5, "ticket" to 4, "recibo" to 5,
                "subtotal" to 3, "iva" to 2, "rfc" to 3,
                "comprobante de pago" to 4
            ),
            "Documentos escolares" to mapOf(
                "tarea" to 3, "examen" to 4, "universidad" to 2,
                "asignatura" to 3, "calificacion" to 3,
                "actividad academica" to 3
            ),
            "Medicamentos" to mapOf(
                "medicamento" to 5, "tabletas" to 2,
                "dosis" to 3, "capsulas" to 2,
                "contraindicaciones" to 4, "farmaceutica" to 3
            )
        )

        val resultados = reglas.mapValues { (_, palabras) ->
            palabras.entries.sumOf { (palabra, puntos) ->
                val patron = Regex("(?<![a-z0-9])${Regex.escape(palabra)}(?![a-z0-9])")
                if (patron.containsMatchIn(contenido)) puntos else 0
            }
        }
        val mejor = resultados.maxByOrNull { it.value }
        return if (mejor != null && mejor.value >= 2) mejor.key else "Sin clasificar"
    }

    fun nombreDirectorio(carpeta: String): String = when (carpeta) {
        "Menús" -> "menus"
        "Recibos y facturas" -> "recibos_y_facturas"
        "Documentos escolares" -> "documentos_escolares"
        "Medicamentos" -> "medicamentos"
        else -> "sin_clasificar"
    }
}


