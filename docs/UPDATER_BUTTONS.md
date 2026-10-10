# Standard Chamomilo controls

Waypointer 1.6.17 embeds Chamomilo Interface Kit 0.4.4 through the canonical
updater 1.1.0; its reviewed SDK is pinned in the updater reference. It includes the six compact textures
and the public v1 native adapters. Large kit masters and former generated
map interface bitmaps are not packaged. The custom compass dial and
rotating needle are retained as embedded resources and file-backed fallbacks.

`WaypointerUi.button` creates `ChamomiloUiV1Button` with native press/release
actions, animation, bold yellow hover captions and normal disabled state.
Widths allow both native caption fonts. Frames and button materials tile in
both axes; corners stay fixed and final partial tiles are cropped.
Captions use the kit's bundled Alegreya Sans SC fonts and automatic ink fitting.
Navigation selectors, the square ADD / TO / MONITOR action, settings actions
and confirmation dialogs use Low density. Table row actions (including Mark),
filters, compact footers and map toolbars use High density. The mini-map footer
uses the same High density policy with reduced corner geometry inside its 22 px
frame. Hover reserves the real bold font and keeps the control dimensions.

Open windows render at constant opacity through HUD focus/fade transitions,
matching Keybinder. Their texture alpha still preserves transparent corners.

`WaypointerUiWindow` uses the standard movable window with close, collapse,
maximize and resize controls, a five-pixel frame and native position storage.
The map canvases retain their gesture routing and use the kit painter,
nameplates and animated controls. Text fields preserve native input callbacks,
caret, selection, clipboard and Escape; their visible wrapper is the kit field.
Lists use the kit scrollbar and dropdown adapters.

The shared updater is the complete compiled reference module 1.1.0 from
`C:/projects/updater`, with its own original artwork, protocol-1 constructors
and menu/preferences lifecycle. No updater implementation lives in product
sources. `SharedUpdateHooks.install/registerHost` and
`SharedUpdateCoordinator.modInitialized` are the product lifecycle calls.
Its module version and SHA-256 are pinned separately from the Waypointer version.
The build checks exact reference entry names and bytes in the mod JAR and ZIP.

Run `:client-wurm:verifyChamomiloUpdater` and `:client-wurm:verifyWaypointerUi` for native
layout, actions, input routing, scroll limits, four languages and fonts 10/12/18.
The Waypointer offscreen previews use the real compact SDK textures and bundled fonts.
The opacity regression changes HUD alpha between frames and checks all submitted
SDK primitives and caption paints. Release verification
rejects obsolete product UI bitmaps and runs the canonical kit embedding verifier. Actual
game GPU rendering is a manual check.
