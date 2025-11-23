#!/bin/bash
# Lancement du job fetch-previous et suivi de son statut
# Ce job parcourt les années précédentes (1960 à année-1) avec un cursor de continuité
# Usage: ./scripts/fetch-previous.sh [BASE_URL]
# BASE_URL par défaut: http://localhost:8080

BASE_URL="${1:-${BASE_URL:-http://localhost:8080}}"

CURRENT_YEAR=$(date +%Y)
echo "🧪 Pré‑validation données années précédentes (1960 à $((CURRENT_YEAR-1)))"
echo "➡️  Nombre total de documents trouvés (toutes années):" 
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COUNT(*) AS found_total FROM fetch_results;" 2>/dev/null | grep -v Warning || true

echo "➡️  Répartition par année (top 10):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT year, COUNT(*) AS total FROM fetch_results GROUP BY year ORDER BY year DESC LIMIT 10;" 2>/dev/null | grep -v Warning || true

echo "➡️  Cursor actuel (position de reprise):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT document_type, year, number, updated_at FROM fetch_cursor WHERE document_type='loi' ORDER BY updated_at DESC LIMIT 1;" 2>/dev/null | grep -v Warning || true

echo "➡️  Top 5 ranges NOT_FOUND (années précédentes):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT year, number_min, number_max, document_count FROM fetch_not_found_ranges WHERE year < $CURRENT_YEAR ORDER BY year DESC, number_min LIMIT 5;" 2>/dev/null | grep -v Warning || true

echo "➡️  Somme des NOT_FOUND (années précédentes):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COALESCE(SUM(document_count),0) AS sum_not_found_previous FROM fetch_not_found_ranges WHERE year < $CURRENT_YEAR;" 2>/dev/null | grep -v Warning || true

echo "🔁 Lancement du job fetch-previous sur $BASE_URL ..."
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/batch/fetch-previous")
HTTP_CODE=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')

if [ "$HTTP_CODE" != "200" ] && [ "$HTTP_CODE" != "202" ]; then
  echo "❌ Erreur HTTP $HTTP_CODE: $BODY"
  exit 1
fi

JOB_ID=$(echo "$BODY" | sed -E 's/.*"jobExecutionId":([0-9]+).*/\1/')
if [[ -z "$JOB_ID" ]]; then
  echo "❌ Impossible d'extraire jobExecutionId. Réponse brute: $BODY"
  exit 1
fi
echo "✅ Job démarré (HTTP $HTTP_CODE). ID=$JOB_ID"

POLL_INTERVAL=3
MAX_WAIT=600
ELAPSED=0

while true; do
  STATUS_JSON=$(curl -s "$BASE_URL/api/batch/status/$JOB_ID")
  STATUS=$(echo "$STATUS_JSON" | sed -E 's/.*"status":"([A-Z]+)".*/\1/')
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

# Afficher le curseur mis à jour et un aperçu des ranges NOT_FOUND
echo "📊 Résumé post‑job:" 
echo "📊 Documents FOUND totaux (toutes années):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COUNT(*) AS found_count_post FROM fetch_results;" 2>/dev/null | grep -v Warning || true

echo "📊 Répartition par année (top 10 après job):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT year, COUNT(*) AS total FROM fetch_results GROUP BY year ORDER BY year DESC LIMIT 10;" 2>/dev/null | grep -v Warning || true

echo "📊 Cursor après job:"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT document_type, year, number, updated_at FROM fetch_cursor WHERE document_type='loi' ORDER BY updated_at DESC LIMIT 1;" 2>/dev/null | grep -v Warning || true

echo "📊 Top 10 ranges NOT_FOUND (années précédentes après job):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT year, number_min, number_max, document_count FROM fetch_not_found_ranges WHERE year < $CURRENT_YEAR ORDER BY year DESC, number_min LIMIT 10;" 2>/dev/null | grep -v Warning || true

echo "📊 Somme NOT_FOUND (années précédentes après job):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COALESCE(SUM(document_count),0) AS sum_not_found_post FROM fetch_not_found_ranges WHERE year < $CURRENT_YEAR;" 2>/dev/null | grep -v Warning || true
