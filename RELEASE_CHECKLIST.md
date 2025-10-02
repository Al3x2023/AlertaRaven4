# Lista de Verificación para Publicación en Play Store

## ✅ Configuración Completada

### 1. Build Configuration
- [x] Version code: 1
- [x] Version name: 1.0.0
- [x] Application ID: com.alertaraven.emergency
- [x] Namespace actualizado
- [x] ProGuard configurado y optimizado
- [x] Release build type configurado
- [x] Signing configuration preparada

### 2. Documentación
- [x] Privacy Policy creada (PRIVACY_POLICY.md)
- [x] Play Store listing preparada (PLAY_STORE_LISTING.md)
- [x] Release checklist creada

### 3. Nombre y Branding
- [x] App name: "Alerta Raven"
- [x] Package name actualizado a producción

## 📋 Tareas Pendientes Antes de Publicar

### 4. Keystore y Firma
- [ ] Crear keystore de release:
  ```bash
  keytool -genkey -v -keystore keystore/release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias alertaraven
  ```
- [ ] Guardar contraseñas de forma segura
- [ ] Configurar variables de entorno:
  - KEYSTORE_PASSWORD
  - KEY_ALIAS
  - KEY_PASSWORD

### 5. Iconos y Recursos Gráficos

#### Icono de la App (Requerido)
- [ ] Icono adaptativo 512x512 (ic_launcher.png)
- [ ] Icono redondo (ic_launcher_round)
- [ ] Icono foreground
- [ ] Icono background

#### Capturas de Pantalla (Mínimo 2, máximo 8)
Requeridas para teléfonos:
- [ ] Pantalla principal con dashboard (1242x2688)
- [ ] Detección de accidente con timer
- [ ] Lista de contactos de emergencia
- [ ] Perfil médico completo
- [ ] Configuración de alertas
- [ ] Mapa con ubicación
- [ ] Notificación de emergencia
- [ ] Panel de estadísticas

Opcionales para tablets (7" y 10"):
- [ ] Screenshots optimizadas para tablet

#### Gráfico de Funciones (Feature Graphic)
- [ ] 1024x500 píxeles
- [ ] Banner promocional con logo y tagline

### 6. Textos y Descripciones

- [ ] Título corto (30 caracteres): Verificado en PLAY_STORE_LISTING.md
- [ ] Descripción corta (80 caracteres): Preparada
- [ ] Descripción completa (4000 caracteres): Preparada
- [ ] Notas de la versión (500 caracteres):
  ```
  🚨 Primera versión de Alerta Raven
  - Detección automática de accidentes
  - Alertas por SMS y llamadas
  - Compartir ubicación GPS
  - Perfil médico completo
  - Monitoreo en segundo plano
  ```

### 7. Configuración de Play Console

#### Detalles de la Tienda
- [ ] Crear cuenta de desarrollador de Google Play ($25 USD)
- [ ] Completar información del desarrollador
- [ ] Configurar correo de contacto: support@alertaraven.com
- [ ] Configurar sitio web: www.alertaraven.com
- [ ] Añadir política de privacidad URL

#### Clasificación de Contenido
- [ ] Completar cuestionario de clasificación
- [ ] Seleccionar categoría: Herramientas
- [ ] Confirmar clasificación de edad: 18+

#### Precios y Distribución
- [ ] Configurar países (comenzar con: MX, US, ES, CO, AR, CL)
- [ ] Establecer precio: Gratis
- [ ] Aceptar acuerdos de contenido
- [ ] Confirmar que no contiene anuncios

#### Permisos Sensibles
Explicar el uso de cada permiso:
- [ ] LOCATION: "Para enviar ubicación exacta en emergencias"
- [ ] SEND_SMS: "Para enviar alertas automáticas a contactos"
- [ ] CALL_PHONE: "Para realizar llamadas de emergencia automáticas"
- [ ] FOREGROUND_SERVICE: "Para detección continua de accidentes"
- [ ] ACCESS_BACKGROUND_LOCATION: "Para protección mientras conduce"

### 8. Testing

#### Pruebas Locales
- [ ] Probar release build localmente
- [ ] Verificar que ProGuard no rompe funcionalidad
- [ ] Probar detección de accidentes
- [ ] Verificar envío de SMS
- [ ] Verificar llamadas automáticas
- [ ] Probar en diferentes dispositivos/versiones de Android

