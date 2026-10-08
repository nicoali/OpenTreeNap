"""Expose verified historical Milano i-Tree Eco results on matched trees."""
import json

from django.core.management.base import BaseCommand, CommandError
from django.db import connection, transaction

from treemap.audit import FieldPermission, Role
from treemap.instance import Instance
from treemap.lib.object_caches import clear_caches
from treemap.milano_benefits import (
    BENEFIT_FIELDS, FIELD_DESCRIPTIONS, LEGACY_FIELD, REFERENCE, REFERENCE_FIELD,
    SOURCE, SOURCE_FIELD,
    parse_historical_benefits,
)
from treemap.models import InstanceUser, Tree, User
from treemap.udf import UserDefinedFieldDefinition


EXPECTED_TREES = 247779
EXPECTED_MATCHED = 183968
DISPLAY_FIELDS = [name for _, name in BENEFIT_FIELDS] + [SOURCE_FIELD, REFERENCE_FIELD]


def stored_value(tree, name):
    """Read the raw HStore value without repeatedly resolving UDF metadata."""
    return dict.get(tree.udfs, name, None)


class Command(BaseCommand):
    help = ('Validate/expose historical i-Tree Eco benefits for the verified '
            'Milano spatial+taxon matches; dry-run unless --apply is supplied.')

    def add_arguments(self, parser):
        parser.add_argument('--user', required=True, help='Existing Milano administrator username')
        parser.add_argument('--apply', action='store_true')
        parser.add_argument('--batch-size', type=int, default=100)

    def handle(self, *args, **options):
        if not 1 <= options['batch_size'] <= 1000:
            raise CommandError('Batch size must be 1..1000.')
        try:
            instance = Instance.objects.get(url_name='milano')
            user = User.objects.get(username=options['user'])
        except Instance.DoesNotExist:
            raise CommandError('Milano instance does not exist.')
        except User.DoesNotExist:
            raise CommandError('Administrator user does not exist.')
        if instance.config.get('milano_import.schema') != 'otn.milano.v1':
            raise CommandError('Milano is not managed by the verified importer.')
        if not InstanceUser.objects.filter(instance=instance, user=user, admin=True).exists():
            raise CommandError('User must be a Milano administrator.')
        if Tree.objects.filter(instance=instance).count() != EXPECTED_TREES:
            raise CommandError('Milano inventory is incomplete; expected 247779 trees.')

        with connection.cursor() as cursor:
            cursor.execute('SELECT pg_try_advisory_lock(20240331, 2484)')
            locked = cursor.fetchone()[0]
        if not locked:
            raise CommandError('Another Milano import is running.')
        try:
            self.run_import(instance, user, options)
        finally:
            with connection.cursor() as cursor:
                cursor.execute('SELECT pg_advisory_unlock(20240331, 2484)')

    def rows(self, instance):
        return (Tree.objects.filter(instance=instance, udfs__has_key=LEGACY_FIELD)
                .select_related('instance').iterator(chunk_size=500))

    def inspect(self, instance):
        eligible = complete = 0
        sums = {name: 0.0 for _, name in BENEFIT_FIELDS}
        for tree in self.rows(instance):
            raw = stored_value(tree, LEGACY_FIELD)
            if not raw:
                continue
            eligible += 1
            try:
                values = parse_historical_benefits(raw)
            except (ValueError, TypeError, json.JSONDecodeError) as error:
                raise CommandError('Tree %s: %s' % (tree.pk, error))
            present = [stored_value(tree, name) for name in DISPLAY_FIELDS]
            if any(value is not None for value in present):
                if not all(value is not None for value in present):
                    raise CommandError('Tree %s has a partial historical benefit import.' % tree.pk)
                for name, expected in values.items():
                    current = stored_value(tree, name)
                    if isinstance(expected, float):
                        if abs(float(current)-expected) > 0.000001:
                            raise CommandError('Tree %s has changed benefit %s.' % (tree.pk, name))
                    elif current != expected:
                        raise CommandError('Tree %s has changed benefit provenance.' % tree.pk)
                complete += 1
            for _, name in BENEFIT_FIELDS:
                sums[name] += values[name]
        if eligible != EXPECTED_MATCHED:
            raise CommandError('Expected 183968 verified historical matches; found %d.' % eligible)
        return eligible, complete, sums

    def create_fields(self, instance):
        with transaction.atomic():
            field_types = {name: 'float' for _, name in BENEFIT_FIELDS}
            field_types.update({SOURCE_FIELD: 'string', REFERENCE_FIELD: 'string'})
            for name in DISPLAY_FIELDS:
                field_type = field_types[name]
                definition, _ = UserDefinedFieldDefinition.objects.get_or_create(
                    instance=instance, model_type='Tree', name=name, iscollection=False,
                    defaults={'datatype': json.dumps({
                        'type': field_type,
                        'description': FIELD_DESCRIPTIONS[name],
                    })})
                if definition.datatype_dict.get('type') != field_type:
                    raise CommandError('Conflicting UDF definition: '+name)
                for role in Role.objects.filter(instance=instance):
                    level = (FieldPermission.WRITE_DIRECTLY if role.name == Role.ADMINISTRATOR
                             else FieldPermission.READ_ONLY)
                    permission, _ = FieldPermission.objects.get_or_create(
                        instance=instance, role=role, model_name='Tree',
                        field_name=definition.canonical_name,
                        defaults={'permission_level': level})
                    if permission.permission_level != level:
                        permission.permission_level = level
                        permission.save()
        clear_caches()
        instance.refresh_from_db()
        with transaction.atomic():
            for property_name in ('web_detail_fields', 'mobile_api_fields'):
                groups = list(getattr(instance, property_name))
                existing = {key for group in groups for key in group.get('field_keys', [])}
                keys = ['tree.udf:'+name for name in DISPLAY_FIELDS if 'tree.udf:'+name not in existing]
                if keys:
                    groups.append({'header': 'Benefici ecosistemici - stima storica',
                                   'model': 'tree', 'field_keys': keys})
                    setattr(instance, property_name, groups)
            instance.save()

    def run_import(self, instance, user, options):
        eligible, complete, sums = self.inspect(instance)
        self.stdout.write('Validated %d matched trees; %d already imported; %d pending.' %
                          (eligible, complete, eligible-complete))
        self.stdout.write('Matched historical annual benefits: EUR %.2f/year.' %
                          sums['Benefici annuali totali (EUR/anno)'])
        if not options['apply']:
            self.stdout.write('Dry-run complete: no database changes.')
            return

        self.create_fields(instance)
        committed = 0
        batch = []

        def save_batch(trees):
            nonlocal committed
            with transaction.atomic():
                for tree in trees:
                    values = parse_historical_benefits(stored_value(tree, LEGACY_FIELD))
                    if all(stored_value(tree, name) is not None for name in DISPLAY_FIELDS):
                        continue
                    for name, value in values.items():
                        tree.udfs[name] = value
                    tree.save_with_user(user)
                    committed += 1
            self.stdout.write('Committed %d historical benefit records.' % committed)

        for tree in self.rows(instance):
            if all(stored_value(tree, name) is not None for name in DISPLAY_FIELDS):
                continue
            batch.append(tree)
            if len(batch) >= options['batch_size']:
                save_batch(batch)
                batch = []
        if batch:
            save_batch(batch)

        with transaction.atomic():
            instance.refresh_from_db()
            instance.config['milano_benefits.schema'] = 'itree-eco-historical.v1'
            instance.config['milano_benefits.matched_trees'] = eligible
            instance.config['milano_benefits.annual_eur'] = round(
                sums['Benefici annuali totali (EUR/anno)'], 2)
            instance.config['milano_benefits.totals'] = {
                source_name: round(sums[display_name], 2)
                for source_name, display_name in BENEFIT_FIELDS
            }
            instance.config['milano_benefits.source'] = SOURCE
            instance.config['milano_benefits.reference'] = REFERENCE
            instance.save()
        self.stdout.write(self.style.SUCCESS(
            'Milano historical benefits complete: %d matched trees.' % eligible))

