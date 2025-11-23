# 🐳 Guide Docker - law.spring

## 📋 Vue d'ensemble

Cette application est entièrement dockerisée avec :
- **Multi-stage build** - Image optimisée (~250MB vs ~700MB)
- **Docker Compose** - MySQL + Application en un clic
- **Health checks** - Monitoring automatique
- **Volumes persistants** - Données conservées
- **Mode dev/prod** - Configurations adaptées

---

## 🚀 Démarrage Rapide

### Option 1 : Docker Compose (Recommandé)

```bash
# Copier le fichier de configuration
cp .env.docker .env

# Éditer les credentials (IMPORTANT!)
nano .env  # Changer SECURITY_USER_PASSWORD et DATABASE_PASSWORD

# Démarrer (MySQL + App)
./docker-run.sh up

# Accéder à l'application
open http://localhost:8080/swagger-ui.html
```

**Credentials par défaut** :
- Username : `admin`
- Password : `changeme` (⚠️ À changer !)

### Option 2 : Docker seul (sans MySQL externe)

```bash
# Build de l'image
./docker-build.sh

# Lancer avec MySQL externe
docker run -d \
  --name law-spring \
  -p 8080:8080 \
  -e DATABASE_URL="jdbc:mysql://host.docker.internal:3306/law_batch?useSSL=true&serverTimezone=UTC" \
  -e DATABASE_USERNAME=root \
  -e DATABASE_PASSWORD=root \
  -e SECURITY_ENABLED=true \
  -e SECURITY_USER_NAME=admin \
  -e SECURITY_USER_PASSWORD=changeme \
  law-spring-batch:latest
```

---

## 🛠️ Commandes Utiles

### Gestion Docker Compose

```bash
# Démarrer (production)
./docker-run.sh up

# Démarrer (développement - sécurité OFF)
./docker-run.sh dev

# Voir les logs
./docker-run.sh logs

# Redémarrer
./docker-run.sh restart

# Arrêter
./docker-run.sh down

# Rebuild images
./docker-run.sh build

# Nettoyer tout (⚠️ supprime les données)
./docker-run.sh clean
```

### Commandes Docker Manuelles

```bash
# Build manuel
docker build -t law-spring-batch:latest .

# Lister les images
docker images | grep law-spring

# Lancer un container
docker run -p 8080:8080 law-spring-batch:latest

# Voir les logs
docker logs -f law-spring-app

# Accéder au shell du container
docker exec -it law-spring-app sh

# Inspecter le health check
docker inspect --format='{{json .State.Health}}' law-spring-app | jq
```

---

## 📁 Structure Docker

### Fichiers Créés

```
law.spring/
├── Dockerfile                 # Image multi-stage optimisée
├── .dockerignore             # Exclut fichiers inutiles
├── docker-compose.yml        # Orchestration MySQL + App
├── docker-compose.dev.yml    # Override pour développement
├── init-db.sql               # Script init MySQL
├── .env.docker               # Template variables d'environnement
├── docker-build.sh           # Script de build
└── docker-run.sh             # Script de gestion
```

### Dockerfile - Étapes

**Stage 1 : Builder**
- Base : `maven:3.9.5-eclipse-temurin-17`
- Compile l'application
- Génère le JAR

**Stage 2 : Runtime**
- Base : `eclipse-temurin:17-jre-alpine` (légère)
- Installe Tesseract OCR
- Copie le JAR
- User non-root (sécurité)
- Health check intégré

**Taille finale** : ~250MB (vs ~700MB avec JDK complet)

---

## ⚙️ Variables d'Environnement

### Fichier `.env` (Docker Compose)

```bash
# Sécurité
SECURITY_ENABLED=true
SECURITY_USER_NAME=admin
SECURITY_USER_PASSWORD=VotreMotDePasseFort123!

# Base de données
DATABASE_PASSWORD=MySQLPassword456!

# Logs (optionnel)
LOG_LEVEL_ROOT=INFO
LOG_LEVEL_APP=INFO
LOG_LEVEL_SQL=WARN

# JPA (optionnel)
SPRING_JPA_SHOW_SQL=false
SPRING_JPA_FORMAT_SQL=false
```

### Variables Disponibles

