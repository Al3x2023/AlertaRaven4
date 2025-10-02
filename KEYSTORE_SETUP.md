# Guía de Configuración del Keystore

## 📋 Prerequisitos

- Java JDK instalado
- Android Studio instalado
- Acceso a terminal/command line

## 🔑 Paso 1: Crear el Keystore

### Opción A: Usando keytool (Terminal)

```bash
# Crear directorio para keystore
mkdir -p keystore

# Generar keystore
keytool -genkey -v -keystore keystore/release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias alertaraven
```

El comando te pedirá:
1. **Password del keystore**: Crea una contraseña segura (mínimo 6 caracteres)
2. **Confirmar password**: Repite la contraseña
3. **Nombre y apellido**: Ingresa tu nombre o nombre de la empresa
4. **Unidad organizacional**: Ejemplo: "Desarrollo"
5. **Organización**: Nombre de tu empresa
6. **Ciudad**: Tu ciudad
7. **Estado/Provincia**: Tu estado
8. **Código de país**: MX, US, ES, etc.
9. **Password de la key**: Puede ser el mismo que el keystore

### Opción B: Usando Android Studio

1. En Android Studio, ve a: `Build > Generate Signed Bundle / APK`
2. Selecciona `Android App Bundle`
3. Click en `Create new...` junto a Key store path
4. Llena el formulario:
   - **Key store path**: `[proyecto]/keystore/release.jks`
   - **Password**: Contraseña segura
   - **Alias**: alertaraven
   - **Password** (key): Puede ser la misma
   - **Validity**: 25 años (por defecto)
   - **Certificate**: Llena tu información

## 🔐 Paso 2: Guardar las Credenciales de Forma Segura

### Método 1: Variables de Entorno (Recomendado para desarrollo local)

#### En Linux/Mac:
```bash
# Añadir a ~/.bashrc o ~/.zshrc
export KEYSTORE_PASSWORD="tu_contraseña_aquí"
export KEY_ALIAS="alertaraven"
export KEY_PASSWORD="tu_contraseña_key_aquí"

# Recargar configuración
source ~/.bashrc  # o ~/.zshrc
```

#### En Windows (PowerShell):
```powershell
# Añadir a $PROFILE
[System.Environment]::SetEnvironmentVariable("KEYSTORE_PASSWORD", "tu_contraseña", "User")
[System.Environment]::SetEnvironmentVariable("KEY_ALIAS", "alertaraven", "User")
[System.Environment]::SetEnvironmentVariable("KEY_PASSWORD", "tu_contraseña_key", "User")
```

### Método 2: Archivo gradle.properties Local (NO SUBIR A GIT)

Crear archivo `gradle.properties` en la raíz del proyecto (ya está en .gitignore):

```properties
KEYSTORE_PASSWORD=tu_contraseña
KEY_ALIAS=alertaraven
KEY_PASSWORD=tu_contraseña_key
KEYSTORE_FILE=keystore/release.jks
```

Luego actualizar `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(project.findProperty("KEYSTORE_FILE") as String? ?: "keystore/release.jks")
        storePassword = project.findProperty("KEYSTORE_PASSWORD") as String? ?: System.getenv("KEYSTORE_PASSWORD")
        keyAlias = project.findProperty("KEY_ALIAS") as String? ?: System.getenv("KEY_ALIAS")
        keyPassword = project.findProperty("KEY_PASSWORD") as String? ?: System.getenv("KEY_PASSWORD")
    }
}
```

### Método 3: Gestor de Contraseñas (Más Seguro)

Guarda las credenciales en:
- 1Password
- LastPass
- Bitwarden
- Google Password Manager

Y agrégalas manualmente cuando sea necesario.

## 📁 Paso 3: Añadir Keystore al .gitignore

Asegúrate que `.gitignore` contiene:

```
# Keystore files
*.jks
*.keystore
keystore/
gradle.properties
local.properties
```

## ✅ Paso 4: Verificar el Keystore

```bash
# Ver información del keystore
keytool -list -v -keystore keystore/release.jks -alias alertaraven

# Te pedirá la contraseña del keystore
```

