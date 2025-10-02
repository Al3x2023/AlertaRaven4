import asyncio
import logging
from datetime import datetime
from typing import List, Optional, Dict, Any
from uuid import uuid4
import os

from fastapi import FastAPI, HTTPException, Depends, BackgroundTasks, WebSocket, WebSocketDisconnect, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from fastapi.staticfiles import StaticFiles
from fastapi.responses import HTMLResponse, FileResponse
from pydantic import BaseModel, Field

from models import *
from database import Database
from websocket_manager import WebSocketManager, heartbeat_task

# Configuración de logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="AlertaRaven API",
    description="API para recibir y gestionar alertas de emergencia de la aplicación AlertaRaven",
    version="1.0.0"
)

# Configurar CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # En producción, especificar dominios específicos
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Inicializar componentes
db = Database()
websocket_manager = WebSocketManager()

# Configuración de autenticación
security = HTTPBearer()
VALID_API_KEYS = {"alertaraven_mobile_key_2024"}  # En producción usar variables de entorno

def get_api_key(api_key: str):
    return api_key in VALID_API_KEYS

async def verify_api_key(credentials: HTTPAuthorizationCredentials = Depends(security)):
    if credentials.credentials not in VALID_API_KEYS:
        raise HTTPException(status_code=401, detail="API key inválida")
    return credentials.credentials

# Modelos de datos para la API
class LocationData(BaseModel):
    latitude: float = Field(..., description="Latitud de la ubicación")
    longitude: float = Field(..., description="Longitud de la ubicación")
    accuracy: Optional[float] = Field(None, description="Precisión en metros")
    altitude: Optional[float] = Field(None, description="Altitud")
    speed: Optional[float] = Field(None, description="Velocidad")
    timestamp: Optional[str] = Field(None, description="Timestamp de la ubicación")

class MedicalInfo(BaseModel):
    blood_type: Optional[str] = Field(None, description="Tipo de sangre")
    allergies: Optional[List[str]] = Field(None, description="Lista de alergias")
    medications: Optional[List[str]] = Field(None, description="Lista de medicamentos")
    medical_conditions: Optional[List[str]] = Field(None, description="Condiciones médicas")
    emergency_medical_info: Optional[str] = Field(None, description="Información médica adicional")

class EmergencyContact(BaseModel):
    name: str = Field(..., description="Nombre del contacto")
    phone: str = Field(..., description="Número de teléfono")
    relationship: Optional[str] = Field(None, description="Relación con el usuario")
    is_primary: bool = Field(False, description="Si es contacto primario")

class AccidentEventData(BaseModel):
    accident_type: str = Field(..., description="Tipo de accidente")
    timestamp: str = Field(..., description="Timestamp del evento como string")
    confidence: float = Field(..., ge=0.0, le=1.0, description="Nivel de confianza (0.0-1.0)")
    acceleration_magnitude: float = Field(..., description="Magnitud de aceleración")
    gyroscope_magnitude: float = Field(..., description="Magnitud del giroscopio")
    location_data: Optional[LocationData] = Field(None, description="Datos de ubicación")
    additional_sensor_data: Optional[Dict[str, Any]] = Field(None, description="Datos adicionales de sensores")

class EmergencyAlertRequest(BaseModel):
    device_id: str = Field(..., description="ID único del dispositivo")
    user_id: Optional[str] = Field(None, description="ID del usuario")
    accident_event: AccidentEventData = Field(..., description="Datos del evento de accidente")
    medical_info: Optional[MedicalInfo] = Field(None, description="Información médica del usuario")
    emergency_contacts: List[EmergencyContact] = Field(default_factory=list, description="Contactos de emergencia")
    api_key: str = Field(..., description="Clave de API para autenticación")

class AlertResponse(BaseModel):
    alert_id: str = Field(..., description="ID único de la alerta")
    status: str = Field(..., description="Estado de la alerta")
    message: str = Field(..., description="Mensaje de respuesta")
    timestamp: datetime = Field(..., description="Timestamp de procesamiento")

@app.on_event("startup")
async def startup_event():
    """Inicializar la base de datos al arrancar"""
    await db.init_db()
    # Iniciar tarea de heartbeat para WebSockets
    asyncio.create_task(heartbeat_task())
    logger.info("API AlertaRaven iniciada correctamente")

@app.on_event("shutdown")
async def shutdown_event():
    """Limpiar recursos al cerrar"""
    await db.close()
    logger.info("API AlertaRaven cerrada correctamente")

