# Imagem da Saneia API (build multi-stage). Usada no docker-compose local e disponível para ACR/ACI.
# Build na raiz do repositório:  docker build -f dockerfiles/Dockerfile -t saneia-api .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 saneia
COPY --from=build /workspace/target/saneia-api.jar app.jar
USER saneia
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
