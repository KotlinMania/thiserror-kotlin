#!/usr/bin/env python3
"""Run one linked native test binary locally or through existing Parallels Tools."""

import argparse
import base64
from datetime import datetime, timezone
import gzip
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shlex
import shutil
import subprocess
import sys


def android_command(args, binary, digest, receipt):
    adb = args.adb or shutil.which("adb")
    if not adb:
        raise RuntimeError("Android Native validation requires the existing Android SDK adb")
    listing = subprocess.run([adb, "devices"], capture_output=True, text=True, check=True, timeout=30)
    devices = [line.split()[0] for line in listing.stdout.splitlines()[1:]
               if len(line.split()) == 2 and line.split()[1] == "device"]
    required_abi = "arm64-v8a" if args.target == "androidNativeArm64" else "x86_64"
    candidates = []
    for serial in devices:
        if args.serial and serial != args.serial:
            continue
        abi = subprocess.run([adb, "-s", serial, "shell", "getprop", "ro.product.cpu.abi"],
                             capture_output=True, text=True, check=True, timeout=30).stdout.strip()
        if abi == required_abi:
            candidates.append((serial, abi))
    if len(candidates) != 1:
        raise RuntimeError(f"{args.target} requires one connected {required_abi} Android device; found {len(candidates)}")
    serial, abi = candidates[0]
    project_key = hashlib.sha256(str(binary.parents[4]).encode()).hexdigest()[:12]
    folder = f"/data/local/tmp/kotlinmania-{project_key}/{args.target}"
    remote = folder + "/test.kexe"
    base = [adb, "-s", serial]
    subprocess.run(base + ["shell", "mkdir", "-p", folder], check=True, capture_output=True, timeout=30)
    subprocess.run(base + ["push", str(binary), remote], check=True, capture_output=True, timeout=60)
    subprocess.run(base + ["shell", "chmod", "700", remote], check=True, capture_output=True, timeout=30)
    actual = subprocess.run(base + ["shell", "sha256sum", remote], check=True, capture_output=True,
                            text=True, timeout=30).stdout.split()[0]
    if actual != digest:
        raise RuntimeError("Android binary transfer checksum mismatch")
    emulator = subprocess.run(base + ["shell", "getprop", "ro.kernel.qemu"], check=True,
                              capture_output=True, text=True, timeout=30).stdout.strip() == "1"
    receipt.update(executionMode="local-android-emulator" if emulator else "local-android-device", deviceSerial=serial,
                   guestArch="aarch64" if abi == "arm64-v8a" else "x86_64", deviceAbi=abi)
    return subprocess.run(base + ["shell", remote, "--ktest_logger=GTEST"], text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=300)


