"""
Pruebas de RF-IA-001 (extracción de informes con IA). Caso de prueba: CP-TR-MOD06-010, con las variantes
obligatorias de IA (CP-TR-MOD06-005 a 008): datos insuficientes, fallo de IA, acceso sin permiso y validación.

Ninguna prueba llama a Gemini: se usa `ClienteIAFalso` o un doble del SDK.
"""

import base64
import json
import logging
from types import SimpleNamespace

import pytest

from reportes_indicadores.comun.errores import (
    ConfiguracionFaltante,
    ParametroInvalido,
    RecursoNoEncontrado,
    ServicioIANoDisponible,
    TipoArchivoNoAdmitido,
    TransicionNoPermitida,
)
from reportes_indicadores.ia import extraccion_reportes as ext
from reportes_indicadores.ia.cliente_gemini import ClienteGemini

from .conftest import ClienteIAFalso, FuenteDatosMemoria

URL = "/api/v1/ia/extracciones/"
PDF = b"%PDF-1.4 informe de prueba"
DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

PROPUESTA_COMPLETA = {
    "campo_1": 1250.5,
    "campo_2": [{"producto": "Producto de ejemplo", "dosis": "1 L/ha"}],
    "campo_3": [{"plaga": "Plaga de ejemplo"}],
    "campo_4": [{"espacio": "Biblioteca Central", "evidencia": "foto-1"}],
    "campo_5": "Recorrido por los jardines del sector 2",
}


@pytest.fixture
def datos_ia() -> dict:
    campos = [
        ("Metrado", "Decimal"),
        ("Productos aplicados", "JSON"),
        ("Plagas atendidas", "JSON"),
        ("Evidencias por espacio", "JSON"),
        ("Ruta recorrida", "Texto largo"),
    ]
    return {
        "TipoServicio": [
            {"id": 1, "nombre": "Corte de césped", "ejecutor": "Tercerizado"},
            {"id": 2, "nombre": "Control fitosanitario", "ejecutor": "Tercerizado"},
        ],
        "AtencionTercerizada": [
            {"id": 4, "tipo_servicio_id": 2, "estado": "Reporte adjunto"},
            {"id": 5, "tipo_servicio_id": 1, "estado": "Reporte adjunto"},
            {"id": 6, "tipo_servicio_id": 2, "estado": "Observado"},
        ],
        "ReporteProveedor": [
            {"id": 1, "atencion_id": 4, "revision": 1},
            {"id": 2, "atencion_id": 5, "revision": 1},
            {"id": 3, "atencion_id": 6, "revision": 1},
            {"id": 4, "atencion_id": 4, "revision": 2},
        ],
        "FormatoReporteServicio": [
            {"id": 1, "tipo_servicio_id": 2, "version": 1, "vigente": True},
            {"id": 2, "tipo_servicio_id": 2, "version": 0, "vigente": False},
        ],
        "CampoFormatoReporte": [
            {"id": i, "formato_id": 1, "nombre_campo": n, "tipo_dato": t, "obligatorio": True}
            for i, (n, t) in enumerate(campos, start=1)
        ],
        "Archivo": [
            {"id": 1, "nombre_original": "informe.pdf", "tipo_mime": "application/pdf", "ruta_almacenamiento": "archivos/informe.pdf"},
            {"id": 2, "nombre_original": "corte.pdf", "tipo_mime": "application/pdf", "ruta_almacenamiento": "archivos/corte.pdf"},
            {"id": 3, "nombre_original": "informe.docx", "tipo_mime": DOCX, "ruta_almacenamiento": "archivos/informe.docx"},
        ],
        "ArchivoAsociado": [
            {"id": 1, "archivo_id": 1, "entidad": "ReporteProveedor", "registro_id": 1, "categoria": "ReporteProveedor"},
            {"id": 2, "archivo_id": 2, "entidad": "ReporteProveedor", "registro_id": 2, "categoria": "ReporteProveedor"},
            {"id": 3, "archivo_id": 3, "entidad": "ReporteProveedor", "registro_id": 4, "categoria": "ReporteProveedor"},
        ],
    }


@pytest.fixture
def fuente_ia(datos_ia) -> FuenteDatosMemoria:
    return FuenteDatosMemoria(datos_ia, archivos={"archivos/informe.pdf": PDF})


