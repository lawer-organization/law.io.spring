# 🍓 Déploiement sur Raspberry Pi

Guide complet pour déployer Law Spring Batch sur Raspberry Pi.

## 📋 Prérequis

- **Raspberry Pi 2/3/4** (1GB RAM minimum, 4GB recommandé)
- **Système:** Raspberry Pi OS (64-bit recommandé)
- **Connexion:** SSH ou accès direct
- **Espace disque:** 10GB minimum disponible
- **Note:** L'application fonctionne sur Raspberry Pi 1GB mais démarre lentement (~90 secondes)

## 🚀 Installation initiale

### 1. Connexion au Raspberry Pi

```bash
# Depuis votre Mac
ssh pi@<IP_RASPBERRY_PI>
```

### 2. Télécharger le script d'installation

```bash
# Télécharger directement depuis GitHub
curl -O https://raw.githubusercontent.com/lawer-organization/law.io.spring/main/scripts/raspi-setup.sh
chmod +x raspi-setup.sh
```

### 3. Exécuter l'installation

```bash
./raspi-setup.sh
```

Le script va :
- ✅ Installer Java 17, Maven, Git
- ✅ Installer MariaDB Server (compatible MySQL)
- ✅ Configurer le dialect Hibernate pour MariaDB
- ✅ Créer la base de données `law_batch`
- ✅ Cloner le projet
- ✅ Build l'application
- ✅ Créer le service systemd avec mémoire optimisée
- ✅ Configurer les répertoires de données

### 4. Configuration des mots de passe

**IMPORTANT:** Les variables sont configurées directement dans le service systemd.

Éditez le service :

```bash
sudo nano /etc/systemd/system/law-spring-batch.service
```

Changez **impérativement** :
```ini
Environment="SPRING_DATASOURCE_PASSWORD=votre_nouveau_mot_de_passe"
Environment="SECURITY_USER_PASSWORD=votre_nouveau_mot_de_passe_api"
```

Rechargez la configuration :
```bash
sudo systemctl daemon-reload
```

**Note:** Le fichier `.env` existe mais n'est PAS utilisé par systemd (problème de compatibilité).

### 5. Démarrer l'application

```bash
sudo systemctl start law-spring-batch
```

⏳ **Le démarrage prend environ 90 secondes sur Raspberry Pi 1GB.**

Vérifier le statut :
```bash
sudo systemctl status law-spring-batch
```

Suivre les logs en temps réel :
```bash
sudo journalctl -u law-spring-batch -f
```

Attendre le message `Started LawSpringBatchApplication in X seconds`.

### 6. Tester l'application

```bash
# Health check
curl http://localhost:8080/actuator/health

# Avec authentification
curl -u admin:votre_password http://localhost:8080/api/files/stats
```

## ⏰ Configuration des tâches automatiques

### Installer les crons

```bash
cd /opt/law-spring-batch/scripts
./raspi-install-crons.sh
```

### Planning des tâches

| Tâche | Fréquence | Description |
|-------|-----------|-------------|
| Health check | Toutes les 5 min | Vérifie que l'app est UP |
| Full pipeline | Toutes les 30 min | Pipeline complet (fetch → download → OCR → extract) |
| Log rotation | Tous les jours à 00:00 | Supprime les logs > 30 jours |

### Voir les crons installés

```bash
crontab -l
```

### Logs des tâches cron

```bash
# Voir tous les logs
tail -f /var/log/law-*.log

# Log spécifique
tail -f /var/log/law-fetch-current.log
tail -f /var/log/law-ocr.log
```

## 🔄 Mise à jour de l'application

```bash
cd /opt/law-spring-batch/scripts
./raspi-update.sh
```

Ce script va :
1. Récupérer les dernières modifications (git pull)
2. Rebuild le projet
3. Redémarrer le service
4. Vérifier le statut

## 📊 Monitoring

### Statut du service

```bash
sudo systemctl status law-spring-batch
```

### Logs en temps réel

```bash
sudo journalctl -u law-spring-batch -f
```

### Logs des 100 dernières lignes

```bash
sudo journalctl -u law-spring-batch -n 100 --no-pager
```

### Ressources système

```bash
# CPU et RAM
htop

# Espace disque
df -h

# Taille des données
du -sh /var/law-data/*
```

## 🔧 Commandes utiles

### Service systemd

```bash
# Démarrer
sudo systemctl start law-spring-batch

# Arrêter
sudo systemctl stop law-spring-batch

# Redémarrer
sudo systemctl restart law-spring-batch

# Statut
sudo systemctl status law-spring-batch

# Activer au démarrage
sudo systemctl enable law-spring-batch

# Désactiver au démarrage
sudo systemctl disable law-spring-batch
```

### MariaDB/MySQL