def android_guest_command(args, digest):
    config = json.dumps({"sdk": args.android_guest_sdk, "avdHome": args.android_guest_avd_home,
                         "avdName": args.android_guest_avd_name, "binary": args.guest_binary})
    script = r'''
import base64, gzip, hashlib, json, os, pathlib, platform, re, shutil, struct, subprocess, sys, time
config = json.loads(@CONFIG@)
assert platform.system() == 'Linux', 'An existing Linux guest is required'
sdk = pathlib.Path(config['sdk'])
avd_home = pathlib.Path(config['avdHome'])
emulator = sdk / 'emulator/emulator'
adb = sdk / 'platform-tools/adb'
assert emulator.is_file() and adb.is_file(), 'Run setupLocalAndroidX64Runtime first'
with emulator.open('rb') as stream: header = stream.read(20)
assert header[:4] == b'\x7fELF' and struct.unpack('<H', header[18:20])[0] == 62, 'Expected Linux x64 emulator'
raw = (pathlib.Path(config['binary']).read_bytes() if config['binary']
       else gzip.decompress(base64.b64decode(sys.stdin.buffer.readline())))
assert hashlib.sha256(raw).hexdigest() == '@HASH@', 'Transferred binary hash mismatch'
folder = pathlib.Path.home() / '.cache/kotlinmania/native-validation/@HASH@'
folder.mkdir(parents=True, exist_ok=True)
binary = folder / 'test.kexe'
binary.write_bytes(raw)
runtime = pathlib.Path.home() / '.cache/kotlinmania/android-x64'
runtime.mkdir(parents=True, exist_ok=True)
env = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk),
           ANDROID_AVD_HOME=str(avd_home), ANDROID_I_WANT_MY_TCG='yes')
if platform.machine().lower() in ('aarch64', 'arm64'):
    translator = shutil.which('qemu-x86_64')
    assert translator, 'Install the official Ubuntu qemu-user package for the x64 Android emulator'
    engine = sdk / 'emulator/qemu/linux-x86_64/qemu-system-x86_64-headless'
    assert engine.is_file(), 'The Android SDK is missing its headless x64 emulator engine'
    libraries = ':'.join(str(sdk / path) for path in ('emulator/lib64', 'emulator/lib64/gles_swiftshader'))
    emulator_command = [translator, '-L', '/', '-E', 'LD_LIBRARY_PATH=' + libraries,
                        '-E', 'ANDROID_EMULATOR_LAUNCHER_DIR=' + str(sdk / 'emulator'), str(engine)]
    emulator_translation = 'qemu-user'
else:
    emulator_command = [str(emulator)]
    emulator_translation = 'native'
def adb_run(*arguments, check=True, timeout=120):
    return subprocess.run([str(adb), *arguments], env=env, text=True,
                          stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          check=check, timeout=timeout)
adb_run('start-server')
serial = 'emulator-5580'
listing = adb_run('devices').stdout
pid_file = runtime / 'emulator.pid'
alive = False
if pid_file.exists():
    try:
        pid = int(pid_file.read_text())
        os.kill(pid, 0)
        command = pathlib.Path(f'/proc/{pid}/cmdline').read_bytes()
        alive = config['avdName'].encode() in command and str(sdk).encode() in command
        if alive and emulator_translation == 'qemu-user':
            assert command.split(b'\0')[0] == str(translator).encode(), 'Existing AVD uses an unverified translation path'
            assert b'shift=0,align=off,sleep=off' in command.split(b'\0'), 'Restart the task AVD with its configured virtual clock'
    except (OSError, ValueError):
        pass
log = runtime / 'emulator.log'
if serial not in listing and not alive:
    clock_options = []
    if emulator_translation == 'qemu-user':
        clock_options = ['-qemu', '-icount', 'shift=0,align=off,sleep=off', '-rtc', 'clock=vm']
    with log.open('w') as output:
        process = subprocess.Popen(emulator_command + [
            '-avd', config['avdName'], '-port', '5580', '-accel', 'off',
            '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot', '-show-kernel',
            '-gpu', 'swiftshader', '-cores', '1', '-memory', '2048',
        ] + clock_options, env=env, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
    pid_file.write_text(str(process.pid))
    pid = process.pid
    print('Started approved Android x64 AVD with software CPU emulation', flush=True)
runtime_started = time.monotonic()
runtime_timeout = 1800
deadline = runtime_started + runtime_timeout
next_progress = runtime_started
while time.monotonic() < deadline:
    log_text = log.read_text(errors='replace')
    activation_failure = re.search(r'Failed to activate packages: Failed to activate \d+ APEX packages[^\n]*',
                                   log_text)
    if activation_failure:
        raise RuntimeError('Android runtime failed: ' + activation_failure[0])
    apex_ready = re.search(r'processing action \(apexd.status=ready(?:\)| &&)', log_text)
    try:
        state = adb_run('-s', serial, 'shell', 'getprop', 'ro.product.cpu.abi', check=False)
    except subprocess.TimeoutExpired:
        state = subprocess.CompletedProcess([], 124, 'ADB shell is not responsive yet')
    if state.returncode == 0 and state.stdout.strip() == 'x86_64' and apex_ready:
        break
    if 'process' in locals() and process.poll() is not None:
        raise RuntimeError('Android emulator exited: ' + log.read_text()[-5000:])
    if alive:
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            raise RuntimeError('Existing Android emulator exited: ' + log.read_text()[-5000:])
    if time.monotonic() >= next_progress:
        print('ANDROID_NATIVE_RUNTIME_PENDING seconds=' + str(int(time.monotonic() - runtime_started)) +
              ' apexReady=' + str(bool(apex_ready)) + ' adb=' + state.stdout.strip()[:200], flush=True)
        next_progress = time.monotonic() + 60
    time.sleep(5)
else:
    raise RuntimeError('Android Native runtime was not ready in ' + str(runtime_timeout) + ' seconds: ' + log.read_text()[-5000:])
avd_name = adb_run('-s', serial, 'emu', 'avd', 'name').stdout.splitlines()[0]
assert avd_name == config['avdName'], 'The selected Android device belongs to another AVD'
abi = state.stdout.strip()
assert abi == 'x86_64', 'Expected the approved x64 Android image'
remote = '/data/local/tmp/kotlinmania-@HASH@/test.kexe'
adb_run('-s', serial, 'shell', 'mkdir', '-p', remote.rsplit('/', 1)[0])
adb_run('-s', serial, 'push', str(binary), remote, timeout=300)
readback = folder / 'android-readback.kexe'
adb_run('-s', serial, 'pull', remote, str(readback), timeout=300)
assert hashlib.sha256(readback.read_bytes()).hexdigest() == '@HASH@', 'Android device binary readback hash mismatch'
adb_run('-s', serial, 'shell', 'chmod', '700', remote)
print('BINARY_SHA256=@HASH@', flush=True)
print('GUEST_OS=Linux', flush=True)
print('GUEST_ARCH=' + platform.machine(), flush=True)
print('DEVICE_ABI=' + abi, flush=True)
print('DEVICE_SERIAL=' + serial, flush=True)
print('EMULATOR_ARCH=x86_64', flush=True)
print('EMULATOR_TRANSLATION=' + emulator_translation, flush=True)
print('EMULATOR_CLOCK=' + ('instruction-count-shift-0' if emulator_translation == 'qemu-user' else 'realtime'), flush=True)
print('ANDROID_ACCEL=software-tcg', flush=True)
print('ANDROID_NATIVE_READINESS=apex-ready-and-adb-x86_64', flush=True)
print('ANDROID_BINARY_VERIFICATION=round-trip-sha256', flush=True)
result = adb_run('-s', serial, 'shell', remote, '--ktest_logger=GTEST', check=False, timeout=1800)
print(result.stdout, flush=True)
print('GUEST_TEST_EXIT=' + str(result.returncode), flush=True)
sys.exit(result.returncode)
'''.replace('@HASH@', digest).replace('@CONFIG@', repr(config))
    launcher = "import base64,sys;exec(base64.b64decode(sys.stdin.buffer.readline()))"
    return ["python3 -c " + shlex.quote(launcher)], base64.b64encode(script.encode()).decode() + "\n"


