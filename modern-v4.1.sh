#!/usr/bin/env bash
set -euo pipefail
COMPOSE="docker compose -f docker-compose.modern-v4.1.yml --env-file .env.modern-v4.1"

case "${1:-help}" in
  doctor)
    command -v docker >/dev/null || { echo "Docker is not installed."; exit 1; }
    docker compose version >/dev/null || { echo "Docker Compose v2 plugin is not available."; exit 1; }
    [[ -f .env.modern-v4.1 ]] || { echo "Missing .env.modern-v4.1"; exit 1; }
    $COMPOSE config -q
    echo "modern-v4.1 host prerequisites OK"
    docker --version
    docker compose version
    ;;
  build)
    $COMPOSE build --pull web
    ;;
  up)
    $COMPOSE up --build -d
    $COMPOSE ps
    ;;
  logs)
    $COMPOSE logs -f --tail=200 web
    ;;
  status)
    $COMPOSE ps
    ;;
  check)
    $COMPOSE exec web python manage.py check
    ;;
  smoke)
    published="$($COMPOSE port web 8000 | tail -n 1)"
    port="${published##*:}"
    [[ -n "$port" ]] || { echo "Web port is not published; is modern-v4.1 running?"; exit 1; }
    curl -fsS "http://127.0.0.1:${port}/healthz/"
    echo
    ;;
  shell)
    $COMPOSE exec web python manage.py shell
    ;;
  superuser)
    $COMPOSE exec web python manage.py createsuperuser
    ;;
  demo)
    echo "Create a normal superuser first if needed: ./modern-v4.1.sh superuser"
    echo "Then run create_instance explicitly, for example:"
    echo "docker compose -f docker-compose.modern-v4.1.yml --env-file .env.modern-v4.1 exec web python manage.py create_instance 'OTM Demo' --user YOUR_USER --url_name demo --center=-75.1652,39.9526"
    ;;
  worker-up)
    $COMPOSE --profile tasks up -d worker
    ;;
  down)
    $COMPOSE down
    ;;
  reset)
    echo "This removes the modern-v4.1 PostgreSQL/Redis/static/media volumes."
    read -r -p "Type RESET to continue: " confirm
    [[ "$confirm" == "RESET" ]] || exit 1
    $COMPOSE down -v
    ;;
  *)
    cat <<'HELP'
OpenTreeMap modern-v4.1 helper

  ./modern-v4.1.sh doctor     Verify Docker/Compose prerequisites
  ./modern-v4.1.sh build      Build the web image only
  ./modern-v4.1.sh up         Build and start PostGIS, Redis and the web app
  ./modern-v4.1.sh logs       Follow web startup logs
  ./modern-v4.1.sh status     Show container/health status
  ./modern-v4.1.sh check      Run Django system checks
  ./modern-v4.1.sh smoke      Call the /healthz/ endpoint
  ./modern-v4.1.sh superuser  Create an admin user
  ./modern-v4.1.sh demo       Show the demo-instance command
  ./modern-v4.1.sh worker-up  Start the optional Celery worker
  ./modern-v4.1.sh down       Stop the stack
  ./modern-v4.1.sh reset      Stop and DELETE v4.1 volumes
HELP
    ;;
esac
