from django.db import connection
from django.http import JsonResponse


def healthz(request):
    """Minimal liveness/readiness endpoint for modern-v4 containers."""
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT 1")
            cursor.fetchone()
    except Exception as exc:
        return JsonResponse({"status": "error", "database": str(exc)}, status=503)
    return JsonResponse({"status": "ok", "database": "ok"})