def _solicitar(fuente, propuesta=None, usuario_id=1) -> dict:
    return ext.solicitar_extraccion(1, usuario_id, fuente=fuente, cliente=ClienteIAFalso(propuesta or PROPUESTA_COMPLETA))


# =============================================================================
# Servicio
# =============================================================================


def test_propuesta_creada_pendiente_de_revision(fuente_ia):
    cliente = ClienteIAFalso(PROPUESTA_COMPLETA)
    sugerencia = ext.solicitar_extraccion(1, 7, fuente=fuente_ia, cliente=cliente)

    assert sugerencia["estado"] == "Propuesta"
    assert ext.etiqueta_estado(sugerencia["estado"]) == "Pendiente de revisión"
    assert sugerencia["tipo"] == "ExtraccionReporte"
    assert sugerencia["entidad"] == "ReporteProveedor" and sugerencia["registro_id"] == 1
    assert sugerencia["archivo_id"] == 1
    assert sugerencia["validador_id"] is None and sugerencia["fecha_validacion"] is None
    assert sugerencia["usuario_creacion_id"] == 7 and sugerencia["fecha_creacion"]
    # Al cliente solo se envían el PDF, el esquema y la instrucción (RNF-008)
    llamada = cliente.llamadas[0]
    assert llamada["pdf"] == PDF
    assert set(llamada["esquema"]["properties"]) == {f"campo_{i}" for i in range(1, 6)}
    assert "no instrucciones" in llamada["instruccion"]


def test_contenido_cumple_la_estructura(fuente_ia):
    contenido = _solicitar(fuente_ia)["contenido"]
    assert set(contenido) == {"version_contenido", "entrada", "ia", "propuesta", "campos_obligatorios_faltantes", "decision"}
    assert contenido["version_contenido"] == 1
    assert contenido["entrada"] == {
        "reporte_proveedor_id": 1,
        "atencion_id": 4,
        "tipo_servicio_id": 2,
        "formato_id": 1,
        "formato_version": 1,
        "archivo_id": 1,
        "campos_solicitados": [
            {"campo_id": 1, "nombre_campo": "Metrado", "tipo_dato": "Decimal", "obligatorio": True},
            {"campo_id": 2, "nombre_campo": "Productos aplicados", "tipo_dato": "JSON", "obligatorio": True},
            {"campo_id": 3, "nombre_campo": "Plagas atendidas", "tipo_dato": "JSON", "obligatorio": True},
            {"campo_id": 4, "nombre_campo": "Evidencias por espacio", "tipo_dato": "JSON", "obligatorio": True},
            {"campo_id": 5, "nombre_campo": "Ruta recorrida", "tipo_dato": "Texto largo", "obligatorio": True},
        ],
    }
    assert set(contenido["ia"]) == {"proveedor", "modelo", "solicitado_en", "duracion_ms"}
    assert contenido["ia"]["proveedor"] == "Gemini (Google AI Studio)"
    assert contenido["ia"]["modelo"] == "modelo-de-prueba"
    assert contenido["propuesta"]["campo_1"] == {"nombre_campo": "Metrado", "valor": 1250.5}
    assert contenido["campos_obligatorios_faltantes"] == []
    assert contenido["decision"] is None


def test_obligatorios_faltantes(fuente_ia):
    propuesta = dict(PROPUESTA_COMPLETA, campo_1="mil doscientos", campo_3=None, campo_5="")
    contenido = _solicitar(fuente_ia, propuesta)["contenido"]
    # campo_1 tenía un tipo no válido: se deja sin valor
    assert contenido["propuesta"]["campo_1"]["valor"] is None
    assert contenido["campos_obligatorios_faltantes"] == ["campo_1", "campo_3", "campo_5"]


def test_datos_insuficientes_todo_nulo(fuente_ia):
    vacia = {f"campo_{i}": None for i in range(1, 6)}
    sugerencia = _solicitar(fuente_ia, vacia)
    assert sugerencia["estado"] == "Propuesta"
    assert sugerencia["contenido"]["campos_obligatorios_faltantes"] == [f"campo_{i}" for i in range(1, 6)]


def test_fallo_de_ia_no_crea_sugerencia(fuente_ia):
    cliente = ClienteIAFalso(error=ServicioIANoDisponible("No se pudo analizar el informe con IA"))
    with pytest.raises(ServicioIANoDisponible):
        ext.solicitar_extraccion(1, 1, fuente=fuente_ia, cliente=cliente)
    assert fuente_ia.datos["SugerenciaIA"] == []
    assert fuente_ia.escrituras == []


