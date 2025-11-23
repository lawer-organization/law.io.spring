# ✅ Checklist Pré-Déploiement Render

## 📅 Dernière vérification : 23 novembre 2025

---

## 🔍 État Actuel de l'Application

### ✅ Tests Locaux (Docker)

| Composant | État | Détails |
|-----------|------|---------|
| Docker Containers | ✅ Running | law-mysql + law-spring-app (healthy) |
| Health Check | ✅ OK | http://localhost:8080/actuator/health |
| Files API | ✅ OK | 16 PDFs, 16 OCR, 10 Articles JSON |
| Articles API | ✅ OK | 544 articles en base |
| Batch Jobs | ✅ OK | Pipeline complet testé |
| Security | ⚠️ Disabled | SECURITY_ENABLED=false (local only) |

---

## 📋 Prérequis Techniques

### ✅ Configuration Docker
- [x] Dockerfile optimisé (multi-stage, 894MB)
- [x] Base image: eclipse-temurin:17-jre-jammy
- [x] Tesseract via JavaCPP (pas d'install système)
- [x] docker-compose.yml configuré
- [x] Health checks configurés (MySQL + App)
- [x] Non-root user (appuser:1001)

### ✅ Sécurité
- [x] Spring Security HTTP Basic Auth configuré
- [x] Credentials externalisés (variables d'environnement)
- [x] CORS configuré pour React frontend
- [x] .gitignore protège .env
- [x] Endpoints protégés sauf /actuator/health

### ✅ Base de Données
- [x] MySQL 8.0 compatible
- [x] HikariCP keep-alive configuré
- [x] Spring Batch schema auto-init
- [x] JPA ddl-auto: update

### ✅ APIs REST
- [x] 28 endpoints documentés
- [x] Authentication HTTP Basic
- [x] Articles API (4 endpoints)
- [x] Files API (9 endpoints) - NOUVEAU
- [x] Batch Jobs API (8 endpoints)
- [x] Documents API (2 endpoints)
- [x] Health Check (1 endpoint public)

### ✅ Documentation
- [x] README.md principal
- [x] 14 docs techniques dans docs/
- [x] API_REFERENCE.md complet
- [x] DEPLOY_QUICKSTART.md (guide Render)
- [x] FILE_ENDPOINTS.md (nouveaux endpoints)

---

## 🚨 Actions Requises AVANT Déploiement

### 1. ⚠️ Sécurité (CRITIQUE)

```bash
# Générer un mot de passe fort
openssl rand -base64 32
# Exemple: kJ9mL2pQrT8xN3vB6wC5zD1eF4gH7yU0
```

**Variables à configurer sur Render :**
```env
SECURITY_ENABLED=true
SECURITY_USER_NAME=admin
SECURITY_USER_PASSWORD=<MOT_DE_PASSE_FORT_32_CHARS>
```

### 2. 🗄️ Base de Données MySQL sur Render

**Étapes :**
1. Dashboard Render → New → MySQL
2. Name: `law-batch-db`
3. Database: `law_batch`
4. Region: Frankfurt (Europe)
5. Plan: Starter ($7/mois)

**Récupérer :**
- Internal Database URL (format MySQL)
- Hostname (dpg-xxxxx-a)
- Username
- Password

**Convertir en JDBC URL :**
```
jdbc:mysql://<INTERNAL_HOSTNAME>:3306/law_batch?useSSL=true&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

### 3. 🌐 Application Web Service

**Étapes :**
1. Dashboard Render → New → Web Service
2. Connect Repository: `law.io.v2`
3. Root Directory: `law.spring`
4. Environment: `Docker`
5. Region: **Même que MySQL** (Frankfurt)
6. Plan: Starter ($7/mois, 512MB RAM minimum)

**Build Settings :**
- Build Command: Auto-détecté (Dockerfile)
- Start Command: Auto-détecté

### 4. 🔧 Variables d'Environnement Render

**OBLIGATOIRES :**
```env
# Database (copier depuis Render MySQL)
DATABASE_URL=jdbc:mysql://dpg-xxxxx-a:3306/law_batch?useSSL=true&serverTimezone=UTC
DATABASE_USERNAME=law_batch_db_user
DATABASE_PASSWORD=<PASSWORD_FROM_RENDER_MYSQL>

# Security (générer avec openssl rand -base64 32)
SECURITY_ENABLED=true
SECURITY_USER_NAME=admin
SECURITY_USER_PASSWORD=<VOTRE_MOT_DE_PASSE_FORT>

# CORS (URL de votre frontend React)
CORS_ALLOWED_ORIGINS=https://your-react-app.vercel.app,http://localhost:3000

# Logs (production)
LOG_LEVEL_APP=INFO
LOG_LEVEL_SQL=WARN
SPRING_JPA_SHOW_SQL=false
```

### 5. 📝 Fichiers à NE PAS Committer

**Vérifier .gitignore :**
```
.env
.env.local
.env.production
.env.*.local
*.log
data/
```

**Fichiers sensibles présents (OK si dans .gitignore) :**
- `.env` (local, mot de passe test)
- `.env.docker` (template)

---

## 🧪 Tests Pré-Déploiement

### Tests Locaux à Effectuer

```bash
# 1. Health check
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# 2. Files stats
curl -u admin:test123 http://localhost:8080/api/files/stats | jq
# Expected: JSON avec PDFs, OCR, Articles counts

# 3. Articles stats
curl -u admin:test123 http://localhost:8080/api/articles/stats | jq
# Expected: {"totalArticles": 544, ...}

# 4. Lancer pipeline
curl -X POST -u admin:test123 http://localhost:8080/api/batch/full-pipeline
# Expected: {"jobExecutionId": X, "status": "STARTED"}

# 5. Vérifier status job
curl -u admin:test123 http://localhost:8080/api/batch/status/1
# Expected: {"status": "COMPLETED", ...}
```

### ✅ Résultats Attendus
- [x] Health check retourne UP
- [x] APIs répondent avec auth
- [x] Jobs s'exécutent sans erreur
- [x] Base MySQL accessible
- [x] Fichiers accessibles via /api/files/*

---

## 🚀 Ordre de Déploiement

### Étape 1 : Base de Données (15 min)
1. Créer MySQL sur Render
2. Noter les credentials
3. Convertir URL en format JDBC

### Étape 2 : Application (30 min)
1. Créer Web Service
2. Configurer variables d'environnement
3. Déployer (build ~10 min)
4. Vérifier logs de démarrage

### Étape 3 : Validation (10 min)
1. Health check : `https://your-app.onrender.com/actuator/health`
2. Test auth : `curl -u admin:password https://your-app.onrender.com/api/files/stats`
3. Lancer un job test
4. Vérifier les données

### Étape 4 : Frontend React (Variable)
1. Créer app React/Vite
2. Configurer API URL et credentials
3. Utiliser exemples de docs/API_REFERENCE.md
4. Mettre à jour CORS_ALLOWED_ORIGINS

---

## 💰 Coûts Estimés

| Service | Plan | Prix/mois |
|---------|------|-----------|
| MySQL | Starter | $7 |
| Web Service | Starter (512MB) | $7 |
| **Total** | | **$14/mois** |

**Notes :**
- Plan Free MySQL : limité (500MB, 1GB RAM)
- Plan Free Web : sleep après inactivité
- Production : Starter minimum recommandé

---

## 📚 Documentation de Référence

### Guides de Déploiement
- `docs/DEPLOY_QUICKSTART.md` - Guide complet étape par étape
- `docs/DEPLOY_RENDER.md` - Configuration détaillée Render
- `docs/DOCKER_GUIDE.md` - Guide Docker

### Référence API
- `docs/API_REFERENCE.md` - Tous les endpoints avec exemples React
- `docs/FILE_ENDPOINTS.md` - Endpoints fichiers détaillés

### Architecture
- `docs/ARCHITECTURE.md` - Architecture système
- `docs/BATCH_ARCHITECTURE.md` - Jobs Spring Batch

---

## 🔐 Sécurité Post-Déploiement

### À Vérifier Après Déploiement
- [ ] SECURITY_ENABLED=true sur Render
- [ ] Mot de passe fort (32+ caractères)
- [ ] CORS limité aux origines autorisées
- [ ] Pas de credentials dans les logs
- [ ] Health check accessible publiquement
- [ ] Autres endpoints protégés par auth

### Test de Sécurité
```bash
# 1. Health doit être public
curl https://your-app.onrender.com/actuator/health
# Expected: 200 OK

# 2. API doit nécessiter auth
curl https://your-app.onrender.com/api/articles/stats
# Expected: 401 Unauthorized

# 3. Avec auth doit fonctionner
curl -u admin:password https://your-app.onrender.com/api/articles/stats
# Expected: 200 OK avec JSON
```

---

## ⚠️ Points d'Attention

### Performance
- Premier démarrage : ~2 minutes (build Spring Boot)
- Cold start Render : ~30 secondes
- Recommandation : 1GB RAM si beaucoup de jobs batch

### Volumes/Données
- Docker volumes persistés sur Render
- Fichiers PDFs/OCR dans `/app/data`
- Sauvegardes MySQL via Render Dashboard

### Logs
- Render Dashboard → Logs tab
- Logs Spring Boot disponibles en temps réel
- Niveau INFO en production (configurable)

### CORS
- Configurer CORS_ALLOWED_ORIGINS avec URL React
- Supports multiples origines (séparées par virgules)
- Inclure localhost pour dev local

---

## ✅ Checklist Finale

### Avant de Cliquer "Deploy"
- [ ] MySQL créé sur Render
- [ ] Variables d'environnement configurées (10 variables)
- [ ] SECURITY_USER_PASSWORD fort généré
- [ ] DATABASE_URL en format JDBC (Internal hostname)
- [ ] CORS_ALLOWED_ORIGINS avec URL React
- [ ] Region identique (MySQL + App)
- [ ] .env non commité
- [ ] Documentation lue (DEPLOY_QUICKSTART.md)

### Après Déploiement
- [ ] Health check répond UP
- [ ] Test auth fonctionne
- [ ] Lancer un job test (fetch-current)
- [ ] Vérifier les logs (pas d'erreurs)
- [ ] Tester depuis React frontend
- [ ] Documenter URL de production

---

## 🆘 Troubleshooting

### Erreur : Connection refused
➡️ Vérifier DATABASE_URL utilise Internal hostname (sans .render.com)

### Erreur : 401 Unauthorized
➡️ Vérifier SECURITY_USER_NAME et SECURITY_USER_PASSWORD

### Erreur : CORS policy
➡️ Ajouter URL React dans CORS_ALLOWED_ORIGINS

### Erreur : Out of memory
➡️ Upgrader vers plan avec plus de RAM (1GB+)

### Application ne démarre pas
➡️ Vérifier logs Render : Dashboard → Service → Logs

---

## 📞 Support

**Documentation :**
- API : `docs/API_REFERENCE.md`
- Déploiement : `docs/DEPLOY_QUICKSTART.md`
- Fichiers : `docs/FILE_ENDPOINTS.md`

**Tests Locaux :**
```bash
./scripts/test-file-endpoints.sh
```

**Render Documentation :**
- https://render.com/docs
- https://render.com/docs/docker

---

## ✅ Résumé

**Application prête pour production :**
- ✅ Docker optimisé (894MB)
- ✅ Sécurité configurée (à activer sur Render)
- ✅ 28 endpoints REST testés
- ✅ Pipeline batch complet fonctionnel
- ✅ Documentation complète
- ✅ Tests automatisés disponibles

**Temps estimé de déploiement : ~1 heure**

**Prochaine étape :** Suivre `docs/DEPLOY_QUICKSTART.md` 🚀
