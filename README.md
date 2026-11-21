# PlainSight

PlainSight es una aplicación para Android diseñada para apoyar a personas ciegas o con discapacidad visual. Su objetivo es facilitar la lectura de documentos y la identificación de información mediante la cámara y un asistente de voz.

## Funcionalidades

- Captura de fotografías mediante un botón con forma de ojo.
- Reconocimiento de texto (OCR) y lectura en voz alta.
- Guardado automático de fotografías en el almacenamiento local.
- Clasificación de documentos en categorías como menús, recibos y documentos escolares.
- Galería para consultar las fotografías guardadas.
- Control de la linterna mediante gestos.
- Reconocimiento de comandos de voz básicos.

## Cómo utilizar la aplicación

| Acción | Función |
| --- | --- |
| Presionar el botón del ojo | Tomar una fotografía y analizarla |
| Deslizar hacia arriba | Encender o apagar la linterna |
| Deslizar hacia la derecha | Abrir la galería |
| Mantener presionada la cámara | Dar un comando de voz |

Las fotografías se guardan localmente para facilitar su consulta sin depender de una conexión a internet.

## Tecnologías utilizadas

- **Kotlin y XML:** desarrollo de la aplicación y su interfaz.
- **Android Studio:** entorno de desarrollo.
- **CameraX:** manejo de la cámara y la linterna.
- **ML Kit:** reconocimiento de texto y etiquetas visuales.
- **TextToSpeech y SpeechRecognizer:** lectura y reconocimiento de voz.
- **SQLite:** almacenamiento del historial y los datos de las fotografías.
- **RecyclerView:** visualización de fotografías en la galería.

## Estructura del proyecto

| Archivo | Descripción |
| --- | --- |
| `MainActivity.kt` | Controla la cámara, los gestos, la captura y los comandos de voz. |
| `SplashActivity.kt` | Muestra la pantalla de bienvenida. |
| `HistorialDbHelper.kt` | Administra la base de datos local. |
| `ClasificadorDocumentos.kt` | Clasifica las fotografías según su contenido. |
| `GaleriaActivity.kt` | Muestra las fotografías guardadas. |
| `AsistenteVisual.kt` | Prepara consultas sobre las imágenes para la futura integración de un modelo de IA. |
| `activity_main.xml` | Diseño de la pantalla principal. |
| `activity_galeria.xml` | Diseño de la galería. |
| `AndroidManifest.xml` | Declara los permisos y las actividades de la aplicación. |

## Instalación y ejecución

1. Clonar el repositorio:

   ```bash
   git clone https://github.com/keirajmz/plainsight.git
   cd plainsight
   git switch feature/backend
   ```

2. Abrir el proyecto en Android Studio y sincronizar Gradle.
3. Conectar un dispositivo Android compatible (Android 10 o superior) con la depuración USB activada.
4. Presionar **Run** y conceder los permisos de cámara y micrófono cuando se soliciten.

## Estado del proyecto

PlainSight se encuentra en desarrollo. La cámara, el reconocimiento de texto, la lectura en voz alta, la linterna y la galería local ya se han probado en un dispositivo Android. La integración de Gemini para responder preguntas sobre fotografías, la búsqueda avanzada y las mejoras de accesibilidad siguen pendientes.

El reconocimiento y la clasificación pueden presentar errores si la fotografía es borrosa o la información no es legible.
