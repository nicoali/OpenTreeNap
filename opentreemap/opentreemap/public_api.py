# -*- coding: utf-8 -*-
from django.http import JsonResponse
from django.views.decorators.http import require_GET
from django.views.decorators.cache import cache_page
from django.db.models import Count
from django.utils import timezone

from treemap.instance import Instance
from treemap.models import Tree


def _cors(response):
    response['Access-Control-Allow-Origin'] = '*'
    response['Access-Control-Allow-Methods'] = 'GET'
    response['Access-Control-Allow-Headers'] = 'Accept, Content-Type'
    response['Cache-Control'] = 'public, max-age=300'
    return response


@require_GET
@cache_page(300)
def napoli_trees(request):
    try:
        instance = Instance.objects.get(url_name='napoli')
    except Instance.DoesNotExist:
        return _cors(JsonResponse(
            {'status': 'error', 'reason': 'Napoli instance not found'},
            status=404
        ))

    qs = (
        Tree.objects
        .filter(instance=instance, date_removed__isnull=True)
        .select_related('plot', 'species')
        .order_by('plot_id', 'id')
    )

    rows = []
    species_ids = set()

    for tree in qs:
        plot = tree.plot
        species = tree.species
        point = plot.latlon

        if species is not None:
            species_ids.add(species.id)

        address_parts = [
            part for part in (
                plot.address_street,
                plot.address_city,
                plot.address_zip,
            ) if part
        ]

        rows.append({
            'id': tree.id,
            'plot_id': plot.id,
            'custom_id': plot.owner_orig_id or '',
            'lat': point.y,
            'lng': point.x,
            'address': plot.address_street or '',
            'city': plot.address_city or '',
            'zip': plot.address_zip or '',
            'address_full': ', '.join(address_parts),
            'species_id': species.id if species else None,
            'common_name': species.common_name if species else '',
            'scientific_name': species.scientific_name if species else '',
            'genus': species.genus if species else '',
            'species': species.species if species else '',
            'feature_url': '/napoli/features/%s/' % plot.id,
        })

    response = JsonResponse({
        'instance': {
            'id': instance.id,
            'url': instance.url_name,
            'name': instance.name,
        },
        'updated_at': timezone.now().isoformat(),
        'stats': {
            'trees': len(rows),
            'species': len(species_ids),
        },
        'trees': rows,
    })
    return _cors(response)
