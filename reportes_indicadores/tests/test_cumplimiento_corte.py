"""
Pruebas de RF-REP-002 (cumplimiento del corte de césped). Caso de prueba: CP-TR-MOD06-009.

HU-MOD06-009: se ven los días entre ciclos y de ejecución frente a los límites; si faltan fechas o límites, se
informa de la falta y no se muestra cumplimiento calculado.
"""

from datetime import date

import pytest

from reportes_indicadores.comun.periodo import Periodo
from reportes_indicadores.servicios import cumplimiento_corte as cc

from .conftest import FuenteDatosMemoria


def _programacion(pid: int, estacion: str) -> dict:
    return {"id": pid, "tipo_servicio_id": 1, "estacion": estacion}


def _ciclo(cid, estado, inicio, fin_corte=None, ini_limp=None, fin_limp=None, programacion_id=None) -> dict:
    return {
        "id": cid,
        "programacion_id": programacion_id or cid,
        "ruta_id": 1,
        "proveedor_id": 1,
        "estado": estado,
        "fecha_inicio_real": inicio,
        "fecha_fin_corte": fin_corte,
        "fecha_inicio_limpieza": ini_limp,
        "fecha_fin_limpieza": fin_limp,
    }


@pytest.fixture
def datos_corte() -> dict:
    """Escenarios de la sección 12.3 del superprompt."""
    return {
        "TipoServicio": [{"id": 1, "nombre": "Corte de césped", "ejecutor": "Tercerizado"}],
        "FrecuenciaServicio": [
            {"id": i, "tipo_servicio_id": 1, "estacion": e, "frecuencia_teorica_dias": 21,
             "frecuencia_practica_dias": 30, "frecuencia_maxima_dias": 45}
            for i, e in enumerate(("Verano", "Otoño", "Invierno"), start=1)
        ],
        "DuracionServicio": [{"id": 1, "tipo_servicio_id": 1, "duracion_maxima_dias": 3, "dias_limpieza": 1}],
        "Proveedor": [{"id": 1, "razon_social": "Terraverde Ingenieros SAC"}],
        "RutaCorte": [{"id": 1, "nombre": "Ruta de jardines 2026"}],
        "DefinicionIndicador": [
            {"id": 1, "codigo": "RF-REP-002", "nombre": "Cumplimiento del corte de césped",
             "descripcion_calculo": "…", "meta": None, "unidad": "días", "activo": True}
        ],
        "ProgramacionServicio": [
            _programacion(1, "Verano"),
            _programacion(2, "Verano"),
            _programacion(3, "Verano"),
            _programacion(4, "Otoño"),
            _programacion(5, "Otoño"),
            _programacion(6, "Primavera"),
            _programacion(7, "Primavera"),
            _programacion(8, "Primavera"),
        ],
        "CicloCorte": [
            _ciclo(1, "Cerrado", "2026-01-12", "2026-01-14", "2026-01-15", "2026-01-15"),
            _ciclo(2, "Cerrado", "2026-02-09", "2026-02-11", "2026-02-12", "2026-02-12"),
            _ciclo(3, "Cerrado", "2026-03-19", "2026-03-21", "2026-03-22", "2026-03-22"),
            _ciclo(4, "Cerrado", "2026-05-11", "2026-05-13", "2026-05-14", "2026-05-14"),
            _ciclo(5, "Cerrado", "2026-06-08", "2026-06-11", "2026-06-12", "2026-06-13"),
            _ciclo(6, "Cerrado", "2026-09-28", "2026-09-30", "2026-10-01", "2026-10-01"),
            _ciclo(7, "En corte", "2026-10-19"),
            _ciclo(8, "Programado", None),
        ],
    }


def _anio() -> Periodo:
    return Periodo(date(2026, 1, 1), date(2026, 10, 20))


def _por_id(resultado) -> dict:
    return {fila.ciclo_id: fila for fila in resultado.ciclos}


