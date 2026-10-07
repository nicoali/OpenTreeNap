#!/usr/bin/env python3
"""Reproducible, conservative Milano 2024 merge. Python standard library only."""
import argparse
import collections
import csv
import hashlib
import io
import json
import math
from pathlib import Path
import re
import struct
import xml.etree.ElementTree as ET
import zipfile


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False)


def digest(value):
    return hashlib.sha256(canonical(value).encode('utf-8')).hexdigest()


def label(value):
    return ' '.join((value or '').replace('×', 'x').lower().split())


def legacy_label(value):
    return label(re.split("['\"‘’]", value or '')[0])


def positive(value):
    try:
        number = float(value)
    except (ValueError, TypeError):
        return None
    return number if math.isfinite(number) and number > 0 else None


def distance(a, b):
    lon1, lat1, lon2, lat2 = map(math.radians, (*a, *b))
    d = math.sin((lat2-lat1)/2)**2 + math.cos(lat1)*math.cos(lat2)*math.sin((lon2-lon1)/2)**2
    return 6371008.8 * 2 * math.asin(min(1, math.sqrt(d)))


def ewkb(value):
    data = bytes.fromhex(value)
    if len(data) != 25 or struct.unpack('<BII', data[:9]) != (1, 0x20000001, 4326):
        raise ValueError('Expected EWKB Point EPSG:4326')
    return struct.unpack('<dd', data[9:])


def legacy_rows(path):
    with zipfile.ZipFile(path) as archive:
        with zipfile.ZipFile(io.BytesIO(archive.read('alberi.xlsx'))) as book:
            ns = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
            strings = []
            with book.open('xl/sharedStrings.xml') as source:
                for _, element in ET.iterparse(source, events=('end',)):
                    if element.tag == ns+'si':
                        strings.append(''.join(t.text or '' for t in element.iter(ns+'t')))
                        element.clear()
            header = None
            with book.open('xl/worksheets/sheet1.xml') as source:
                for _, element in ET.iterparse(source, events=('end',)):
                    if element.tag == ns+'c':
                        if element.attrib.get('t') != 's':
                            raise ValueError('Unexpected legacy spreadsheet cell')
                        values = next(csv.reader([strings[int(element.find(ns+'v').text)]]))
                        if header is None:
                            header = values
                        else:
                            if len(values) != len(header):
                                raise ValueError('Malformed legacy CSV cell')
                            yield dict(zip(header, values))
                        element.clear()


def enrich(properties, coordinates, old, spatial=False):
    if old is None:
        return 'no_legacy_id', None, None
    gap = distance(coordinates, ewkb(old['the_geom']))
    if gap > 2:
        return 'id_position_conflict', None, gap
    scientific = ' '.join(str(properties.get(k) or '').strip() for k in ('genere', 'specie')).strip()
    if not scientific or label(scientific) != legacy_label(old['sc_name_la']):
        return 'taxonomy_review', None, gap
    return ('verified_spatial_taxon' if spatial else 'verified_id_position_taxon'), {
        'objectid': old['objectid'], 'scientific_name': old['sc_name_la'],
        'category': old['categoria'], 'monumental': old['tr_monumen'] == '1',
        'source': 'verde_Milano.zip/alberi.xlsx',
        'census_date': None, 'archive_file_year': 2020,
        'measurements_snapshot': {k: old[k] for k in ('tr_height', 'tr_diamete', 'tr_diame_1')},
        'benefits_unvalidated': {k: old[k] for k in (
            'structural', 'carbon_sto', 'carbon_s_1', 'gross_carb', 'gross_ca_1',
            'avoided_ru', 'avoided_1', 'pollution_', 'pollutio_1', 'total_annu')},
    }, gap