```bash
# Se connecter
mysql -u law_user -p law_batch

# Voir les tables
mysql -u law_user -p law_batch -e "SHOW TABLES;"

# Compter les articles
mysql -u law_user -p law_batch -e "SELECT COUNT(*) FROM article;"

# Backup de la base
mysqldump -u law_user -p law_batch > backup_$(date +%Y%m%d).sql

# Restore
mysql -u law_user -p law_batch < backup_20250101.sql

# Root sans mot de passe (Raspberry Pi)
sudo mysql
```

### API REST (depuis le Raspberry Pi)

```bash
# Stats des fichiers
curl -u admin:password http://localhost:8080/api/files/stats

# Lancer un job manuellement
curl -X POST -u admin:password http://localhost:8080/api/batch/fetch-current

# Pipeline complet
curl -X POST -u admin:password http://localhost:8080/api/batch/full-pipeline

# Statut d'un job
curl -u admin:password http://localhost:8080/api/batch/jobs/last/fetch-current-job
```

## 🌐 Accès depuis l'extérieur

### Option 1: Port forwarding sur le routeur

Configurer le routeur pour rediriger le port 8080 vers le Raspberry Pi.

### Option 2: Nginx reverse proxy

```bash
# Installer Nginx
sudo apt-get install -y nginx

# Configurer
sudo nano /etc/nginx/sites-available/law-api
```

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/law-api /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

### Option 3: Cloudflare Tunnel

Gratuit et sécurisé, permet d'exposer le Raspberry Pi sans ouvrir de ports.

## 🔒 Sécurité

### Firewall (UFW)

```bash
# Installer
sudo apt-get install -y ufw

# Configurer
sudo ufw allow ssh
sudo ufw allow 8080/tcp
sudo ufw enable

# Statut
sudo ufw status
```

### Changer les mots de passe par défaut

```bash
# MySQL
mysql -u root -p
ALTER USER 'law_user'@'localhost' IDENTIFIED BY 'nouveau_mot_de_passe';
FLUSH PRIVILEGES;

# Application (.env)
cd /opt/law-spring-batch
nano .env
# Modifier SECURITY_USER_PASSWORD

# Redémarrer
sudo systemctl restart law-spring-batch
```

## 📁 Structure des répertoires

```
/opt/law-spring-batch/          # Application
├── src/
├── target/
├── .env                        # Configuration
└── scripts/

/opt/law-cron-scripts/          # Scripts cron
├── fetch-current.sh
├── download-pdfs.sh
├── process-ocr.sh
└── extract-articles.sh

/var/law-data/                  # Données
├── pdfs/loi/                   # PDFs téléchargés
├── ocr/loi/                    # Fichiers OCR
├── articles/loi/               # Articles JSON
└── output/                     # Exports

/var/log/                       # Logs cron
├── law-fetch-current.log
├── law-download.log
├── law-ocr.log
└── law-extract.log
```

## 🐛 Dépannage

### L'application ne démarre pas

```bash
# Voir les logs détaillés
sudo journalctl -u law-spring-batch -n 100 --no-pager

# Erreur commune: "Unable to determine Dialect"
# Solution: Vérifier que SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT est défini
sudo grep DIALECT /etc/systemd/system/law-spring-batch.service
# Doit contenir: Environment="SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.MariaDBDialect"

# Tester la connexion MariaDB/MySQL
mysql -u law_user -p law_batch -e "SELECT 1;"

# Sur Raspberry Pi, root n'a pas de mot de passe par défaut
sudo mysql -e "SELECT User, Host FROM mysql.user WHERE User = 'law_user';"
```

### Erreur de mémoire (OutOfMemoryError)

La configuration par défaut est optimisée pour Raspberry Pi 1GB :
- **Raspberry Pi 1GB:** `-Xms256m -Xmx800m` (défaut)
- **Raspberry Pi 2GB:** `-Xms512m -Xmx1536m`
- **Raspberry Pi 4GB+:** `-Xms1G -Xmx3G`

Modifier la mémoire :

```bash
sudo nano /etc/systemd/system/law-spring-batch.service
```

Modifier `ExecStart` :
```ini
ExecStart=/usr/bin/java -Xms512m -Xmx1536m -jar /opt/law-spring-batch/target/law-spring-batch-1.0.0-SNAPSHOT.jar
```

Recharger et redémarrer :
```bash
sudo systemctl daemon-reload
sudo systemctl restart law-spring-batch
```

### Les crons ne s'exécutent pas

```bash
# Vérifier que cron est actif
sudo systemctl status cron

# Voir les logs cron système
grep CRON /var/log/syslog

# Tester un script manuellement
/opt/law-cron-scripts/fetch-current.sh
```

## 📞 Support

- **GitHub Issues:** https://github.com/lawer-organization/law.io.spring/issues
- **Documentation:** `docs/` dans le projet

## 🎉 C'est tout !

Votre application Law Spring Batch est maintenant déployée et automatisée sur votre Raspberry Pi ! 🍓
