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

    def test_visible_labels_and_plural_counts(self):
        translation.activate('it')
        expected = {'Edit': 'Modifica', 'Common or Scientific Name': 'Nome comune o scientifico', 'Post comment': 'Pubblica commento', 'Yearly Ecosystem Services': 'Servizi ecosistemici annuali', 'Quick Edit': 'Modifica rapida', 'Stewardship': 'Interventi di cura'}
        for source, label in expected.items():
            self.assertEqual(translation.gettext(source), label)
        self.assertEqual(translation.ngettext('tree', 'trees', 637), 'alberi')
        self.assertEqual(translation.ngettext('Edit record', 'Edit records', 2), 'Modifiche')
        translation.activate('en')
        self.assertEqual(translation.gettext('Edit'), 'Edit')

    def test_translated_fragment_cache_varies_by_language_and_filter(self):
        from treemap.branding import localized_etag
        request = RequestFactory().get('/napoli/benefit/search?q=oak')
        translation.activate('it')
        italian = localized_etag('same-data-revision', request)
        self.assertEqual(italian, localized_etag('same-data-revision', request))
        translation.activate('en')
        self.assertNotEqual(italian, localized_etag('same-data-revision', request))
        translation.activate('it')
        different_filter = RequestFactory().get('/napoli/benefit/search?q=pine')
        self.assertNotEqual(italian, localized_etag('same-data-revision', different_filter))

    def test_benefit_view_translates_real_count_labels(self):
        from types import SimpleNamespace
        from unittest.mock import patch
        from treemap.views.tree import search_tree_benefits
        instance = SimpleNamespace(has_resources=False)
        with patch('treemap.views.tree.Filter'), patch('treemap.views.tree.get_cached_plot_count', return_value=638), patch('treemap.views.tree.get_benefits_for_filter', return_value=({}, {'plot': {'n_total': 637}})), patch('treemap.views.tree.format_benefits', return_value={}):
            translation.activate('it')
            context = search_tree_benefits(RequestFactory().get('/napoli/benefit/search'), instance)
        self.assertEqual(context['tree_count_label'], 'alberi,')
        self.assertEqual(context['empty_plot_count_label'], 'area di impianto vuota')

    def test_detail_etag_works_and_separates_languages(self):
        from types import SimpleNamespace
        from unittest.mock import patch
        from treemap.views.map_feature import map_feature_hash
        request = RequestFactory().get('/napoli/features/60/')
        request.user = SimpleNamespace(pk=1)
        with patch('treemap.views.map_feature.get_map_feature_or_404', return_value=SimpleNamespace(hash='feature-revision')):
            translation.activate('it')
            italian = map_feature_hash(request, object(), 60)
            translation.activate('en')
            self.assertNotEqual(italian, map_feature_hash(request, object(), 60))

    def test_information_page_omits_map_controls(self):
        from django.template import Context, engines
        template = engines['django'].engine.from_string('''{% extends "treemap/staticpage.html" %}{% block topnav %}{% endblock %}{% block header %}{% endblock %}{% block footer %}{% endblock %}{% block config_scripts %}{% endblock %}{% block global_scripts %}{% endblock %}{% block templates %}{% endblock %}''')
        html = template.render(Context({'title': 'Domande frequenti', 'content': '<h1>Domande frequenti</h1><p>Risposta</p>'}))
        self.assertIn('otn-information-page', html)
        self.assertIn('<p>Risposta</p>', html)
        for marker in ['stats-bar', 'exportBtn', 'species-typeahead', 'perform-search', 'searchBar']:
            self.assertNotIn(marker, html)
