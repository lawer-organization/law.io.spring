# Architecture Spring Batch - Law Spring Batch

## Vue d'ensemble

Ce document décrit l'architecture des processus Spring Batch pour le traitement des documents légaux du Bénin.

## Architecture globale

```
┌─────────────────────────────────────────────────────────────────────┐
│                         SPRING BATCH JOBS                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌────────────┐    ┌──────────────┐    ┌────────────────┐           │
│  │ Fetch Job  │    │ Download Job │    │ Extract Job    │           │
│  └────────────┘    └──────────────┘    └────────────────┘           │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │              Full Pipeline Job                               │   │
│  │  (Fetch → Download → Extract)                                │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## 1. Fetch Job - Vérification de l'existence des documents

### Objectif
Vérifier l'existence des documents légaux sur le site SGG et enregistrer les résultats. Le job est divisé en deux steps distincts : année courante (current) et années précédentes (previous).

### Architecture
```
┌─────────────────────────────────────────────────────────────────┐
│                         FETCH JOB                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              FETCH CURRENT STEP                          │  │
│  │  (Année courante - Scan complet)                         │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          │                                       │
│                          ▼                                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              FETCH PREVIOUS STEP                         │  │
│  │  (1960 → année-1 - Scan incrémental)                     │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 1.1. Fetch Current Step - Année courante

#### Objectif
Scanner **tous** les documents de l'année en cours (1-2000) à chaque exécution, car de nouveaux documents peuvent être publiés.

#### Architecture
```
┌─────────────────────────────────────────────────────────────────┐
│                    FETCH CURRENT STEP                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌────────────────────┐   ┌─────────────────┐   ┌───────────┐ │
│  │CurrentYearReader   │──▶│ FetchProcessor  │──▶│TrackingWriter│ │
│  └────────────────────┘   └─────────────────┘   └───────────┘ │
│         │                       │                      │        │
│         │                       │                      │        │
│         ▼                       ▼                      ▼        │
│   Génère URLs            Vérifie existence      Sauvegarde BD  │
│   année = CURRENT        via HTTP HEAD          (fetch_results)│
│   numéros 1-2000         Code 200 = exists                     │
│   Ignore BD cache        Gère redirections                     │
│   Padding: 01-09         Try avec/sans padding                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

Comportement:
- Année: Calendar.getInstance().get(YEAR) (dynamique)
- Numéros: TOUJOURS 1 à 2000
- Padding: Pour 1-9, essayer "01" à "09" (2 caractères)
- Cache: IGNORE la BD (rescan complet)
- Raison: Nouveaux documents peuvent apparaître
```

#### Reader: `CurrentYearLawDocumentReader`
- **Type**: ItemReader<LawDocument>
- **Rôle**: Génère les URLs de l'année courante
- **Configuration**:
  - Types: loi, decret
  - Année: `Calendar.getInstance().get(Calendar.YEAR)` (dynamique)
  - Numéros: 1-2000 (fixe)
- **Logique padding**:
  - Pour numéros 1-9 : génère 2 variants
    - `loi-2025-1` et `loi-2025-01`
    - `decret-2025-5` et `decret-2025-05`
  - Pour numéros 10-2000 : génère 1 URL
    - `loi-2025-42`
- **Cache**: Ignore complètement `fetch_results`
- **Output**: Stream de `LawDocument` avec URLs

### 1.2. Fetch Previous Step - Années précédentes

#### Objectif
Scanner intelligemment les années 1960 à (année courante - 1) en évitant les URLs déjà connues ou not found.

