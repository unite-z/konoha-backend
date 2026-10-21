from rest_framework.permissions import BasePermission

JEFE = 'Jefe de sección'
INGENIERO = 'Ingeniero de campo'
PRACTICANTE = 'Practicante autorizado'
ROLES = (JEFE, INGENIERO, PRACTICANTE)


def rol_de(usuario):
    """Rol de mayor jerarquía del usuario en MOD-05, o `None` si no pertenece a ninguno."""
    asignados = set(usuario.groups.filter(name__in=ROLES).values_list('name', flat=True))
    return next((rol for rol in ROLES if rol in asignados), None)


class EstaAutenticado(BasePermission):
    """Cualquier usuario autenticado y activo, tenga o no un rol del módulo."""

    def has_permission(self, request, view):
        return bool(request.user and request.user.is_authenticated and request.user.is_active)


class PermisoModulo(BasePermission):
    message = 'Tu usuario no tiene permisos para esta operación'

    def has_permission(self, request, view):
        if not request.user or not request.user.is_authenticated or not request.user.is_active:
            return False
        roles = (JEFE,) if request.method not in ('GET', 'HEAD', 'OPTIONS') and getattr(view, 'solo_jefe', False) else ROLES
        return request.user.groups.filter(name__in=roles).exists()
