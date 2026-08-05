FROM --platform=linux/arm64 amazoncorretto:21-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY build/libs/app.jar app.jar
RUN chown app:app /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
