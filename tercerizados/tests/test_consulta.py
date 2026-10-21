"""`/me`, filtros y búsqueda de listados, catálogos y nombres en las respuestas."""
import base64
from datetime import timedelta
import pytest
from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.db import connection
from django.test.utils import CaptureQueriesContext
from django.utils import timezone
from rest_framework.test import APIClient
from tercerizados import models as m
from tercerizados.permisos import ROLES
from .test_api import BASE, crear, ejecutar, reporte

pytestmark = pytest.mark.django_db
RAIZ = '/api/tercerizados/'
ME = RAIZ + 'me/'


def usuario_con(nombre, *grupos, **datos):
    user = get_user_model().objects.create_user(username=nombre, password='clave-local', **datos)
    for grupo in grupos:
        user.groups.add(Group.objects.get_or_create(name=grupo)[0])
    return user


def con_sesion(user):
    client = APIClient()
    client.force_authenticate(user)
    return client


def basic(usuario, clave):
    return 'Basic ' + base64.b64encode(f'{usuario}:{clave}'.encode()).decode()


# --- /me -----------------------------------------------------------------------------------------------

@pytest.mark.parametrize('grupo', ROLES)
def test_me_devuelve_rol_y_nombre(grupo):
    user = usuario_con('ana', grupo, first_name='Ana', last_name='Rojas')
    response = con_sesion(user).get(ME)
    assert response.status_code == 200
    assert response.data == {'id': user.pk, 'usuario': 'ana', 'nombre': 'Ana Rojas', 'rol': grupo}


def test_me_sin_nombre_usa_el_usuario():
    assert con_sesion(usuario_con('jperez', ROLES[1])).get(ME).data['nombre'] == 'jperez'


def test_me_usa_el_rol_de_mayor_jerarquia():
    user = usuario_con('mixto', ROLES[2], ROLES[1], ROLES[0])
    assert con_sesion(user).get(ME).data['rol'] == ROLES[0]
    user = usuario_con('mixto2', ROLES[2], ROLES[1])
    assert con_sesion(user).get(ME).data['rol'] == ROLES[1]


@pytest.mark.parametrize('grupos', [(), ('Practicante',), ('Administradores',)])
def test_me_usuario_sin_rol_del_modulo_recibe_rol_nulo(grupos):
    user = usuario_con('sinrol', *grupos)
    user.is_staff = user.is_superuser = True
    user.save()
    response = con_sesion(user).get(ME)
    assert response.status_code == 200 and response.data['rol'] is None
    # Seguir sin acceso al resto del módulo.
    assert con_sesion(user).get(BASE).status_code == 403


def test_me_con_http_basic():
    usuario_con('basico', ROLES[0])
    response = APIClient().get(ME, HTTP_AUTHORIZATION=basic('basico', 'clave-local'))
    assert response.status_code == 200 and response.data['usuario'] == 'basico'


@pytest.mark.parametrize('cabecera', [None, basic('basico', 'incorrecta'), basic('inexistente', 'clave-local'), 'Basic !!!'])
def test_me_credenciales_invalidas_o_ausentes_son_403(cabecera):
    usuario_con('basico', ROLES[0])
    extra = {'HTTP_AUTHORIZATION': cabecera} if cabecera else {}
    assert APIClient().get(ME, **extra).status_code == 403


def test_me_usuario_inactivo_es_403():
    usuario_con('baja', ROLES[0], is_active=False)
    assert APIClient().get(ME, HTTP_AUTHORIZATION=basic('baja', 'clave-local')).status_code == 403


def test_me_solo_admite_lectura(cliente):
    assert cliente.post(ME, {}, format='json').status_code == 405


# --- Filtros y búsqueda de atenciones --------------------------------------------------------------------

