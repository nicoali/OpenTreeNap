from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import json
import requests
import os
from urllib.parse import urlencode
from unittest import skipIf
from unittest.mock import patch

from django.http import HttpResponse
from django.test.client import RequestFactory

from treemap.tests.base import OTMTestCase

from geocode.views import geocode, reverse_geocode


class MockGeocodeRequest():
    def __init__(self, address, key):
        self.GET = {
            'address': address,
            'key': key
        }


class GeocodeTest(OTMTestCase):

    def setUp(self):
        self.factory = RequestFactory()

    @skipIf('ESRI_CLIENT_ID' not in os.environ
            or 'ESRI_CLIENT_SECRET' not in os.environ,
            'Set Esri Client ID & Secret to run authenticated geocode tests')
    def test_azavea_office_geocodes_correctly(self):
        extent = {
            'xmin': -8475485,
            'xmax': -8280250,
            'ymin': 4643135,
            'ymax': 4954810,
        }

        # Fetch suggestions from ESRI suggestion engine (as the front end does)
        url = (
            'https://geocode.arcgis.com/arcgis/rest/services/'
            'World/GeocodeServer/suggest?' +
            urlencode({
                'f': 'json',
                'searchExtent': json.dumps(dict({
                    'spatialReference': {'wkid': 102100}
                }, **extent)),
                'text': '340 n 12th st philadelphia'
            }))
        result = json.loads(requests.get(url).content)

        self.assertIn('suggestions', result,
                      'The reponse should have a "suggestions" property')
        self.assertTrue(len(result['suggestions']) > 0,
                        'There should be one or more suggestions')
        suggestion = result['suggestions'][0]
        self.assertIn('text', suggestion,
                      'The suggestion should have a "text" property')
        self.assertIn('magicKey', suggestion,
                      'The suggestion should have a "magicKey" property')

        # Geocode first suggestion
        res = geocode(MockGeocodeRequest(
            suggestion['text'], suggestion['magicKey']))

        self.assertNotIn('error', res,
                         'The response should not have an error property')
        self.assertIn('lat', res, 'The reponse should have a "lat" property')
        self.assertIn('lng', res, 'The reponse should have a "lng" property')
        self.assertTrue(abs(res['lat'] - 39.958750) < .00001,
                        'Latitude not as expected')
        self.assertTrue(abs(res['lng'] - (-75.158416)) < .00001,
                        'Longitude not as expected')

    def test_geocoding_without_magic_key_returns_404(self):
        res = geocode(MockGeocodeRequest('', ''))

        self.assertTrue(isinstance(res, HttpResponse),
                        'View should return HttpResponse on 404')
        self.assertEqual(404, res.status_code,
                         'Status code should be 404 Not Found')

        response_json = json.loads(res.content)
        self.assertIn('error', response_json,
                      'The response body should have an "error" property')


class ReverseGeocodeTest(OTMTestCase):

    def setUp(self):
        self.factory = RequestFactory()

    def test_reverse_geocode_rejects_invalid_coordinates(self):
        request = self.factory.get('/geocode/reverse-geocode', {
            'lat': 'not-a-number',
            'lng': '14.2681',
        })
        response = reverse_geocode(request)
        self.assertEqual(400, response.status_code)

    @patch('geocode.views.requests.get')
    @patch('geocode.views.ESRI_WGS.get_token')
    def test_reverse_geocode_uses_authenticated_server_proxy(
            self, get_token, requests_get):
        get_token.return_value = 'test-token'

        class MockResponse(object):
            status_code = 200

            def json(self):
                return {
                    'address': {
                        'Address': 'Via Toledo, 1',
                        'City': 'Napoli',
                        'Postal': '80134',
                    },
                    'location': {
                        'x': 14.2491,
                        'y': 40.8401,
                    },
                }

        requests_get.return_value = MockResponse()
        request = self.factory.get('/geocode/reverse-geocode', {
            'lat': '40.8401',
            'lng': '14.2491',
        })

        response = reverse_geocode(request)

        self.assertEqual('Via Toledo, 1', response['address']['Address'])
        self.assertEqual('Napoli', response['address']['City'])
        self.assertEqual('80134', response['address']['Postal'])

        kwargs = requests_get.call_args[1]
        self.assertEqual('true', kwargs['params']['forStorage'])
        self.assertEqual('test-token', kwargs['params']['token'])
        self.assertEqual(12, kwargs['timeout'])
