FROM clojure:tools-deps-bookworm-slim AS builder
WORKDIR /opt
COPY . .
RUN clojure -Sdeps '{:mvn/local-repo "./.m2/repository"}' -T:build uber

FROM eclipse-temurin:21-alpine AS runtime
COPY --from=builder /opt/target/app.jar /app.jar
COPY --from=builder /opt/public /public
RUN addgroup -S app && adduser -S -G app app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "app.jar", "clojure.main", "-m", "app.core"]
