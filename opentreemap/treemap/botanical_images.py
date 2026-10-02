# -*- coding: utf-8 -*-
from __future__ import unicode_literals

import json
import os
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


_LOCAL_MANIFEST_PATH = os.path.join(
    os.path.dirname(__file__),
    'static',
    'botanical-images.json'
)

_REMOTE_MANIFEST_URL = os.environ.get(
    'OTN_BOTANICAL_MANIFEST_URL',
    'https://opentreenap.altervista.org/'
    'wp-json/opentreenap/v1/botanical-images'
)

_RUNTIME_CACHE_PATH = os.environ.get(
    'OTN_BOTANICAL_CACHE_PATH',
    '/tmp/opentreenap-botanical-images.json'
)

_DEFAULT_TTL_SECONDS = 300
_MIN_TTL_SECONDS = 60
_MAX_TTL_SECONDS = 3600

_cache_lock = threading.Lock()
_cache_manifest = None
_cache_expires_at = 0.0


def _key(value):
    return (value or '').strip().casefold()


def _read_json(path):
    try:
        with open(path, 'r', encoding='utf-8') as handle:
            data = json.load(handle)

        if isinstance(data, dict) and isinstance(
                data.get('species'), dict):
            return data
    except (OSError, ValueError, TypeError):
        pass

    return None


def _write_runtime_cache(data):
    try:
        directory = os.path.dirname(_RUNTIME_CACHE_PATH)
        if directory and not os.path.isdir(directory):
            os.makedirs(directory)

        tmp_path = _RUNTIME_CACHE_PATH + '.tmp'

        with open(tmp_path, 'w', encoding='utf-8') as handle:
            json.dump(
                data,
                handle,
                ensure_ascii=False,
                separators=(',', ':')
            )

        os.replace(
            tmp_path,
            _RUNTIME_CACHE_PATH
        )
    except (OSError, TypeError, ValueError):
        # The remote manifest must never make a tree detail page fail.
        pass


def _fallback_manifest():
    runtime = _read_json(
        _RUNTIME_CACHE_PATH
    )

    if runtime is not None:
        return runtime

    local = _read_json(
        _LOCAL_MANIFEST_PATH
    )

    if local is not None:
        return local

    return {'species': {}}


def _remote_manifest():
    request = Request(
        _REMOTE_MANIFEST_URL,
        headers={
            'Accept': 'application/json',
            'User-Agent': 'OpenTreeNap-OTN/1.0',
        }
    )

    try:
        with urlopen(
                request,
                timeout=2.0) as response:
            raw = response.read().decode(
                'utf-8'
            )
    except (
            HTTPError,
            URLError,
            OSError,
            ValueError):
        return None

    try:
        data = json.loads(raw)
    except (ValueError, TypeError):
        return None

    if not isinstance(data, dict):
        return None

    if not isinstance(
            data.get('species'), dict):
        return None

    return data


def _ttl_for(manifest):
    try:
        ttl = int(
            manifest.get(
                'cache_ttl_seconds',
                _DEFAULT_TTL_SECONDS
            )
        )
    except (TypeError, ValueError):
        ttl = _DEFAULT_TTL_SECONDS

    return max(
        _MIN_TTL_SECONDS,
        min(
            ttl,
            _MAX_TTL_SECONDS
        )
    )


def _manifest():
    global _cache_manifest
    global _cache_expires_at

    now = time.time()

    if (
        _cache_manifest is not None and
        now < _cache_expires_at
    ):
        return _cache_manifest

    with _cache_lock:
        now = time.time()

        if (
            _cache_manifest is not None and
            now < _cache_expires_at
        ):
            return _cache_manifest

        remote = _remote_manifest()

        if remote is not None:
            manifest = remote
            _write_runtime_cache(
                remote
            )
        elif _cache_manifest is not None:
            # Stale-while-error: keep the last successful manifest instead
            # of dropping representative images because WordPress is
            # temporarily unreachable.
            manifest = _cache_manifest
        else:
            manifest = _fallback_manifest()

        _cache_manifest = manifest
        _cache_expires_at = (
            now + _ttl_for(manifest)
        )

        return manifest


def botanical_entry(scientific_name):
    return (
        _manifest()
        .get('species', {})
        .get(_key(scientific_name))
    )


def botanical_image_url(scientific_name):
    entry = botanical_entry(
        scientific_name
    )

    if not entry:
        return ''

    return (
        entry.get(
            'representative_image'
        ) or
        entry.get(
            'image_url'
        ) or
        ''
    )


def botanical_page_url(scientific_name):
    entry = botanical_entry(
        scientific_name
    )

    if not entry:
        return ''

    return entry.get(
        'page_url',
        ''
    )


def botanical_gallery(scientific_name):
    entry = botanical_entry(
        scientific_name
    )

    if not entry:
        return []

    gallery = entry.get(
        'gallery',
        []
    )

    return (
        gallery
        if isinstance(gallery, list)
        else []
    )
