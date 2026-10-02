<?php
/**
 * Plugin Name: OpenTreeNap Botanical Manifest
 * Description: Espone un manifest REST automatico delle immagini botaniche rappresentative pubblicate nella Media Library di OpenTreeNap.
 * Version: 1.0.0
 * Author: OpenTreeNap
 */

if (!defined('ABSPATH')) {
    exit;
}

define(
    'OTN_BOTANICAL_MANIFEST_ROUTE',
    'opentreenap/v1'
);

define(
    'OTN_BOTANICAL_MANIFEST_TTL',
    300
);

/**
 * Normalize a media filename to the stem WordPress users see.
 */
function otn_botanical_media_stem($attachment_id) {
    $file = get_attached_file($attachment_id);

    if (!$file) {
        $url = wp_get_attachment_url($attachment_id);
        if (!$url) {
            return '';
        }
        $file = wp_parse_url($url, PHP_URL_PATH);
    }

    $name = pathinfo(wp_basename($file), PATHINFO_FILENAME);

    // Ignore a WordPress generated dimension suffix if the uploaded file
    // itself already contains one.
    $name = preg_replace('/-\d+x\d+$/i', '', $name);

    return sanitize_title($name);
}

/**
 * Return [species_slug, role] for files that follow the OpenTreeNap naming
 * convention. Representative images are completely automatic.
 *
 * Examples:
 *   quercus-ilex-immagine-rappresentativa.webp
 *   pinus-pinea-immagine-rappresentativa-1.webp
 *   quercus-ilex-foglia.webp
 *   quercus-ilex-frutto-2.webp
 */
function otn_botanical_parse_media_name($stem) {
    if (!$stem) {
        return null;
    }

    $patterns = array(
        'representative' => '/^(.+?)-immagine-rappresentativa(?:-\d+)?$/i',
        'leaf'           => '/^(.+?)-foglia(?:-\d+)?$/i',
        'fruit'          => '/^(.+?)-frutto(?:-\d+)?$/i',
        'flower'         => '/^(.+?)-fiore(?:-\d+)?$/i',
        'bark'           => '/^(.+?)-corteccia(?:-\d+)?$/i',
        'habit'          => '/^(.+?)-portamento(?:-\d+)?$/i',
        'gallery'        => '/^(.+?)-galleria(?:-\d+)?$/i',
    );

    foreach ($patterns as $role => $regex) {
        if (preg_match($regex, $stem, $matches)) {
            return array(
                'slug' => sanitize_title($matches[1]),
                'role' => $role,
            );
        }
    }

    return null;
}

/**
 * Convert a species slug to a scientific name when no explicit override is
 * present. This intentionally covers the common binomial case used by OTN.
 */
function otn_botanical_scientific_name_from_slug($slug) {
    $parts = array_values(array_filter(explode('-', $slug)));

    if (!$parts) {
        return '';
    }

    $name_parts = array();

    foreach ($parts as $index => $part) {
        if (strtolower($part) === 'x') {
            $name_parts[] = '×';
            continue;
        }

        $part = strtolower($part);

        if ($index === 0) {
            $part = ucfirst($part);
        }

        $name_parts[] = $part;
    }

    return implode(' ', $name_parts);
}

/**
 * Pick one stable, web-friendly source URL and append the attachment modified
 * timestamp so app and OTN invalidate stale image caches after replacement.
 */
function otn_botanical_attachment_url($attachment_id) {
    $url = wp_get_attachment_image_url($attachment_id, 'large');

    if (!$url) {
        $url = wp_get_attachment_url($attachment_id);
    }

    if (!$url) {
        return '';
    }

    $version = get_post_modified_time('U', true, $attachment_id);

    if ($version) {
        $url = add_query_arg('v', $version, $url);
    }

    return esc_url_raw($url);
}

/**
 * Optional exact scientific-name override.
 *
 * If a filename is not enough (hybrids, subspecies, cultivars), set the
 * attachment custom field _otn_scientific_name. Normal binomials need no
 * manual metadata.
 */
function otn_botanical_attachment_scientific_name($attachment_id, $slug) {
    $override = trim(
        (string) get_post_meta(
            $attachment_id,
            '_otn_scientific_name',
            true
        )
    );

    if ($override !== '') {
        return $override;
    }

    return otn_botanical_scientific_name_from_slug($slug);
}

/**
 * Build the public manifest from Media Library attachments.
 */
