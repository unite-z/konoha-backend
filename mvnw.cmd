@echo off
rem Maven Wrapper mínimo para Windows: descarga Maven la primera vez (ver .mvn\wrapper\maven-wrapper.properties)
rem y lo ejecuta. Solo requiere JDK 21. Uso: mvnw.cmd spring-boot:run
setlocal
set "BASEDIR=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$url=((Get-Content '%BASEDIR%.mvn\wrapper\maven-wrapper.properties') -match '^distributionUrl=')[0].Substring(16).Trim();" ^
  "$nombre=[IO.Path]::GetFileName($url) -replace '-bin\.zip$','';" ^
  "$base=if($env:MAVEN_USER_HOME){$env:MAVEN_USER_HOME}else{Join-Path $env:USERPROFILE '.m2'};" ^
  "$destino=Join-Path $base ('wrapper\dists\'+$nombre);" ^
  "if(-not (Test-Path (Join-Path $destino 'bin\mvn.cmd'))){" ^
  "  Write-Host ('mvnw: descargando '+$nombre+' (solo la primera vez)...');" ^
  "  $tmp=Join-Path ([IO.Path]::GetTempPath()) ([Guid]::NewGuid()); New-Item -ItemType Directory $tmp | Out-Null;" ^
  "  $zip=Join-Path $tmp 'maven.zip'; Invoke-WebRequest $url -OutFile $zip -UseBasicParsing;" ^
  "  $esperado=((Invoke-WebRequest ($url+'.sha512') -UseBasicParsing).Content.ToString().Trim() -split '\s+')[0];" ^
  "  if((Get-FileHash $zip -Algorithm SHA512).Hash -ne $esperado.ToUpper()){ throw 'mvnw: la suma SHA-512 no coincide' };" ^
  "  Expand-Archive $zip $tmp; New-Item -ItemType Directory -Force (Split-Path $destino) | Out-Null;" ^
  "  if(Test-Path $destino){Remove-Item -Recurse -Force $destino}; Move-Item (Join-Path $tmp $nombre) $destino; Remove-Item -Recurse -Force $tmp }" ^
  "Set-Content -Path (Join-Path $env:TEMP 'mvnw-ruta.txt') -Value (Join-Path $destino 'bin\mvn.cmd') -NoNewline"
if errorlevel 1 exit /b 1
set /p MVN_CMD=<"%TEMP%\mvnw-ruta.txt"
"%MVN_CMD%" %*
