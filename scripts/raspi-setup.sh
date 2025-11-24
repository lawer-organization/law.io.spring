#!/bin/bash
#############################################
# Script d'installation pour Raspberry Pi
# À exécuter sur le Raspberry Pi
#############################################

set -e

echo "🍓 Installation Law Spring Batch sur Raspberry Pi"
echo "=================================================="

# Couleurs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Vérifier si on est sur Raspberry Pi
if [ ! -f /proc/device-tree/model ] || ! grep -q "Raspberry Pi" /proc/device-tree/model; then
    echo -e "${YELLOW}⚠️  Attention: Ce script est conçu pour Raspberry Pi${NC}"
    read -p "Continuer quand même? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo ""
echo "📦 1. Installation des dépendances système"
echo "----------------------------------------"

# Mise à jour du système
sudo apt-get update
sudo apt-get upgrade -y

# Installer Java 17
if ! command -v java &> /dev/null; then
    echo "Installation de Java 17..."
    sudo apt-get install -y openjdk-17-jdk
else
    echo "✅ Java déjà installé"
    java -version
fi

# Installer Maven
if ! command -v mvn &> /dev/null; then
    echo "Installation de Maven..."
    sudo apt-get install -y maven
else
    echo "✅ Maven déjà installé"
    mvn -version
fi

# Installer Git
if ! command -v git &> /dev/null; then
    echo "Installation de Git..."
    sudo apt-get install -y git
else
    echo "✅ Git déjà installé"
fi

echo ""
echo "🗄️  2. Installation de MariaDB (compatible MySQL)"
echo "----------------------------------------"

if ! command -v mysql &> /dev/null; then
    echo "Installation de MariaDB Server..."
    sudo apt-get install -y mariadb-server
    
    # Démarrer MariaDB
    sudo systemctl start mariadb
    sudo systemctl enable mariadb
    
    echo -e "${YELLOW}"
    echo "⚠️  IMPORTANT: Sécuriser MariaDB"
    echo "Exécuter après ce script: sudo mysql_secure_installation"
    echo -e "${NC}"
else
    echo "✅ MariaDB/MySQL déjà installé"
fi

echo ""
echo "📁 3. Création de la base de données"
echo "----------------------------------------"

read -p "Voulez-vous créer la base de données maintenant? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Sur Raspberry Pi, MariaDB root n'a pas de mot de passe par défaut."
    echo "Utilisation de sudo mysql..."
    
    # Créer la base de données et l'utilisateur (sans mot de passe root)
    sudo mysql << EOF
CREATE DATABASE IF NOT EXISTS law_batch CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'law_user'@'localhost' IDENTIFIED BY 'law_password_2024';
GRANT ALL PRIVILEGES ON law_batch.* TO 'law_user'@'localhost';
FLUSH PRIVILEGES;
SELECT User, Host FROM mysql.user WHERE User = 'law_user';
EOF
    
    echo -e "${GREEN}✅ Base de données créée${NC}"
    echo "Database: law_batch"
    echo "User: law_user"
    echo "Password: law_password_2024"
    echo -e "${YELLOW}⚠️  Changez ce mot de passe en production !${NC}"
fi

echo ""
echo "📂 4. Création des répertoires"
echo "----------------------------------------"

# Répertoires dans /home/pi
INSTALL_DIR="/home/pi/law-spring-batch"
DATA_DIR="/home/pi/law-data"
CRON_DIR="/home/pi/law-cron-scripts"

mkdir -p "$INSTALL_DIR"
mkdir -p "$DATA_DIR"/{pdfs/loi,ocr/loi,articles/loi,output}
mkdir -p "$CRON_DIR"

echo "✅ Répertoires créés:"
echo "   - Installation: $INSTALL_DIR"
echo "   - Données: $DATA_DIR"
echo "   - Scripts cron: $CRON_DIR"

echo ""
echo "📥 5. Clonage du projet"
echo "----------------------------------------"

if [ -d "$INSTALL_DIR/.git" ]; then
    echo "Mise à jour du projet..."
    cd "$INSTALL_DIR"
    git pull
else
    echo "Clonage du projet..."
    git clone https://github.com/lawer-organization/law.io.spring.git "$INSTALL_DIR"
fi

cd "$INSTALL_DIR"

echo ""
echo "🔧 6. Configuration"
echo "----------------------------------------"

# Créer le fichier .env (pour référence, non utilisé par systemd)
cat > .env << 'EOF'
# NOTE: Ce fichier n'est PAS utilisé par systemd
# Les variables sont configurées directement dans le service systemd
# Ce fichier sert uniquement de référence

