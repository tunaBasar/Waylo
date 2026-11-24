#!/usr/bin/env bash
# Static check of docker-compose.prod.yml: renders config only, starts nothing.
# 1) empty env must fail and list the missing vars, 2) strong dummy values (never written to disk) must pass.
set -u
cd "$(dirname "$0")/.."
F=(-f docker-compose.yml -f docker-compose.prod.yml)

if out=$(env -i PATH="$PATH" docker compose "${F[@]}" config -q 2>&1); then
  echo "FAIL: compose config succeeded with an empty env, required vars are not enforced"; exit 1
fi
echo "OK: empty env rejected. First reported error:"
echo "$out" | head -3 | cut -c1-200

gen() { openssl rand -base64 48 | tr -d '\n'; }
envs=(PATH="$PATH" WAYLO_PAYOUT_IBAN_KEY="$(openssl rand -base64 32)")
for v in POSTGRES_PASSWORD PGADMIN_PASSWORD JWT_SECRET VOUCHER_SECRET CONFIG_PASSWORD EUREKA_PASSWORD MAIL_PASSWORD; do
  envs+=("$v=$(gen)")
done
for s in $(grep -oE 'WAYLO_SERVICE_(PRIVATE_KEY|KEYS)_[A-Z_]+' docker-compose.prod.yml | sort -u); do
  envs+=("$s=$(gen)")
done
if ! out=$(env -i "${envs[@]}" docker compose "${F[@]}" config -q 2>&1); then
  echo "FAIL: compose config rejected strong values:"; echo "$out" | head -5 | cut -c1-200; exit 1
fi
echo "OK: strong dummy values accepted"
