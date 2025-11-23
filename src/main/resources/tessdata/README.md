# Tessdata - Trained Data Files

Ce répertoire contient les fichiers de données d'entraînement pour Tesseract OCR.

## Installation du fichier français (fra.traineddata)

### Option 1 : Téléchargement direct (Recommandé)
```bash
cd src/main/resources/tessdata/
curl -L https://github.com/tesseract-ocr/tessdata/raw/main/fra.traineddata -o fra.traineddata
```

### Option 2 : Via Homebrew (macOS)
```bash
# Le fichier est déjà présent dans l'installation Homebrew
cp /opt/homebrew/share/tessdata/fra.traineddata src/main/resources/tessdata/
```

### Option 3 : Via apt (Linux)
```bash
# Le fichier est déjà présent après installation
sudo apt install tesseract-ocr-fra
cp /usr/share/tesseract-ocr/4.00/tessdata/fra.traineddata src/main/resources/tessdata/
```

## Vérification

Après installation, vous devriez avoir :
```
src/main/resources/tessdata/
├── README.md
└── fra.traineddata  (~ 5 MB)
```

## Note

Le fichier `fra.traineddata` est automatiquement extrait au runtime dans un répertoire temporaire.
Il est embarqué dans le JAR final lors du build Maven.
