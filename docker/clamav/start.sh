#!/bin/sh
# Replaces the image's /init, which needs root for chown. First boot downloads ~300 MB of signatures.
set -eu
if ! ls /var/lib/clamav/main.* >/dev/null 2>&1; then
  freshclam --foreground --stdout
fi
freshclam --daemon --foreground --stdout &
exec clamd --foreground