# Base de données
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/law_batch?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=law_user
SPRING_DATASOURCE_PASSWORD=law_password_2024

# Hibernate Dialect (IMPORTANT pour MariaDB)
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.MariaDBDialect

# Sécurité
SECURITY_USER_USERNAME=admin
SECURITY_USER_PASSWORD=change_me_in_production

# Application
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
SPRING_BATCH_JOB_ENABLED=false

# Répertoires
LAW_DIRECTORIES_DATA=/home/pi/law-data

# Performance (Raspberry Pi optimisé)
LAW_BATCH_MAX_THREADS=1
LAW_BATCH_CHUNK_SIZE=5
EOF

echo "✅ Fichier .env créé (référence seulement)"
echo -e "${YELLOW}⚠️  Les variables sont configurées dans le service systemd${NC}"

echo ""
echo "🔨 7. Build du projet"
echo "----------------------------------------"

mvn clean package -DskipTests

echo ""
echo "🚀 8. Installation du service systemd"
echo "----------------------------------------"

# Créer le service systemd avec variables en ligne
# NOTE: EnvironmentFile ne fonctionne pas correctement avec systemd sur Raspberry Pi
# On définit les variables directement avec Environment=
sudo tee /etc/systemd/system/law-spring-batch.service > /dev/null << 'SERVICE'
[Unit]
Description=Law Spring Batch Application
After=mariadb.service
Requires=mariadb.service

[Service]
Type=simple
User=pi
WorkingDirectory=/home/pi/law-spring-batch
Environment="SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/law_batch?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
Environment="SPRING_DATASOURCE_USERNAME=law_user"
Environment="SPRING_DATASOURCE_PASSWORD=law_password_2024"
Environment="SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.MariaDBDialect"
Environment="SECURITY_USER_USERNAME=admin"
Environment="SECURITY_USER_PASSWORD=change_me_in_production"
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="SERVER_PORT=8080"
Environment="SPRING_BATCH_JOB_ENABLED=false"
Environment="LAW_DIRECTORIES_DATA=/home/pi/law-data"
Environment="LAW_BATCH_MAX_THREADS=1"
Environment="LAW_BATCH_CHUNK_SIZE=5"
Environment="LAW_BATCH_MAX_DOCUMENTS_TO_EXTRACT=20"
ExecStart=/usr/bin/java -Xms256m -Xmx800m -jar /home/pi/law-spring-batch/target/law-spring-batch-1.0.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
SERVICE

# Recharger systemd
sudo systemctl daemon-reload
sudo systemctl enable law-spring-batch.service

echo "✅ Service systemd installé"
echo ""
echo "Commandes disponibles:"
echo "  sudo systemctl start law-spring-batch    # Démarrer"
echo "  sudo systemctl stop law-spring-batch     # Arrêter"
echo "  sudo systemctl status law-spring-batch   # Statut"
echo "  sudo journalctl -u law-spring-batch -f   # Logs"

echo ""
echo -e "${GREEN}✅ Installation terminée !${NC}"
echo ""
echo "📝 Prochaines étapes:"
echo "1. Modifier les mots de passe dans /etc/systemd/system/law-spring-batch.service"
echo "   sudo nano /etc/systemd/system/law-spring-batch.service"
echo "   Changer: SECURITY_USER_PASSWORD et SPRING_DATASOURCE_PASSWORD"
echo ""
echo "2. Recharger et démarrer le service"
echo "   sudo systemctl daemon-reload"
echo "   sudo systemctl start law-spring-batch"
echo ""
echo "3. Vérifier le démarrage (prend ~90 secondes)"
echo "   sudo journalctl -u law-spring-batch -f"
echo "   curl http://localhost:8080/actuator/health"
echo ""
echo "4. Configurer les crons automatiques"
echo "   cd ~/law-spring-batch/scripts"
echo "   ./raspi-install-crons.sh"
echo ""
echo "⚠️  IMPORTANT:"
echo "   - Tout installé dans /home/pi/"
echo "   - Installation: ~/law-spring-batch"
echo "   - Données: ~/law-data"
echo "   - Scripts cron: ~/law-cron-scripts"
echo "   - Mémoire Java: -Xms256m -Xmx800m"
echo "   - Threads: 1 (single-threaded pour éviter conflits)"
echo "   - Temps de démarrage: ~90 secondes"
echo ""
