"""Genera el modelo entidad-relación de docs/base-datos/ desde las migraciones de Flyway.

Crea una base temporal, le aplica src/main/resources/db/migration/V*.sql en orden, lee el esquema
del catálogo de PostgreSQL, la borra y escribe modelo-er.drawio con una página por módulo. Si está
el CLI de draw.io, exporta además un PNG por página.

Uso (con PostGIS levantado, ver docs/estandares/pruebas.md):

    PGHOST=127.0.0.1 PGPORT=55432 PGUSER=areas_verdes PGPASSWORD=... \
        python3 docs/base-datos/generar-modelo-er.py

Una tabla nueva hay que colocarla en PAGINAS y darle categoría en CATEGORIA; si falta, el script
lo dice y no escribe nada.
"""
import json
import os
import re
import shutil
import subprocess
from datetime import datetime
from html import escape
from pathlib import Path
from zoneinfo import ZoneInfo

RAIZ = Path(__file__).resolve().parents[2]
MIGRACIONES_DIR = RAIZ / "src/main/resources/db/migration"
DESTINO = Path(__file__).resolve().parent
SALIDA = DESTINO / "modelo-er.drawio"

CONSULTA = """
select json_build_object(
 'columnas', (select json_agg(json_build_object('t',c.relname,'col',a.attname,'tipo',format_type(a.atttypid,a.atttypmod),'nulo',not a.attnotnull,'pos',a.attnum) order by c.relname,a.attnum)
   from pg_attribute a join pg_class c on c.oid=a.attrelid join pg_namespace n on n.oid=c.relnamespace
   where n.nspname='public' and c.relkind='r' and a.attnum>0 and not a.attisdropped and c.relname not in ('flyway_schema_history','spatial_ref_sys')),
 'restricciones', (select json_agg(json_build_object('t',c.relname,'tipo',k.contype,'nombre',k.conname,'cols',(select json_agg(a.attname order by x.o) from unnest(k.conkey) with ordinality x(n,o) join pg_attribute a on a.attrelid=k.conrelid and a.attnum=x.n),'ref',r.relname,'refcols',(select json_agg(a.attname order by x.o) from unnest(k.confkey) with ordinality x(n,o) join pg_attribute a on a.attrelid=k.confrelid and a.attnum=x.n),'def',pg_get_constraintdef(k.oid)))
   from pg_constraint k join pg_class c on c.oid=k.conrelid join pg_namespace n on n.oid=c.relnamespace left join pg_class r on r.oid=k.confrelid
   where n.nspname='public' and c.relname not in ('flyway_schema_history','spatial_ref_sys')),
 'indices_unicos', (select json_agg(json_build_object('t',t.relname,'nombre',i.relname,'def',pg_get_indexdef(i.oid)))
   from pg_index x join pg_class i on i.oid=x.indexrelid join pg_class t on t.oid=x.indrelid join pg_namespace n on n.oid=t.relnamespace
   where n.nspname='public' and x.indisunique and not x.indisprimary and not exists (select 1 from pg_constraint k where k.conindid=i.oid)),
 'triggers', (select json_agg(json_build_object('t',c.relname,'nombre',g.tgname,'def',pg_get_triggerdef(g.oid)))
   from pg_trigger g join pg_class c on c.oid=g.tgrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and not g.tgisinternal)
);
"""


def psql(base, *args):
    return subprocess.run(["psql", "-X", "-q", "-v", "ON_ERROR_STOP=1", "-d", base, *args],
                          check=True, capture_output=True, text=True).stdout


def version(f):
    return int(re.match(r"V(\d+)__", f.name).group(1))


def leer_esquema():
    migraciones = sorted(MIGRACIONES_DIR.glob("V*__*.sql"), key=version)
    temporal = f"modelo_er_{os.getpid()}"
    psql("postgres", "-c", f"CREATE DATABASE {temporal}")
    try:
        for m in migraciones:
            psql(temporal, "-f", str(m))
        esquema = json.loads(psql(temporal, "-At", "-c", CONSULTA))
    finally:
        psql("postgres", "-c", f"DROP DATABASE IF EXISTS {temporal}")
    return esquema, f"V{version(migraciones[0])} a V{version(migraciones[-1])}"


