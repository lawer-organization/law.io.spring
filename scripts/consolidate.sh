#!/bin/bash

# Script de consolidation des articles JSON vers la base de données

echo "===========================================" 
echo "    Law.Spring - Consolidation Job"
echo "==========================================="
echo ""

# Vérifier si le serveur Spring Boot est démarré
if ! curl -s -f http://localhost:8080/api/batch/status/1 > /dev/null 2>&1; then
    if ! curl -s http://localhost:8080/ > /dev/null 2>&1; then
        echo "❌ Le serveur Spring Boot n'est pas démarré"
        echo "   Lancez-le d'abord avec: ./start.sh"
        exit 1
    fi
fi

echo "✅ Serveur Spring Boot détecté"
echo ""

# Compter les fichiers JSON à consolider
TOTAL_LOI=$(find data/articles/loi -type f -name "*.json" 2>/dev/null | wc -l | tr -d ' ')
TOTAL_DECRET=$(find data/articles/decret -type f -name "*.json" 2>/dev/null | wc -l | tr -d ' ')
TOTAL=$((TOTAL_LOI + TOTAL_DECRET))

echo "📊 Fichiers JSON trouvés:"
echo "   - Lois: $TOTAL_LOI"
echo "   - Décrets: $TOTAL_DECRET"
echo "   - Total: $TOTAL"
echo ""

if [ "$TOTAL" -eq 0 ]; then
    echo "⚠️  Aucun fichier JSON trouvé"
    echo "   Lancez d'abord: ./extract-articles.sh"
    exit 0
fi

echo "🚀 Lancement du job de consolidation..."
echo ""

RESPONSE=$(curl -s -X POST http://localhost:8080/api/batch/consolidate)

if [ $? -ne 0 ]; then
    echo "❌ Erreur lors de l'appel à l'API"
    exit 1
fi

echo "$RESPONSE" | jq '.'

JOB_ID=$(echo "$RESPONSE" | jq -r '.jobExecutionId // empty')

if [ -z "$JOB_ID" ]; then
    echo ""
    echo "❌ Échec du lancement du job"
    exit 1
fi

echo ""
echo "✅ Job de consolidation lancé avec succès"
echo "   Job ID: $JOB_ID"
echo ""
echo "📊 Pour suivre l'exécution:"
echo "   tail -f logs/application.log"
echo ""
echo "📊 Pour vérifier le statut:"
echo "   curl http://localhost:8080/api/batch/status/$JOB_ID | jq"
echo ""
