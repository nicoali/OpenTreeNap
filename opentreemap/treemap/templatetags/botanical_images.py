# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django import template

from treemap.botanical_images import (
    botanical_image_url as resolve_botanical_image_url,
    botanical_page_url as resolve_botanical_page_url,
)

register = template.Library()


@register.simple_tag
def botanical_image_url(scientific_name):
    return resolve_botanical_image_url(scientific_name)


@register.simple_tag
def botanical_page_url(scientific_name):
    return resolve_botanical_page_url(scientific_name)
