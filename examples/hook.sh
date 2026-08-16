#!/bin/sh
set -eu

# Reference exec-hook implementation against a generic REST-ish tracker.
# Reads $TMS_URL and $TMS_TOKEN from the environment; adapt both the endpoints
# and the field mappings to your actual tracker. See docs/exec-hook.md for the
# request/response contract this script must honor.

payload=$(cat)
action=$(echo "$payload" | jq -r '.action')

case "$action" in
  search)
    curl -sS --fail -H "Authorization: Bearer $TMS_TOKEN" \
      "$TMS_URL/issues?label=$(echo "$payload" | jq -r '.repo')" |
      jq '{tickets: [.items[] | {id: .key, hash: .puzzlerHash}]}'
    ;;
  create)
    curl -sS --fail -X POST -H "Authorization: Bearer $TMS_TOKEN" \
      -H 'Content-Type: application/json' \
      -d "$(echo "$payload" | jq '{title: .subject, body: .description, puzzlerHash: .hash}')" \
      "$TMS_URL/issues" | jq '{id: .key}'
    ;;
  close)
    curl -sS --fail -X POST -H "Authorization: Bearer $TMS_TOKEN" \
      -H 'Content-Type: application/json' \
      -d "$(echo "$payload" | jq '{comment: .reason, state: "closed"}')" \
      "$TMS_URL/issues/$(echo "$payload" | jq -r '.id')" > /dev/null
    echo '{}'
    ;;
esac