def spatial_matches(features, old):
    # A grid speeds up the 2m search. The smallest cell dimension in this
    # envelope exceeds 2m; checking neighbouring cells covers the full radius.
    def cell(coordinates):
        return tuple(math.floor(v / .00003) for v in coordinates)
    grid = collections.defaultdict(list)
    for key, record in old.items():
        coordinates = ewkb(record['the_geom'])
        grid[cell(coordinates)].append((key,coordinates,legacy_label(record['sc_name_la'])))
    proposals = {}
    uses = collections.Counter()
    statuses = {}
    for feature in features:
        p = feature['properties']
        key = str(p['obj_id']).strip()
        coordinates = feature['geometry']['coordinates']
        taxon = label(' '.join(str(p.get(k) or '').strip() for k in ('genere','specie')))
        cx, cy = cell(coordinates)
        candidates = []
        nearby = False
        for dx in (-1,0,1):
            for dy in (-1,0,1):
                for old_key, point, old_taxon in grid.get((cx+dx,cy+dy), ()):
                    if distance(coordinates,point) <= 2:
                        nearby = True
                        if taxon and taxon == old_taxon:
                            candidates.append(old_key)
        if len(candidates) == 1:
            proposals[key] = candidates[0]
            uses[candidates[0]] += 1
        elif candidates:
            statuses[key] = 'ambiguous_spatial_match'
        elif nearby:
            statuses[key] = 'spatial_taxonomy_review'
    matches = {key:old_key for key,old_key in proposals.items() if uses[old_key] == 1}
    statuses.update({key:'ambiguous_reverse_match' for key,old_key in proposals.items() if uses[old_key] > 1})
    return matches, statuses


def validate_row(row):
    if row.get('schema') != 'otn.milano.v1' or row.get('source_date') != '2024-03-31':
        raise ValueError('Unsupported dataset/schema')
    expected = digest({k:v for k,v in row.items() if k != 'fingerprint'})
    if row.get('fingerprint') != expected:
        raise ValueError('Record fingerprint mismatch')
    if not re.fullmatch(r'milano:2024:[0-9]+', row.get('external_id', '')):
        raise ValueError('Invalid external ID')
    coordinates = row['coordinates']
    if len(coordinates) != 2 or not all(isinstance(v, (int, float)) and math.isfinite(v) for v in coordinates):
        raise ValueError('Invalid coordinates')
    if not (9.0 <= coordinates[0] <= 9.4 and 45.3 <= coordinates[1] <= 45.7):
        raise ValueError('Coordinates outside Milano envelope')
    for key, maximum in (('diameter_cm', 508), ('height_m', 243.84), ('crown_diameter_m', None)):
        value = row[key]
        if value is not None and (positive(value) is None or maximum and value > maximum):
            raise ValueError('Invalid measurement: '+key)
    return row


