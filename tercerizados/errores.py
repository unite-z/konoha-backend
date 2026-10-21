from rest_framework.exceptions import APIException


class ErrorDominio(APIException):
    status_code = 400

    def __init__(self, mensaje):
        super().__init__({'codigo': self.__class__.__name__, 'detalle': mensaje})


class NoEncontrado(ErrorDominio):
    status_code = 404


class Conflicto(ErrorDominio):
    status_code = 409


class AlmacenamientoNoDisponible(ErrorDominio):
    status_code = 503
