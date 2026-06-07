# ── Stage 1: Build ────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-11 AS builder

WORKDIR /build

# Cache dependencies dulu (layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source & build
COPY src src
RUN mvn -DskipTests clean package -q

# ── Stage 2: Runtime ──────────────────────────────────────────────
FROM tomcat:9-jdk11-temurin

RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=builder /build/target/service-poc.war \
     /usr/local/tomcat/webapps/service-poc.war

# Set Tomcat startup options untuk pass config.path ke Java
ENV CATALINA_OPTS="-Dconfig.path=/usr/local/tomcat/config/saml.properties"

EXPOSE 8080
CMD ["catalina.sh", "run"]