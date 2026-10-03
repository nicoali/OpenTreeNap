# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import json

import requests

from django.http import HttpResponse
from django.utils.translation import gettext as _
from django.conf import settings
from django.core.cache import cache
from django.contrib.gis.geos.point import Point

from django_tinsel.decorators import json_api_call

from omgeo import Geocoder
from omgeo.places import Viewbox, PlaceQuery
from omgeo.services.esri import EsriWGS


geocoder = Geocoder(sources=settings.OMGEO_SETTINGS)
ESRI_WGS = EsriWGS(settings=settings.OMGEO_SETTINGS[0][1]['settings'])


def _omgeo_candidate_to_dict(candidate, srid=3857):
    p = Point(candidate.x, candidate.y, srid=candidate.wkid)
    if candidate.wkid != srid:
        p.transform(srid)
    return {
        'address': candidate.match_addr,
        'region': candidate.match_region,
        'city': candidate.match_city,
        'srid': p.srid,
        'score': candidate.score,
        'x': p.x,
        'y': p.y,
        'type': candidate.locator_type,
    }


def _no_results_response(address, inregion=False):
    response = HttpResponse()
    response.status_code = 404

    if inregion:
        err = _("No results found in the area for %(address)s")
    else:
        err = _("No results found for %(address)s")

    content = {'error': err % {'address': address}}

    response.write(json.dumps(content))
    response['Content-length'] = str(len(response.content))
    response['Content-Type'] = "application/json"
    return response


def _in_bbox(bbox, c):
    x, y = c['x'], c['y']

    valid_x = x >= float(bbox['xmin']) and x <= float(bbox['xmax'])
    valid_y = y >= float(bbox['ymin']) and y <= float(bbox['ymax'])

    return valid_x and valid_y


def _contains_bbox(request):
    return ('xmin' in request.GET and 'ymin' in request.GET and
            'xmax' in request.GET and 'ymax' in request.GET)


def _get_viewbox_from_request(request):
    if _contains_bbox(request):
        xmin, ymin, xmax, ymax = [request.GET[b] for b
                                  in ['xmin', 'ymin', 'xmax', 'ymax']]
        return Viewbox(
            left=float(xmin),
            right=float(xmax),
            bottom=float(ymin),
            top=float(ymax),
            wkid=3857)
    else:
        return None


def geocode(request):
    """
    Search for specified address, returning candidates with lat/int
    """
    key = request.GET.get('key')
    address = request.GET.get('address').encode('utf-8')
    for_storage = 'forStorage' in request.GET

    if key:
        # See settings.OMGEO_SETTINGS for configuration
        pq = PlaceQuery(query=address, key=key, for_storage=for_storage)
        geocode_result = geocoder.geocode(pq)
        candidates = geocode_result.get('candidates', None)
        if candidates:
            # There should only be one candidate since the user already chose a
            # specific suggestion and the front end filters out suggestions
            # that might result in more than one candidate (like "Beaches").
            match = candidates[0]
            return {
                'lat': match.y,
                'lng': match.x
            }

    return _no_results_response(address)



def _json_error(message, status=502):
    response = HttpResponse(
        json.dumps({'error': message}),
        status=status,
        content_type='application/json'
    )
    response['Content-length'] = str(len(response.content))
    return response


def reverse_geocode(request):
    """
    Reverse geocode a WGS84 point through the server-side Esri client.

    The historical browser client called ArcGIS directly with JSONP and
    forStorage=true but without authentication. Modern ArcGIS requires an
    authenticated token when results are requested for storage, so proxy the
    request through Django and reuse the configured ESRI_CLIENT_ID / SECRET.
    The ArcGIS response shape is returned unchanged for legacy OTM clients.
    """
    try:
        lat = float(request.GET.get('lat'))
        lng = float(request.GET.get('lng'))
    except (TypeError, ValueError):
        return _json_error('lat and lng must be valid numbers', status=400)

    if not (-90.0 <= lat <= 90.0 and -180.0 <= lng <= 180.0):
        return _json_error('lat or lng is outside the valid range', status=400)

    try:
        distance = int(request.GET.get('distance', '200'))
    except (TypeError, ValueError):
        distance = 200
    distance = max(1, min(distance, 1000))

    token = cache.get('otn_esri_geocode_access_token')

    if not token:
        esri_settings = settings.OMGEO_SETTINGS[0][1].get('settings', {})
        client_id = esri_settings.get('client_id')
        client_secret = esri_settings.get('client_secret')

        if not client_id or not client_secret:
            return _json_error(
                'Esri credentials are not configured on the OTN server',
                status=503
            )

        try:
            token_response = requests.post(
                'https://www.arcgis.com/sharing/rest/oauth2/token',
                data={
                    'f': 'json',
                    'grant_type': 'client_credentials',
                    'client_id': client_id,
                    'client_secret': client_secret,
                },
                timeout=12
            )
            token_payload = token_response.json()
        except requests.RequestException as exc:
            return _json_error(
                'Esri token service unavailable: %s' % exc,
                status=503
            )
        except ValueError:
            return _json_error(
                'Esri token service returned invalid JSON',
                status=503
            )

        token = token_payload.get('access_token')

        if not token:
            error = token_payload.get('error') or {}
            if isinstance(error, dict):
                message = (
                    error.get('message') or
                    error.get('error_description') or
                    error.get('details')
                )
                code = error.get('code')
            else:
                message = str(error)
                code = None

            if not message:
                message = (
                    token_payload.get('error_description') or
                    token_payload.get('message') or
                    'access_token missing'
                )

            if isinstance(message, (list, tuple)):
                message = '; '.join([str(value) for value in message])

            if code is not None:
                message = '%s (code %s)' % (message, code)

            return _json_error(
                'Esri authentication failed: %s' % message,
                status=503
            )

        try:
            expires_in = int(token_payload.get('expires_in', 1800))
        except (TypeError, ValueError):
            expires_in = 1800

        cache.set(
            'otn_esri_geocode_access_token',
            token,
            max(60, expires_in - 60)
        )

    url = (
        'https://geocode.arcgis.com/arcgis/rest/services/'
        'World/GeocodeServer/reverseGeocode'
    )
    params = {
        'location': '%.8f,%.8f' % (lng, lat),
        'distance': distance,
        'outSR': 4326,
        'f': 'json',
        'forStorage': 'true',
        'langCode': 'it',
        'outFields': '*',
        'token': token,
    }

    try:
        upstream = requests.get(url, params=params, timeout=12)
        payload = upstream.json()
    except requests.RequestException as exc:
        return _json_error('Reverse geocoder unavailable: %s' % exc, status=502)
    except ValueError:
        return _json_error('Reverse geocoder returned invalid JSON', status=502)

    if upstream.status_code < 200 or upstream.status_code >= 300:
        return _json_error(
            'Reverse geocoder HTTP %s' % upstream.status_code,
            status=502
        )

    if isinstance(payload, dict) and payload.get('error'):
        error = payload.get('error') or {}
        message = error.get('message') if isinstance(error, dict) else str(error)
        return _json_error(
            'Reverse geocoder error: %s' % (message or 'unknown error'),
            status=502
        )

    if not isinstance(payload, dict) or not payload.get('address'):
        return _json_error('No reverse-geocode result found', status=404)

    return payload

def get_esri_token(request):
    return {'token': ESRI_WGS.get_token()}


geocode_view = json_api_call(geocode)
reverse_geocode_view = json_api_call(reverse_geocode)
get_esri_token_view = json_api_call(get_esri_token)
