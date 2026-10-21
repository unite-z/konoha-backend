"""
Pruebas de RF-REP-004 (distribución del personal).

Sin caso de prueba en Casos de Prueba v2: estas pruebas sirven de propuesta para QA (HU-MOD06-002).
"""

import json
from dataclasses import asdict
from datetime import date

import pytest

from reportes_indicadores.comun.periodo import Periodo
from reportes_indicadores.servicios import distribucion_personal as dp

from .conftest import FuenteDatosMemoria

PERIODO = Periodo(date(2026, 10, 13), date(2026, 10, 14))


@pytest.fixture
def datos_personal() -> dict:
    personal = [
        {"id": 2, "nombres": "Andrés", "apellidos": "(ejemplo)", "documento_identidad": "00000000", "telefono": "999"},
        {"id": 3, "nombres": "Oscar", "apellidos": "(ejemplo)", "documento_identidad": "11111111", "telefono": "888"},
    ] + [
        {"id": i, "nombres": f"Operario {i:02d}", "apellidos": "(ejemplo)", "documento_identidad": "X", "telefono": "Y"}
        for i in range(5, 12)
    ]
    return {
        "Personal": personal,
        "Cuadrilla": [
            {"id": 1, "nombre": "Cuadrilla 1", "capataz_id": 2, "activa": True},
            {"id": 2, "nombre": "Cuadrilla 2", "capataz_id": 3, "activa": True},
            {"id": 3, "nombre": "Cuadrilla 3", "capataz_id": None, "activa": False},
        ],
        "AsignacionCuadrilla": [
            {"id": 1, "operario_id": 5, "cuadrilla_id": 1, "fecha_inicio": "2026-01-01", "fecha_fin": None},
            {"id": 2, "operario_id": 6, "cuadrilla_id": 1, "fecha_inicio": "2026-01-01", "fecha_fin": None},
            {"id": 3, "operario_id": 7, "cuadrilla_id": 1, "fecha_inicio": "2026-01-01", "fecha_fin": None},
            {"id": 4, "operario_id": 8, "cuadrilla_id": 2, "fecha_inicio": "2026-01-01", "fecha_fin": None},
            {"id": 5, "operario_id": 9, "cuadrilla_id": 2, "fecha_inicio": "2026-01-01", "fecha_fin": None},
            # Asignación vencida a la Cuadrilla 1: el 14/10 ya no es vigente
            {"id": 6, "operario_id": 10, "cuadrilla_id": 1, "fecha_inicio": "2026-01-01", "fecha_fin": "2026-10-13"},
        ],
        "ClaseActividad": [
            {"id": 3, "nombre": "Mantenimiento de jardines"},
            {"id": 6, "nombre": "Riego"},
        ],
        "TipoActividad": [
            {"id": 14, "clase_id": 3, "nombre": "Canteo"},
            {"id": 15, "clase_id": 3, "nombre": "Deshierbo"},
            {"id": 38, "clase_id": 6, "nombre": "Riego manual"},
        ],
        "AreaVerde": [
            {"id": 1, "nombre": "Tontódromo"},
            {"id": 2, "nombre": "Educación"},
            {"id": 4, "nombre": "Biblioteca Central"},
        ],
        "ParteDiario": [
            {"id": 1, "fecha": "2026-10-13", "cuadrilla_id": 1, "capataz_id": 2, "estado": "Cerrado"},
            {"id": 2, "fecha": "2026-10-13", "cuadrilla_id": 2, "capataz_id": 3, "estado": "Cerrado"},
            {"id": 3, "fecha": "2026-10-14", "cuadrilla_id": 1, "capataz_id": 2, "estado": "Cerrado"},
            {"id": 9, "fecha": "2026-10-20", "cuadrilla_id": 1, "capataz_id": 2, "estado": "Abierto"},
        ],
        "Actividad": [
            # 13/10, Cuadrilla 1: dos actividades, el operario 7 en ambas
            {"id": 1, "parte_id": 1, "tipo_actividad_id": 14, "area_verde_id": 1,
             "fecha_hora_actividad": "2026-10-13T08:00:00-05:00", "superficie_m2": 300.0, "tiempo_minutos": 120},
            {"id": 2, "parte_id": 1, "tipo_actividad_id": 15, "area_verde_id": 2,
             "fecha_hora_actividad": "2026-10-13T10:00:00-05:00", "superficie_m2": 100.0, "tiempo_minutos": 60},
            # misma combinación que la 1 → misma fila
            {"id": 3, "parte_id": 1, "tipo_actividad_id": 14, "area_verde_id": 1,
             "fecha_hora_actividad": "2026-10-13T14:00:00-05:00", "superficie_m2": 50.0, "tiempo_minutos": 30},
            # 13/10, Cuadrilla 2: sin tiempo
            {"id": 4, "parte_id": 2, "tipo_actividad_id": 38, "area_verde_id": 4,
             "fecha_hora_actividad": "2026-10-13T08:00:00-05:00", "superficie_m2": 200.0, "tiempo_minutos": None},
            # 14/10, Cuadrilla 1: el operario 8 (Cuadrilla 2) y el 10 (asignación vencida) apoyan
            {"id": 5, "parte_id": 3, "tipo_actividad_id": 15, "area_verde_id": 1,
             "fecha_hora_actividad": "2026-10-14T08:00:00-05:00", "superficie_m2": 120.0, "tiempo_minutos": 90},
            # fuera del periodo
            {"id": 9, "parte_id": 9, "tipo_actividad_id": 14, "area_verde_id": 1,
             "fecha_hora_actividad": "2026-10-20T08:00:00-05:00", "superficie_m2": 10.0, "tiempo_minutos": 10},
        ],
        "ParticipacionActividad": [
            {"actividad_id": 1, "operario_id": 5},
            {"actividad_id": 1, "operario_id": 6},
            {"actividad_id": 1, "operario_id": 7},
            {"actividad_id": 2, "operario_id": 7},
            {"actividad_id": 3, "operario_id": 5},
            {"actividad_id": 4, "operario_id": 8},
            {"actividad_id": 4, "operario_id": 9},
            {"actividad_id": 5, "operario_id": 5},
            {"actividad_id": 5, "operario_id": 8},
            {"actividad_id": 5, "operario_id": 10},
            {"actividad_id": 9, "operario_id": 5},
        ],
    }


