#!/usr/bin/env python3
"""Importación idempotente del catastro XLSX a MOD-02. Por defecto solo valida."""

import argparse
import os
import re
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET
from collections import Counter
from decimal import Decimal, InvalidOperation
from pathlib import Path

NS = {
    "m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
}


def texto_celda(celda, cadenas):
    tipo = celda.attrib.get("t")
    if tipo == "inlineStr":
        return "".join(n.text or "" for n in celda.findall(".//m:t", NS)).strip()
    valor = celda.find("m:v", NS)
    if valor is None or valor.text is None:
        return ""
    if tipo == "s":
        return cadenas[int(valor.text)].strip()
    return valor.text.strip()


def leer_libro(ruta):
    with zipfile.ZipFile(ruta) as libro:
        raiz = ET.fromstring(libro.read("xl/workbook.xml"))
        relaciones = ET.fromstring(libro.read("xl/_rels/workbook.xml.rels"))
        destino_por_id = {r.attrib["Id"]: r.attrib["Target"] for r in relaciones}
        cadenas = []
        if "xl/sharedStrings.xml" in libro.namelist():
            shared = ET.fromstring(libro.read("xl/sharedStrings.xml"))
            cadenas = [
                "".join(t.text or "" for t in si.findall(".//m:t", NS))
                for si in shared.findall("m:si", NS)
            ]

        hoja = next(
            (
                s
                for s in raiz.findall("m:sheets/m:sheet", NS)
                if s.attrib.get("name", "").strip().casefold() == "catastro"
            ),
            None,
        )
        if hoja is None:
            raise ValueError("No se encontró la hoja 'catastro'.")
        rid = hoja.attrib[f"{{{NS['r']}}}id"]
        destino = destino_por_id[rid].lstrip("/")
        ruta_hoja = destino if destino.startswith("xl/") else f"xl/{destino}"
        datos = ET.fromstring(libro.read(ruta_hoja))
        filas = datos.findall("m:sheetData/m:row", NS)
        if len(filas) < 2:
            raise ValueError("La hoja 'catastro' no tiene encabezados ni registros.")

        def por_columna(fila):
            salida = {}
            for celda in fila.findall("m:c", NS):
                col = re.sub(r"\d+$", "", celda.attrib["r"])
                salida[col] = texto_celda(celda, cadenas)
            return salida

        encabezados = {k: v.strip().casefold() for k, v in por_columna(filas[1]).items()}
        requeridos = {
            "referencia": "c",
            "latitud": "d",
            "longitud": "e",
            "nombre común": "f",
            "nombre científico": "g",
            "tipo de vegetación (formas biológicas)": "h",
            "cantidad": "i",
            "código": "k",
            "ubicación": "b",
        }
        por_nombre = {v: k for k, v in encabezados.items()}
        faltan = set(requeridos) - set(por_nombre)
        if faltan:
            raise ValueError("Faltan columnas: " + ", ".join(sorted(faltan)))

        resultado = []
        for fila in filas[2:]:
            celdas = por_columna(fila)
            if not any(celdas.values()):
                continue
            valores = {nombre: celdas.get(por_nombre[nombre], "").strip() for nombre in requeridos}
            if not valores["referencia"]:
                raise ValueError(f"La fila {fila.attrib.get('r')} no tiene Referencia.")
            if valores["código"].casefold() in {"null", "none", "n/a", "#n/a"}:
                valores["código"] = ""
            elif re.fullmatch(r"\d+\.0+", valores["código"]):
                valores["código"] = valores["código"].split(".", 1)[0]
            if not valores["cantidad"]:
                valores["cantidad"] = Decimal(1)
            for campo in ("latitud", "longitud", "cantidad"):
                if not valores[campo] and campo in {"latitud", "longitud"}:
                    valores[campo] = None
                    continue
                try:
                    numero = Decimal(valores[campo])
                except (InvalidOperation, ValueError):
                    raise ValueError(f"Valor inválido en {campo}, fila {fila.attrib.get('r')}.")
                if not numero.is_finite():
                    raise ValueError(f"Valor no finito en {campo}, fila {fila.attrib.get('r')}.")
                valores[campo] = numero
            if valores["latitud"] is not None and (
                valores["latitud"] < -90 or valores["latitud"] > 90
            ):
                raise ValueError(f"Latitud fuera de rango, fila {fila.attrib.get('r')}.")
            if valores["longitud"] is not None and (
                valores["longitud"] < -180 or valores["longitud"] > 180
            ):
                raise ValueError(f"Longitud fuera de rango, fila {fila.attrib.get('r')}.")
            if valores["cantidad"] < 1 or valores["cantidad"] != valores["cantidad"].to_integral_value():
                raise ValueError(f"Cantidad debe ser entero positivo, fila {fila.attrib.get('r')}.")
            if not valores["nombre común"] and not valores["nombre científico"]:
                valores["especie"] = None
            else:
                valores["especie"] = True
            resultado.append(valores)
        return resultado


def literal(valor):
    if valor is None or valor == "":
        return "NULL"
    if isinstance(valor, Decimal):
        return format(valor, "f")
    return "'" + str(valor).replace("'", "''") + "'"


