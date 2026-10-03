from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.urls import re_path
from geocode.views import (geocode_view, reverse_geocode_view,
                           get_esri_token_view)

urlpatterns = [
    re_path(r'^geocode
    re_path(r'^get-geocode-token$', get_esri_token_view, name='get_geocode_token')
]
, geocode_view, name='geocode'),
    re_path(r'^reverse-geocode
    re_path(r'^get-geocode-token$', get_esri_token_view, name='get_geocode_token')
]
, reverse_geocode_view, name='reverse_geocode'),
    re_path(r'^get-geocode-token$', get_esri_token_view, name='get_geocode_token')
]
