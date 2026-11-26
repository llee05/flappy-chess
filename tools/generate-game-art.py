#!/usr/bin/env python3
"""Original in-game pixel artwork. Rebuild with Python 3 and rsvg-convert.

The SVG is composed on the renderer's 800x400 raster, without antialiasing,
fonts, filters, or external resources. Only the lightweight PNG ships at runtime.
The lobby ornaments are reused from their editable SVG sources.
"""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "web/src/main/webapp/art"
parts = []


def path(d, fill, stroke=None, width=1):
    border = f' stroke="{stroke}" stroke-width="{width}" stroke-linejoin="miter"' if stroke else ""
    parts.append(f'<path d="{d}" fill="{fill}"{border}/>')


def rect(x, y, w, h, fill):
    parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill}"/>')


def group(transform):
    parts.append(f'<g transform="{transform}">')


def end():
    parts.append('</g>')


def cloud(x, y, scale=1, near=False):
    group(f'translate({x} {y}) scale({scale})')
    path('M0 17H6V12H13V6H22V2H35V0H46V5H54V9H65V15H75V20H80V25H73V28H10V25H0Z',
         '#b6ddee' if near else '#a1cfe8')
    path('M0 17H7V12H14V7H23V3H35V1H45V6H52V10H62V16H70V20H76V23H62V22H50V24H38V21H27V23H15V21H0Z',
         '#f3fbff' if near else '#d4edf7')
    path('M16 14H23V8H33V4H38V8H43V14H49V19H37V18H27V20H17Z', '#fff' if near else '#e4f4fb')
    end()


def scroll(x, width, bottom, center=False):
    group(f'translate({x} 0)')
    if center:
        edge = ('M0 31Q24 25 48 30Q94 38 144 29H228Q280 38 324 30Q349 25 372 31'
                'L368 354Q382 366 370 375Q358 383 346 376Q323 399 288 382'
                'Q256 402 221 386Q201 389 186 400Q170 389 151 386'
                'Q115 402 84 382Q49 399 26 376Q12 383 2 375Q-10 365 4 354Z')
    else:
        edge = (f'M0 32Q12 27 28 31Q{width // 2} 40 {width - 28} 31Q{width - 12} 27 {width} 32'
                f'V{bottom - 24}Q{width} {bottom - 14} {width - 15} {bottom - 19}'
                f'Q{width - 16} {bottom - 5} {width // 2 + 18} {bottom + 7}'
                f'L{width // 2} {bottom + 20}L{width // 2 - 18} {bottom + 7}'
                f'Q16 {bottom - 5} 15 {bottom - 19}Q0 {bottom - 14} 0 {bottom - 24}Z')
    path(edge, '#a6403c', '#472a29', 2)
    group('translate(0 -3)')
    path(edge, '#d87557', '#60372f')
    group('translate(0 -3)')
    path(edge, '#ffe9c3', '#b88360')
    end()
    end()
    # Paper edge, folded shoulders and sparse fibers.
    path(f'M4 37V{bottom - 28}H7V43L20 35H15Z', '#fff3d4')
    path(f'M{width - 4} 37V{bottom - 28}H{width - 7}V43L{width - 20} 35H{width - 15}Z', '#eac18e')
    path(f'M13 36Q{width // 2} 59 {width - 13} 36L{width - 18} 39Q{width // 2} 63 18 39Z', '#e2b788')
    path(f'M18 37Q{width // 2} 52 {width - 18} 37Q{width // 2} 57 18 37Z', '#fff3d4')
    for y in (69, 159, 248, 319):
        rect(9, y, 2, 6, '#efc994')
        rect(width - 11, y + 8, 2, 4, '#f4d7a8')
    end()


def ornament(x, y, scale=1):
    group(f'translate({x} {y}) scale({scale})')
    # Paired curling clouds, with a pointed central flourish.
    curl = ('M0 14V9H3V5H8V2H16V0H22V3H27V7H30V13H27V17H22V18H18V16H15V12H18V10H21V13H23V9H20V7H15V9H12V15H15V20H8V19H3V17H0Z')
    path(curl, '#fff0cf', '#b88360')
    group('translate(76 0) scale(-1 1)')
    path(curl, '#fff0cf', '#b88360')
    end()
    path('M25 13L38 3L51 13L38 26Z', '#fff1ce', '#d4ac7e')
    path('M4 7H8V5H14V3H18V5H12V7H8V11H5Z', '#fff8dd')
    end()