@pytest.fixture
def escenario(cliente, referencias, entrada):
    """Tres atenciones: A (Demanda, Requerimiento), B (Unidad, OC referenciada), C (Estacional, otro contrato)."""
    contrato, area, ejemplar, _ = referencias
    proveedor2 = m.Proveedor.objects.create(razon_social='Verde Norte SAC')
    servicio2 = m.TipoServicio.objects.create(nombre='Poda de altura', ejecutor='Tercerizado')
    hoy = timezone.localdate()
    contrato2 = m.Contrato.objects.create(numero='CT-NORTE', proveedor=proveedor2, tipo_servicio=servicio2,
        vigencia_inicio=hoy - timedelta(days=5), vigencia_fin=hoy + timedelta(days=5))
    a = crear(cliente, {**entrada, 'descripcion': 'Corte de césped del jardín central'})
    b = crear(cliente, {**entrada, 'descripcion': 'Riego complementario', 'origen': 'SolicitudUnidad',
                         'unidad_provisional': 'Biblioteca Central'})
    c = crear(cliente, {**entrada, 'contrato_id': contrato2.pk, 'origen': 'ProgramacionEstacional',
                         'descripcion': 'Poda estacional'})
    assert cliente.patch(f'{BASE}{b["id"]}/orden-compra/', {'numero_orden_compra': 'OC-777'}, format='json').status_code == 200
    return {'a': a, 'b': b, 'c': c, 'contrato': contrato, 'contrato2': contrato2}


def ids(response):
    assert response.status_code == 200, response.data
    return {fila['id'] for fila in response.data['results']}


def test_sin_filtros_lista_todo(cliente, escenario):
    response = cliente.get(BASE)
    assert response.data['count'] == 3
    assert [f['id'] for f in response.data['results']] == [escenario[k]['id'] for k in ('c', 'b', 'a')]


def test_filtra_por_estado(cliente, escenario):
    assert ids(cliente.get(BASE, {'estado': 'OC referenciada'})) == {escenario['b']['id']}
    assert ids(cliente.get(BASE, {'estado': 'Requerimiento registrado'})) == {escenario['a']['id'], escenario['c']['id']}
    assert ids(cliente.get(BASE, {'estado': 'En ejecución'})) == set()


def test_filtra_por_origen(cliente, escenario):
    assert ids(cliente.get(BASE, {'origen': 'SolicitudUnidad'})) == {escenario['b']['id']}
    assert ids(cliente.get(BASE, {'origen': 'ProgramacionEstacional'})) == {escenario['c']['id']}


def test_filtra_por_contrato(cliente, escenario):
    assert ids(cliente.get(BASE, {'contrato_id': escenario['contrato2'].pk})) == {escenario['c']['id']}


def test_filtros_se_combinan(cliente, escenario):
    respuesta = cliente.get(BASE, {'estado': 'Requerimiento registrado', 'origen': 'Demanda',
                                   'contrato_id': escenario['contrato'].pk})
    assert ids(respuesta) == {escenario['a']['id']}
    assert ids(cliente.get(BASE, {'estado': 'OC referenciada', 'origen': 'Demanda'})) == set()


@pytest.mark.parametrize('busqueda, esperado', [
    ('césped', {'a'}), ('CÉSPED', {'a'}), ('riego', {'b'}), ('OC-777', {'b'}), ('verde norte', {'c'}),
    ('CT-NORTE', {'c'}), ('poda', {'c'}), ('biblioteca', {'b'}), ('proveedor de prueba', {'a', 'b'}),
    ('jardín central', {'a'}), ('jardín riego', set()), ('   ', {'a', 'b', 'c'}), ('no-existe', set()),
])
def test_busqueda_por_palabras(cliente, escenario, busqueda, esperado):
    assert ids(cliente.get(BASE, {'q': busqueda})) == {escenario[k]['id'] for k in esperado}


def test_busqueda_por_codigo(cliente, escenario):
    codigo = escenario['b']['codigo']
    assert ids(cliente.get(BASE, {'q': 'AT-'})) == {escenario['a']['id'], escenario['b']['id'], escenario['c']['id']}
    assert ids(cliente.get(BASE, {'q': codigo[:20].lower()})) == {escenario['b']['id']}
    assert ids(cliente.get(BASE, {'q': codigo})) == {escenario['b']['id']}


def test_busqueda_no_interpreta_comodines(cliente, escenario):
    assert ids(cliente.get(BASE, {'q': '%'})) == set()
    assert ids(cliente.get(BASE, {'q': '_'})) == set()


