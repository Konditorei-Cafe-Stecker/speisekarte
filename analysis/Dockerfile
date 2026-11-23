# Tools image for Kubernetes jobs: the forecast publisher and the menu import.
# Build from the repository root (it needs the menu SQL from the backend):
#   docker build -f analysis/Dockerfile -t stecker-tools .
FROM python:3.11-slim
ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="stecker-tools" \
      org.opencontainers.image.version="$APP_VERSION"
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY analysis/requirements.txt analysis/requirements.txt
RUN pip install --no-cache-dir -r analysis/requirements.txt
COPY analysis/*.py analysis/
COPY analysis/sql analysis/sql
COPY backend/src/main/resources/db/stecker_menu_import.sql db/stecker_menu_import.sql
RUN useradd --system --uid 10001 stecker && mkdir -p analysis/output && chown -R 10001 /app
USER 10001
ENV MPLCONFIGDIR=/tmp/matplotlib
