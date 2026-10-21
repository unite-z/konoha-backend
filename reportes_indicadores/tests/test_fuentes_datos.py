"""
Pruebas de la capa `fuentes_datos/` (patrón Estrategia, sección 5 del superprompt).

Usan un directorio temporal (`tmp_path`) como `MOCK_DATA_DIR`: no leen `mock-data/`.
"""

import json
import threading

import pytest

from reportes_indicadores.comun.errores import FuenteDatosNoDisponible, RecursoNoEncontrado
from reportes_indicadores.fuentes_datos import FuenteDatosBD, FuenteDatosMock, obtener_fuente_datos
from reportes_indicadores.fuentes_datos.bd import MENSAJE_BD_NO_DISPONIBLE
from reportes_indicadores.fuentes_datos.entidades import CARPETA_POR_ENTIDAD, ENTIDADES_ESCRIBIBLES


@pytest.fixture
def directorio_mock(tmp_path, settings):
    settings.MOCK_DATA_DIR = tmp_path
    for entidad, carpeta in CARPETA_POR_ENTIDAD.items():
        (tmp_path / carpeta).mkdir(exist_ok=True)
        (tmp_path / carpeta / f"{entidad}.json").write_text("[]", encoding="utf-8")
    (tmp_path / "mod03" / "CicloCorte.json").write_text(
        json.dumps([{"id": 1, "estado": "En corte"}, {"id": 2, "estado": "Cerrado"}]), encoding="utf-8"
    )
    return tmp_path


def test_obtener_fuente_respeta_data_source(settings):
    settings.DATA_SOURCE = "mock"
    assert isinstance(obtener_fuente_datos(), FuenteDatosMock)
    settings.DATA_SOURCE = "db"
    assert isinstance(obtener_fuente_datos(), FuenteDatosBD)


def test_fuente_bd_no_disponible():
    fuente = FuenteDatosBD()
    for llamada in (
        lambda: fuente.listar("CicloCorte"),
        lambda: fuente.obtener("CicloCorte", 1),
        lambda: fuente.crear("SugerenciaIA", {}),
        lambda: fuente.actualizar("SugerenciaIA", 1, {}),
        lambda: fuente.leer_contenido_archivo({}),
    ):
        with pytest.raises(FuenteDatosNoDisponible) as info:
            llamada()
        assert info.value.mensaje == MENSAJE_BD_NO_DISPONIBLE


def test_api_con_bd_responde_503(api_client, settings):
    settings.DATA_SOURCE = "db"
    respuesta = api_client.get("/api/v1/reportes/cumplimiento-corte/", {"desde": "2026-01-01", "hasta": "2026-10-20"})
    assert respuesta.status_code == 503
    assert respuesta.json() == {"codigo": "FuenteDatosNoDisponible", "detalle": MENSAJE_BD_NO_DISPONIBLE}


def test_listar_y_obtener(directorio_mock):
    fuente = FuenteDatosMock()
    assert [c["id"] for c in fuente.listar("CicloCorte")] == [1, 2]
    assert fuente.listar("CicloCorte", estado="Cerrado") == [{"id": 2, "estado": "Cerrado"}]
    assert fuente.obtener("CicloCorte", 1) == {"id": 1, "estado": "En corte"}
    assert fuente.obtener("CicloCorte", 9) is None
    # Devuelve copias
    fuente.listar("CicloCorte")[0]["estado"] = "modificado"
    assert fuente.obtener("CicloCorte", 1)["estado"] == "En corte"


def test_entidad_desconocida(directorio_mock):
    with pytest.raises(ValueError, match="Inventada"):
        FuenteDatosMock().listar("Inventada")


def test_archivo_faltante_o_invalido(directorio_mock):
    (directorio_mock / "mod03" / "CicloCorte.json").unlink()
    with pytest.raises(FuenteDatosNoDisponible, match="mod03/CicloCorte.json"):
        FuenteDatosMock().listar("CicloCorte")
    (directorio_mock / "mod03" / "CicloCorte.json").write_text("{no es json", encoding="utf-8")
    with pytest.raises(FuenteDatosNoDisponible, match="CicloCorte"):
        FuenteDatosMock().listar("CicloCorte")


def test_escritura_fuera_del_mod06_se_rechaza(directorio_mock):
    fuente = FuenteDatosMock()
    with pytest.raises(PermissionError, match="El MOD-06 solo escribe en SugerenciaIA y ReporteGenerado"):
        fuente.crear("CicloCorte", {"estado": "Cerrado"})
    with pytest.raises(PermissionError):
        fuente.actualizar("ReporteProveedor", 1, {"resumen": "x"})
    assert ENTIDADES_ESCRIBIBLES == {"SugerenciaIA", "ReporteGenerado"}


def test_escritura_atomica(directorio_mock):
    fuente = FuenteDatosMock()
    creada = fuente.crear("SugerenciaIA", {"estado": "Propuesta", "contenido": {"texto": "Pendiente de revisión"}})
    assert creada["id"] == 1
    assert fuente.crear("SugerenciaIA", {"estado": "Propuesta"})["id"] == 2
    actualizada = fuente.actualizar("SugerenciaIA", 1, {"estado": "Validada", "id": 50})
    assert actualizada["id"] == 1 and actualizada["estado"] == "Validada"
    ruta = directorio_mock / "mod06" / "SugerenciaIA.json"
    texto = ruta.read_text(encoding="utf-8")
    assert "Pendiente de revisión" in texto  # ensure_ascii=False
    assert texto.startswith('[\n  {')  # indentación de 2 espacios
    assert [r["id"] for r in json.loads(texto)] == [1, 2]
    # No quedan archivos temporales
    assert sorted(p.name for p in ruta.parent.iterdir()) == sorted(
        f"{e}.json" for e, c in CARPETA_POR_ENTIDAD.items() if c == "mod06"
    )
    with pytest.raises(RecursoNoEncontrado):
        fuente.actualizar("SugerenciaIA", 99, {"estado": "Validada"})


def test_escritura_concurrente_sin_perdidas(directorio_mock):
    fuente = FuenteDatosMock()
    hilos = [threading.Thread(target=lambda: fuente.crear("SugerenciaIA", {"estado": "Propuesta"})) for _ in range(10)]
    for hilo in hilos:
        hilo.start()
    for hilo in hilos:
        hilo.join()
    assert sorted(r["id"] for r in fuente.listar("SugerenciaIA")) == list(range(1, 11))


def test_leer_contenido_archivo(directorio_mock):
    (directorio_mock / "archivos").mkdir()
    (directorio_mock / "archivos" / "informe.pdf").write_bytes(b"%PDF")
    fuente = FuenteDatosMock()
    assert fuente.leer_contenido_archivo({"ruta_almacenamiento": "archivos/informe.pdf"}) == b"%PDF"
    with pytest.raises(RecursoNoEncontrado, match="súbelo de nuevo"):
        fuente.leer_contenido_archivo({"ruta_almacenamiento": "archivos/no-existe.pdf"})


@pytest.mark.parametrize(
    "ruta",
    ["../secreto.txt", "archivos/../../secreto.txt", "/etc/passwd", "C:/Windows/win.ini", "C:\\Windows\\win.ini", "\\\\servidor\\x.pdf", ""],
)
def test_no_se_puede_salir_de_mock_data_dir(directorio_mock, ruta):
    (directorio_mock.parent / "secreto.txt").write_text("secreto", encoding="utf-8")
    with pytest.raises(RecursoNoEncontrado):
        FuenteDatosMock().leer_contenido_archivo({"ruta_almacenamiento": ruta})
