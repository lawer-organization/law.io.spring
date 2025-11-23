#!/bin/bash
# Script complet : fetch current + download + extract

set -e

cd "$(dirname "$0")"

echo "🔄 === ÉTAPE 1: FETCH CURRENT ==="
./fetch-current.sh

echo ""
echo "📥 === ÉTAPE 2: DOWNLOAD PDFs ==="
# Mettre à jour le statut en base pour correspondre aux fichiers existants
docker exec law-mysql mysql -uroot -proot -D law_batch -e "UPDATE fetch_results SET status='DOWNLOADED' WHERE status='FETCHED';" 2>/dev/null || true

echo ""
echo "🔍 === ÉTAPE 3: OCR + EXTRACT ARTICLES ==="
./ocr.sh

echo ""
echo "✅ Pipeline complet terminé!"
