# Law Spring Batch - Traitement des documents juridiques du Bénin

Version Spring Batch du projet law.io.v2

## 📚 Documentation

**➡️ [Documentation complète disponible dans docs/](./docs/README.md)**

- [Guide de Démarrage](./docs/GUIDE.md)
- [Architecture Système](./docs/ARCHITECTURE.md)
- [Jobs Spring Batch](./docs/BATCH_ARCHITECTURE.md)
- [Pipeline Extraction/Consolidation](./docs/EXTRACTION_CONSOLIDATION.md)
- [Système d'Exceptions](./docs/EXCEPTIONS.md)
- [Guide de Migration](./docs/MIGRATION.md)

## 🎯 Vue d'ensemble

Application Spring Boot avec Spring Batch pour le traitement automatisé des lois et décrets du Bénin :
- **Fetch** : Vérification de l'existence des documents
- **Download** : Téléchargement des PDFs
- **OCR** : Extraction du texte via Tesseract
- **Extract** : Parsing des articles et export JSON
- **Consolidate** : Import des JSON en base de données

## 🚀 Démarrage rapide

### Prérequis
- Java 17+
- Maven 3.8+

### Build
```bash
mvn clean package
```

### Lancement
```bash
mvn spring-boot:run
```

L'application démarre sur `http://localhost:8080`

## 📋 API REST

### 🔐 Authentification
Tous les endpoints (sauf `/actuator/health`) nécessitent HTTP Basic Auth :
```bash
curl -u admin:password http://localhost:8080/api/...
```

### 🎯 Endpoints Disponibles

#### Articles & Documents
- `GET /api/articles/export` - Exporter tous les articles en JSON
- `GET /api/articles/stats` - Statistiques des articles
- `GET /api/fetch-results/{year}` - Documents d'une année
- `GET /api/fetch-results/stats` - Statistiques des documents

#### Fichiers (NOUVEAU)
- `GET /api/files/stats` - Statistiques globales (PDFs, OCR, JSON)
- `GET /api/files/pdfs` - Liste des PDFs disponibles
- `GET /api/files/ocr` - Liste des fichiers OCR
- `GET /api/files/articles` - Liste des JSON d'articles
- `GET /api/files/pdfs/{filename}` - Télécharger un PDF
- `GET /api/files/ocr/{filename}` - Télécharger un OCR
- `GET /api/files/articles/{filename}` - Télécharger un JSON
- `GET /api/files/ocr/{filename}/content` - Lire contenu OCR
- `GET /api/files/articles/{filename}/content` - Lire contenu JSON

#### Batch Jobs
- `POST /api/batch/fetch-current` - Récupérer documents année courante
- `POST /api/batch/fetch-previous` - Récupérer années précédentes
- `POST /api/batch/download` - Télécharger les PDFs
- `POST /api/batch/ocr` - Lancer l'OCR
- `POST /api/batch/extract` - Extraire les articles
- `POST /api/batch/full-pipeline` - Pipeline complet
- `GET /api/batch/status/{jobId}` - Statut d'un job

#### Health Check
- `GET /actuator/health` - Santé de l'application (public, pas d'auth)

📚 **Documentation complète** : [docs/API_REFERENCE.md](./docs/API_REFERENCE.md)

### Exemples

```bash
# Lancer le pipeline complet
curl -X POST -u admin:test123 http://localhost:8080/api/batch/full-pipeline

# Vérifier statut job
curl -u admin:test123 http://localhost:8080/api/batch/status/1

# Statistiques fichiers
curl -u admin:test123 http://localhost:8080/api/files/stats | jq

# Télécharger un PDF
curl -u admin:test123 -O http://localhost:8080/api/files/pdfs/loi-2025-11.pdf

# Lire contenu OCR
curl -X POST http://localhost:8080/api/batch/download

# Extract (extraire les articles)
curl -X POST http://localhost:8080/api/batch/extract

# Pipeline complet (fetch + download + extract)
curl -X POST http://localhost:8080/api/batch/full-pipeline
```

### Monitoring

```bash
# Statut d'un job
curl http://localhost:8080/api/batch/status/{jobExecutionId}

# Console H2 (base de données Batch)
http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:file:./data/batch-db
# Username: sa
# Password: (vide)
```

## 🏗️ Architecture

### Jobs Spring Batch

1. **fetchJob** : Génère tous les documents possibles et vérifie leur existence via HTTP HEAD
2. **downloadJob** : Télécharge les PDFs des documents existants
3. **extractJob** : Extrait le texte (OCR) et parse les articles
4. **fullPipelineJob** : Enchainement complet des 3 jobs

