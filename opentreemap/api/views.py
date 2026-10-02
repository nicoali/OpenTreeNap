# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from functools import partial

from django.core.exceptions import PermissionDenied
from django.conf import settings
from django.views.decorators.http import require_http_methods
from django.views.decorators.csrf import csrf_exempt
from django.http import HttpResponseBadRequest
from django.shortcuts import get_object_or_404
from django.db import transaction
from django.contrib.gis.db.models.functions import Transform, X, Y
from django.contrib.auth.forms import PasswordResetForm
from django.contrib.auth.tokens import default_token_generator

from django_tinsel.utils import decorate as do
from django_tinsel.decorators import route, json_api_call

from treemap.models import Plot, Tree, User
from treemap.views.misc import species_list
from treemap.lib.map_feature import context_dict_for_plot
from treemap.lib.tree import add_tree_photo_helper
from treemap.lib.photo import context_dict_for_photo
from treemap.lib.dates import DATETIME_FORMAT

from treemap.decorators import (return_400_if_validation_errors,
                                require_http_method)
from treemap.decorators import api_instance_request as instance_request
from treemap.decorators import api_admin_instance_request as \
    admin_instance_request
from treemap.decorators import creates_instance_user
from django_tinsel.exceptions import HttpBadRequestException
from treemap.audit import (Audit, approve_or_reject_audit_and_apply,
                           AuthorizeException)

from api.auth import create_401unauthorized
from api.decorators import (check_signature, check_signature_and_require_login,
                            login_required, set_api_version)
from api.instance import (instance_info, instances_closest_to_point,
                          public_instances, transform_instance_info_response)
from api.plots import (plots_closest_to_point, get_plot, update_or_create_plot,
                       transform_plot_update_dict)
from api.user import (user_info, create_user, update_user,
                      update_profile_photo, transform_user_request,
                      transform_user_response)
from exporter.views import users_json, users_csv


def datetime_to_iso_string(d):
    if d:
        return d.strftime(DATETIME_FORMAT)
    else:
        return None


def status(request):
    return [{'api_version': 'v2',
             'status': 'online',
             'message': ''}]


def extract_plot_from_audit(audit):
    if audit.model == 'Plot':
        return Plot.objects.get(pk=audit.model_id)
    elif audit.model == 'Tree':
        return Tree.objects.get(id=audit.model_id).plot


@require_http_methods(["GET"])
@json_api_call
@instance_request
@login_required
def edits(request, instance, user_id):
    if (int(user_id) != request.user.pk):
        return create_401unauthorized()

    user = request.user

    result_offset = int(request.GET.get("offset", 0))
    num_results = min(int(request.GET.get("length", 15)), 15)

    audits = Audit.objects.filter(instance=instance)\
                          .filter(user=user)\
                          .filter(model_in=['Tree', 'Plot'])\
                          .order_by('-created', 'id')

    audits = audits[result_offset:(result_offset+num_results)]

    keys = []
    for audit in audits:
        d = {}
        plot = extract_plot_from_audit(audit)
        d["plot_id"] = plot.pk

        if plot.pk:
            d["plot"] = context_dict_for_plot(request, plot)

        d["id"] = audit.pk
        d["name"] = audit.display_action
        d["created"] = datetime_to_iso_string(audit.created)
        d["value"] = audit.current_value

        keys.append(d)

    return keys


def reset_password(request):
    # Note: iOS send email as part of the query string, Android doesn't support
    # password reset at all
    email = request.GET["email"]
    try:
        User.objects.get(email=email)
    except User.DoesNotExist:
        return {"status": "failure", "message": "Email address not found."}

    resetform = PasswordResetForm({"email": email})
    if (resetform.is_valid()):
        opts = {
            'use_https': request.is_secure(),
            'token_generator': default_token_generator,
            'from_email': None,
            'email_template_name': 'registration/password_reset_email.html',
            'request': request}

        resetform.save(**opts)
        return {"status": "success"}
    else:
        raise HttpBadRequestException()


def version(request):
    """ API Request

    Get version information for OTM and the API. Generally, the API
    is unstable for any API version < 1 and minor changes
    (i.e. 1.4,1.5,1.6) represent no break in existing functionality

    Verb: GET
    Params: None
    Output:
      {
        otm_version, string -> OpenTreeMap Version (i.e. 1.0.2)
        api_version, string -> API version (i.e. 1.6)
      }

    """
    return {"otm_version": settings.OTM_VERSION,
            "api_version": settings.API_VERSION}


