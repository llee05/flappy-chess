# Lobby artwork

The scroll menu uses original, hand-drawn SVG pixel art: `scroll-sheet`,
`scroll-hanger`, `scroll-footer`, `title-ornaments`, `menu-ribbon`, the four
`menu-*` scenes, `menu-icons`, and `utility-bevel`. Shapes use a small integer
coordinate grid and `shape-rendering="crispEdges"`; there are no embedded fonts,
bitmaps, scripts, filters, or external dependencies. These eleven assets total
about 20 KB uncompressed. The existing golden title lettering is retained.

The paper and button frames deliberately use `preserveAspectRatio="none"` so
native HTML controls can grow independently of the illustrations on phones.
Scene images keep their proportions. Text, focus indicators, interaction states,
and sound state belong to HTML/CSS rather than being baked into the artwork.

The animated sky and chess-carrying birds are drawn separately by
`../menu-flights.js`. Older park and panel images remain available here but are
not requested by the scroll menu.
