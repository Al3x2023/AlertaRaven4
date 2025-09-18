# Script de prueba para llamadas automáticas
Write-Host "=== Prueba de Llamadas Automáticas ===" -ForegroundColor Green

# 1. Verificar que la app esté instalada
Write-Host "1. Verificando instalación..." -ForegroundColor Yellow
adb -s SOSPIROAC815123029335 shell pm list packages | findstr alertaraven4

# 2. Iniciar la aplicación
Write-Host "2. Iniciando aplicación..." -ForegroundColor Yellow
adb -s SOSPIROAC815123029335 shell am start -n com.example.alertaraven4/.MainActivity

# 3. Esperar un momento para que se inicialice
Start-Sleep -Seconds 3

# 4. Habilitar llamadas automáticas (simulando configuración)
Write-Host "3. Habilitando llamadas automáticas..." -ForegroundColor Yellow
adb -s SOSPIROAC815123029335 shell am broadcast -a com.example.alertaraven4.SET_AUTO_CALL --ez enabled true

# 5. Simular accidente
Write-Host "4. Simulando accidente..." -ForegroundColor Yellow
adb -s SOSPIROAC815123029335 shell am broadcast -a com.example.alertaraven4.SIMULATE_ACCIDENT --es accident_type "COLLISION" --ef severity 0.9

# 6. Monitorear logs por 10 segundos
Write-Host "5. Monitoreando logs por 10 segundos..." -ForegroundColor Yellow
$job = Start-Job -ScriptBlock {
    adb -s SOSPIROAC815123029335 logcat -s "EmergencyAlertManager:*" "AccidentMonitoringService:*" | Select-String -Pattern "call|emergency|alert" -CaseSensitive:$false
}

Start-Sleep -Seconds 10
Stop-Job $job
Receive-Job $job
Remove-Job $job

Write-Host "=== Prueba completada ===" -ForegroundColor Green