def test_filtros_con_busqueda(cliente, escenario):
    assert ids(cliente.get(BASE, {'q': 'poda', 'estado': 'Requerimiento registrado'})) == {escenario['c']['id']}
    assert ids(cliente.get(BASE, {'q': 'poda', 'origen': 'Demanda'})) == set()


@pytest.mark.parametrize('parametros, campo', [
    ({'estado': 'Inventado'}, 'estado'), ({'origen': 'demanda'}, 'origen'),
    ({'contrato_id': 'abc'}, 'contrato_id'), ({'contrato_id': '0'}, 'contrato_id'), ({'contrato_id': '-3'}, 'contrato_id'),
    ({'q': 'x' * 101}, 'q'),
])
def test_filtro_invalido_responde_400(cliente, escenario, parametros, campo):
    response = cliente.get(BASE, parametros)
    assert response.status_code == 400
    assert campo in response.data


def test_valores_vacios_y_parametros_desconocidos_se_ignoran(cliente, escenario):
    assert ids(cliente.get(BASE, {'estado': '', 'origen': ' ', 'contrato_id': '', 'otro': 'x'})) == {
        escenario[k]['id'] for k in 'abc'}


def test_paginacion_conserva_el_total_filtrado(cliente, referencias, entrada, escenario):
    for i in range(27):
        crear(cliente, {**entrada, 'descripcion': f'Masivo {i}'})
    primera = cliente.get(BASE, {'q': 'masivo'})
    assert primera.data['count'] == 27 and len(primera.data['results']) == 25 and primera.data['next']
    segunda = cliente.get(BASE, {'q': 'masivo', 'page': 2})
    assert len(segunda.data['results']) == 2 and segunda.data['next'] is None


def test_filtros_disponibles_para_los_tres_roles(escenario):
    for grupo in ROLES:
        assert ids(con_sesion(usuario_con(f'u-{grupo}', grupo)).get(BASE, {'estado': 'OC referenciada'})) == {escenario['b']['id']}


# --- Nombres en las respuestas ------------------------------------------------------------------------------

def test_atencion_incluye_nombres(cliente, usuario, escenario):
    usuario.first_name, usuario.last_name = 'Robert', 'Gómez'
    usuario.save()
    detalle = cliente.get(f'{BASE}{escenario["b"]["id"]}/').data
    assert detalle['contrato_numero'] == 'CT-TEST'
    assert detalle['proveedor_nombre'] == 'Proveedor de prueba' and detalle['proveedor_id']
    assert detalle['tipo_servicio_nombre'] == 'Corte'
    assert detalle['unidad_solicitante_nombre'] == 'Biblioteca Central'
    assert detalle['unidad_solicitante_provisional'] is True
    assert detalle['solicitado_por_nombre'] == 'Robert Gómez' and detalle['usuario_creacion_nombre'] == 'Robert Gómez'
    assert [h['usuario_nombre'] for h in detalle['historial']] == ['Robert Gómez', 'Robert Gómez']
    assert [h['estado_nuevo'] for h in detalle['historial']] == ['Requerimiento registrado', 'OC referenciada']
    # Los IDs siguen presentes.
    assert detalle['contrato_id'] and detalle['tipo_servicio_id'] and detalle['unidad_solicitante_id']


def test_atencion_sin_unidad_devuelve_nulos(cliente, escenario):
    detalle = cliente.get(f'{BASE}{escenario["a"]["id"]}/').data
    assert detalle['unidad_solicitante_nombre'] is None and detalle['unidad_solicitante_provisional'] is None


def test_alcances_incluyen_nombres_y_orden_estable(cliente, referencias, escenario):
    _, area, ejemplar, unidad = referencias
    alcances = cliente.get(f'{BASE}{escenario["a"]["id"]}/').data['alcances']
    assert [a['id'] for a in alcances] == sorted(a['id'] for a in alcances)
    por_area = next(a for a in alcances if a['area_verde_id'])
    por_ejemplar = next(a for a in alcances if a['ejemplar_id'])
    assert por_area['area_verde_nombre'] == 'Jardín A' and por_area['ejemplar_codigo'] is None
    assert por_ejemplar['ejemplar_codigo'] == 'E-01' and por_ejemplar['ejemplar_area_verde_nombre'] == 'Jardín A'
    assert por_ejemplar['area_verde_nombre'] is None


