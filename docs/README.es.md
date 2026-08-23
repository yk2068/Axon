<div align="center">

<img src="assets/logo.png" alt="Logo de Axon" width="600">

# Axon

**Convierte cualquier dispositivo en un touchpad inalámbrico de alto rendimiento, teclado y rueda de desplazamiento para tu PC.**

[![Build](https://github.com/Kaia-Alenia/Axon/actions/workflows/build.yml/badge.svg)](https://github.com/Kaia-Alenia/Axon/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Go](https://img.shields.io/badge/Go-1.25-00ADD8?logo=go&logoColor=white)](https://go.dev/)
[![Android](https://img.shields.io/badge/Android-Kotlin-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Patreon](https://img.shields.io/badge/Patreon-alenia__studios-F96854?logo=patreon&logoColor=white)](https://www.patreon.com/alenia_studios)

<br/>

Una herramienta de entrada remota de código abierto y ultra baja latencia creada por **AXON**. Ejecuta el servidor en tu computadora, conéctate desde la app nativa de Android de tu teléfono o desde el navegador, y experimenta un control fluido y nativo.

</div>

*Lee esto en otros idiomas: [English](../README.md)*

---

## Descripción General y Características Principales

Axon está diseñado desde cero para ofrecer una experiencia de periférico sin errores y sin lag. A diferencia de las apps convencionales de control remoto, Axon utiliza protocolos de red optimizados (UDP y Bluetooth RFCOMM de bajo nivel) para lograr un rendimiento sin precedentes.

- **Ultra Baja Latencia (1ms - 3ms):** Gracias a nuestras implementaciones optimizadas de Bluetooth y USB/ADB, el retardo de entrada es prácticamente inexistente.
- **Asignación Dinámica de Puertos:** Si los puertos estándar están en uso, el servidor cambia automáticamente a asignación dinámica sin colapsar.
- **Gestos Avanzados:** Pinch-to-zoom, desplazamiento multi-táctil y doble toque sostenido para arrastrar.
- **Integración de Hardware:** Usa los botones físicos de volumen de tu dispositivo Android para controlar el volumen del sistema.
- **Compatibilidad Multiplataforma:** Funciona en Linux, Windows y macOS con simulación de entrada nativa.
- **Emparejamiento Sin Configuración:** Escanea el código QR que aparece en tu terminal y conéctate al instante.
- **Triple Conectividad:** Modos Wi-Fi, Bluetooth y USB/ADB.

## Descargas e Instalación

### 1. App Android (Cliente)

Descarga el APK desde la página de lanzamientos:
[axon-android-debug.apk](https://github.com/kaia-alenia/axon/releases/latest/download/axon-android-debug.apk)

### 2. Servidor (Computadora)

#### Opción A: Instalación en Una Línea (Recomendado)

**Linux y macOS:**
```bash
curl -fsSL https://raw.githubusercontent.com/Kaia-Alenia/Axon/main/install.sh | bash
```

**Windows (PowerShell):**
```powershell
iwr -useb https://raw.githubusercontent.com/Kaia-Alenia/Axon/main/install.ps1 | iex
```

Una vez instalado:
```bash
axon                # Inicia el servidor
axon --help         # Muestra ayuda
```

#### Opción B: Descarga Directa del Binario

- **Windows:** [axon-windows-amd64.exe](https://github.com/kaia-alenia/axon/releases/latest/download/axon-windows-amd64.exe)
- **Linux (x86_64):** [axon-linux-amd64](https://github.com/kaia-alenia/axon/releases/latest/download/axon-linux-amd64)
- **Linux (ARM64):** [axon-linux-arm64](https://github.com/kaia-alenia/axon/releases/latest/download/axon-linux-arm64)
- **macOS (Intel):** [axon-darwin-amd64](https://github.com/kaia-alenia/axon/releases/latest/download/axon-darwin-amd64)
- **macOS (Apple Silicon):** [axon-darwin-arm64](https://github.com/kaia-alenia/axon/releases/latest/download/axon-darwin-arm64)

#### Opción C: Compilar desde el Código Fuente

```bash
git clone https://github.com/Kaia-Alenia/Axon.git
cd Axon/server
go build -o axon .
./axon
```

## Comandos del Servidor

```bash
axon                        # Puertos por defecto (6969 TCP, 6970 UDP)
axon --port 7000            # Puerto TCP personalizado
axon --udp-port 7001        # Puerto UDP personalizado
axon --port 0 --udp-port 0  # Auto-asignar puertos
axon --version              # Ver versión
```

## Solución de Problemas

### Advertencia de ADB al Iniciar

```
[ADB] Warning: failed to setup adb reverse for port 6969: exit status 1
```

**Esto no es un error.** Solo aparece cuando ADB está instalado pero no hay dispositivo conectado por USB.

## Contribuciones

Revisa nuestra [Guía de Contribución](CONTRIBUTING.es.md) y el [Código de Conducta](CODE_OF_CONDUCT.md).

## Licencia

Licenciado bajo la **GNU General Public License v3 (GPL v3)**. Ver [LICENSE](LICENSE).

---

<div align="center">

**AXON** · [contact.aleniastudios@gmail.com](mailto:contact.aleniastudios@gmail.com)

</div>
