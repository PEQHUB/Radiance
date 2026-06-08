# Material Lab Missing Features

This is a product backlog for the current channel-first Material Lab pass. It is intentionally grounded in what the implementation can plausibly support: procedural recipes, pack/generated/final previews, runtime bake/upload, saved modifier JSON, and RadSER texture rules. It does not assume node graphs, imported maps, pixel painting, hidden material classifiers, or batch sprite edits.

AO is no longer a visible editing channel. Existing LabPBR `_n.B` AO data should be preserved by the compiler unless a future shader-backed lighting use case makes AO editing worth reintroducing.

## Cross-Cutting UI

- Reduce empty space with compact channel cards, denser two-row control clusters, and a smaller bottom strip.
- Add pack/generated/final preview lanes for every procedural channel.
- Add before/after compare for saved baseline vs unsaved preview.
- Add visible dirty-state summary showing which channels have recipe overrides.
- Add better provenance badges: Pack, Generated, Recipe, Runtime Cache, Rule Only.
- Add slider/value affordances that clearly separate dropdowns, toggles, sliders, and disabled roadmap controls.
- Add per-channel warnings beside the relevant controls instead of large dead text blocks.
- Add sibling sprite navigation and texture browser/search without making batch edits.
- Add generated mask thumbnails that update immediately after slider release or source mode changes.
- Add compact histogram/levels widgets where channel generation is image-derived.

## Roughness / Smoothness (`_s.R`)

Current state: roughness source mode, flat roughness, generated blend, flat blend, min/max remap, gamma, contrast, edge influence, smoothing, invert, preview, bake to LabPBR smoothness.

Missing:

- Split preview for pack smoothness, generated roughness, flat value, and final packed result.
- Histogram/levels controls: black point, white point, midpoint, auto-range.
- More generator modes: inverse luminance, edge/cavity, saturation, height-curvature, crack/detail emphasis.
- Frequency controls: broad/mid/fine detail sliders, blur radius, sharpen amount, denoise strength.
- Wear/polish/wetness overlays with separate edge roughness.
- Explicit roughness-to-smoothness readout for `_s.R` packing.
- Per-channel reset that shows exactly which roughness fields will be cleared.

## Metal / F0 (`_s.G`)

Current state: pack/flat/measured/LabPBR source modes, measured metal presets, scalar F0, LabPBR metal-code slider, conductor RGB F0 fields, RadSER conductor texture rule.

Missing:

- Real metal mask controls instead of mostly flat scalar metal.
- Named LabPBR metal-code picker instead of raw 230-255 numeric slider.
- Separate dielectric F0 map controls for non-metal surfaces.
- Pack metal-code preservation UI with explicit preserve/override status.
- Metal mask generator modes: luminance threshold, saturation threshold, edge mask, channel-derived mask.
- Mask cleanup: soften, dilate, erode, despeckle, clamp.
- Conductor inspector swatches with measured source notes and preview of RGB reflectance.
- Oxide/tarnish/patina layer controls for metals that should not be perfect conductors.
- Better distinction between LabPBR pack code output and RadSER measured conductor RGB rule output.

## Porosity / SSS (`_s.B`)

Current state: preserved by truth contract, not exposed as an editable channel.

Missing:

- Channel mode selector: preserve, porosity, SSS.
- Porosity amount, remap, invert, cavity influence, and wetness coupling.
- SSS strength, radius, tint, thickness/depth, and material preset defaults.
- Pack/generated/final preview lanes for `_s.B`.
- Shader/native backing before any editable control is enabled.
- Audit JSON and self-test coverage for `_s.B` preservation and override.

## Emission (`_s.A`)

Current state: manual mask source, gain, low/high threshold for luminance, invert, whole-texture mode, spec-alpha mode, bake to `_s.A`.

Missing:

- Physical emission color/tint and luminance/nits controls.
- More mask sources: albedo red/green/blue, hue, saturation, value, spec alpha, manual flat mask.
- Mask cleanup: softness, dilate, erode, despeckle, blur.
- Split preview for source mask, thresholded mask, and final emission.
- Optional pulse/flicker/animation recipe metadata.
- Clear warning when emission edits change texture emission mask but do not create block-light behavior.
- Better disabled roadmap controls for physical light integration.

## Height (`_n.A`)

Current state: pack/generated/flat source, generated blend, flat blend, flat height, scale, gamma, min/max, offset, smoothing, invert, bake to normal alpha.

Missing:

- Split preview for pack height alpha, generated height, flat height, and final height.
- More height generators: luminance, inverse luminance, edge/cavity, distance/bevel, shape-from-shading.
- Histogram/levels controls and auto-normalize.
- Blur radius, sharpen amount, erosion/dilation, denoise.
- Per-material displacement scalar with global cap visibly wired to renderer settings.
- Parallax/displacement mode status and eligibility warnings.
- Height range readout showing min/max alpha and packed metadata.
- Relief preview or exaggerated lighting preview for judging height shape.

## Normal (`_n.RG`)

Current state: pack/generated source, generated blend, pack strength, generated strength, overall strength, flip green, generated normal from height, bake to normal RG.

Missing:

- Generator radius and Sobel/Scharr mode.
- Separate X/Y strength controls.
- Detail normal controls with strength, frequency, and blend mode.
- Combine modes: replace, add detail, overlay generated on pack, preserve pack Z convention.
- Normal smoothing actually wired to compiler if exposed.
- Tile/seam preview.
- Normal validity warnings for overdriven vectors or inverted handedness.
- More orientation presets beyond flip green.

## Transmission / IOR

Current state: dielectric preset, IOR, transmission scalar, RadSER texture rule; alpha remains Minecraft coverage.

Missing:

- Absorption color and absorption distance.
- Thin glass vs solid volume mode.
- Refraction roughness or blur.
- Thickness mask/source controls.
- Better water/ice/glass preset inspector with physical readout.
- Stronger alpha/cutout warning in the inspector.
- Shader/audit tests proving transmission rules affect the intended material path.

## Advanced Optics

Current state: anisotropic scalar, coat weight, coat roughness, sheen weight, sheen tint; roadmap text for missing controls.

Missing:

- Anisotropic rotation and direction/tangent controls.
- Coat IOR, coat tint, and optional coat mask.
- Sheen roughness and sheen tint map controls.
- UV scale/offset, filter radius, and mip bias once backend support exists.
- Per-material displacement scalar with global cap.
- SSS/porosity controls only after `_s.B` backing lands.
- Physical emission nits/tint only after shader/light integration lands.

## Albedo / Alpha

Current state: pack truth only; no albedo editing.

Missing:

- Read-only alpha coverage preview and warning that alpha is not transmission.
- Optional albedo analysis only as a generator input, not an editable albedo pipeline.
- Color statistics and luminance histogram to explain generated masks.
- Sibling texture context for multi-texture blocks without batch editing.

## Persistence / Audit / QA

- Extend self-tests for each future enabled control before exposing it.
- Audit JSON should include active channel recipe fields, generated/final preview status, and texture-rule fields.
- Save/Revert/Reset should report exactly which channels changed.
- Reload/rehydrate should prove saved recipes restore the same bake state.
- In-game QA still needs small/current/wide layout checks, live preview checks, generated mask visibility, Save/Revert/Reset checks, and DebugBridge audit checks.