def test_errores_de_configuracion_y_archivo(fuente_ia):
    cliente = ClienteIAFalso(PROPUESTA_COMPLETA)
    with pytest.raises(RecursoNoEncontrado):
        ext.solicitar_extraccion(999, 1, fuente=fuente_ia, cliente=cliente)
    with pytest.raises(ConfiguracionFaltante, match="formato estándar vigente"):
        ext.solicitar_extraccion(2, 1, fuente=fuente_ia, cliente=cliente)
    with pytest.raises(RecursoNoEncontrado, match="no tiene informe adjunto"):
        ext.solicitar_extraccion(3, 1, fuente=fuente_ia, cliente=cliente)
    with pytest.raises(TipoArchivoNoAdmitido, match="Solo se admite el informe en PDF"):
        ext.solicitar_extraccion(4, 1, fuente=fuente_ia, cliente=cliente)
    assert cliente.llamadas == []


def test_esquema_desde_los_campos_del_formato():
    campos = [
        {"id": 1, "nombre_campo": "A", "tipo_dato": "Texto"},
        {"id": 2, "nombre_campo": "B", "tipo_dato": "Entero"},
        {"id": 3, "nombre_campo": "C", "tipo_dato": "Decimal"},
        {"id": 4, "nombre_campo": "D", "tipo_dato": "Booleano"},
        {"id": 5, "nombre_campo": "E", "tipo_dato": "Fecha"},
        {"id": 6, "nombre_campo": "F", "tipo_dato": "JSON"},
        {"id": 7, "nombre_campo": "G", "tipo_dato": "Texto largo"},
    ]
    esquema = ext.construir_esquema(campos)
    props = esquema["properties"]
    assert esquema["type"] == "object" and esquema["required"] == [f"campo_{i}" for i in range(1, 8)]
    assert props["campo_1"]["type"] == ["string", "null"]
    assert props["campo_2"]["type"] == ["integer", "null"]
    assert props["campo_3"]["type"] == ["number", "null"]
    assert props["campo_4"]["type"] == ["boolean", "null"]
    assert props["campo_5"] == {"type": ["string", "null"], "format": "date", "description": "E"}
    assert props["campo_6"]["type"] == ["array", "null"] and props["campo_6"]["items"] == {"type": "object"}
    with pytest.raises(ConfiguracionFaltante, match="tipo de dato no admitido"):
        ext.construir_esquema([{"id": 9, "nombre_campo": "Foto", "tipo_dato": "Imagen"}])


def test_validar_aceptar_corregir_descartar(fuente_ia):
    sugerencia = _solicitar(fuente_ia)
    decisiones = {
        "campo_1": {"accion": "corregir", "valor_final": 1300},
        "campo_2": {"accion": "aceptar"},
        "campo_3": {"accion": "descartar", "valor_final": "se ignora"},
        "campo_4": {"accion": "aceptar"},
        "campo_5": {"accion": "corregir", "valor_final": "Ruta corregida"},
    }
    validada = ext.validar(sugerencia["id"], 1, decisiones, fuente=fuente_ia)
    assert validada["estado"] == "Validada"
    assert validada["validador_id"] == 1 and validada["fecha_validacion"]
    assert validada["usuario_modificacion_id"] == 1
    decision = validada["contenido"]["decision"]
    assert decision["por_campo"]["campo_1"] == {"accion": "corregir", "valor_final": 1300}
    assert decision["por_campo"]["campo_2"]["valor_final"] == PROPUESTA_COMPLETA["campo_2"]
    assert decision["por_campo"]["campo_3"] == {"accion": "descartar", "valor_final": None}
    assert decision["resumen"] == {"aceptados": 2, "corregidos": 2, "descartados": 1}
    assert decision["motivo_rechazo"] is None


def test_todo_descartado_queda_rechazada(fuente_ia):
    sugerencia = _solicitar(fuente_ia)
    decisiones = {f"campo_{i}": {"accion": "descartar"} for i in range(1, 6)}
    assert ext.validar(sugerencia["id"], 1, decisiones, fuente=fuente_ia)["estado"] == "Rechazada"