def shield(cx, y):
    group(f'translate({cx - 35} {y})')
    path('M-4 0H74V4H70V65L35 91L0 65V4H-4Z', '#ad7047', '#472a29', 2)
    path('M0 1H70V64L35 88L0 64Z', '#edb66b')
    path('M3 3H67V62L35 84L3 62Z', '#fff1ce')
    path('M5 5H65V60L35 80L5 60Z', '#33586b')
    path('M8 7H61V58L35 76L8 58Z', '#3f6f82')
    rect(8, 7, 2, 48, '#6997a2')
    rect(61, 7, 2, 48, '#27485d')
    path('M35 66L39 70L35 74L31 70Z', '#efb95b')
    rect(34, 68, 2, 2, '#fff1ce')
    end()


def landscape(x, y):
    group(f'translate({x} {y})')
    # Snowy layered mountains and sage foothills; the castle echoes the menu.
    path('M0 52L22 25L34 37L65 7L92 40L110 25L124 51V78H0Z', '#9bc5c9')
    path('M1 54L22 25L34 37L23 34L18 39L14 38Z M44 31L65 7L82 28L72 24L66 29L61 21L55 29Z M99 39L110 25L122 42L111 36L107 39Z', '#fff1ce')
    path('M0 66L34 40L44 48L76 22L124 65V79H0Z', '#79aab4')
    path('M53 46L76 22L89 39L80 35L77 39L73 32L67 39Z', '#f3efda')
    path('M0 74L20 63L33 69L53 62L72 71L91 62L108 66L124 58V88H0Z', '#749d86')
    path('M0 83L22 74L40 81L60 72L78 80L97 74L124 80V92H0Z', '#a2b58b')
    # Gatehouse, towers, crenellations and three crimson flags.
    path('M40 78V61H46V51H57V44H72V51H81V61H89V78Z', '#d99b70', '#765044')
    path('M42 77V61H49V52H58V46H70V52H78V61H86V77Z', '#ffe9bd')
    for tx, ty in ((40, 59), (78, 59), (55, 43)):
        rect(tx, ty, 11, 4, '#b96c55')
        for j in range(3):
            rect(tx + j * 4, ty - 3, 3, 4, '#fff1ce')
        rect(tx + 4, ty + 5, 2, 5, '#6c4641')
    path('M57 78V68H59V65H64V68H66V78Z', '#734641')
    for tx, ty in ((48, 49), (63, 36), (83, 56)):
        rect(tx, ty - 9, 1, 11, '#734641')
        path(f'M{tx + 1} {ty - 9}H{tx + 9}V{ty - 5}H{tx + 5}V{ty - 4}H{tx + 1}Z', '#c65044', '#734641')
    cloud(-4, 2, .43)
    cloud(91, 0, .4)
    path('M0 91Q10 80 23 89Q32 80 44 89Q60 84 69 94Q81 81 96 89Q112 79 124 88V98H0Z', '#f5d8a8')
    path('M0 94Q10 83 23 92Q32 83 44 92Q60 87 69 97Q81 84 96 92Q112 82 124 91V101H0Z', '#ffe9c3')
    end()


def hanger():
    # Reuse the original lobby suspension birds and tassels, in the same palette.
    root = ET.parse(ART / 'scroll-hanger.svg').getroot()
    nodes = list(root)
    # First 14 elements are tassels; two claw paths precede the bird groups.
    for transform, indices in [('translate(-35 -18) scale(1.45)', range(7)),
                               ('translate(110 -18) scale(1.45)', range(7, 14)),
                               ('translate(9 -1)', [16]),
                               ('translate(290 -1)', [17])]:
        group(transform)
        for i in indices:
            # CSS custom properties in SVG are resolved for portable librsvg rendering.
            markup = ET.tostring(nodes[i], encoding='unicode')
            for key, value in (('--shade', '#e8b768'), ('--light', '#fff1c3'), ('--mid', '#d2924f')):
                if i == 17:
                    value = {'--shade': '#87bcd2', '--light': '#e0f4f2', '--mid': '#6392ae'}[key]
                markup = markup.replace(f'var({key})', value)
            parts.append(markup)
        end()


