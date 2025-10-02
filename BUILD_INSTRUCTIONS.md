# Instrucciones de Build para Play Store

## 🚀 Resumen Rápido

Para generar el build de producción para Play Store, sigue estos pasos:

```bash
# 1. Configurar keystore (solo primera vez)
# Ver KEYSTORE_SETUP.md para instrucciones detalladas

# 2. Configurar variables de entorno
export KEYSTORE_PASSWORD="tu_contraseña"
export KEY_ALIAS="alertaraven"
export KEY_PASSWORD="tu_contraseña_key"

# 3. Hacer ejecutable gradlew (si es necesario)
chmod +x gradlew

# 4. Limpiar build anterior
./gradlew clean

# 5. Generar AAB firmado (recomendado para Play Store)
./gradlew bundleRelease

# 6. El archivo estará en:
# app/build/outputs/bundle/release/app-release.aab
```

## 📦 Tipos de Build

### Android App Bundle (AAB) - RECOMENDADO
```bash
./gradlew bundleRelease
```
- Formato requerido por Play Store desde agosto 2021
- Tamaño de descarga optimizado
- Google Play maneja APKs específicos por dispositivo
- Ubicación: `app/build/outputs/bundle/release/app-release.aab`

### APK Universal
```bash
./gradlew assembleRelease
```
- Para distribución directa o testing
- Un solo APK para todos los dispositivos
- Mayor tamaño que AAB
- Ubicación: `app/build/outputs/apk/release/app-release.apk`

## 🔧 Configuración del Proyecto

### Versión Actual
- **Version Name**: 1.0.0
- **Version Code**: 1
- **Package**: com.alertaraven.emergency
- **Min SDK**: 27 (Android 8.1)
- **Target SDK**: 36

### Optimizaciones Habilitadas
- ✅ ProGuard/R8 optimización
- ✅ Shrinking de recursos
- ✅ Ofuscación de código
- ✅ Optimización de dependencias

## 📝 Pre-requisitos

### 1. Keystore Configurado
Antes del primer build, necesitas crear el keystore:
```bash
# Ver instrucciones detalladas en KEYSTORE_SETUP.md
mkdir -p keystore
keytool -genkey -v -keystore keystore/release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias alertaraven
```

### 2. Variables de Entorno
El build espera estas variables:
- `KEYSTORE_PASSWORD`: Contraseña del archivo keystore
- `KEY_ALIAS`: Alias de la key (default: alertaraven)
- `KEY_PASSWORD`: Contraseña de la key

### 3. Dependencias
Asegúrate de tener:
- JDK 11 o superior
- Android SDK instalado
- Gradle (se usa el wrapper incluido)

## 🔍 Verificar el Build

### Información del AAB/APK
```bash
# Ver tamaño
ls -lh app/build/outputs/bundle/release/app-release.aab

# Analizar contenido del AAB
bundletool build-apks --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=app.apks \
  --mode=universal
```

### Instalar para Testing
```bash
# Desde AAB (requiere bundletool)
bundletool install-apks --apks=app.apks

# Desde APK directo
adb install app/build/outputs/apk/release/app-release.apk
```

### Ver Logs
```bash
adb logcat | grep -i "alertaraven\|crash\|error"
```

## 🐛 Troubleshooting

### Error: "Keystore file not found"
```bash
# Verifica que el keystore existe
ls -l keystore/release.jks

# Si no existe, créalo (ver KEYSTORE_SETUP.md)
```

### Error: "Password incorrect"
```bash
# Verifica las variables de entorno
echo $KEYSTORE_PASSWORD
echo $KEY_ALIAS
echo $KEY_PASSWORD

# Re-exporta si es necesario
export KEYSTORE_PASSWORD="tu_contraseña"
```

### Error de Build
```bash
# Limpiar y rebuild
./gradlew clean
./gradlew bundleRelease --stacktrace

# Ver más detalles
./gradlew bundleRelease --info
```

### ProGuard Rompe Funcionalidad
```bash
# Revisar reglas en app/proguard-rules.pro
# Añadir reglas keep específicas para clases problemáticas

# Deshabilitar temporalmente para debugging
# En app/build.gradle.kts cambiar:
# isMinifyEnabled = false
```

## 📤 Subir a Play Store

### 1. Preparar AAB
```bash
# Generar AAB final
./gradlew clean bundleRelease

# Verificar integridad
ls -lh app/build/outputs/bundle/release/app-release.aab
```

### 2. Subir a Play Console
1. Ir a https://play.google.com/console
2. Seleccionar "Alerta Raven"
3. Ir a "Versiones > Producción"
4. Click "Crear nueva versión"
5. Subir `app-release.aab`
6. Completar notas de versión
7. Revisar y publicar

### 3. Notas de Versión (Primera versión)
```
🚨 Primera versión de Alerta Raven

Características principales:
• Detección automática de accidentes vehiculares
• Alertas por SMS y llamadas a contactos de emergencia
• Compartir ubicación GPS en tiempo real
• Perfil médico completo para emergencias
• Monitoreo continuo en segundo plano
• Optimizado para bajo consumo de batería

Esta es la versión inicial. Tu feedback es importante para mejoras futuras.
```

## 🔄 Actualizaciones Futuras

Para versiones posteriores:

1. Incrementar version code y name en `app/build.gradle.kts`:
```kotlin
versionCode = 2
versionName = "1.0.1"
```

2. Generar nuevo build:
```bash
./gradlew clean bundleRelease
```

3. Subir a Play Store con notas de versión describiendo cambios

## 📊 Análisis del Build

### Ver Tamaño de APK por ABI
```bash
./gradlew :app:assembleRelease

# APKs generados por arquitectura
ls -lh app/build/outputs/apk/release/
```

### Análisis de Dependencias
```bash
# Ver árbol de dependencias
./gradlew :app:dependencies

# Analizar tamaño
./gradlew :app:analyzeReleaseBundle
```

### Verificar Ofuscación
```bash
# Mapping file para traducir stack traces
cat app/build/outputs/mapping/release/mapping.txt
```

## ✅ Checklist Pre-Publicación

Antes de subir a Play Store, verifica:

- [ ] Version code incrementado
- [ ] Keystore backup realizado
- [ ] Build generado sin errores
- [ ] App probada en release mode
- [ ] Funciones críticas verificadas:
  - [ ] Detección de accidentes
  - [ ] Envío de SMS
  - [ ] Llamadas automáticas
  - [ ] Compartir ubicación
  - [ ] Permisos solicitados correctamente
- [ ] ProGuard no rompe funcionalidad
- [ ] Tamaño de AAB razonable (< 50MB ideal)
- [ ] Crashlytics o sistema de logs configurado
- [ ] Privacy policy accesible

## 🔐 Seguridad

**IMPORTANTE**:
- ❌ NUNCA subas el keystore a Git
- ❌ NUNCA compartas las contraseñas
- ✅ Haz backup del keystore
- ✅ Usa variables de entorno
- ✅ Documenta passwords en gestor seguro

## 📞 Ayuda

Si tienes problemas:
1. Revisa los logs: `./gradlew bundleRelease --stacktrace --info`
2. Verifica el archivo: `RELEASE_CHECKLIST.md`
3. Consulta documentación oficial: https://developer.android.com/studio/publish

---

**Última actualización**: 2 de octubre de 2025
**Mantenedor**: [Tu Nombre/Empresa]
