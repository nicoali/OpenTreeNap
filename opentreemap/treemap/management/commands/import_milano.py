"""Create the Milano tenant and resumably import the prepared 2024 inventory."""
import hashlib
import importlib.util
import json
from pathlib import Path

from django.contrib.gis.geos import MultiPolygon, Point, Polygon
from django.core.management import call_command
from django.core.management.base import BaseCommand, CommandError
from django.db import connection, transaction

from treemap.audit import FieldPermission, Role, add_default_permissions, add_instance_permissions
from treemap.instance import Instance
from treemap.models import InstanceUser, Plot, Species, Tree, User
from treemap.udf import UserDefinedFieldDefinition
from treemap.units import get_storage_value
from treemap.lib.object_caches import clear_caches
from treemap.milano_catalog import resolve_species


SOURCE_ID = 'ID censimento Milano'
FINGERPRINT = 'Impronta censimento Milano'
FIELDS = {
    SOURCE_ID: 'string', FINGERPRINT: 'string',
    'Diametro chioma m': 'float', 'Municipio Milano': 'string',
    'Data censimento Milano': 'string', 'Dati originali Milano': 'string',
    'Dati storici Milano': 'string', 'Qualità censimento Milano': 'string',
    'Monumentale archivio storico': 'string', 'Categoria archivio storico': 'string',
}


def preparation_module():
    path = Path(__file__).resolve().parents[4] / 'scripts' / 'prepare_milano.py'
    spec = importlib.util.spec_from_file_location('prepare_milano', str(path))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def file_hash(path):
    result = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024*1024), b''):
            result.update(block)
    return result.hexdigest()


