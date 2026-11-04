FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S inventory && adduser -S inventory -G inventory && mkdir data && chown inventory:inventory data
COPY --from=build /src/target/inventorys-1.0.0.jar app.jar
USER inventory
EXPOSE 8080
VOLUME ["/app/data"]
ENTRYPOINT ["java","-jar","app.jar"]
