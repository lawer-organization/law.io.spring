#!/bin/bash

# Script pour lancer l'application avec Docker Compose
# Usage: ./docker-run.sh [up|down|logs|restart]

set -e

ACTION="${1:-up}"

case "$ACTION" in
  up)
    echo "🚀 Starting application with Docker Compose..."
    docker compose up -d
    echo ""
    echo "✅ Application started!"
    echo ""
    echo "📋 Services:"
    docker compose ps
    echo ""
    echo "🔍 View logs: ./docker-run.sh logs"
    echo "🛑 Stop: ./docker-run.sh down"
    echo ""
    echo "🌐 Access:"
    echo "  - App: http://localhost:8080"
    echo "  - Swagger: http://localhost:8080/swagger-ui.html"
    echo "  - Health: http://localhost:8080/actuator/health"
    ;;
    
  down)
    echo "🛑 Stopping application..."
    docker compose down
    echo "✅ Application stopped!"
    ;;
    
  logs)
    echo "📋 Viewing logs (Ctrl+C to exit)..."
    docker compose logs -f app
    ;;
    
  restart)
    echo "🔄 Restarting application..."
    docker compose restart
    echo "✅ Application restarted!"
    ;;
    
  dev)
    echo "🔧 Starting in development mode..."
    docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
    echo "✅ Development environment started!"
    echo "   Security: DISABLED"
    echo "   Logs: DEBUG"
    ;;
    
  build)
    echo "🔨 Rebuilding images..."
    docker compose build --no-cache
    echo "✅ Images rebuilt!"
    ;;
    
  clean)
    echo "🧹 Cleaning up..."
    docker compose down -v
    echo "✅ Cleaned! (volumes removed)"
    ;;
    
  *)
    echo "Usage: ./docker-run.sh [up|down|logs|restart|dev|build|clean]"
    echo ""
    echo "Commands:"
    echo "  up      - Start application (production mode)"
    echo "  down    - Stop application"
    echo "  logs    - View application logs"
    echo "  restart - Restart application"
    echo "  dev     - Start in development mode (security off)"
    echo "  build   - Rebuild Docker images"
    echo "  clean   - Stop and remove volumes"
    exit 1
    ;;
esac
