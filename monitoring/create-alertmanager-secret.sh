#!/usr/bin/env bash
# Creates the Alertmanager config as a Kubernetes Secret, so the SMTP password
# and email address are never stored in Git.
# Usage: ./monitoring/create-alertmanager-secret.sh
set -euo pipefail

read -r -p "Gmail address (sends and receives alerts): " ALERT_EMAIL
read -r -s -p "Gmail App Password (16 characters, input hidden): " SMTP_PASSWORD
echo
SMTP_PASSWORD=${SMTP_PASSWORD// /}   # Google shows it with spaces; remove them

kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -

kubectl -n monitoring create secret generic alertmanager-finsecure \
  --dry-run=client -o yaml \
  --from-file=alertmanager.yaml=<(cat <<EOF
global:
  resolve_timeout: 5m
  smtp_smarthost: smtp.gmail.com:587
  smtp_from: "FinSecure Alerts <${ALERT_EMAIL}>"
  smtp_auth_username: "${ALERT_EMAIL}"
  smtp_auth_password: "${SMTP_PASSWORD}"
  smtp_require_tls: true

route:
  receiver: "null"
  group_by: [alertname]
  group_wait: 30s
  group_interval: 2m
  repeat_interval: 1h
  routes:
    # Only FinSecure application alerts are emailed
    - receiver: email
      matchers:
        - team = "finsecure"

receivers:
  - name: "null"
  - name: email
    email_configs:
      - to: "${ALERT_EMAIL}"
        send_resolved: true
EOF
) | kubectl apply -f -

echo "Secret monitoring/alertmanager-finsecure is ready."
