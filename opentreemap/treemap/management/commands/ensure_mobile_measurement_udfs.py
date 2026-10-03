# -*- coding: utf-8 -*-
from __future__ import unicode_literals

import json

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

from treemap.audit import Role, FieldPermission
from treemap.instance import Instance
from treemap.udf import UserDefinedFieldDefinition


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
    help = (
        'Prepare the scalar Tree UDF fields used by the OpenTreeNap '
        'mobile measurement workflow.'
    )

    def add_arguments(self, parser):
        parser.add_argument('--instance', default='napoli')

    @transaction.atomic
    def handle(self, *args, **options):
        slug = options['instance']

        try:
            instance = Instance.objects.get(url_name=slug)
        except Instance.DoesNotExist:
            raise CommandError('Unknown instance: %s' % slug)

        created = []
        existing = []

        for name, field_type in FIELDS:
            udf = UserDefinedFieldDefinition.objects.filter(
                instance=instance,
                model_type='Tree',
                name=name,
                iscollection=False,
            ).first()

            if udf is None:
                udf = UserDefinedFieldDefinition.objects.create(
                    name=name,
                    model_type='Tree',
                    iscollection=False,
                    instance=instance,
                    datatype=json.dumps({'type': field_type}),
                )
                created.append(name)
            else:
                existing.append(name)

            # Mirror the permission behavior of treemap.lib.udf.udf_create(),
            # but deliberately do NOT mutate instance.mobile_api_fields or
            # instance.web_detail_fields here. Some long-lived OTM instances
            # contain legacy duplicate field entries which make Instance.save()
            # fail validation. Android reads these scalar UDFs directly from
            # mobile_meta.tree_udfs, so registering them in those legacy field
            # lists is not required for the measurement API.
            #
            # Measurement fields must be immediately writable by the instance
            # administrator. Older instances may have a role default of
            # WRITE_WITH_AUDIT, which would make a successful mobile PUT appear
            # to "save" while the value remains pending. Force administrator
            # measurement permissions to WRITE_DIRECTLY and preserve the
            # configured default level for all other roles.
            for role in Role.objects.filter(instance=instance):
                desired_level = (
                    FieldPermission.WRITE_DIRECTLY
                    if role.name == Role.ADMINISTRATOR
                    else role.default_permission_level
                )

                permission, was_created = FieldPermission.objects.get_or_create(
                    model_name='Tree',
                    field_name=udf.canonical_name,
                    role=role,
                    instance=role.instance,
                    defaults={'permission_level': desired_level},
                )

                if (
                    role.name == Role.ADMINISTRATOR and
                    permission.permission_level != desired_level
                ):
                    permission.permission_level = desired_level
                    permission.save()

        if created:
            self.stdout.write(
                self.style.SUCCESS(
                    'Created %d measurement field(s): %s'
                    % (len(created), ', '.join(created))
                )
            )

        if existing:
            self.stdout.write(
                'Already present (%d): %s'
                % (len(existing), ', '.join(existing))
            )

        self.stdout.write(
            self.style.SUCCESS(
                'Measurement fields ready for %s.' % slug
            )
        )