d, MIGRACIONES = leer_esquema()
FECHA = datetime.now(ZoneInfo("America/Lima")).strftime("%d-%m-%Y")

# ---- categorías (color de cabecera) ----
CAT = {
    "seg": ("#dae8fc", "#6c8ebf"),
    "op": ("#d5e8d4", "#82b366"),
    "cat": ("#fff2cc", "#d6b656"),
    "arch": ("#e1d5e7", "#9673a6"),
    "prov": ("#f5f5f5", "#666666"),
}
CATEGORIA = {
    # MOD-01
    "core_rol": "seg", "core_permiso": "seg", "core_rol_permiso": "seg", "core_usuario": "seg",
    "core_sesion": "seg", "core_registro_auditoria": "seg", "core_registro_acceso": "seg",
    "core_archivo": "arch", "core_adjunto": "arch",
    # MOD-03
    "mod03_frecuencia_servicio": "cat", "mod03_duracion_servicio": "cat", "mod03_tipo_restriccion": "cat",
    "mod03_restriccion_calendario": "op", "mod03_programacion_servicio": "op",
    "mod03_confirmacion_restriccion": "op",
    # MOD-04
    "mod04_personal": "op", "mod04_cuadrilla": "op", "mod04_asignacion_cuadrilla": "op",
    "mod04_tipo_actividad": "cat", "mod04_encargo": "op", "mod04_parte_diario": "op",
    "mod04_actividad": "op", "mod04_participacion_actividad": "op", "mod04_idempotencia": "seg",
    # MOD-05
    "mod05_contrato": "cat", "mod05_contrato_detalle": "cat", "mod05_proveedor": "cat",
    "mod05_proveedor_detalle": "cat", "mod05_tiposervicio": "cat", "mod05_unidadinstitucional": "cat",
    "mod05_unidadmedida": "cat", "mod05_plaga": "cat",
    "mod05_areaverde": "prov", "mod05_ejemplar": "prov",
    "mod05_atenciontercerizada": "op", "mod05_alcanceatencion": "op", "mod05_historialestado": "op",
    "mod05_atencion_especial": "op",
    "mod05_reporteproveedor": "arch", "mod05_archivoasociado": "arch", "mod05_archivo": "arch",
    "mod05_usuario": "prov", "mod05_usuario_rol": "prov", "mod05_registroauditoria": "seg",
}

NOTAS = {
    "mod05_usuario": ["copia de core_usuario: se consolida en AV-287"],
    "mod05_usuario_rol": ["copia de los roles de MOD-01: se consolida en AV-287"],
    "mod05_areaverde": ["copia provisional de MOD-02: se consolida en AV-287"],
    "mod05_ejemplar": ["copia provisional de MOD-02: se consolida en AV-287"],
    "mod05_historialestado": ["solo inserciones (trigger)"],
    "mod05_registroauditoria": ["solo inserciones (trigger)"],
    "core_registro_auditoria": ["solo inserciones (trigger)"],
    "core_registro_acceso": ["solo inserciones (trigger)"],
    "core_usuario": ["trigger: el rol asignado tiene que estar activo", "personal_id sin FK (MOD-04)"],
    "mod03_programacion_servicio": ["cuadrilla_id sin FK (MOD-04)"],
}
# índices únicos que no son restricciones (parciales o sobre expresiones)
UNICOS_EXTRA = {
    "core_usuario": ["U lower(correo), si no es nulo"],
    "mod04_cuadrilla": ["U lower(trim(nombre))", "U capataz_id, si activa"],
    "mod04_asignacion_cuadrilla": ["U operario_id, si fecha_fin es nula"],
}

