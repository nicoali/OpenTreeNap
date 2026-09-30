from django.conf import settings
from django.http import HttpResponse
from django.test import RequestFactory, SimpleTestCase
from django.utils import translation
from django.views.i18n import set_language, JavaScriptCatalog
from opentreemap.middleware import OpenTreeNapLocaleMiddleware
from treemap.branding import upgrade_legacy_colors


class RebrandingTests(SimpleTestCase):
    def tearDown(self):
        translation.deactivate()

    def test_italian_default_ignores_browser_language(self):
        request = RequestFactory().get('/napoli/map/?q=oak', HTTP_ACCEPT_LANGUAGE='en-US')
        middleware = OpenTreeNapLocaleMiddleware(lambda req: HttpResponse())
        middleware.process_request(request)
        self.assertEqual(request.LANGUAGE_CODE, 'it')
        self.assertEqual(translation.gettext('Log in'), 'Accedi')
        response = middleware.process_response(request, HttpResponse())
        self.assertEqual(response['Content-Language'], 'it')
        self.assertIn('Cookie', response['Vary'])

    def test_saved_english_and_invalid_choice(self):
        middleware = OpenTreeNapLocaleMiddleware(lambda req: HttpResponse())
        request = RequestFactory().get('/napoli/map/')
        request.COOKIES[settings.LANGUAGE_COOKIE_NAME] = 'en'
        middleware.process_request(request)
        self.assertEqual(translation.gettext('Log in'), 'Log in')
        request.COOKIES[settings.LANGUAGE_COOKIE_NAME] = 'unsupported'
        middleware.process_request(request)
        self.assertEqual(request.LANGUAGE_CODE, 'it')

    def test_language_post_preserves_map_url(self):
        target = '/napoli/map/?q=oak#details'
        request = RequestFactory().post('/i18n/setlang/', {'language': 'en', 'next': target})
        response = set_language(request)
        self.assertEqual(response['Location'], target)
        self.assertEqual(response.cookies[settings.LANGUAGE_COOKIE_NAME].value, 'en')
        request = RequestFactory().post('/i18n/setlang/', {'language': 'en', 'next': 'https://evil.example/'})
        self.assertNotIn('evil.example', set_language(request)['Location'])

    def test_javascript_catalog_italian(self):
        translation.activate('it')
        response = JavaScriptCatalog.as_view(packages=['treemap'])(RequestFactory().get('/jsi18n/'))
        self.assertIn('OpenTreeNap', response.content.decode())
        self.assertIn('gettext', response.content.decode())

    def test_upgrade_preserves_map_and_custom_branding(self):
        config = {'scss_variables': {'primary-color': '#8baa3d', 'secondary-color': '123456'}, 'map': {'center': [40.85, 14.26], 'zoom': 13}}
        updated = upgrade_legacy_colors(config)
        self.assertEqual(updated['scss_variables']['primary-color'], '557F2D')
        self.assertEqual(updated['scss_variables']['secondary-color'], '123456')
        self.assertEqual(updated['map'], config['map'])
        self.assertEqual(config['scss_variables']['primary-color'], '#8baa3d')
        self.assertEqual(upgrade_legacy_colors(updated), updated)
