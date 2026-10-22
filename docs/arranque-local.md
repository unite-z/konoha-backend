# Arranque en local (macOS y Windows)

Los pasos 1 a 3 se hacen una sola vez. Para el día a día, ve a [Uso diario](#uso-diario).

| Herramienta | Versión |
|---|---|
| JDK | **21** exactamente (Java 8 u otra versión no sirve) |
| PostgreSQL | 14 o posterior, o Docker |

No hace falta instalar Maven: `./mvnw` (macOS) y `mvnw.cmd` (Windows) descargan Maven 3.9.11 la primera vez y verifican su SHA-512.

## macOS

### 1. Instalar herramientas

```sh
brew install openjdk@21 postgresql@16
sudo ln -sfn "$(brew --prefix openjdk@21)/libexec/openjdk.jdk" /Library/Java/JavaVirtualMachines/openjdk-21.jdk
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zprofile
source ~/.zprofile
/usr/libexec/java_home -V     # debe listar una línea con 21
```

### 2. Crear la base de datos

Con Docker (recomendado, ya trae PostGIS):

```sh
docker run -d --name konoha-db -p 5432:5432 \
  -e POSTGRES_USER=areas_verdes -e POSTGRES_PASSWORD=tu_clave \
  -e POSTGRES_DB=areas_verdes_mod05 postgis/postgis:16-3.4
```

O con PostgreSQL de Homebrew:

```sh
brew services start postgresql@16
createuser -P areas_verdes            # pide una contraseña: anótala
createdb -O areas_verdes areas_verdes_mod05
```

En Mac con Apple Silicon, `postgis/postgis` solo existe para x86 y corre emulada (lenta). La alternativa multiarquitectura es `imresamu/postgis:16-3.4`, con los mismos parámetros. CI y los servidores usan `postgis/postgis`.

No hay que crear tablas: Flyway las crea en el primer arranque.

### 3. Configurar y arrancar

```sh
cp .env.example .env
open -e .env       # completa MOD05_DB_PASSWORD y MOD05_DEMO_PASSWORD (12 caracteres o más), sin comillas
./mvnw -version    # debe decir "Java version: 21..."
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--mod05.seed=true
```

El backend está listo cuando `http://localhost:8000/api/docs` abre Swagger.

## Windows 10/11 (PowerShell)

### 1. Instalar herramientas

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install PostgreSQL.PostgreSQL.16
$jdk = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-21*")[0].FullName
setx JAVA_HOME "$jdk"
```

Cierra y vuelve a abrir PowerShell. `java -version` debe decir 21.

### 2. Crear la base de datos

Con Docker Desktop, el mismo `docker run` de macOS (en una sola línea). O con PostgreSQL instalado:

```powershell
$pg = "C:\Program Files\PostgreSQL\16\bin"
& "$pg\psql.exe" -U postgres -c "CREATE ROLE areas_verdes LOGIN PASSWORD 'tu_clave';"
& "$pg\createdb.exe" -U postgres -O areas_verdes areas_verdes_mod05
```

### 3. Configurar y arrancar

```powershell
copy .env.example .env
notepad .env
.\mvnw.cmd -version
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=--mod05.seed=true"
```

En PowerShell, los argumentos `-D...` van entre comillas.

## Uso diario

```sh
docker start konoha-db        # o brew services start postgresql@16
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

Usuarios de prueba de MOD-05: `mod05_jefe`, `mod05_ingeniero` y `mod05_practicante`, con la clave de `MOD05_DEMO_PASSWORD`.

## Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `The JAVA_HOME environment variable is not defined correctly` | `JAVA_HOME` apunta a una carpeta que no existe. Repite el paso 1 |
| `class file version 61.0 ... only recognizes ... 52.0` | Maven usa Java 8. `./mvnw -version` tiene que decir 21 |
| `Connection refused` o `password authentication failed` | PostgreSQL no está levantado o `MOD05_DB_PASSWORD` no coincide |
| `mvnw: bad interpreter` o `\r` en el error | El fichero se guardó con fin de línea de Windows. `git checkout -- mvnw` lo restaura (`.gitattributes` lo fuerza a LF) |
| Puerto 8000 ocupado | Cierra la otra instancia o cambia `PORT` en `.env` (y el proxy de Vite en el frontend) |