# Reglas CHECK, redactadas para leerlas de un vistazo
REGLAS = {
    "MOD-01": [
        "usuario: intentos_fallidos ≥ 0; correo con formato a@b",
        "permiso.codigo: MOD.RECURSO.ACCION en mayúsculas",
        "permiso.modulo: MOD-01 a MOD-06",
        "sesion.canal: oficina o campo",
        "registro_acceso.canal: oficina, campo o api",
        "registro_acceso.resultado: Exitoso, Fallido, Bloqueado, Denegado…",
        "registro_auditoria.accion: Alta, Modificacion, CambioEstado, CambioRol…",
        "archivo: tamano_bytes > 0",
        "adjunto.entidad: actividad, atencion, incidencia, ejemplar…",
    ],
    "MOD-03": [
        "frecuencia: estación Verano, Otoño, Invierno o Primavera",
        "frecuencia: teórica, práctica y máxima > 0; máxima ≥ práctica (o teórica)",
        "duración: duracion_maxima_dias > 0; dias_limpieza ≥ 0",
        "duración y restricción: hora_fin > hora_inicio",
        "tipo_restriccion.efecto: Prohibicion, Advertencia o Favorable",
        "restricción: fecha_fin ≥ fecha_inicio",
        "programación: estado Programada, EnCurso, Completada o Cancelada",
        "programación: ventana_mes AAAA-MM; fin prevista ≥ inicio prevista",
        "programación: fin real exige inicio real y es ≥ inicio real",
        "confirmación: justificación no vacía",
    ],
    "MOD-04": [
        "personal: nombres y apellidos no vacíos; tipo Capataz u Operario",
        "personal: solo un Capataz puede tener usuario_id",
        "cuadrilla: nombre no vacío",
        "asignación: fecha_fin ≥ fecha_inicio",
        "encargo: periodicidad Semanal (< 7 días) o Quincenal (< 14 días)",
        "encargo: fecha_fin ≥ fecha_inicio; plazo_previsto dentro del periodo",
        "parte_diario.estado: Abierto o Cerrado",
    ],
    "MOD-05": [
        "contrato: vigencia_fin ≥ vigencia_inicio",
        "contrato_detalle: monto_fijo ≥ 0",
        "proveedor_detalle: SinVinculo o Principal sin proveedor relacionado; Subsidiaria y Vinculada con él",
        "atención: fecha_hora_fin ≥ fecha_hora_inicio",
        "atención: origen SolicitudUnidad exige unidad_solicitante_id; Demanda y ProgramacionEstacional la dejan vacía",
        "alcance: área verde o ejemplar, nunca los dos",
        "alcance: metrado y unidad_medida_id van juntos (metrado ≥ 0)",
        "atención especial: 1 o 2 aplicaciones adicionales; realizadas entre 0 y las adicionales",
        "archivo: tamano_bytes ≥ 0; reporte: revision ≥ 0",
        "usuario_rol.rol: Jefe de sección, Ingeniero de campo o Practicante autorizado",
    ],
}

