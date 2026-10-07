import copy
import importlib.util
import json
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / 'opentreemap' / 'treemap' / 'DotDict.py'
spec = importlib.util.spec_from_file_location('otn_dotdict_compat', str(path))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
DotDict = module.DotDict


class DotDictCompatibilityTests(unittest.TestCase):
    def test_django_update_expression_probe(self):
        value = DotDict({'milano_import': {'schema': 'otn.milano.v1'}})
        self.assertFalse(hasattr(value, 'resolve_expression'))
        self.assertFalse(hasattr(value, 'as_sql'))
        self.assertIsNone(getattr(value, 'resolve_expression', None))
        with self.assertRaises(AttributeError):
            value.missing_attribute
        with self.assertRaises(KeyError):
            value['missing_attribute']

    def test_nested_access_and_serialization_remain_intact(self):
        value = DotDict({'value_display': {'tree': {'height': {'units': 'm'}}}})
        self.assertEqual(value.value_display.tree.height.units, 'm')
        self.assertEqual(value.get('value_display.tree.height.units'), 'm')
        value['milano_import.completed'] = True
        self.assertTrue(value.milano_import.completed)
        cloned = copy.deepcopy(value)
        cloned['value_display.tree.height.units'] = 'ft'
        self.assertEqual(value.value_display.tree.height.units, 'm')
        self.assertEqual(json.loads(json.dumps(value))['milano_import']['completed'], True)


if __name__ == '__main__':
    unittest.main()
