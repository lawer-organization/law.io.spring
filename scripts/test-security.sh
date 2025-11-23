#!/bin/bash

# Script de test de sécurité en local
# Usage: ./test-security.sh

set -e

BASE_URL="http://localhost:8080"
USERNAME="admin"
PASSWORD="changeme"

echo "🔐 Tests de Sécurité - law.spring"
echo "=================================="
echo ""

# Couleurs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test 1: Health Check Public
echo "📋 Test 1: Health Check (Public - Sans Auth)"
RESPONSE=$(curl -s -w "%{http_code}" -o /tmp/health.json "$BASE_URL/actuator/health")
if [ "$RESPONSE" = "200" ]; then
    echo -e "${GREEN}✅ PASS${NC} - Health check accessible sans authentification"
    cat /tmp/health.json | jq '.' 2>/dev/null || cat /tmp/health.json
else
    echo -e "${RED}❌ FAIL${NC} - Health check retourne $RESPONSE"
fi
echo ""

# Test 2: API Sans Auth (doit échouer)
echo "📋 Test 2: API Sans Authentification (doit échouer avec 401)"
RESPONSE=$(curl -s -w "%{http_code}" -o /tmp/response.json "$BASE_URL/api/articles/stats")
if [ "$RESPONSE" = "401" ]; then
    echo -e "${GREEN}✅ PASS${NC} - API protégée (401 Unauthorized)"
else
    echo -e "${RED}❌ FAIL${NC} - API retourne $RESPONSE au lieu de 401"
fi
echo ""

# Test 3: API Avec Auth Incorrecte (doit échouer)
echo "📋 Test 3: API Avec Mauvais Mot de Passe (doit échouer avec 401)"
RESPONSE=$(curl -s -w "%{http_code}" -o /tmp/response.json -u "admin:wrongpassword" "$BASE_URL/api/articles/stats")
if [ "$RESPONSE" = "401" ]; then
    echo -e "${GREEN}✅ PASS${NC} - Authentification échouée correctement (401)"
else
    echo -e "${RED}❌ FAIL${NC} - Retourne $RESPONSE au lieu de 401"
fi
echo ""

# Test 4: API Avec Auth Correcte
echo "📋 Test 4: API Avec Authentification Correcte"
RESPONSE=$(curl -s -w "%{http_code}" -o /tmp/stats.json -u "$USERNAME:$PASSWORD" "$BASE_URL/api/articles/stats")
if [ "$RESPONSE" = "200" ]; then
    echo -e "${GREEN}✅ PASS${NC} - Authentification réussie"
    cat /tmp/stats.json | jq '.' 2>/dev/null || cat /tmp/stats.json
else
    echo -e "${YELLOW}⚠️  WARN${NC} - API retourne $RESPONSE (vérifier si SECURITY_ENABLED=false)"
fi
echo ""

# Test 5: Actuator Info (protégé)
echo "📋 Test 5: Actuator Info (Protégé)"
RESPONSE=$(curl -s -w "%{http_code}" -o /tmp/response.json "$BASE_URL/actuator/info")
if [ "$RESPONSE" = "401" ]; then
    echo -e "${GREEN}✅ PASS${NC} - Actuator info protégé (401)"
else
    echo -e "${YELLOW}⚠️  WARN${NC} - Actuator info retourne $RESPONSE (devrait être 401 si sécurité activée)"
fi
echo ""

# Test 6: Actuator Info Avec Auth
echo "📋 Test 6: Actuator Info Avec Authentification"
RESPONSE=$(curl -s -w "%{http_code}" -o /tmp/info.json -u "$USERNAME:$PASSWORD" "$BASE_URL/actuator/info")
if [ "$RESPONSE" = "200" ]; then
    echo -e "${GREEN}✅ PASS${NC} - Actuator info accessible avec auth"
    cat /tmp/info.json | jq '.' 2>/dev/null || cat /tmp/info.json
else
    echo -e "${YELLOW}⚠️  INFO${NC} - Actuator info retourne $RESPONSE"
fi
echo ""

# Test 7: Swagger UI (protégé)
echo "📋 Test 7: Swagger UI (Protégé)"
RESPONSE=$(curl -s -w "%{http_code}" -o /tmp/response.html "$BASE_URL/swagger-ui.html")
if [ "$RESPONSE" = "401" ]; then
    echo -e "${GREEN}✅ PASS${NC} - Swagger UI protégé (401)"
else
    echo -e "${YELLOW}⚠️  WARN${NC} - Swagger UI retourne $RESPONSE (devrait être 401 si sécurité activée)"
fi
echo ""

# Test 8: POST Endpoint (fetch-current)
echo "📋 Test 8: POST Batch Job Sans Auth (doit échouer avec 401)"
RESPONSE=$(curl -s -w "%{http_code}" -o /tmp/response.json -X POST "$BASE_URL/api/batch/fetch-current")
if [ "$RESPONSE" = "401" ]; then
    echo -e "${GREEN}✅ PASS${NC} - POST endpoint protégé (401)"
else
    echo -e "${YELLOW}⚠️  WARN${NC} - POST retourne $RESPONSE (devrait être 401 si sécurité activée)"
fi
echo ""

# Test 9: POST Endpoint Avec Auth
echo "📋 Test 9: POST Batch Job Avec Auth"
RESPONSE=$(curl -s -w "%{http_code}" -o /tmp/job.json -u "$USERNAME:$PASSWORD" -X POST "$BASE_URL/api/batch/fetch-current")
if [ "$RESPONSE" = "202" ] || [ "$RESPONSE" = "409" ]; then
    echo -e "${GREEN}✅ PASS${NC} - POST endpoint accessible avec auth ($RESPONSE)"
    cat /tmp/job.json | jq '.' 2>/dev/null || cat /tmp/job.json
else
    echo -e "${YELLOW}⚠️  INFO${NC} - POST retourne $RESPONSE"
fi
echo ""

# Résumé
echo "=================================="
echo "🎯 Résumé des Tests"
echo "=================================="
echo ""
echo "Si SECURITY_ENABLED=true:"
echo "  - Tous les endpoints API doivent retourner 401 sans auth ✅"
echo "  - Tous les endpoints API doivent fonctionner avec auth ✅"
echo "  - Health check reste public ✅"
echo ""
echo "Si SECURITY_ENABLED=false (développement):"
echo "  - Tous les endpoints sont accessibles sans auth ⚠️"
echo ""
echo "Configuration actuelle:"
if grep -q "SECURITY_ENABLED=false" .env.local 2>/dev/null; then
    echo -e "  ${YELLOW}⚠️  Mode développement (sécurité désactivée)${NC}"
elif grep -q "SECURITY_ENABLED=true" .env.local 2>/dev/null; then
    echo -e "  ${GREEN}✅ Mode production (sécurité activée)${NC}"
else
    echo -e "  ${YELLOW}⚠️  Fichier .env.local non trouvé${NC}"
fi
echo ""
echo "Pour activer la sécurité en local:"
echo "  1. Créer/éditer .env.local"
echo "  2. Ajouter: SECURITY_ENABLED=true"
echo "  3. Redémarrer l'application"
echo ""
