#!/bin/bash

# Script de test des endpoints fichiers
# Usage: ./test-file-endpoints.sh

API_URL="http://localhost:8080"
USERNAME="admin"
PASSWORD="test123"
AUTH="$USERNAME:$PASSWORD"

echo "🧪 Test des Endpoints Fichiers"
echo "=============================="
echo ""

# Couleurs
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Fonction de test
test_endpoint() {
    local method=$1
    local endpoint=$2
    local description=$3
    
    echo -ne "${YELLOW}Testing:${NC} $description... "
    
    response=$(curl -s -u "$AUTH" -X "$method" "$API_URL$endpoint" -o /dev/null -w "%{http_code}")
    
    if [ "$response" = "200" ]; then
        echo -e "${GREEN}✓ OK${NC} (HTTP $response)"
    else
        echo -e "${RED}✗ FAIL${NC} (HTTP $response)"
    fi
}

echo "📊 1. Statistiques"
echo "-------------------"
test_endpoint "GET" "/api/files/stats" "Statistiques globales"
echo ""

echo "📄 2. Listes de fichiers"
echo "------------------------"
test_endpoint "GET" "/api/files/pdfs" "Liste des PDFs"
test_endpoint "GET" "/api/files/ocr" "Liste des OCR"
test_endpoint "GET" "/api/files/articles" "Liste des articles JSON"
echo ""

echo "🔍 3. Détails (récupération du premier fichier)"
echo "-----------------------------------------------"

# Récupérer le nom du premier PDF
first_pdf=$(curl -s -u "$AUTH" "$API_URL/api/files/pdfs" | grep -o '"filename":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -n "$first_pdf" ]; then
    echo "Premier PDF trouvé: $first_pdf"
    
    # Tester le téléchargement
    test_endpoint "GET" "/api/files/pdfs/$first_pdf" "Téléchargement PDF"
    
    # Tester l'OCR correspondant
    ocr_file="${first_pdf%.pdf}.txt"
    test_endpoint "GET" "/api/files/ocr/$ocr_file" "Téléchargement OCR"
    test_endpoint "GET" "/api/files/ocr/$ocr_file/content" "Lecture contenu OCR"
    
    # Tester le JSON correspondant
    json_file="${first_pdf%.pdf}.json"
    test_endpoint "GET" "/api/files/articles/$json_file" "Téléchargement JSON"
    test_endpoint "GET" "/api/files/articles/$json_file/content" "Lecture contenu JSON"
else
    echo -e "${RED}✗ Aucun PDF trouvé${NC}"
fi
echo ""

echo "📊 4. Exemples de réponses"
echo "-------------------------"

echo "Stats globales:"
curl -s -u "$AUTH" "$API_URL/api/files/stats" | jq '.pdfs, .ocr, .articles' 2>/dev/null || echo "jq non installé, réponse brute ci-dessous:"
echo ""

if [ -n "$first_pdf" ]; then
    ocr_file="${first_pdf%.pdf}.txt"
    echo "Contenu OCR ($ocr_file) - Premières lignes:"
    curl -s -u "$AUTH" "$API_URL/api/files/ocr/$ocr_file/content" | jq -r '.content' 2>/dev/null | head -10 || echo "jq non installé"
fi
echo ""

echo "✅ Tests terminés!"
echo ""
echo "📚 Documentation complète: docs/FILE_ENDPOINTS.md"