| Variable | Défaut | Description |
|----------|--------|-------------|
| `SECURITY_ENABLED` | `true` | Active/désactive la sécurité |
| `SECURITY_USER_NAME` | `admin` | Username pour l'API |
| `SECURITY_USER_PASSWORD` | `changeme` | Password (⚠️ à changer) |
| `DATABASE_URL` | Auto (docker-compose) | URL JDBC MySQL |
| `DATABASE_USERNAME` | `law_user` | User MySQL |
| `DATABASE_PASSWORD` | `root` | Password MySQL |
| `LOG_LEVEL_ROOT` | `INFO` | Niveau logs root |
| `LOG_LEVEL_APP` | `INFO` | Niveau logs application |
| `LOG_LEVEL_SQL` | `WARN` | Niveau logs SQL |
| `SPRING_JPA_SHOW_SQL` | `false` | Afficher requêtes SQL |
| `ACTUATOR_SHOW_DETAILS` | `when-authorized` | Détails actuator |
| `JAVA_OPTS` | `-Xmx512m -Xms256m` | Options JVM |

---

## 🔍 Health Checks & Monitoring

### Health Check Docker

**Automatique** : Docker vérifie `/actuator/health` toutes les 30s

```bash
# Voir le statut
docker ps

# Détails health check
docker inspect law-spring-app | grep -A 10 Health
```

**États possibles** :
- `healthy` ✅ - Application OK
- `unhealthy` ❌ - Application en erreur
- `starting` ⏳ - Démarrage en cours (60s grace period)

### Logs

```bash
# Logs temps réel
docker-compose logs -f app

# Logs MySQL
docker-compose logs -f mysql

# Dernières 100 lignes
docker-compose logs --tail=100 app

# Logs depuis timestamp
docker-compose logs --since="2025-11-23T10:00:00" app
```

### Monitoring

```bash
# Consommation ressources
docker stats law-spring-app

# Processus dans le container
docker top law-spring-app

# Événements
docker events --filter container=law-spring-app
```

---

## 💾 Volumes & Données

### Volumes Créés

```bash
# Lister les volumes
docker volume ls | grep law

# Inspecter un volume
docker volume inspect law-spring_mysql_data

# Backup MySQL
docker exec law-mysql mysqldump -u root -proot law_batch > backup.sql

# Restore MySQL
docker exec -i law-mysql mysql -u root -proot law_batch < backup.sql
```

### Mapping Volumes

| Volume | Type | Description |
|--------|------|-------------|
| `mysql_data` | Named | Données MySQL persistantes |
| `app_data` | Named | Fichiers app (PDFs, OCR, JSON) |
| `./logs` | Bind | Logs accessibles sur host |

### Accéder aux Données

```bash
# Accéder au container app
docker exec -it law-spring-app sh

# Naviguer dans les données
cd /app/data
ls -la pdfs/ ocr/ articles/

# Copier fichiers depuis container
docker cp law-spring-app:/app/data/articles ./local-articles
```

---

## 🧪 Tests

### Test Health Check

```bash
# Sans auth (public)
curl http://localhost:8080/actuator/health

# Attendu: {"status":"UP"}
```

### Test API Avec Auth

```bash
# Stats articles
curl -u admin:changeme http://localhost:8080/api/articles/stats

# Lancer un job
curl -u admin:changeme -X POST http://localhost:8080/api/batch/fetch-current
```

### Test Containers

```bash
# Test MySQL connectivité
docker exec law-mysql mysql -u law_user -proot -e "SHOW DATABASES;"

# Test app logs
docker logs law-spring-app | grep "Started LawSpringBatchApplication"

# Test health depuis container
docker exec law-spring-app wget -qO- http://localhost:8080/actuator/health
```

---

## 🚀 Déploiement Production

### Option 1 : Docker Compose sur VPS

```bash
# Sur le serveur
git clone <repo>
cd law.spring

# Configurer
cp .env.docker .env
nano .env  # Éditer credentials

# Démarrer
docker-compose up -d

# Vérifier
docker-compose ps
curl http://localhost:8080/actuator/health
```

### Option 2 : Push vers Registry

```bash
# Tag pour registry
docker tag law-spring-batch:latest registry.example.com/law-spring:latest

# Push
docker push registry.example.com/law-spring:latest

# Pull & Run sur serveur
docker pull registry.example.com/law-spring:latest
docker run -d -p 8080:8080 \
  -e DATABASE_URL=... \
  -e SECURITY_USER_PASSWORD=... \
  registry.example.com/law-spring:latest
```

### Option 3 : Render avec Docker

