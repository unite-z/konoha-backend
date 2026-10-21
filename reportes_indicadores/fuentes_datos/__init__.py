"""
Selección de la fuente de datos del MOD-06.

Punto único que devuelve la fuente de datos según `settings.DATA_SOURCE` (`mock` → `mock.py`, `db` → `bd.py`).
Ningún otro módulo debe leer `DATA_SOURCE` ni los JSON directamente.

Requisitos que atiende:
- RF-REP-002 (HU-MOD06-009, CU-MOD06-009, CP-TR-MOD06-009)
- RF-REP-004 (HU-MOD06-002, sin caso de prueba)
- RF-REP-006 (HU-MOD06-014, CU-MOD06-014, CP-TR-MOD06-014)
- RF-IA-001 (HU-MOD06-010, CU-MOD06-010, CP-TR-MOD06-010)

Entidades que leerá:
- TipoServicio, FrecuenciaServicio, DuracionServicio, ProgramacionServicio, CicloCorte, RutaCorte, Proveedor
- ParteDiario, Actividad, ParticipacionActividad, Personal, Cuadrilla, AsignacionCuadrilla, ClaseActividad, TipoActividad, UnidadMedida, AreaVerde, Lugar
- AreaVerde, SectorMantenimiento, AsignacionSector, Cuartel, Actividad, AtencionTercerizada, AlcanceAtencion, AplicacionFitosanitaria, AplicacionObjetivo, IntervencionArbolado, Ejemplar
- AtencionTercerizada, Contrato, TipoServicio, ReporteProveedor, FormatoReporteServicio, CampoFormatoReporte, Archivo, ArchivoAsociado, SugerenciaIA, Plaga, ProductoAgronomico, UnidadMedida, Usuario

TODO: implementar
"""
