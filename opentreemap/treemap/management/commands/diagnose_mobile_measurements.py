# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.core.management.base import BaseCommand, CommandError

from treemap.audit import Audit, FieldPermission
from treemap.instance import Instance
from treemap.models import Tree, User
from treemap.udf import UserDefinedFieldDefinition


MEASUREMENT_FIELDS = (
    'Circonferenza 1,30 m',
    'Metodo misura altezza',
    'Metodo misura circonferenza',
    'Stato misura altezza',
    'Stato misura circonferenza',
    'Qualità misura',
    'Errore altezza stimato m',
    'Errore circonferenza stimato cm',
    'Data rilievo',
)


class Command(BaseCommand):
    help = 'Diagnose OpenTreeNap mobile measurement permissions and recent writes.'

    def add_arguments(self, parser):
        parser.add_argument('--instance', default='napoli')
        parser.add_argument('--username', default='nicoali')
        parser.add_argument('--plot', type=int, default=None)

    def handle(self, *args, **options):
        slug = options['instance']
        username = options['username']
        plot_id = options['plot']

        try:
            instance = Instance.objects.get(url_name=slug)
        except Instance.DoesNotExist:
            raise CommandError('Unknown instance: %s' % slug)

        try:
            user = User.objects.get(username=username)
        except User.DoesNotExist:
            raise CommandError('Unknown user: %s' % username)

        role = user.get_role(instance)

        self.stdout.write('Instance: %s (#%s)' % (instance.url_name, instance.pk))
        self.stdout.write('User: %s (#%s)' % (user.username, user.pk))
        self.stdout.write(
            'Role: %s (#%s), default_permission=%s'
            % (role.name, role.pk, role.default_permission_level)
        )

        self.stdout.write('\nMeasurement UDFs / permissions:')
        for name in MEASUREMENT_FIELDS:
            udf = UserDefinedFieldDefinition.objects.filter(
                instance=instance,
                model_type='Tree',
                name=name,
                iscollection=False,
            ).first()

            if udf is None:
                self.stdout.write('  MISSING: %s' % name)
                continue

            perm = FieldPermission.objects.filter(
                instance=instance,
                role=role,
                model_name='Tree',
                field_name=udf.canonical_name,
            ).first()

            self.stdout.write(
                '  %s | udf_id=%s | permission=%s'
                % (
                    name,
                    udf.pk,
                    perm.permission_level if perm else 'MISSING',
                )
            )

        builtins = ('height', 'diameter')
        self.stdout.write('\nBuilt-in Tree permissions:')
        for field_name in builtins:
            perm = FieldPermission.objects.filter(
                instance=instance,
                role=role,
                model_name='Tree',
                field_name=field_name,
            ).first()
            self.stdout.write(
                '  %s | permission=%s'
                % (
                    field_name,
                    perm.permission_level if perm else 'MISSING',
                )
            )

        audit_fields = list(builtins) + [
            'udf:%s' % name for name in MEASUREMENT_FIELDS
        ]

        self.stdout.write('\nRecent measurement audits for this user:')
        audits = Audit.objects.filter(
            instance=instance,
            user=user,
            model='Tree',
            field__in=audit_fields,
        ).order_by('-created', '-id')[:20]

        if not audits:
            self.stdout.write('  none')
        else:
            for audit in audits:
                self.stdout.write(
                    '  audit=%s tree=%s field=%s action=%s value=%r created=%s'
                    % (
                        audit.pk,
                        audit.model_id,
                        audit.field,
                        audit.action,
                        audit.current_value,
                        audit.created,
                    )
                )

        if plot_id is not None:
            self.stdout.write('\nCurrent tree values for plot %s:' % plot_id)
            tree = Tree.objects.filter(
                instance=instance,
                plot_id=plot_id,
            ).order_by('-id').first()

            if tree is None:
                self.stdout.write('  no tree found')
            else:
                self.stdout.write('  tree_id=%s' % tree.pk)
                self.stdout.write('  height=%r' % tree.height)
                self.stdout.write('  diameter=%r' % tree.diameter)
                for name in MEASUREMENT_FIELDS:
                    self.stdout.write(
                        '  %s=%r'
                        % (name, tree.udfs.get(name, None, do_not_clean=True))
                    )
