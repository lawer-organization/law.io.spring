# API REST Reference - Law Spring Batch

## 🔗 Base URL
- **Local** : `http://localhost:8080`
- **Render** : `https://your-app.onrender.com`

## 🔐 Authentification

Toutes les APIs (sauf `/actuator/health`) nécessitent HTTP Basic Authentication.

```javascript
// Exemple fetch avec auth
const response = await fetch('https://your-app.onrender.com/api/articles/stats', {
  headers: {
    'Authorization': 'Basic ' + btoa('admin:your-password')
  }
});
```

---

## 📊 Articles API

### GET `/api/articles/export`
Exporter tous les articles en JSON.

**Response:**
```json
[
  {
    "id": 1,
    "type": "loi",
    "year": 2025,
    "number": 11,
    "articleNumber": "Article 1",
    "content": "...",
    "metadata": {...}
  }
]
```

### GET `/api/articles/stats`
Statistiques sur les articles extraits.

**Response:**
```json
{
  "totalArticles": 544,
  "byYear": {
    "2025": 431,
    "2024": 113
  },
  "byType": {
    "loi": 544
  }
}
```

---

## 📄 Documents API

### GET `/api/fetch-results/{year}`
Liste des documents trouvés pour une année.

**Params:**
- `year` (path) : Année (ex: 2025)

**Response:**
```json
[
  {
    "documentId": "loi-2025-11",
    "url": "https://sgg.gouv.bj/doc/loi-2025-11/download",
    "status": "EXTRACTED",
    "year": 2025,
    "number": 11
  }
]
```

### GET `/api/fetch-results/stats`
Statistiques globales sur les documents.

**Response:**
```json
{
  "totalDocuments": 16,
  "byStatus": {
    "EXTRACTED": 15,
    "DOWNLOADED": 1
  },
  "byYear": {
    "2025": 16
  }
}
```

---

## 📁 File Resources API

### GET `/api/files/stats`
Statistiques globales des fichiers sur le disque (PDFs, OCR, Articles JSON).

**Response:**
```json
{
  "pdfs": {
    "count": 16,
    "totalSize": 129545741,
    "totalSizeMB": "123.54 MB"
  },
  "ocr": {
    "count": 16,
    "totalSize": 712287,
    "totalSizeMB": "0.68 MB"
  },
  "articles": {
    "count": 10,
    "totalSize": 389618,
    "totalSizeMB": "0.37 MB"
  },
  "dataDirectory": "data"
}
```

---

### GET `/api/files/pdfs`
Liste tous les fichiers PDF disponibles.

**Response:**
```json
{
  "count": 16,
  "exists": true,
  "directory": "data/pdfs/loi",
  "files": [
    {
      "filename": "loi-2025-11.pdf",
      "size": 1438381,
      "sizeMB": "1.37 MB",
      "lastModified": "2025-11-23T17:39:18.436271131Z"
    }
  ]
}
```

---

### GET `/api/files/ocr`
Liste tous les fichiers OCR (texte extrait) disponibles.

**Response:**
```json
{
  "count": 16,
  "exists": true,
  "directory": "data/ocr/loi",
  "files": [
    {
      "filename": "loi-2025-11.txt",
      "size": 3255,
      "sizeMB": "0.00 MB",
      "lastModified": "2025-11-23T18:33:29.070133375Z"
    }
  ]
}
```

---

### GET `/api/files/articles`
Liste tous les fichiers JSON d'articles disponibles.

**Response:**
```json
{
  "count": 10,
  "exists": true,
  "directory": "data/articles/loi",
  "files": [
    {
      "filename": "loi-2025-11.json",
      "size": 2294,
      "sizeMB": "0.00 MB",
      "lastModified": "2025-11-23T18:48:04.845611027Z"
    }
  ]
}
```

---

### GET `/api/files/pdfs/{filename}`
Télécharge un fichier PDF.

**Params:**
- `filename` (path) : Nom du fichier (ex: `loi-2025-11.pdf`)

**Headers:**
```
Content-Type: application/pdf
Content-Disposition: attachment; filename="loi-2025-11.pdf"
```

**Exemple:**
```bash
curl -u admin:password -O http://localhost:8080/api/files/pdfs/loi-2025-11.pdf
```