def test_rechazar(fuente_ia):
    sugerencia = _solicitar(fuente_ia)
    rechazada = ext.rechazar(sugerencia["id"], 1, "El informe no corresponde", fuente=fuente_ia)
    assert rechazada["estado"] == "Rechazada"
    decision = rechazada["contenido"]["decision"]
    assert decision["motivo_rechazo"] == "El informe no corresponde"
    assert all(d == {"accion": "descartar", "valor_final": None} for d in decision["por_campo"].values())
    assert decision["resumen"] == {"aceptados": 0, "corregidos": 0, "descartados": 5}


def test_validar_dos_veces(fuente_ia):
    sugerencia = _solicitar(fuente_ia)
    decisiones = {f"campo_{i}": {"accion": "aceptar"} for i in range(1, 6)}
    ext.validar(sugerencia["id"], 1, decisiones, fuente=fuente_ia)
    with pytest.raises(TransicionNoPermitida, match=r"estado actual: Validada"):
        ext.validar(sugerencia["id"], 1, decisiones, fuente=fuente_ia)
    with pytest.raises(TransicionNoPermitida):
        ext.rechazar(sugerencia["id"], 1, None, fuente=fuente_ia)


def test_decisiones_incompletas_o_sobrantes(fuente_ia):
    sugerencia = _solicitar(fuente_ia)
    incompletas = {f"campo_{i}": {"accion": "aceptar"} for i in range(1, 5)}
    with pytest.raises(ParametroInvalido, match="faltan: campo_5"):
        ext.validar(sugerencia["id"], 1, incompletas, fuente=fuente_ia)
    sobrantes = {f"campo_{i}": {"accion": "aceptar"} for i in range(1, 7)}
    with pytest.raises(ParametroInvalido, match="sobran: campo_6"):
        ext.validar(sugerencia["id"], 1, sobrantes, fuente=fuente_ia)


def test_corregir_sin_valor_o_con_tipo_erroneo(fuente_ia):
    sugerencia = _solicitar(fuente_ia)
    base = {f"campo_{i}": {"accion": "aceptar"} for i in range(1, 6)}
    with pytest.raises(ParametroInvalido, match="Indica el valor final del campo 'Metrado'"):
        ext.validar(sugerencia["id"], 1, dict(base, campo_1={"accion": "corregir"}), fuente=fuente_ia)
    with pytest.raises(ParametroInvalido, match="debe ser de tipo Decimal"):
        ext.validar(sugerencia["id"], 1, dict(base, campo_1={"accion": "corregir", "valor_final": "x"}), fuente=fuente_ia)
    with pytest.raises(ParametroInvalido, match="debe ser de tipo JSON"):
        ext.validar(sugerencia["id"], 1, dict(base, campo_2={"accion": "corregir", "valor_final": {"a": 1}}), fuente=fuente_ia)
    # La sugerencia sigue en Propuesta
    assert ext.obtener(sugerencia["id"], fuente=fuente_ia)["estado"] == "Propuesta"


def test_listar_y_obtener(fuente_ia):
    primera = _solicitar(fuente_ia)
    segunda = _solicitar(fuente_ia)
    ext.rechazar(primera["id"], 1, None, fuente=fuente_ia)
    assert [s["id"] for s in ext.listar(fuente=fuente_ia)] == [segunda["id"], primera["id"]]
    assert [s["id"] for s in ext.listar(estado="Rechazada", fuente=fuente_ia)] == [primera["id"]]
    assert len(ext.listar(reporte_proveedor_id=1, fuente=fuente_ia)) == 2
    assert ext.listar(reporte_proveedor_id=2, fuente=fuente_ia) == []
    with pytest.raises(RecursoNoEncontrado):
        ext.obtener(999, fuente=fuente_ia)


def test_metricas(fuente_ia):
    vacia = ext.metricas(fuente=fuente_ia)
    assert vacia["total_sugerencias"] == 0
    assert vacia["tasa_aceptacion_sin_cambios"] is None and vacia["duracion_media_ms"] is None

    s1, s2, _s3 = _solicitar(fuente_ia), _solicitar(fuente_ia), _solicitar(fuente_ia)
    ext.validar(
        s1["id"],
        1,
        {
            "campo_1": {"accion": "aceptar"},
            "campo_2": {"accion": "aceptar"},
            "campo_3": {"accion": "aceptar"},
            "campo_4": {"accion": "corregir", "valor_final": [{"espacio": "OCAI"}]},
            "campo_5": {"accion": "descartar"},
        },
        fuente=fuente_ia,
    )
    ext.rechazar(s2["id"], 1, None, fuente=fuente_ia)
    m = ext.metricas(fuente=fuente_ia)
    assert m["total_sugerencias"] == 3
    assert m["por_estado"] == {"Propuesta": 1, "Validada": 1, "Rechazada": 1}
    assert (m["campos_aceptados"], m["campos_corregidos"], m["campos_descartados"]) == (3, 1, 6)
    assert m["tasa_aceptacion_sin_cambios"] == 0.3
    assert isinstance(m["duracion_media_ms"], float)