PAGINAS = [
    {
        "id": "mod01", "nombre": "MOD-01 · Core y seguridad",
        "titulo": "MOD-01 · Core y seguridad",
        "texto": "Usuarios, roles, permisos, sesiones, auditoría común y adjuntos (V6 a V10).",
        "columnas": [
            ["core_rol", "core_rol_permiso", "core_permiso"],
            ["core_usuario", "core_sesion"],
            ["core_registro_auditoria", "core_registro_acceso", "core_adjunto", "core_archivo"],
        ],
    },
    {
        "id": "mod03", "nombre": "MOD-03 · Planificación",
        "titulo": "MOD-03 · Planificación y corte de césped",
        "texto": "Frecuencias, duraciones, calendario de restricciones y programaciones (V2 a V4). Todavía referencia las copias de MOD-05 (AV-287).",
        "columnas": [
            ["mod03_programacion_servicio", "mod03_confirmacion_restriccion", "mod03_restriccion_calendario"],
            ["mod05_tiposervicio", "mod05_proveedor", "mod03_tipo_restriccion", "mod05_areaverde"],
            ["mod03_frecuencia_servicio", "mod03_duracion_servicio"],
        ],
    },
    {
        "id": "mod04", "nombre": "MOD-04 · Operación de campo",
        "titulo": "MOD-04 · Operación de campo",
        "texto": "Personal, cuadrillas, encargos, partes diarios y actividades (V11).",
        "desplazamiento": {1: 100},
        "columnas": [
            ["mod04_cuadrilla", "mod04_asignacion_cuadrilla"],
            ["mod04_personal", "mod04_encargo", "mod04_tipo_actividad", "mod04_idempotencia"],
            ["mod04_parte_diario", "mod04_actividad", "mod04_participacion_actividad", "mod05_areaverde"],
        ],
    },
    {
        "id": "mod05", "nombre": "MOD-05 · Servicios tercerizados",
        "titulo": "MOD-05 · Servicios tercerizados",
        "texto": "Proveedores, contratos, atenciones tercerizadas, reportes y atención especial (V1 y V5). MOD-06 no tiene tablas: lee las de MOD-05 y mock-data/.",
        "columnas": [
            ["mod05_proveedor", "mod05_proveedor_detalle", "mod05_contrato", "mod05_contrato_detalle",
             "mod05_tiposervicio", "mod05_unidadinstitucional", "mod05_unidadmedida"],
            ["mod05_atenciontercerizada", "mod05_alcanceatencion", "mod05_reporteproveedor",
             "mod05_archivoasociado", "mod05_archivo"],
            ["mod05_areaverde", "mod05_ejemplar", "mod05_atencion_especial", "mod05_plaga", "mod05_historialestado"],
            ["mod05_usuario", "mod05_usuario_rol", "mod05_registroauditoria"],
        ],
    },
]

MODULO_DE = lambda t: "MOD-01" if t.startswith("core_") else "MOD-" + t[3:5]

# ---- datos del esquema ----
columnas = {}
for c in d["columnas"]:
    columnas.setdefault(c["t"], []).append(c)
pk, uni, fks, uni_comp = {}, {}, {}, {}
for r in d["restricciones"]:
    t = r["t"]
    if r["tipo"] == "p":
        pk[t] = r["cols"]
    elif r["tipo"] == "u":
        if len(r["cols"]) == 1:
            uni.setdefault(t, set()).add(r["cols"][0])
        else:
            uni_comp.setdefault(t, []).append(r["cols"])
    elif r["tipo"] == "f":
        fks.setdefault(t, []).append(r)
fkcols = {t: {f["cols"][0] for f in v} for t, v in fks.items()}


def tipo(s):
    s = s.replace("character varying", "varchar").replace("timestamp with time zone", "timestamptz")
    s = s.replace("time without time zone", "time").replace("character(", "char(")
    return s


def es_unica(t, col):
    return col in uni.get(t, set()) or pk.get(t) == [col]


# ---- XML ----
celdas = []


def x(s):
    return escape(s, quote=True)


ESTILO_FILA = ("text;html=1;align=left;verticalAlign=middle;spacingLeft=8;spacingRight=4;overflow=hidden;"
               "rotatable=0;points=[[0,0.5],[1,0.5]];portConstraint=eastwest;fontSize=11;fontFamily=Helvetica;")
ESTILO_NOTA = "text;html=1;align=left;verticalAlign=middle;spacingLeft=8;fontSize=10;fontStyle=2;fontColor={};"
FILA, CAB, MARGEN = 22, 30, 6


AUDITORIA = {"usuario_creacion_id", "usuario_modificacion_id"}


def propia(pid, t):
    return t.startswith("core_") if pid == "mod01" else t.startswith(pid + "_")


def dibujar_fk(pid, t, f, en_pagina):
    """Las FK de auditoría y las que salen de la página se anotan en la columna en vez de dibujarse."""
    return f["cols"][0] not in AUDITORIA and f["ref"] in en_pagina