---

### GET `/api/files/ocr/{filename}`
Télécharge un fichier OCR (texte).

**Params:**
- `filename` (path) : Nom du fichier (ex: `loi-2025-11.txt`)

**Headers:**
```
Content-Type: text/plain
Content-Disposition: attachment; filename="loi-2025-11.txt"
```

---

### GET `/api/files/articles/{filename}`
Télécharge un fichier JSON d'articles.

**Params:**
- `filename` (path) : Nom du fichier (ex: `loi-2025-11.json`)

**Headers:**
```
Content-Type: application/json
Content-Disposition: attachment; filename="loi-2025-11.json"
```

---

### GET `/api/files/ocr/{filename}/content`
Lit le contenu d'un fichier OCR directement (sans téléchargement).

**Params:**
- `filename` (path) : Nom du fichier (ex: `loi-2025-11.txt`)

**Response:**
```json
{
  "filename": "loi-2025-11.txt",
  "content": "RÉPUBLIQUE DU BÉNIN\nLOI N° 2025-11...",
  "size": 3255,
  "lines": 87
}
```

---

### GET `/api/files/articles/{filename}/content`
Lit le contenu d'un fichier JSON d'articles directement.

**Params:**
- `filename` (path) : Nom du fichier (ex: `loi-2025-11.json`)

**Response:**
```json
[
  {
    "documentId": "loi-2025-11",
    "articleIndex": 1,
    "title": "loi-2025-11 article-1",
    "content": "Article 1er : ...",
    "confidence": 0.6,
    "documentType": "loi",
    "documentYear": 2025,
    "documentNumber": 11,
    "sourceUrl": "https://sgg.gouv.bj/doc/loi-2025-11/download",
    "promulgationDate": "2025-07-01",
    "signatories": [
      {
        "role": "Président de la République",
        "name": "Patrice TALON"
      }
    ],
    "extractedAt": "2025-11-23T18:48:04.846202570"
  }
]
```

---

## 🔄 Batch Jobs API

### POST `/api/batch/fetch-current`
Lancer le job de récupération des documents de l'année en cours.

**Response:**
```json
{
  "jobName": "fetchCurrentJob",
  "jobExecutionId": 1,
  "status": "STARTED"
}
```

### POST `/api/batch/fetch-previous`
Lancer le job de récupération des documents des années précédentes.

### POST `/api/batch/download`
Télécharger les PDFs des documents trouvés.

### POST `/api/batch/ocr`
Lancer l'extraction OCR sur les PDFs téléchargés.

### POST `/api/batch/extract`
Extraire les articles depuis les fichiers OCR.

### POST `/api/batch/full-pipeline`
Exécuter le pipeline complet (fetch → download → ocr → extract).

**Response:**
```json
{
  "jobName": "fullPipelineJob",
  "jobExecutionId": 10,
  "status": "STARTED"
}
```

### GET `/api/batch/status/{jobExecutionId}`
Obtenir le statut d'un job en cours.

**Params:**
- `jobExecutionId` (path) : ID du job

**Response:**
```json
{
  "jobName": "ocrJob",
  "jobExecutionId": 6,
  "startTime": "2025-11-23T18:29:20",
  "endTime": "2025-11-23T18:38:19",
  "status": "COMPLETED",
  "exitStatus": "COMPLETED"
}
```

---

## 🏥 Health Check

### GET `/actuator/health`
**Public** - Pas d'authentification requise.

**Response:**
```json
{
  "status": "UP"
}
```

---

## 🚀 Exemple Frontend React

### Configuration Axios

```javascript
// src/api/config.js
import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';
const API_USERNAME = process.env.REACT_APP_API_USERNAME || 'admin';
const API_PASSWORD = process.env.REACT_APP_API_PASSWORD;

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  auth: {
    username: API_USERNAME,
    password: API_PASSWORD
  },
  headers: {
    'Content-Type': 'application/json'
  }
});
```

### Service API

