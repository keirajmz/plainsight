
package com.example.plainsight

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class GaleriaActivity : AppCompatActivity() {

    private lateinit var historial: HistorialDbHelper

    private val trabajadores = Executors.newFixedThreadPool(2)
    private val principal = Handler(Looper.getMainLooper())

    private val cache = object : LruCache<String, Bitmap>(8192) {
        override fun sizeOf(
            key: String,
            value: Bitmap
        ): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_galeria)

        historial = HistorialDbHelper(this)

        val galeria = findViewById<RecyclerView>(
            R.id.recyclerGaleria
        )

        val titulo = findViewById<TextView>(
            R.id.textoBuscando
        )

        // Consultar fotografías del almacenamiento local
        val fotografias = historial.ultimos(200)

        titulo.text = if (fotografias.isEmpty()) {
            "Todavía no hay fotografías guardadas"
        } else {
            "${fotografias.size} fotografías guardadas"
        }

        // Cuatro columnas, como en el prototipo
        galeria.layoutManager = GridLayoutManager(this, 4)

        galeria.adapter = FotoAdapter(fotografias)
    }

    private inner class FotoAdapter(
        private val fotografias: List<DocumentoEscaneado>
    ) : RecyclerView.Adapter<FotoAdapter.FotoHolder>() {

        inner class FotoHolder(vista: View) :
            RecyclerView.ViewHolder(vista) {

            val imagen: ImageView =
                vista.findViewById(R.id.imagenGaleria)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): FotoHolder {

            val vista = LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.item_foto_galeria,
                    parent,
                    false
                )

            return FotoHolder(vista)
        }

        override fun getItemCount(): Int {
            return fotografias.size
        }

        override fun onBindViewHolder(
            holder: FotoHolder,
            position: Int
        ) {

            val documento = fotografias[position]
            val ruta = documento.rutaFoto

            holder.imagen.tag = ruta
            holder.imagen.setImageDrawable(null)

            holder.imagen.contentDescription =
                "Fotografía ${position + 1}, carpeta " +
                        documento.carpeta

            val miniatura = cache.get(ruta)

            if (miniatura != null) {
                holder.imagen.setImageBitmap(miniatura)
            } else {
                trabajadores.execute {
                    val imagen = cargarImagen(ruta, 300)

                    if (imagen != null) {
                        cache.put(ruta, imagen)
                    }

                    principal.post {
                        if (!isDestroyed &&
                            holder.imagen.tag == ruta
                        ) {
                            holder.imagen.setImageBitmap(imagen)
                        }
                    }
                }
            }

            holder.itemView.setOnClickListener {
                mostrarFotografia(documento)
            }
        }
    }

    // Cargar versiones reducidas de las imágenes
    private fun cargarImagen(
        ruta: String,
        tamaño: Int
    ): Bitmap? {

        val opciones = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeFile(ruta, opciones)

        if (opciones.outWidth <= 0 ||
            opciones.outHeight <= 0
        ) return null

        var escala = 1

        while (
            opciones.outWidth / escala > tamaño * 2 ||
            opciones.outHeight / escala > tamaño * 2
        ) {
            escala *= 2
        }

        return BitmapFactory.decodeFile(
            ruta,
            BitmapFactory.Options().apply {
                inSampleSize = escala
            }
        )
    }

    // Abrir una fotografía al tocarla
    private fun mostrarFotografia(
        documento: DocumentoEscaneado
    ) {

        val imagen = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription =
                "Fotografía de la carpeta ${documento.carpeta}"
        }

        val dialogo = AlertDialog.Builder(this)
            .setTitle(documento.carpeta)
            .setView(imagen)
            .setPositiveButton("Cerrar", null)
            .create()

        dialogo.show()

        trabajadores.execute {
            val foto = cargarImagen(
                documento.rutaFoto,
                1400
            )

            principal.post {
                if (dialogo.isShowing && !isDestroyed) {
                    imagen.setImageBitmap(foto)
                }
            }
        }
    }

    override fun onDestroy() {
        trabajadores.shutdownNow()
        historial.close()
        super.onDestroy()
    }
}
