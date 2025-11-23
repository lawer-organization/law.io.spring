# Ressources du projet

## 📦 Fichiers copiés depuis law.io.v2

### Dictionnaires et corrections OCR

- **corrections.csv** (2.1 KB)
  - Corrections orthographiques pour améliorer la qualité OCR
  - Format: `erreur,correction`

- **liste.de.mots.francais.frgut.txt** (4 MB)
  - Dictionnaire français complet (FRGUT)
  - Utilisé pour validation et correction des textes extraits

- **patterns.properties** (1.7 KB)
  - Expressions régulières pour extraction de métadonnées
  - Patterns pour dates, signataires, titres de lois

- **signatories.csv** (746 B)
  - Liste des signataires officiels connus
  - Format: `role,nom`

### Prompts IA

- **prompts/groq-text-extraction.txt** (4.4 KB)
  - Template pour extraction via Groq Vision API
  - Instructions pour parsing structuré

### Données Tesseract

- **tessdata/fra.traineddata** (14 MB) ✅ **Copié**
  - Modèle Tesseract entraîné pour le français
  - Requis pour l'OCR
  - Source: https://github.com/tesseract-ocr/tessdata

- **tessdata/README.md**
  - Instructions de téléchargement si nécessaire

## 📁 Structure des données

```
src/
├── main/resources/
│   ├── application.yml
│   ├── corrections.csv
│   ├── liste.de.mots.francais.frgut.txt
│   ├── patterns.properties
│   ├── signatories.csv
│   ├── prompts/
│   │   └── groq-text-extraction.txt
│   └── tessdata/
│       ├── fra.traineddata (14 MB)
│       └── README.md
│
└── database/data/
    ├── pdfs/{loi|decret}/      # PDFs téléchargés
    ├── ocr/{loi|decret}/       # Textes extraits
    ├── articles/{loi|decret}/  # JSONs individuels
    └── output.json             # Consolidation finale
```

## 🔧 Utilisation

### Corrections OCR

Le service `TesseractOcrService` utilise automatiquement :
- `fra.traineddata` pour l'OCR
- `corrections.csv` pour post-traitement
- `liste.de.mots.francais.frgut.txt` pour validation

### Extraction métadonnées

Le service `ArticleExtractorService` utilise :
- `patterns.properties` pour regex
- `signatories.csv` pour normalisation des noms

### Prompts IA (optionnel)

Si vous utilisez Groq Vision API :
- `prompts/groq-text-extraction.txt` contient le template

## 📥 Téléchargement manuel

Si `fra.traineddata` est manquant :

```bash
cd src/main/resources/tessdata
wget https://github.com/tesseract-ocr/tessdata/raw/main/fra.traineddata
```

Ou depuis Tesseract best :
```bash
wget https://github.com/tesseract-ocr/tessdata_best/raw/main/fra.traineddata
```

## ✅ Vérification

```bash
# Vérifier les ressources
ls -lh src/main/resources/*.{csv,txt,properties}
ls -lh src/main/resources/tessdata/fra.traineddata

# Taille totale
du -sh src/main/resources/
```

## 🔍 Notes

- Tous les fichiers sont compatibles UTF-8
- Les CSVs utilisent `,` comme séparateur
- Les patterns regex sont case-insensitive
- Le modèle Tesseract est la version standard (pas best/fast)
