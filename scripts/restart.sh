#!/bin/bash

# Script pour recompiler et redémarrer l'application law-spring-batch

PROJECT_DIR="/Volumes/FOLDER/dev/projects/law.spring/law.spring"

echo "🔨 Compilation du projet..."
cd "$PROJECT_DIR" && mvn clean package -DskipTests -q
if [ $? -ne 0 ]; then
    echo "❌ Erreur de compilation"
    exit 1
fi
echo "✅ Compilation réussie"

echo "🔄 Arrêt de l'application..."
pkill -9 -f "law-spring-batch"

# Tuer le processus qui écoute sur le port 8080
PORT_PID=$(lsof -ti:8080)
if [ -n "$PORT_PID" ]; then
    echo "🔪 Arrêt du processus sur le port 8080 (PID: $PORT_PID)..."
    kill -9 $PORT_PID 2>/dev/null
fi

sleep 2

# Note: Reset BD désactivé - on garde les données existantes (mode UPDATE)
# echo "🗑️  Reset de la base MySQL law_batch (DROP + CREATE)..."
# if docker ps --format '{{.Names}}' | grep -q '^law-mysql$'; then
#     docker exec law-mysql mysql -uroot -proot -e "SET FOREIGN_KEY_CHECKS=0; DROP DATABASE IF EXISTS law_batch; CREATE DATABASE law_batch;" >/dev/null 2>&1
#     if [ $? -eq 0 ]; then
#         echo "✅ Base law_batch recréée"
#     else
#         echo "❌ Échec reset base law_batch"; exit 2
#     fi
# else
#     echo "❌ Conteneur law-mysql introuvable (skip reset)" 
# fi

echo "🚀 Démarrage de l'application..."
nohup java -jar "$PROJECT_DIR/target/law-spring-batch-1.0.0-SNAPSHOT.jar" > "$PROJECT_DIR/app.log" 2>&1 &

sleep 5

# Vérifier si l'application démarre correctement
if curl -s http://localhost:8080/actuator/health | grep -q "UP" 2>/dev/null; then
    echo "✅ Application démarrée avec succès"
    echo "📋 Logs: tail -f $PROJECT_DIR/app.log"
else
    echo "⏳ L'application démarre... (vérifier les logs)"
    echo "📋 tail -f $PROJECT_DIR/app.log"
fi
