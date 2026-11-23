#!/bin/sh
# Writes the trusted proxy ranges from TRUSTED_PROXY_CIDR (one or more,
# separated by spaces or commas) into the two files default.conf includes.
# Runs at container start, before the template is filled in.
set -eu
ranges=$(printf '%s' "${TRUSTED_PROXY_CIDR:-127.0.0.1/32}" | tr ',' ' ')
realip=/etc/nginx/conf.d/trusted-proxies-realip.inc
geo=/etc/nginx/conf.d/trusted-proxies-geo.inc
: > "$realip"
: > "$geo"
for range in $ranges; do
  case "$range" in
    *[!0-9A-Fa-f.:/]*)
      echo "TRUSTED_PROXY_CIDR: not an address range: $range" >&2
      exit 1 ;;
  esac
  echo "set_real_ip_from $range;" >> "$realip"
  echo "$range 1;" >> "$geo"
done
