import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import dependency_inventory as inventory


class DependencyInventoryTest(unittest.TestCase):
    def test_pnpm_v6_and_v9_scoped_peer_coordinates(self):
        for version, coordinate in [('6.0', '/@scope/pkg@1.2.3(vue@3.5.0)'), ('9.0', '@scope/pkg@1.2.3')]:
            with self.subTest(version=version), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                lock = root / 'pnpm-lock.yaml'
                lock.write_text(f"lockfileVersion: '{version}'\npackages:\n  '{coordinate}':\n    dev: true\n", encoding='utf-8')
                with patch.object(inventory, 'ROOT', root):
                    rows = inventory.lock_inventory(lock)
                self.assertEqual(rows[0]['coordinates'], [{'ecosystem': 'npm', 'name': '@scope/pkg', 'version': '1.2.3'}])
                self.assertEqual(rows[0]['scope'], 'dev-only')

    def test_real_locks_are_pnpm_and_match_declared_axios(self):
        for project in ['kuailemao-blog', 'kuailemao-admin']:
            root = inventory.ROOT / 'blog-frontend' / project
            manifest = json.loads((root / 'package.json').read_text(encoding='utf-8'))
            self.assertTrue(manifest['packageManager'].startswith('pnpm@'))
            self.assertFalse((root / 'package-lock.json').exists())
            rows = inventory.lock_inventory(root / 'pnpm-lock.yaml')
            self.assertTrue(any(c['name'] == 'axios' and c['version'] == manifest['dependencies']['axios'] for r in rows for c in r['coordinates']))

    def test_jar_tree_resolves_missing_metadata_and_flags_unknown(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            nested = io.BytesIO()
            with zipfile.ZipFile(nested, 'w') as library:
                library.writestr('example.class', b'synthetic')
            jar = root / 'app.jar'
            with zipfile.ZipFile(jar, 'w') as archive:
                for name in ['example-1.0.jar', 'unknown-1.0.jar']:
                    archive.writestr('BOOT-INF/lib/' + name, nested.getvalue())
            tree = root / 'tree.txt'
            tree.write_text('+- org.example:example:jar:1.0:compile\n', encoding='utf-8')
            rows = inventory.jar_inventory(jar, tree)
            self.assertEqual(rows[0]['coordinates'][0]['name'], 'org.example:example')
            self.assertEqual(rows[1]['coordinateStatus'], 'unresolved-review-required')

    def test_osv_sends_only_public_coordinates_and_flags_pagination(self):
        rows = [{'source': 'private/local/path', 'secret': 'synthetic-do-not-send',
                 'coordinates': [{'ecosystem': 'npm', 'name': 'axios', 'version': '1.20.0'}]}]
        response = io.BytesIO(json.dumps({'results': [{'next_page_token': 'more'}]}).encode())
        with patch.object(inventory.urllib.request, 'urlopen', return_value=response) as request:
            result = inventory.audit_osv(rows)
        sent = json.loads(request.call_args.args[0].data)
        self.assertEqual(sent, {'queries': [{'package': {'ecosystem': 'npm', 'name': 'axios'}, 'version': '1.20.0'}]})
        self.assertTrue(result[0]['paginationPending'])

    def test_incomplete_osv_response_cannot_pass(self):
        rows = [{'coordinates': [{'ecosystem': 'npm', 'name': 'axios', 'version': '1.20.0'}]}]
        with patch.object(inventory.urllib.request, 'urlopen', return_value=io.BytesIO(b'{"results": []}')):
            with self.assertRaisesRegex(ValueError, 'Incomplete'):
                inventory.audit_osv(rows)


if __name__ == '__main__':
    unittest.main()
