#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Paranoid Android
# SPDX-License-Identifier: Apache-2.0

import argparse
import hashlib
import os
from pathlib import Path
import struct
import zipfile
import zlib


# shortcut: pinned C5.0 bytecode, refresh this patch when the camera APK changes.
DEX_SHA256 = "918b89f7e941cd9d467c130b2ff1db15a4953e0ef66ba6d6bd2dbb5d1713bfbb"
PATCHED_DEX_SHA256 = "2b3941a6f789574047f1c2e1ac08040e8d742fef4149e0930c442a8e72906547"
PATCH_OFFSET = 2182962
ORIGINAL = bytes.fromhex("7120592925000c05")
# ProductConfig.<clinit>: use the existing "Asteroids" string instead of ro.product.device.
REPLACEMENT = bytes.fromhex("1a058a1300000000")


def patch_dex(data):
    digest = hashlib.sha256(data).hexdigest()
    if digest == PATCHED_DEX_SHA256:
        return data
    if digest != DEX_SHA256 or data[PATCH_OFFSET:PATCH_OFFSET + 8] != ORIGINAL:
        raise ValueError("NTCamera bytecode changed; refresh the Asteroids device patch")

    patched = bytearray(data)
    patched[PATCH_OFFSET:PATCH_OFFSET + 8] = REPLACEMENT
    patched[12:32] = hashlib.sha1(patched[32:]).digest()
    struct.pack_into("<I", patched, 8, zlib.adler32(patched[12:]) & 0xffffffff)
    if hashlib.sha256(patched).hexdigest() != PATCHED_DEX_SHA256:
        raise ValueError("NTCamera device patch produced unexpected bytecode")
    return bytes(patched)


def resolve_source(source):
    src = Path(source)
    if src.exists():
        return src
    if src.is_symlink():
        target = os.readlink(source)
        for anchor in [Path.cwd(), Path(__file__).resolve()]:
            anchor_str = str(anchor)
            if "/out/" in anchor_str:
                root = Path(anchor_str.split("/out/")[0])
                candidate = (root / src.parent / target).resolve()
                if candidate.exists():
                    return candidate
    return src


def patch_apk(source, output):
    source = resolve_source(source)
    if Path(source).resolve() == Path(output).resolve():
        raise ValueError("The extracted camera APK must remain unchanged")
    with zipfile.ZipFile(source) as original:
        dex = patch_dex(original.read("classes5.dex"))
        with zipfile.ZipFile(output, "w") as patched:
            patched.comment = original.comment
            for member in original.infolist():
                name = member.filename
                # android_app_import signs the generated APK with the platform certificate.
                if name.startswith("META-INF/") and (
                    name == "META-INF/MANIFEST.MF"
                    or name.endswith((".SF", ".RSA", ".DSA", ".EC"))
                ):
                    continue
                patched.writestr(member, dex if name == "classes5.dex" else original.read(name))


def main():
    parser = argparse.ArgumentParser(description="Keep NTCamera on the Asteroids product profile")
    parser.add_argument("source")
    parser.add_argument("output")
    args = parser.parse_args()
    patch_apk(args.source, args.output)


if __name__ == "__main__":
    main()
