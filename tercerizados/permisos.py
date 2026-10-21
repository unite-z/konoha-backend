from rest_framework.permissions import BasePermission

JEFE = 'Jefe de sección'
INGENIERO = 'Ingeniero de campo'
PRACTICANTE = 'Practicante autorizado'
ROLES = (JEFE, INGENIERO, PRACTICANTE)


class PermisoModulo(BasePermission):
    message = 'Tu usuario no tiene permisos para esta operación'

    def has_permission(self, request, view):
        if not request.user or not request.user.is_authenticated or not request.user.is_active:
            return False
        roles = (JEFE,) if request.method not in ('GET', 'HEAD', 'OPTIONS') and getattr(view, 'solo_jefe', False) else ROLES
        return request.user.groups.filter(name__in=roles).exists()