**render.yaml** :
```yaml
services:
  - type: web
    name: law-spring-batch
    runtime: docker
    dockerfilePath: ./Dockerfile
    envVars:
      - key: DATABASE_URL
        sync: false
      - key: SECURITY_USER_PASSWORD
        generateValue: true
```

---

## 🔧 Développement

### Mode Développement

```bash
# Démarrer en mode dev (sécurité OFF, logs DEBUG)
./docker-run.sh dev

# Ou manuellement
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

**Avantages mode dev** :
- Sécurité désactivée (pas d'auth)
- Logs verbeux (DEBUG, SQL)
- Actuator détails complets
- Hot reload (si devtools activé)

### Rebuild Après Changements

```bash
# Rebuild complet
./docker-run.sh build

# Redémarrer
./docker-run.sh restart

# Ou tout en un
docker-compose up -d --build
```

### Debug

```bash
# Accéder au shell
docker exec -it law-spring-app sh

# Variables d'environnement
docker exec law-spring-app env | grep SECURITY

# Tester Tesseract
docker exec law-spring-app tesseract --version

# Fichiers application
docker exec law-spring-app ls -la /app
```

---

## 🐛 Troubleshooting

### Container Ne Démarre Pas

**Problème** : `Exited (1)`

```bash
# Voir les logs d'erreur
docker logs law-spring-app

# Causes communes :
# - DATABASE_URL incorrect
# - MySQL pas démarré (attendre health check)
# - Port 8080 déjà utilisé
```

**Solution** :
```bash
# Vérifier MySQL
docker logs law-mysql

# Restart
docker-compose restart app
```

### Erreur MySQL Connection

**Problème** : `Communications link failure`

```bash
# Vérifier MySQL
docker exec law-mysql mysql -u root -proot -e "SELECT 1"

# Vérifier réseau
docker network inspect law-spring_law-network
```

**Solution** :
```bash
# Recréer réseau
docker-compose down
docker-compose up -d
```

### Application Lente

**Problème** : Timeouts, lenteur

```bash
# Vérifier ressources
docker stats law-spring-app

# Si mémoire saturée, augmenter JAVA_OPTS
```

**Solution** :
```bash
# Éditer docker-compose.yml
JAVA_OPTS: "-Xmx1024m -Xms512m"

# Restart
docker-compose restart app
```

### Volume Plein

```bash
# Vérifier taille volumes
docker system df -v

# Nettoyer volumes inutilisés
docker volume prune

# Nettoyer images anciennes
docker image prune -a
```

---

## 📊 Comparaison Déploiements

| Méthode | Avantages | Inconvénients | Coût |
|---------|-----------|---------------|------|
| **Docker Compose Local** | Simple, tout-en-un | Pas de HA | Gratuit |
| **Render** | HTTPS auto, scaling | Coût mensuel | $14-45/mois |
| **VPS + Docker** | Contrôle total | Maintenance | $5-20/mois |
| **Kubernetes** | Production-grade | Complexe | Variable |
| **Docker Swarm** | Clustering simple | Moins populaire | Variable |

---

## 📚 Ressources

### Documentation
- [Dockerfile](./Dockerfile)
- [docker-compose.yml](./docker-compose.yml)
- [DEPLOY_RENDER.md](./docs/DEPLOY_RENDER.md)
- [SECURITY_SUMMARY.md](./docs/SECURITY_SUMMARY.md)

### Liens Externes
- **Docker** : [https://docs.docker.com/](https://docs.docker.com/)
- **Docker Compose** : [https://docs.docker.com/compose/](https://docs.docker.com/compose/)
- **Best Practices** : [https://docs.docker.com/develop/dev-best-practices/](https://docs.docker.com/develop/dev-best-practices/)

---

## ✅ Checklist Avant Production

- [ ] Changer `SECURITY_USER_PASSWORD` (mot de passe fort)
- [ ] Changer `DATABASE_PASSWORD` (mot de passe fort)
- [ ] Configurer volumes pour backup
- [ ] Tester health checks
- [ ] Configurer monitoring/alertes
- [ ] Setup reverse proxy (Nginx/Traefik) pour HTTPS
- [ ] Configurer firewall (ports 80/443 seulement)
- [ ] Plan de backup automatique
- [ ] Documentation équipe (credentials, procédures)

---

**Version** : 1.0  
**Date** : 23 novembre 2025  
**Auteur** : GitHub Copilot  
**Status** : ✅ Production Ready
