"""Presentation defaults shared by OpenTreeNap's legacy-branding upgrade."""
from copy import deepcopy

LEGACY_COLORS = {'primary-color': '8BAA3D', 'secondary-color': '56ABB2'}
OPENTREENAP_COLORS = {'primary-color': '557F2D', 'secondary-color': '4B9FBD'}


def upgrade_legacy_colors(config):
    """Return a copy, replacing only exact legacy defaults, never map data."""
    updated = deepcopy(config)
    colors = updated.get('scss_variables')
    if isinstance(colors, dict):
        for field, legacy in LEGACY_COLORS.items():
            value = colors.get(field)
            if isinstance(value, str) and value.lstrip('#').upper() == legacy:
                colors[field] = OPENTREENAP_COLORS[field]
    return updated


def localized_etag(value, request):
    """Keep translated fragments distinct across language, URL and UI changes."""
    import hashlib
    from django.utils.translation import get_language

    identity = '%s:otn-ui-3:%s:%s' % (value, get_language(), request.get_full_path())
    return hashlib.md5(identity.encode('utf-8')).hexdigest()
