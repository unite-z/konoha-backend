"""
Servicio de RF-REP-002: cumplimiento del corte de césped.

Trazabilidad: RF-REP-002 · HU-MOD06-009 · CU-MOD06-009 · CP-TR-MOD06-009 · RN-001, RN-002, RI-24 ·
CU-MOD03-001, 002, 010 (1a), 012 y 014.

Texto del requisito (Catálogo v2): «El sistema deberá mostrar el cumplimiento del corte de césped: días
transcurridos entre ciclos respecto a la frecuencia y días de ejecución respecto a la duración máxima».

Reglas de cálculo [Definición acordada 20/10]:
1. Ciclos evaluables: los que tienen `fecha_inicio_real`. Un ciclo «Programado» sin fecha real no se evalúa
   (CU-MOD03-012 2a).
2. Ciclos del periodo: evaluables con `fecha_inicio_real` dentro del periodo, por fecha ascendente.
3. Ciclo anterior: el evaluable inmediatamente anterior en **todo el historial** (aunque quede fuera del periodo).
4. Días entre ciclos = fecha_inicio_real(n) − fecha_inicio_real(n−1), en días naturales.
5. Estación aplicable: la de la ProgramacionServicio del ciclo n; límites en FrecuenciaServicio
   (tipo_servicio_id + estacion).
6. Estado de frecuencia: «Límite no configurado» / «Dentro de la frecuencia» (≤ práctica) / «Tolerado» (≤ máxima)
   / «Fuera de frecuencia» (CU-MOD03-010 1a). Con un solo límite: dentro o fuera respecto de ese límite.
7. Días de corte = fecha_fin_corte − fecha_inicio_real + 1 (nulo si el ciclo sigue en curso).
8. Días de limpieza = fecha_fin_limpieza − fecha_inicio_limpieza + 1 (nulo si falta alguna).
9. Estado de duración frente a DuracionServicio (RN-002: 3 días de corte más 1 de limpieza).
10. RI-24: el resultado es informativo («alerta, no bloqueo»). El servicio no modifica nada.

HU-MOD06-009: si faltan fechas o límites, se informa de la falta (`faltantes`) y no se calcula el cumplimiento.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from datetime import date

from .. import fuentes_datos
from ..comun.fechas import a_fecha, ahora_iso
from ..comun.periodo import Periodo
from ..fuentes_datos.base import FuenteDatos
from . import indicador_por_codigo

CODIGO_INDICADOR = "RF-REP-002"
MENSAJE_SIN_DATOS = "No hay ciclos de corte con fecha de inicio en el periodo elegido"

# Estados de frecuencia
SIN_CICLO_ANTERIOR = "Sin ciclo anterior"
LIMITE_NO_CONFIGURADO = "Límite no configurado"
DENTRO_FRECUENCIA = "Dentro de la frecuencia"
TOLERADO = "Tolerado"
FUERA_FRECUENCIA = "Fuera de frecuencia"
ESTADOS_FRECUENCIA = (
    SIN_CICLO_ANTERIOR,
    LIMITE_NO_CONFIGURADO,
    DENTRO_FRECUENCIA,
    TOLERADO,
    FUERA_FRECUENCIA,
)

# Estados de duración
SIN_FECHA_FIN_CORTE = "Sin fecha de fin de corte"
DENTRO_MAXIMO = "Dentro del máximo"
EXCEDE_MAXIMO = "Excede el máximo"
ESTADOS_DURACION = (LIMITE_NO_CONFIGURADO, SIN_FECHA_FIN_CORTE, DENTRO_MAXIMO, EXCEDE_MAXIMO)


@dataclass(frozen=True)
class FilaCumplimientoCiclo:
    """Cumplimiento de un ciclo de corte (una fila del reporte)."""

    ciclo_id: int
    estado_ciclo: str | None
    proveedor_id: int | None
    proveedor_razon_social: str | None
    ruta_id: int | None
    ruta_nombre: str | None
    estacion: str | None
    fecha_inicio_real: date
    ciclo_anterior_id: int | None
    fecha_inicio_anterior: date | None
    dias_entre_ciclos: int | None
    frecuencia_teorica_dias: int | None
    frecuencia_practica_dias: int | None
    frecuencia_maxima_dias: int | None
    estado_frecuencia: str
    fecha_fin_corte: date | None
    dias_corte: int | None
    fecha_inicio_limpieza: date | None
    fecha_fin_limpieza: date | None
    dias_limpieza: int | None
    dias_total: int | None
    duracion_maxima_dias: int | None
    dias_limpieza_maximo: int | None
    maximo_total: int | None
    estado_duracion: str
    faltantes: tuple[str, ...]


@dataclass(frozen=True)
class ResumenCumplimientoCorte:
    """Conteos del periodo por estado."""

    total_ciclos: int
    por_estado_frecuencia: dict[str, int]
    por_estado_duracion: dict[str, int]


@dataclass(frozen=True)
class ResultadoCumplimientoCorte:
    """Resultado de RF-REP-002."""

    periodo: dict[str, date]
    filtros_aplicados: dict
    generado_en: str
    sin_datos: bool
    mensaje_sin_datos: str | None
    indicador: dict | None
    ciclos: tuple[FilaCumplimientoCiclo, ...]
    resumen: ResumenCumplimientoCorte | None
    ultimo_ciclo: FilaCumplimientoCiclo | None


def calcular(periodo: Periodo, fuente: FuenteDatos | None = None, **filtros: object) -> ResultadoCumplimientoCorte:
    """
    Calcula el cumplimiento del corte de césped en `periodo` (DefinicionIndicador.calcular, Diagrama de Clases v1).

    RF-REP-002 · HU-MOD06-009 · CP-TR-MOD06-009. Solo lectura (CU-MOD06-001).
    Este reporte no admite filtros adicionales; `filtros` existe por uniformidad con la operación `calcular`.
    """
    fuente = fuente or fuentes_datos.obtener_fuente_datos()
    indicador = indicador_por_codigo(fuente, CODIGO_INDICADOR)
    filtros_aplicados = {k: v for k, v in filtros.items() if v is not None}

    evaluables = sorted(
        (c for c in fuente.listar("CicloCorte") if a_fecha(c.get("fecha_inicio_real")) is not None),
        key=lambda c: (a_fecha(c["fecha_inicio_real"]), c["id"]),
    )
    del_periodo = [c for c in evaluables if periodo.contiene(a_fecha(c["fecha_inicio_real"]))]

    if not del_periodo:
        return ResultadoCumplimientoCorte(
            periodo=periodo.como_dict(),
            filtros_aplicados=filtros_aplicados,
            generado_en=ahora_iso(),
            sin_datos=True,
            mensaje_sin_datos=MENSAJE_SIN_DATOS,
            indicador=indicador,
            ciclos=(),
            resumen=None,
            ultimo_ciclo=None,
        )

    contexto = _Contexto(fuente)
    posicion = {c["id"]: i for i, c in enumerate(evaluables)}
    filas = tuple(
        _evaluar_ciclo(ciclo, evaluables[posicion[ciclo["id"]] - 1] if posicion[ciclo["id"]] > 0 else None, contexto)
        for ciclo in del_periodo
    )

    resumen = ResumenCumplimientoCorte(
        total_ciclos=len(filas),
        por_estado_frecuencia=_contar(filas, "estado_frecuencia", ESTADOS_FRECUENCIA),
        por_estado_duracion=_contar(filas, "estado_duracion", ESTADOS_DURACION),
    )
    return ResultadoCumplimientoCorte(
        periodo=periodo.como_dict(),
        filtros_aplicados=filtros_aplicados,
        generado_en=ahora_iso(),
        sin_datos=False,
        mensaje_sin_datos=None,
        indicador=indicador,
        ciclos=filas,
        resumen=resumen,
        ultimo_ciclo=filas[-1],
    )


class _Contexto:
    """Catálogos y registros relacionados, leídos una vez por cálculo."""

    def __init__(self, fuente: FuenteDatos) -> None:
        self.programaciones = {p["id"]: p for p in fuente.listar("ProgramacionServicio")}
        self.frecuencias = fuente.listar("FrecuenciaServicio")
        self.duraciones = fuente.listar("DuracionServicio")
        self.proveedores = {p["id"]: p for p in fuente.listar("Proveedor")}
        self.rutas = {r["id"]: r for r in fuente.listar("RutaCorte")}

    def frecuencia(self, tipo_servicio_id: int | None, estacion: str | None) -> dict | None:
        for f in self.frecuencias:
            if f.get("tipo_servicio_id") == tipo_servicio_id and f.get("estacion") == estacion:
                return f
        return None

    def duracion(self, tipo_servicio_id: int | None) -> dict | None:
        for d in self.duraciones:
            if d.get("tipo_servicio_id") == tipo_servicio_id:
                return d
        return None


def _dias_inclusivos(inicio: date | None, fin: date | None) -> int | None:
    if inicio is None or fin is None:
        return None
    return (fin - inicio).days + 1


def _estado_frecuencia(dias: int, practica: int | None, maxima: int | None) -> str:
    """Regla 6 (sin el caso de límite faltante, que se resuelve antes)."""
    if practica is not None and maxima is not None:
        if dias <= practica:
            return DENTRO_FRECUENCIA
        if dias <= maxima:
            return TOLERADO
        return FUERA_FRECUENCIA
    limite = practica if practica is not None else maxima
    return DENTRO_FRECUENCIA if dias <= limite else FUERA_FRECUENCIA


def _evaluar_ciclo(ciclo: dict, anterior: dict | None, ctx: _Contexto) -> FilaCumplimientoCiclo:
    faltantes: list[str] = []

    programacion = ctx.programaciones.get(ciclo.get("programacion_id"))
    tipo_servicio_id = programacion.get("tipo_servicio_id") if programacion else None
    estacion = programacion.get("estacion") if programacion else None
    if programacion is None:
        faltantes.append("El ciclo no tiene programación de servicio asociada")

    inicio = a_fecha(ciclo["fecha_inicio_real"])
    inicio_anterior = a_fecha(anterior["fecha_inicio_real"]) if anterior else None
    dias_entre = (inicio - inicio_anterior).days if inicio_anterior else None

    # --- Frecuencia (reglas 4 a 6) --------------------------------------
    frecuencia = ctx.frecuencia(tipo_servicio_id, estacion) if programacion else None
    teorica = frecuencia.get("frecuencia_teorica_dias") if frecuencia else None
    practica = frecuencia.get("frecuencia_practica_dias") if frecuencia else None
    maxima = frecuencia.get("frecuencia_maxima_dias") if frecuencia else None
    limite_frecuencia_falta = frecuencia is None or (practica is None and maxima is None)

    if programacion is not None and limite_frecuencia_falta:
        if estacion is None:
            faltantes.append("La programación del ciclo no indica la estación")
        elif frecuencia is None:
            faltantes.append(f"No hay frecuencia configurada para la estación {estacion}")
        else:
            faltantes.append(f"La frecuencia de la estación {estacion} no tiene límite práctico ni máximo")

    if dias_entre is None:
        estado_frecuencia = SIN_CICLO_ANTERIOR
        faltantes.append("No hay un ciclo anterior con fecha de inicio registrada")
    elif limite_frecuencia_falta:
        estado_frecuencia = LIMITE_NO_CONFIGURADO
    else:
        estado_frecuencia = _estado_frecuencia(dias_entre, practica, maxima)

    # --- Duración (reglas 7 a 9) ----------------------------------------
    fin_corte = a_fecha(ciclo.get("fecha_fin_corte"))
    inicio_limpieza = a_fecha(ciclo.get("fecha_inicio_limpieza"))
    fin_limpieza = a_fecha(ciclo.get("fecha_fin_limpieza"))
    dias_corte = _dias_inclusivos(inicio, fin_corte)
    dias_limpieza = _dias_inclusivos(inicio_limpieza, fin_limpieza)

    if fin_corte is None:
        faltantes.append("Falta la fecha de fin de corte")
    if inicio_limpieza is None:
        faltantes.append("Falta la fecha de inicio de limpieza")
    if fin_limpieza is None:
        faltantes.append("Falta la fecha de fin de limpieza")

    duracion = ctx.duracion(tipo_servicio_id) if programacion else None
    duracion_maxima = duracion.get("duracion_maxima_dias") if duracion else None
    limpieza_maxima = duracion.get("dias_limpieza") if duracion else None

    if duracion is None or duracion_maxima is None:
        estado_duracion = LIMITE_NO_CONFIGURADO
        if programacion is not None:
            faltantes.append("No hay duración máxima configurada para el servicio")
    elif dias_corte is None:
        estado_duracion = SIN_FECHA_FIN_CORTE
    else:
        corte_ok = dias_corte <= duracion_maxima
        limpieza_ok = dias_limpieza is None or limpieza_maxima is None or dias_limpieza <= limpieza_maxima
        estado_duracion = DENTRO_MAXIMO if corte_ok and limpieza_ok else EXCEDE_MAXIMO

    dias_total = dias_corte + (dias_limpieza or 0) if dias_corte is not None else None
    maximo_total = duracion_maxima + (limpieza_maxima or 0) if duracion_maxima is not None else None

    proveedor = ctx.proveedores.get(ciclo.get("proveedor_id"))
    ruta = ctx.rutas.get(ciclo.get("ruta_id"))

    return FilaCumplimientoCiclo(
        ciclo_id=ciclo["id"],
        estado_ciclo=ciclo.get("estado"),
        proveedor_id=ciclo.get("proveedor_id"),
        proveedor_razon_social=proveedor.get("razon_social") if proveedor else None,
        ruta_id=ciclo.get("ruta_id"),
        ruta_nombre=ruta.get("nombre") if ruta else None,
        estacion=estacion,
        fecha_inicio_real=inicio,
        ciclo_anterior_id=anterior["id"] if anterior else None,
        fecha_inicio_anterior=inicio_anterior,
        dias_entre_ciclos=dias_entre,
        frecuencia_teorica_dias=teorica,
        frecuencia_practica_dias=practica,
        frecuencia_maxima_dias=maxima,
        estado_frecuencia=estado_frecuencia,
        fecha_fin_corte=fin_corte,
        dias_corte=dias_corte,
        fecha_inicio_limpieza=inicio_limpieza,
        fecha_fin_limpieza=fin_limpieza,
        dias_limpieza=dias_limpieza,
        dias_total=dias_total,
        duracion_maxima_dias=duracion_maxima,
        dias_limpieza_maximo=limpieza_maxima,
        maximo_total=maximo_total,
        estado_duracion=estado_duracion,
        faltantes=tuple(faltantes),
    )


def _contar(filas: tuple[FilaCumplimientoCiclo, ...], atributo: str, valores: tuple[str, ...]) -> dict[str, int]:
    conteo = Counter(getattr(f, atributo) for f in filas)
    return {valor: conteo.get(valor, 0) for valor in valores}