def test_solo_escribe_en_sugerencia_ia(fuente_ia):
    sugerencia = _solicitar(fuente_ia)
    ext.validar(sugerencia["id"], 1, {f"campo_{i}": {"accion": "aceptar"} for i in range(1, 6)}, fuente=fuente_ia)
    assert fuente_ia.escrituras
    assert {entidad for _, entidad, _ in fuente_ia.escrituras} == {"SugerenciaIA"}


# =============================================================================
# Adaptador de Gemini (con un doble del SDK: sin llamadas reales)
# =============================================================================


class _SDKFalso:
    """Imita `genai.Client`: `client.interactions.create(**kwargs)`."""

    def __init__(self, texto=None, error=None):
        self.kwargs = None
        self._texto = texto
        self._error = error
        self.interactions = SimpleNamespace(create=self._create)

    def _create(self, **kwargs):
        self.kwargs = kwargs
        if self._error:
            raise self._error
        return SimpleNamespace(output_text=self._texto)


def test_cliente_gemini_envia_pdf_y_esquema():
    sdk = _SDKFalso(texto=json.dumps({"campo_1": 10}))
    cliente = ClienteGemini(api_key="clave-falsa", modelo="modelo-x", cliente_sdk=sdk)
    esquema = {"type": "object", "properties": {"campo_1": {"type": ["number", "null"]}}}
    assert cliente.extraer_json(PDF, esquema, "instrucción") == {"campo_1": 10}
    assert sdk.kwargs["model"] == "modelo-x"
    assert sdk.kwargs["system_instruction"] == "instrucción"
    documento = sdk.kwargs["input"][0]
    assert documento == {"type": "document", "data": base64.b64encode(PDF).decode("utf-8"), "mime_type": "application/pdf"}
    assert sdk.kwargs["response_format"] == {"type": "text", "mime_type": "application/json", "schema": esquema}
    assert "timeout" not in sdk.kwargs


@pytest.mark.parametrize(
    "sdk",
    [
        _SDKFalso(error=RuntimeError("fallo de red")),
        _SDKFalso(texto=None),
        _SDKFalso(texto="   "),
        _SDKFalso(texto="no es json"),
        _SDKFalso(texto="[1, 2]"),
    ],
)
def test_cliente_gemini_errores(sdk):
    cliente = ClienteGemini(api_key="clave-falsa", modelo="modelo-x", cliente_sdk=sdk)
    with pytest.raises(ServicioIANoDisponible, match="registra los datos manualmente"):
        cliente.extraer_json(PDF, {}, "instrucción")


def test_cliente_gemini_sin_configuracion_no_muestra_la_clave(caplog):
    with caplog.at_level(logging.ERROR):
        with pytest.raises(ServicioIANoDisponible, match="no está configurado"):
            ClienteGemini(api_key="", modelo="modelo-x")
        with pytest.raises(ServicioIANoDisponible, match="no está configurado"):
            ClienteGemini(api_key="clave-secreta-123", modelo="")
    assert "clave-secreta-123" not in caplog.text


def test_cliente_gemini_error_no_expone_la_clave(caplog):
    sdk = _SDKFalso(error=RuntimeError("detalle con clave-secreta-123"))
    cliente = ClienteGemini(api_key="clave-secreta-123", modelo="modelo-x", cliente_sdk=sdk)
    with caplog.at_level(logging.ERROR):
        with pytest.raises(ServicioIANoDisponible) as info:
            cliente.extraer_json(PDF, {}, "instrucción")
    assert "clave-secreta-123" not in caplog.text
    assert "clave-secreta-123" not in str(info.value)
    assert info.value.__cause__ is None


# =============================================================================
# Endpoints
# =============================================================================


