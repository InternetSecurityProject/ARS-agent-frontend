import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("firebase_config.py")
APP = "1:123456:android:abcdef"


class FirebaseConfigTest(unittest.TestCase):
    def client_config(self):
        return {
            "project_info": {"project_number": "123456", "project_id": "team-project"},
            "client": [{"client_info": {"mobilesdk_app_id": APP,
                        "android_client_info": {"package_name": "kr.ac.suwon.arsagent.demo"}},
                        "api_key": [{"current_key": "PUBLIC-CLIENT-KEY"}]}],
        }

    def invoke(self, config, app=APP, credentials=None):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder) / "firebase-team.json"
            credential_file = Path(folder) / "service.json"
            env = {**os.environ, "GOOGLE_SERVICES_JSON": config if isinstance(config, str) else json.dumps(config),
                   "FIREBASE_APP_ID": app}
            args = [sys.executable, str(SCRIPT), "--output", str(output)]
            if credentials is not None:
                env["FIREBASE_SERVICE_ACCOUNT_JSON"] = json.dumps(credentials)
                args += ["--credentials-out", str(credential_file)]
            result = subprocess.run(args, env=env, capture_output=True, text=True)
            return result, output.read_text() if output.exists() else None, credential_file.exists(), (
                output.stat().st_mode & 0o777 if output.exists() else None)

    def test_valid_client_outputs_only_selected_public_fields(self):
        config = self.client_config()
        config["unused"] = "DO-NOT-PACKAGE"
        result, text, _, permissions = self.invoke(config)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual({"application_id": APP, "api_key": "PUBLIC-CLIENT-KEY", "project_id": "team-project",
                          "sender_id": "123456", "package_name": "kr.ac.suwon.arsagent.demo"}, json.loads(text))
        self.assertNotIn("DO-NOT-PACKAGE", text)
        self.assertEqual(0o600, permissions)

    def test_missing_malformed_and_admin_json_fail_without_echo(self):
        for config in ["", "BAD-SECRET-INPUT", {}, {"type": "service_account", "private_key": "PRIVATE-MARKER"}]:
            result, output, _, _ = self.invoke(config)
            self.assertNotEqual(0, result.returncode)
            self.assertIsNone(output)
            self.assertNotIn("BAD-SECRET-INPUT", result.stderr)
            self.assertNotIn("PRIVATE-MARKER", result.stderr)

    def test_wrong_package_rejected(self):
        config = self.client_config()
        config["client"][0]["client_info"]["android_client_info"]["package_name"] = "kr.ac.suwon.arsagent"
        result, output, _, _ = self.invoke(config)
        self.assertNotEqual(0, result.returncode); self.assertIsNone(output)

    def test_wrong_app_id_or_project_number_rejected(self):
        for app in ["team-project", "1:999:android:abcdef", "1:123456:android:fedcba", ""]:
            result, output, _, _ = self.invoke(self.client_config(), app)
            self.assertNotEqual(0, result.returncode); self.assertIsNone(output)
        config = self.client_config()
        config["project_info"]["project_number"] = "999"
        result, output, _, _ = self.invoke(config)
        self.assertNotEqual(0, result.returncode); self.assertIsNone(output)

    def test_duplicate_matching_clients_are_rejected(self):
        config = self.client_config()
        config["client"] *= 2
        result, output, _, _ = self.invoke(config)
        self.assertNotEqual(0, result.returncode); self.assertIsNone(output)

    def test_missing_key_rejected(self):
        config = self.client_config()
        config["client"][0]["api_key"] = []
        result, output, _, _ = self.invoke(config)
        self.assertNotEqual(0, result.returncode); self.assertIsNone(output)

    def test_wrong_json_container_types_fail_without_traceback(self):
        for config in [[], "null", {"project_info": []}, {"project_info": {"project_number": "123456", "project_id": "p"}, "client": ["BAD"]}]:
            result, output, _, _ = self.invoke(config)
            self.assertNotEqual(0, result.returncode); self.assertIsNone(output)
            self.assertIn("Firebase configuration validation failed", result.stderr)
            self.assertNotIn("Traceback", result.stderr)

    def test_mismatched_service_account_never_written_or_echoed(self):
        credentials = {"type": "service_account", "project_id": "other", "private_key": "PRIVATE-MARKER"}
        result, output, exists, _ = self.invoke(self.client_config(), credentials=credentials)
        self.assertNotEqual(0, result.returncode); self.assertFalse(exists); self.assertIsNone(output)
        self.assertNotIn("PRIVATE-MARKER", result.stdout + result.stderr)

    def test_service_account_written_separately_from_public_config(self):
        credentials = {"type": "service_account", "project_id": "team-project",
                       "private_key": "-----BEGIN PRIVATE KEY-----\nPRIVATE-MARKER\n-----END PRIVATE KEY-----\n",
                       "client_email": "ci@team-project.iam.gserviceaccount.com", "token_uri": "https://oauth2.googleapis.com/token"}
        result, output, exists, _ = self.invoke(self.client_config(), credentials=credentials)
        self.assertEqual(0, result.returncode, result.stderr); self.assertTrue(exists)
        self.assertNotIn("PRIVATE-MARKER", output + result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
