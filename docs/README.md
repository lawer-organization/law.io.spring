# 📚 Documentation law.spring

Documentation complète du système de traitement automatisé des textes juridiques du Bénin.

## 🚀 Démarrage Rapide

- **[Guide de Démarrage](./GUIDE.md)** - Installation et premiers pas
- **[README Principal](../README.md)** - Vue d'ensemble du projet

## 📖 Architecture

### Architecture Générale
- **[Architecture Système](./ARCHITECTURE.md)** - Vue d'ensemble de l'architecture
- **[Architecture Batch](./BATCH_ARCHITECTURE.md)** - Jobs Spring Batch détaillés
- **[Structure du Projet](./PROJECT_STRUCTURE.md)** - Organisation du code

### Pipeline de Traitement
- **[Extraction et Consolidation](./EXTRACTION_CONSOLIDATION.md)** - Architecture complète du pipeline
  - Job d'extraction (OCR → JSON)
  - Job de consolidation (JSON → DB)
  - Flux de traitement
  - Scripts et commandes

## 🔧 Développement

### Gestion des Erreurs
- **[Système d'Exceptions](./EXCEPTIONS.md)** - Guide complet
  - 11 exceptions personnalisées
  - GlobalExceptionHandler
  - Réponses API standardisées
  - Exemples d'utilisation

- **[Résumé Exceptions](./SUMMARY_EXCEPTIONS.md)** - Vue synthétique
  - Métriques et statistiques
  - Cas d'usage couverts
  - Prochaines étapes

### API & Configuration
- **[Révision Méthodes HTTP](./HTTP_METHODS_REVIEW.md)** - Conformité REST et Pool MySQL
  - Correction GET → POST pour actions
  - Configuration HikariCP Keep-Alive
  - Tests et validation
  - Impact clients API

- **[Migration API v1.1](./MIGRATION_API_V1.1.md)** - Guide de migration pour clients
  - Changements breaking
  - Exemples avant/après
  - Script de test
  - Checklist déploiement

### Sécurité & Déploiement
- **[Résumé Sécurité](./SECURITY_SUMMARY.md)** - Vue d'ensemble complète
  - État avant/après
  - Modifications apportées
  - Tests locaux
  - Guide express Render

- **[Déploiement Render](./DEPLOY_RENDER.md)** - Guide complet step-by-step
  - Configuration MySQL
  - Variables d'environnement
  - Tests post-déploiement
  - Troubleshooting

- **[Guide Docker](./DOCKER_GUIDE.md)** - Déploiement avec Docker
  - Dockerfile multi-stage
  - Docker Compose (MySQL + App)
  - Volumes et persistance
  - Mode dev/prod
  - Commandes utiles

- **[Checklist Sécurité](./SECURITY_CHECKLIST.md)** - Guide rapide
  - Checklist avant déploiement
  - Authentification API
  - Niveaux de sécurité
  - Rappels importants

### Migration
- **[Guide de Migration](./MIGRATION.md)** - Migration law.io.v2 → law.spring
  - Différences principales
  - Correspondances des composants
  - Services réutilisés
  - Commandes équivalentes

- **[Migration Ranges](./MIGRATION_RANGES.md)** - Stratégie de migration progressive
  - Approche par plages d'années
  - Gestion des trous
  - Optimisations

## 📚 Ressources

- **[Ressources](./RESOURCES.md)** - Liens et références utiles
  - Documentation API
  - Tutoriels Spring Batch
  - Outils de développement

## 📋 Organisation de la Documentation

```
docs/
├── README.md                          (ce fichier - index principal)
│
├── Démarrage/
│   ├── GUIDE.md                       Guide de démarrage
│   └── ../README.md                   README du projet
│
├── Architecture/
│   ├── ARCHITECTURE.md                Architecture système
│   ├── BATCH_ARCHITECTURE.md          Jobs Spring Batch
│   ├── PROJECT_STRUCTURE.md           Structure du code
│   └── EXTRACTION_CONSOLIDATION.md    Pipeline extraction/consolidation
│
├── Développement/
│   ├── EXCEPTIONS.md                  Guide des exceptions
│   ├── SUMMARY_EXCEPTIONS.md          Résumé exceptions
│   └── RESTRUCTURATION_EXTRACTION.md  Historique restructuration
│
├── Migration/
│   ├── MIGRATION.md                   Guide migration v2 → Spring
│   └── MIGRATION_RANGES.md            Stratégie par ranges
│
└── Ressources/
    └── RESOURCES.md                   Liens et références
```

## 🎯 Navigation Rapide

### Par Cas d'Usage

**Je veux démarrer le projet**
→ [GUIDE.md](./GUIDE.md)

**Je veux comprendre l'architecture**
→ [ARCHITECTURE.md](./ARCHITECTURE.md) → [BATCH_ARCHITECTURE.md](./BATCH_ARCHITECTURE.md)

**Je veux comprendre le pipeline**
→ [EXTRACTION_CONSOLIDATION.md](./EXTRACTION_CONSOLIDATION.md)

**Je veux gérer les erreurs**
→ [EXCEPTIONS.md](./EXCEPTIONS.md)

**Je veux migrer du code de law.io.v2**
→ [MIGRATION.md](./MIGRATION.md)

**Je cherche un fichier spécifique**
→ [PROJECT_STRUCTURE.md](./PROJECT_STRUCTURE.md)

### Par Rôle

**Développeur Backend**
- Architecture: [ARCHITECTURE.md](./ARCHITECTURE.md)
- Batch: [BATCH_ARCHITECTURE.md](./BATCH_ARCHITECTURE.md)
- Exceptions: [EXCEPTIONS.md](./EXCEPTIONS.md)
- Structure: [PROJECT_STRUCTURE.md](./PROJECT_STRUCTURE.md)

**Développeur Frontend/API**
- Guide: [GUIDE.md](./GUIDE.md)
- Exceptions: [EXCEPTIONS.md](./EXCEPTIONS.md) (réponses API)

**DevOps/Admin**
- Guide: [GUIDE.md](./GUIDE.md)
- Pipeline: [EXTRACTION_CONSOLIDATION.md](./EXTRACTION_CONSOLIDATION.md)
- Scripts et commandes

**Chef de Projet**
- README: [../README.md](../README.md)
- Architecture: [ARCHITECTURE.md](./ARCHITECTURE.md)
- Migration: [MIGRATION.md](./MIGRATION.md)

## 🔄 Dernières Mises à Jour

- **23 Nov 2025** : Création de l'index principal et réorganisation
- **23 Nov 2025** : Système d'exceptions personnalisées complet
- **23 Nov 2025** : Documentation pipeline extraction/consolidation
- **22 Nov 2025** : Architecture Batch détaillée
- **19 Nov 2025** : Guide de migration v2 → Spring

## 📞 Support

Pour toute question ou suggestion concernant la documentation :
- Ouvrir une issue GitHub
- Contacter l'équipe de développement

---

**Version actuelle** : 1.0.0  
**Dernière mise à jour** : 23 novembre 2025
