#!/bin/bash
# Lancement du job d'extraction d'articles depuis les fichiers OCR existants
# Usage: ./scripts/extract-articles.sh [BASE_URL]
# BASE_URL par défaut: http://localhost:8080

BASE_URL="${1:-${BASE_URL:-http://localhost:8080}}"

echo "🧪 Pré‑validation (extraction d'articles)"
echo "➡️  Fichiers OCR disponibles:"
FS_OCR=$(find data/ocr/loi -type f -name '*.txt' 2>/dev/null | wc -l | tr -d ' ')
echo "     $FS_OCR fichiers .txt"

if [ "$FS_OCR" -eq 0 ]; then
    echo "❌ Aucun fichier OCR trouvé. Exécutez d'abord ocr.sh"
    exit 1
fi

echo "➡️  Articles actuellement extraits:"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COUNT(*) AS article_count FROM article_extractions;" 2>/dev/null | grep -v Warning || true

echo "➡️  Documents avec status EXTRACTED:"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COUNT(*) AS extracted_db FROM fetch_results WHERE status='EXTRACTED';" 2>/dev/null | grep -v Warning || true

echo "➡️  Répartition par année (articles existants):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT year, COUNT(*) AS total FROM fetch_results WHERE status='EXTRACTED' GROUP BY year ORDER BY year DESC LIMIT 10;" 2>/dev/null | grep -v Warning || true

echo "🔁 Lancement du job d'extraction d'articles sur $BASE_URL ..."
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/batch/extract")
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
MAX_WAIT=600  # 10 minutes pour l'extraction d'articles (plus rapide que l'OCR)
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

echo "📊 Résumé post‑job (extraction d'articles):" 
echo "📊 Articles extraits en base:"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COUNT(*) AS article_count FROM article_extractions;" 2>/dev/null | grep -v Warning || true

echo "📊 Documents EXTRACTED (statut en base):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT COUNT(*) AS extracted_db FROM fetch_results WHERE status='EXTRACTED';" 2>/dev/null | grep -v Warning || true

echo "📊 Répartition par année (articles extraits):"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT year, COUNT(*) AS total FROM fetch_results WHERE status='EXTRACTED' GROUP BY year ORDER BY year DESC LIMIT 10;" 2>/dev/null | grep -v Warning || true

echo "📊 Top 5 des derniers articles extraits:"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT CONCAT(type, '-', year, '-', number) as document, article_number, SUBSTRING(content, 1, 60) as preview FROM article_extractions ORDER BY id DESC LIMIT 5;" 2>/dev/null | grep -v Warning || true

echo "📊 Statistiques par document:"
docker exec law-mysql mysql -uroot -proot law_batch -e "SELECT CONCAT(type, '-', year, '-', number) as document, COUNT(*) as articles FROM article_extractions GROUP BY type, year, number ORDER BY year DESC, number DESC LIMIT 10;" 2>/dev/null | grep -v Warning || true
