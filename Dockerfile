# Multi-stage build pour optimiser la taille de l'image
FROM maven:3.9.5-eclipse-temurin-17 AS builder

WORKDIR /app

# Copier les fichiers de configuration Maven
COPY pom.xml .
COPY src ./src

# Build de l'application (sans tests pour accélérer)
RUN mvn clean package -DskipTests

# Image finale légère - Tesseract embarqué via JavaCPP (pas d'installation système nécessaire)
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Créer utilisateur non-root pour sécurité
RUN groupadd -g 1001 appuser && \
    useradd -u 1001 -g appuser -m -s /bin/bash appuser

# Copier le JAR depuis l'étape de build
COPY --from=builder /app/target/law-spring-batch-*.jar app.jar

# Copier les ressources nécessaires (tesseract data, etc.)
COPY src/main/resources/tessdata /app/tessdata
COPY src/main/resources/*.csv /app/resources/
COPY src/main/resources/*.txt /app/resources/
COPY src/main/resources/patterns.properties /app/resources/

# Créer les répertoires de données
RUN mkdir -p /app/data/pdfs /app/data/ocr /app/data/articles && \
    chown -R appuser:appuser /app

# Utiliser l'utilisateur non-root
USER appuser

# Exposer le port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Variables d'environnement par défaut
ENV JAVA_OPTS="-Xmx512m -Xms256m" \
    SPRING_PROFILES_ACTIVE="production"

# Lancer l'application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
