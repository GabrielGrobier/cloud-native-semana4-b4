FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /code
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /code/target/demo-api-1.0.0.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/salud || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
