#!/usr/bin/env bash
# Captures the decoded payload of a real client-credentials token into the fixture the tests
# compare against. Run it against the dev tenant whenever the app registration changes.
#
#   ENTRA_TENANT_ID=... ENTRA_API_CLIENT_ID=... ENTRA_CLIENT_ID=... ENTRA_CLIENT_SECRET=... \
#     scripts/capture-entra-token.sh
set -euo pipefail

: "${ENTRA_TENANT_ID:?}" "${ENTRA_API_CLIENT_ID:?}" "${ENTRA_CLIENT_ID:?}" "${ENTRA_CLIENT_SECRET:?}"

token=$(curl -sf -X POST \
  "https://login.microsoftonline.com/${ENTRA_TENANT_ID}/oauth2/v2.0/token" \
  -d grant_type=client_credentials \
  -d "client_id=${ENTRA_CLIENT_ID}" \
  -d "client_secret=${ENTRA_CLIENT_SECRET}" \
  -d "scope=${ENTRA_API_CLIENT_ID}/.default" | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')

payload=${token#*.}; payload=${payload%%.*}
printf '%s' "$payload" | tr '_-' '/+' | base64 -d 2>/dev/null | python3 -m json.tool \
  > src/test/resources/entra/decoded-token.json

echo "captured -> src/test/resources/entra/decoded-token.json"
grep -E '"(aud|iss|ver|roles)"' src/test/resources/entra/decoded-token.json
