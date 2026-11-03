"""Execute linked Kotlin tests on every available retained Apple simulator device."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys


def run(*arguments, timeout=300):
    return subprocess.run(["xcrun", "simctl", *arguments], text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build-dir", required=True, type=Path)
    parser.add_argument("--report-dir", required=True, type=Path)
    args = parser.parse_args()
    args.report_dir.mkdir(parents=True, exist_ok=True)
    listing = run("list", "devices", "available", "-j")
    listing.check_returncode()
    devices = json.loads(listing.stdout)["devices"]
    targets = {"iOS": "iosSimulatorArm64", "tvOS": "tvosSimulatorArm64",
               "watchOS": "watchosSimulatorArm64"}
    results = []
    for platform, target in targets.items():
        matching = [(runtime, device) for runtime, group in devices.items()
                    if runtime.startswith("com.apple.CoreSimulator.SimRuntime." + platform + "-")
                    for device in group if device.get("isAvailable")]
        if not matching:
            raise RuntimeError("No available simulator devices for " + platform)
        binary = (args.build_dir / "bin" / target / "debugTest/test.kexe").resolve(strict=True)
        digest = hashlib.sha256(binary.read_bytes()).hexdigest()
        for runtime, device in matching:
            receipt = {"runtime": runtime, "device": device["name"], "udid": device["udid"],
                       "target": target, "binary": str(binary), "binarySha256": digest,
                       "executionMode": "local-apple-simulator-standalone", "passed": False,
                       "startedAt": datetime.now(timezone.utc).isoformat()}
            output = ""
            print(f"Running {target} on {device['name']} ({runtime})", flush=True)
            try:
                # Match KotlinNativeSimulatorTest's standalone command-line test route.
                process = run("spawn", "--standalone", device["udid"], str(binary), "--ktest_logger=GTEST")
                output = process.stdout
                receipt["processExit"] = process.returncode
                process.check_returncode()
                ran = re.search(r"\[==========\] (\d+) tests? from .* ran\.", output)
                passed = re.search(r"\[  PASSED  \] (\d+) tests?\.", output)
                if not ran or not passed or int(ran[1]) == 0 or ran[1] != passed[1]:
                    raise RuntimeError("Missing, empty or failing native test results")
                if re.search(r"\[\s*(?:SKIPPED|FAILED|DISABLED)\s*\]|YOU HAVE \d+ DISABLED", output):
                    raise RuntimeError("Required simulator tests were skipped, disabled or failed")
                if hashlib.sha256(binary.read_bytes()).hexdigest() != digest:
                    raise RuntimeError("Native test binary changed during execution")
                receipt.update(tests=int(ran[1]), passed=True)
            except (OSError, RuntimeError, subprocess.SubprocessError) as error:
                receipt["error"] = str(error)
                if isinstance(error, subprocess.CalledProcessError):
                    output += error.stdout or ""
            finally:
                receipt["finishedAt"] = datetime.now(timezone.utc).isoformat()
                (args.report_dir / (device["udid"] + ".log")).write_text(output)
                (args.report_dir / (device["udid"] + ".json")).write_text(json.dumps(receipt, indent=2) + "\n")
                results.append(receipt)
                print(json.dumps(receipt), flush=True)
    (args.report_dir / "summary.json").write_text(json.dumps(results, indent=2) + "\n")
    return 0 if results and all(row["passed"] for row in results) else 1


if __name__ == "__main__":
    sys.exit(main())
