FROM openjdk:23-jdk-slim
ARG JAR_FILE=target/powblockchain-0.0.1.jar
ENV SPRING_PROFILES_ACTIVE=dev
COPY ${JAR_FILE} app.jar
EXPOSE 8080
EXPOSE 50051
ENTRYPOINT ["java","-jar","/app.jar"]