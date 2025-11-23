#!/bin/bash
# Lancement du job fetch-current et suivi de son statut
# Usage: ./scripts/fetch-current.sh [BASE_URL]
# BASE_URL par défaut: http://localhost:8080

BASE_URL="${1:-${BASE_URL:-http://localhost:8080}}"

CURRENT_YEAR=$(date +%Y)
echo "🧪 Pré‑validation des données pour l'année $CURRENT_YEAR"
echo "➡️  Nombre de documents trouvés (FOUND) pour l'année courante:" 
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COUNT(*) AS found_count FROM fetch_results WHERE year=$CURRENT_YEAR;" 2>/dev/null | grep -v Warning || true

echo "➡️  Répartition par type (tous documents trouvés):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT document_type, COUNT(*) AS total FROM fetch_results GROUP BY document_type;" 2>/dev/null | grep -v Warning || true

echo "➡️  Extrait des 20 premières URLs trouvées pour l'année $CURRENT_YEAR (si présentes):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT document_id, url FROM fetch_results WHERE year=$CURRENT_YEAR ORDER BY document_id LIMIT 20;" 2>/dev/null | grep -v Warning || true

echo "➡️  Top 5 ranges NOT_FOUND existantes (avant job):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT number_min, number_max, document_count FROM fetch_not_found_ranges WHERE year=$CURRENT_YEAR ORDER BY number_min LIMIT 5;" 2>/dev/null | grep -v Warning || true

echo "➡️  Somme des NOT_FOUND (avant job):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COALESCE(SUM(document_count),0) AS sum_not_found FROM fetch_not_found_ranges WHERE year=$CURRENT_YEAR;" 2>/dev/null | grep -v Warning || true

echo "➡️  Max théorique configuré (law.maxNumberPerYear) vs couvert (FOUND + NOT_FOUND approx):" 
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT 'METRICS' label, (SELECT COUNT(*) FROM fetch_results WHERE year=$CURRENT_YEAR) AS found, (SELECT COALESCE(SUM(document_count),0) FROM fetch_not_found_ranges WHERE year=$CURRENT_YEAR) AS not_found_sum;" 2>/dev/null | grep -v Warning || true

echo "🔁 Lancement du job fetch-current sur $BASE_URL ..."
RAW=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "$BASE_URL/api/batch/fetch-current")
HTTP_CODE=$(echo "$RAW" | tail -n1 | sed -E 's/HTTP_CODE://')
RESP=$(echo "$RAW" | sed '$d')
if [ "$HTTP_CODE" != "202" ]; then
  echo "❌ Échec démarrage job (HTTP $HTTP_CODE). Payload: $RESP"
  exit 1
fi
JOB_ID=$(echo "$RESP" | grep -o '"jobExecutionId":[0-9]*' | cut -d':' -f2)
if [[ -z "$JOB_ID" ]]; then
  echo "❌ jobExecutionId introuvable dans la réponse: $RESP"
  exit 1
fi
echo "✅ Job démarré (HTTP $HTTP_CODE). ID=$JOB_ID"

POLL_INTERVAL=3
MAX_WAIT=600
ELAPSED=0

while true; do
  STATUS_RAW=$(curl -s -w "\nHTTP_CODE:%{http_code}" "$BASE_URL/api/batch/status/$JOB_ID")
  STATUS_HTTP=$(echo "$STATUS_RAW" | tail -n1 | sed -E 's/HTTP_CODE://')
  STATUS_JSON=$(echo "$STATUS_RAW" | sed '$d')
  if [ "$STATUS_HTTP" != "200" ]; then
    echo "⏱ Statut HTTP=$STATUS_HTTP (stop polling)"; break; fi
  STATUS=$(echo "$STATUS_JSON" | grep -o '"status":"[A-Z]*"' | cut -d'"' -f4)
  echo "⏱ Statut actuel: $STATUS"
  if [[ "$STATUS" == "COMPLETED" || "$STATUS" == "FAILED" ]]; then
    echo "🏁 Terminé: $STATUS_JSON"
    break
  fi
  sleep $POLL_INTERVAL
  ELAPSED=$((ELAPSED+POLL_INTERVAL))
  if [ $ELAPSED -ge $MAX_WAIT ]; then
    echo "⌛ Timeout après $MAX_WAIT secondes"
    break
  fi
done

# Afficher le curseur et un aperçu des ranges NOT_FOUND
echo "📊 Résumé post‑job FOUND année $CURRENT_YEAR:" 
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COUNT(*) AS found_count_post FROM fetch_results WHERE year=$CURRENT_YEAR;" 2>/dev/null | grep -v Warning || true

echo "📊 Extrait des 20 premières URLs trouvées (après job, pour contrôle):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT document_id, url FROM fetch_results WHERE year=$CURRENT_YEAR ORDER BY document_id LIMIT 20;" 2>/dev/null | grep -v Warning || true

echo "📊 Top 10 ranges NOT_FOUND (après job):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT year, number_min, number_max, document_count FROM fetch_not_found_ranges WHERE year=$CURRENT_YEAR ORDER BY number_min DESC LIMIT 10;" 2>/dev/null | grep -v Warning || true

echo "📊 Somme NOT_FOUND (après job):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COALESCE(SUM(document_count),0) AS sum_not_found_post FROM fetch_not_found_ranges WHERE year=$CURRENT_YEAR;" 2>/dev/null | grep -v Warning || true
