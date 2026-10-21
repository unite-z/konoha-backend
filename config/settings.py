"""
Configuración mínima de arranque del back-end del MOD-06 «Reportes, indicadores e IA».

Los valores se leen de backend/.env (ver backend/.env.example).
"""

import os
from pathlib import Path

from django.core.exceptions import ImproperlyConfigured
from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent

# Carga backend/.env antes de leer cualquier variable.
load_dotenv(BASE_DIR / ".env")


# --- Django -----------------------------------------------------------------

SECRET_KEY = os.getenv("DJANGO_SECRET_KEY", "")
if not SECRET_KEY:
    raise ImproperlyConfigured("Falta DJANGO_SECRET_KEY en backend/.env.")

DEBUG = os.getenv("DJANGO_DEBUG", "False").strip().lower() in ("true", "1", "yes")

ALLOWED_HOSTS = [
    host.strip()
    for host in os.getenv("DJANGO_ALLOWED_HOSTS", "").split(",")
    if host.strip()
]


# --- Fuente de datos del MOD-06 ---------------------------------------------

DATA_SOURCE = os.getenv("DATA_SOURCE", "mock").strip().lower()
if DATA_SOURCE not in ("mock", "db"):
    raise ImproperlyConfigured(
        f"DATA_SOURCE='{DATA_SOURCE}' no es válido. "
        "Usa 'mock' o 'db' en backend/.env."
    )

MOCK_DATA_DIR = (BASE_DIR / os.getenv("MOCK_DATA_DIR", "../mock-data")).resolve()


# --- Gemini (Google AI Studio) ----------------------------------------------

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "")


# --- Aplicaciones y middleware ------------------------------------------------

INSTALLED_APPS = [
    "django.contrib.contenttypes",
    "django.contrib.auth",
    "django.contrib.staticfiles",
    "rest_framework",
    "drf_spectacular",
    "reportes_indicadores",
    "tercerizados",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.middleware.common.CommonMiddleware",
]

ROOT_URLCONF = "config.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.request",
            ],
        },
    },
]

WSGI_APPLICATION = "config.wsgi.application"


# --- Base de datos ------------------------------------------------------------

TERCERIZADOS_ENABLED = os.getenv("TERCERIZADOS_ENABLED", "False").lower() in ("true", "1", "yes")

if DATA_SOURCE == "db" or TERCERIZADOS_ENABLED:
    # Pasar a `django.contrib.gis.db.backends.postgis` cuando se active GeoDjango (ADR-001).
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.postgresql",
            "NAME": os.getenv("POSTGRES_DB", ""),
            "USER": os.getenv("POSTGRES_USER", ""),
            "PASSWORD": os.getenv("POSTGRES_PASSWORD", ""),
            "HOST": os.getenv("POSTGRES_HOST", "localhost"),
            "PORT": os.getenv("POSTGRES_PORT", "5432"),
        }
    }
else:
    # Modo mock: sin base de datos. El servidor arranca sin PostgreSQL.
    DATABASES = {}

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"


# --- Django REST Framework y OpenAPI ------------------------------------------

REST_FRAMEWORK = {
    "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
    # TEMPORAL: sustituir por la autenticación por token y los permisos por rol
    # del MOD-01 (RF-T-001, RF-T-004, RNF-007).
    "DEFAULT_AUTHENTICATION_CLASSES": [],
    "DEFAULT_PERMISSION_CLASSES": ["rest_framework.permissions.AllowAny"],
    "EXCEPTION_HANDLER": "reportes_indicadores.api.manejador_excepciones.manejar_excepcion",
}

SPECTACULAR_SETTINGS = {
    "TITLE": "API MOD-06 Reportes, indicadores e IA",
    "DESCRIPTION": (
        "API del MOD-06: RF-REP-002, RF-REP-004, RF-REP-006 y RF-IA-001. Las consultas no modifican registros "
        "operativos. Las propuestas de IA requieren validación humana (RN-IA-001)"
    ),
    "VERSION": "0.1.0",
}


# --- Logging ------------------------------------------------------------------

LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "handlers": {"consola": {"class": "logging.StreamHandler"}},
    "loggers": {
        "reportes_indicadores": {"handlers": ["consola"], "level": "INFO"},
    },
}


# --- Internacionalización -----------------------------------------------------

LANGUAGE_CODE = "es"

# Supuesto: campus en Lima. La zona horaria está pendiente en el Modelo de BD v1 (sección 11.2).
TIME_ZONE = "America/Lima"

USE_I18N = True

USE_TZ = True


# --- Archivos estáticos -------------------------------------------------------

STATIC_URL = "static/"

# MOD-05 usa PostgreSQL aunque MOD-06 siga leyendo mocks. Sin MEDIA_URL pública.
TERCERIZADOS_MAX_ARCHIVO_BYTES = int(os.getenv("TERCERIZADOS_MAX_ARCHIVO_BYTES", str(20 * 1024 * 1024)))
STORAGES = {
    "default": {"BACKEND": "django.core.files.storage.FileSystemStorage"},
    "staticfiles": {"BACKEND": "django.contrib.staticfiles.storage.StaticFilesStorage"},
    "tercerizados": {
        "BACKEND": "django.core.files.storage.FileSystemStorage",
        "OPTIONS": {"location": (os.getenv("TERCERIZADOS_ARCHIVOS_DIR") or str(BASE_DIR / "private-media")), "base_url": None},
    },
}
