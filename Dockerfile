# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

# Copier uniquement le pom d'abord pour profiter du cache Docker sur les dépendances
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -e -B dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -e -B clean package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Utilisateur non-root pour l'exécution du conteneur
RUN addgroup -S qrmenu && adduser -S qrmenu -G qrmenu
USER qrmenu

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

# Healthcheck basé sur Actuator (voir application.yml)
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
