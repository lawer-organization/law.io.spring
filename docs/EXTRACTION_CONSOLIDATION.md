# Architecture d'Extraction et Consolidation des Articles

## Vue d'ensemble

Le système d'extraction des articles a été restructuré pour séparer les préoccupations entre :
1. **Extraction** : Export des articles au format JSON sur disque
2. **Consolidation** : Import des fichiers JSON vers la base de données

Cette séparation offre plusieurs avantages :
- Fichiers JSON comme format intermédiaire auditable
- Possibilité de re-consolider sans refaire l'extraction coûteuse (OCR)
- Traitement indépendant des deux phases
- Facilite le traitement externe des données JSON

## Architecture des Jobs

### 1. Job d'Extraction (`articleExtractionJob`)

**Objectif** : Extraire les articles depuis les fichiers OCR et les exporter en JSON

**Composants** :
- **Reader** : `OcrFileReader`
  - Lit les documents avec statut EXTRACTED dans `fetch_results`
  - Scanne les fichiers OCR disponibles dans `data/ocr/{type}/`
  
- **Processor** : `ArticleExtractionProcessor`
  - Vérifie si le JSON existe déjà
  - Lit le texte OCR depuis `data/ocr/{type}/{documentId}.txt`
  - Extrait les articles avec `ArticleExtractorService`
  - Extrait les métadonnées (titre, date, signataires)
  - Calcule le score de confiance
  - Crée les objets `ArticleExtraction` avec toutes les métadonnées
  - **Export JSON** : Écrit un fichier JSON par document
    - Chemin : `data/articles/{type}/{documentId}.json`
    - Format : Array d'articles avec métadonnées complètes
    - Pretty-printed pour lisibilité
  
- **Writer** : `ArticleExtractionWriter`
  - Met à jour le statut à `EXTRACTED` dans `fetch_results`
  - Affiche un résumé (nombre de documents traités)

**Exemple de fichier JSON généré** :
```json
[
  {
    "documentId": "loi-2025-15",
    "articleIndex": 1,
    "title": "loi-2025-15 article-1",
    "content": "La présente loi...",
    "confidence": 96.5,
    "documentType": "loi",
    "documentYear": 2025,
    "documentNumber": 15,
    "sourceUrl": "https://sgg.gouv.bj/doc/loi-2025-15/download",
    "lawTitle": "Loi n° 2025-15 portant...",
    "promulgationDate": "2025-01-15",
    "promulgationCity": "Cotonou",
    "signatories": "[{\"name\":\"...\",\"title\":\"...\"}]",
    "extractedAt": "2025-01-20T10:30:00"
  },
  ...
]
```

**Commande** : `./extract-articles.sh` ou `POST /api/batch/extract`

---

### 2. Job de Consolidation (`consolidateJob`)

**Objectif** : Importer les fichiers JSON d'articles vers la base de données

**Composants** :
- **Reader** : `ConsolidationReader`
  - Scanne les répertoires `data/articles/{type}/` pour les fichiers `.json`
  - Crée des objets `LawDocument` pour chaque fichier trouvé
  - Vérifie l'existence dans `fetch_results` pour obtenir les métadonnées
  
- **Processor** : `ConsolidationProcessor`
  - Lit le fichier JSON depuis le disque
  - Désérialise avec Gson en `List<ArticleExtraction>`
  - Gère les dates avec un adaptateur LocalDate
  - Retourne une liste vide si le fichier n'existe pas (skip silencieux)
  