Verifica que muestre:
- Alias name: alertaraven
- Creation date: Fecha actual
- Entry type: PrivateKeyEntry
- Certificate fingerprints (SHA256, SHA1)

## 🏗️ Paso 5: Generar Build Firmado

### Usando Gradle:

```bash
# Asegúrate que las variables de entorno estén configuradas
./gradlew bundleRelease

# O con credenciales inline (NO recomendado para producción)
./gradlew bundleRelease \
  -Pandroid.injected.signing.store.file=keystore/release.jks \
  -Pandroid.injected.signing.store.password=tu_contraseña \
  -Pandroid.injected.signing.key.alias=alertaraven \
  -Pandroid.injected.signing.key.password=tu_contraseña_key
```

El archivo AAB firmado estará en:
`app/build/outputs/bundle/release/app-release.aab`

### Usando Android Studio:

1. `Build > Generate Signed Bundle / APK`
2. Selecciona `Android App Bundle`
3. Click `Next`
4. Selecciona tu keystore: `keystore/release.jks`
5. Ingresa contraseñas
6. Selecciona build type: `release`
7. Click `Finish`

## 🔄 Paso 6: Backup del Keystore

**EXTREMADAMENTE IMPORTANTE:**

1. **Haz múltiples copias de seguridad del keystore:**
   - En un disco duro externo
   - En almacenamiento en la nube (cifrado)
   - En un USB en un lugar seguro

2. **Documenta las contraseñas de forma segura**

3. **Si pierdes el keystore, NO podrás actualizar tu app en Play Store**

### Ubicaciones recomendadas para backup:

- ☁️ Google Drive (en carpeta cifrada)
- ☁️ Dropbox (en carpeta cifrada)
- 💾 Disco duro externo
- 🔐 Bóveda de contraseñas (1Password, etc.)

## ⚠️ Seguridad: Qué NO Hacer

❌ **NUNCA** subas el keystore a Git/GitHub
❌ **NUNCA** compartas las contraseñas por email/chat
❌ **NUNCA** uses contraseñas simples como "123456"
❌ **NUNCA** guardes las contraseñas en código fuente
❌ **NUNCA** uses el mismo keystore para apps diferentes

## 📝 Troubleshooting

### Error: "Keystore was tampered with, or password was incorrect"
- Verifica que la contraseña sea correcta
- Verifica que no haya espacios al inicio/final
- Intenta con comillas: `"tu_contraseña"`

### Error: "Could not find keystore"
- Verifica la ruta del archivo
- Asegúrate que `keystore/release.jks` existe
- Usa ruta absoluta si es necesario

### Error: "Cannot recover key"
- El password de la key es diferente al del keystore
- Ingresa el password correcto de la key

## 🔄 Rotación de Keys (Avanzado)

Google Play permite rotación de keys usando Play App Signing:

1. Sube tu keystore original a Google Play Console
2. Google genera y maneja un key de producción
3. Tú firmas con tu "upload key"
4. Esto permite recuperación si pierdes tu key

Más información: https://support.google.com/googleplay/android-developer/answer/9842756

## 📞 Información del Keystore Actual

Para referencia rápida, documenta (de forma segura):

```
Keystore Information:
- Archivo: keystore/release.jks
- Alias: alertaraven
- Algorithm: RSA
- Key size: 2048 bits
- Validity: 10000 días (27+ años)
- Fecha de creación: [Fecha]
- Contraseña del keystore: [Guardada en gestor de contraseñas]
- Contraseña de la key: [Guardada en gestor de contraseñas]
- SHA256 Fingerprint: [Obtenido con keytool -list]
```

## ✅ Checklist Final

- [ ] Keystore creado en `keystore/release.jks`
- [ ] Contraseñas documentadas y guardadas de forma segura
- [ ] Keystore añadido a .gitignore
- [ ] Variables de entorno configuradas
- [ ] Backup del keystore realizado (mínimo 2 copias)
- [ ] Build de prueba generado exitosamente
- [ ] Fingerprints SHA256 documentados
- [ ] Equipo informado sobre ubicación del keystore

Una vez completado todo, estás listo para generar builds de producción para Play Store.
