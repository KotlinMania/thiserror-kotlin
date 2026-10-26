#!/usr/bin/env python3
"""Run one linked native test binary locally or through existing Parallels Tools."""

import argparse
import base64
import gzip
import hashlib
import json
from pathlib import Path
import platform
import re
import shlex
import shutil
import subprocess
import sys


def guest_command(target, digest):
    if target == "mingwX64":
        script = r"""
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try {
  $bytes = [Convert]::FromBase64String([Console]::ReadLine())
  $folder = Join-Path $env:LOCALAPPDATA 'KotlinMania\native-validation\@HASH@'
  [IO.Directory]::CreateDirectory($folder) | Out-Null
  $binary = Join-Path $folder 'test.exe'
  $inputStream = [IO.MemoryStream]::new($bytes)
  $gzip = [IO.Compression.GZipStream]::new($inputStream, [IO.Compression.CompressionMode]::Decompress)
  $outputStream = [IO.File]::Create($binary)
  $gzip.CopyTo($outputStream)
  $outputStream.Dispose(); $gzip.Dispose(); $inputStream.Dispose()
  $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $binary).Hash.ToLowerInvariant()
  if ($hash -ne '@HASH@') { throw 'Transferred binary hash mismatch' }
  Write-Output "BINARY_SHA256=$hash"
  Write-Output "GUEST_OS=Windows"
  Write-Output "GUEST_ARCH=$([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture)"
  & $binary --ktest_logger=GTEST
  $testExit = $LASTEXITCODE
  Write-Output "GUEST_TEST_EXIT=$testExit"
  exit $testExit
} catch { [Console]::Error.WriteLine($_); exit 1 }
""".replace("@HASH@", digest)
        return [
            "powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand",
            base64.b64encode(script.encode("utf-16le")).decode("ascii"),
        ]
    script = """
import base64, gzip, hashlib, os, pathlib, platform, subprocess, sys
raw = gzip.decompress(base64.b64decode(sys.stdin.buffer.readline()))
assert hashlib.sha256(raw).hexdigest() == '@HASH@', 'Transferred binary hash mismatch'
assert platform.system() == 'Linux', 'A Linux guest is required'
folder = pathlib.Path.home() / '.cache/kotlinmania/native-validation/@HASH@'
folder.mkdir(parents=True, exist_ok=True)
binary = folder / 'test.kexe'
binary.write_bytes(raw)
binary.chmod(0o700)
print('BINARY_SHA256=@HASH@', flush=True)
print('GUEST_OS=' + platform.system(), flush=True)
print('GUEST_ARCH=' + platform.machine(), flush=True)
result = subprocess.run([str(binary), '--ktest_logger=GTEST'])
print('GUEST_TEST_EXIT=' + str(result.returncode), flush=True)
sys.exit(result.returncode)
""".replace("@HASH@", digest)
    launcher = "import base64;exec(base64.b64decode(%r))" % base64.b64encode(script.encode()).decode()
    return ["python3 -c " + shlex.quote(launcher)]


