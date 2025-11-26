# 📚 Documentation law.spring

Documentation complète du système de traitement automatisé des textes juridiques du Bénin.

---

## 🌟 GUIDE PRINCIPAL

### **📖 [GUIDE COMPLET](./GUIDE_COMPLET.md)** - Tout-en-un

**Le guide consolidé contient :**
- ✅ Introduction et vue d'ensemble
- ✅ Architecture complète du système
- ✅ Architecture Spring Batch détaillée
- ✅ API REST Reference
- ✅ Déploiement (Maven + Raspberry Pi)
- ✅ Configuration et monitoring
- ✅ Troubleshooting complet
- ✅ Structure du projet

**👉 Commencez par là ! C'est le document de référence unique.**

---

## 📁 Autres Documents (Référence)

Les documents ci-dessous sont conservés pour référence historique et détails spécifiques :

### 🚀 Démarrage
- [Guide de Démarrage](./GUIDE.md) - Installation rapide
- [README Principal](../README.md) - Vue d'ensemble projet

### 🏗️ Architecture
- [Architecture Système](./ARCHITECTURE.md) - Vue d'ensemble
- [Architecture Batch](./BATCH_ARCHITECTURE.md) - Jobs Spring Batch
- [Structure Projet](./PROJECT_STRUCTURE.md) - Organisation code
- [Pipeline Extraction](./EXTRACTION_CONSOLIDATION.md) - OCR → JSON → DB

### 🔧 API et Configuration
- [API Reference](./API_REFERENCE.md) - Endpoints REST
- [Gestion Erreurs](./EXCEPTIONS.md) - Exceptions personnalisées
- [Révision HTTP](./HTTP_METHODS_REVIEW.md) - Conformité REST

### 🚢 Déploiement
- [Déploiement Maven](./DEPLOY_MAVEN.md) - Configuration SSH
- [Déploiement Raspberry Pi](./DEPLOY_RASPBERRY_PI.md) - Setup complet
- [Succès Déploiement](./DEPLOYMENT_SUCCESS.md) - État actuel
- [Déploiement Render](./DEPLOY_RENDER.md) - Cloud hosting
- [Guide Docker](./DOCKER_GUIDE.md) - Containerisation

### 🔄 Migration
- [Guide Migration](./MIGRATION.md) - law.io.v2 → law.spring
- [Migration Ranges](./MIGRATION_RANGES.md) - Stratégie progressive

### 📚 Ressources
- [Ressources](./RESOURCES.md) - Liens utiles

---

## 🎯 Navigation Rapide

| Besoin | Document |
|--------|----------|
| **Tout comprendre** | [GUIDE_COMPLET.md](./GUIDE_COMPLET.md) ⭐ |
| Démarrer rapidement | [GUIDE.md](./GUIDE.md) |
| Comprendre l'architecture | [GUIDE_COMPLET.md](./GUIDE_COMPLET.md) § 2-3 |
| Utiliser l'API | [GUIDE_COMPLET.md](./GUIDE_COMPLET.md) § 4 |
| Déployer sur Raspberry Pi | [GUIDE_COMPLET.md](./GUIDE_COMPLET.md) § 5 |
| Résoudre un problème | [GUIDE_COMPLET.md](./GUIDE_COMPLET.md) § 7 |

---

## 📊 État Actuel du Système

**Dernière mise à jour : 24 novembre 2025**

### Déploiement Raspberry Pi
- ✅ **Status:** Opérationnel (192.168.0.37:8080)
- ✅ **Service:** law-spring-batch.service (enabled, active)
- ✅ **Base de données:** law_batch (MariaDB)
- ✅ **Scheduler:** 6 jobs automatiques actifs
- ✅ **Mémoire:** 256MB-800MB (optimisé 1GB RAM)

### Jobs Testés
- ✅ **fetch-current:** 2484 documents en 18min13s
- ✅ **download:** 2 PDFs (68.7MB) en 2min
- ✅ **ocr, extract, consolidate:** Fonctionnels

### Configuration
- **Thread pool:** 1 (exécution séquentielle)
- **Chunk size:** 10
- **Max threads batch:** 4
- **Démarrage:** ~90 secondes

---

## 🔄 Dernières Mises à Jour

- **24 Nov 2025** : Création GUIDE_COMPLET.md - Documentation consolidée
- **24 Nov 2025** : Déploiement réussi et validé sur Raspberry Pi
- **24 Nov 2025** : Tests jobs fetch-current et download
- **23 Nov 2025** : Configuration scheduler avec 6 jobs automatiques
- **23 Nov 2025** : Retrait Spring Security pour simplification
- **23 Nov 2025** : Configuration déploiement Maven SSH

---

## 📞 Support

Pour toute question :
- **Documentation complète:** [GUIDE_COMPLET.md](./GUIDE_COMPLET.md)
- **Issues GitHub:** https://github.com/lawer-organization/law.io.v2/issues
- **Email:** Contact équipe de développement

---

**Version actuelle** : 1.0.0  
**Dernière mise à jour** : 24 novembre 2025  
**Statut** : ✅ Production Ready
