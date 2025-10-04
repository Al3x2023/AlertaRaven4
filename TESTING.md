# Pruebas de la app móvil

Este documento describe las pruebas unitarias e integradas añadidas, cómo ejecutarlas y los resultados observados en el entorno actual.

## Alcance

- Unitarias (JVM):
  - `AccidentMLPredictorTest`: valida la predicción y distribución de probabilidades de `AccidentMLPredictor`.
  - `AccidentType` nombres en mayúsculas: garantiza compatibilidad con el backend.
  - `SensorEventRequestSerializationTest`: verifica la serialización JSON de `SensorEventRequest` con claves y mayúsculas correctas.

- Integradas (instrumentadas en dispositivo):
  - `SettingsManagerInstrumentedTest`: comprueba persistencia del estado del toggle “Enviar datos de entrenamiento” en `SharedPreferences` mediante `SettingsManager`.

## Estructura de archivos de prueba

- Unitarias (JVM):
  - `app/src/test/java/com/example/alertaraven4/ml/AccidentMLPredictorTest.kt`
  - `app/src/test/java/com/example/alertaraven4/api/SensorEventRequestSerializationTest.kt`

- Instrumentadas (Android):
  - `app/src/androidTest/java/com/example/alertaraven4/settings/SettingsManagerInstrumentedTest.kt`

## Cómo ejecutar

1) Unitarias (JVM):
   - Windows: `./gradlew.bat test`
   - Linux/macOS: `./gradlew test`

2) Compilar instrumentadas (sin ejecutar):
   - Windows: `./gradlew.bat assembleAndroidTest`

3) Integradas (instrumentadas, requieren dispositivo/emulador):
   - Windows: `./gradlew.bat connectedDebugAndroidTest`
   - Requiere un dispositivo o emulador conectado y listo (API nivel 28+ recomendado).

## Resultados en este entorno

- Unitarias:
  - Ejecutadas con `./gradlew.bat test` → `BUILD SUCCESSFUL`.
  - Se observaron algunas advertencias de deprecación en componentes UI (no afectan a las pruebas).

- Instrumentadas:
  - Compilación: `./gradlew.bat assembleAndroidTest` → `BUILD SUCCESSFUL`.
  - Ejecución con dispositivo `AC81 - 13`: `./gradlew.bat connectedDebugAndroidTest` → `BUILD SUCCESSFUL`.
  - Se ejecutaron 2 pruebas instrumentadas sin fallos.

## Detalle de casos

- AccidentMLPredictorTest
  - Crea un `AccidentData` representativo y valida que `predictAccidentProbability(...)` devuelve:
    - `confidence` en rango `[0, 1]`.
    - Probabilidades para las cuatro clases principales (`COLLISION`, `SUDDEN_STOP`, `ROLLOVER`, `FALL`).

- AccidentType en mayúsculas
  - Verifica que `AccidentType.values().map { it.name }` estén en mayúsculas para alinear con el `enum` del backend.

- SensorEventRequestSerializationTest
  - Construye un `SensorEventRequest` y valida que la salida JSON contiene:
    - Claves `device_id`, `label`, `predicted_label`, `prediction_confidence`.
    - `label` y `predicted_label` en mayúsculas.

- SettingsManagerInstrumentedTest
  - Inicializa `SettingsManager` con `ApplicationContext`.
  - Activa/desactiva `reportTrainingDataEnabled` y verifica persistencia (`SharedPreferences`).

## Requisitos y buenas prácticas

- Mantener versiones de librerías de prueba según `gradle/libs.versions.toml`.
- Para pruebas instrumentadas:
  - Conectar dispositivo/emulador y habilitar “Depuración USB” (o iniciar AVD).
  - Asegurar permisos y servicios del sistema accesibles si se incorporan casos dependientes de sensores.

## Próximos pasos sugeridos

- Añadir pruebas instrumentadas de UI para `SettingsScreen` utilizando `compose-ui-test` si el entorno lo permite (evitando dependencias de servicios del sistema durante la composición).
- Añadir pruebas de integración de red con un `MockWebServer` para validar `POST /api/v1/sensor-events` end-to-end desde cliente (modo stub).

## Notas

- Se evitó una prueba de UI directa sobre el `SettingsToggleItem` privado para mantener estabilidad; en su lugar, se cubre la persistencia del ajuste mediante `SettingsManager`.
- No se realizaron cambios visuales en la UI; únicamente se añadieron tests y documentación.