def select_guest(prlctl, target, requested):
    result = subprocess.run(
        [prlctl, "list", "-a", "-i", "-j"], text=True, capture_output=True, timeout=30, check=True,
    )
    guests = json.loads(result.stdout)
    linux = {"ubuntu", "debian", "fedora", "linux", "centos", "rhel", "redhat", "opensuse", "kali"}
    candidates = [guest for guest in guests if guest.get("State") == "running"
                  and guest.get("GuestTools", {}).get("state") == "installed"
                  and ((guest.get("OS", "").startswith("win")) if target == "mingwX64"
                       else guest.get("OS") in linux)
                  and (not requested or guest.get("ID") == requested or guest.get("Name") == requested)]
    if len(candidates) != 1:
        raise RuntimeError(
            f"{target} requires exactly one running guest with existing Parallels Tools; "
            f"found {len(candidates)}. Select an existing guest with --vm when ambiguous."
        )
    return candidates[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", required=True, choices=["linuxArm64", "linuxX64", "mingwX64"])
    parser.add_argument("--binary", required=True, type=Path)
    parser.add_argument("--report-dir", required=True, type=Path)
    parser.add_argument("--vm")
    args = parser.parse_args()
    args.report_dir.mkdir(parents=True, exist_ok=True)
    binary_arch = "aarch64" if args.target == "linuxArm64" else "x86_64"
    receipt = {"target": args.target, "hostOs": platform.system(), "hostArch": platform.machine(),
               "binaryArch": binary_arch}
    output = ""
    try:
        binary = args.binary.resolve(strict=True)
        raw = binary.read_bytes()
        digest = hashlib.sha256(raw).hexdigest()
        receipt.update(binary=str(binary), binarySha256=digest)
        native_os = "Windows" if args.target == "mingwX64" else "Linux"
        if platform.system() == native_os:
            receipt["executionMode"] = "local-host"
            result = subprocess.run([str(binary), "--ktest_logger=GTEST"], text=True,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=300)
            output = result.stdout
        else:
            prlctl = shutil.which("prlctl")
            if not prlctl:
                raise RuntimeError(f"No native {native_os} host or existing Parallels CLI is available")
            guest = select_guest(prlctl, args.target, args.vm)
            receipt.update(executionMode="local-parallels", guestId=guest["ID"], guestName=guest["Name"])
            command = [prlctl, "exec", guest["ID"], "--current-user"] + guest_command(args.target, digest)
            payload = base64.b64encode(gzip.compress(raw)).decode("ascii") + "\n"
            result = subprocess.run(command, input=payload, text=True, stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, timeout=300)
            output = result.stdout
            receipt["processExit"] = result.returncode
            architecture = re.search(r"GUEST_ARCH=(\S+)", output)
            if architecture:
                receipt["guestArch"] = architecture[1]
            if f"BINARY_SHA256={digest}" not in output or "GUEST_TEST_EXIT=0" not in output:
                raise RuntimeError(f"Guest did not confirm the binary hash and successful test exit (host exit {result.returncode})")
        receipt["processExit"] = result.returncode
        runtime_arch = receipt.get("guestArch", receipt["hostArch"]).lower()
        runtime_arch = {"arm64": "aarch64", "amd64": "x86_64"}.get(runtime_arch, runtime_arch)
        receipt["emulated"] = runtime_arch != binary_arch
        if result.returncode != 0:
            raise RuntimeError(f"Native test process failed with exit {result.returncode}")
        ran = re.search(r"\[==========\] (\d+) tests? from .* ran\.", output)
        passed = re.search(r"\[  PASSED  \] (\d+) tests?\.", output)
        if not ran or not passed or int(ran[1]) == 0 or ran[1] != passed[1]:
            raise RuntimeError("Missing, empty or failing native test result")
        if re.search(r"\[\s*(?:SKIPPED|FAILED|DISABLED)\s*\]|YOU HAVE \d+ DISABLED", output):
            raise RuntimeError("Required native tests were skipped, disabled or failed")
        if hashlib.sha256(binary.read_bytes()).hexdigest() != digest:
            raise RuntimeError("Linked binary changed during test execution; rerun validation")
        receipt.update(tests=int(ran[1]), passed=True)
        print(f"{args.target}: {ran[1]} tests passed ({receipt['executionMode']}); sha256={digest}")
        return 0
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        receipt.update(passed=False, error=str(error))
        print(f"{args.target}: {error}", file=sys.stderr)
        if output:
            print(output[-3000:], file=sys.stderr)
        return 1
    finally:
        (args.report_dir / f"{args.target}.log").write_text(output)
        (args.report_dir / f"{args.target}.json").write_text(json.dumps(receipt, indent=2) + "\n")


if __name__ == "__main__":
    sys.exit(main())
