# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.urls import re_path

from django_tinsel.decorators import route

from api.views import (status_view, version_view, public_instances_endpoint,
                       remove_current_tree_from_plot, plots_endpoint,
                       species_list_endpoint, approve_pending_edit,
                       reject_pending_edit, update_user_endpoint,
                       reset_password_endpoint, user_endpoint,
                       plot_endpoint, edits, plots_closest_to_point_endpoint,
                       instance_info_endpoint, add_photo_endpoint,
                       export_users_csv_endpoint, export_users_json_endpoint,
                       update_profile_photo_endpoint,
                       instances_closest_to_point_endpoint)

from treemap.instance import URL_NAME_PATTERN


instance_pattern = (r'^instance/(?P<instance_url_name>' +
                    URL_NAME_PATTERN + r')')

lat_lon_pattern = r'(?P<lat>-{0,1}\d+(\.\d+){0,1}),' \
                  r'(?P<lng>-{0,1}\d+(\.\d+){0,1})'

urlpatterns = [
    re_path(r'^$', status_view),
    re_path(r'^version$', version_view),

    re_path(r'^user$', user_endpoint, name='user_info'),
    re_path(r'^user/(?P<user_id>\d+)$', update_user_endpoint,
        name='update_user'),
    re_path(r'^user/(?P<user_id>\d+)/photo$', update_profile_photo_endpoint,
        name='update_user_photo'),
    re_path(r'^user/(?P<user_id>\d+)/edits$', edits),

    re_path(r'^send-password-reset-email$', reset_password_endpoint),

    re_path('^locations/' + lat_lon_pattern + '/instances',
        instances_closest_to_point_endpoint),

    re_path('^instances', public_instances_endpoint),

    re_path(instance_pattern + r'/plots/(?P<plot_id>\d+)/tree/photo$',
        add_photo_endpoint),

    re_path(instance_pattern + r'/plots/(?P<plot_id>\d+)/tree$',
        route(DELETE=remove_current_tree_from_plot)),

    re_path(instance_pattern + r'/pending-edits/(?P<pending_edit_id>\d+)/approve',
        route(POST=approve_pending_edit)),

    re_path(instance_pattern + r'/pending-edits/(?P<pending_edit_id>\d+)/reject',
        route(POST=reject_pending_edit)),

    # OTM2/instance endpoints
    re_path(instance_pattern + '$', instance_info_endpoint),
    re_path(instance_pattern + '/species$', species_list_endpoint),
    re_path(instance_pattern + r'/plots$', plots_endpoint),
    re_path(instance_pattern + r'/plots/(?P<plot_id>\d+)$',
        plot_endpoint),
    re_path(instance_pattern + r'/locations/' + lat_lon_pattern + '/plots',
        plots_closest_to_point_endpoint),
    re_path(instance_pattern + r'/users.csv',
        export_users_csv_endpoint, name='user_csv'),
    re_path(instance_pattern + r'/users.json',
        export_users_json_endpoint, name='user_json'),
]
