FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon assemble

FROM eclipse-temurin:21-jre

WORKDIR /opt/clue
COPY --from=builder /workspace/build/libs /opt/clue/build/libs
COPY --from=builder /workspace/bin /opt/clue/bin
COPY --from=builder /workspace/config /opt/clue/config
RUN mkdir -p /opt/clue/plugins

ENV PATH="/opt/clue/bin:${PATH}"
