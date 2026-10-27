import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('catalog_read', Path(__file__).with_name('run-catalog-read.py'))
catalog = importlib.util.module_from_spec(spec)
spec.loader.exec_module(catalog)


class CatalogReadContractTest(unittest.TestCase):
    def setUp(self):
        self.fixture = {'runId': 'test', 'productCount': 100, 'saleProductIds': list(range(1, 101))}

    def test_observed_cache_setting_is_required_instead_of_assuming_the_requested_mode(self):
        self.assertTrue(catalog.enabled_metric('sale_cache_enabled 1.0\n'))
        self.assertFalse(catalog.enabled_metric('sale_cache_enabled 0.0\n'))
        with self.assertRaises(ValueError):
            catalog.enabled_metric('unrelated 1\n')

    def test_cold_mode_rejects_repeated_detail_ids_and_parallel_warmup(self):
        catalog.validate_settings(self.fixture, 'detail', 'cold', 100, 1)
        for iterations, vus in [(101, 1), (100, 5)]:
            with self.assertRaises(ValueError):
                catalog.validate_settings(self.fixture, 'detail', 'cold', iterations, vus)

    def test_duplicate_fixture_ids_cannot_be_reported_as_independent_cold_reads(self):
        self.fixture['saleProductIds'] = [1] * 100
        with self.assertRaises(ValueError):
            catalog.validate_settings(self.fixture, 'detail', 'cold', 100, 1)


if __name__ == '__main__':
    unittest.main()