#### Architecture
```
┌─────────────────────────────────────────────────────────────────┐
│                   FETCH PREVIOUS STEP                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌────────────────────┐   ┌─────────────────┐   ┌───────────┐ │
│  │PreviousYearsReader │──▶│ FetchProcessor  │──▶│TrackingWriter│ │
│  └────────────────────┘   └─────────────────┘   └───────────┘ │
│         │                       │                      │        │
│         │                       │                      │        │
│         ▼                       ▼                      ▼        │
│   Génère URLs            Vérifie existence      Sauvegarde BD  │
│   années: 1960 à N-1     via HTTP HEAD          (fetch_results)│
│   numéros: 1-2000        Code 200 = exists                     │
│   Check BD cache         Gère redirections                     │
│   Skip exists/notfound   Try avec/sans padding                 │
│   Padding: 01-09                                                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

Comportement:
- Années: 1960 à (CURRENT_YEAR - 1)
- Numéros: 1 à 2000
- Padding: Pour 1-9, essayer "01" à "09" (2 caractères)
- Cache: UTILISE fetch_results
  - Skip si exists=true (déjà trouvé)
  - Skip si exists=false (404 confirmé)
- Raison: Optimisation, pas de nouveaux docs
```

#### Reader: `PreviousYearsLawDocumentReader`
- **Type**: ItemReader<LawDocument>
- **Rôle**: Génère les URLs des années précédentes avec optimisation
- **Configuration**:
  - Types: loi, decret
  - Années: 1960 à `Calendar.getInstance().get(Calendar.YEAR) - 1`
  - Numéros: 1-2000
- **Logique padding**: Identique à CurrentYear
- **Cache optimisation**:
  1. Au démarrage, charge `fetch_results` en mémoire
  2. Pour chaque URL générée:
     - Check si `documentId` existe en BD
     - Si `exists=true` → SKIP (déjà téléchargé)
     - Si `exists=false` → SKIP (404 confirmé)
     - Si absent → GÉNÈRE (à vérifier)
- **Output**: Stream de `LawDocument` (URLs non vérifiées uniquement)

### Composants communs

#### Processor: `FetchProcessor`
- **Type**: ItemProcessor<LawDocument, LawDocument>
- **Rôle**: Vérifie l'existence du document via HTTP HEAD
- **Logique**:
  - Envoie requête HTTP HEAD
  - Gère redirections (max 5)
  - Détecte existence (code 200)
  - Retry sur erreurs temporaires
  - **Logique padding automatique** :
    - Si numéro 1-9 et URL sans padding → 404
    - Retry automatiquement avec padding (ex: 01, 02, etc.)
    - Si succès avec padding → marque comme found
    - Évite de générer 2 URLs distinctes
- **Output**: `LawDocument` avec status et exists

#### Writer: `TrackingWriter`
- **Type**: ItemWriter<LawDocument>
- **Rôle**: Enregistre les résultats en base
- **Logique**:
  - Vérifie unicité par documentId
  - Mise à jour si existe, création sinon
  - Sauvegarde batch pour performance
- **Target**: Table `fetch_results` (MySQL)

### Configuration du Fetch Job

```yaml
Chunk size: 10
Max threads: 4
Transaction: Géré par Spring Batch

Fetch Current:
  - Année: Dynamique (Calendar.getInstance())
  - Range: 1-2000
  - Cache: Désactivé
  - Padding: Auto-retry sur 1-9

Fetch Previous:
  - Années: 1960 à (current-1)
  - Range: 1-2000
  - Cache: Activé (fetch_results)
  - Padding: Auto-retry sur 1-9
  - Skip: exists=true OU exists=false
```

### Exemple de génération d'URLs avec padding

#### Pour numéro 1-9 (avec auto-retry dans FetchProcessor):
```
Reader génère:
- loi-2025-1   → FetchProcessor essaie
  - Si 404 → Retry avec loi-2025-01
  - Si 200 → OK
  
- decret-2024-5 → FetchProcessor essaie
  - Si 404 → Retry avec decret-2024-05
  - Si 200 → OK
```

#### Pour numéro 10-2000:
```
Reader génère:
- loi-2025-42
- decret-2023-123
(Pas de variant padding)
```

### Flux de décision - Fetch Previous Step

