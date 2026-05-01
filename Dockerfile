# ──────────────────────────────────────────────────────────────────────────────
# Stage 1 — build uber-jar (Maven + Scala 2.11 + amazoncorretto-17)
# ──────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.11-amazoncorretto-17 AS builder

ARG MAVEN_REVISION=0.3.0
ARG WEBROBOT_SDK_MAVEN_VERSION=0.3.10

WORKDIR /build
COPY pom.xml .
COPY src/ src/
COPY resources/ resources/

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B clean package -DskipTests \
      -Drevision=${MAVEN_REVISION} \
      -Dwebrobot.sdk.depversion=${WEBROBOT_SDK_MAVEN_VERSION} && \
    cp target/webrobot-cli-*-uber.jar target/webrobot-cli-uber.jar

# ──────────────────────────────────────────────────────────────────────────────
# Stage 2 — runtime: Python 3.13 + Temurin JRE 21 + browser-use + camoufox
# ──────────────────────────────────────────────────────────────────────────────
FROM python:3.13-bookworm

ARG TEMURIN_VERSION=21

ENV DEBIAN_FRONTEND=noninteractive \
    PYTHONUNBUFFERED=1 \
    WEBROBOT_PYTHON=/usr/local/bin/python3.13 \
    JAVA_HOME=/opt/java \
    PATH="/opt/java/bin:$PATH" \
    XDG_CACHE_HOME=/var/lib/camoufox-cache

# Dipendenze di sistema: camoufox/Firefox browser + xvfb
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates \
    libgtk-3-0 libx11-xcb1 libasound2 \
    libnss3 libnspr4 libatk1.0-0 libatk-bridge2.0-0 libcups2 libdrm2 \
    libdbus-1-3 libxcb1 libxkbcommon0 libx11-6 libxcomposite1 \
    libxdamage1 libxext6 libxfixes3 libxrandr2 libgbm1 \
    libpango-1.0-0 libcairo2 libatspi2.0-0 \
    fonts-noto-core xvfb \
  && rm -rf /var/lib/apt/lists/*

# Temurin JRE (stesso approccio dell'install script: estrae top-level dir)
RUN set -eux; \
    TMPDIR=$(mktemp -d); \
    curl -fsSL \
      "https://api.adoptium.net/v3/binary/latest/${TEMURIN_VERSION}/ga/linux/x64/jre/hotspot/normal/eclipse" \
      -o "$TMPDIR/jre.tar.gz"; \
    tar -xzf "$TMPDIR/jre.tar.gz" -C "$TMPDIR"; \
    JRE_DIR=$(find "$TMPDIR" -maxdepth 1 -mindepth 1 -type d | head -1); \
    mv "$JRE_DIR" /opt/java; \
    rm -rf "$TMPDIR"; \
    java -version

# Pacchetti Python: browser-use, camoufox, LLM providers
RUN pip install --no-cache-dir \
    browser-use \
    "camoufox[geoip]" \
    langchain-anthropic \
    langchain-openai \
    langchain-groq

# Binario camoufox/Firefox baked in (~100 MB)
RUN mkdir -p /var/lib/camoufox-cache && chmod 1777 /var/lib/camoufox-cache && \
    python -m camoufox fetch

# CLI jar dal builder
COPY --from=builder /build/target/webrobot-cli-uber.jar /opt/webrobot-cli/webrobot-cli.jar

# Wrapper webrobot
RUN printf '#!/usr/bin/env bash\nexport WEBROBOT_PYTHON=/usr/local/bin/python3.13\nexec /opt/java/bin/java -jar /opt/webrobot-cli/webrobot-cli.jar "$@" 2> >(grep -v "^SLF4J:" >&2)\n' \
    > /usr/local/bin/webrobot && \
    chmod 755 /usr/local/bin/webrobot

WORKDIR /workspace

ENTRYPOINT ["webrobot"]
CMD ["--help"]