@require_http_methods(["GET"])
def get_plot_list(request, instance):
    """ API Request

    Get a list of all plots in the database. This is meant to be a
    lightweight listing service. To get more details about a plot
    use the ^plot/{id}$ service

    Verb: GET
    Params:
      offset, integer, default = 0  -> offset to start results from
      size, integer, default = 100 -> Maximum 10000, number of results to get

    Output:
      [{
          width, integer, opt -> Width of tree bed
          length, integer, opt -> Length of bed
          type, string, opt -> Plot type
          geometry, Point -> Lat/lng pt
          readonly, boolean -> True if this is a readonly tree
          tree, {
             id, integer -> tree id
             species, integer, opt -> Species id
             dbh, real, opt -> Diameter of the tree
          }
       }]

      """
    start = int(request.GET.get("offset", "0"))
    size = min(int(request.GET.get("size", "100")), 10000)
    end = size + start

    # Mobile/map clients only need a compact inventory payload.  The legacy
    # context_dict_for_plot() path is intentionally rich: it loads photos,
    # audits, containing polygons, progress data and sharing metadata for every
    # plot.  That is appropriate for a plot-detail response but unnecessarily
    # expensive for rendering hundreds or thousands of markers.
    #
    # mobile=1 keeps the existing endpoint and HMAC contract while returning
    # only the fields required to draw the map. Full plot details are still
    # retrieved lazily from /plots/{id} when the user taps a marker.
    mobile = request.GET.get("mobile", "").lower() in ("1", "true", "yes")

    if mobile:
        # The web map and tiler count Tree rows, so the mobile inventory must
        # expose the same population.  Keep this as a compact values() query:
        # no photos, audits, containing polygons or sharing metadata.
        rows = Tree.objects\
            .filter(instance=instance)\
            .annotate(
                geom_wgs84=Transform('plot__geom', 4326),
                longitude=X('geom_wgs84'),
                latitude=Y('geom_wgs84'),
            )\
            .order_by('id')\
            .values(
                'id',
                'plot_id',
                'longitude',
                'latitude',
                'udfs',
                'species_id',
                'species__common_name',
                'species__genus',
                'species__species',
                'species__cultivar',
                'species__other_part_of_name',
            )[start:end]

        results = []

        for row in rows:
            species = None
            common_name = row['species__common_name'] or ''
            genus = row['species__genus'] or ''
            species_name = row['species__species'] or ''
            cultivar = row['species__cultivar'] or ''
            other = row['species__other_part_of_name'] or ''

            scientific_parts = [genus, species_name, other]
            scientific_name = ' '.join(
                part for part in scientific_parts if part)
            if cultivar:
                scientific_name = ("%s '%s'" %
                                   (scientific_name, cultivar)).strip()

            if row['species_id']:
                species = {
                    'id': row['species_id'],
                    'common_name': common_name,
                    'genus': genus,
                    'species': species_name,
                    'cultivar': cultivar,
                    'other_part_of_name': other,
                    'scientific_name': scientific_name,
                }

            title = common_name or scientific_name or (
                'Albero #%s' % row['id'])

            udfs = dict(row['udfs'] or {})
            monumental_value = udfs.get('Monumentale')
            is_monumental = str(monumental_value).strip().lower() in (
                '1', 'true', 'yes', 'si', 'sì', 'monumentale',
                "albero monumentale d'italia", 'centenario',
                'centenaria', 'heritage'
            )

            results.append({
                'has_tree': True,
                'title': title,
                'is_monumental': is_monumental,
                # Address, DBH, height, Custom ID, photos and other rich
                # fields are fetched lazily from /plots/{id} after a tap.
                'address_full': '',
                'plot': {
                    'id': row['plot_id'],
                    'geom': {
                        'srid': 4326,
                        'x': row['longitude'],
                        'y': row['latitude'],
                    },
                },
                'tree': {
                    'id': row['id'],
                    'species': species,
                },
            })

        return results

    # Legacy/full response retained for existing web and API clients.
    # order_by prevents testing weirdness
    plots = Plot.objects.filter(instance=instance)\
                        .order_by('id')[start:end]

    def ctxt_for_plot(plot):
        return context_dict_for_plot(request, plot)

    return [ctxt_for_plot(plot) for plot in plots]


def _approve_or_reject_pending_edit(
        request, instance, user, pending_edit_id, approve):
    audit = Audit.objects.get(pk=pending_edit_id, instance=instance)
    approve_or_reject_audit_and_apply(audit, user, approve)

    updated_plot = extract_plot_from_audit(audit)

    # Reject remaining audits on specified field if
    # we approved this audit
    # TODO: Should this logic be moved to app_or_rej_audit_and_ap?
    if approve:
        for pending_audit in updated_plot.get_active_pending_audits()\
                                         .filter(field=audit.field):
            approve_or_reject_audit_and_apply(pending_audit, user, False)

    return context_dict_for_plot(request, updated_plot)


