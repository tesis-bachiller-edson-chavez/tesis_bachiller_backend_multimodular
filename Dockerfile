# Etapa 1: Construir la aplicación
# Usamos una imagen con el JDK 24 basado en Alpine
FROM eclipse-temurin:24-jdk-alpine as builder

WORKDIR /workspace

# Copiamos los archivos de Gradle para aprovechar la caché de Docker
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Damos permisos de ejecución al wrapper de Gradle
RUN chmod +x ./gradlew

# Descargamos las dependencias.
RUN ./gradlew dependencies --no-daemon

# Ahora copiamos el código fuente
COPY src src

# Construimos la aplicación, saltando los tests
RUN ./gradlew build --no-daemon -x test

# Etapa 2: Crear la imagen final y ligera
# Usamos una imagen solo con el JRE 24 basado en Alpine, que es mucho más pequeña
FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

# Instalar wget, descargar el agente de Datadog y luego remover wget
RUN apk add --no-cache wget && \
    wget -O dd-java-agent.jar https://repo1.maven.org/maven2/com/datadoghq/dd-java-agent/0.110.0/dd-java-agent-0.110.0.jar && \
    apk del wget

# Copiamos solo el JAR construido desde la etapa anterior
COPY --from=builder /workspace/build/libs/*.jar app.jar

# Exponemos el puerto 8080
EXPOSE 8080

# Variables de entorno para la configuración de Datadog.
# Se pueden (y deben) sobreescribir al ejecutar el contenedor.
ENV DD_SERVICE=tesis-backend
ENV DD_LOGS_INJECTION=true
ENV DD_PROFILING_ENABLED=true
# ENV DD_ENV=production # Descomentar y ajustar para cada entorno
# ENV DD_AGENT_HOST=... # Configurar según la infraestructura de AWS

# El comando para ejecutar la aplicación con el agente de Datadog
ENTRYPOINT ["java", "-javaagent:dd-java-agent.jar", "-jar", "app.jar"]