def destino_anotado(pid, t, col, en_pagina):
    for f in fks.get(t, []):
        if f["cols"][0] == col and not dibujar_fk(pid, t, f, en_pagina):
            return f["ref"]
    return None


def ancho_tabla(t, en_pagina, pid):
    largo = len(t) + 4
    for c in columnas[t]:
        destino = destino_anotado(pid, t, c["col"], en_pagina)
        largo = max(largo, len(c["col"]) + len(tipo(c["tipo"])) + 5 + (len(destino) + 3 if destino else 0))
    for n in NOTAS.get(t, []) + UNICOS_EXTRA.get(t, []):
        largo = max(largo, int(len(n) * 0.9) - 6)
    for u in uni_comp.get(t, []):
        largo = max(largo, int(len(", ".join(u)) * 0.9))
    return max(300, 62 + int(largo * 6.4))


def tabla(pid, t, xpos, ypos, ancho, en_pagina):
    relleno, borde = CAT[CATEGORIA[t]]
    filas = []
    for c in columnas[t]:
        marcas = []
        if c["col"] in (pk.get(t) or []):
            marcas.append("PK")
        if c["col"] in fkcols.get(t, set()):
            marcas.append("FK")
        if c["col"] in uni.get(t, set()):
            marcas.append("U")
        negrita = "font-weight:bold;" if "PK" in marcas else ""
        destino = destino_anotado(pid, t, c["col"], en_pagina)
        flecha = f'<span style="color:#999;"> → {destino}</span>' if destino else ""
        valor = (f'<span style="display:inline-block;width:46px;color:#555;font-size:10px;">{",".join(marcas)}</span>'
                 f'<span style="{negrita}">{c["col"]}</span>'
                 f'<span style="color:#777;"> : {x(tipo(c["tipo"]))}{" ?" if c["nulo"] else ""}</span>{flecha}')
        filas.append((f"{pid}_{t}__{c['col']}", valor, ESTILO_FILA))
    for u in uni_comp.get(t, []):
        filas.append((f"{pid}_{t}__u_{'_'.join(u)}", f"U ({', '.join(u)})", ESTILO_NOTA.format("#555555")))
    for i, u in enumerate(UNICOS_EXTRA.get(t, [])):
        filas.append((f"{pid}_{t}__ux{i}", u, ESTILO_NOTA.format("#555555")))
    for i, n in enumerate(NOTAS.get(t, [])):
        filas.append((f"{pid}_{t}__nota{i}", n, ESTILO_NOTA.format("#b85450")))
    return pintar(pid, t, xpos, ypos, ancho, filas,
                  f"fillColor={relleno};strokeColor={borde};")


def externa(pid, t, cols_ref, xpos, ypos, ancho):
    filas = [(f"{pid}_{t}__{c}",
              f'<span style="display:inline-block;width:46px;color:#555;font-size:10px;">PK</span>'
              f'<span style="font-weight:bold;">{c}</span>', ESTILO_FILA) for c in cols_ref]
    nota = f"tabla de {MODULO_DE(t)}"
    if t in ("mod05_areaverde", "mod05_ejemplar", "mod05_usuario"):
        nota += " (copia provisional, AV-287)"
    filas.append((f"{pid}_{t}__nota", nota, ESTILO_NOTA.format("#666666")))
    return pintar(pid, t, xpos, ypos, ancho, filas,
                  "fillColor=#ffffff;strokeColor=#999999;dashed=1;fontColor=#666666;")