def strap(x):
    group(f'translate({x} 20)')
    path('M0 0H15V17L12 23H3L0 20Z', '#a6403c', '#472a29', 2)
    rect(2, 1, 3, 16, '#edab70')
    rect(11, 1, 2, 16, '#6d3338')
    path('M3 18H6V20H10V18H13V21L10 23H6L3 21Z', '#efb95b')
    # Keep ornaments distinct from the exact bird highlight colors sampled by CI.
    rect(7, 18, 3, 3, '#ffe4a0')
    end()


def draw():
    parts.append('<svg xmlns="http://www.w3.org/2000/svg" width="800" height="400" viewBox="0 0 800 400" shape-rendering="crispEdges">')
    rect(0, 0, 800, 400, '#68b5e5')
    rect(0, 115, 800, 140, '#71bceb')
    rect(0, 255, 800, 145, '#7bc3ed')
    for x, y, s in [(146, -9, .8), (279, -12, .75), (491, -10, 1), (653, -12, .8),
                    (-18, 91, .8), (744, 85, 1), (20, 218, .7), (722, 220, .9),
                    (-17, 304, 1.1), (750, 304, 1), (28, 367, 1.4), (680, 369, 1.3)]:
        cloud(x, y, s)
    for x, y, s in [(-25, 362, 1.4), (149, 376, 1), (546, 380, 1.2), (739, 363, 1.4)]:
        cloud(x, y, s, True)
    scroll(78, 128, 355)
    scroll(598, 128, 355)
    scroll(216, 372, 377, True)
    # Inset around the exact board rectangle: (224,44) to (580,356).
    rect(222, 42, 360, 316, '#c29b75')
    rect(223, 43, 358, 314, '#fff3d4')
    rect(224, 44, 356, 312, '#e7c392')
    ornament(94, 77, 1.25)
    ornament(615, 108, 1.15)
    shield(142, 135)
    shield(662, 137)
    landscape(80, 247)
    landscape(600, 235)
    ornament(295, 366, .9)
    ornament(441, 366, .9)
    # Brown footer crown, isolated from files and board feedback.
    group('translate(392 369)')
    path('M0 3L5 6L9 0L13 6L18 3L15 14H3Z', '#8d5540')
    rect(3, 16, 12, 2, '#8d5540')
    end()
    # One continuous wooden rod, with joints and warm gold highlights.
    path('M59 24H741V33H59V31H55V26H59Z', '#b97240', '#472a29', 2)
    rect(59, 25, 682, 2, '#ffe4a1')
    rect(59, 28, 682, 2, '#e9b469')
    rect(59, 32, 682, 1, '#8f4834')
    for x in (209, 587):
        rect(x, 24, 6, 9, '#9b583d')
        rect(x + 1, 24, 2, 7, '#fbd48b')
    for x in (96, 174, 239, 548, 616, 694):
        strap(x)
    # The lobby's winged crown crest, centered on our continuous rod.
    root = ET.parse(ART / 'scroll-hanger.svg').getroot()
    group('translate(2 -16) scale(1.6 .85)')
    for node in list(root)[-6:]:
        parts.append(ET.tostring(node, encoding='unicode'))
    end()
    hanger()
    parts.append('</svg>')


if __name__ == '__main__':
    draw()
    output = ROOT / 'assets/images/game-scrolls.png'
    with tempfile.TemporaryDirectory(prefix='flappy-art-') as scratch:
        svg = Path(scratch) / 'game-scrolls.svg'
        svg.write_text('\n'.join(parts))
        subprocess.run(['rsvg-convert', str(svg), '-o', str(output)], check=True)
    print(f'{output}: {output.stat().st_size:,} bytes')
