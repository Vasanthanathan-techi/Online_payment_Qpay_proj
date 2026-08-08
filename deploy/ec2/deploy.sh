#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
test -f deploy/ec2/.env || { echo "Copy deploy/ec2/.env.example to deploy/ec2/.env and set secrets." >&2; exit 1; }

docker compose --env-file deploy/ec2/.env -f deploy/ec2/compose.yml config --quiet
docker compose --env-file deploy/ec2/.env -f deploy/ec2/compose.yml build --pull
docker compose --env-file deploy/ec2/.env -f deploy/ec2/compose.yml up -d --remove-orphans

port="$(awk -F= '$1 == "QPAY_HTTP_PORT" { print $2 }' deploy/ec2/.env | tail -1)"
port="${port:-80}"
for attempt in $(seq 1 60); do
  if curl --fail --silent "http://localhost:${port}/actuator/health" | grep -q '"status":"UP"'; then
    docker compose --env-file deploy/ec2/.env -f deploy/ec2/compose.yml ps
    echo "QPay deployment is healthy on port ${port}."
    exit 0
  fi
  sleep 5
done

docker compose --env-file deploy/ec2/.env -f deploy/ec2/compose.yml ps
docker compose --env-file deploy/ec2/.env -f deploy/ec2/compose.yml logs --tail 100 api-gateway
echo "QPay gateway did not become healthy within five minutes." >&2
exit 1
