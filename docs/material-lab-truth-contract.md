# Material Lab Truth Contract

Material Lab edits texture sprites, not blocks, graph nodes, or hidden parent materials. The selected sprite is the only edited target; block context and sibling textures are navigation aids.

Saved state is a modifier recipe under `radiance/material_lab/profiles/<stackFingerprint>.json`. Generated `_n` and `_s` images are runtime rebuild products, not the saved truth.

Legacy AutoPBR node sidecars under `radiance/autopbr/rules` are ignored by Material Lab. They must not affect rehydrate, audit, compile, or runtime upload.

The UI is channel-first: the bottom strip selects one active channel and the center panel edits only that channel. Controls must not be fake buttons standing in for dropdowns, and no control may imply map import, pixel painting, material-family classification, or batch sprite edits.

Alpha remains Minecraft coverage/opacity. Physical transmission and IOR are RadSER material rules and never inferred from albedo alpha.

Measured material presets are manual only. They come from `assets/radiance/material_lab/common_materials.json`; applying a metal preset may emit a RadSER `TEXTURE_RULE_CONDUCTOR_F0_RGB` rule, but it must never be selected by sprite-name guessing.

LABPBR packing:

- `_s.R` stores smoothness. UI edits roughness and the compiler converts to smoothness.
- `_s.G` stores F0 or a LabPBR metal code. Pack-authored LabPBR metal codes stay pack truth until a recipe explicitly overrides metal/F0.
- `_s.B` is preserved unless SSS/porosity receives complete backing.
- `_s.A` stores manual emission.
- `_n.RG` stores normal XY, `_n.B` stores AO, `_n.A` stores height.

AO editing is a LabPBR packed-channel compatibility feature until shader lighting use is explicitly added and tested.

Every enabled control must have all of these before it appears as an editable control: persisted recipe state, Java evaluation, texture bake or texture-rule upload, native/shader consumption, audit JSON, and self-test coverage. Unsupported future controls may be shown only as disabled/status text.
