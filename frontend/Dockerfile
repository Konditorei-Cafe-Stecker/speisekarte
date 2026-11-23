# Café Stecker website: the React app built by Vite, served by nginx.
# nginx also forwards /api to the backend and caches the menu.
# Build:  docker build -t stecker-frontend frontend
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# The version from git (deploy/local-up.sh passes it), served as
# /version.json so you can see which website version is live.
ARG APP_VERSION=dev
RUN npm run build \
    && printf '{"version":"%s"}\n' "$APP_VERSION" > dist/version.json

FROM nginxinc/nginx-unprivileged:1.27-alpine
ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="stecker-frontend" \
      org.opencontainers.image.version="$APP_VERSION"
# Filled into the template at start. Defaults for plain Docker; Kubernetes
# sets the headless backend service and the cluster DNS. TRUSTED_PROXY_CIDR:
# the range of an Ingress/load balancer in front of nginx (none by default).
ENV BACKEND_HOST=backend \
    DNS_RESOLVER=127.0.0.11 \
    TRUSTED_PROXY_CIDR=127.0.0.1/32
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/proxy-headers.inc /etc/nginx/proxy-headers.inc
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080