- **Writer** : `ConsolidationWriter`
  - Supprime les anciennes extractions du document (si existantes)
  - Sauvegarde les nouvelles extractions dans `article_extractions`
  - Met à jour le statut à `CONSOLIDATED` dans `fetch_results`
  - Affiche un résumé (nombre d'articles et documents consolidés)

**Commande** : `./consolidate.sh` ou `POST /api/batch/consolidate`

---

## Flux de Traitement Complet

```
┌─────────────────┐
│   Fetch Job     │  → Récupère les métadonnées (URL, année, numéro)
│                 │     Statut: FETCHED
└────────┬────────┘
         ↓
┌─────────────────┐
│  Download Job   │  → Télécharge les PDFs
│                 │     Statut: DOWNLOADED
└────────┬────────┘
         ↓
┌─────────────────┐
│    OCR Job      │  → Extrait le texte via Tesseract
│                 │     Sauvegarde: data/ocr/{type}/{id}.txt
│                 │     Statut: EXTRACTED
└────────┬────────┘
         ↓
┌─────────────────┐
│  Extract Job    │  → Extrait les articles depuis OCR
│                 │     Export: data/articles/{type}/{id}.json
│                 │     Statut: EXTRACTED (maintenu)
└────────┬────────┘
         ↓
┌─────────────────┐
│ Consolidate Job │  → Importe les JSON en base
│                 │     Table: article_extractions
│                 │     Statut: CONSOLIDATED
└─────────────────┘
```

## Statuts des Documents

| Statut          | Description                                          |
|-----------------|------------------------------------------------------|
| `FETCHED`       | Métadonnées récupérées, URL connue                  |
| `DOWNLOADED`    | PDF téléchargé et stocké                             |
| `EXTRACTED`     | Texte OCR extrait + Articles exportés en JSON        |
| `CONSOLIDATED`  | Articles importés dans article_extractions           |

## Structure des Données

### Répertoires
```
data/
├── pdfs/
│   ├── loi/           → PDFs des lois
│   └── decret/        → PDFs des décrets
├── ocr/
│   ├── loi/           → Textes OCR des lois
│   └── decret/        → Textes OCR des décrets
└── articles/
    ├── loi/           → JSON des articles de lois
    └── decret/        → JSON des articles de décrets
```

### Tables de Base de Données

**fetch_results** : Suivi des documents
- `documentId`, `documentType`, `year`, `number`, `url`
- `status`: FETCHED → DOWNLOADED → EXTRACTED → CONSOLIDATED

**article_extractions** : Articles extraits
- `documentId`, `articleIndex`, `title`, `content`, `confidence`
- `documentType`, `documentYear`, `documentNumber`, `sourceUrl`
- `lawTitle`, `promulgationDate`, `promulgationCity`, `signatories`
- `extractedAt`

## Scripts Disponibles

| Script                 | Endpoint                  | Description                                |
|------------------------|---------------------------|--------------------------------------------|
| `fetch-current.sh`     | `/api/batch/fetch-current`| Récupère les documents de l'année en cours |
| `fetch-previous.sh`    | `/api/batch/fetch-previous`| Récupère les documents des années passées |
| `download.sh`          | `/api/batch/download`     | Télécharge les PDFs                        |
| `ocr.sh`               | `/api/batch/ocr`          | Extrait le texte OCR des PDFs              |
| `extract-articles.sh`  | `/api/batch/extract`      | Extrait les articles et exporte en JSON    |
| `consolidate.sh`       | `/api/batch/consolidate`  | Consolide les JSON en base de données      |

## Cas d'Usage

### 1. Traitement Initial Complet
```bash
./fetch-current.sh      # Récupère les documents
./download.sh           # Télécharge les PDFs
./ocr.sh                # Extrait le texte
./extract-articles.sh   # Exporte les articles en JSON
./consolidate.sh        # Importe en base
```

### 2. Re-consolidation (sans refaire l'extraction)
Si vous avez modifié la structure de la base ou voulez réimporter :
```bash
./consolidate.sh        # Réimporte les JSON existants
```

### 3. Traitement Externe des JSON
Les fichiers JSON peuvent être traités par des outils externes :
```bash
# Exemple : analyser les articles avec jq
cat data/articles/loi/loi-2025-15.json | jq '.[] | {title, confidence}'

# Exemple : copier vers un autre système
rsync -av data/articles/ backup@server:/archives/
```

### 4. Pipeline Complet pour un Document Spécifique
```bash
curl "http://localhost:8080/api/batch/full-pipeline?documentId=loi-2025-15"
./consolidate.sh        # Puis consolider
```

## Maintenance

### Nettoyer et Reconsolider
```bash
# Supprimer les entrées en base
mysql -u root law_batch -e "DELETE FROM article_extractions WHERE documentId = 'loi-2025-15'"

# Reconsolider depuis les JSON
./consolidate.sh
```

### Vérifier l'État des Fichiers
```bash
# Compter les fichiers JSON
find data/articles -name "*.json" | wc -l

# Compter les documents consolidés
mysql -u root law_batch -e "SELECT COUNT(DISTINCT documentId) FROM article_extractions"

# Vérifier les statuts
mysql -u root law_batch -e "SELECT status, COUNT(*) FROM fetch_results GROUP BY status"
```

## Avantages de l'Architecture

1. **Séparation des préoccupations**
   - Extraction (coûteuse) séparée de la consolidation (rapide)
   - Permet de re-consolider sans refaire l'OCR

2. **Auditabilité**
   - Fichiers JSON comme source de vérité intermédiaire
   - Facilite le debugging et la vérification des données

3. **Flexibilité**
   - Traitement externe possible des JSON
   - Import/export facilité vers d'autres systèmes

4. **Performance**
   - Possibilité de re-consolider rapidement
   - Pas besoin de refaire l'OCR coûteux

5. **Maintenabilité**
   - Code plus simple et modulaire
   - Tests unitaires plus faciles
   - Évolution indépendante des deux phases
