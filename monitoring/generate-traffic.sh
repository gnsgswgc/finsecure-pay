#!/usr/bin/env bash
# Sends a mix of valid and invalid transactions so the dashboard has live data.
# Usage: ./monitoring/generate-traffic.sh [minutes] [reject-percent]
#   ./monitoring/generate-traffic.sh 5 20    # 5 minutes, ~20% rejected (normal)
#   ./monitoring/generate-traffic.sh 4 80    # ~80% rejected -> fires FinsecureHighRejectionRate
set -euo pipefail

MINUTES=${1:-5}
REJECT_PCT=${2:-20}
HOST=$(kubectl -n finsecure get svc finsecure-pay -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
URL="http://${HOST}"
END=$(( $(date +%s) + MINUTES * 60 ))
n=0

echo "Sending traffic to ${URL} for ${MINUTES} min (~${REJECT_PCT}% rejected). Ctrl+C to stop."
while [ "$(date +%s)" -lt "$END" ]; do
  n=$((n + 1))
  if [ $((RANDOM % 100)) -lt "$REJECT_PCT" ]; then
    body="{\"transactionId\":\"LOAD-$$-$n\",\"amount\":-5,\"currency\":\"JPY\",\"cardNumber\":\"1234\",\"merchantId\":\"M1\"}"
  else
    body="{\"transactionId\":\"LOAD-$$-$n\",\"amount\":$((RANDOM % 900 + 10)),\"currency\":\"SGD\",\"cardNumber\":\"4111111111111111\",\"merchantId\":\"M1\"}"
  fi
  curl -s -o /dev/null -X POST "${URL}/validate" -H "Content-Type: application/json" -d "$body" &
  curl -s -o /dev/null "${URL}/health" &
  [ $((n % 50)) -eq 0 ] && echo "  sent ${n} transactions"
  sleep 0.2
done
wait
echo "Done: ${n} transactions sent."