def _fila(resultado, jornada, cuadrilla_id, tipo_id, area_id):
    return next(
        f
        for f in resultado.filas
        if f.jornada == jornada and f.cuadrilla_id == cuadrilla_id and f.tipo_actividad_id == tipo_id and f.area_verde_id == area_id
    )


def test_agregacion_por_jornada_cuadrilla_tipo_y_espacio(datos_personal):
    resultado = dp.calcular(PERIODO, fuente=FuenteDatosMemoria(datos_personal))
    assert len(resultado.filas) == 4
    fila = _fila(resultado, date(2026, 10, 13), 1, 14, 1)
    assert fila.numero_actividades == 2
    assert fila.tiempo_minutos == 150
    assert fila.superficie_m2 == 350.0
    assert fila.numero_operarios == 3
    assert fila.clase_actividad_nombre == "Mantenimiento de jardines"
    assert fila.capataz_nombre == "Andrés (ejemplo)"
    # Orden: jornada, cuadrilla, clase, tipo, espacio
    claves = [(f.jornada, f.cuadrilla_nombre, f.clase_actividad_nombre, f.tipo_actividad_nombre) for f in resultado.filas]
    assert claves == sorted(claves)


def test_horas_persona(datos_personal):
    resultado = dp.calcular(PERIODO, fuente=FuenteDatosMemoria(datos_personal))
    fila = _fila(resultado, date(2026, 10, 13), 1, 14, 1)
    # (120 × 3 + 30 × 1) / 60 = 6.5
    assert fila.horas_persona == 6.5
    total_13_c1 = next(t for t in resultado.totales_por_cuadrilla_jornada if t.jornada == date(2026, 10, 13) and t.cuadrilla_id == 1)
    # 6.5 + (60 × 1) / 60 = 7.5
    assert total_13_c1.horas_persona == 7.5


