# Contribuir a Axon

Ante todo, **gracias** por considerar contribuir a Axon. Cada aportación ayuda a mejorar este proyecto para todos.

## Tabla de Contenidos

- [Código de Conducta](#código-de-conducta)
- [¿Cómo Puedo Contribuir?](#cómo-puedo-contribuir)
- [Primeros Pasos](#primeros-pasos)
- [Configuración de Desarrollo](#configuración-de-desarrollo)
- [Proceso de Pull Request](#proceso-de-pull-request)
- [Guía de Estilo](#guía-de-estilo)

## Código de Conducta

Este proyecto sigue un [Código de Conducta](CODE_OF_CONDUCT.md). Al participar, se espera que lo respetes. Reporta comportamientos inaceptables a [contact.aleniastudios@gmail.com](mailto:contact.aleniastudios@gmail.com).

## ¿Cómo Puedo Contribuir?

### Reportar Errores

- Usa la plantilla de [Reporte de Error](https://github.com/Kaia-Alenia/Axon/issues/new?template=bug_report.md).
- Incluye tu sistema operativo, versión de Go y pasos para reproducir.
- Adjunta logs o capturas de pantalla si es posible.

### Sugerir Funcionalidades

- Usa la plantilla de [Solicitud de Funcionalidad](https://github.com/Kaia-Alenia/Axon/issues/new?template=feature_request.md).
- Explica el problema que resolvería tu funcionalidad.
- Describe tu solución propuesta.

### Contribuciones de Código

1. Busca issues etiquetados con [`good first issue`](https://github.com/Kaia-Alenia/Axon/labels/good%20first%20issue) o [`help wanted`](https://github.com/Kaia-Alenia/Axon/labels/help%20wanted).
2. Comenta en el issue para avisar que estás trabajando en él.
3. Haz fork, crea una rama, programa y envía un PR.

## Primeros Pasos

### Requisitos Previos

**Servidor (Go):**
- Go 1.25+
- Linux: paquete `xdotool` para simulación de entrada
- Windows/macOS: Sin dependencias adicionales

**Android:**
- Android Studio o JDK 17+
- Android SDK 36

### Configuración de Desarrollo

```bash
# Clonar
git clone https://github.com/Kaia-Alenia/Axon.git
cd Axon

# Servidor
cd server
go build -o axon .
./axon

# Android
cd android
./gradlew assembleDebug
```

## Proceso de Pull Request

1. **Fork** el repositorio y crea tu rama desde `main`.
2. **Sigue** la [guía de estilo](#guía-de-estilo) a continuación.
3. **Prueba** tus cambios localmente antes de enviar.
4. **Actualiza** la documentación si cambiaste alguna API pública o comportamiento.
5. **Completa** la plantilla del PR íntegramente.
6. **Espera** la revisión: nos comprometemos a responder en 48 horas.

## Guía de Estilo

### Go (Servidor)

- Sigue el formato estándar de `gofmt`.
- Usa nombres de variables descriptivos.
- Mantén las funciones enfocadas y cortas.
- El código específico de plataforma va en archivos `_linux.go`, `_windows.go` o `_darwin.go`.
- **Todo el código, comentarios e identificadores deben estar en inglés.**

### Kotlin (Android)

- Sigue las [convenciones de código de Kotlin](https://kotlinlang.org/docs/coding-conventions.html).
- Usa las mejores prácticas de Jetpack Compose.
- Mantén los composables pequeños y reutilizables.

### Commits

- Usa mensajes de commit claros y descriptivos.
- Usa tiempo presente: "Add feature" y no "Added feature".
- Referencia issues cuando aplique: `Fix #42`.

## Localización (i18n)

Queremos que Axon sea accesible para todos. El proyecto soporta inglés y español. Si quieres contribuir traducciones para otros idiomas:

### Cliente Android
1. Las traducciones se gestionan mediante archivos de recursos estándar de Android.
2. Localiza las cadenas base en `android/app/src/main/res/values/strings.xml`.
3. Crea un nuevo directorio para tu idioma objetivo (p. ej., `values-fr` para francés) dentro de `android/app/src/main/res/`.
4. Copia `strings.xml` a tu nueva carpeta y traduce los valores.

---

¡Gracias por ayudar a mejorar Axon!
