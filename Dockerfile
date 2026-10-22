# Imagen de la api. Se construye en CI y se publica en GHCR por SHA (konoha-api:<sha>).

# Build con la imagen oficial de Maven: el mvnw del repo necesita unzip, que la imagen de Temurin no trae.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp -q dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -q -DskipTests -Dspotless.skip=true package \
    && cp target/*.jar /build/app.jar

FROM eclipse-temurin:21-jre
ARG REVISION=desconocida
LABEL org.opencontainers.image.source="konoha-backend" \
      org.opencontainers.image.revision="${REVISION}"

RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar app.jar
COPY --chown=app:app mock-data ./mock-data

# Memoria: la JVM toma el 65 % del límite del contenedor (ADR 0003). El resto es para metaspace e hilos.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError" \
    TZ=America/Lima \
    PORT=8000 \
    MOCK_DATA_DIR=/app/mock-data \
    MOD05_MOCK_DATA=/app/mock-data \
    MOD05_ARCHIVOS_DIR=/app/private-media \
    APP_REVISION=${REVISION}

RUN mkdir -p /app/private-media && chown app:app /app/private-media
USER app
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