```
┌─────────────────────┐
│ Generate URL        │
│ (type-year-number)  │
└──────────┬──────────┘
           │
           ▼
   ┌───────────────┐
   │ Check BD      │
   │ fetch_results │
   └───────┬───────┘
           │
     ┌─────┴─────┐
     │           │
     ▼           ▼
[Existe?]    [Absent?]
     │           │
     ▼           ▼
  ┌─────┐    ┌──────┐
  │Skip │    │Include│
  │     │    │in job│
  └─────┘    └──────┘
  
exists=true  → SKIP (déjà téléchargé)
exists=false → SKIP (404 confirmé)
absent       → INCLUDE (à vérifier)
```

---

## 2. Download Job - Téléchargement des PDFs

### Objectif
Télécharger les PDFs des documents trouvés et les stocker en base de données.

### Architecture
```
┌─────────────────────────────────────────────────────────────────┐
│                       DOWNLOAD JOB                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      DOWNLOAD STEP                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐   ┌─────────────────┐   ┌─────────────┐ │
│  │ LawDocumentReader│──▶│CompositeProcessor│──▶│TrackingWriter│ │
│  └──────────────────┘   └─────────────────┘   └─────────────┘ │
│         │                       │                      │        │
│         │              ┌────────┴────────┐             │        │
│         │              │                 │             │        │
│         ▼              ▼                 ▼             ▼        │
│   Génère URLs   FetchProcessor   DownloadProcessor  Sauvegarde │
│   (loi/decret)  (vérifie)        (télécharge)       BD MySQL  │
│                 exists=true?     → PDF bytes         LONGBLOB  │
│                                  → SHA-256                     │
│                                  → fileSize                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

Données stockées:
- documentId
- URL
- pdfContent (LONGBLOB)
- sha256 (checksum)
- fileSize (bytes)
- downloadedAt (timestamp)
```

### Composants

#### Reader: `LawDocumentReader`
- **Type**: ItemReader
- **Rôle**: Génère les URLs des documents
- **Configuration**: Identique au Fetch Job

#### Composite Processor: `FetchProcessor` + `DownloadProcessor`
**1. FetchProcessor**
- Vérifie existence du document
- Filtre: continue seulement si exists=true

**2. DownloadProcessor**
- **Type**: ItemProcessor<LawDocument, LawDocument>
- **Rôle**: Télécharge le PDF et calcule métadonnées
- **Logique**:
  - HTTP GET avec Apache HttpClient 5
  - Stockage en ByteArrayOutputStream (mémoire)
  - Calcul SHA-256 pour intégrité
  - Sauvegarde en LONGBLOB dans MySQL
  - Gestion retry sur erreurs réseau
- **Output**: `LawDocument` avec PDF téléchargé
- **Target**: Table `download_results` (MySQL)

#### Writer: `TrackingWriter`
- Enregistre les métadonnées de téléchargement
- Table: `fetch_results` (mise à jour status)

### Configuration
```yaml
Chunk size: 10
Max threads: 4
HTTP timeout: 30000ms
Max retries: 3
```

---

## 3. Extract Job - Extraction OCR et Articles

### Objectif
Extraire le texte via OCR et parser les articles des PDFs stockés en base.

### Architecture
```
┌─────────────────────────────────────────────────────────────────┐
│                        EXTRACT JOB                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       EXTRACT STEP                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐   ┌────────────────────┐   ┌─────────────┐  │
│  │FilePdfReader│──▶│ExtractionProcessor │──▶│TrackingWriter│  │
│  └──────────────┘   └────────────────────┘   └─────────────┘  │
│         │                     │                       │         │
│         │                     │                       │         │
│         ▼                     ▼                       ▼         │
│   Lit depuis BD      ┌───────┴────────┐      Sauvegarde BD    │
│   download_results   │                │      - ocr_results    │
│   (LONGBLOB)         ▼                ▼      - article_       │
│                 TesseractOcr  ArticleExtractor   extractions  │
│                 (byte[] → text) (regex parsing)               │
│                 - PDFBox direct                                │
│                 - OCR si qualité                               │
│                   insuffisante                                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

Données générées:

OCR Results:
- documentId
- pdfPath (virtuel)
- ocrText (LONGTEXT)
- textLength
- quality
- extractedAt

Article Extractions:
- documentId
- articleIndex
- title
- content (TEXT)
- confidence
- metadata (type, year, number, etc.)
- signatories (JSON)
- extractedAt
```

