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

# Copiamos solo el JAR construido desde la etapa anterior
COPY --from=builder /workspace/build/libs/*.jar app.jar

# Exponemos el puerto 8080
EXPOSE 8080

# El comando para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]