def prepare(modern, legacy, output):
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    old = {}
    for record in legacy_rows(legacy):
        key = record['objectid']
        if key in old:
            raise ValueError('Duplicate legacy ID: '+key)
        old[key] = record
    with zipfile.ZipFile(modern) as archive:
        names = archive.namelist()
        if names != ['ds2484_alberi_20240331.geojson']:
            raise ValueError('Expected official 2024-03-31 GeoJSON')
        data = json.loads(archive.read(names[0]))
    counts = collections.Counter()
    flags = collections.Counter()
    species = collections.Counter()
    seen = set()
    matches, spatial_statuses = spatial_matches(data['features'], old)
    with (output/'legacy.original.jsonl').open('w', encoding='utf-8') as historical:
        for key in sorted(old, key=int):
            historical.write(canonical(old[key])+'\n')
    bounds = [180, 90, -180, -90]
    fields = ['external_id', 'longitude', 'latitude', 'genus', 'species', 'cultivar',
              'diameter_cm', 'height_m', 'crown_diameter_m', 'municipio', 'localita',
              'merge_status', 'legacy_monumental', 'legacy_category']
    with (output/'milano.otn.jsonl').open('w', encoding='utf-8') as target, \
            (output/'milano.normalized.csv').open('w', encoding='utf-8', newline='') as table, \
            (output/'merge_audit.csv').open('w', encoding='utf-8', newline='') as audit:
        writer = csv.DictWriter(table, fields)
        writer.writeheader()
        auditor = csv.writer(audit)
        auditor.writerow(['obj_id', 'merge_status', 'legacy_objectid', 'distance_m', 'same_id_status'])
        for feature in data['features']:
            p = feature['properties']
            key = str(p['obj_id']).strip()
            if key in seen:
                raise ValueError('Duplicate modern ID: '+key)
            seen.add(key)
            if feature['geometry']['type'] != 'Point':
                raise ValueError('Non-point geometry: '+key)
            coordinates = feature['geometry']['coordinates']
            same_id_status, _, _ = enrich(p, coordinates, old.get(key))
            matched_key = matches.get(key)
            if matched_key is not None:
                status, enrichment, gap = enrich(p,coordinates,old[matched_key],spatial=matched_key != key)
            else:
                status, enrichment, gap = spatial_statuses.get(key,'no_verified_legacy_match'),None,None
            counts[status] += 1
            auditor.writerow([key,status,matched_key or '', '' if gap is None else round(gap,4),same_id_status])
            values = {dst: positive(p.get(src)) for src, dst in (
                ('diam_tronc','diameter_cm'), ('h_m','height_m'), ('diam_chiom','crown_diameter_m'))}
            quality = []
            for measurement, maximum in (('diameter_cm',508), ('height_m',243.84)):
                if values[measurement] is not None and values[measurement] > maximum:
                    quality.append(measurement+'_outside_otn_limit')
                    values[measurement] = None
            for measurement, value in values.items():
                if value is None:
                    quality.append(measurement+'_missing_or_invalid')
            if values['height_m'] and values['height_m'] > 60:
                quality.append('height_over_60m_review')
            genus = str(p.get('genere') or '').strip()
            epithet = str(p.get('specie') or '').strip()
            if label(epithet) in ('spp', 'spp.', 'sp', 'sp.'):
                epithet = ''
            cultivar = str(p.get('varieta') or '').strip()
            if not genus:
                epithet, cultivar = '', ''
                quality.append('genus_missing')
            elif not epithet:
                quality.append('genus_only')
            taxon = {'genus':genus,'species':epithet,'cultivar':cultivar}
            species[canonical(taxon)] += 1
            flags.update(quality)
            row = {'schema':'otn.milano.v1','external_id':'milano:2024:'+key,
                   'source_date':'2024-03-31','coordinates':coordinates,
                   'taxon':taxon, **values, 'merge_status':status, 'quality_flags':quality,
                   'official_properties':p, 'legacy':enrichment}
            row['fingerprint'] = digest(row)
            validate_row(row)
            target.write(canonical(row)+'\n')
            writer.writerow(dict(external_id=row['external_id'],longitude=coordinates[0],
                latitude=coordinates[1],**taxon,**values,municipio=p.get('municipio'),
                localita=p.get('localita'),merge_status=status,
                legacy_monumental='' if enrichment is None else enrichment['monumental'],
                legacy_category='' if enrichment is None else enrichment['category']))
            bounds = [min(bounds[0],coordinates[0]),min(bounds[1],coordinates[1]),
                      max(bounds[2],coordinates[0]),max(bounds[3],coordinates[1])]
    with (output/'taxonomy_review.csv').open('w', encoding='utf-8', newline='') as target:
        writer = csv.writer(target)
        writer.writerow(['genus','species','cultivar','trees','otm_code_status'])
        for taxon, n in sorted(species.items()):
            p = json.loads(taxon)
            writer.writerow([p[k] for k in ('genus','species','cultivar')]+[n,'unmapped'])
    report = {'schema':'otn.milano.v1','instance_slug':'milano','source_date':'2024-03-31',
              'trees':len(seen),'legacy_trees':len(old),'merge':dict(counts),
              'legacy_absent_from_modern':len(set(old)-seen),'quality_flags':dict(flags),
              'taxon_combinations_including_unknown':len(species),'bounds_4326':bounds,
              'legacy_spatially_unmatched':len(old)-len(matches),
              'policy':'Modern inventory only; enrich position <=2m+exact taxon, unique in both directions; no stale-tree union',
              'inputs_sha256':{Path(p).name:hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in (modern,legacy)},
              'outputs_sha256':{name:hashlib.sha256((output/name).read_bytes()).hexdigest() for name in (
                  'legacy.original.jsonl','merge_audit.csv','milano.normalized.csv','milano.otn.jsonl','taxonomy_review.csv')}}
    (output/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--modern',required=True)
    parser.add_argument('--legacy',required=True)
    parser.add_argument('--output',required=True)
    args = parser.parse_args()
    prepare(args.modern,args.legacy,args.output)