### Composants

#### Reader: `FilePdfReader`
- **Type**: ItemReader<LawDocument>
- **Rôle**: Lit les PDFs depuis la base de données
- **Source**: Table `download_results`
- **Logique**:
  - Query: findAll() sur DownloadResultRepository
  - Parse documentId pour extraire métadonnées
  - Construit LawDocument avec référence BD
- **Output**: Stream de `LawDocument`

#### Processor: `ExtractionProcessor`
- **Type**: ItemProcessor<LawDocument, LawDocument>
- **Rôle**: Extrait texte OCR et parse les articles
- **Dépendances**:
  - `TesseractOcrService`: Extraction OCR
  - `ArticleExtractorService`: Parsing articles
  - `DownloadResultRepository`: Lecture PDF bytes
  - `OcrResultRepository`: Sauvegarde OCR
  - `ArticleExtractionRepository`: Sauvegarde articles

**Workflow**:
1. **Vérification cache OCR**
   - Check si OCR déjà fait (ocrResultRepository)
   - Si existe, utilise texte cached

2. **Extraction OCR** (si pas en cache)
   - Récupère pdfContent (byte[]) depuis BD
   - TesseractOcrService.extractText(byte[])
   - Processus:
     - Essai extraction directe PDFBox
     - Calcul qualité (ratio caractères valides)
     - Si qualité < seuil (0.70), OCR Tesseract
     - Tesseract: PDF → Images → OCR par page
   - Sauvegarde dans `ocr_results`

3. **Extraction articles**
   - Parse texte OCR avec regex patterns
   - Extraction métadonnées:
     - Titre de la loi
     - Date de promulgation
     - Lieu de promulgation
     - Signataires (JSON)
   - Parse chaque article (numéro + contenu)
   - Calcul confidence score
   - Sauvegarde dans `article_extractions`

#### Writer: `TrackingWriter`
- Enregistre le statut d'extraction
- Table: `fetch_results` (mise à jour status)

### Services

#### `TesseractOcrService`
```java
extractText(byte[] pdfBytes) throws IOException
- Loader.loadPDF(pdfBytes)
- PDFTextStripper (tentative directe)
- calculateTextQuality()
- extractWithOcr() si nécessaire
  - TessBaseAPI (try-with-resources)
  - Render pages à 300 DPI
  - OCR avec Tesseract fra.traineddata
```

#### `ArticleExtractorService`
```java
extractArticles(String ocrText, LawDocument doc)
- Patterns.properties (regex)
- Extract metadata
- Parse articles (Article 1er, Article 2, etc.)
- Build ArticleExtraction entities
- Gestion signataires (JSON)
```

### Configuration
```yaml
Chunk size: 10
Max threads: 4
OCR:
  language: fra
  dpi: 300
  quality-threshold: 0.70
```

---

## 4. Full Pipeline Job - Pipeline complet

### Objectif
Exécuter l'ensemble du processus: Fetch → Download → Extract en séquence.

### Architecture
```
┌─────────────────────────────────────────────────────────────────┐
│                    FULL PIPELINE JOB                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│         ┌──────────────┐                                        │
│    ┌───│  Fetch Step  │                                         │
│    │   └──────────────┘                                         │
│    │         │                                                  │
│    │         │ Vérifie existence                                │
│    │         │ Sauvegarde fetch_results                         │
│    │         │                                                  │
│    │         ▼                                                  │
│    │   ┌──────────────┐                                         │
│    └──▶│Download Step │                                         │
│        └──────────────┘                                         │
│              │                                                  │
│              │ Télécharge PDFs                                  │
│              │ Sauvegarde download_results (LONGBLOB)           │
│              │                                                  │
│              ▼                                                  │
│        ┌──────────────┐                                         │
│        │ Extract Step │                                         │
│        └──────────────┘                                         │
│              │                                                  │
│              │ OCR + Parse articles                             │
│              │ Sauvegarde ocr_results                           │
│              │ Sauvegarde article_extractions                   │
│              │                                                  │
│              ▼                                                  │
│          ┌────────┐                                             │
│          │ Succès │                                             │
│          └────────┘                                             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

Gestion d'erreur:
- Échec d'un step = arrêt pipeline
- Status sauvegardé dans Spring Batch metadata
- Logs détaillés par step
- Possibilité de restart depuis dernier step réussi
```