@require_http_methods(["POST"])
@instance_request
@creates_instance_user
@json_api_call
@login_required
def approve_pending_edit(request, instance, pending_edit_id):
    return _approve_or_reject_pending_edit(
        request, instance, request.user, pending_edit_id, True)


@require_http_methods(["POST"])
@instance_request
@creates_instance_user
@json_api_call
@login_required
def reject_pending_edit(request, instance, pending_edit_id):
    return _approve_or_reject_pending_edit(
        request, instance, request.user, pending_edit_id, False)


@require_http_methods(["DELETE"])
@json_api_call
@instance_request
@creates_instance_user
@login_required
@transaction.atomic
def remove_plot(request, instance, plot_id):
    plot = get_object_or_404(Plot, pk=plot_id, instance=instance)
    try:
        plot.delete_with_user(request.user)
        return {"ok": True}
    except AuthorizeException as e:
        raise PermissionDenied(e.msg)


@require_http_methods(["DELETE"])
@json_api_call
@instance_request
@creates_instance_user
@login_required
@transaction.atomic
def remove_current_tree_from_plot(request, instance, plot_id):
    plot = get_object_or_404(Plot, pk=plot_id, instance=instance)
    tree = plot.current_tree()

    if tree:
        try:
            tree.delete_with_user(request.user)
            updated_plot = Plot.objects.get(pk=plot_id)
            return context_dict_for_plot(request, updated_plot)
        except:
            raise PermissionDenied(
                '%s does not have permission to the '
                'current tree from plot %s' %
                (request.user.username, plot_id))
    else:
        raise HttpResponseBadRequest(
            "Plot %s does not have a current tree" % plot_id)


def add_photo(request, instance, plot_id):
    treephoto, __ = add_tree_photo_helper(request, instance, plot_id)

    return context_dict_for_photo(request, treephoto)


# Note that API requests going to private instances require
# authentication "login_optional" before they can access they
# instance data

instance_api_do = partial(do, csrf_exempt, check_signature, set_api_version,
                          instance_request, json_api_call)

api_do = partial(do, csrf_exempt, check_signature, set_api_version,
                 json_api_call)

logged_in_api_do = partial(do, csrf_exempt, set_api_version,
                           check_signature_and_require_login, json_api_call)

plots_closest_to_point_endpoint = instance_api_do(plots_closest_to_point)

instances_closest_to_point_endpoint = api_do(
    instances_closest_to_point)

public_instances_endpoint = api_do(public_instances)

instance_info_endpoint = instance_api_do(
    transform_instance_info_response,
    instance_info)

plots_endpoint = instance_api_do(
    route(GET=get_plot_list,
          POST=do(
              login_required,
              creates_instance_user,
              transform_plot_update_dict,
              return_400_if_validation_errors,
              update_or_create_plot)))


plot_endpoint = instance_api_do(
    route(GET=get_plot,
          ELSE=do(login_required,
                  creates_instance_user,
                  route(
                      PUT=do(return_400_if_validation_errors,
                             update_or_create_plot),
                      DELETE=remove_plot))))

species_list_endpoint = instance_api_do(
    route(GET=species_list))

user_endpoint = api_do(
    route(
        GET=do(login_required, transform_user_response, user_info),
        POST=do(
            transform_user_request,
            return_400_if_validation_errors,
            create_user)))

update_user_endpoint = logged_in_api_do(
    transform_user_request,
    return_400_if_validation_errors,
    transform_user_response,
    route(PUT=update_user))

add_photo_endpoint = logged_in_api_do(
    route(
        POST=do(
            instance_request,
            creates_instance_user,
            return_400_if_validation_errors,
            add_photo)))

status_view = api_do(route(GET=status))

version_view = api_do(route(GET=version))

update_profile_photo_endpoint = logged_in_api_do(
    require_http_method('POST'),
    return_400_if_validation_errors,
    update_profile_photo)

export_users_csv_endpoint = do(
    csrf_exempt,
    check_signature_and_require_login,
    set_api_version,
    admin_instance_request,
    route(GET=users_csv))

export_users_json_endpoint = do(
    csrf_exempt,
    check_signature_and_require_login,
    set_api_version,
    admin_instance_request,
    route(GET=users_json))

reset_password_endpoint = api_do(route(POST=reset_password))
