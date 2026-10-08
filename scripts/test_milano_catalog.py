import importlib.util
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / 'opentreemap' / 'treemap' / 'milano_catalog.py'
spec = importlib.util.spec_from_file_location('milano_catalog_test',str(path))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class FakeCatalog:
    def __init__(self):
        self.rows = []

    def filter(self, **fields):
        rows = [r for r in self.rows if all(getattr(r,k)==v for k,v in fields.items())]
        class Query:
            def first(self):
                return rows[0] if rows else None
        return Query()


class FakeSpecies:
    objects = FakeCatalog()

    def __init__(self, **fields):
        self.__dict__.update(fields)

    def save_with_user(self,user):
        keys = ('instance','common_name','genus','species','cultivar','other_part_of_name')
        for row in self.objects.rows:
            if all(getattr(row,k)==getattr(self,k) for k in keys):
                raise ValueError('OTN catalog unique identity violated')
        self.objects.rows.append(self)


class CatalogTests(unittest.TestCase):
    def setUp(self):
        FakeSpecies.objects = FakeCatalog()
        self.taxon = {'genus':'Magnolia','species':'','cultivar':''}

    def seed(self,instance=3):
        row = FakeSpecies(instance=instance,common_name='Magnolia',genus='Magnolia',
                          species='',cultivar='',other_part_of_name='',otm_code='NATIVE')
        row.save_with_user(None)
        return row

    def test_native_seed_reused_and_code_preserved(self):
        seed = self.seed()
        row = module.resolve_species(FakeSpecies,3,None,self.taxon,'MILANO_LOCAL_TEST')
        self.assertIs(row,seed)
        self.assertEqual(row.otm_code,'NATIVE')
        self.assertEqual(len(FakeSpecies.objects.rows),1)

    def test_other_tenant_not_reused_and_rerun_no_duplicate(self):
        self.seed(instance=1)
        row = module.resolve_species(FakeSpecies,3,None,self.taxon,'MILANO_LOCAL_TEST')
        self.assertEqual(row.instance,3)
        self.assertEqual(row.otm_code,'MILANO_LOCAL_TEST')
        rerun = module.resolve_species(FakeSpecies,3,None,self.taxon,'MILANO_LOCAL_TEST')
        self.assertIs(row,rerun)
        self.assertEqual(len(FakeSpecies.objects.rows),2)

    def test_distinct_species_not_collapsed_to_genus(self):
        seed = self.seed()
        taxon = {**self.taxon,'species':'grandiflora'}
        row = module.resolve_species(FakeSpecies,3,None,taxon,'MILANO_LOCAL_GRANDIFLORA')
        self.assertIsNot(row,seed)
        self.assertEqual(row.species,'grandiflora')


if __name__ == '__main__':
    unittest.main()
