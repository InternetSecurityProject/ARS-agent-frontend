"""Validate CI inputs without logging their values. Output only public Android client fields."""
import argparse
import json
import os
from pathlib import Path
import re
import sys

PACKAGE = "kr.ac.suwon.arsagent.demo"


def public_config(raw, expected_app):
    config = json.loads(raw)
    if config.get("type") == "service_account" or "private_key" in config:
        raise ValueError()
    info = config["project_info"]
    number, project = str(info["project_number"]), info["project_id"]
    if not number.isascii() or not number.isdigit() or not isinstance(project, str) or not project.strip():
        raise ValueError()
    if not re.fullmatch(r"1:" + number + r":android:[a-f0-9]+", expected_app):
        raise ValueError()
    clients = [c for c in config["client"]
               if c.get("client_info", {}).get("android_client_info", {}).get("package_name") == PACKAGE]
    if len(clients) != 1:
        raise ValueError()
    client = clients[0]
    if client["client_info"]["mobilesdk_app_id"] != expected_app:
        raise ValueError()
    key = client["api_key"][0]["current_key"]
    if not isinstance(key, str) or not key.strip():
        raise ValueError()
    return {"application_id": expected_app, "api_key": key, "project_id": project,
            "sender_id": number, "package_name": PACKAGE}


def credential_config(raw, project):
    value = json.loads(raw)
    if (value.get("type") != "service_account" or value.get("project_id") != project
            or not isinstance(value.get("private_key"), str)
            or not value["private_key"].startswith("-----BEGIN PRIVATE KEY-----\n")
            or not isinstance(value.get("client_email"), str)
            or not value["client_email"].endswith(".iam.gserviceaccount.com")
            or value.get("token_uri") != "https://oauth2.googleapis.com/token"):
        raise ValueError()
    return value


def write_private(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation avoids following pre-existing links or clobbering user configuration.
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as handle:
        json.dump(value, handle)
        handle.write("\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--credentials-out")
    args = parser.parse_args()
    written = []
    try:
        public = public_config(os.environ["GOOGLE_SERVICES_JSON"], os.environ["FIREBASE_APP_ID"])
        credential = None
        if args.credentials_out:
            credential = credential_config(os.environ["FIREBASE_SERVICE_ACCOUNT_JSON"], public["project_id"])
        write_private(args.output, public)
        written.append(Path(args.output))
        if credential is not None:
            write_private(args.credentials_out, credential)
            written.append(Path(args.credentials_out))
    except (ValueError, KeyError, TypeError, AttributeError, IndexError, OSError):
        for path in written:
            path.unlink(missing_ok=True)
        print("Firebase configuration validation failed. Check Android .demo app ID, client JSON and service-account project; values are not logged.", file=sys.stderr)
        return 1
    print("Firebase configuration validated.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