#### Track de Prueba Interno
- [ ] Subir primera versión a track interno
- [ ] Añadir testers (mínimo 20 para 14 días)
- [ ] Recopilar feedback
- [ ] Corregir bugs encontrados

#### Track de Prueba Cerrado (Opcional)
- [ ] Crear lista de testers beta
- [ ] Subir a track cerrado
- [ ] Periodo de testing: 7-14 días
- [ ] Revisar crashes y ANRs en Play Console

### 9. Cumplimiento Legal

- [ ] Verificar que Privacy Policy está accesible públicamente
- [ ] Añadir términos de servicio
- [ ] Verificar cumplimiento de GDPR (si aplica en Europa)
- [ ] Añadir descargo de responsabilidad sobre uso de emergencia
- [ ] Verificar leyes locales sobre apps de emergencia

### 10. Marketing y ASO (App Store Optimization)

- [ ] Keywords investigadas y añadidas
- [ ] Competidores analizados
- [ ] Video promocional creado (YouTube)
- [ ] Preparar campaña de lanzamiento
- [ ] Configurar Google Analytics/Firebase

### 11. Build de Producción

```bash
# 1. Limpiar proyecto
./gradlew clean

# 2. Generar AAB (Android App Bundle) - Recomendado
./gradlew bundleRelease

# El archivo estará en:
# app/build/outputs/bundle/release/app-release.aab

# 3. O generar APK (si es necesario)
./gradlew assembleRelease

# El archivo estará en:
# app/build/outputs/apk/release/app-release.apk
```

### 12. Subir a Play Console

- [ ] Iniciar sesión en Play Console
- [ ] Crear nueva aplicación
- [ ] Subir AAB a track de producción
- [ ] Completar todos los detalles de la tienda
- [ ] Añadir capturas de pantalla
- [ ] Revisar y enviar para revisión
- [ ] Tiempo de revisión estimado: 3-7 días

## 📱 Comandos Útiles

### Generar Keystore
```bash
mkdir -p keystore
keytool -genkey -v -keystore keystore/release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias alertaraven
```

### Build de Release
```bash
export KEYSTORE_PASSWORD="tu_contraseña"
export KEY_ALIAS="alertaraven"
export KEY_PASSWORD="tu_contraseña"
./gradlew bundleRelease
```

### Verificar APK
```bash
# Ver tamaño del APK
ls -lh app/build/outputs/apk/release/

# Analizar contenido del APK
./gradlew :app:analyzeReleaseBundle
```

### Testing Local de Release Build
```bash
# Instalar release build
adb install app/build/outputs/apk/release/app-release.apk

# Ver logs en tiempo real
adb logcat | grep AlertaRaven
```

## 🔍 Verificaciones Finales Antes de Enviar

- [ ] Todas las funciones principales funcionan correctamente
- [ ] No hay crashes en la app
- [ ] Permisos se solicitan correctamente
- [ ] UI/UX es intuitiva y clara
- [ ] Textos sin errores ortográficos
- [ ] Iconos y recursos gráficos de alta calidad
- [ ] Privacy policy accesible
- [ ] Información de contacto correcta
- [ ] Screenshots representan bien la app
- [ ] Descripción clara y atractiva

## 📊 Post-Lanzamiento

- [ ] Monitorear crashes en Play Console
- [ ] Responder reviews de usuarios
- [ ] Analizar métricas de instalación
- [ ] Preparar actualizaciones basadas en feedback
- [ ] Promocionar en redes sociales
- [ ] Solicitar reviews de usuarios satisfechos

## 🚨 Notas Importantes

1. **Keystore**: NUNCA subas el keystore a control de versiones. Guárdalo en un lugar seguro.

2. **Contraseñas**: Usa variables de entorno o un sistema de gestión de secretos.

3. **Testing**: No saltes la fase de testing. Los usuarios encontrarán bugs que tú no viste.

4. **Revisión**: Google puede rechazar la app si:
   - Los permisos no están justificados
   - La privacy policy no es clara
   - Hay funcionalidad rota
   - Las screenshots son engañosas

5. **Updates**: Mantén la app actualizada regularmente para mejor ranking.

6. **Legal**: Asegúrate de cumplir con todas las regulaciones locales sobre apps de emergencia.

## 📞 Soporte

Si tienes dudas durante el proceso:
- Documentación oficial: https://developer.android.com/distribute
- Play Console Help: https://support.google.com/googleplay/android-developer
