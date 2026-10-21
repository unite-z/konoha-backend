"""
Conversión de fechas del JSON y fecha actual en la zona horaria del proyecto (`settings.TIME_ZONE`, America/Lima).

Formatos (Modelo de BD v1, tipos lógicos):
- Fecha: "AAAA-MM-DD".
- Fecha y hora: ISO 8601 con zona, por ejemplo "2026-10-20T08:30:00-05:00".
- Hora: "HH:MM".

Para saber si un instante pertenece a un periodo se usa su fecha **local** (`fecha_local`).
Las diferencias de días son días naturales [Definición acordada 20/10].
"""

from __future__ import annotations

from datetime import date, datetime
from zoneinfo import ZoneInfo

from django.conf import settings
from django.utils import timezone


def zona_local() -> ZoneInfo:
    """Zona horaria del proyecto (`settings.TIME_ZONE`)."""
    return ZoneInfo(settings.TIME_ZONE)


def a_fecha(valor: str | date | None) -> date | None:
    """Convierte "AAAA-MM-DD" (o un `date`) a `date`. Devuelve `None` si el valor es nulo o vacío."""
    if valor is None or valor == "":
        return None
    if isinstance(valor, datetime):
        return fecha_local(valor)
    if isinstance(valor, date):
        return valor
    return date.fromisoformat(str(valor))


def a_fecha_hora(valor: str | datetime | None) -> datetime | None:
    """
    Convierte un texto ISO 8601 (o un `datetime`) a `datetime` **siempre con zona horaria**.

    Si el valor no trae zona, se interpreta en la zona local del proyecto.
    """
    if valor is None or valor == "":
        return None
    dt = valor if isinstance(valor, datetime) else datetime.fromisoformat(str(valor))
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=zona_local())
    return dt


def fecha_local(dt: datetime) -> date:
    """Fecha del instante `dt` en America/Lima (`settings.TIME_ZONE`)."""
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=zona_local())
    return dt.astimezone(zona_local()).date()


def hoy() -> date:
    """Fecha actual en la zona local del proyecto."""
    return timezone.localdate()


def ahora() -> datetime:
    """Instante actual con zona, expresado en la zona local del proyecto."""
    return timezone.localtime()


def ahora_iso() -> str:
    """Instante actual en ISO 8601 con zona (America/Lima), con precisión de segundos."""
    return ahora().replace(microsecond=0).isoformat()
