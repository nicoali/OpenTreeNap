import importlib.util
import json
from pathlib import Path
import unittest


path = Path(__file__).resolve().parents[1] / 'opentreemap' / 'treemap' / 'milano_benefits.py'
spec = importlib.util.spec_from_file_location('milano_benefits_test', str(path))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def payload(**changes):
    values = {
        'carbon_sto': '1529.7', 'carbon_s_1': '245.77',
        'gross_carb': '28.4', 'gross_ca_1': '4.56',
        'avoided_ru': '1.7', 'avoided_1': '3.2',
        'pollution_': '1428.2', 'pollutio_1': '78.91',
        'structural': '4785.53', 'total_annu': '86.66',
    }
    values.update(changes)
    return json.dumps({'benefits_unvalidated': values})


class MilanoBenefitsTests(unittest.TestCase):
    def test_maps_values_and_provenance(self):
        values = module.parse_historical_benefits(payload())
        self.assertEqual(values['Carbonio immagazzinato (kg)'], 1529.7)
        self.assertEqual(values['Inquinanti rimossi (g/anno)'], 1428.2)
        self.assertEqual(values['Benefici annuali totali (EUR/anno)'], 86.66)
        self.assertIn('i-Tree Eco', values[module.SOURCE_FIELD])
        self.assertIn('2011', values[module.REFERENCE_FIELD])
        self.assertEqual(set(values),
                         {name for _, name in module.BENEFIT_FIELDS} |
                         {module.SOURCE_FIELD, module.REFERENCE_FIELD})
        self.assertEqual(set(module.FIELD_DESCRIPTIONS), set(values))

    def test_rejects_missing_negative_and_non_finite_values(self):
        for changes in ({'carbon_sto': ''}, {'avoided_ru': '-1'}, {'pollution_': 'NaN'}):
            with self.assertRaises(ValueError):
                module.parse_historical_benefits(payload(**changes))

    def test_rejects_inconsistent_annual_total(self):
        with self.assertRaises(ValueError):
            module.parse_historical_benefits(payload(total_annu='99'))

    def test_json_null_is_not_a_benefit_payload(self):
        with self.assertRaisesRegex(ValueError, 'must be an object'):
            module.parse_historical_benefits('null')


if __name__ == '__main__':
    unittest.main()
