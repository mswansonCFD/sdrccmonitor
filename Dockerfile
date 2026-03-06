# syntax=docker/dockerfile:1.7

FROM debian:bookworm-slim AS builder

ARG JDK_URL=https://download.bell-sw.com/java/25.0.1+11/bellsoft-jdk25.0.1+11-linux-amd64-full.tar.gz
ARG JDK_DIR=/opt/bellsoft-jdk25.0.1+11-full

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates tar bash \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /opt \
    && curl -fsSL "$JDK_URL" -o /tmp/jdk.tar.gz \
    && tar -xzf /tmp/jdk.tar.gz -C /opt \
    && rm /tmp/jdk.tar.gz

ENV JAVA_HOME=${JDK_DIR}
ENV PATH=${JAVA_HOME}/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

WORKDIR /workspace
COPY . .

RUN chmod +x gradlew \
    && ./gradlew --no-daemon clean compileJava -x test

FROM debian:bookworm-slim AS artifact
WORKDIR /workspace
COPY --from=builder /workspace/build /workspace/build

CMD ["bash", "-lc", "ls -lah /workspace/build"]
