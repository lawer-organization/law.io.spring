# 📁 Endpoints Fichiers - Documentation

## Vue d'ensemble

Les nouveaux endpoints permettent d'accéder directement aux ressources stockées sur le disque :
- **PDFs** : Documents originaux téléchargés
- **OCR** : Textes extraits des PDFs par Tesseract
- **Articles JSON** : Articles structurés extraits et parsés

Tous les endpoints nécessitent **HTTP Basic Authentication** sauf `/actuator/health`.

---

## 🎯 Cas d'Usage

### 1. Dashboard de Monitoring
```javascript
// Afficher les statistiques des fichiers
const stats = await filesAPI.getStats();
console.log(`PDFs: ${stats.pdfs.count}, OCR: ${stats.ocr.count}`);
```

### 2. Visualiseur de Documents
```javascript
// Lister et afficher les PDFs
const pdfs = await filesAPI.listPdfs();
pdfs.files.forEach(pdf => {
  console.log(`${pdf.filename} - ${pdf.sizeMB}`);
});
```

### 3. Recherche Texte (Full-Text Search)
```javascript
// Charger tous les OCR et chercher un mot-clé
const ocrFiles = await filesAPI.listOcr();
for (const file of ocrFiles.files) {
  const content = await filesAPI.readOcrContent(file.filename);
  if (content.content.includes('nationalité')) {
    console.log(`Trouvé dans ${file.filename}`);
  }
}
```

### 4. Prévisualisation Document
```javascript
// Afficher le texte OCR avant téléchargement PDF
const ocr = await filesAPI.readOcrContent('loi-2025-11.txt');
console.log(`Document: ${ocr.filename}`);
console.log(`Lignes: ${ocr.lines}, Taille: ${ocr.size} bytes`);
console.log(ocr.content.substring(0, 500)); // Prévisualisation
```

### 5. Export Batch
```javascript
// Télécharger tous les PDFs d'une année
const pdfs = await filesAPI.listPdfs();
const pdfs2025 = pdfs.files.filter(f => f.filename.includes('2025'));

pdfs2025.forEach(pdf => {
  window.open(filesAPI.downloadPdf(pdf.filename), '_blank');
});
```

---

## 🔗 Endpoints Disponibles

### 📊 Statistiques

#### `GET /api/files/stats`
Statistiques globales de tous les types de fichiers.

**Réponse:**
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

### 📄 Listes de Fichiers

#### `GET /api/files/pdfs`
Liste tous les PDFs avec métadonnées (nom, taille, date modification).

#### `GET /api/files/ocr`
Liste tous les fichiers OCR (.txt).

#### `GET /api/files/articles`
Liste tous les fichiers JSON d'articles.

**Format de réponse (même pour les 3):**
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

### ⬇️ Téléchargement de Fichiers

#### `GET /api/files/pdfs/{filename}`
Télécharge un PDF spécifique.

**Exemple:**
```bash
curl -u admin:password -O http://localhost:8080/api/files/pdfs/loi-2025-11.pdf
```

**Headers de réponse:**
```
Content-Type: application/pdf
Content-Disposition: attachment; filename="loi-2025-11.pdf"
```

#### `GET /api/files/ocr/{filename}`
Télécharge un fichier OCR (.txt).

**Exemple:**
```bash
curl -u admin:password -O http://localhost:8080/api/files/ocr/loi-2025-11.txt
```

#### `GET /api/files/articles/{filename}`
Télécharge un fichier JSON d'articles.

**Exemple:**
```bash
curl -u admin:password -O http://localhost:8080/api/files/articles/loi-2025-11.json
```

---

### 📖 Lecture de Contenu (sans téléchargement)

#### `GET /api/files/ocr/{filename}/content`
Lit le contenu d'un fichier OCR directement en JSON.

**Réponse:**
```json
{
  "filename": "loi-2025-11.txt",
  "content": "RÉPUBLIQUE DU BÉNIN\nFraternité-Justice-Travail...",
  "size": 3255,
  "lines": 87
}
```

**Utilité:** Afficher le texte dans une interface web sans télécharger le fichier.

#### `GET /api/files/articles/{filename}/content`
Lit le contenu JSON d'un fichier d'articles.

**Réponse:**
```json
[
  {
    "documentId": "loi-2025-11",
    "articleIndex": 1,
    "title": "loi-2025-11 article-1",
    "content": "Article 1er : Sont modifiées...",
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
  },
  {
    "documentId": "loi-2025-11",
    "articleIndex": 2,
    "title": "loi-2025-11 article-2",
    "content": "Article 2: La présente loi...",
    ...
  }
]
```

---

## 🔐 Sécurité

### Validation de Nom de Fichier
Le contrôleur valide les noms de fichiers pour éviter les attaques path traversal :

```java
if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
}
```

❌ **Bloqué:**
- `../../../etc/passwd`
- `loi-2025/../../../secrets.txt`
- `C:\Windows\System32\config.sys`

