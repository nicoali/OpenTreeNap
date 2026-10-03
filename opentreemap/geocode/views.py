# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import json
import logging
import time

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
logger = logging.getLogger(__name__)


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


def _reverse_geocode_nominatim(lat, lng):
    """Light, user-triggered OSM fallback with caching and <= 1 req/s."""
    cache_key = 'otn:nominatim:reverse:%.5f:%.5f' % (lat, lng)
    cached = cache.get(cache_key)
    if cached:
        return cached

    # OSMF public Nominatim policy requires an absolute maximum of one
    # request per second per application. Redis-backed cache.add is atomic,
    # so this also coordinates the two Gunicorn workers.
    lock_key = 'otn:nominatim:rate-lock'
    acquired = False
    for _ in range(20):
        if cache.add(lock_key, '1', timeout=1):
            acquired = True
            break
        time.sleep(0.1)

    if not acquired:
        raise RuntimeError('Nominatim rate limiter timeout')

    headers = {
        'User-Agent': settings.NOMINATIM_USER_AGENT,
        'Accept': 'application/json',
        'Accept-Language': 'it',
    }
    params = {
        'lat': '%.8f' % lat,
        'lon': '%.8f' % lng,
        'format': 'jsonv2',
        'addressdetails': 1,
        'zoom': 18,
    }

    response = requests.get(
        settings.NOMINATIM_REVERSE_URL,
        params=params,
        headers=headers,
        timeout=12
    )
    response.raise_for_status()
    payload = response.json()

    address = payload.get('address') or {}
    road = (
        address.get('road') or
        address.get('pedestrian') or
        address.get('footway') or
        address.get('path') or
        address.get('residential')
    )
    house_number = address.get('house_number')
    street = road
    if road and house_number:
        street = '%s, %s' % (road, house_number)

    city = (
        address.get('city') or
        address.get('town') or
        address.get('village') or
        address.get('municipality') or
        address.get('county')
    )
    postal = address.get('postcode')
    region = (
        address.get('state') or
        address.get('region') or
        address.get('state_district') or
        address.get('county')
    )
    display_name = payload.get('display_name')

    if not any([street, city, region, postal, display_name]):
        return None

    result = {
        'address': {
            'Address': street or '',
            'City': city or '',
            'Region': region or '',
            'Postal': postal or '',
            'LongLabel': display_name or '',
        },
        'location': {
            'x': lng,
            'y': lat,
        },
        '_provider': 'OpenStreetMap Nominatim',
        '_attribution': '© OpenStreetMap contributors',
    }

    # Reverse-geocode results for tree-placement points change slowly and
    # caching avoids repeated calls for the same location.
    cache.set(cache_key, result, 60 * 60 * 24 * 30)
    return result

def reverse_geocode(request):
    """Reverse geocode a WGS84 point, preferring Esri and falling back to OSM."""
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

    esri_settings = settings.OMGEO_SETTINGS[0][1].get('settings', {})
    client_id = esri_settings.get('client_id')
    client_secret = esri_settings.get('client_secret')

    if client_id and client_secret:
        token = cache.get('otn_esri_geocode_access_token')

        if not token:
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
                token = token_payload.get('access_token')
            except (requests.RequestException, ValueError) as exc:
                token = None
                logger.warning('Esri token request failed: %s', exc)

            if token:
                try:
                    expires_in = int(token_payload.get('expires_in', 1800))
                except (TypeError, ValueError):
                    expires_in = 1800
                cache.set(
                    'otn_esri_geocode_access_token',
                    token,
                    max(60, expires_in - 60)
                )
            else:
                logger.warning(
                    'Esri credentials configured but no access_token returned; '
                    'falling back to Nominatim'
                )

        if token:
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
                if (
                    200 <= upstream.status_code < 300 and
                    isinstance(payload, dict) and
                    payload.get('address') and
                    not payload.get('error')
                ):
                    payload['_provider'] = 'Esri ArcGIS'
                    return payload
                logger.warning('Esri reverse geocode failed; using OSM fallback')
            except (requests.RequestException, ValueError) as exc:
                logger.warning('Esri reverse geocode failed: %s', exc)

    try:
        payload = _reverse_geocode_nominatim(lat, lng)
    except (requests.RequestException, ValueError, RuntimeError) as exc:
        return _json_error(
            'Reverse geocoder unavailable: %s' % exc,
            status=502
        )

    if payload:
        return payload

    return _json_error('No reverse-geocode result found', status=404)

def get_esri_token(request):
    return {'token': ESRI_WGS.get_token()}


geocode_view = json_api_call(geocode)
reverse_geocode_view = json_api_call(reverse_geocode)
get_esri_token_view = json_api_call(get_esri_token)
