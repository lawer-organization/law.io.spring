#!/bin/bash
#
# Script de déploiement automatique sur Raspberry Pi
# Usage: ./deploy.sh
#

set -e  # Arrêt en cas d'erreur

echo "🚀 Déploiement Law Spring Batch sur Raspberry Pi"
echo "================================================"
echo ""

# Variables
RASPI_HOST="pi@192.168.0.37"
RASPI_PATH="/home/pi/law-spring"
JAR_NAME="law-spring-batch-1.0.0-SNAPSHOT.jar"
ENV_FILE=".env.raspi"

# 1. Build
echo "📦 Build du projet..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Erreur lors du build Maven"
    exit 1
fi

echo "✅ Build réussi"
echo ""

# 2. Transfert du JAR
echo "📤 Transfert du JAR vers le Raspberry Pi..."
scp target/${JAR_NAME} ${RASPI_HOST}:${RASPI_PATH}/

if [ $? -ne 0 ]; then
    echo "❌ Erreur lors du transfert SSH"
    exit 1
fi

echo "✅ JAR transféré"
echo ""

# 3. Transfert et configuration des variables d'environnement
echo "⚙️  Configuration des variables d'environnement..."

if [ ! -f "$ENV_FILE" ]; then
    echo "⚠️  Fichier $ENV_FILE non trouvé, configuration ignorée"
else
    # Transférer le fichier .env.raspi
    scp ${ENV_FILE} ${RASPI_HOST}:${RASPI_PATH}/.env
    
    # Lire le fichier et configurer le service systemd
    echo "📝 Mise à jour du service systemd avec les variables d'environnement..."
    
    # Créer un script temporaire pour mettre à jour le service
    cat > /tmp/update-service.sh << 'SCRIPT'
#!/bin/bash
ENV_FILE="/home/pi/law-spring/.env"
SERVICE_FILE="/etc/systemd/system/law-spring-batch.service"

# Backup du service existant
sudo cp "$SERVICE_FILE" "${SERVICE_FILE}.backup.$(date +%Y%m%d_%H%M%S)"

# Créer le nouveau fichier service avec les variables
{
    cat << 'HEADER'
[Unit]
Description=Law Spring Batch Application
After=mariadb.service
Wants=mariadb.service

[Service]
Type=simple
User=pi
WorkingDirectory=/home/pi/law-spring
HEADER

    # Ajouter les variables d'environnement
    while IFS='=' read -r key value; do
        if [[ -n "$key" && ! "$key" =~ ^# ]]; then
            key=$(echo "$key" | xargs)
            value=$(echo "$value" | xargs)
            echo "Environment=\"${key}=${value}\""
        fi
    done < "$ENV_FILE"

    cat << 'FOOTER'
ExecStart=/usr/bin/java -Xms128m -Xmx512m -jar /home/pi/law-spring/law-spring-batch-1.0.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
FOOTER
} | sudo tee "$SERVICE_FILE" > /dev/null

echo "✅ Service systemd mis à jour"
SCRIPT

    # Transférer et exécuter le script
    scp /tmp/update-service.sh ${RASPI_HOST}:/tmp/
    ssh ${RASPI_HOST} "chmod +x /tmp/update-service.sh && /tmp/update-service.sh && rm /tmp/update-service.sh"
    rm /tmp/update-service.sh
    
    echo "✅ Configuration appliquée"
fi

echo ""

# 4. Redémarrage
echo "🔄 Redémarrage du service..."
ssh ${RASPI_HOST} "sudo systemctl daemon-reload && sudo systemctl restart law-spring-batch"

if [ $? -ne 0 ]; then
    echo "❌ Erreur lors du redémarrage du service"
    exit 1
fi

echo "⏳ Attente du démarrage (90 secondes)..."
sleep 90

# 5. Vérification
echo ""
echo "📊 Statut du service:"
echo "===================="
ssh ${RASPI_HOST} "sudo systemctl status law-spring-batch --no-pager | head -15"

echo ""
echo "🏥 Health Check:"
echo "==============="
ssh ${RASPI_HOST} "curl -s http://localhost:8080/actuator/health"

echo ""
echo ""
echo "✅ Déploiement terminé avec succès!"
echo "🌐 Application disponible sur: http://192.168.0.37:8080"