✅ **Autorisé:**
- `loi-2025-11.pdf`
- `loi-2024-31.txt`
- `loi-2025-11.json`

### Authentification
Tous les endpoints nécessitent HTTP Basic Auth configuré dans Spring Security.

**Exception:** `/actuator/health` est public (sans auth).

---

## 📊 Performance

### Cache Navigateur
Les fichiers sont servis avec headers appropriés pour le cache :

```
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
```

### Compression
Pour les fichiers volumineux, activer la compression GZIP dans `application.yml` :

```yaml
server:
  compression:
    enabled: true
    mime-types: application/json,text/plain,application/pdf
    min-response-size: 1024
```

### Streaming
Les fichiers sont servis via `FileSystemResource` qui supporte le streaming automatique.

---

## 🧪 Tests

### Test cURL

```bash
# Statistiques
curl -u admin:test123 http://localhost:8080/api/files/stats | jq

# Liste PDFs
curl -u admin:test123 http://localhost:8080/api/files/pdfs | jq '.count'

# Télécharger PDF
curl -u admin:test123 -O http://localhost:8080/api/files/pdfs/loi-2025-11.pdf

# Lire contenu OCR
curl -u admin:test123 http://localhost:8080/api/files/ocr/loi-2025-11.txt/content | jq '.lines'

# Lire JSON articles
curl -u admin:test123 http://localhost:8080/api/files/articles/loi-2025-11.json/content | jq '.[0].title'
```

### Test HTTP Client (JavaScript)

```javascript
import { filesAPI } from './api/articles';

// Test complet
async function testFileEndpoints() {
  try {
    // 1. Statistiques
    const stats = await filesAPI.getStats();
    console.log('Stats:', stats);

    // 2. Liste PDFs
    const pdfs = await filesAPI.listPdfs();
    console.log('PDFs:', pdfs.count);

    // 3. Lire OCR
    const firstPdf = pdfs.files[0];
    const ocrFilename = firstPdf.filename.replace('.pdf', '.txt');
    const ocr = await filesAPI.readOcrContent(ocrFilename);
    console.log('OCR Lines:', ocr.lines);

    // 4. Lire JSON articles
    const articleFilename = firstPdf.filename.replace('.pdf', '.json');
    const articles = await filesAPI.readArticleContent(articleFilename);
    console.log('Articles:', articles.length);

  } catch (error) {
    console.error('Test failed:', error);
  }
}

testFileEndpoints();
```

---

## 🚀 Intégration React

### Composant FileViewer

```jsx
import { useState, useEffect } from 'react';
import { filesAPI } from '../api/articles';

export default function FileViewer() {
  const [files, setFiles] = useState({ pdfs: [], ocr: [], articles: [] });
  const [stats, setStats] = useState(null);
  const [selectedContent, setSelectedContent] = useState(null);

  useEffect(() => {
    loadAllFiles();
  }, []);

  const loadAllFiles = async () => {
    const [pdfList, ocrList, articleList, fileStats] = await Promise.all([
      filesAPI.listPdfs(),
      filesAPI.listOcr(),
      filesAPI.listArticles(),
      filesAPI.getStats()
    ]);

    setFiles({
      pdfs: pdfList.files,
      ocr: ocrList.files,
      articles: articleList.files
    });
    setStats(fileStats);
  };

  const viewFile = async (type, filename) => {
    if (type === 'ocr') {
      const content = await filesAPI.readOcrContent(filename);
      setSelectedContent({ type: 'ocr', data: content });
    } else if (type === 'article') {
      const content = await filesAPI.readArticleContent(filename);
      setSelectedContent({ type: 'article', data: content });
    }
  };

  return (
    <div>
      <h1>Gestionnaire de Fichiers</h1>

      {stats && (
        <div className="stats">
          <div>PDFs: {stats.pdfs.count} ({stats.pdfs.totalSizeMB})</div>
          <div>OCR: {stats.ocr.count} ({stats.ocr.totalSizeMB})</div>
          <div>Articles: {stats.articles.count} ({stats.articles.totalSizeMB})</div>
        </div>
      )}

      <div className="file-lists">
        <div>
          <h2>PDFs</h2>
          <ul>
            {files.pdfs.map(file => (
              <li key={file.filename}>
                {file.filename} - {file.sizeMB}
                <button onClick={() => window.open(filesAPI.downloadPdf(file.filename))}>
                  Télécharger
                </button>
              </li>
            ))}
          </ul>
        </div>

        <div>
          <h2>OCR</h2>
          <ul>
            {files.ocr.map(file => (
              <li key={file.filename}>
                {file.filename}
                <button onClick={() => viewFile('ocr', file.filename)}>
                  Voir
                </button>
              </li>
            ))}
          </ul>
        </div>

        <div>
          <h2>Articles JSON</h2>
          <ul>
            {files.articles.map(file => (
              <li key={file.filename}>
                {file.filename} - {file.sizeMB}
                <button onClick={() => viewFile('article', file.filename)}>
                  Voir
                </button>
              </li>
            ))}
          </ul>
        </div>
      </div>

      {selectedContent && (
        <div className="content-viewer">
          <h3>Contenu: {selectedContent.data.filename || 'Articles'}</h3>
          {selectedContent.type === 'ocr' ? (
            <pre>{selectedContent.data.content}</pre>
          ) : (
            <pre>{JSON.stringify(selectedContent.data, null, 2)}</pre>
          )}
          <button onClick={() => setSelectedContent(null)}>Fermer</button>
        </div>
      )}
    </div>
  );
}
```

