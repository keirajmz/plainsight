package com.example.plainsight

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// Cada foto mantiene su texto, su ubicación física y su carpeta inteligente.
data class DocumentoEscaneado(
    val id: Long,
    val fecha: Long,
    val texto: String,
    val rutaFoto: String,
    val carpeta: String
)

class HistorialDbHelper(context: Context) :
    SQLiteOpenHelper(context, "plainsight_historial.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE escaneos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha INTEGER NOT NULL,
                texto TEXT NOT NULL,
                ruta_foto TEXT NOT NULL,
                carpeta TEXT NOT NULL DEFAULT 'Sin clasificar'
            )
            """.trimIndent()
        )
    }

    // Actualiza el historial anterior sin borrar fotografías ni registros.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE escaneos ADD COLUMN carpeta TEXT NOT NULL DEFAULT 'Sin clasificar'"
            )
        }
    }

    fun guardar(texto: String, rutaFoto: String, carpeta: String = "Sin clasificar"): Long {
        val datos = ContentValues().apply {
            put("fecha", System.currentTimeMillis())
            put("texto", texto)
            put("ruta_foto", rutaFoto)
            put("carpeta", carpeta)
        }
        return writableDatabase.insertOrThrow("escaneos", null, datos)
    }

    fun ultimos(limite: Int = 5): List<DocumentoEscaneado> =
        readableDatabase.query(
            "escaneos", null, null, null, null, null, "id DESC", limite.toString()
        ).use { it.aDocumentos() }

    fun buscar(termino: String, limite: Int = 5): List<DocumentoEscaneado> =
        readableDatabase.query(
            "escaneos", null,
            "texto LIKE ? OR carpeta LIKE ?",
            arrayOf("%$termino%", "%$termino%"),
            null, null, "id DESC", limite.toString()
        ).use { it.aDocumentos() }

    fun porCarpeta(carpeta: String, limite: Int = 5): List<DocumentoEscaneado> =
        readableDatabase.query(
            "escaneos", null, "carpeta = ?", arrayOf(carpeta),
            null, null, "id DESC", limite.toString()
        ).use { it.aDocumentos() }

    fun carpetas(): List<Pair<String, Int>> {
        val resultado = mutableListOf<Pair<String, Int>>()
        readableDatabase.rawQuery(
            "SELECT carpeta, COUNT(*) FROM escaneos GROUP BY carpeta ORDER BY carpeta", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                resultado.add(cursor.getString(0) to cursor.getInt(1))
            }
        }
        return resultado
    }

    private fun Cursor.aDocumentos(): List<DocumentoEscaneado> {
        val encontrados = mutableListOf<DocumentoEscaneado>()
        val indiceId = getColumnIndexOrThrow("id")
        val indiceFecha = getColumnIndexOrThrow("fecha")
        val indiceTexto = getColumnIndexOrThrow("texto")
        val indiceFoto = getColumnIndexOrThrow("ruta_foto")
        val indiceCarpeta = getColumnIndexOrThrow("carpeta")
        while (moveToNext()) {
            encontrados.add(
                DocumentoEscaneado(
                    id = getLong(indiceId),
                    fecha = getLong(indiceFecha),
                    texto = getString(indiceTexto),
                    rutaFoto = getString(indiceFoto),
                    carpeta = getString(indiceCarpeta)
                )
            )
        }
        return encontrados
    }

    // Contar fotografías guardadas
    fun contarFotos(): Int {
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM escaneos",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0)
            } else {
                0
            }
        }
    }

}
