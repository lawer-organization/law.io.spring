#!/bin/bash
# Lancement du job extract pour extraire les articles via OCR
# Usage: ./scripts/extract.sh [BASE_URL]
# BASE_URL par défaut: http://localhost:8080

BASE_URL="${1:-${BASE_URL:-http://localhost:8080}}"

echo "🧪 Pré‑validation (mode filesystem)"
echo "➡️  Documents DOWNLOADED (prêts pour extraction):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COUNT(*) AS ready_for_extract FROM fetch_results WHERE status='DOWNLOADED';" 2>/dev/null | grep -v Warning || true

echo "➡️  Documents déjà extraits (EXTRACTED):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COUNT(*) AS extracted_db FROM fetch_results WHERE status='EXTRACTED';" 2>/dev/null | grep -v Warning || true

FS_PDFS=$(find data/pdfs/loi -type f -name '*.pdf' 2>/dev/null | wc -l | tr -d ' ')
echo "➡️  Fichiers PDF sur disque: $FS_PDFS"

FS_OCR=$(find data/ocr/loi -type f -name '*.txt' 2>/dev/null | wc -l | tr -d ' ')
echo "➡️  Fichiers OCR (.txt) sur disque: $FS_OCR"

echo "➡️  Répartition par année (DOWNLOADED):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT year, COUNT(*) AS total FROM fetch_results WHERE status='DOWNLOADED' GROUP BY year ORDER BY year DESC LIMIT 10;" 2>/dev/null | grep -v Warning || true

echo "🔁 Lancement du job OCR sur $BASE_URL ..."
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/batch/ocr")
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

POLL_INTERVAL=5
MAX_WAIT=3600  # 1 heure pour l'extraction OCR (peut être long)
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

echo "📊 Résumé post‑job (filesystem):" 
echo "📊 Documents EXTRACTED (statut en base):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COUNT(*) AS extracted_db FROM fetch_results WHERE status='EXTRACTED';" 2>/dev/null | grep -v Warning || true

FS_OCR_AFTER=$(find data/ocr/loi -type f -name '*.txt' 2>/dev/null | wc -l | tr -d ' ')
echo "📊 Fichiers OCR sur disque: $FS_OCR_AFTER"

echo "📊 Répartition par année (EXTRACTED):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT year, COUNT(*) AS total FROM fetch_results WHERE status='EXTRACTED' GROUP BY year ORDER BY year DESC LIMIT 10;" 2>/dev/null | grep -v Warning || true

echo "📊 Documents restants (DOWNLOADED):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COUNT(*) AS remaining FROM fetch_results WHERE status='DOWNLOADED';" 2>/dev/null | grep -v Warning || true

TOTAL_SIZE=$(find data/ocr/loi -type f -name '*.txt' -exec stat -f %z {} + 2>/dev/null | awk '{s+=$1} END {printf "%.2f MB", s/1024/1024}')
echo "📊 Taille totale des fichiers OCR: $TOTAL_SIZE"

echo "📊 Articles extraits en base:"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COUNT(*) AS article_count FROM article_extractions;" 2>/dev/null | grep -v Warning || true
