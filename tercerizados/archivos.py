"""Almacenamiento privado; nunca exponer sus rutas mediante MEDIA_URL."""
import hashlib
import zipfile
from pathlib import Path
from django.conf import settings
from django.core.files.storage import storages
from .errores import ErrorDominio

MIMES = {
    '.pdf': 'application/pdf', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png',
    '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
}


def almacenamiento():
    return storages['tercerizados']


def validar(archivo):
    nombre = Path(archivo.name.replace('\\', '/')).name
    extension = Path(nombre).suffix.lower()
    if not nombre or len(nombre) > 200 or extension not in MIMES:
        raise ErrorDominio('Archivo no admitido. Usa PDF, JPEG, PNG, DOCX o XLSX')
    if archivo.size <= 0 or archivo.size > settings.TERCERIZADOS_MAX_ARCHIVO_BYTES:
        raise ErrorDominio('El archivo está vacío o supera el tamaño máximo permitido')
    cabecera = archivo.read(8)
    archivo.seek(0)
    valido = (
        (extension == '.pdf' and cabecera.startswith(b'%PDF-')) or
        (extension in {'.jpg', '.jpeg'} and cabecera.startswith(b'\xff\xd8\xff')) or
        (extension == '.png' and cabecera == b'\x89PNG\r\n\x1a\n')
    )
    if extension in {'.docx', '.xlsx'}:
        try:
            with zipfile.ZipFile(archivo) as paquete:
                nombres = set(paquete.namelist())
                entrada = 'word/document.xml' if extension == '.docx' else 'xl/workbook.xml'
                valido = '[Content_Types].xml' in nombres and entrada in nombres
        except (zipfile.BadZipFile, OSError):
            valido = False
        finally:
            archivo.seek(0)
    if not valido:
        raise ErrorDominio('El contenido del archivo no corresponde a su extensión')
    digest = hashlib.sha256()
    for chunk in archivo.chunks():
        digest.update(chunk)
    archivo.seek(0)
    return dict(nombre_original=nombre, tipo_mime=MIMES[extension], tamano_bytes=archivo.size, hash_contenido=digest.hexdigest())