@pytest.fixture
def api_ia(usar_fuente, datos_ia):
    return usar_fuente(datos_ia, archivos={"archivos/informe.pdf": PDF})


def _crear_por_api(api_client) -> dict:
    respuesta = api_client.post(URL, {"reporte_proveedor_id": 1}, format="json")
    assert respuesta.status_code == 201, respuesta.content
    return respuesta.json()


def test_api_solicitar_crea_propuesta(api_client, api_ia, usar_cliente_ia):
    usar_cliente_ia(PROPUESTA_COMPLETA)
    datos = _crear_por_api(api_client)
    assert datos["estado"] == "Propuesta"
    assert datos["etiqueta_estado"] == "Pendiente de revisión"
    assert datos["contenido"]["propuesta"]["campo_5"]["valor"] == PROPUESTA_COMPLETA["campo_5"]
    assert len(api_ia.datos["SugerenciaIA"]) == 1


def test_api_fallo_de_ia_503_sin_sugerencia(api_client, api_ia, usar_cliente_ia):
    usar_cliente_ia(error=ServicioIANoDisponible("No se pudo analizar el informe con IA; registra los datos manualmente"))
    respuesta = api_client.post(URL, {"reporte_proveedor_id": 1}, format="json")
    assert respuesta.status_code == 503
    assert respuesta.json()["codigo"] == "ServicioIANoDisponible"
    assert api_ia.datos["SugerenciaIA"] == []


@pytest.mark.parametrize("clave,modelo", [("", "modelo-x"), ("clave-falsa", "")])
def test_api_sin_clave_o_modelo_503(api_client, api_ia, settings, sin_ia_real, clave, modelo):
    settings.GEMINI_API_KEY = clave
    settings.GEMINI_MODEL = modelo
    respuesta = api_client.post(URL, {"reporte_proveedor_id": 1}, format="json")
    assert respuesta.status_code == 503
    assert respuesta.json() == {
        "codigo": "ServicioIANoDisponible",
        "detalle": "El análisis con IA no está configurado; registra los datos manualmente",
    }
    assert api_ia.datos["SugerenciaIA"] == []


@pytest.mark.parametrize(
    "reporte_id,estado,codigo",
    [(2, 422, "ConfiguracionFaltante"), (3, 404, "RecursoNoEncontrado"), (4, 415, "TipoArchivoNoAdmitido"), (999, 404, "RecursoNoEncontrado")],
)
def test_api_errores_de_solicitud(api_client, api_ia, usar_cliente_ia, reporte_id, estado, codigo):
    usar_cliente_ia(PROPUESTA_COMPLETA)
    respuesta = api_client.post(URL, {"reporte_proveedor_id": reporte_id}, format="json")
    assert respuesta.status_code == estado
    assert respuesta.json()["codigo"] == codigo


def test_api_solicitud_sin_reporte_400(api_client, api_ia):
    respuesta = api_client.post(URL, {}, format="json")
    assert respuesta.status_code == 400
    assert "reporte_proveedor_id" in respuesta.json()


def test_api_validar_y_volver_a_validar(api_client, api_ia, usar_cliente_ia):
    usar_cliente_ia(PROPUESTA_COMPLETA)
    sid = _crear_por_api(api_client)["id"]
    cuerpo = {"decisiones": {f"campo_{i}": {"accion": "aceptar"} for i in range(1, 6)}}
    respuesta = api_client.post(f"{URL}{sid}/validar/", cuerpo, format="json")
    assert respuesta.status_code == 200
    assert respuesta.json()["estado"] == "Validada" and respuesta.json()["etiqueta_estado"] == "Validada"
    otra = api_client.post(f"{URL}{sid}/validar/", cuerpo, format="json")
    assert otra.status_code == 409
    assert otra.json()["detalle"] == "Esta propuesta ya fue revisada (estado actual: Validada)"


def test_api_validar_decisiones_incompletas_400(api_client, api_ia, usar_cliente_ia):
    usar_cliente_ia(PROPUESTA_COMPLETA)
    sid = _crear_por_api(api_client)["id"]
    respuesta = api_client.post(f"{URL}{sid}/validar/", {"decisiones": {"campo_1": {"accion": "aceptar"}}}, format="json")
    assert respuesta.status_code == 400
    assert respuesta.json()["codigo"] == "ParametroInvalido"
    assert "campo_2" in respuesta.json()["detalle"]


