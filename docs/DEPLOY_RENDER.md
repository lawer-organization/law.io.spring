# Guide de Déploiement sur Render.com

## 🚀 Déploiement Spring Boot sur Render

### 📋 Prérequis

1. **Compte Render** : [https://render.com/](https://render.com/)
2. **Base de données MySQL** : Créer un service MySQL sur Render
3. **Repository Git** : Code poussé sur GitHub/GitLab

---

## 🔒 Étape 1 : Sécurité

### Génération Mot de Passe Fort

```bash
# Générer un mot de passe aléatoire de 32 caractères
openssl rand -base64 32
# Exemple : kJ9mL2pQrT8xN3vB6wC5zD1eF4gH7yU0
```

**⚠️ IMPORTANT** : Noter ce mot de passe dans un gestionnaire de mots de passe sécurisé.

---

## 📦 Étape 2 : Créer le Service MySQL sur Render

1. **Dashboard Render** → **New** → **MySQL**
2. **Configuration** :
   - Name : `law-batch-db`
   - Region : Choisir le plus proche (ex: `Frankfurt` pour Europe)
   - Plan : `Free` ou `Starter` selon vos besoins
3. **Créer** → Render génère automatiquement :
   - `Hostname`
   - `Port`
   - `Database`
   - `Username`
   - `Password`
   - **Internal Database URL** (à utiliser)

4. **Récupérer l'URL interne** :
   ```
   mysql://user:password@dpg-xxxxx-a.frankfurt-postgres.render.com/law_batch_db
   ```

5. **Convertir en format JDBC** :
   ```
   jdbc:mysql://dpg-xxxxx-a.frankfurt-postgres.render.com:3306/law_batch_db?useSSL=true&serverTimezone=UTC
   ```

---

## 🌐 Étape 3 : Créer le Service Web

1. **Dashboard Render** → **New** → **Web Service**
2. **Connecter Repository** :
   - GitHub ou GitLab
   - Sélectionner repository `law.spring`
   - Branch : `main`

3. **Configuration Service** :

| Paramètre | Valeur |
|-----------|--------|
| **Name** | `law-spring-batch` |
| **Region** | Même que DB (ex: Frankfurt) |
| **Root Directory** | `law.spring` |
| **Runtime** | `Java` |
| **Build Command** | `mvn clean package -DskipTests` |
| **Start Command** | `java -jar target/law-spring-batch-1.0.0-SNAPSHOT.jar` |
| **Plan** | `Starter` (minimum pour Java) |

4. **Advanced Settings** :
   - **Auto-Deploy** : `Yes` (déploiement automatique sur push)
   - **Health Check Path** : `/actuator/health`

---

## 🔐 Étape 4 : Variables d'Environnement

Dans **Dashboard Render** → **Environment** → Ajouter :

### Variables Obligatoires

```bash
# Sécurité
SECURITY_ENABLED=true
SECURITY_USER_NAME=admin
SECURITY_USER_PASSWORD=kJ9mL2pQrT8xN3vB6wC5zD1eF4gH7yU0  # Votre mot de passe généré

# Base de données (utiliser Internal Database URL de l'étape 2)
DATABASE_URL=jdbc:mysql://dpg-xxxxx-a.frankfurt-postgres.render.com:3306/law_batch_db?useSSL=true&serverTimezone=UTC
DATABASE_USERNAME=law_batch_user
DATABASE_PASSWORD=xxxxxxxxxxxxx  # Password fourni par Render

# Logs production
LOG_LEVEL_ROOT=INFO
LOG_LEVEL_APP=INFO
LOG_LEVEL_SQL=WARN
LOG_LEVEL_SECURITY=INFO

# JPA
SPRING_JPA_SHOW_SQL=false
SPRING_JPA_FORMAT_SQL=false

# Actuator
ACTUATOR_SHOW_DETAILS=when-authorized

# Profile
SPRING_PROFILES_ACTIVE=production
```

### Variables Optionnelles

```bash
# Si vous voulez personnaliser le port (Render utilise $PORT automatiquement)
# SERVER_PORT=${PORT}

# CORS (si frontend séparé)
# CORS_ALLOWED_ORIGINS=https://mon-frontend.onrender.com
```

---

## 📝 Étape 5 : Fichiers de Configuration

### 1. Créer `Procfile` (optionnel, Render détecte Java automatiquement)

```
web: java -Dserver.port=$PORT -jar target/law-spring-batch-1.0.0-SNAPSHOT.jar
```

### 2. Vérifier `.gitignore`

```gitignore
# Environnement local
.env.local
.env

# Build
target/
*.log

# IDE
.idea/
*.iml
.vscode/

# Données locales
data/
```

---

## 🚀 Étape 6 : Déploiement

1. **Pousser le code** :
```bash
git add .
git commit -m "feat: add security and Render config"
git push origin main
```

2. **Render déploie automatiquement** :
   - Build : ~5-10 minutes
   - Logs visibles dans Dashboard → **Logs**

3. **Vérifier le déploiement** :
```bash
# Health check (public)
curl https://law-spring-batch.onrender.com/actuator/health

# API (avec authentification)
curl -u admin:kJ9mL2pQrT8xN3vB6wC5zD1eF4gH7yU0 \
  https://law-spring-batch.onrender.com/api/articles/stats
```

---

## 🧪 Étape 7 : Tests Post-Déploiement

### Test 1 : Health Check
```bash
curl https://law-spring-batch.onrender.com/actuator/health
# ✅ Attendu: {"status":"UP"}
```

### Test 2 : API Protégée Sans Auth
```bash
curl https://law-spring-batch.onrender.com/api/articles/stats
# ✅ Attendu: 401 Unauthorized
```

### Test 3 : API Protégée Avec Auth
```bash
curl -u admin:VOTRE_MOT_DE_PASSE \
  https://law-spring-batch.onrender.com/api/articles/stats
# ✅ Attendu: {"totalArticles":123,...}
```

### Test 4 : Lancer un Job
```bash
curl -u admin:VOTRE_MOT_DE_PASSE \
  -X POST https://law-spring-batch.onrender.com/api/batch/fetch-current
# ✅ Attendu: {"message":"Fetch Current Year Job started successfully",...}
```

### Test 5 : Swagger UI
```
https://law-spring-batch.onrender.com/swagger-ui.html
# Login: admin / VOTRE_MOT_DE_PASSE
```

---

## 📊 Monitoring

### Logs en Temps Réel

**Dashboard Render** → **Logs** :
```
2025-11-23 10:00:00 [main] INFO  bj.gouv.sgg.LawApplication - Starting application
2025-11-23 10:00:05 [main] INFO  o.s.b.w.embedded.tomcat.TomcatWebServer - Tomcat started on port 8080
2025-11-23 10:00:06 [main] INFO  bj.gouv.sgg.LawApplication - Started application in 12.345 seconds
```

### Métriques Render

- **CPU Usage**
- **Memory Usage**
- **Request Rate**
- **Response Time**

### Alertes (Plan Payant)

Configurer alertes pour :
- Service Down
- High Memory Usage (>80%)
- High Response Time (>2s)

---

## 🔧 Étape 8 : Configuration Avancée (Optionnel)

### A. HTTPS (Automatique sur Render)

Render fournit automatiquement :
- Certificat SSL Let's Encrypt
- Renouvellement automatique
- Redirection HTTP → HTTPS

### B. Custom Domain

1. **Dashboard** → **Settings** → **Custom Domain**
2. Ajouter : `api.votredomaine.com`
3. Configurer DNS (CNAME) :
   ```
   api.votredomaine.com → law-spring-batch.onrender.com
   ```

### C. Scaling (Plan Payant)

**Dashboard** → **Scaling** :
- **Instances** : 1-10 instances
- **Auto-scaling** : Basé sur CPU/Memory

### D. Background Workers (Jobs Batch)

Si vous voulez exécuter les jobs batch en arrière-plan :

1. Créer un **Background Worker** sur Render
2. Utiliser la même base de données
3. Commande : `java -jar target/law-spring-batch-1.0.0-SNAPSHOT.jar --spring.batch.job.enabled=true`

---

## 🐛 Troubleshooting

### Erreur : "Application failed to start"

**Logs** :
```
java.lang.OutOfMemoryError: Java heap space
```

**Solution** :
```bash
# Dans Start Command, augmenter la mémoire JVM :
java -Xmx512m -jar target/law-spring-batch-1.0.0-SNAPSHOT.jar
```

### Erreur : "Connection refused" (MySQL)

**Cause** : Mauvaise configuration `DATABASE_URL`

**Vérifier** :
1. URL interne MySQL (pas externe)
2. Format JDBC correct : `jdbc:mysql://...`
3. Credentials exacts

### Erreur : "401 Unauthorized" sur Swagger

**Solution** :
1. Ouvrir `https://law-spring-batch.onrender.com/swagger-ui.html`
2. Cliquer sur **Authorize** (cadenas)
3. Username : `admin`
4. Password : Votre mot de passe
5. Cliquer **Authorize**

### Service Lent au Démarrage

**Cause** : Render Free tier met en veille après 15 min d'inactivité

**Solutions** :
1. **Upgrade** vers plan Starter ($7/mois) - pas de mise en veille
2. **Keep-alive** : Ping toutes les 10 min avec cron externe
3. **Render Cron Job** : Ping `/actuator/health` régulièrement

---

## 💰 Coûts Estimés

### Option Free (Test)
- **Web Service** : Free (750h/mois)
- **MySQL** : Free (1GB)
- **Limitations** :
  - Mise en veille après 15 min
  - 512MB RAM
  - Pas de scaling

**Total** : **$0/mois**

### Option Starter (Production)
- **Web Service** : Starter ($7/mois)
- **MySQL** : Starter ($7/mois)
- **Avantages** :
  - Pas de mise en veille
  - 512MB RAM + scaling
  - Support email

**Total** : **$14/mois**

### Option Professional (Haute Performance)
- **Web Service** : Pro ($25/mois)
- **MySQL** : Pro ($20/mois)
- **Avantages** :
  - 2GB RAM
  - Auto-scaling
  - Support prioritaire

**Total** : **$45/mois**

---

## 🔐 Best Practices Sécurité

### ✅ À Faire

1. **Mots de passe forts** : Minimum 32 caractères aléatoires
2. **Variables d'environnement** : Jamais de credentials dans le code
3. **HTTPS uniquement** : Render force HTTPS automatiquement
4. **Logs minimaux** : Pas de `show-sql=true` en production
5. **Actuator protégé** : Authentification requise
6. **Swagger protégé** : Authentification requise
7. **Rate limiting** (optionnel) : Limite nombre de requêtes/min

### ❌ À Éviter

1. ❌ Credentials dans `application.yml`
2. ❌ Logs verbeux (`DEBUG`, `TRACE`) en production
3. ❌ `useSSL=false` en base de données
4. ❌ Endpoints sensibles sans authentification
5. ❌ Exposer `/actuator` sans protection

---

## 📚 Ressources

- **Render Docs** : [https://render.com/docs](https://render.com/docs)
- **Spring Security** : [https://spring.io/guides/gs/securing-web/](https://spring.io/guides/gs/securing-web/)
- **Spring Boot on Render** : [https://render.com/docs/deploy-spring-boot](https://render.com/docs/deploy-spring-boot)
- **MySQL on Render** : [https://render.com/docs/databases](https://render.com/docs/databases)

---

## ✉️ Support

En cas de problème :
1. Consulter logs Render Dashboard
2. Vérifier variables d'environnement
3. Tester en local avec `.env.local`
4. Support Render : [https://render.com/support](https://render.com/support)

---

**Version** : 1.0  
**Date** : 23 novembre 2025  
**Auteur** : GitHub Copilot