def test_alcance_con_metrado_incluye_unidad(cliente, referencias, entrada):
    _, area, _, unidad = referencias
    entrada['alcances'] = [{'area_verde_id': area.pk, 'metrado': '120.50', 'unidad_medida_id': unidad.pk}]
    alcance = crear(cliente, entrada)['alcances'][0]
    assert alcance['unidad_medida_nombre'] == 'Metros cuadrados' and alcance['unidad_medida_simbolo'] == 'm2'


def test_respuestas_de_operaciones_tambien_traen_nombres(cliente, entrada):
    creada = crear(cliente, entrada)
    assert creada['proveedor_nombre'] == 'Proveedor de prueba'
    modificada = cliente.patch(f'{BASE}{creada["id"]}/orden-compra/', {'numero_orden_compra': 'OC-1'}, format='json')
    assert modificada.data['contrato_numero'] == 'CT-TEST' and modificada.data['historial'][-1]['usuario_nombre'] == 'jefe'


def test_reportes_incluyen_responsable(cliente, entrada, almacenamiento_privado):
    atencion = crear(cliente, entrada)
    ejecutar(cliente, atencion['id'])
    assert reporte(cliente, atencion['id']).data['usuario_creacion_nombre'] == 'jefe'
    assert cliente.get(f'{BASE}{atencion["id"]}/reportes/').data['results'][0]['usuario_creacion_nombre'] == 'jefe'


def test_listado_no_hace_consultas_por_fila(cliente, referencias, entrada):
    def consultas():
        with CaptureQueriesContext(connection) as capturadas:
            assert cliente.get(BASE).status_code == 200
        return len(capturadas)
    crear(cliente, {**entrada, 'origen': 'SolicitudUnidad', 'unidad_provisional': 'Facultad X'})
    base = consultas()
    for i in range(6):
        crear(cliente, {**entrada, 'origen': 'SolicitudUnidad', 'unidad_provisional': f'Unidad {i}'})
    assert consultas() == base


def test_listado_de_reportes_no_hace_consultas_por_fila(cliente, entrada, almacenamiento_privado):
    atencion = crear(cliente, entrada)
    ejecutar(cliente, atencion['id'])
    reporte(cliente, atencion['id'])
    url = f'{BASE}{atencion["id"]}/reportes/'

    def consultas():
        with CaptureQueriesContext(connection) as capturadas:
            assert cliente.get(url).status_code == 200
        return len(capturadas)
    base = consultas()
    for _ in range(3):
        reporte(cliente, atencion['id'])
    assert consultas() == base


# --- Catálogos ----------------------------------------------------------------------------------------------

@pytest.fixture
def catalogos(referencias):
    contrato, area, ejemplar, unidad = referencias
    hoy = timezone.localdate()
    proveedor2 = m.Proveedor.objects.create(razon_social='Aguas del Sur', activo=False)
    poda = m.TipoServicio.objects.create(nombre='Poda de altura', ejecutor='Tercerizado', es_riesgo=True)
    propio = m.TipoServicio.objects.create(nombre='Riego', ejecutor='PersonalPropio', activo=False)
    vencido = m.Contrato.objects.create(numero='CT-VENCIDO', proveedor=proveedor2, tipo_servicio=poda,
        vigencia_inicio=hoy - timedelta(days=60), vigencia_fin=hoy - timedelta(days=1))
    futuro = m.Contrato.objects.create(numero='CT-FUTURO', proveedor=contrato.proveedor, tipo_servicio=poda,
        vigencia_inicio=hoy + timedelta(days=1), vigencia_fin=hoy + timedelta(days=30))
    area2 = m.AreaVerde.objects.create(nombre='Bosque Húmedo', activo=False)
    e2 = m.Ejemplar.objects.create(codigo='E-02', area_verde=area)
    e3 = m.Ejemplar.objects.create(codigo='E-100', area_verde=area2)
    suelto = m.Ejemplar.objects.create(codigo='E-SUELTO')
    oficial = m.UnidadInstitucional.objects.create(nombre='Biblioteca', provisional=False)
    prov = m.UnidadInstitucional.objects.create(nombre='Facultad Nueva', provisional=True, activo=False)
    return dict(contrato=contrato, vencido=vencido, futuro=futuro, area=area, area2=area2, e1=ejemplar, e2=e2, e3=e3,
                suelto=suelto, oficial=oficial, prov=prov, proveedor2=proveedor2, poda=poda, propio=propio)