def guest_command(target, digest, guest_binary):
    if target == "mingwX64":
        script = r"""
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try {
  $folder = Join-Path $env:LOCALAPPDATA 'KotlinMania\native-validation\@HASH@'
  [IO.Directory]::CreateDirectory($folder) | Out-Null
  $binary = Join-Path $folder 'test.exe'
  $source = @SOURCE@
  if ($null -ne $source) {
    Copy-Item -LiteralPath $source -Destination $binary
  } else {
    $bytes = [Convert]::FromBase64String([Console]::ReadLine())
    $inputStream = [IO.MemoryStream]::new($bytes)
    $gzip = [IO.Compression.GZipStream]::new($inputStream, [IO.Compression.CompressionMode]::Decompress)
    $outputStream = [IO.File]::Create($binary)
    $gzip.CopyTo($outputStream)
    $outputStream.Dispose(); $gzip.Dispose(); $inputStream.Dispose()
  }
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
""".replace("@HASH@", digest).replace("@SOURCE@", "'" + guest_binary.replace("'", "''") + "'" if guest_binary else "$null")
        launcher = "$code=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String([Console]::ReadLine())); & ([ScriptBlock]::Create($code))"
        return [
            "powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand",
            base64.b64encode(launcher.encode("utf-16le")).decode("ascii"),
        ], base64.b64encode(script.encode()).decode() + "\n"
    script = """
import base64, gzip, hashlib, os, pathlib, platform, subprocess, sys
source = @SOURCE@
raw = (pathlib.Path(source).read_bytes() if source
       else gzip.decompress(base64.b64decode(sys.stdin.buffer.readline())))
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
""".replace("@HASH@", digest).replace("@SOURCE@", repr(guest_binary))
    launcher = "import base64,sys;exec(base64.b64decode(sys.stdin.buffer.readline()))"
    return ["python3 -c " + shlex.quote(launcher)], base64.b64encode(script.encode()).decode() + "\n"


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
    parser.add_argument("--target", required=True, choices=[
        "linuxArm64", "linuxX64", "mingwX64", "androidNativeArm64", "androidNativeX64",
    ])
    parser.add_argument("--binary", required=True, type=Path)
    parser.add_argument("--report-dir", required=True, type=Path)
    parser.add_argument("--vm")
    parser.add_argument("--guest-binary")
    parser.add_argument("--adb")
    parser.add_argument("--serial")
    parser.add_argument("--android-guest-sdk")
    parser.add_argument("--android-guest-avd-home")
    parser.add_argument("--android-guest-avd-name")
    args = parser.parse_args()
    args.report_dir.mkdir(parents=True, exist_ok=True)
    binary_arch = "aarch64" if args.target.endswith("Arm64") else "x86_64"
    receipt = {"target": args.target, "hostOs": platform.system(), "hostArch": platform.machine(),
               "binaryArch": binary_arch, "startedAt": datetime.now(timezone.utc).isoformat()}
    output = ""
    try:
        binary = args.binary.resolve(strict=True)
        raw = binary.read_bytes()
        digest = hashlib.sha256(raw).hexdigest()
        receipt.update(binary=str(binary), binarySha256=digest)
        native_os = "Windows" if args.target == "mingwX64" else "Linux"
        android_guest = bool(args.android_guest_sdk)
        if android_guest and (args.target != "androidNativeX64" or
                              os.environ.get("CI", "").lower() not in ("", "false", "0")):
            raise RuntimeError("Android guest provisioning is restricted to local Android Native x64 validation")
        if args.target.startswith("androidNative") and not android_guest:
            result = android_command(args, binary, digest, receipt)
            output = result.stdout
        elif platform.system() == native_os and not android_guest:
            receipt["executionMode"] = "local-host"
            result = subprocess.run([str(binary), "--ktest_logger=GTEST"], text=True,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=300)
            output = result.stdout
        else:
            if os.environ.get("CI", "").lower() not in ("", "false", "0"):
                raise RuntimeError(f"CI requires a {native_os} runner; Parallels execution is local-only")
            prlctl = shutil.which("prlctl")
            if not prlctl:
                raise RuntimeError(f"No native {native_os} host or existing Parallels CLI is available")
            guest = select_guest(prlctl, args.target, args.vm)
            receipt.update(executionMode="local-parallels", guestId=guest["ID"], guestName=guest["Name"])
            receipt["binaryTransport"] = "existing-shared-file" if args.guest_binary else "stdin-base64-gzip"
            script_input = ""
            if android_guest:
                guest_arguments, script_input = android_guest_command(args, digest)
            else:
                guest_arguments, script_input = guest_command(args.target, digest, args.guest_binary)
            command = [prlctl, "exec", guest["ID"], "--current-user"] + guest_arguments
            payload = script_input
            if not args.guest_binary:
                payload += base64.b64encode(gzip.compress(raw)).decode("ascii") + "\n"
            live_log = args.report_dir / f"{args.target}.log"
            try:
                with live_log.open('w') as stream:
                    result = subprocess.run(command, input=payload, text=True, stdout=stream,
                                            stderr=subprocess.STDOUT, timeout=5400 if android_guest else 300)
            finally:
                output = live_log.read_text()
            receipt["processExit"] = result.returncode
            if result.returncode != 0:
                raise RuntimeError(f"Guest command failed with exit {result.returncode}")
            architecture = re.search(r"(?m)^GUEST_ARCH=(\S+)\r?$", output)
            if not architecture or not re.search(r"(?m)^GUEST_OS=" + native_os + r"\r?$", output):
                raise RuntimeError("Guest did not confirm its operating system and architecture")
            receipt["guestArch"] = architecture[1]
            if not re.search(r"(?m)^BINARY_SHA256=" + digest + r"\r?$", output) or not re.search(r"(?m)^GUEST_TEST_EXIT=0\r?$", output):
                raise RuntimeError(f"Guest did not confirm the binary hash and successful test exit (host exit {result.returncode})")
            if android_guest:
                if not re.search(r"(?m)^DEVICE_ABI=x86_64\r?$", output):
                    raise RuntimeError("Guest did not confirm the Android x64 device ABI")
                translation = re.search(r"(?m)^EMULATOR_TRANSLATION=(native|qemu-user)\r?$", output)
                if not translation:
                    raise RuntimeError("Guest did not confirm the emulator execution path")
                clock = re.search(r"(?m)^EMULATOR_CLOCK=(instruction-count-shift-0|realtime)\r?$", output)
                if not clock:
                    raise RuntimeError("Guest did not confirm the emulator clock mode")
                if not re.search(r"(?m)^ANDROID_NATIVE_READINESS=apex-ready-and-adb-x86_64\r?$", output):
                    raise RuntimeError("Guest did not confirm Android Native runtime readiness")
                if not re.search(r"(?m)^ANDROID_BINARY_VERIFICATION=round-trip-sha256\r?$", output):
                    raise RuntimeError("Guest did not confirm the Android binary readback checksum")
                receipt.update(executionMode="local-parallels-android-emulator", deviceAbi="x86_64",
                               emulatorProcessArch="x86_64", androidAcceleration="software-tcg",
                               emulatorTranslation=translation[1], emulatorClock=clock[1],
                               androidReadiness="apex-ready-and-adb-x86_64",
                               androidBinaryVerification="round-trip-sha256",
                               deviceSerial="emulator-5580")
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
        receipt["finishedAt"] = datetime.now(timezone.utc).isoformat()
        (args.report_dir / f"{args.target}.log").write_text(output)
        (args.report_dir / f"{args.target}.json").write_text(json.dumps(receipt, indent=2) + "\n")


if __name__ == "__main__":
    sys.exit(main())
