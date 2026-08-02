#!/bin/sh
set -eu

CACHE_DIRECTORY="${TRUST_CACHE_DIRECTORY:-/app/cache/trusted-lists}"
RUNTIME_USER="${RUNTIME_UID:-1001}"
RUNTIME_GROUP="${RUNTIME_GID:-0}"

if [ "$(id -u)" -ne 0 ]; then
    echo "cache directory ready"
    echo "runtime UID: $(id -u)"
    if [ -d "$CACHE_DIRECTORY" ] && [ -w "$CACHE_DIRECTORY" ]; then
        echo "writable true"
    else
        echo "writable false"
        exit 1
    fi
    exec "$@"
fi

mkdir -p "$CACHE_DIRECTORY"
chown "$RUNTIME_USER:$RUNTIME_GROUP" "$CACHE_DIRECTORY"
chmod 0775 "$CACHE_DIRECTORY"

echo "cache directory ready"
echo "runtime UID: $RUNTIME_USER"
if su-exec "$RUNTIME_USER:$RUNTIME_GROUP" test -w "$CACHE_DIRECTORY"; then
    echo "writable true"
else
    echo "writable false"
    exit 1
fi

exec su-exec "$RUNTIME_USER:$RUNTIME_GROUP" "$@"
