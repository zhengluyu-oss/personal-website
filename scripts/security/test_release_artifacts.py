import contextlib
import gzip
import io
from pathlib import Path
import tempfile
import unittest
import zipfile
from check_release_artifacts import scan_jar, scan_dist, private_values

class ReleaseGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="blog-release-qa-")
        self.root = Path(self.temp.name)
    def tearDown(self):
        self.temp.cleanup()
    def jar(self, extra=None):
        path = self.root / "qa.jar"
        with zipfile.ZipFile(path, "w") as archive:
            for name in ["application.yml", "META-INF/spring.factories", "xyz/kuailemao/config/ProductionConfigurationGuard.class"]:
                archive.writestr("BOOT-INF/classes/" + name, b"safe")
            for name, data in (extra or {}).items():
                archive.writestr(name, data)
        return path
    def test_private_profile_and_stale_resource_are_rejected(self):
        findings=scan_jar(self.jar({"BOOT-INF/classes/application-dev.yml": b"LY_SECURITY_CANARY_PRIVATE"}))
        self.assertTrue(any(rule=="private-config-file" for _,rule in findings))
        self.assertTrue(any(rule=="synthetic-private-canary" for _,rule in findings))
        self.assertNotIn("LY_SECURITY_CANARY_PRIVATE", str(findings))
    def test_test_dependencies_are_not_runtime_libraries(self):
        findings=scan_jar(self.jar({"BOOT-INF/lib/mockito-core-5.jar": b"fake-library"}))
        self.assertTrue(any(rule=="test-only-runtime-dependency" for _,rule in findings))
        self.assertEqual([],scan_jar(self.jar()))
    def test_private_value_detection_does_not_echo_value(self):
        config=self.root/"private.yml"; config.write_text("password: fictional-supersecret-value\n",encoding="utf-8")
        findings=scan_jar(self.jar({"BOOT-INF/classes/banner.txt": b"fictional-supersecret-value"}),private_values([config]))
        self.assertTrue(any(rule=="known-local-private-value" for _,rule in findings))
        self.assertNotIn("fictional-supersecret-value",str(findings))
    def test_frontend_gzip_payload_is_scanned(self):
        (self.root/"index.html").write_text("safe",encoding="utf-8")
        (self.root/"index.js.gz").write_bytes(gzip.compress(b"LY_SECURITY_CANARY_FRONTEND"))
        findings=scan_dist(self.root)
        self.assertTrue(any(rule=="synthetic-private-canary" for _,rule in findings))
    def test_unbuilt_dist_and_missing_backend_guard_fail_closed(self):
        self.assertTrue(scan_dist(self.root))
        path=self.root/"old.jar"
        with zipfile.ZipFile(path,"w") as archive: archive.writestr("BOOT-INF/classes/application.yml","safe")
        self.assertTrue(any(rule=="missing-production-guard" for _,rule in scan_jar(path)))

if __name__=="__main__": unittest.main()
