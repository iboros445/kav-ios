#!/usr/bin/env python3
"""Write the SideStore/AltStore source (source.json) for one Kav.ipa.

The bundle ID, version, build number, minimum iOS and privacy strings are read from the IPA itself, so the source
always matches what it points at. SideStore checks the declared permissions against the app, hence `appPermissions`.

    make_source_json.py --ipa app/Kav.ipa --url https://.../Kav-2.2-9.ipa --notes "commit subject" > source.json
"""
import argparse
import datetime
import json
import os
import plistlib
import re
import zipfile

REPO = "iboros445/kav-ios"
BRANCH = "claude/ios-migration-plan-4ojstt"
ICON = f"https://raw.githubusercontent.com/{REPO}/{BRANCH}/.github/icon.png"


def info_plist(ipa):
    with zipfile.ZipFile(ipa) as z:
        name = next(n for n in z.namelist() if re.fullmatch(r"Payload/[^/]+\.app/Info\.plist", n))
        return plistlib.loads(z.read(name))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ipa", required=True)
    ap.add_argument("--url", required=True, help="public download URL of the IPA")
    ap.add_argument("--notes", default="")
    a = ap.parse_args()

    p = info_plist(a.ipa)
    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat()
    version = {
        "version": p["CFBundleShortVersionString"],
        "buildVersion": str(p["CFBundleVersion"]),
        "date": now,
        "localizedDescription": a.notes or "Latest iOS build.",
        "downloadURL": a.url,
        "size": os.path.getsize(a.ipa),
        "minOSVersion": p.get("MinimumOSVersion", "16.0"),
    }
    privacy = {k: v for k, v in p.items() if k.startswith("NS") and k.endswith("UsageDescription")}
    app = {
        "name": p.get("CFBundleDisplayName") or p["CFBundleName"],
        "bundleIdentifier": p["CFBundleIdentifier"],
        "developerName": "iboros445",
        "subtitle": "Israeli public transport, on iPhone.",
        "localizedDescription": "The iOS build of Kav, made from the Android app's own code. Development builds: "
                                "every successful build of the iOS branch lands here.",
        "iconURL": ICON,
        "tintColor": "#3478F6",
        "category": "utilities",
        "screenshots": [],
        "versions": [version],
        "appPermissions": {"entitlements": [], "privacy": privacy},
        # pre-2.0 source fields, for older SideStore/AltStore builds
        "version": version["version"],
        "versionDate": now,
        "versionDescription": version["localizedDescription"],
        "downloadURL": version["downloadURL"],
        "size": version["size"],
    }
    source = {
        "name": "Kav for iPhone",
        "identifier": "io.github.iboros445.kav.source",
        "subtitle": "Development builds of Kav for iOS",
        "iconURL": ICON,
        "website": f"https://github.com/{REPO}",
        "tintColor": "#3478F6",
        "apps": [app],
        "news": [],
    }
    print(json.dumps(source, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