def pintar(pid, t, xpos, ypos, ancho, filas, colores):
    alto = CAB + FILA * len(filas) + MARGEN
    celdas.append(
        f'<mxCell id="{pid}_{t}" value="{t}" style="swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;'
        f'startSize={CAB};horizontalStack=0;resizeParent=1;resizeParentMax=0;resizeLast=0;collapsible=0;'
        f'marginBottom={MARGEN};html=1;rounded=1;arcSize=4;{colores}'
        f'fontFamily=Helvetica;fontSize=13;swimlaneFillColor=#ffffff;" vertex="1" parent="1">'
        f'<mxGeometry x="{xpos}" y="{ypos}" width="{ancho}" height="{alto}" as="geometry"/></mxCell>')
    for i, (cid, valor, estilo) in enumerate(filas):
        celdas.append(f'<mxCell id="{cid}" value="{x(valor)}" style="{estilo}" vertex="1" parent="{pid}_{t}">'
                      f'<mxGeometry x="0" y="{CAB + FILA * i}" width="{ancho}" height="{FILA}" as="geometry"/></mxCell>')
    return alto


def caja(cid, html, xpos, ypos, ancho, alto, borde="#cccccc"):
    celdas.append(f'<mxCell id="{cid}" value="{x(html)}" style="rounded=1;arcSize=4;html=1;align=left;'
                  f'verticalAlign=top;whiteSpace=wrap;spacing=10;fontSize=11;fillColor=#ffffff;strokeColor={borde};" '
                  f'vertex="1" parent="1"><mxGeometry x="{xpos}" y="{ypos}" width="{ancho}" height="{alto}" '
                  f'as="geometry"/></mxCell>')


LEYENDA = ('<b>Leyenda</b><br>PK clave primaria · FK clave foránea · U único<br><code>?</code> admite nulos<br>'
           'Colores: <span style="background:#dae8fc">&nbsp;seguridad y auditoría&nbsp;</span> '
           '<span style="background:#d5e8d4">&nbsp;operación&nbsp;</span> '
           '<span style="background:#fff2cc">&nbsp;catálogos y configuración&nbsp;</span> '
           '<span style="background:#e1d5e7">&nbsp;reportes y archivos&nbsp;</span> '
           '<span style="background:#f5f5f5">&nbsp;copia provisional&nbsp;</span><br>'
           'Borde discontinuo: tabla de otro módulo (solo la clave referenciada)<br>'
           '<span style="color:#999;">→ tabla</span>: FK que no se dibuja como línea (auditoría o tabla de otra página)')

# comprobación: cada tabla de la base está en una página y tiene categoría
propias_paginas = {t for p in PAGINAS for c in p["columnas"] for t in c if propia(p["id"], t)}
faltan = sorted(set(columnas) - propias_paginas)
if faltan:
    raise SystemExit(f"Tablas sin colocar en PAGINAS: {', '.join(faltan)}")
sin_categoria = sorted(set(columnas) - set(CATEGORIA))
if sin_categoria:
    raise SystemExit(f"Tablas sin categoría en CATEGORIA: {', '.join(sin_categoria)}")

