"""
Periodo de consulta de los reportes del MOD-06.

CU-MOD06-001: «el actor selecciona un rango de fechas». Todos los endpoints de reporte reciben
`desde` y `hasta` (ambos obligatorios, formato AAAA-MM-DD, ambos extremos incluidos).
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from .errores import ParametroInvalido

MENSAJE_FALTA_DESDE = "Indica la fecha de inicio del periodo (desde)"
MENSAJE_FALTA_HASTA = "Indica la fecha de fin del periodo (hasta)"
MENSAJE_FORMATO_FECHA = "Usa el formato AAAA-MM-DD"
MENSAJE_ORDEN_FECHAS = "La fecha de fin debe ser igual o posterior a la fecha de inicio"


@dataclass(frozen=True)
class Periodo:
    """Rango de fechas `[desde, hasta]`, ambos extremos incluidos."""

    desde: date
    hasta: date

    def __post_init__(self) -> None:
        if self.hasta < self.desde:
            raise ParametroInvalido(MENSAJE_ORDEN_FECHAS)

    def contiene(self, fecha: date | None) -> bool:
        """`True` si `fecha` está entre `desde` y `hasta` (ambos incluidos). Una fecha nula no pertenece."""
        if fecha is None:
            return False
        return self.desde <= fecha <= self.hasta

    def como_dict(self) -> dict[str, date]:
        """Representación `{desde, hasta}` usada en las respuestas."""
        return {"desde": self.desde, "hasta": self.hasta}