def lista(cliente, ruta, **parametros):
    response = cliente.get(RAIZ + 'catalogos/' + ruta + '/', parametros)
    assert response.status_code == 200, response.data
    return response.data['results']


def test_contratos_vigentes(cliente, catalogos):
    assert [c['numero'] for c in lista(cliente, 'contratos', vigente='true')] == ['CT-TEST']
    assert [c['numero'] for c in lista(cliente, 'contratos', vigente='false')] == ['CT-FUTURO', 'CT-VENCIDO']
    assert [c['numero'] for c in lista(cliente, 'contratos')] == ['CT-FUTURO', 'CT-TEST', 'CT-VENCIDO']


def test_contratos_incluyen_nombres_y_ids(cliente, catalogos):
    contrato = lista(cliente, 'contratos', vigente='true')[0]
    assert contrato['proveedor_nombre'] == 'Proveedor de prueba' and contrato['tipo_servicio_nombre'] == 'Corte'
    assert contrato['proveedor_id'] and contrato['tipo_servicio_id']
    assert contrato['vigencia_inicio'] and contrato['vigencia_fin']


def test_contratos_filtran_y_buscan(cliente, catalogos):
    assert [c['numero'] for c in lista(cliente, 'contratos', tipo_servicio_id=catalogos['poda'].pk)] == ['CT-FUTURO', 'CT-VENCIDO']
    assert [c['numero'] for c in lista(cliente, 'contratos', proveedor_id=catalogos['proveedor2'].pk)] == ['CT-VENCIDO']
    assert [c['numero'] for c in lista(cliente, 'contratos', q='aguas')] == ['CT-VENCIDO']
    assert [c['numero'] for c in lista(cliente, 'contratos', q='poda', vigente='true')] == []
    assert [c['numero'] for c in lista(cliente, 'contratos', q='ct-test')] == ['CT-TEST']


def test_vigencia_usa_la_fecha_de_lima(cliente, catalogos, monkeypatch):
    from tercerizados.api import views
    hoy = timezone.localdate()
    monkeypatch.setattr(views.timezone, 'localdate', lambda: hoy + timedelta(days=2))
    assert [c['numero'] for c in lista(cliente, 'contratos', vigente='true')] == ['CT-FUTURO', 'CT-TEST']


def test_areas_verdes_buscan_y_filtran(cliente, catalogos):
    assert [a['nombre'] for a in lista(cliente, 'areas-verdes')] == ['Bosque Húmedo', 'Jardín A']
    assert [a['nombre'] for a in lista(cliente, 'areas-verdes', activo='true')] == ['Jardín A']
    assert [a['nombre'] for a in lista(cliente, 'areas-verdes', activo='false')] == ['Bosque Húmedo']
    assert [a['nombre'] for a in lista(cliente, 'areas-verdes', q='bosque hú')] == ['Bosque Húmedo']


def test_ejemplares_por_area_y_busqueda(cliente, catalogos):
    area = catalogos['area']
    assert [e['codigo'] for e in lista(cliente, 'ejemplares', area_verde_id=area.pk)] == ['E-01', 'E-02']
    assert [e['codigo'] for e in lista(cliente, 'ejemplares', q='e-1')] == ['E-100']
    assert [e['codigo'] for e in lista(cliente, 'ejemplares', q='bosque')] == ['E-100']
    assert [e['codigo'] for e in lista(cliente, 'ejemplares', q='02', area_verde_id=area.pk)] == ['E-02']
    assert lista(cliente, 'ejemplares', q='02', area_verde_id=catalogos['area2'].pk) == []


def test_ejemplares_incluyen_nombre_del_area_o_nulo(cliente, catalogos):
    filas = {e['codigo']: e for e in lista(cliente, 'ejemplares')}
    assert filas['E-01']['area_verde_nombre'] == 'Jardín A' and filas['E-01']['area_verde_id']
    assert filas['E-SUELTO']['area_verde_nombre'] is None and filas['E-SUELTO']['area_verde_id'] is None


