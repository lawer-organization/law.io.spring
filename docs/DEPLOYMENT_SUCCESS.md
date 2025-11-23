# ✅ Déploiement Raspberry Pi - Succès !

**Date:** 23 novembre 2025  
**Raspberry Pi:** 192.168.0.37  
**RAM:** 906 Mo (Raspberry Pi 2/3)  
**Statut:** 🟢 OPÉRATIONNEL

---

## 🎯 Application Déployée

### Informations Générales
- **URL:** `http://192.168.0.37:8080`
- **Version:** law-spring-batch 1.0.0-SNAPSHOT
- **Java:** 25.0.1
- **Spring Boot:** 3.2.0
- **Base de données:** MariaDB (localhost:3306)

### Health Check
```bash
curl http://192.168.0.37:8080/actuator/health
# {"status":"UP"}
```

---

## 🔐 Identifiants (PAR DÉFAUT - À CHANGER !)

### API REST (Basic Auth)
- **Username:** `admin`
- **Password:** `change_me_in_production`

### Base de données MariaDB
- **Database:** `law_batch`
- **Username:** `law_user`
- **Password:** `law_password_2024`

**⚠️ IMPORTANT:** Changez ces mots de passe en production !

---

## 🔧 Problèmes Résolus

### 1. Hibernate Dialect Error
**Erreur:** `Unable to determine Dialect without JDBC metadata`

**Cause:** MariaDB incompatible avec auto-détection Hibernate (colonne RESERVED inexistante)

**Solution:** Ajout explicite du dialect dans systemd
```bash
Environment="SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.MariaDBDialect"
```

### 2. Variables d'environnement non chargées
**Erreur:** systemd n'utilisait pas le fichier `.env`

**Solution:** Variables directement dans le service systemd avec `Environment=`

### 3. Mémoire insuffisante
**Problème:** Raspberry Pi avec seulement 906 Mo RAM, swap utilisé à 405 Mo

**Solution:** Réduction de la mémoire Java
- Avant: `-Xms512m -Xmx1536m`
- Après: `-Xms256m -Xmx800m`

---

## ⏰ Automatisation (Cron Jobs)

### Jobs Planifiés

| Fréquence | Tâche | Script |
|-----------|-------|--------|
| **Toutes les 5 min** | Health check + auto-restart | `/opt/law-cron-scripts/health-check.sh` |
| **Toutes les 30 min** | Pipeline complet | `/opt/law-cron-scripts/full-pipeline.sh` |
| **Tous les jours 00:00** | Rotation logs (>30j) | `find /var/log/law-*.log -mtime +30 -delete` |

### Pipeline Complet (6 étapes)
1. `fetch-current.sh` - Récupération année en cours
2. `fetch-previous.sh` - Récupération 15 derniers jours
3. `download-pdfs.sh` - Téléchargement PDFs
4. `process-ocr.sh` - OCR Tesseract
5. `extract-articles.sh` - Extraction articles
6. `consolidate.sh` - Consolidation en base

---

## 📊 Monitoring

### Vérifier le statut
```bash
# Statut du service
sudo systemctl status law-spring-batch

# Logs en temps réel
sudo journalctl -u law-spring-batch -f

# Health check
curl http://localhost:8080/actuator/health

# Stats des fichiers
curl -u admin:change_me_in_production http://localhost:8080/api/files/stats

# Mémoire et CPU
free -h
ps aux | grep java
```

### Logs disponibles
```
/var/log/law-fetch-current.log
/var/log/law-fetch-previous.log
/var/log/law-download.log
/var/log/law-ocr.log
/var/log/law-extract.log
/var/log/law-full-pipeline.log
/var/log/law-health.log
```

---

## 🔄 Opérations Courantes

### Redémarrer l'application
```bash
sudo systemctl restart law-spring-batch
```

### Mettre à jour depuis GitHub
```bash
cd /opt/law-spring-batch
sudo systemctl stop law-spring-batch
git pull
mvn clean package -DskipTests
sudo systemctl start law-spring-batch
```

### Voir les crons installés
```bash
crontab -l
```

### Exécuter un job manuellement
```bash
# Pipeline complet
sudo /opt/law-cron-scripts/full-pipeline.sh

# Ou via API
curl -X POST -u admin:change_me_in_production \
  http://localhost:8080/api/batch/full-pipeline
```

---

## 📁 Structure des Répertoires

