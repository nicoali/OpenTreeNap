import hashlib
from pathlib import Path

from django.conf import settings
from django.core.management.base import BaseCommand
from django.db import transaction

from treemap.branding import upgrade_legacy_colors
from treemap.models import Instance


class Command(BaseCommand):
    help = 'Replace exact OpenTreeMap logo/color defaults; preserve custom branding and all map data.'

    def add_arguments(self, parser):
        parser.add_argument('--apply', action='store_true', help='Save changes; otherwise report only.')
        parser.add_argument('--instance', help='Limit to an existing instance URL name.')

    def handle(self, *args, **options):
        legacy_hashes = set()
        for filename in ('logo.png', 'logo@2x.png', 'otmLogo126.png'):
            path = Path(settings.PROJECT_ROOT) / 'assets' / 'img' / filename
            if path.exists():
                legacy_hashes.add(hashlib.sha256(path.read_bytes()).hexdigest())

        queryset = Instance.objects.all()
        if options['instance']:
            queryset = queryset.filter(url_name=options['instance'])
        changed = 0
        with transaction.atomic():
            for instance in queryset.select_for_update().iterator():
                config = upgrade_legacy_colors(instance.config)
                replace_logo = False
                if instance.logo:
                    try:
                        with instance.logo.open('rb') as image:
                            replace_logo = hashlib.sha256(image.read()).hexdigest() in legacy_hashes
                    except OSError:
                        self.stderr.write('Unreadable logo for %s; retaining its reference.' % instance.url_name)
                if config == instance.config and not replace_logo:
                    continue
                changed += 1
                self.stdout.write('%s: legacy colors=%s, legacy logo=%s' % (
                    instance.url_name, config != instance.config, replace_logo))
                if options['apply']:
                    instance.config = config
                    if replace_logo:
                        # Keep the media file; clear only its exact legacy reference.
                        instance.logo = None
                    instance.save(update_fields=['config', 'logo'])
        self.stdout.write('%s: %s instance(s).' % ('Applied' if options['apply'] else 'Preview only', changed))
