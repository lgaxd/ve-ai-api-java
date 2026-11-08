# =============================================================================
# Vê AI · API Java · imagem Docker (usada pelo docker-compose.yml)
# Estágio 1 compila o .jar com Maven; estágio 2 só tem o JRE e o .jar.
# Os testes não rodam aqui: os de integração precisam de Docker (Testcontainers).
# Rode-os fora da imagem com ./mvnw verify.
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependências primeiro: a camada fica em cache enquanto o pom.xml não muda
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Usuário sem privilégios
RUN groupadd --system veai && useradd --system --gid veai veai
COPY --from=build /build/target/ve-ai-api-java-*.jar app.jar
USER veai

EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=America/Sao_Paulo"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