### Configuration
- **Steps**: fetchStep → downloadStep → extractStep
- **Incrementer**: RunIdIncrementer (nouveau run à chaque exécution)
- **Comportement**: Exécution séquentielle avec propagation d'erreur

---

## Base de données MySQL

### Tables applicatives

#### `fetch_results`
```sql
CREATE TABLE fetch_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id VARCHAR(50) UNIQUE NOT NULL,
    url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    document_exists BOOLEAN NOT NULL,
    fetched_at DATETIME NOT NULL,
    error_message TEXT,
    INDEX idx_fetch_document_id (document_id),
    INDEX idx_fetch_exists (document_exists)
);
```

#### `download_results`
```sql
CREATE TABLE download_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id VARCHAR(50) UNIQUE NOT NULL,
    url VARCHAR(500) NOT NULL,
    pdf_content LONGBLOB NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    file_size BIGINT NOT NULL,
    downloaded_at DATETIME NOT NULL,
    INDEX idx_download_document_id (document_id)
);
```

#### `ocr_results`
```sql
CREATE TABLE ocr_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id VARCHAR(50) UNIQUE NOT NULL,
    pdf_path VARCHAR(500),
    ocr_text LONGTEXT NOT NULL,
    text_length INT NOT NULL,
    quality DOUBLE,
    extracted_at DATETIME NOT NULL,
    INDEX idx_ocr_document_id (document_id)
);
```

#### `article_extractions`
```sql
CREATE TABLE article_extractions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id VARCHAR(50) NOT NULL,
    article_index INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    confidence DOUBLE NOT NULL,
    document_type VARCHAR(20),
    document_year INT,
    document_number INT,
    source_url VARCHAR(500),
    law_title VARCHAR(500),
    promulgation_date VARCHAR(50),
    promulgation_city VARCHAR(100),
    signatories JSON,
    extracted_at DATETIME NOT NULL,
    INDEX idx_article_document_id (document_id),
    INDEX idx_article_index (article_index),
    INDEX idx_article_extracted_at (extracted_at)
);
```

### Tables Spring Batch (auto-créées)
- `BATCH_JOB_INSTANCE`
- `BATCH_JOB_EXECUTION`
- `BATCH_JOB_EXECUTION_PARAMS`
- `BATCH_STEP_EXECUTION`
- `BATCH_JOB_EXECUTION_CONTEXT`
- `BATCH_STEP_EXECUTION_CONTEXT`

---

## API REST - Contrôle des jobs

### Endpoints

#### Lancer les jobs
```
GET /api/batch/fetch          - Lance le fetch job
GET /api/batch/download       - Lance le download job
GET /api/batch/extract        - Lance le extract job
GET /api/batch/full-pipeline  - Lance le pipeline complet
```

#### Consultation
```
GET /api/batch/status/{jobExecutionId}  - Statut d'un job
GET /api/batch/articles/export          - Export JSON des articles
GET /api/batch/articles/stats           - Statistiques articles
```

#### Documentation
```
GET /swagger-ui.html  - Interface Swagger UI
GET /api-docs         - Spécification OpenAPI JSON
```

---

## Configuration et propriétés

### application.yml
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/law_batch
    username: root
    password: root
  jpa:
    hibernate:
      ddl-auto: update
  batch:
    job:
      enabled: false  # Pas d'auto-run
    jdbc:
      initialize-schema: always

