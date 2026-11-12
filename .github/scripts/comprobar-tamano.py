#!/usr/bin/env python3
"""Avisa en el PR si cambia más de LIMITE líneas. Solo comenta; nunca bloquea.

Cuenta las líneas añadidas y borradas sin pruebas, lockfile, mock-data ni documentación.
Si el PR vuelve a estar dentro del límite, borra el aviso.

Variables: GH_TOKEN, REPO, PR, BASE y LIMITE (400).
Con SECO=1 imprime el aviso en vez de publicarlo; CABEZA cambia el HEAD comparado.
"""
import json
import os
import subprocess
import sys
import urllib.request

MARCA = "<!-- tamano-pr -->"
EXCLUIDOS = [
    ":(exclude,glob)**/*.test.ts",
    ":(exclude,glob)**/*.test.tsx",
    ":(exclude,glob)src/test/**",
    ":(exclude,glob)**/package-lock.json",
    ":(exclude,glob)mock-data/**",
    ":(exclude,glob)docs/**",
    ":(exclude,glob)**/*.md",
]

entorno = os.environ
seco = entorno.get("SECO") == "1"
limite = int(entorno.get("LIMITE", "400"))


def tamano():
    salida = subprocess.run(
        ["git", "diff", "--numstat", f"{entorno['BASE']}...{entorno.get('CABEZA', 'HEAD')}", "--", ".", *EXCLUIDOS],
        check=True, capture_output=True, text=True,
    ).stdout
    ficheros = []
    for linea in salida.splitlines():
        mas, menos, ruta = linea.split("\t", 2)
        if mas != "-":  # los binarios no cuentan
            ficheros.append((ruta, int(mas) + int(menos)))
    return sum(n for _, n in ficheros), sorted(ficheros, key=lambda f: -f[1])


def aviso(total, ficheros):
    filas = "\n".join(f"| `{ruta}` | {n} |" for ruta, n in ficheros[:10])
    return (
        f"{MARCA}\n### 📏 PR demasiado grande para revisarlo\n\n"
        f"Cambia **{total} líneas** sin contar pruebas, lockfile, mock-data ni documentación. "
        f"El máximo recomendado es **{limite}**: un PR así no se revisa bien, ni por una persona ni por la IA.\n\n"
        f"Pártelo en varios tickets AV, cada uno con su rama y su PR. El cambio se concentra en:\n\n"
        f"| Fichero | Líneas |\n|---|---|\n{filas}\n\n"
        f"_Aviso automático: no bloquea la fusión. Desaparece cuando el PR baja de {limite} líneas._"
    )


def pedir(url, cuerpo=None, metodo=None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(url, data=datos, method=metodo)
    peticion.add_header("Authorization", f"Bearer {entorno['GH_TOKEN']}")
    peticion.add_header("Accept", "application/vnd.github+json")
    with urllib.request.urlopen(peticion, timeout=30) as respuesta:
        return json.loads(respuesta.read() or "null")


def main():
    total, ficheros = tamano()
    print(f"{total} líneas contadas (límite {limite}).")
    if seco:
        print(aviso(total, ficheros) if total > limite else "Dentro del límite: sin aviso.")
        return
    api = f"https://api.github.com/repos/{entorno['REPO']}/issues"
    previo = next(
        (c for c in pedir(f"{api}/{entorno['PR']}/comments?per_page=100")
         if c["body"].startswith(MARCA) and c["user"]["login"] == "github-actions[bot]"),
        None,
    )
    if total > limite:
        if previo:
            pedir(f"{api}/comments/{previo['id']}", {"body": aviso(total, ficheros)}, "PATCH")
        else:
            pedir(f"{api}/{entorno['PR']}/comments", {"body": aviso(total, ficheros)}, "POST")
    elif previo:
        pedir(f"{api}/comments/{previo['id']}", metodo="DELETE")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:  # es un aviso: un fallo aquí no debe molestar al PR
        print(f"::warning::No se pudo comprobar el tamaño del PR: {error}")
    sys.exit(0)
