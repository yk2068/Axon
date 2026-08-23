# Hoja de Ruta de Axon

Este documento describe la visión futura y los planes de desarrollo de Axon.

## Corto Plazo (Mejoras Inmediatas y Estabilidad)

- **Seguridad y Privacidad:**
  - [ ] Implementar un sistema de PIN/Contraseña para emparejamiento seguro (evitar conexiones no deseadas en redes públicas).
  - [ ] Cifrado de Extremo a Extremo (E2EE) para la transmisión de pulsaciones de teclas.
- **Mejoras de UI/UX (Cliente):**
  - [ ] Soporte completo para temas dinámicos (Material You) y palanca manual de modo oscuro/claro.
  - [ ] Perfiles personalizados para sensibilidad y aceleración del touchpad.
- **Nuevos Modos de Control:**
  - [ ] **Modo Presentación:** Interfaz simplificada con botones gigantes de siguiente/anterior para diapositivas (PowerPoint/Keynote) y un puntero láser virtual.
  - [ ] **Controles de Medios Dedicados:** Pantalla específica para control de reproducción (Play/Pausa, saltar pista, controles avanzados de volumen).

## Mediano Plazo (Evolución y Expansión)

- **Soporte de Cliente Multiplataforma:**
  - [ ] **Cliente iOS:** Desarrollar la versión para iPhone/iPad (considerando Kotlin Multiplatform o Swift nativo).
- **Interfaz Gráfica para el Servidor:**
  - [ ] **Aplicación en la Bandeja del Sistema:** Crear una GUI minimalista para el servidor PC (Windows/Mac/Linux) para activar el servidor, ver dispositivos conectados y configurar puertos sin usar la terminal.
- **Entradas Avanzadas:**
  - [ ] **Modo Gamepad (Giroscopio):** Usar los sensores de movimiento del teléfono (giroscopio/acelerómetro) como volante para juegos de carreras o simuladores de vuelo en PC.
  - [ ] **Dictado por Voz:** Usar las capacidades de reconocimiento de voz del teléfono para escribir texto directamente en el PC.

## Largo Plazo (Visión Futura)

- **Expansión a Smart TVs (Android TV / Google TV):**
  - [ ] **Servidor Axon TV:** Adaptar el servidor para ejecutarse nativamente en televisiones con Google TV.
  - [ ] Usar la API *AccessibilityService* para inyectar movimientos del ratón y clics en la interfaz del televisor.
  - [ ] Permitir a los usuarios buscar películas o escribir contraseñas largas en el televisor cómodamente desde su teclado móvil.
- **Espejo de Pantalla Ligero:**
  - [ ] Transmitir la pantalla del PC al teléfono a muy pocos FPS (para no impactar la latencia) para controlar el PC incluso si el monitor está apagado o en otra habitación.
- **Integración de Automatización:**
  - [ ] Atajos o *Intents* para permitir que apps como Tasker o Siri Shortcuts ejecuten macros en el PC (p. ej., "Apagar PC" desde un comando de voz al teléfono).
