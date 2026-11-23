# Forwarded to the backend with every /api request.
proxy_http_version 1.1;
proxy_set_header Connection "";
proxy_set_header Host $host;
# Replace whatever X-Forwarded-For a client sent with the guest's address
# as nginx determined it ($remote_addr: the connecting address, or the one
# a trusted proxy reported, see set_real_ip_from), so nobody can pick the
# address the backend's login limit counts.
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto $forwarded_proto;
proxy_set_header X-Forwarded-Host $host;
proxy_read_timeout 30s;
