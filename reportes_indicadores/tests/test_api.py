"""
Pruebas de la capa `api/` para los reportes (RF-REP-002, RF-REP-004 y RF-REP-006): validación del periodo,
permisos, estado vacío, solo lectura y esquema OpenAPI.
"""

import pytest

from reportes_indicadores import permisos

RUTAS_REPORTES = [
    "/api/v1/reportes/cumplimiento-corte/",
    "/api/v1/reportes/distribucion-personal/",
    "/api/v1/reportes/atenciones-espacio-sector/",
]
PERIODO = {"desde": "2026-01-01", "hasta": "2026-10-20"}


@pytest.fixture
def datos_minimos() -> dict:
    return {
        "TipoServicio": [{"id": 1, "nombre": "Corte de césped", "ejecutor": "Tercerizado"}],
        "ProgramacionServicio": [{"id": 1, "tipo_servicio_id": 1, "estacion": "Verano"}],
        "FrecuenciaServicio": [
            {"id": 1, "tipo_servicio_id": 1, "estacion": "Verano", "frecuencia_teorica_dias": 21,
             "frecuencia_practica_dias": 30, "frecuencia_maxima_dias": 45}
        ],
        "DuracionServicio": [{"id": 1, "tipo_servicio_id": 1, "duracion_maxima_dias": 3, "dias_limpieza": 1}],
        "CicloCorte": [
            {"id": 1, "programacion_id": 1, "ruta_id": None, "proveedor_id": None, "estado": "Cerrado",
             "fecha_inicio_real": "2026-01-12", "fecha_fin_corte": "2026-01-14",
             "fecha_inicio_limpieza": "2026-01-15", "fecha_fin_limpieza": "2026-01-15"}
        ],
        "Cuadrilla": [{"id": 1, "nombre": "Cuadrilla 1", "capataz_id": 2, "activa": True}],
        "Personal": [
            {"id": 2, "nombres": "Andrés", "apellidos": "(ejemplo)", "documento_identidad": "12345678", "telefono": "987654321"},
            {"id": 5, "nombres": "Operario 01", "apellidos": "(ejemplo)", "documento_identidad": "87654321", "telefono": "912345678"},
        ],
        "AsignacionCuadrilla": [{"id": 1, "operario_id": 5, "cuadrilla_id": 1, "fecha_inicio": "2026-01-01", "fecha_fin": None}],
        "ClaseActividad": [{"id": 3, "nombre": "Mantenimiento de jardines"}],
        "TipoActividad": [{"id": 14, "clase_id": 3, "nombre": "Canteo"}],
        "SectorMantenimiento": [{"id": 1, "nombre": "Sector 1", "atencion_a_demanda": False}],
        "AreaVerde": [{"id": 1, "nombre": "Tontódromo", "sector_id": 1, "cuartel_id": None}],
        "ParteDiario": [{"id": 1, "fecha": "2026-10-13", "cuadrilla_id": 1, "capataz_id": 2, "estado": "Cerrado"}],
        "Actividad": [
            {"id": 1, "parte_id": 1, "tipo_actividad_id": 14, "area_verde_id": 1,
             "fecha_hora_actividad": "2026-10-13T08:00:00-05:00", "superficie_m2": 100.0, "tiempo_minutos": 60}
        ],
        "ParticipacionActividad": [{"actividad_id": 1, "operario_id": 5}],
    }


@pytest.mark.parametrize("ruta", RUTAS_REPORTES)
def test_reportes_responden_200_y_no_escriben(api_client, usar_fuente, datos_minimos, ruta):
    fuente = usar_fuente(datos_minimos)
    respuesta = api_client.get(ruta, PERIODO)
    assert respuesta.status_code == 200, respuesta.content
    cuerpo = respuesta.json()
    assert cuerpo["periodo"] == PERIODO
    assert cuerpo["sin_datos"] is False and cuerpo["mensaje_sin_datos"] is None
    assert cuerpo["generado_en"].endswith("-05:00")
    assert fuente.escrituras == []


@pytest.mark.parametrize("ruta", RUTAS_REPORTES)
def test_reportes_solo_admiten_get(api_client, usar_fuente, datos_minimos, ruta):
    usar_fuente(datos_minimos)
    assert api_client.post(ruta, PERIODO).status_code == 405


@pytest.mark.parametrize(
    "ruta,mensaje",
    [
        ("/api/v1/reportes/cumplimiento-corte/", "No hay ciclos de corte con fecha de inicio en el periodo elegido"),
        ("/api/v1/reportes/distribucion-personal/", "No hay partes diarios registrados en el periodo elegido"),
        ("/api/v1/reportes/atenciones-espacio-sector/", "No hay atenciones realizadas en el periodo elegido"),
    ],
)
def test_estado_vacio(api_client, usar_fuente, ruta, mensaje):
    usar_fuente({})
    cuerpo = api_client.get(ruta, PERIODO).json()
    assert cuerpo["sin_datos"] is True
    assert cuerpo["mensaje_sin_datos"] == mensaje
    assert cuerpo.get("resumen") is None