```javascript
// src/api/articles.js
import { apiClient } from './config';

export const articlesAPI = {
  // Récupérer tous les articles
  getAll: async () => {
    const response = await apiClient.get('/api/articles/export');
    return response.data;
  },

  // Récupérer les statistiques
  getStats: async () => {
    const response = await apiClient.get('/api/articles/stats');
    return response.data;
  }
};

export const documentsAPI = {
  // Documents par année
  getByYear: async (year) => {
    const response = await apiClient.get(`/api/fetch-results/${year}`);
    return response.data;
  },

  // Statistiques documents
  getStats: async () => {
    const response = await apiClient.get('/api/fetch-results/stats');
    return response.data;
  }
};

export const filesAPI = {
  // Statistiques des fichiers
  getStats: async () => {
    const response = await apiClient.get('/api/files/stats');
    return response.data;
  },

  // Liste des PDFs
  listPdfs: async () => {
    const response = await apiClient.get('/api/files/pdfs');
    return response.data;
  },

  // Liste des fichiers OCR
  listOcr: async () => {
    const response = await apiClient.get('/api/files/ocr');
    return response.data;
  },

  // Liste des articles JSON
  listArticles: async () => {
    const response = await apiClient.get('/api/files/articles');
    return response.data;
  },

  // Lire le contenu OCR
  readOcrContent: async (filename) => {
    const response = await apiClient.get(`/api/files/ocr/${filename}/content`);
    return response.data;
  },

  // Lire le contenu d'un article JSON
  readArticleContent: async (filename) => {
    const response = await apiClient.get(`/api/files/articles/${filename}/content`);
    return response.data;
  },

  // Télécharger un PDF
  downloadPdf: (filename) => {
    return `${apiClient.defaults.baseURL}/api/files/pdfs/${filename}`;
  },

  // Télécharger un OCR
  downloadOcr: (filename) => {
    return `${apiClient.defaults.baseURL}/api/files/ocr/${filename}`;
  },

  // Télécharger un article JSON
  downloadArticleJson: (filename) => {
    return `${apiClient.defaults.baseURL}/api/files/articles/${filename}`;
  }
  }
};

export const batchAPI = {
  // Lancer pipeline complet
  runFullPipeline: async () => {
    const response = await apiClient.post('/api/batch/full-pipeline');
    return response.data;
  },

  // Vérifier statut job
  getJobStatus: async (jobId) => {
    const response = await apiClient.get(`/api/batch/status/${jobId}`);
    return response.data;
  },

  // Health check
  checkHealth: async () => {
    const response = await axios.get(`${API_BASE_URL}/actuator/health`);
    return response.data;
  }
};
```

### Composant React Exemple

```jsx
// src/components/Dashboard.jsx
import { useState, useEffect } from 'react';
import { articlesAPI, batchAPI } from '../api/articles';

export default function Dashboard() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadStats();
  }, []);

  const loadStats = async () => {
    try {
      const data = await articlesAPI.getStats();
      setStats(data);
    } catch (error) {
      console.error('Erreur chargement stats:', error);
    } finally {
      setLoading(false);
    }
  };

  const runPipeline = async () => {
    try {
      setLoading(true);
      const result = await batchAPI.runFullPipeline();
      console.log('Job lancé:', result.jobExecutionId);
      
      // Poll status
      const interval = setInterval(async () => {
        const status = await batchAPI.getJobStatus(result.jobExecutionId);
        if (status.status === 'COMPLETED' || status.status === 'FAILED') {
          clearInterval(interval);
          await loadStats(); // Recharger les stats
        }
      }, 5000);
    } catch (error) {
      console.error('Erreur pipeline:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <div>Chargement...</div>;

  return (
    <div>
      <h1>Tableau de Bord</h1>
      
      {stats && (
        <div>
          <h2>Statistiques</h2>
          <p>Total Articles: {stats.totalArticles}</p>
          <p>Par Année: {JSON.stringify(stats.byYear)}</p>
        </div>
      )}

      <button onClick={runPipeline} disabled={loading}>
        {loading ? 'En cours...' : 'Lancer Pipeline Complet'}
      </button>
    </div>
  );
}
```

### Composant pour Afficher les Fichiers

