#!/usr/bin/env python3
"""Run actual Glyph binder/preference source with bounded host Android fixtures."""
from pathlib import Path
import os
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
PACKAGE = HERE.parents[1]
ANDROID = HERE.parents[5]
COMPILER = ANDROID / "external/kotlinc/bin/kotlinc"
COROUTINES = ANDROID / "external/kotlinc/lib/kotlinx-coroutines-core-jvm.jar"


def checked(command):
    result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode:
        print(result.stdout)
        raise SystemExit(result.returncode)
    return result.stdout


cases = {
    "caller": ["src/co/aospa/glyph/Services/ThirdPartyService.kt"],
    "preferences": ["src/co/aospa/glyph/data/source/GlyphPreferences.kt",
                    "src/co/aospa/glyph/data/model/ScheduleMode.kt"],
}
with tempfile.TemporaryDirectory(prefix="glyph-kotlin-host-") as directory:
    for case, sources in cases.items():
        jar = Path(directory) / f"{case}.jar"
        command = [str(COMPILER), "-classpath", str(COROUTINES)]
        command.extend(str(source) for source in sorted((HERE / case).glob("*.kt")))
        command.extend(str(PACKAGE / source) for source in sources)
        command.extend(["-include-runtime", "-d", str(jar)])
        checked(command)
        print(checked(["java", "-cp", os.pathsep.join([str(jar), str(COROUTINES)]), "MainKt"]))