### Structure du projet

```
bj.gouv.sgg/
├── batch/
│   ├── config/        # Configuration des jobs Batch
│   ├── reader/        # ItemReaders (génération/lecture documents)
│   ├── processor/     # ItemProcessors (fetch/download/extract)
│   └── writer/        # ItemWriters (persistence)
├── config/            # Configuration Spring
├── controller/        # API REST
├── model/             # Entités métier
└── service/           # Services (OCR, extraction, etc.)
```

### Caractéristiques Spring Batch

- **Chunk-oriented processing** : Traitement par lots configurable
- **Multi-threading** : Parallélisation des tâches
- **Throttling** : Limitation de concurrence
- **Restart/Resume** : Reprise après échec
- **Skip policy** : Gestion des erreurs
- **Job repository** : Persistance de l'état dans H2

## ⚙️ Configuration

Fichier `application.yml` :

```yaml
law:
  base-url: https://sgg.gouv.bj/doc
  start-year: 1960
  
  batch:
    chunk-size: 10        # Nombre d'items par chunk
    max-threads: 4        # Parallélisation
    throttle-limit: 2     # Limite de concurrence
```

## 📊 Monitoring

### Métriques Spring Batch

- Nombre de documents traités
- Durée d'exécution
- Taux de succès/échec
- Skip count

### Logs

```bash
tail -f logs/law-spring-batch.log
```

## 📖 Documentation Détaillée

Pour plus d'informations, consultez la [documentation complète](./docs/README.md) :

- **[ARCHITECTURE.md](./docs/ARCHITECTURE.md)** - Architecture du système
- **[BATCH_ARCHITECTURE.md](./docs/BATCH_ARCHITECTURE.md)** - Détails des jobs Spring Batch
- **[EXTRACTION_CONSOLIDATION.md](./docs/EXTRACTION_CONSOLIDATION.md)** - Pipeline d'extraction et consolidation
- **[EXCEPTIONS.md](./docs/EXCEPTIONS.md)** - Système de gestion des erreurs
- **[GUIDE.md](./docs/GUIDE.md)** - Guide de démarrage complet
- **[MIGRATION.md](./docs/MIGRATION.md)** - Migration depuis law.io.v2
- **[PROJECT_STRUCTURE.md](./docs/PROJECT_STRUCTURE.md)** - Structure du code
- **[RESOURCES.md](./docs/RESOURCES.md)** - Ressources et références

## 🔄 Comparaison avec law.io.v2

| Aspect | law.io.v2 (Jobs Java) | law.spring (Spring Batch) |
|--------|----------------------|---------------------------|
| Orchestration | Scripts Bash | Spring Batch Jobs |
| Persistance | Fichiers .txt | Base H2 + fichiers |
| Monitoring | Logs uniquement | API REST + H2 console |
| Restart | Manuel | Automatique |
| Parallélisation | ThreadPool custom | Spring Batch multi-thread |
| API | Aucune | REST API |

## 📁 Données

```
src/database/
├── data/
│   ├── pdfs/{loi|decret}/      # PDFs téléchargés
│   ├── ocr/{loi|decret}/       # Textes extraits
│   ├── articles/{loi|decret}/  # JSONs individuels
│   └── output.json             # Consolidation finale
└── *.result.txt                # Fichiers de tracking
```

### Remplacement du stockage BLOB

Depuis la migration filesystem (nov. 2025):
- Les tables `download_results` et `ocr_results` ont été supprimées.
- Les contenus binaires PDF et textes OCR sont stockés directement sur disque sous `data/pdfs/...` et `data/ocr/...`.
- Le statut de téléchargement est tracé via la colonne `status` de `fetch_results` (`FETCHED` → `DOWNLOADED` → `EXTRACTED`).
- Le script `scripts/db-drop-binary-tables.sh` permet de nettoyer une base existante.

Avantages:
- Plus de limite MySQL `max_allowed_packet`.
- Téléchargement de gros PDFs (>100MB) sans ajustement serveur.
- Accès direct aux fichiers pour outils externes (OCR, diff, compression).

Conséquence:
- Sauvegarde et restauration se font au niveau répertoire `data/` (penser à inclure dans backups).
- Contrôle d'intégrité géré par `sha256` dans le processus mais non persisté actuellement (peut être ajouté dans une table dédiée si nécessaire).

## 🔧 Développement

### Tests
```bash
mvn test
```

### Build sans tests
```bash
mvn package -DskipTests
```

## 📝 Licence

Projet d'étude - Gouvernement du Bénin