law:
  base-url: https://sgg.gouv.bj/doc
  start-year: 2023
  max-number-per-year: 100
  
  http:
    timeout: 30000
    max-retries: 3
  
  ocr:
    language: fra
    dpi: 300
    quality-threshold: 0.70
  
  batch:
    chunk-size: 10
    max-threads: 4
```

---

## Patterns et bonnes pratiques

### 1. Reader-Processor-Writer Pattern
Chaque job utilise le pattern classique Spring Batch:
- **Reader**: Génère/lit les items à traiter
- **Processor**: Transforme/enrichit les items
- **Writer**: Persiste les résultats

### 2. Chunking
Traitement par lots (chunks) pour:
- Optimiser les transactions
- Gérer la mémoire efficacement
- Améliorer les performances

### 3. Multi-threading
- TaskExecutor avec concurrencyLimit
- Traitement parallèle des chunks
- Thread-safe repositories

### 4. Idempotence
- Vérification d'unicité avant sauvegarde (TrackingWriter)
- Mise à jour vs création
- Cache OCR pour éviter retraitement

### 5. Resilience
- Retry automatique sur erreurs temporaires
- Gestion des redirections HTTP
- Logs détaillés pour diagnostic

### 6. Stockage complet en base de données
- PDFs en LONGBLOB (plus de filesystem)
- OCR text en LONGTEXT
- Métadonnées en JSON
- Architecture scalable et portable

---

## Flux de données complet

```
┌─────────────┐
│  Site SGG   │
│sgg.gouv.bj  │
└──────┬──────┘
       │
       │ HTTP HEAD (Fetch)
       ▼
┌─────────────────┐
│ fetch_results   │◀── Table MySQL
└────────┬────────┘
         │
         │ Filter exists=true
         ▼
┌─────────────────┐
│  Site SGG PDF   │
└────────┬────────┘
         │
         │ HTTP GET (Download)
         ▼
┌──────────────────┐
│download_results  │◀── LONGBLOB MySQL
│  (PDF bytes)     │
└────────┬─────────┘
         │
         │ Load PDF bytes
         ▼
┌──────────────────┐
│  TesseractOCR    │◀── Service
└────────┬─────────┘
         │
         │ OCR text
         ▼
┌──────────────────┐
│  ocr_results     │◀── LONGTEXT MySQL
└────────┬─────────┘
         │
         │ Parse articles
         ▼
┌──────────────────┐
│ArticleExtractor  │◀── Service
└────────┬─────────┘
         │
         │ Articles individuels
         ▼
┌──────────────────┐
│article_extractions│◀── Table MySQL
└──────────────────┘
         │
         │ Export/API
         ▼