---

## 📝 Notes Importantes

1. **Permissions** : L'utilisateur Docker (`appuser:1001`) doit avoir accès en lecture aux dossiers `/app/data/*`
2. **Volumes** : Les dossiers doivent être montés correctement dans `docker-compose.yml`
3. **CORS** : Les endpoints sont couverts par la config CORS globale dans `SecurityConfig.java`
4. **Taille** : Attention aux fichiers volumineux (certains PDFs font 36MB+)
5. **Encoding** : Les fichiers OCR sont en UTF-8, attention aux caractères spéciaux

---

## 🔄 Workflow Complet

```
1. Lancer Pipeline
   POST /api/batch/full-pipeline
   ↓
2. Vérifier Progression
   GET /api/batch/status/{jobId}
   ↓
3. Consulter Statistiques
   GET /api/files/stats
   ↓
4. Lister Fichiers
   GET /api/files/pdfs
   GET /api/files/ocr
   GET /api/files/articles
   ↓
5. Visualiser Contenu
   GET /api/files/ocr/{filename}/content
   GET /api/files/articles/{filename}/content
   ↓
6. Télécharger si Nécessaire
   GET /api/files/pdfs/{filename}
```

---

## 🎨 Exemple UI (HTML/CSS)

```html
<!DOCTYPE html>
<html>
<head>
  <title>Law File Viewer</title>
  <style>
    .stats { display: flex; gap: 20px; margin: 20px 0; }
    .stat-card { padding: 15px; border: 1px solid #ccc; border-radius: 5px; }
    .file-list { max-height: 400px; overflow-y: auto; }
    .file-item { padding: 10px; border-bottom: 1px solid #eee; }
    .content-viewer { margin-top: 20px; padding: 20px; background: #f5f5f5; }
    pre { white-space: pre-wrap; max-height: 500px; overflow: auto; }
  </style>
</head>
<body>
  <h1>📁 Law File Viewer</h1>
  
  <div class="stats" id="stats"></div>
  <div class="file-list" id="fileList"></div>
  <div class="content-viewer" id="contentViewer"></div>

  <script>
    const API_URL = 'http://localhost:8080';
    const AUTH = 'Basic ' + btoa('admin:test123');

    async function loadStats() {
      const response = await fetch(`${API_URL}/api/files/stats`, {
        headers: { 'Authorization': AUTH }
      });
      const stats = await response.json();
      
      document.getElementById('stats').innerHTML = `
        <div class="stat-card">
          <h3>PDFs</h3>
          <p>${stats.pdfs.count} fichiers</p>
          <p>${stats.pdfs.totalSizeMB}</p>
        </div>
        <div class="stat-card">
          <h3>OCR</h3>
          <p>${stats.ocr.count} fichiers</p>
          <p>${stats.ocr.totalSizeMB}</p>
        </div>
        <div class="stat-card">
          <h3>Articles JSON</h3>
          <p>${stats.articles.count} fichiers</p>
          <p>${stats.articles.totalSizeMB}</p>
        </div>
      `;
    }

    async function loadFiles() {
      const response = await fetch(`${API_URL}/api/files/pdfs`, {
        headers: { 'Authorization': AUTH }
      });
      const data = await response.json();
      
      document.getElementById('fileList').innerHTML = data.files.map(file => `
        <div class="file-item">
          <strong>${file.filename}</strong> - ${file.sizeMB}
          <button onclick="viewOcr('${file.filename.replace('.pdf', '.txt')}')">Voir OCR</button>
          <a href="${API_URL}/api/files/pdfs/${file.filename}" download>Télécharger PDF</a>
        </div>
      `).join('');
    }

    async function viewOcr(filename) {
      const response = await fetch(`${API_URL}/api/files/ocr/${filename}/content`, {
        headers: { 'Authorization': AUTH }
      });
      const data = await response.json();
      
      document.getElementById('contentViewer').innerHTML = `
        <h3>${data.filename}</h3>
        <p>Taille: ${data.size} bytes | Lignes: ${data.lines}</p>
        <pre>${data.content}</pre>
      `;
    }

    loadStats();
    loadFiles();
  </script>
</body>
</html>
```

---

✅ **Endpoints prêts pour production** - Documentés et testés !
