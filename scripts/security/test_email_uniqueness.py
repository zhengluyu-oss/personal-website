"""Real MySQL checks, exclusively against the named disposable local QA container."""
import concurrent.futures
import json
from pathlib import Path
import subprocess
import unittest
import uuid

import pymysql

CONTAINER = "luyu-security-qa-mysql-20261002"
ROOT = Path(__file__).resolve().parents[2]


class EmailUniquenessMigrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        inspected = subprocess.run(["docker", "inspect", CONTAINER], capture_output=True, text=True, check=True)
        container = json.loads(inspected.stdout)[0]
        labels = container["Config"]["Labels"]
        if labels.get("codex.task") != "blog-security-qa" or labels.get("codex.disposable") != "true":
            raise RuntimeError("Refusing a non-disposable database")
        binding = container["NetworkSettings"]["Ports"]["3306/tcp"]
        if binding != [{"HostIp": "127.0.0.1", "HostPort": "13306"}]:
            raise RuntimeError("Unexpected QA port binding")

    def connect(self):
        return pymysql.connect(host="127.0.0.1", port=13306, user="root", password="local-qa-only",
                               database=self.database, autocommit=True, charset="utf8mb4")

    def client(self, sql, database=None):
        return subprocess.run(["docker", "exec", "-i", CONTAINER, "mysql", "-uroot", "-plocal-qa-only",
                               "--batch", "--skip-column-names", database or self.database],
                              input=sql, text=True, capture_output=True)

    def setUp(self):
        self.database = "qa_email_" + uuid.uuid4().hex
        result = self.client(f"CREATE DATABASE `{self.database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;", "blog_security_qa")
        self.assertEqual(0, result.returncode, "Cannot create isolated fixture database")
        result = self.client("CREATE TABLE sys_user (id BIGINT PRIMARY KEY, email VARCHAR(50) NULL, is_deleted INT DEFAULT 0, marker VARCHAR(50));")
        self.assertEqual(0, result.returncode)

    def tearDown(self):
        # Only the randomly named database created by this test can be removed.
        if self.database.startswith("qa_email_") and len(self.database) == 41:
            result = self.client(f"DROP DATABASE `{self.database}`;", "blog_security_qa")
            self.assertEqual(0, result.returncode)

    def migrate(self):
        return self.client((ROOT / "sql/security-email-uniqueness.sql").read_text(encoding="utf-8"))

    def rows(self):
        with self.connect() as connection, connection.cursor() as cursor:
            cursor.execute("SELECT * FROM sys_user ORDER BY id")
            return cursor.fetchall()

    def test_repeat_preserves_values_and_allows_multiple_unbound_accounts(self):
        self.client("INSERT INTO sys_user VALUES (1,'Owner@example.invalid',0,'keep'),(2,NULL,0,'unbound'),(3,NULL,1,'deleted');")
        before = self.rows()
        self.assertEqual(0, self.migrate().returncode)
        self.assertEqual(0, self.migrate().returncode)
        self.assertEqual(before, self.rows())
        self.assertNotEqual(0, self.client("INSERT INTO sys_user VALUES(4,'owner@example.invalid',0,'conflict');").returncode)

    def test_duplicate_preflight_does_not_print_values_or_modify_accounts(self):
        self.client("INSERT INTO sys_user VALUES (1,'duplicate@example.invalid',0,'keep1'),(2,'duplicate@example.invalid',1,'keep2');")
        before = self.rows()
        report = self.client((ROOT / "scripts/security/email-uniqueness-preflight.sql").read_text(encoding="utf-8"))
        self.assertEqual(0, report.returncode)
        self.assertIn("1\t2", report.stdout)
        self.assertNotIn("duplicate@", report.stdout + report.stderr)
        self.assertNotEqual(0, self.migrate().returncode)
        self.assertEqual(before, self.rows())

    def test_blank_address_stops_without_rewriting_data(self):
        self.client("INSERT INTO sys_user VALUES (1,'   ',0,'unchanged');")
        before = self.rows()
        self.assertNotEqual(0, self.migrate().returncode)
        self.assertEqual(before, self.rows())

    def test_soft_deleted_account_keeps_email_reserved(self):
        self.client("INSERT INTO sys_user VALUES (1,'reserved@example.invalid',1,'keep');")
        self.assertEqual(0, self.migrate().returncode)
        self.assertNotEqual(0, self.client("INSERT INTO sys_user VALUES (2,'reserved@example.invalid',0,'reject');").returncode)
        self.assertEqual(1, len(self.rows()))

    def test_wrong_existing_index_is_not_silently_replaced(self):
        self.client("CREATE INDEX uk_sys_user_email ON sys_user(email);")
        self.assertNotEqual(0, self.migrate().returncode)
        result = self.client("SELECT NON_UNIQUE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user' AND INDEX_NAME='uk_sys_user_email';")
        self.assertEqual("1", result.stdout.strip())

    def test_concurrent_claims_have_only_one_winner(self):
        self.assertEqual(0, self.migrate().returncode)
        def claim(account_id):
            return self.client(f"INSERT INTO sys_user VALUES ({account_id},'race@example.invalid',0,'test');").returncode == 0
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            self.assertEqual(1, sum(executor.map(claim, [1, 2])))
        self.assertEqual(1, len(self.rows()))


if __name__ == "__main__":
    unittest.main()