@app.get("/")
async def root():
    """Endpoint raíz de la API"""
    return {
        "message": "AlertaRaven API - Sistema de Alertas de Emergencia",
        "version": "1.0.0",
        "status": "active",
        "timestamp": datetime.now()
    }

@app.get("/health")
async def health_check():
    """Verificación de salud de la API"""
    try:
        # Verificar conexión a la base de datos
        db_healthy = await db.health_check()
        ws_stats = await websocket_manager.get_connection_stats()
        
        return {
            "status": "healthy" if db_healthy else "unhealthy",
            "database": "connected" if db_healthy else "disconnected",
            "timestamp": datetime.now(),
            "websocket_connections": ws_stats["total_connections"],
            "services": {
                "database": db_healthy,
                "websockets": True
            }
        }
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        raise HTTPException(status_code=503, detail="Service unavailable")

@app.post("/api/v1/emergency-alert-debug")
async def receive_emergency_alert_debug(request_data: dict):
    """
    Endpoint temporal para debugging - recibe cualquier JSON y lo registra
    """
    logger.info(f"=== DEBUG ENDPOINT ===")
    logger.info(f"Datos recibidos (raw): {request_data}")
    logger.info(f"Tipo de datos: {type(request_data)}")
    return {"status": "received", "message": "Datos registrados para debugging"}

@app.post("/api/v1/emergency-alert", response_model=AlertResponse)
async def receive_emergency_alert(
    alert_request: EmergencyAlertRequest,
    background_tasks: BackgroundTasks,
    api_key: str = Depends(verify_api_key)
):
    """
    Recibe una alerta de emergencia desde la aplicación móvil
    """
    try:
        logger.info(f"Recibida alerta de emergencia del dispositivo: {alert_request.device_id}")
        logger.info(f"Datos completos recibidos: {alert_request.dict()}")
        
        # Crear objeto de alerta
        try:
            # Convertir el tipo de accidente a mayúsculas para que coincida con el enum
            accident_type_str = alert_request.accident_event.accident_type.upper()
            logger.info(f"Convirtiendo tipo de accidente: '{alert_request.accident_event.accident_type}' -> '{accident_type_str}'")
            accident_type_enum = AccidentType(accident_type_str)
            logger.info(f"Enum creado exitosamente: {accident_type_enum}")
        except ValueError as e:
            logger.warning(f"Tipo de accidente desconocido: {alert_request.accident_event.accident_type}, error: {e}")
            accident_type_enum = AccidentType.UNKNOWN
        
        # Parsear el timestamp (viene como string ISO)
        try:
            # Intentar parsear como ISO format
            timestamp = datetime.fromisoformat(alert_request.accident_event.timestamp.replace('Z', '+00:00'))
        except ValueError:
            try:
                # Fallback: intentar como timestamp en milisegundos
                timestamp = datetime.fromtimestamp(float(alert_request.accident_event.timestamp) / 1000)
            except (ValueError, TypeError):
                logger.warning(f"No se pudo parsear timestamp: {alert_request.accident_event.timestamp}")
                timestamp = datetime.now()
            
        alert = EmergencyAlert(
            alert_id=str(uuid4()),
            device_id=alert_request.device_id,
            user_id=alert_request.user_id,
            accident_type=accident_type_enum,
            timestamp=timestamp,
            location_data=alert_request.accident_event.location_data.dict() if alert_request.accident_event.location_data else None,
            medical_info=alert_request.medical_info.dict() if alert_request.medical_info else None,
            emergency_contacts=[contact.dict() for contact in alert_request.emergency_contacts],
            confidence=alert_request.accident_event.confidence,
            acceleration_magnitude=alert_request.accident_event.acceleration_magnitude,
            gyroscope_magnitude=alert_request.accident_event.gyroscope_magnitude,
            status=AlertStatus.RECEIVED,
            created_at=datetime.now(),
            updated_at=datetime.now()
        )
        
        # Guardar en base de datos
        alert_id = await db.save_alert(alert)
        
        # Procesar alerta en background
        background_tasks.add_task(process_emergency_alert, alert_id)
        
        # Notificar via WebSocket a clientes conectados
        await websocket_manager.notify_new_alert({
            "alert_id": alert_id,
            "device_id": alert_request.device_id,
            "accident_type": alert_request.accident_event.accident_type,
            "confidence": alert_request.accident_event.confidence,
            "timestamp": alert.timestamp.isoformat(),
            "location": alert_request.accident_event.location_data.dict() if alert_request.accident_event.location_data else None
        })
        
        logger.info(f"Alerta procesada exitosamente. ID: {alert_id}")
        
        return AlertResponse(
            alert_id=alert_id,
            status="received",
            message="Alerta de emergencia recibida y procesada correctamente",
            timestamp=datetime.now()
        )
        
    except ValueError as e:
        logger.error(f"Error de validación: {e}")
        raise HTTPException(status_code=400, detail=f"Datos inválidos: {str(e)}")
    except HTTPException:
        raise
    except Exception as e:
        import traceback
        logger.error(f"Error procesando alerta de emergencia: {e}")
        logger.error(f"Traceback: {traceback.format_exc()}")
        raise HTTPException(status_code=500, detail="Error interno del servidor")