def test_operario_en_dos_actividades_cuenta_una_vez(datos_personal):
    resultado = dp.calcular(PERIODO, fuente=FuenteDatosMemoria(datos_personal))
    total = next(t for t in resultado.totales_por_cuadrilla_jornada if t.jornada == date(2026, 10, 13) and t.cuadrilla_id == 1)
    assert total.numero_operarios == 3  # 5, 6 y 7 (el 7 participa en dos actividades)
    assert resultado.resumen.operarios_distintos == 6  # 5, 6, 7, 8, 9, 10


def test_apoyo_eventual_se_marca(datos_personal):
    resultado = dp.calcular(PERIODO, fuente=FuenteDatosMemoria(datos_personal))
    fila = _fila(resultado, date(2026, 10, 14), 1, 15, 1)
    apoyo = {o.operario_id: o.es_apoyo for o in fila.operarios}
    assert apoyo == {5: False, 8: True, 10: True}


def test_actividad_sin_tiempo(datos_personal):
    resultado = dp.calcular(PERIODO, fuente=FuenteDatosMemoria(datos_personal))
    assert resultado.resumen.actividades_sin_tiempo == 1
    fila = _fila(resultado, date(2026, 10, 13), 2, 38, 4)
    assert fila.tiempo_minutos is None and fila.horas_persona is None


def test_cuadrillas_sin_parte(datos_personal):
    resultado = dp.calcular(PERIODO, fuente=FuenteDatosMemoria(datos_personal))
    sin_parte = [(c.jornada, c.cuadrilla_id, c.estado) for c in resultado.cuadrillas_sin_parte]
    # La Cuadrilla 3 está inactiva: no se lista.
    assert sin_parte == [(date(2026, 10, 14), 2, "Sin parte aún")]


def test_filtros(datos_personal):
    fuente = FuenteDatosMemoria(datos_personal)
    por_cuadrilla = dp.calcular(PERIODO, cuadrilla_id=2, fuente=fuente)
    assert {f.cuadrilla_id for f in por_cuadrilla.filas} == {2}
    assert por_cuadrilla.filtros_aplicados == {"cuadrilla_id": 2}
    assert [(c.jornada, c.cuadrilla_id) for c in por_cuadrilla.cuadrillas_sin_parte] == [(date(2026, 10, 14), 2)]

    por_clase = dp.calcular(PERIODO, clase_actividad_id=6, fuente=fuente)
    assert {f.tipo_actividad_id for f in por_clase.filas} == {38}

    por_tipo = dp.calcular(PERIODO, tipo_actividad_id=15, fuente=fuente)
    assert {f.tipo_actividad_id for f in por_tipo.filas} == {15}

    por_area = dp.calcular(PERIODO, area_verde_id=2, fuente=fuente)
    assert [f.area_verde_id for f in por_area.filas] == [2]

    sin_coincidencias = dp.calcular(PERIODO, area_verde_id=999, fuente=fuente)
    assert sin_coincidencias.sin_datos is True


def test_no_devuelve_datos_personales(datos_personal):
    resultado = dp.calcular(PERIODO, fuente=FuenteDatosMemoria(datos_personal))
    texto = json.dumps(asdict(resultado), default=str)
    assert "documento_identidad" not in texto and "telefono" not in texto
    assert "00000000" not in texto and "11111111" not in texto


def test_sin_datos(datos_personal):
    resultado = dp.calcular(Periodo(date(2026, 11, 1), date(2026, 11, 30)), fuente=FuenteDatosMemoria(datos_personal))
    assert resultado.sin_datos is True
    assert resultado.mensaje_sin_datos == "No hay partes diarios registrados en el periodo elegido"
    assert resultado.filas == () and resultado.resumen is None


def test_no_escribe(datos_personal):
    fuente = FuenteDatosMemoria(datos_personal)
    dp.calcular(PERIODO, fuente=fuente)
    assert fuente.escrituras == []