def test_estados_de_frecuencia_y_duracion(datos_corte):
    resultado = cc.calcular(_anio(), fuente=FuenteDatosMemoria(datos_corte))
    filas = _por_id(resultado)

    assert filas[1].estado_frecuencia == "Sin ciclo anterior" and filas[1].dias_entre_ciclos is None
    assert (filas[2].dias_entre_ciclos, filas[2].estado_frecuencia) == (28, "Dentro de la frecuencia")
    assert (filas[3].dias_entre_ciclos, filas[3].estado_frecuencia) == (38, "Tolerado")
    assert (filas[4].dias_entre_ciclos, filas[4].estado_frecuencia) == (53, "Fuera de frecuencia")
    assert filas[6].estado_frecuencia == "Límite no configurado"
    assert "No hay frecuencia configurada para la estación Primavera" in filas[6].faltantes

    # Duración: 3 + 1 días dentro del máximo
    assert (filas[2].dias_corte, filas[2].dias_limpieza, filas[2].dias_total) == (3, 1, 4)
    assert filas[2].maximo_total == 4 and filas[2].estado_duracion == "Dentro del máximo"
    # 4 días de corte + 2 de limpieza
    assert (filas[5].dias_corte, filas[5].dias_limpieza, filas[5].dias_total) == (4, 2, 6)
    assert filas[5].estado_duracion == "Excede el máximo"
    # Ciclo en curso
    assert filas[7].dias_corte is None and filas[7].dias_total is None
    assert filas[7].estado_duracion == "Sin fecha de fin de corte"
    assert "Falta la fecha de fin de corte" in filas[7].faltantes


def test_ciclo_programado_sin_fecha_se_excluye(datos_corte):
    resultado = cc.calcular(_anio(), fuente=FuenteDatosMemoria(datos_corte))
    assert 8 not in _por_id(resultado)
    assert resultado.resumen.total_ciclos == 7


def test_ciclo_anterior_fuera_del_periodo_si_se_usa(datos_corte):
    periodo = Periodo(date(2026, 2, 1), date(2026, 2, 28))
    resultado = cc.calcular(periodo, fuente=FuenteDatosMemoria(datos_corte))
    assert [f.ciclo_id for f in resultado.ciclos] == [2]
    fila = resultado.ciclos[0]
    assert fila.ciclo_anterior_id == 1
    assert fila.fecha_inicio_anterior == date(2026, 1, 12)
    assert fila.dias_entre_ciclos == 28


def test_sin_limites_no_calcula_cumplimiento(datos_corte):
    datos_corte["DuracionServicio"] = []
    datos_corte["FrecuenciaServicio"] = []
    resultado = cc.calcular(_anio(), fuente=FuenteDatosMemoria(datos_corte))
    for fila in resultado.ciclos:
        assert fila.estado_frecuencia in ("Límite no configurado", "Sin ciclo anterior")
        assert fila.estado_duracion == "Límite no configurado"
        assert fila.maximo_total is None
        assert "No hay duración máxima configurada para el servicio" in fila.faltantes


def test_solo_un_limite_de_frecuencia(datos_corte):
    for f in datos_corte["FrecuenciaServicio"]:
        f["frecuencia_practica_dias"] = None  # solo máxima (45)
    filas = _por_id(cc.calcular(_anio(), fuente=FuenteDatosMemoria(datos_corte)))
    assert filas[3].estado_frecuencia == "Dentro de la frecuencia"  # 38 ≤ 45
    assert filas[4].estado_frecuencia == "Fuera de frecuencia"  # 53 > 45


def test_periodo_sin_ciclos_devuelve_sin_datos(datos_corte):
    resultado = cc.calcular(Periodo(date(2026, 7, 1), date(2026, 8, 31)), fuente=FuenteDatosMemoria(datos_corte))
    assert resultado.sin_datos is True
    assert resultado.mensaje_sin_datos == "No hay ciclos de corte con fecha de inicio en el periodo elegido"
    assert resultado.ciclos == () and resultado.resumen is None and resultado.ultimo_ciclo is None


def test_ultimo_ciclo_y_resumen(datos_corte):
    resultado = cc.calcular(_anio(), fuente=FuenteDatosMemoria(datos_corte))
    assert resultado.ultimo_ciclo.ciclo_id == 7
    assert resultado.resumen.por_estado_frecuencia["Tolerado"] == 1
    assert resultado.resumen.por_estado_duracion["Excede el máximo"] == 1
    assert resultado.indicador["codigo"] == "RF-REP-002"
    assert filas_contexto_ok(resultado)


def filas_contexto_ok(resultado) -> bool:
    fila = resultado.ciclos[0]
    return fila.proveedor_razon_social == "Terraverde Ingenieros SAC" and fila.ruta_nombre == "Ruta de jardines 2026"


def test_indicador_ausente_no_es_error(datos_corte):
    datos_corte["DefinicionIndicador"] = []
    assert cc.calcular(_anio(), fuente=FuenteDatosMemoria(datos_corte)).indicador is None


def test_no_escribe(datos_corte):
    fuente = FuenteDatosMemoria(datos_corte)
    cc.calcular(_anio(), fuente=fuente)
    assert fuente.escrituras == []