def test_api_corregir_sin_valor_400(api_client, api_ia, usar_cliente_ia):
    usar_cliente_ia(PROPUESTA_COMPLETA)
    sid = _crear_por_api(api_client)["id"]
    decisiones = {f"campo_{i}": {"accion": "aceptar"} for i in range(1, 6)}
    decisiones["campo_1"] = {"accion": "corregir"}
    respuesta = api_client.post(f"{URL}{sid}/validar/", {"decisiones": decisiones}, format="json")
    assert respuesta.status_code == 400
    assert "valor final" in respuesta.json()["detalle"]


def test_api_accion_no_valida_400(api_client, api_ia, usar_cliente_ia):
    usar_cliente_ia(PROPUESTA_COMPLETA)
    sid = _crear_por_api(api_client)["id"]
    respuesta = api_client.post(f"{URL}{sid}/validar/", {"decisiones": {"campo_1": {"accion": "borrar"}}}, format="json")
    assert respuesta.status_code == 400


def test_api_rechazar(api_client, api_ia, usar_cliente_ia):
    usar_cliente_ia(PROPUESTA_COMPLETA)
    sid = _crear_por_api(api_client)["id"]
    respuesta = api_client.post(f"{URL}{sid}/rechazar/", {"motivo": "Informe ilegible"}, format="json")
    assert respuesta.status_code == 200
    assert respuesta.json()["estado"] == "Rechazada"
    assert respuesta.json()["contenido"]["decision"]["motivo_rechazo"] == "Informe ilegible"


def test_api_ingeniero_no_puede_validar_403(api_client, api_ia, usar_cliente_ia, usuario_con_rol):
    usar_cliente_ia(PROPUESTA_COMPLETA)
    api_client.force_authenticate(user=usuario_con_rol("Ingeniero de campo"))
    sid = _crear_por_api(api_client)["id"]  # el ingeniero sí puede solicitar (propone)
    cuerpo = {"decisiones": {f"campo_{i}": {"accion": "aceptar"} for i in range(1, 6)}}
    respuesta = api_client.post(f"{URL}{sid}/validar/", cuerpo, format="json")
    assert respuesta.status_code == 403
    assert respuesta.json()["detail"] == "Tu rol no tiene acceso a esta función"
    assert api_client.post(f"{URL}{sid}/rechazar/", {}, format="json").status_code == 403
    assert api_client.get(f"{URL}metricas/").status_code == 403


def test_api_capataz_no_puede_solicitar_403(api_client, api_ia, usuario_con_rol):
    api_client.force_authenticate(user=usuario_con_rol("Capataz"))
    assert api_client.post(URL, {"reporte_proveedor_id": 1}, format="json").status_code == 403
    assert api_client.get(URL).status_code == 403


def test_api_jefe_valida_y_registra_su_id(api_client, api_ia, usar_cliente_ia, usuario_con_rol):
    usar_cliente_ia(PROPUESTA_COMPLETA)
    api_client.force_authenticate(user=usuario_con_rol("Jefe de sección", id_usuario=1))
    sid = _crear_por_api(api_client)["id"]
    cuerpo = {"decisiones": {f"campo_{i}": {"accion": "aceptar"} for i in range(1, 6)}}
    datos = api_client.post(f"{URL}{sid}/validar/", cuerpo, format="json").json()
    assert datos["validador_id"] == 1 and datos["usuario_creacion_id"] == 1


def test_api_lista_detalle_y_metricas(api_client, api_ia, usar_cliente_ia):
    usar_cliente_ia(PROPUESTA_COMPLETA)
    ids = [_crear_por_api(api_client)["id"] for _ in range(27)]
    pagina_1 = api_client.get(URL).json()
    assert pagina_1["count"] == 27 and len(pagina_1["results"]) == 25
    assert api_client.get(URL, {"page": 2}).json()["results"][-1]["id"] == ids[0]
    assert api_client.get(URL, {"estado": "Validada"}).json()["count"] == 0
    assert api_client.get(URL, {"estado": "Otra"}).status_code == 400
    assert api_client.get(f"{URL}{ids[0]}/").json()["id"] == ids[0]
    assert api_client.get(f"{URL}9999/").status_code == 404
    metricas = api_client.get(f"{URL}metricas/").json()
    assert metricas["por_estado"]["Propuesta"] == 27
