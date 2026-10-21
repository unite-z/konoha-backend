"""
Permisos por rol del MOD-06.

Fuente: matriz de permisos por rol (Historias de Usuario v3, sección 2.1) y catálogo de roles
(Modelo de BD v1, tabla 3).

| Función                                         | Jefe de sección | Ingeniero de campo | Practicante | Capataz | Operario |
|-------------------------------------------------|-----------------|--------------------|-------------|---------|----------|
| Panel de indicadores y reportes (RF-REP-002/004/006) | Consulta    | Consulta (ACC-S3)  | Consulta (ACC-S3) | —  | —        |
| Solicitar análisis de IA (RF-IA-001)            | Sí              | Propone            | Propone     | —       | —        |
| Validar o rechazar datos propuestos por IA      | Valida          | —                  | —           | —       | —        |

Mientras el MOD-01 no esté integrado (autenticación vacía: el usuario es anónimo), `PermisoPorRol` permite el
acceso y deja un aviso en el log una sola vez por proceso.
"""

from __future__ import annotations

import logging
import threading
from typing import Any, Iterable

from rest_framework.permissions import BasePermission

logger = logging.getLogger(__name__)

# --- Roles (Modelo de BD v1, tabla 3; nombres exactos) ----------------------

ROL_JEFE_SECCION = "Jefe de sección"
ROL_INGENIERO_CAMPO = "Ingeniero de campo"
ROL_PRACTICANTE = "Practicante"
ROL_CAPATAZ = "Capataz"
ROL_OPERARIO = "Operario"

ROLES_CONSULTA_REPORTES: frozenset[str] = frozenset({ROL_JEFE_SECCION, ROL_INGENIERO_CAMPO, ROL_PRACTICANTE})
ROLES_SOLICITAR_IA: frozenset[str] = frozenset({ROL_JEFE_SECCION, ROL_INGENIERO_CAMPO, ROL_PRACTICANTE})
ROLES_VALIDAR_IA: frozenset[str] = frozenset({ROL_JEFE_SECCION})

MENSAJE_ROL_SIN_ACCESO = "Tu rol no tiene acceso a esta función"
MENSAJE_SIN_AUTENTICACION = (
    "Permisos de MOD-06 sin aplicar: falta la autenticación del MOD-01 (RF-T-001, RF-T-004)"
)

_aviso_emitido = False
_candado_aviso = threading.Lock()


def _esta_autenticado(user: Any) -> bool:
    return bool(user is not None and getattr(user, "is_authenticated", False))


def rol_de_usuario(user: Any) -> str | None:
    """
    Nombre del rol del usuario, o `None` si es anónimo o no tiene rol.

    TODO: ajustar al modelo de usuario del MOD-01 (Usuario.rol_id → Rol.nombre).
    Hoy se asume un objeto con `rol_id` y se resuelve el nombre en la entidad Rol de la fuente de datos.
    """
    if not _esta_autenticado(user):
        return None
    rol_id = getattr(user, "rol_id", None)
    if rol_id is None:
        return None
    from .fuentes_datos import obtener_fuente_datos  # import diferido: evita ciclos al cargar la app

    rol = obtener_fuente_datos().obtener("Rol", rol_id)
    return rol.get("nombre") if rol else None


def id_de_usuario(user: Any) -> int | None:
    """
    Id del usuario autenticado, o `None` si es anónimo.

    TODO: ajustar al modelo de usuario del MOD-01 (Usuario.id).
    """
    if not _esta_autenticado(user):
        return None
    return getattr(user, "id", None)


def _avisar_sin_autenticacion() -> None:
    global _aviso_emitido
    with _candado_aviso:
        if not _aviso_emitido:
            logger.warning(MENSAJE_SIN_AUTENTICACION)
            _aviso_emitido = True


class PermisoPorRol(BasePermission):
    """
    Permiso DRF configurado con un conjunto de roles: `PermisoPorRol(ROLES_CONSULTA_REPORTES)`.

    - Usuario anónimo (MOD-01 aún no integrado): permite y avisa una vez por proceso.
      TODO: cuando el MOD-01 publique la autenticación, rechazar a los usuarios anónimos.
    - Usuario autenticado cuyo rol no está en el conjunto: 403 «Tu rol no tiene acceso a esta función».
    """

    message = MENSAJE_ROL_SIN_ACCESO

    def __init__(self, roles: Iterable[str]) -> None:
        self.roles = frozenset(roles)

    def has_permission(self, request: Any, view: Any) -> bool:
        user = getattr(request, "user", None)
        if not _esta_autenticado(user):
            _avisar_sin_autenticacion()
            return True
        return rol_de_usuario(user) in self.roles
