# syntax=docker/dockerfile:1

# ---------- 1) Build: compila o .jar com Maven + JDK 21 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Primeiro só o pom.xml: as dependências ficam numa camada em cache e só voltam
# a ser descarregadas quando o pom.xml muda (builds seguintes muito mais rápidos).
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Os testes correm no GitHub Actions antes de gerar a imagem.
RUN mvn -q -B clean package -DskipTests

# ---------- 2) Runtime: só o JRE (sem Maven nem código-fonte) ----------
FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.title="chuanga-studio-backend" \
      org.opencontainers.image.description="API do Chuanga Studio (Spring Boot)" \
      org.opencontainers.image.source="https://github.com/igortobias/CHUANGA-STUDIO-BACK"
WORKDIR /app
# Sem utilizador root dentro do contentor.
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
USER app
# Produção por omissão: exige os segredos por variável de ambiente e recusa os de exemplo.
# A JVM usa até 75% da memória dada ao contentor; datas/horas no fuso do estúdio.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Africa/Maputo"
EXPOSE 8080
# O Docker marca o contentor como "healthy" só quando a API responde.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/api/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
