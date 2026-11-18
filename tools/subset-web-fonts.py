#!/usr/bin/env python3
"""Build the lobby's small WOFF2 fonts from the bundled TTFs in assets/fonts.

Requires fontTools with Brotli: python3 -m pip install fonttools brotli
"""
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets" / "fonts"
OUTPUT = ROOT / "web" / "src" / "main" / "webapp" / "fonts"

# Latin text, punctuation, arrows and shapes cover every lobby string and room code.
LATIN = (
    "U+0020-007E,U+00A0-017F,U+0192,U+0218-021B,U+02C6-02DD,U+2000-206F,"
    "U+20A0-20CF,U+2100-215F,U+2190-21FF,U+2200-22FF,U+25A0-25FF,U+FB01-FB02"
)
CHESS = "U+2654-265F"

# (source, output, unicode ranges, new family name or None to keep the original).
# Liberation's Reserved Font Name cannot name a modified (subset) version under the OFL.
FONTS = [
    ("LiberationMono-Bold.ttf", "LobbyPixel-Bold.woff2", LATIN, "Lobby Pixel"),
    ("NotoSansSymbols2-Regular.ttf", "NotoSansSymbols2-Chess.woff2", CHESS, None),
]
LICENSES = ["OFL.txt", "Liberation-LICENSE.txt"]


def rename(font, family, style):
    full = f"{family} {style}"
    postscript = f"{family.replace(' ', '')}-{style}"
    names = font["name"]
    for record in list(names.names):
        if record.nameID in (16, 17, 18, 21, 22):
            names.removeNames(nameID=record.nameID)
    for name_id, value in ((1, family), (2, style), (3, f"{postscript};subset"), (4, full), (6, postscript)):
        names.setName(value, name_id, 3, 1, 0x409)
        names.setName(value, name_id, 1, 0, 0)


def build(source, output, unicodes, family):
    options = subset.Options()
    options.flavor = "woff2"
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.notdef_outline = True
    font = TTFont(SOURCE / source)
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=subset.parse_unicodes(unicodes))
    subsetter.subset(font)
    if family:
        rename(font, family, font["name"].getDebugName(2) or "Regular")
    font.flavor = "woff2"
    font.save(OUTPUT / output)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for source, output, unicodes, family in FONTS:
        build(source, output, unicodes, family)
    for license_file in LICENSES:
        (OUTPUT / license_file).write_bytes((SOURCE / license_file).read_bytes())


if __name__ == "__main__":
    main()
