#!/bin/bash
# Pipeline complet : fetch current/previous → download → OCR → extract → consolidate

set -e

cd "$(dirname "$0")"

echo "🔄 === ÉTAPE 1: FETCH CURRENT ==="
./fetch-current.sh

echo ""
echo "🔄 === ÉTAPE 2: FETCH PREVIOUS ==="
./fetch-previous.sh

echo ""
echo "📥 === ÉTAPE 3: DOWNLOAD PDFs ==="
./download.sh

echo ""
echo "🔍 === ÉTAPE 4: OCR PROCESSING ==="
./ocr.sh

echo ""
echo "📝 === ÉTAPE 5: EXTRACT ARTICLES ==="
./extract-articles.sh

echo ""
echo "📊 === ÉTAPE 6: CONSOLIDATION ==="
./consolidate.sh

echo ""
echo "✅ Pipeline complet terminé!"
