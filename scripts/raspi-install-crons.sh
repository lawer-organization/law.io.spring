#!/bin/bash
#############################################
# Installation des tâches cron pour Raspberry Pi
#############################################

set -e

echo "⏰ Installation des tâches cron"
echo "================================"

# Couleurs
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

INSTALL_DIR="/opt/law-spring-batch"
API_URL="http://localhost:8080/api/batch"
API_USER="admin"

# Demander le mot de passe API
read -p "Entrez le mot de passe API (SECURITY_USER_PASSWORD): " -s API_PASSWORD
echo ""

# Créer le répertoire de scripts cron
CRON_DIR="/opt/law-cron-scripts"
sudo mkdir -p "$CRON_DIR"

echo ""
echo "📝 Création des scripts cron"
echo "----------------------------"

# Script 1: Fetch current laws (tous les jours à 2h00)
sudo tee "$CRON_DIR/fetch-current.sh" > /dev/null << EOF
#!/bin/bash
# Récupère les nouvelles lois du jour
curl -X POST -u "$API_USER:$API_PASSWORD" \\
  "$API_URL/fetch-current" \\
  >> /var/log/law-fetch-current.log 2>&1
EOF

# Script 2: Fetch previous laws (tous les lundis à 3h00)
sudo tee "$CRON_DIR/fetch-previous.sh" > /dev/null << EOF
#!/bin/bash
# Récupère les lois des 15 derniers jours
curl -X POST -u "$API_USER:$API_PASSWORD" \\
  "$API_URL/fetch-previous" \\
  >> /var/log/law-fetch-previous.log 2>&1
EOF

# Script 3: Download PDFs (tous les jours à 4h00)
sudo tee "$CRON_DIR/download-pdfs.sh" > /dev/null << EOF
#!/bin/bash
# Télécharge les PDFs depuis la base de données
curl -X POST -u "$API_USER:$API_PASSWORD" \\
  "$API_URL/download-pdfs" \\
  >> /var/log/law-download.log 2>&1
EOF

# Script 4: OCR Processing (tous les jours à 5h00)
sudo tee "$CRON_DIR/process-ocr.sh" > /dev/null << EOF
#!/bin/bash
# Traite les PDFs avec OCR
curl -X POST -u "$API_USER:$API_PASSWORD" \\
  "$API_URL/process-ocr" \\
  >> /var/log/law-ocr.log 2>&1
EOF

# Script 5: Extract articles (tous les jours à 6h00)
sudo tee "$CRON_DIR/extract-articles.sh" > /dev/null << EOF
#!/bin/bash
# Extrait les articles depuis les fichiers OCR
curl -X POST -u "$API_USER:$API_PASSWORD" \\
  "$API_URL/extract-articles" \\
  >> /var/log/law-extract.log 2>&1
EOF

# Script 6: Full pipeline (tous les dimanches à 1h00)
sudo tee "$CRON_DIR/full-pipeline.sh" > /dev/null << EOF
#!/bin/bash
# Pipeline complet: fetch → download → OCR → extract
curl -X POST -u "$API_USER:$API_PASSWORD" \\
  "$API_URL/full-pipeline" \\
  >> /var/log/law-full-pipeline.log 2>&1
EOF

# Script 7: Health check (toutes les 5 minutes)
sudo tee "$CRON_DIR/health-check.sh" > /dev/null << EOF
#!/bin/bash
# Vérifie que l'application est UP
HEALTH=\$(curl -s http://localhost:8080/actuator/health | jq -r '.status')
if [ "\$HEALTH" != "UP" ]; then
  echo "[ERROR] Application DOWN at \$(date)" >> /var/log/law-health.log
  # Redémarrer le service
  sudo systemctl restart law-spring-batch
fi
EOF

# Rendre les scripts exécutables
sudo chmod +x "$CRON_DIR"/*.sh

echo "✅ Scripts créés dans $CRON_DIR"

echo ""
echo "⏰ Configuration du crontab"
echo "---------------------------"

# Créer le fichier crontab
CRON_FILE="/tmp/law-crontab"
cat > "$CRON_FILE" << EOF
# Law Spring Batch - Tâches automatiques
# Format: minute heure jour mois jour_semaine commande

# Vérification santé (toutes les 5 minutes)
*/5 * * * * $CRON_DIR/health-check.sh

# Fetch current laws (tous les jours à 2h00)
0 2 * * * $CRON_DIR/fetch-current.sh

# Fetch previous laws (toutes les 3 heures, décalé)
0 1-22/3 * * * $CRON_DIR/fetch-previous.sh

# Download PDFs (toutes les 3 heures)
0 */3 * * * $CRON_DIR/download-pdfs.sh

# OCR Processing (toutes les 3 heures, décalé)
0 */3 * * * $CRON_DIR/process-ocr.sh

# Extract articles (toutes les 3 heures, décalé)
0 1-22/3 * * * $CRON_DIR/extract-articles.sh

# Full pipeline (tous les dimanches à 1h00)
0 1 * * 0 $CRON_DIR/full-pipeline.sh

# Rotation des logs (tous les jours à minuit)
0 0 * * * find /var/log/law-*.log -mtime +30 -delete

EOF

# Installer le crontab
crontab "$CRON_FILE"
rm "$CRON_FILE"

echo "✅ Crontab installé"

echo ""
echo "📋 Résumé des tâches planifiées"
echo "================================"
echo ""
echo "Toutes les 5 minutes:"
echo "  → Health check (redémarre si DOWN)"
echo ""
echo "Toutes les 3 heures:"
echo "  00:00, 03:00, 06:00... → Download PDFs + OCR Processing"
echo "  01:00, 04:00, 07:00... → Fetch previous + Extract articles"
echo ""
echo "Tous les jours:"
echo "  00:00 → Rotation des logs (supprime > 30 jours)"
echo "  02:00 → Fetch current (nouvelles lois du jour)"
echo ""
echo "Toutes les semaines:"
echo "  Dimanche 01:00 → Full pipeline (fetch → download → OCR → extract)"
echo ""
echo "📊 Logs disponibles:"
echo "  /var/log/law-fetch-current.log"
echo "  /var/log/law-fetch-previous.log"
echo "  /var/log/law-download.log"
echo "  /var/log/law-ocr.log"
echo "  /var/log/law-extract.log"
echo "  /var/log/law-full-pipeline.log"
echo "  /var/log/law-health.log"
echo ""
echo "🔍 Commandes utiles:"
echo "  crontab -l              # Voir les crons"
echo "  crontab -e              # Éditer les crons"
echo "  tail -f /var/log/law-*.log  # Suivre les logs"
echo ""
echo -e "${GREEN}✅ Installation des crons terminée !${NC}"
echo ""