class Command(BaseCommand):
    help = 'Validate/create/import Milano 2024; dry-run unless --apply is supplied.'

    def add_arguments(self, parser):
        parser.add_argument('dataset', help='Path to milano.otn.jsonl')
        parser.add_argument('--report', required=True, help='Prepared report.json')
        parser.add_argument('--user', required=True, help='Existing OTN administrator username')
        parser.add_argument('--apply', action='store_true')
        parser.add_argument('--publish', action='store_true', help='Make complete instance public')
        parser.add_argument('--batch-size', type=int, default=100)

    def handle(self, *args, **options):
        path = Path(options['dataset'])
        module = preparation_module()
        report = json.loads(Path(options['report']).read_text(encoding='utf-8'))
        if report.get('trees') != 247779 or report.get('source_date') != '2024-03-31':
            raise CommandError('Expected complete Milano 2024 inventory (247779 trees).')
        if file_hash(path) != report['outputs_sha256']['milano.otn.jsonl']:
            raise CommandError('Dataset SHA256 does not match report.')
        if options['batch_size'] < 1 or options['batch_size'] > 1000:
            raise CommandError('Batch size must be 1..1000.')
        try:
            user = User.objects.get(username=options['user'])
        except User.DoesNotExist:
            raise CommandError('Administrator user does not exist.')

        def rows():
            with path.open(encoding='utf-8') as source:
                for line_number, line in enumerate(source, 1):
                    try:
                        yield module.validate_row(json.loads(line))
                    except (ValueError, KeyError, TypeError) as error:
                        raise CommandError('Line %d: %s' % (line_number, error))

        # Validate every row before any write. Calculate bounds from data,
        # rather than trusting arbitrary report coordinates.
        seen = set()
        bounds = [180, 90, -180, -90]
        for row in rows():
            if row['external_id'] in seen:
                raise CommandError('Duplicate external ID: '+row['external_id'])
            seen.add(row['external_id'])
            x, y = row['coordinates']
            bounds = [min(bounds[0],x),min(bounds[1],y),max(bounds[2],x),max(bounds[3],y)]
        if len(seen) != report['trees']:
            raise CommandError('Incomplete dataset.')
        self.stdout.write('Validated %d trees; bounds %s.' % (len(seen), bounds))
        if options['publish'] and not options['apply']:
            raise CommandError('--publish requires --apply.')

        # Serialize all cooperating Milano imports across batch commits.
        with connection.cursor() as cursor:
            cursor.execute('SELECT pg_try_advisory_lock(20240331, 2484)')
            locked = cursor.fetchone()[0]
        if not locked:
            raise CommandError('Another Milano import is running.')
        try:
            self.run_import(options, user, module, rows, seen, bounds, report)
        finally:
            with connection.cursor() as cursor:
                cursor.execute('SELECT pg_advisory_unlock(20240331, 2484)')

    def run_import(self, options, user, module, rows, seen, bounds, report):
        instance = Instance.objects.filter(url_name='milano').first()
        if instance and instance.config.get('milano_import.schema') != 'otn.milano.v1':
            raise CommandError('Existing /milano/ is not managed by this importer; refusing to change it.')
        if instance and not InstanceUser.objects.filter(instance=instance,user=user,admin=True).exists():
            raise CommandError('User must be an administrator of the existing Milano instance.')
        existing = {}
        if instance:
            for tree in Tree.objects.filter(instance=instance).iterator(chunk_size=500):
                key = tree.udfs.get(SOURCE_ID, None)
                if key:
                    if key in existing:
                        raise CommandError('Duplicate database source ID: '+key)
                    existing[key] = tree.udfs.get(FINGERPRINT, None)
            if set(existing)-seen:
                raise CommandError('Existing source IDs are absent from this dataset; manual review required.')
        for row in rows():
            key = row['external_id']
            if key in existing and existing[key] != row['fingerprint']:
                raise CommandError('Changed source record %s; existing trees will not be overwritten.' % key)
        self.stdout.write('%d new; %d already imported.' % (len(seen)-len(existing),len(existing)))
        if not options['apply']:
            self.stdout.write('Dry-run complete: no database changes.')
            return

        if instance is None:
            with transaction.atomic():
                center = '%s,%s' % ((bounds[0]+bounds[2])/2, (bounds[1]+bounds[3])/2)
                call_command('create_instance','Milano',user=user.username,url_name='milano',center=center)
                instance = Instance.objects.get(url_name='milano')
                # Data envelope with a small padding, not a municipal boundary.
                lower = Point(bounds[0]-.005,bounds[1]-.005,srid=4326)
                upper = Point(bounds[2]+.005,bounds[3]+.005,srid=4326)
                lower.transform(3857)
                upper.transform(3857)
                instance.bounds.geom = MultiPolygon(Polygon.from_bbox((lower.x,lower.y,upper.x,upper.y)),srid=3857)
                instance.bounds.save()
                instance.is_public = False
                instance.itree_region_default = ''
                instance.config['milano_import.schema'] = 'otn.milano.v1'
                display = instance.config.get('value_display', {})
                display['tree'] = {'diameter':{'units':'cm','digits':1},
                                   'height':{'units':'m','digits':1},
                                   'canopy_height':{'units':'m','digits':1}}
                instance.config['value_display'] = display
                instance.save()
                # Public/default users can read; import administrator can edit.
                role = instance.default_role
                role.default_permission_level = FieldPermission.READ_ONLY
                role.save()
                FieldPermission.objects.filter(instance=instance,role=role).update(permission_level=FieldPermission.READ_ONLY)
                role.instance_permissions.clear()
                # Native create_instance assigns the importing user the
                # default role with admin=True; explicitly give the admin role.
                admin_role, _ = Role.objects.get_or_create(instance=instance,name=Role.ADMINISTRATOR,
                    defaults={'rep_thresh':0,'default_permission_level':FieldPermission.WRITE_DIRECTLY})
                add_default_permissions(instance,roles=[admin_role])
                add_instance_permissions([admin_role])
                membership = InstanceUser.objects.get(instance=instance,user=user)
                membership.role = admin_role
                membership.save_with_user(user)

        with transaction.atomic():
            for name, field_type in FIELDS.items():
                definition, _ = UserDefinedFieldDefinition.objects.get_or_create(
                    instance=instance,model_type='Tree',name=name,iscollection=False,
                    defaults={'datatype':json.dumps({'type':field_type})})
                if definition.datatype_dict.get('type') != field_type:
                    raise CommandError('Conflicting UDF definition: '+name)
                for role in Role.objects.filter(instance=instance):
                    level = FieldPermission.WRITE_DIRECTLY if role.name == Role.ADMINISTRATOR else FieldPermission.READ_ONLY
                    permission, _ = FieldPermission.objects.get_or_create(instance=instance,role=role,
                        model_name='Tree',field_name=definition.canonical_name,
                        defaults={'permission_level':level})
                    if role.name == Role.ADMINISTRATOR and permission.permission_level != level:
                        permission.permission_level = level
                        permission.save()

        clear_caches()
        instance.refresh_from_db()
        with transaction.atomic():
            visible = ['Diametro chioma m','Municipio Milano','Data censimento Milano',
                       'Monumentale archivio storico','Categoria archivio storico','Qualità censimento Milano']
            for property_name in ('web_detail_fields','mobile_api_fields'):
                groups = list(getattr(instance,property_name))
                existing_keys = {key for group in groups for key in group.get('field_keys',[])}
                keys = ['tree.udf:'+name for name in visible if 'tree.udf:'+name not in existing_keys]
                if keys:
                    groups.append({'header':'Censimento Milano','model':'tree','field_keys':keys})
                    setattr(instance,property_name,groups)
            instance.save()

        species_cache = {}
        inserted = 0
        batch = []

        def save_batch(batch):
            with transaction.atomic():
                for row in batch:
                    taxon = row['taxon']
                    species = None
                    if taxon['genus']:
                        # Local codes deliberately do not invent i-Tree mappings.
                        code = 'MILANO_LOCAL_'+module.digest(taxon)[:24]
                        if code not in species_cache:
                            species = resolve_species(Species,instance,user,taxon,code)
                            species_cache[code] = species
                        species = species_cache[code]
                    point = Point(*row['coordinates'],srid=4326)
                    point.transform(3857)
                    plot = Plot(instance=instance,geom=point,address_city='Milano',
                                address_street=str(row['official_properties'].get('localita') or '')[:255])
                    plot.save_with_user(user)
                    # OTN storage is normally inches/feet. Its conversion utility
                    # respects the actual server settings and Milano metric display.
                    tree = Tree(instance=instance,plot=plot,species=species,
                        diameter=get_storage_value(instance,'tree','diameter',row['diameter_cm']),
                        height=get_storage_value(instance,'tree','height',row['height_m']))
                    payload = {SOURCE_ID:row['external_id'],FINGERPRINT:row['fingerprint'],
                        'Diametro chioma m':row['crown_diameter_m'],
                        'Municipio Milano':str(row['official_properties'].get('municipio') or ''),
                        'Data censimento Milano':row['source_date'],
                        'Dati originali Milano':module.canonical(row['official_properties']),
                        'Dati storici Milano':module.canonical(row['legacy']),
                        'Qualità censimento Milano':module.canonical(row['quality_flags'])}
                    if row['legacy'] is not None:
                        payload['Monumentale archivio storico'] = 'sì' if row['legacy']['monumental'] else 'no'
                        payload['Categoria archivio storico'] = row['legacy']['category']
                    for key, value in payload.items():
                        if value is not None:
                            tree.udfs[key] = value
                    tree.save_with_user(user)
                    if plot.is_pending_insert or tree.is_pending_insert:
                        raise CommandError('Import administrator lacks direct create permissions.')

        for row in rows():
            if row['external_id'] in existing:
                continue
            batch.append(row)
            if len(batch) >= options['batch_size']:
                save_batch(batch)
                inserted += len(batch)
                self.stdout.write('Committed %d new trees.' % inserted)
                batch = []
        if batch:
            save_batch(batch)
            inserted += len(batch)
        with transaction.atomic():
            instance.refresh_from_db()
            instance.config['milano_import.completed'] = True
            instance.config['milano_import.source_date'] = report['source_date']
            instance.config['milano_import.trees'] = len(seen)
            if options['publish']:
                instance.is_public = True
            instance.save()
        self.stdout.write(self.style.SUCCESS('Milano complete: %d trees; public=%s.' % (len(seen),instance.is_public)))