```
/opt/law-spring-batch/          # Application principale
  ├── target/                   # JAR compilé
  ├── scripts/                  # Scripts de déploiement
  └── .env                      # Configuration (non utilisée, voir systemd)

/opt/law-cron-scripts/          # Scripts cron
  ├── full-pipeline.sh
  ├── fetch-current.sh
  ├── fetch-previous.sh
  ├── download-pdfs.sh
  ├── process-ocr.sh
  ├── extract-articles.sh
  └── health-check.sh

/var/law-data/                  # Données
  ├── pdfs/                     # PDFs téléchargés
  ├── ocr/                      # Fichiers OCR
  └── articles/                 # Articles JSON

/etc/systemd/system/
  └── law-spring-batch.service  # Service systemd
```

---

## 🚀 Prochaines Étapes

### 1. Sécurité (PRIORITAIRE)
```bash
# Changer le mot de passe API
ssh pi@192.168.0.37
sudo nano /etc/systemd/system/law-spring-batch.service
# Modifier Environment="SECURITY_USER_PASSWORD=..."
sudo systemctl daemon-reload
sudo systemctl restart law-spring-batch

# Changer le mot de passe MariaDB
sudo mysql
ALTER USER 'law_user'@'localhost' IDENTIFIED BY 'NOUVEAU_MOT_DE_PASSE';
FLUSH PRIVILEGES;
EXIT;
# Mettre à jour aussi dans systemd service
```

### 2. Tests
```bash
# Lancer un pipeline complet manuel
sudo /opt/law-cron-scripts/full-pipeline.sh
tail -f /var/log/law-full-pipeline.log
```

### 3. Frontend
- Configurer CORS si nécessaire
- URL API: `http://192.168.0.37:8080`
- Authentication: Basic Auth avec `admin:NOUVEAU_PASSWORD`

---

## 📈 Performance Actuelle

- **Démarrage:** ~81 secondes
- **RAM utilisée:** 447 Mo (49% du total)
- **Swap utilisé:** 405 Mo
- **CPU:** Pic à ~139% pendant le démarrage

### Recommandations
- ✅ Mémoire Java optimisée (256m-800m)
- ⚠️ Envisager upgrade vers Raspberry Pi 4 (4GB) si performance insuffisante
- ✅ Swap activé (905 Mo) - compense le manque de RAM

---

## 🐛 Troubleshooting

### L'application ne démarre pas
```bash
# Vérifier les logs
sudo journalctl -u law-spring-batch -n 100

# Vérifier MariaDB
sudo systemctl status mariadb
mysql -u law_user -p law_batch -e "SELECT 1"

# Vérifier la mémoire
free -h
# Si mémoire insuffisante: réduire Xmx dans le service systemd
```

### Pipeline ne s'exécute pas
```bash
# Vérifier les crons
crontab -l

# Logs de cron
grep CRON /var/log/syslog

# Exécuter manuellement
sudo /opt/law-cron-scripts/full-pipeline.sh
```

### Erreur "Connection refused"
```bash
# Vérifier que le port 8080 est ouvert
netstat -tulpn | grep 8080

# Vérifier le pare-feu
sudo ufw status
```

---

## 📝 Notes Techniques

### MariaDB vs MySQL
- Raspberry Pi OS utilise MariaDB par défaut
- Compatible MySQL mais nécessite dialect Hibernate explicite
- Driver JDBC: `mysql-connector-java` (compatible MariaDB)

### Java 25 vs Java 17
- Application construite pour Java 17
- Fonctionne sur Java 25.0.1 (rétro-compatibilité)
- Pas de problème détecté pour l'instant

### Swap
- 905 Mo de swap configuré automatiquement
- Compense le manque de RAM physique
- Performance acceptable mais plus lent que RAM

---

## ✅ Checklist de Déploiement

- [x] Application démarrée
- [x] Health check OK
- [x] MariaDB connectée
- [x] Cron jobs installés
- [x] Logs configurés
- [x] Mémoire optimisée
- [x] Service systemd enabled (démarre au boot)
- [ ] **Mots de passe changés (FAIRE MAINTENANT)**
- [ ] Pipeline testé manuellement
- [ ] Frontend connecté
- [ ] CORS configuré (si nécessaire)
- [ ] Backup database configuré
- [ ] Monitoring externe (optionnel)

---

**🎉 Déploiement réussi ! L'application est opérationnelle.**

*Dernière mise à jour: 23 novembre 2025 18:54 EST*