@pytest.mark.parametrize("ruta", RUTAS_REPORTES)
@pytest.mark.parametrize(
    "parametros,campo,mensaje",
    [
        ({"hasta": "2026-10-20"}, "desde", "Indica la fecha de inicio del periodo (desde)"),
        ({"desde": "2026-01-01"}, "hasta", "Indica la fecha de fin del periodo (hasta)"),
        ({"desde": "", "hasta": "2026-10-20"}, "desde", "Indica la fecha de inicio del periodo (desde)"),
        ({"desde": "20/10/2026", "hasta": "2026-10-20"}, "desde", "Usa el formato AAAA-MM-DD"),
        ({"desde": "2026-01-01", "hasta": "2026-13-01"}, "hasta", "Usa el formato AAAA-MM-DD"),
        ({"desde": "2026-10-20", "hasta": "2026-01-01"}, "hasta", "La fecha de fin debe ser igual o posterior a la fecha de inicio"),
    ],
)
def test_periodo_invalido_400(api_client, usar_fuente, ruta, parametros, campo, mensaje):
    fuente = usar_fuente({})
    respuesta = api_client.get(ruta, parametros)
    assert respuesta.status_code == 400
    assert respuesta.json()[campo] == [mensaje]
    assert fuente.escrituras == []


def test_filtros_invalidos_400(api_client, usar_fuente):
    usar_fuente({})
    base = "/api/v1/reportes/"
    assert api_client.get(f"{base}distribucion-personal/", dict(PERIODO, cuadrilla_id="abc")).status_code == 400
    assert api_client.get(f"{base}atenciones-espacio-sector/", dict(PERIODO, modalidad="Otra")).status_code == 400
    assert api_client.get(f"{base}atenciones-espacio-sector/", dict(PERIODO, orden="mayor")).status_code == 400
    assert api_client.get(f"{base}atenciones-espacio-sector/", dict(PERIODO, sector_id="x")).status_code == 400


def test_filtros_aplicados_se_devuelven(api_client, usar_fuente, datos_minimos):
    usar_fuente(datos_minimos)
    cuerpo = api_client.get(
        "/api/v1/reportes/atenciones-espacio-sector/", dict(PERIODO, sector_id="sin_sector", orden="asc")
    ).json()
    assert cuerpo["filtros_aplicados"] == {"sector_id": "sin_sector", "orden": "asc"}
    cuerpo = api_client.get("/api/v1/reportes/distribucion-personal/", dict(PERIODO, cuadrilla_id=1)).json()
    assert cuerpo["filtros_aplicados"] == {"cuadrilla_id": 1}


def test_distribucion_sin_datos_personales(api_client, usar_fuente, datos_minimos):
    usar_fuente(datos_minimos)
    texto = api_client.get("/api/v1/reportes/distribucion-personal/", PERIODO).content.decode("utf-8")
    for prohibido in ("documento_identidad", "telefono", "12345678", "987654321", "87654321", "912345678"):
        assert prohibido not in texto


@pytest.mark.parametrize("rol,esperado", [
    ("Jefe de sección", 200),
    ("Ingeniero de campo", 200),
    ("Practicante", 200),
    ("Capataz", 403),
    ("Operario", 403),
])
def test_permisos_de_consulta(api_client, usar_fuente, datos_minimos, usuario_con_rol, rol, esperado):
    usar_fuente(datos_minimos)
    api_client.force_authenticate(user=usuario_con_rol(rol))
    for ruta in RUTAS_REPORTES:
        respuesta = api_client.get(ruta, PERIODO)
        assert respuesta.status_code == esperado
        if esperado == 403:
            assert respuesta.json()["detail"] == "Tu rol no tiene acceso a esta función"


def test_anonimo_permitido_con_aviso_unico(api_client, usar_fuente, datos_minimos, caplog, monkeypatch):
    monkeypatch.setattr(permisos, "_aviso_emitido", False)
    usar_fuente(datos_minimos)
    with caplog.at_level("WARNING", logger="reportes_indicadores.permisos"):
        for ruta in RUTAS_REPORTES:
            assert api_client.get(ruta, PERIODO).status_code == 200
    avisos = [r for r in caplog.records if "falta la autenticación del MOD-01" in r.getMessage()]
    assert len(avisos) == 1


def test_rol_de_usuario_resuelve_el_nombre_del_rol(usar_fuente):
    from types import SimpleNamespace

    usar_fuente({"Rol": [{"id": 1, "nombre": "Jefe de sección", "descripcion": None, "activo": True}]})
    assert permisos.rol_de_usuario(SimpleNamespace(is_authenticated=True, rol_id=1)) == "Jefe de sección"
    assert permisos.rol_de_usuario(SimpleNamespace(is_authenticated=False, rol_id=1)) is None
    assert permisos.id_de_usuario(SimpleNamespace(is_authenticated=False, id=3)) is None
    assert permisos.id_de_usuario(SimpleNamespace(is_authenticated=True, id=3)) == 3


def test_esquema_openapi_incluye_las_cuatro_etiquetas(api_client):
    respuesta = api_client.get("/api/schema/", HTTP_ACCEPT="application/vnd.oai.openapi+json")
    assert respuesta.status_code == 200
    texto = respuesta.content.decode("utf-8")
    for etiqueta in (
        "RF-REP-002 Cumplimiento del corte",
        "RF-REP-004 Distribución del personal",
        "RF-REP-006 Atenciones por espacio y sector",
        "RF-IA-001 Extracción de informes con IA",
    ):
        assert etiqueta in texto
