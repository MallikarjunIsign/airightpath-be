FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

COPY target/rightpath.jar rightpath.jar

EXPOSE 8081 

ENTRYPOINT ["java","-jar","rightpath.jar"]  