#!/bin/bash
#############################################
# Mise à jour de l'application sur Raspberry Pi
#############################################

set -e

echo "🔄 Mise à jour Law Spring Batch"
echo "==============================="

INSTALL_DIR="/opt/law-spring-batch"

cd "$INSTALL_DIR"

echo ""
echo "📥 1. Récupération des dernières modifications"
git pull

echo ""
echo "🔨 2. Rebuild du projet"
mvn clean package -DskipTests

echo ""
echo "🔄 3. Redémarrage du service"
sudo systemctl restart law-spring-batch

echo ""
echo "⏳ Attente du démarrage (10 secondes)..."
sleep 10

echo ""
echo "🔍 4. Vérification du statut"
sudo systemctl status law-spring-batch --no-pager

echo ""
echo "🏥 5. Health check"
curl -s http://localhost:8080/actuator/health | jq .

echo ""
echo "✅ Mise à jour terminée !"
echo ""
echo "📊 Voir les logs: sudo journalctl -u law-spring-batch -f"
