# Registro de Cambios

Todos los cambios notables de este proyecto se documentan en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es/1.1.0/) y este proyecto sigue el [Versionado Semántico](https://semver.org/lang/es/).


## [1.6.6] - 2026-08-23

### 🚀 Añadido / Mejorado
- Cumplimiento de estándares de código de Alenia Studios (Código Limpio).
- Actualización de versión interna en Go y Android.

## [1.6.3] - 2026-06-29

### ✔️ Corregido
- **Advertencia ADB:** Restaurado un mensaje de advertencia limpio y de una sola línea. La salida de error del sistema se suprime; solo se muestra un aviso conciso de `exit status 1` cuando ADB está instalado pero no hay dispositivo conectado.
- **Simulación de Entrada en macOS:** Separado el simulador de entrada específico de Linux (uinput/xdotool) de macOS. macOS ahora usa su propio simulador nativo respaldado por `cliclick` (instalable vía Homebrew), evitando fallos en Apple Silicon y Macs Intel.

### ✅ Añadido
- **Sección de Solución de Problemas (README):** Añadida una explicación clara de la advertencia de inicio de ADB con instrucciones de instalación específicas por plataforma.

## [1.6.2] - 2026-06-29

### ✔️ Corregido
- **Advertencias ADB:** Silenciadas las advertencias verbosas de conectividad ADB y evitado invocar ADB si no está instalado en el PATH del sistema.

## [1.6.1] - 2026-06-29

### ✔️ Corregido
- **Renderizado de Terminal en Windows:** Habilitado el Procesamiento de Terminal Virtual para Windows CMD/PowerShell para renderizar correctamente los colores ANSI y el arte ASCII.

## [1.6.0] - 2026-06-29

### ✔️ Corregido
- **Compilación en Windows:** Resuelto un error crítico de compilación multiplataforma relacionado con `SO_REUSEADDR` implementando lógica de control de socket específica por plataforma.

### ✅ Añadido

**Conectividad y Entrada**
- **Bluetooth de Baja Latencia:** Soporte Bluetooth ultrarrápido y de baja latencia optimizado específicamente para Windows y macOS.
- **Gestos Avanzados:** Nuevos gestos intuitivos para zoom.
- **Integración de Hardware:** El volumen ahora se puede controlar directamente con los botones físicos de volumen.
- **Auto-Conexión:** Emparejamiento fluido vía código QR.
- **Conectividad Versátil:** Soporte completo para modos Wi-Fi y Bluetooth.

**Arquitectura y Clientes**
- **Servidor Go:** Backend robusto utilizando protocolos WebSocket + UDP para transmisión de entrada de ultra baja latencia.
- **Cliente Web Embebido:** Soporte completo de touchpad, scroll, teclado y botones del ratón desde el navegador.
- **Cliente Android Nativo:** Construido desde cero con Jetpack Compose para una experiencia nativa y fluida.
- **Simulación de Entrada:** Simulación de entrada multiplataforma confiable en Linux, Windows y macOS.

**Proyecto e Infraestructura**
- **Pipeline CI/CD:** Flujos de compilación y publicación automatizados con GitHub Actions.
- **Estándares de Comunidad:** Añadidas guía de contribución, código de conducta y política de seguridad.