function otn_botanical_build_manifest() {
    $attachment_ids = get_posts(array(
        'post_type'      => 'attachment',
        'post_mime_type' => 'image',
        'post_status'    => 'inherit',
        'posts_per_page' => -1,
        'fields'         => 'ids',
        'orderby'        => 'modified',
        'order'          => 'ASC',
        'no_found_rows'  => true,
    ));

    $candidates = array();
    $latest_modified = 0;

    foreach ($attachment_ids as $attachment_id) {
        $stem = otn_botanical_media_stem($attachment_id);
        $parsed = otn_botanical_parse_media_name($stem);

        if (!$parsed) {
            continue;
        }

        $url = otn_botanical_attachment_url($attachment_id);

        if (!$url) {
            continue;
        }

        $modified = (int) get_post_modified_time(
            'U',
            true,
            $attachment_id
        );

        $latest_modified = max(
            $latest_modified,
            $modified
        );

        $slug = $parsed['slug'];
        $role = $parsed['role'];

        if (!isset($candidates[$slug])) {
            $candidates[$slug] = array();
        }

        if (!isset($candidates[$slug][$role])) {
            $candidates[$slug][$role] = array();
        }

        $candidates[$slug][$role][] = array(
            'attachment_id' => (int) $attachment_id,
            'url'           => $url,
            'modified'      => $modified,
        );
    }

    $species = array();

    foreach ($candidates as $slug => $roles) {
        if (empty($roles['representative'])) {
            // A representative image is the contract that activates a
            // species in the shared manifest.
            continue;
        }

        usort(
            $roles['representative'],
            function ($a, $b) {
                return $b['modified'] <=> $a['modified'];
            }
        );

        $representative = $roles['representative'][0];

        $scientific_name =
            otn_botanical_attachment_scientific_name(
                $representative['attachment_id'],
                $slug
            );

        if (!$scientific_name) {
            continue;
        }

        $gallery = array();

        foreach ($roles as $role => $items) {
            usort(
                $items,
                function ($a, $b) {
                    return $a['modified'] <=> $b['modified'];
                }
            );

            foreach ($items as $item) {
                $gallery[] = array(
                    'role'          => $role,
                    'attachment_id' => $item['attachment_id'],
                    'url'           => $item['url'],
                );
            }
        }

        $key = strtolower($scientific_name);

        $species[$key] = array(
            'scientific_name'       => $scientific_name,
            'slug'                  => $slug,
            'representative_image'  => $representative['url'],
            // Backward compatibility with Sprint 3 app/backend v1.
            'image_url'             => $representative['url'],
            'page_url'              => home_url('/specie/' . $slug . '/'),
            'gallery'               => $gallery,
        );
    }

    ksort($species);

    $version_seed = wp_json_encode(
        $species,
        JSON_UNESCAPED_SLASHES |
        JSON_UNESCAPED_UNICODE
    );

    $version = sha1((string) $version_seed);

    return array(
        'schema_version'    => 2,
        'version'           => $version,
        'source'            => home_url('/'),
        'updated'           => $latest_modified
            ? gmdate('c', $latest_modified)
            : gmdate('c'),
        'cache_ttl_seconds' => OTN_BOTANICAL_MANIFEST_TTL,
        'species'           => $species,
    );
}

function otn_botanical_manifest_rest_callback($request) {
    $manifest = otn_botanical_build_manifest();
    $etag = '"' . $manifest['version'] . '"';

    $if_none_match = trim(
        (string) $request->get_header('if-none-match')
    );

    if ($if_none_match === $etag) {
        $response = new WP_REST_Response(
            null,
            304
        );
    } else {
        $response = rest_ensure_response(
            $manifest
        );
    }

    $response->header(
        'Cache-Control',
        'public, max-age=' .
        OTN_BOTANICAL_MANIFEST_TTL .
        ', stale-while-revalidate=86400'
    );

    $response->header(
        'ETag',
        $etag
    );

    return $response;
}

add_action(
    'rest_api_init',
    function () {
        register_rest_route(
            OTN_BOTANICAL_MANIFEST_ROUTE,
            '/botanical-images',
            array(
                'methods'             => WP_REST_Server::READABLE,
                'callback'            => 'otn_botanical_manifest_rest_callback',
                'permission_callback' => '__return_true',
            )
        );
    }
);

/**
 * Small optional attachment field for exceptional taxonomy names.
 */
add_filter(
    'attachment_fields_to_edit',
    function ($fields, $post) {
        $fields['otn_scientific_name'] = array(
            'label' => 'OTN nome scientifico',
            'input' => 'text',
            'value' => get_post_meta(
                $post->ID,
                '_otn_scientific_name',
                true
            ),
            'helps' => 'Solo per casi speciali. Lascia vuoto per derivarlo automaticamente dal nome file.',
        );

        return $fields;
    },
    10,
    2
);

add_filter(
    'attachment_fields_to_save',
    function ($post, $attachment) {
        if (isset($attachment['otn_scientific_name'])) {
            $value = sanitize_text_field(
                $attachment['otn_scientific_name']
            );

            if ($value === '') {
                delete_post_meta(
                    $post['ID'],
                    '_otn_scientific_name'
                );
            } else {
                update_post_meta(
                    $post['ID'],
                    '_otn_scientific_name',
                    $value
                );
            }
        }

        return $post;
    },
    10,
    2
);