diagramas = []
for pag in PAGINAS:
    celdas = ['<mxCell id="0"/>', '<mxCell id="1" parent="0"/>']
    pid = pag["id"]
    en_pagina = [t for col in pag["columnas"] for t in col]
    propias = [t for t in en_pagina if propia(pid, t)]
    # de las tablas de otros módulos solo se dibuja la clave a la que apuntan las FK dibujadas
    ref_ext = {t: set() for t in en_pagina if not propia(pid, t)}
    for t in propias:
        for f in fks.get(t, []):
            if dibujar_fk(pid, t, f, en_pagina) and f["ref"] in ref_ext:
                ref_ext[f["ref"]].update(f["refcols"])
    titulo = ('<b style="font-size:20px;">Modelo entidad-relación · ' + pag["titulo"] + "</b><br>" + pag["texto"]
              + "<br>Esquema real de PostgreSQL tras las migraciones de <code>main</code> (" + MIGRACIONES + "), "
              + FECHA + ". " + str(len(propias)) + " tablas propias. No se dibujan <code>flyway_schema_history</code> "
              "ni <code>spatial_ref_sys</code> (PostGIS).")
    celdas.append(
        f'<mxCell id="{pid}_titulo" value="{x(titulo)}" '
        f'style="text;html=1;align=left;verticalAlign=top;whiteSpace=wrap;fontFamily=Helvetica;fontSize=12;" '
        f'vertex="1" parent="1"><mxGeometry x="40" y="20" width="1140" height="90" as="geometry"/></mxCell>')
    xpos, top, gap_x, gap_y = 40, 150, 120, 40
    alturas = []
    for i, col in enumerate(pag["columnas"]):
        ancho = max(ancho_tabla(t, en_pagina, pid) if propia(pid, t) else 300 for t in col)
        y = top + pag.get("desplazamiento", {}).get(i, 0)
        for t in col:
            if propia(pid, t):
                y += tabla(pid, t, xpos, y, ancho, en_pagina) + gap_y
            else:
                y += externa(pid, t, sorted(ref_ext[t]), xpos, y, ancho) + gap_y
        alturas.append((xpos, ancho, y))
        xpos += ancho + gap_x
    # leyenda y reglas debajo de la columna más corta
    xl, al, yl = min(alturas, key=lambda a: a[2])
    al = max(al, 360)
    caja(f"{pid}_leyenda", LEYENDA, xl, yl, al, 135)
    reglas = REGLAS[pid.upper().replace("MOD", "MOD-")]
    por_linea = (al - 20) / 6.0
    alto_reglas = 36 + 13 * sum(1 + int(len(r) / por_linea) for r in reglas)
    caja(f"{pid}_reglas", "<b>Reglas de la base de datos (CHECK)</b><br>" + "<br>".join(x(r) for r in reglas),
         xl, yl + 135 + gap_y, al, alto_reglas)
    alturas.append((xl, al, yl + 135 + gap_y + alto_reglas))
    # relaciones
    n = 0
    for t in propias:
        for f in fks.get(t, []):
            if not dibujar_fk(pid, t, f, en_pagina):
                continue
            col = f["cols"][0]
            nulo = next(c["nulo"] for c in columnas[t] if c["col"] == col)
            inicio = ("ERzeroToOne" if nulo else "ERmandOne") if es_unica(t, col) else "ERmany"
            fin = "ERzeroToOne" if nulo else "ERmandOne"
            celdas.append(
                f'<mxCell id="{pid}_fk{n}" style="edgeStyle=entityRelationEdgeStyle;html=1;rounded=1;'
                f'endArrow={fin};startArrow={inicio};endFill=0;startFill=0;strokeColor=#666666;strokeWidth=1;" '
                f'edge="1" parent="1" source="{pid}_{t}__{col}" target="{pid}_{f["ref"]}__{f["refcols"][0]}">'
                f'<mxGeometry relative="1" as="geometry"/></mxCell>')
            n += 1
    diagramas.append(
        f'<diagram id="{pid}" name="{x(pag["nombre"])}"><mxGraphModel dx="1600" dy="1000" grid="1" gridSize="10" '
        f'guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="{xpos}" '
        f'pageHeight="{max(a[2] for a in alturas) + 40}" math="0" shadow="0"><root>\n' + "\n".join(celdas)
        + "\n</root></mxGraphModel></diagram>")


SALIDA.write_text('<mxfile host="drawio" agent="konoha">\n' + "\n".join(diagramas) + "\n</mxfile>\n")
print(f"{SALIDA.relative_to(RAIZ)}: {len(propias_paginas)} tablas en {len(PAGINAS)} páginas ({MIGRACIONES})")

if shutil.which("drawio"):
    for i, pag in enumerate(PAGINAS, start=1):
        png = DESTINO / f"modelo-er-{pag['id']}.png"
        subprocess.run(["drawio", "-x", "-f", "png", "-s", "1.5", "-b", "20", "-p", str(i), "-o", str(png),
                        str(SALIDA)], check=True, capture_output=True)
        print(f"{png.relative_to(RAIZ)}")
else:
    print("Sin el CLI de draw.io: exporta los PNG a mano (ver README.md).")
