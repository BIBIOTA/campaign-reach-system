#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
COLLECTION="docs/postman/campaign-reach.postman_collection.json"
ENVIRONMENT="docs/postman/local-email-e2e.postman_environment.json"
FOLDER="local/manual EMAIL e2e acceptance"

BASE_URL="${BASE_URL:-http://localhost:8080}"
MAILPIT_BASE_URL="${MAILPIT_BASE_URL:-http://localhost:${MAILPIT_UI_PORT:-8025}}"
OPERATOR_USERNAME="${OPERATOR_USERNAME:-operator}"
OPERATOR_PASSWORD="${OPERATOR_PASSWORD:-operator-pass}"
POSTGRES_DB="${POSTGRES_DB:-campaign_reach}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
E2E_AUDIENCE_LIST_ID="${E2E_AUDIENCE_LIST_ID:-11111111-1111-4111-8111-111111111111}"
E2E_USER_ID="${E2E_USER_ID:-22222222-2222-4222-8222-222222222222}"
E2E_MAX_POLL_ATTEMPTS="${E2E_MAX_POLL_ATTEMPTS:-60}"
E2E_POLL_INTERVAL_MS="${E2E_POLL_INTERVAL_MS:-2000}"

if command -v newman >/dev/null 2>&1; then
  NEWMAN=(newman)
elif command -v npx >/dev/null 2>&1; then
  NEWMAN=(npx -y newman)
else
  echo "newman or npx is required. Install Newman with: npm install -g newman" >&2
  exit 1
fi

if ! docker compose ps postgres --status running >/dev/null 2>&1; then
  echo "docker compose postgres is not running. Start the local stack first: docker compose up -d" >&2
  exit 1
fi

echo "Seeding static audience list ${E2E_AUDIENCE_LIST_ID} with member ${E2E_USER_ID}..."
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
  -v list_id="${E2E_AUDIENCE_LIST_ID}" -v user_id="${E2E_USER_ID}" <<'SQL'
INSERT INTO audience_list (id, name, created_at)
VALUES (:'list_id'::uuid, 'Local EMAIL e2e acceptance', now())
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name;

INSERT INTO audience_list_member (list_id, user_id, added_at)
VALUES (:'list_id'::uuid, :'user_id'::uuid, now())
ON CONFLICT (list_id, user_id) DO UPDATE
SET added_at = EXCLUDED.added_at;
SQL

echo "Clearing Mailpit mailbox at ${MAILPIT_BASE_URL} before the run..."
curl -fsS -X DELETE "${MAILPIT_BASE_URL}/api/v1/messages" \
  -H "Content-Type: application/json" \
  -d '{}' >/dev/null

echo "Running Newman folder '${FOLDER}' against ${BASE_URL}..."
"${NEWMAN[@]}" run "${COLLECTION}" \
  --environment "${ENVIRONMENT}" \
  --folder "${FOLDER}" \
  --env-var "baseUrl=${BASE_URL}" \
  --env-var "basicAuthUsername=${OPERATOR_USERNAME}" \
  --env-var "basicAuthPassword=${OPERATOR_PASSWORD}" \
  --env-var "mailpitBaseUrl=${MAILPIT_BASE_URL}" \
  --env-var "e2eAudienceListId=${E2E_AUDIENCE_LIST_ID}" \
  --env-var "e2eMaxPollAttempts=${E2E_MAX_POLL_ATTEMPTS}" \
  --env-var "e2ePollIntervalMs=${E2E_POLL_INTERVAL_MS}"

echo "EMAIL e2e acceptance completed. Mailpit messages are available at ${MAILPIT_BASE_URL}."