def generar_sql(filas):
    lineas = ["BEGIN;"]
    for fila in filas:
        comun = literal(fila["nombre común"])
        cientifico = literal(fila["nombre científico"])
        ref = literal(fila["referencia"])
        codigo_catastro = literal(fila["código"])
        tipo = literal(fila["tipo de vegetación (formas biológicas)"])
        ubicacion = literal(fila["ubicación"])
        lat = literal(fila["latitud"])
        lon = literal(fila["longitud"])
        cantidad = literal(fila["cantidad"])
        codigo_interno = literal("EJ-CAT-" + fila["referencia"])
        if fila.get("especie") and fila["nombre científico"]:
            lineas.append(
                "INSERT INTO mod02_especie_vegetal (nombre_comun, nombre_cientifico, forma_biologica) "
                f"VALUES ({comun}, {cientifico}, {tipo}) "
                "ON CONFLICT (lower(nombre_cientifico)) "
                "WHERE nombre_cientifico IS NOT NULL AND length(trim(nombre_cientifico)) > 0 "
                "DO UPDATE SET forma_biologica = EXCLUDED.forma_biologica;"
            )
            condicion_especie = f"lower(s.nombre_cientifico) = lower({cientifico})"
        elif fila.get("especie"):
            condicion_especie = f"lower(s.nombre_comun) = lower({comun}) AND s.nombre_cientifico IS NULL"
            lineas.append(
                "INSERT INTO mod02_especie_vegetal (nombre_comun, forma_biologica) "
                f"SELECT {comun}, {tipo} WHERE NOT EXISTS (SELECT 1 FROM mod02_especie_vegetal "
                f"WHERE lower(nombre_comun) = lower({comun}) AND nombre_cientifico IS NULL);"
            )
        else:
            condicion_especie = "false"
        lineas.append(
            "INSERT INTO mod02_ejemplar (codigo, referencia_catastro, codigo_catastro, especie_id, "
            "tipo_vegetacion, ubicacion, latitud, longitud, cantidad) "
            f"SELECT {codigo_interno}, {ref}, {codigo_catastro}, "
            f"(SELECT s.id FROM mod02_especie_vegetal s WHERE {condicion_especie} ORDER BY s.id LIMIT 1), "
            f"{tipo}, {ubicacion}, {lat}, {lon}, {cantidad} FROM (VALUES (1)) AS origen(unidad) "
            "ON CONFLICT (referencia_catastro) WHERE referencia_catastro IS NOT NULL "
            "DO UPDATE SET codigo_catastro = EXCLUDED.codigo_catastro, especie_id = EXCLUDED.especie_id, "
            "tipo_vegetacion = EXCLUDED.tipo_vegetacion, ubicacion = EXCLUDED.ubicacion, "
            "latitud = EXCLUDED.latitud, longitud = EXCLUDED.longitud, cantidad = EXCLUDED.cantidad;"
        )
    lineas.append("COMMIT;")
    return "\n".join(lineas) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archivo", type=Path, help="Ruta al archivo catastro campus.xlsx")
    parser.add_argument("--aplicar", action="store_true", help="Ejecuta la importación en PostgreSQL")
    args = parser.parse_args()
    filas = leer_libro(args.archivo)
    referencias = [f["referencia"].casefold() for f in filas]
    duplicadas = [ref for ref, n in Counter(referencias).items() if n > 1]
    codigos = [f["código"].casefold() for f in filas if f["código"]]
    codigos_duplicados = [codigo for codigo, n in Counter(codigos).items() if n > 1]
    if duplicadas or codigos_duplicados:
        raise ValueError(
            f"Identificadores duplicados: referencias={duplicadas[:5]}, códigos={codigos_duplicados[:5]}"
        )
    print(f"Validación correcta: {len(filas)} filas, {len(set(codigos))} códigos opcionales únicos.")
    if not args.aplicar:
        print("Modo de validación: no se escribió en la base de datos. Usa --aplicar para importar.")
        return

    jdbc_url = os.environ.get("MOD05_DB_URL", "jdbc:postgresql://localhost:5432/areas_verdes_mod05")
    match = re.fullmatch(r"jdbc:postgresql://([^:/?#]+)(?::(\d+))?/([^?]+)(?:\?.*)?", jdbc_url)
    if not match:
        raise ValueError("MOD05_DB_URL debe ser jdbc:postgresql://host:puerto/base.")
    host, port, database = match.groups()
    comando = ["psql", "-v", "ON_ERROR_STOP=1", "-h", host, "-d", database]
    if port:
        comando.extend(["-p", port])
    usuario = os.environ.get("MOD05_DB_USER")
    if usuario:
        comando.extend(["-U", usuario])
    entorno = os.environ.copy()
    if "MOD05_DB_PASSWORD" in entorno:
        entorno["PGPASSWORD"] = entorno["MOD05_DB_PASSWORD"]
    subprocess.run(comando, input=generar_sql(filas), text=True, check=True, env=entorno)
    print(f"Importación completada: {len(filas)} registros procesados.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)
