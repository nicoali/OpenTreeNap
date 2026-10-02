# -*- coding: utf-8 -*-
from __future__ import unicode_literals

import json
import os
from functools import lru_cache


_MANIFEST_PATH = os.path.join(
    os.path.dirname(__file__),
    'static',
    'botanical-images.json'
)


def _key(value):
    return (value or '').strip().casefold()


@lru_cache(maxsize=1)
def _manifest():
    try:
        with open(_MANIFEST_PATH, 'r', encoding='utf-8') as handle:
            return json.load(handle)
    except (OSError, ValueError):
        return {'species': {}}


def botanical_entry(scientific_name):
    return _manifest().get('species', {}).get(_key(scientific_name))


def botanical_image_url(scientific_name):
    entry = botanical_entry(scientific_name)
    if not entry:
        return ''
    return entry.get('image_url', '')


def botanical_page_url(scientific_name):
    entry = botanical_entry(scientific_name)
    if not entry:
        return ''
    return entry.get('page_url', '')
