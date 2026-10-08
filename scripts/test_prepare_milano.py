import copy
import struct
import unittest

import prepare_milano as m


def old(key, point=(9.18,45.47), taxon='Platanus x acerifolia'):
    record = dict.fromkeys(['categoria','tr_monumen','tr_height','tr_diamete','tr_diame_1',
        'structural','carbon_sto','carbon_s_1','gross_carb','gross_ca_1','avoided_ru',
        'avoided_1','pollution_','pollutio_1','total_annu'], '')
    record.update(objectid=key,sc_name_la=taxon,
        the_geom=struct.pack('<BII',1,0x20000001,4326).hex()+struct.pack('<dd',*point).hex())
    return record


def feature(key, point=(9.18,45.47), genus='Platanus', species='x acerifolia'):
    return {'properties':{'obj_id':key,'genere':genus,'specie':species},
            'geometry':{'type':'Point','coordinates':list(point)}}


class MergeTests(unittest.TestCase):
    def test_reassigned_ids_use_position(self):
        records = {'1':old('1',(9.2,45.48)),'2':old('2')}
        matches, statuses = m.spatial_matches([feature('1')], records)
        self.assertEqual(matches, {'1':'2'})
        status, enrichment, gap = m.enrich(feature('1')['properties'],[9.18,45.47],records['2'],True)
        self.assertEqual(status,'verified_spatial_taxon')
        self.assertEqual(enrichment['objectid'],'2')
        self.assertAlmostEqual(gap,0)

    def test_ambiguous_and_reverse_matches_not_merged(self):
        matches, statuses = m.spatial_matches([feature('3')],{'1':old('1'),'2':old('2')})
        self.assertEqual(matches,{})
        self.assertEqual(statuses['3'],'ambiguous_spatial_match')
        matches, statuses = m.spatial_matches([feature('3'),feature('4')],{'1':old('1')})
        self.assertEqual(matches,{})
        self.assertEqual(set(statuses.values()),{'ambiguous_reverse_match'})

    def test_different_species_and_distant_points_not_merged(self):
        matches, statuses = m.spatial_matches([feature('3',species='orientalis')],{'1':old('1')})
        self.assertEqual(matches,{})
        self.assertEqual(statuses['3'],'spatial_taxonomy_review')
        self.assertEqual(m.spatial_matches([feature('3',(9.3,45.5))],{'1':old('1')})[0],{})

    def test_decimal_and_null_values(self):
        self.assertEqual(m.positive('26.22'),26.22)
        self.assertEqual(m.positive('33.422538049298'),33.422538049298)
        for value in ('0','-1','NaN','Infinity',None,''):
            self.assertIsNone(m.positive(value))

    def test_coordinate_order_and_fingerprint(self):
        row = {'schema':'otn.milano.v1','source_date':'2024-03-31',
               'external_id':'milano:2024:1','coordinates':[9.18,45.47],
               'diameter_cm':70,'height_m':20,'crown_diameter_m':15}
        row['fingerprint'] = m.digest(row)
        m.validate_row(row)
        altered = copy.deepcopy(row)
        altered['height_m'] = 200
        with self.assertRaises(ValueError):
            m.validate_row(altered)
        swapped = {**row,'coordinates':[45.47,9.18]}
        swapped['fingerprint'] = m.digest({k:v for k,v in swapped.items() if k!='fingerprint'})
        with self.assertRaises(ValueError):
            m.validate_row(swapped)


if __name__ == '__main__':
    unittest.main()
