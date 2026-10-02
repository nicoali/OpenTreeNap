# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.core.management.base import BaseCommand, CommandError
from treemap.instance import Instance
from treemap.lib.udf import udf_create, udf_exists

FIELDS = (
    ('Circonferenza 1,30 m', 'float'),
    ('Metodo misura altezza', 'string'),
    ('Metodo misura circonferenza', 'string'),
    ('Stato misura altezza', 'string'),
    ('Stato misura circonferenza', 'string'),
    ('Qualità misura', 'string'),
    ('Errore altezza stimato m', 'float'),
    ('Errore circonferenza stimato cm', 'float'),
    ('Data rilievo', 'date'),
)

class Command(BaseCommand):
    help = 'Prepare Tree UDF fields used by the OpenTreeNap mobile measurement workflow.'

    def add_arguments(self, parser):
        parser.add_argument('--instance', default='napoli')

    def handle(self, *args, **options):
        slug = options['instance']
        try:
            instance = Instance.objects.get(url_name=slug)
        except Instance.DoesNotExist:
            raise CommandError('Unknown instance: %s' % slug)

        created = 0
        for name, field_type in FIELDS:
            params = {
                'udf.name': name,
                'udf.model': 'Tree',
                'udf.type': field_type,
            }
            if not udf_exists(params, instance):
                udf_create(params, instance)
                created += 1

        self.stdout.write(
            self.style.SUCCESS(
                'Measurement fields ready for %s (%d created).' % (slug, created)
            )
        )