def test_unidades_institucionales_filtran(cliente, catalogos):
    assert [u['nombre'] for u in lista(cliente, 'unidades-institucionales', provisional='true')] == ['Facultad Nueva']
    assert [u['nombre'] for u in lista(cliente, 'unidades-institucionales', activo='true')] == ['Biblioteca']
    assert [u['nombre'] for u in lista(cliente, 'unidades-institucionales', q='fac')] == ['Facultad Nueva']


def test_proveedores_tipos_y_unidades_de_medida(cliente, catalogos):
    assert [p['razon_social'] for p in lista(cliente, 'proveedores', activo='true')] == ['Proveedor de prueba']
    assert [p['razon_social'] for p in lista(cliente, 'proveedores', q='sur')] == ['Aguas del Sur']
    assert [t['nombre'] for t in lista(cliente, 'tipos-servicio', ejecutor='PersonalPropio')] == ['Riego']
    assert [t['nombre'] for t in lista(cliente, 'tipos-servicio', es_riesgo='true')] == ['Poda de altura']
    assert [t['nombre'] for t in lista(cliente, 'tipos-servicio', activo='true', q='corte')] == ['Corte']
    assert [u['simbolo'] for u in lista(cliente, 'unidades-medida', q='m2')] == ['m2']
    assert [u['nombre'] for u in lista(cliente, 'unidades-medida', q='metros')] == ['Metros cuadrados']


@pytest.mark.parametrize('ruta, parametros, campo', [
    ('contratos', {'vigente': 'quizá'}, 'vigente'), ('contratos', {'proveedor_id': 'x'}, 'proveedor_id'),
    ('areas-verdes', {'activo': '2'}, 'activo'), ('ejemplares', {'area_verde_id': '0'}, 'area_verde_id'),
    ('tipos-servicio', {'ejecutor': 'Otro'}, 'ejecutor'), ('ejemplares', {'q': 'x' * 101}, 'q'),
    ('unidades-institucionales', {'provisional': 'no'}, 'provisional'),
])
def test_catalogo_filtro_invalido_responde_400(cliente, catalogos, ruta, parametros, campo):
    response = cliente.get(RAIZ + f'catalogos/{ruta}/', parametros)
    assert response.status_code == 400 and campo in response.data


def test_catalogo_booleano_acepta_variantes(cliente, catalogos):
    assert len(lista(cliente, 'areas-verdes', activo='1')) == len(lista(cliente, 'areas-verdes', activo='TRUE')) == 1
    assert len(lista(cliente, 'areas-verdes', activo='0')) == 1


def test_catalogo_paginado_con_filtros(cliente, referencias):
    area = referencias[1]
    for i in range(30):
        m.Ejemplar.objects.create(codigo=f'X-{i:03d}', area_verde=area)
    response = cliente.get(RAIZ + 'catalogos/ejemplares/', {'q': 'x-', 'area_verde_id': area.pk})
    assert response.data['count'] == 30 and len(response.data['results']) == 25
    assert response.data['results'][0]['codigo'] == 'X-000'
    siguiente = cliente.get(RAIZ + 'catalogos/ejemplares/', {'q': 'x-', 'area_verde_id': area.pk, 'page': 2})
    assert [e['codigo'] for e in siguiente.data['results']] == [f'X-{i:03d}' for i in range(25, 30)]


def test_catalogos_requieren_rol_del_modulo(catalogos):
    sin_rol = con_sesion(usuario_con('sinrol'))
    assert sin_rol.get(RAIZ + 'catalogos/contratos/').status_code == 403
    assert APIClient().get(RAIZ + 'catalogos/contratos/', {'vigente': 'true'}).status_code == 403
    for grupo in ROLES:
        assert con_sesion(usuario_con(f'c-{grupo}', grupo)).get(RAIZ + 'catalogos/contratos/').status_code == 200


def test_catalogos_no_exponen_importes_ni_datos_internos(cliente, catalogos):
    contrato = lista(cliente, 'contratos')[0]
    assert not {'importe', 'monto', 'ruc'} & set(contrato)
    assert 'ruc' not in lista(cliente, 'proveedores')[0]