```jsx
// src/components/FileBrowser.jsx
import { useState, useEffect } from 'react';
import { filesAPI } from '../api/articles';

export default function FileBrowser() {
  const [fileStats, setFileStats] = useState(null);
  const [pdfs, setPdfs] = useState([]);
  const [selectedOcr, setSelectedOcr] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadFiles();
  }, []);

  const loadFiles = async () => {
    try {
      const [stats, pdfList] = await Promise.all([
        filesAPI.getStats(),
        filesAPI.listPdfs()
      ]);
      setFileStats(stats);
      setPdfs(pdfList.files);
    } catch (error) {
      console.error('Erreur chargement fichiers:', error);
    } finally {
      setLoading(false);
    }
  };

  const viewOcrContent = async (filename) => {
    try {
      const content = await filesAPI.readOcrContent(filename);
      setSelectedOcr(content);
    } catch (error) {
      console.error('Erreur lecture OCR:', error);
    }
  };

  if (loading) return <div>Chargement...</div>;

  return (
    <div>
      <h1>Navigateur de Fichiers</h1>

      {fileStats && (
        <div>
          <h2>Statistiques</h2>
          <ul>
            <li>PDFs: {fileStats.pdfs.count} ({fileStats.pdfs.totalSizeMB})</li>
            <li>OCR: {fileStats.ocr.count} ({fileStats.ocr.totalSizeMB})</li>
            <li>Articles JSON: {fileStats.articles.count} ({fileStats.articles.totalSizeMB})</li>
          </ul>
        </div>
      )}

      <h2>PDFs Disponibles</h2>
      <table>
        <thead>
          <tr>
            <th>Fichier</th>
            <th>Taille</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {pdfs.map((pdf) => (
            <tr key={pdf.filename}>
              <td>{pdf.filename}</td>
              <td>{pdf.sizeMB}</td>
              <td>
                <a href={filesAPI.downloadPdf(pdf.filename)} 
                   target="_blank" 
                   rel="noopener noreferrer">
                  Télécharger PDF
                </a>
                {' | '}
                <button onClick={() => viewOcrContent(pdf.filename.replace('.pdf', '.txt'))}>
                  Voir OCR
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {selectedOcr && (
        <div style={{ marginTop: '20px', padding: '10px', border: '1px solid #ccc' }}>
          <h3>Contenu OCR: {selectedOcr.filename}</h3>
          <p><strong>Taille:</strong> {selectedOcr.size} bytes | <strong>Lignes:</strong> {selectedOcr.lines}</p>
          <pre style={{ whiteSpace: 'pre-wrap', maxHeight: '400px', overflow: 'auto' }}>
            {selectedOcr.content}
          </pre>
          <button onClick={() => setSelectedOcr(null)}>Fermer</button>
        </div>
      )}
      
      <div className="stats">
        <h2>Statistiques</h2>
        <p>Total articles: {stats?.totalArticles}</p>
        <p>Documents 2025: {stats?.byYear['2025']}</p>
      </div>

      <button onClick={runPipeline}>
        Lancer Pipeline Complet
      </button>
    </div>
  );
}
```

---

## 🔧 Configuration CORS

Pour permettre à votre frontend React d'accéder aux APIs, CORS est déjà configuré dans `SecurityConfig.java`.

Les origines autorisées :
- `http://localhost:3000` (dev local)
- `http://localhost:5173` (Vite dev)
- Votre domaine de production

---

## 📝 Variables d'Environnement React (.env)

```env
# Développement local
REACT_APP_API_URL=http://localhost:8080
REACT_APP_API_USERNAME=admin
REACT_APP_API_PASSWORD=your-local-password

# Production (Render)
# REACT_APP_API_URL=https://your-app.onrender.com
# REACT_APP_API_USERNAME=admin
# REACT_APP_API_PASSWORD=your-production-password
```

---

## 🚀 Déploiement sur Render

Suivez le guide complet dans `DEPLOY_RENDER.md` :

1. ✅ Créer service MySQL
2. ✅ Créer Web Service (Spring Boot)
3. ✅ Configurer variables d'environnement
4. ✅ Déployer
5. ✅ Tester les endpoints
6. ✅ Connecter votre frontend React

---

## 📚 Ressources

- [Documentation Spring Security](https://docs.spring.io/spring-security/reference/index.html)
- [Documentation Spring Batch](https://docs.spring.io/spring-batch/reference/index.html)
- [Render Documentation](https://render.com/docs)
- [Axios Documentation](https://axios-http.com/docs/intro)
