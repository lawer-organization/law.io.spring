# 🚀 Déploiement Render - Guide Complet

## 📋 Checklist Pré-Déploiement

- [x] Docker fonctionne en local
- [x] Spring Security configuré avec HTTP Basic Auth
- [x] CORS configuré pour React
- [x] Tesseract via JavaCPP (pas d'install système)
- [x] Spring Batch auto-init (sans @EnableBatchProcessing)
- [ ] Créer compte Render
- [ ] Déployer MySQL sur Render
- [ ] Déployer application Spring Boot
- [ ] Configurer variables d'environnement
- [ ] Tester les endpoints
- [ ] Connecter frontend React

---

## 🗄️ Étape 1 : Base de Données MySQL sur Render

### 1.1 Créer le Service MySQL

1. **Dashboard Render** → **New** → **MySQL**
2. **Configuration** :
   - **Name** : `law-batch-db`
   - **Database** : `law_batch`
   - **Region** : Frankfurt (Europe) ou proche de vous
   - **Plan** : Free (pour test) ou Starter ($7/mois)

3. **Créer** → Render génère :
   ```
   Hostname: dpg-xxxxx-a.frankfurt-postgres.render.com
   Port: 3306
   Database: law_batch
   Username: law_batch_db_user
   Password: [généré automatiquement]
   ```

4. **Noter l'Internal Connection String** :
   ```
   mysql://law_batch_db_user:password@dpg-xxxxx-a/law_batch
   ```

### 1.2 Convertir en JDBC URL

```
jdbc:mysql://dpg-xxxxx-a.frankfurt-postgres.render.com:3306/law_batch?useSSL=true&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

⚠️ **Important** : Utiliser l'**Internal hostname** (sans `.render.com`) pour connexion depuis l'app.

---

## 🌐 Étape 2 : Application Spring Boot sur Render

### 2.1 Créer le Web Service

1. **Dashboard Render** → **New** → **Web Service**
2. **Connect Repository** :
   - GitHub : Connecter votre repo `law.io.v2`
   - Branche : `main`

3. **Configuration** :
   - **Name** : `law-spring-batch`
   - **Region** : **Même région que MySQL** (Frankfurt)
   - **Branch** : `main`
   - **Root Directory** : `law.spring`
   - **Environment** : `Docker`
   - **Plan** : Starter ($7/mois minimum - 512MB RAM)

### 2.2 Build Settings

Render détecte automatiquement le `Dockerfile` :

```dockerfile
# Build Command (automatique)
docker build -t law-spring-batch .

# Start Command (automatique)
docker run -p 8080:8080 law-spring-batch
```

---

## 🔧 Étape 3 : Variables d'Environnement

### 3.1 Variables Obligatoires

Dans **Environment** → **Add Environment Variable** :

| Clé | Valeur | Description |
|-----|--------|-------------|
| `DATABASE_URL` | `jdbc:mysql://dpg-xxxxx-a:3306/law_batch?useSSL=true&serverTimezone=UTC` | URL JDBC MySQL (Internal) |
| `DATABASE_USERNAME` | `law_batch_db_user` | User MySQL |
| `DATABASE_PASSWORD` | `[from Render MySQL]` | Password MySQL |
| `SECURITY_USER_NAME` | `admin` | Username API |
| `SECURITY_USER_PASSWORD` | `[générer un fort]` | Password API (32 chars) |
| `SECURITY_ENABLED` | `true` | Activer sécurité |
| `CORS_ALLOWED_ORIGINS` | `https://your-react-app.vercel.app,http://localhost:3000` | Origines React autorisées |
| `LOG_LEVEL_APP` | `INFO` | Niveau de logs |
| `SPRING_JPA_SHOW_SQL` | `false` | Désactiver logs SQL en prod |

### 3.2 Générer Password Sécurisé

```bash
openssl rand -base64 32
# Exemple : kJ9mL2pQrT8xN3vB6wC5zD1eF4gH7yU0
```

### 3.3 Format CORS_ALLOWED_ORIGINS

Si votre React est sur Vercel/Netlify :
```
https://your-app.vercel.app,http://localhost:3000,http://localhost:5173
```

---

## 🚀 Étape 4 : Déployer

1. **Cliquer** → **Create Web Service**
2. **Attendre** le build (~5-10 minutes)
3. **Logs** :
   ```
   ==> Building...
   ==> Successfully built Docker image
   ==> Deploying...
   ==> Your service is live at https://law-spring-batch.onrender.com
   ```

4. **Vérifier santé** :
   ```bash
   curl https://law-spring-batch.onrender.com/actuator/health
   # {"status":"UP"}
   ```

---

## ✅ Étape 5 : Tester les APIs

### 5.1 Test sans Auth (Health Check)

```bash
curl https://law-spring-batch.onrender.com/actuator/health
```

**Expected** :
```json
{"status":"UP"}
```

### 5.2 Test avec Auth (Stats)

```bash
curl -u admin:your-password \
  https://law-spring-batch.onrender.com/api/articles/stats
```

**Expected** :
```json
{
  "totalArticles": 0,
  "byYear": {},
  "byType": {}
}
```

### 5.3 Lancer Pipeline Complet

```bash
curl -X POST -u admin:your-password \
  https://law-spring-batch.onrender.com/api/batch/full-pipeline
```

**Expected** :
```json
{
  "jobName": "fullPipelineJob",
  "jobExecutionId": 1,
  "status": "STARTED"
}
```

### 5.4 Vérifier Statut Job

```bash
curl -u admin:your-password \
  https://law-spring-batch.onrender.com/api/batch/status/1
```

**Expected** :
```json
{
  "jobName": "fullPipelineJob",
  "jobExecutionId": 1,
  "status": "COMPLETED",
  "exitStatus": "COMPLETED"
}
```

---

## ⚛️ Étape 6 : Frontend React (Vite)

### 6.1 Créer Projet React

```bash
npm create vite@latest law-frontend -- --template react
cd law-frontend
npm install axios
```

### 6.2 Configuration API (.env)

```env
# .env.development
VITE_API_URL=http://localhost:8080
VITE_API_USERNAME=admin
VITE_API_PASSWORD=test123

# .env.production
VITE_API_URL=https://law-spring-batch.onrender.com
VITE_API_USERNAME=admin
VITE_API_PASSWORD=kJ9mL2pQrT8xN3vB6wC5zD1eF4gH7yU0
```

### 6.3 Service API

```javascript
// src/api/client.js
import axios from 'axios';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  auth: {
    username: import.meta.env.VITE_API_USERNAME,
    password: import.meta.env.VITE_API_PASSWORD
  }
});

export const articlesAPI = {
  getAll: () => apiClient.get('/api/articles/export'),
  getStats: () => apiClient.get('/api/articles/stats')
};

export const batchAPI = {
  runPipeline: () => apiClient.post('/api/batch/full-pipeline'),
  getJobStatus: (id) => apiClient.get(`/api/batch/status/${id}`)
};
```

### 6.4 Composant Dashboard

```jsx
// src/App.jsx
import { useState, useEffect } from 'react';
import { articlesAPI, batchAPI } from './api/client';

function App() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadStats();
  }, []);

  const loadStats = async () => {
    try {
      const { data } = await articlesAPI.getStats();
      setStats(data);
    } catch (error) {
      console.error('Erreur:', error);
    }
  };

  const runPipeline = async () => {
    setLoading(true);
    try {
      const { data } = await batchAPI.runPipeline();
      alert(`Pipeline lancé! Job ID: ${data.jobExecutionId}`);
      
      // Poll status toutes les 5 secondes
      const interval = setInterval(async () => {
        const status = await batchAPI.getJobStatus(data.jobExecutionId);
        if (status.data.status === 'COMPLETED') {
          clearInterval(interval);
          await loadStats();
          alert('Pipeline terminé!');
        }
      }, 5000);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <h1>Law Spring Batch - Dashboard</h1>
      
      {stats && (
        <div>
          <h2>Statistiques</h2>
          <p>Total Articles: {stats.totalArticles}</p>
        </div>
      )}

      <button onClick={runPipeline} disabled={loading}>
        {loading ? 'En cours...' : 'Lancer Pipeline'}
      </button>
    </div>
  );
}

export default App;
```

### 6.5 Déployer React sur Vercel

```bash
# Installer Vercel CLI
npm i -g vercel

# Déployer
vercel
```

Ajouter les variables d'environnement dans Vercel Dashboard :
- `VITE_API_URL`
- `VITE_API_USERNAME`
- `VITE_API_PASSWORD`

---

## 🔍 Troubleshooting

### Erreur : "Connection refused"

✅ **Solution** : Vérifier que `DATABASE_URL` utilise l'**Internal hostname** (sans `.render.com`)

### Erreur : "401 Unauthorized"

✅ **Solution** : Vérifier `SECURITY_USER_NAME` et `SECURITY_USER_PASSWORD` dans Render

### Erreur : "CORS policy"

✅ **Solution** : Ajouter l'origine React dans `CORS_ALLOWED_ORIGINS`

### Erreur : "Out of memory"

✅ **Solution** : Upgrader vers plan avec plus de RAM (1GB minimum recommandé)

### Logs ne s'affichent pas

✅ **Solution** : Render → Service → Logs tab

---

## 📊 Monitoring

### Logs Render

```bash
# Dans Dashboard Render
Logs → Dernières 24h
```

### Métriques

```bash
# Actuator endpoints
curl -u admin:password https://your-app.onrender.com/actuator/info
curl -u admin:password https://your-app.onrender.com/actuator/metrics
```

---

## 💰 Coûts Render

| Service | Plan | Prix/mois |
|---------|------|-----------|
| MySQL | Free | $0 (limité) |
| MySQL | Starter | $7 |
| Web Service | Starter | $7 |
| **Total** | **Starter** | **$14/mois** |

---

## ✅ Checklist Post-Déploiement

- [ ] Health check fonctionne
- [ ] Auth fonctionne (test avec curl)
- [ ] CORS fonctionne (test depuis React)
- [ ] Pipeline complet s'exécute
- [ ] Articles sont extraits
- [ ] Frontend React connecté
- [ ] Variables d'environnement sécurisées
- [ ] Monitoring configuré

---

## 📚 Ressources

- **API Reference** : `docs/API_REFERENCE.md`
- **Security Guide** : `docs/SECURITY_SUMMARY.md`
- **Docker Guide** : `docs/DOCKER_GUIDE.md`
- **Render Docs** : https://render.com/docs
- **Spring Boot Docs** : https://spring.io/projects/spring-boot

🎉 **Votre application est maintenant en production !**