@app.get("/api/v1/alerts/{alert_id}")
async def get_alert_status(alert_id: str):
    """
    Obtiene el estado de una alerta específica
    """
    try:
        alert = await db.get_alert(alert_id)
        if not alert:
            raise HTTPException(status_code=404, detail="Alerta no encontrada")
        
        return {
            "alert_id": alert_id,
            "status": alert.status.value,
            "timestamp": alert.timestamp,
            "accident_type": alert.accident_type.value,
            "confidence": alert.confidence
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error obteniendo estado de alerta {alert_id}: {e}")
        raise HTTPException(status_code=500, detail="Error interno del servidor")

@app.get("/api/v1/alerts")
async def get_alerts(
    limit: int = 50,
    offset: int = 0,
    device_id: Optional[str] = None,
    status: Optional[str] = None
):
    """
    Obtiene lista de alertas con filtros opcionales
    """
    try:
        alerts = await db.get_alerts(
            limit=limit,
            offset=offset,
            device_id=device_id,
            status=status
        )
        stats = await db.get_alert_statistics()
        
        return {
            "alerts": [
                {
                    "alert_id": alert.alert_id,
                    "device_id": alert.device_id,
                    "user_id": alert.user_id,
                    "accident_type": alert.accident_type.value,
                    "status": alert.status.value,
                    "confidence": alert.confidence,
                    "timestamp": alert.timestamp,
                    "created_at": alert.created_at,
                    "location_data": alert.location_data
                }
                for alert in alerts
            ],
            "pagination": {
                "limit": limit,
                "offset": offset,
                "total": len(alerts)
            },
            "statistics": stats
        }
    except Exception as e:
        logger.error(f"Error obteniendo alertas: {e}")
        raise HTTPException(status_code=500, detail="Error interno del servidor")

async def process_emergency_alert(alert_id: str):
    """
    Procesa una alerta de emergencia en background
    """
    try:
        alert = await db.get_alert(alert_id)
        if not alert:
            logger.error(f"Alerta {alert_id} no encontrada para procesamiento")
            return
        
        logger.info(f"Procesando alerta {alert_id}")
        
        # Simular procesamiento inicial
        await asyncio.sleep(1)
        
        # Actualizar estado a procesando
        await db.update_alert_status(alert_id, AlertStatus.PROCESSING)
        await websocket_manager.notify_alert_status_change(
            alert_id, "received", "processing", alert.device_id
        )
        
        # Simular validación y procesamiento de datos
        await asyncio.sleep(2)
        
        # Determinar si la alerta es válida basado en la confianza
        if alert.confidence >= 0.7:
            # Alta confianza - marcar como confirmada
            await db.update_alert_status(alert_id, AlertStatus.CONFIRMED)
            await websocket_manager.notify_alert_status_change(
                alert_id, "processing", "confirmed", alert.device_id
            )
            
            # Simular envío de notificaciones a contactos de emergencia
            await asyncio.sleep(1)
            
            await db.update_alert_status(alert_id, AlertStatus.COMPLETED)
            await websocket_manager.notify_alert_status_change(
                alert_id, "confirmed", "completed", alert.device_id
            )
        else:
            # Baja confianza - marcar como pendiente de revisión
            await db.update_alert_status(alert_id, AlertStatus.PENDING_REVIEW)
            await websocket_manager.notify_alert_status_change(
                alert_id, "processing", "pending_review", alert.device_id
            )
        
        logger.info(f"Alerta {alert_id} procesada exitosamente")
        
    except Exception as e:
        logger.error(f"Error procesando alerta {alert_id}: {e}")
        await db.update_alert_status(alert_id, AlertStatus.FAILED)
        await websocket_manager.notify_alert_status_change(
            alert_id, "processing", "failed", None
        )

# ================================
# ENDPOINTS PARA APLICACIÓN WEB
# ================================

# Configurar archivos estáticos
app.mount("/static", StaticFiles(directory="static"), name="static")

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    """Endpoint WebSocket para notificaciones en tiempo real"""
    await websocket_manager.connect(websocket)
    try:
        while True:
            # Mantener la conexión activa
            await websocket.receive_text()
    except WebSocketDisconnect:
        websocket_manager.disconnect(websocket)

@app.get("/", response_class=HTMLResponse)
# Añade estos endpoints a tu archivo FastAPI existente

@app.get("/dashboard", response_class=HTMLResponse)
async def dashboard():
    """Página principal del dashboard"""
    return FileResponse("static/index.html")

@app.get("/components/{component_name}")
async def get_component(component_name: str):
    """Servir componentes individuales"""
    component_path = f"static/components/{component_name}.html"
    if os.path.exists(component_path):
        return FileResponse(component_path)
    else:
        raise HTTPException(status_code=404, detail="Componente no encontrado")

# Endpoints específicos para cada página (opcional)
@app.get("/alerts-page", response_class=HTMLResponse)
async def alerts_page():
    """Página de alertas"""
    return FileResponse("static/index.html")

@app.get("/map-page", response_class=HTMLResponse)
async def map_page():
    """Página del mapa"""
    return FileResponse("static/index.html")

@app.get("/statistics-page", response_class=HTMLResponse)
async def statistics_page():
    """Página de estadísticas"""
    return FileResponse("static/index.html")

@app.get("/system-page", response_class=HTMLResponse)
async def system_page():
    """Página del sistema"""
    return FileResponse("static/index.html")

    

@app.get("/api/alerts", response_model=List[Dict[str, Any]])
async def get_alerts(
    status: Optional[str] = Query(None, description="Filtrar por estado"),
    accident_type: Optional[str] = Query(None, description="Filtrar por tipo de accidente"),
    limit: int = Query(50, description="Número máximo de alertas"),
    offset: int = Query(0, description="Offset para paginación")
):
    """Obtener lista de alertas con filtros opcionales"""
    try:
        alerts = await db.get_alerts(status=status, accident_type=accident_type, limit=limit, offset=offset)
        return alerts
    except Exception as e:
        logger.error(f"Error obteniendo alertas: {e}")
        raise HTTPException(status_code=500, detail="Error interno del servidor")

@app.get("/api/alerts/{alert_id}")
async def get_alert_details(alert_id: str):
    """Obtener detalles completos de una alerta específica"""
    try:
        alert = await db.get_alert_by_id(alert_id)
        if not alert:
            raise HTTPException(status_code=404, detail="Alerta no encontrada")
        return alert
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error obteniendo alerta {alert_id}: {e}")
        raise HTTPException(status_code=500, detail="Error interno del servidor")

@app.get("/api/statistics")
async def get_statistics():
    """Obtener estadísticas del sistema para el dashboard"""
    try:
        stats = await db.get_statistics()
        return stats
    except Exception as e:
        logger.error(f"Error obteniendo estadísticas: {e}")
        raise HTTPException(status_code=500, detail="Error interno del servidor")

@app.put("/api/alerts/{alert_id}/status")
async def update_alert_status_endpoint(alert_id: str, request: dict):
    """Actualizar el estado de una alerta (para paramédicos/aseguradoras)"""
    try:
        new_status = request.get("status")
        if not new_status:
            raise HTTPException(status_code=400, detail="Status requerido")
            
        valid_statuses = ["PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED", "FAILED"]
        if new_status.upper() not in valid_statuses:
            raise HTTPException(status_code=400, detail="Estado inválido")
        
        updated_alert = await db.update_alert_status(alert_id, new_status.upper())
        if not updated_alert:
            raise HTTPException(status_code=404, detail="Alerta no encontrada")
        
        # Notificar cambio de estado via WebSocket
        await websocket_manager.notify_alert_status_change(alert_id, "manual", new_status.lower(), None)
        
        return {"message": "Estado actualizado exitosamente", "alert_id": alert_id, "new_status": new_status}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error actualizando estado de alerta {alert_id}: {e}")
        raise HTTPException(status_code=500, detail="Error interno del servidor")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)