┌──────────────────┐
│  JSON Output     │
└──────────────────┘
```

---

## Monitoring et observabilité

### Logs
```
bj.gouv.sgg: DEBUG
org.springframework.batch: INFO
```

### Métriques
- Nombre de documents traités
- Taux de succès/échec
- Durée d'exécution par step
- Taille des PDFs téléchargés

### Spring Batch Metadata
- JobExecution: historique des runs
- StepExecution: détails par step
- ExecutionContext: état intermédiaire

---

## Évolution future

### Améliorations possibles
1. **Partitioning**: Distribuer le traitement sur plusieurs instances
2. **Remote Chunking**: Déléguer le processing à des workers
3. **Skip Policy**: Continuer en cas d'erreur sur un item
4. **Retry Logic**: Retry configurable avec backoff
5. **Job Parameters**: Paramétrer années/types dynamiquement
6. **Scheduling**: Cron jobs pour exécution automatique
7. **Notifications**: Email/Slack sur succès/échec
8. **Cache Redis**: Cache distribué pour OCR results

### Scalabilité
- Base de données: Read replicas pour lecture
- PDFs: Compression en base ou migration S3
- OCR: Queue système (RabbitMQ) pour async
- API: Load balancer + multiple instances

---

## Diagramme de séquence - Full Pipeline

```
┌─────┐  ┌──────┐  ┌────────┐  ┌─────────┐  ┌──────┐  ┌──────┐
│User │  │ API  │  │ Batch  │  │ Readers │  │ Proc │  │ MySQL│
└──┬──┘  └───┬──┘  └───┬────┘  └────┬────┘  └───┬──┘  └───┬──┘
   │         │         │             │            │         │
   │  GET    │         │             │            │         │
   │ /full-  │         │             │            │         │
   │pipeline │         │             │            │         │
   ├────────>│         │             │            │         │
   │         │         │             │            │         │
   │         │ start   │             │            │         │
   │         │ Job     │             │            │         │
   │         ├────────>│             │            │         │
   │         │         │             │            │         │
   │         │         │  FETCH STEP │            │         │
   │         │         ├────────────>│            │         │
   │         │         │             │            │         │
   │         │         │             │ generate   │         │
   │         │         │             │ URLs       │         │
   │         │         │             ├───────────>│         │
   │         │         │             │            │         │
   │         │         │             │            │ HTTP    │
   │         │         │             │            │ HEAD    │
   │         │         │             │            ├────────>│
   │         │         │             │            │         │
   │         │         │             │            │<────────┤
   │         │         │             │            │ 200/404 │
   │         │         │             │            │         │
   │         │         │             │            │ save    │
   │         │         │             │            ├────────>│
   │         │         │             │            │         │
   │         │         │             │<───────────┤         │
   │         │         │<────────────┤            │         │
   │         │         │             │            │         │
   │         │         │ DOWNLOAD STEP            │         │
   │         │         ├────────────>│            │         │
   │         │         │             │            │         │
   │         │         │             │ read fetch │         │
   │         │         │             │ exists=true│         │
   │         │         │             ├───────────>│         │
   │         │         │             │            │         │
   │         │         │             │            │ HTTP GET│
   │         │         │             │            ├────────>│
   │         │         │             │            │         │
   │         │         │             │            │<────────┤
   │         │         │             │            │ PDF bytes│
   │         │         │             │            │         │
   │         │         │             │            │save BLOB│
   │         │         │             │            ├────────>│
   │         │         │             │            │         │
   │         │         │             │<───────────┤         │
   │         │         │<────────────┤            │         │
   │         │         │             │            │         │
   │         │         │  EXTRACT STEP            │         │
   │         │         ├────────────>│            │         │
   │         │         │             │            │         │
   │         │         │             │ read PDFs  │         │
   │         │         │             │ from DB    │         │
   │         │         │             ├───────────>│         │
   │         │         │             │            │         │
   │         │         │             │            │read BLOB│
   │         │         │             │            ├────────>│
   │         │         │             │            │         │
   │         │         │             │            │<────────┤
   │         │         │             │            │ bytes[] │
   │         │         │             │            │         │
   │         │         │             │            │ OCR     │
   │         │         │             │            ├────────>│
   │         │         │             │            │         │
   │         │         │             │            │ Parse   │
   │         │         │             │            ├────────>│
   │         │         │             │            │         │
   │         │         │             │            │save OCR │
   │         │         │             │            │+articles│
   │         │         │             │            ├────────>│
   │         │         │             │            │         │
   │         │         │             │<───────────┤         │
   │         │         │<────────────┤            │         │
   │         │         │             │            │         │
   │         │<────────┤             │            │         │
   │         │ Complete│             │            │         │
   │<────────┤         │             │            │         │
   │ 202     │         │             │            │         │
   │         │         │             │            │         │
```

---

## Conclusion

Cette architecture Spring Batch fournit:

✅ **Modularité**: Jobs indépendants et composables  
✅ **Performance**: Multi-threading et chunking  
✅ **Fiabilité**: Retry, idempotence, transactions  
✅ **Observabilité**: Logs détaillés, metadata Spring Batch  
✅ **Scalabilité**: Architecture stateless, stockage BD  
✅ **Maintenabilité**: Code structuré, patterns éprouvés  
✅ **API REST**: Contrôle et monitoring via endpoints  

Le système est prêt pour le traitement de milliers de documents légaux avec une architecture robuste et évolutive.
