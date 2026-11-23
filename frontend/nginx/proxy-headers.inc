# Forwarded to the backend with every /api request.
proxy_http_version 1.1;
proxy_set_header Connection "";
proxy_set_header Host $host;
# nginx is where guests arrive: replace whatever X-Forwarded-For a client
# sent with the address nginx sees, so nobody can pick the address the
# backend's login limit counts.
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host $host;
proxy_read_timeout 30s;
