#!/usr/bin/env python3
"""Run the actual native player/controller tests with host transport stubs."""
from pathlib import Path
import subprocess
import tempfile

SOURCE = Path(__file__).resolve().parents[1]
STUBS = {
    "log": '''#[macro_export] macro_rules! error { ($($t:tt)*) => { eprintln!($($t)*); } }
#[macro_export] macro_rules! warn { ($($t:tt)*) => { eprintln!($($t)*); } }''',
    "rustutils": '''pub mod android { pub mod system_properties {
pub fn read(_: &str) -> Result<Option<String>, std::io::Error> { Ok(None) }
pub fn write(_: &str, _: &str) -> Result<(), std::io::Error> { Ok(()) }
} }''',
    "binder": (SOURCE / "tests/binder_stub.rs").read_text(),
    "android_hardware_light": (SOURCE / "tests/transport_stub.rs").read_text(),
}


def checked(args):
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode:
        print(result.stdout)
        raise SystemExit(result.returncode)
    return result.stdout


with tempfile.TemporaryDirectory(prefix="glyph-native-host-") as directory:
    out = Path(directory)
    for name, source in STUBS.items():
        stub = out / f"{name}.rs"
        stub.write_text(source)
        command = ["rustc", "--edition=2021", "--crate-name", name, "--crate-type=rlib",
                   str(stub), "-o", str(out / f"lib{name}.rlib")]
        if name == "android_hardware_light":
            command.extend(["--extern", f"binder={out / 'libbinder.rlib'}"])
        checked(command)
    command = ["rustc", "--edition=2021", "--cfg", "phone3a", "--test",
               str(SOURCE / "host_tests.rs"), "-o", str(out / "glyph-native-tests")]
    for name in STUBS:
        command.extend(["--extern", f"{name}={out / f'lib{name}.rlib'}"])
    command.extend(["-L", str(out)])
    checked(command)
    print(checked([str(out / "glyph-native-tests"), "--test-threads=1", "--nocapture"]))
