# Radiance for Minecraft 1.20.1 — Engineering PRD & Implementation Plan

This document has two parts. **Part 1** is the engineering Product Requirements Document — what is being built, the locked technical decisions, the staged GitHub-release milestones, and the explicit risk gates each release must clear. **Part 2** is the Implementation Plan — the engineering checkpoints that produce the artifacts described in Part 1.

This is not a launch document. There is no Modrinth listing, no install metric, no marketing claim. The audience is the engineer building it, future contributors who fork it, and self-supporting end users who install from GitHub Releases on a clean Fabric profile.

---

# Part 1 — Engineering PRD

## 1. Summary

A GitHub-only Windows backport of the Radiance Fabric mod from Minecraft 1.21.4 to Minecraft 1.20.1. Radiance replaces the vanilla OpenGL renderer with a Vulkan ray-tracing renderer (the C++ project MCVR, shipped as `core.dll`).

The first goal is **feasibility** — prove the Java side compiles against 1.20.1 yarn (with deferred mixin source files compile-quarantined in a separate source root), the native library loads, and the mod boots without breaking vanilla rendering. Each subsequent staged GitHub Release adds one strict layer of the renderer dependency chain (vanilla unbroken → boot path → world/chunk/buffer bridge → entities/particles/UI → ray tracing & DLSS → hardening). No release is gated on a marketplace launch; each is a GitHub Release artifact intended for the user's own testing and for early-adopter contributors who self-install on a clean Fabric profile.

The dominant risk is that the renderer half (MCVR) is C++ / CMake / Vulkan SDK and the engineer has no prior C++ experience. This risk is addressed in §4.2, §10, and Implementation Checkpoint 0.

## 2. Audience

In priority order:

1. **The engineer building this** — uses this PRD as a technical contract for what each staged release must demonstrate.
2. **Future contributors** — anyone who forks, PRs, or proposes feature work after the initial release. The PRD's locked decisions in §4 are the reason patches that contradict them get rejected.
3. **Advanced 1.20.1 Fabric users** — comfortable installing from GitHub Releases, reading log files, capturing crash dumps, and running on a clean Fabric profile. Not modpack players. Not modpack curators. Not content creators.

## 3. Distribution

GitHub Releases only. Each release publishes:

- Windows Fabric 1.20.1 jar — `Radiance-<version>-fabric-1.20.1-windows.jar`
- Source git tag matching the release tag
- SHA-256 checksum file (`Radiance-<version>-fabric-1.20.1-windows.jar.sha256`)
- `README.md` — install instructions, GPU/Vulkan requirements, MSVC redistributable note
- `KNOWN-ISSUES.md` — every observed defect, with severity
- `CRASH-REPORTING.md` — how to capture `.minecraft/logs/latest.log`, JVM `hs_err_pid*.log` files, and Windows `.dmp` crash dumps

Explicitly out:

- Modrinth, CurseForge, or any third-party marketplace listing
- Auto-update / phone-home / any network call originated by the mod
- Install metrics, telemetry, post-release analytics
- Marketing copy in the release description (release notes are a technical changelog)

The end-user install is **not** a single-file install. They need: Fabric Loader (installed via the official Fabric installer) + Fabric API jar + Radiance jar + (optional) NVIDIA DLSS DLLs + a recent MSVC runtime. Each release's README must spell this out.

## 4. Hard Requirements (locked decisions)

### 4.1 Source baseline
- PEQHUB/Radiance @ main, `mod_version=0.1.3-alpha`, local checkout `/Users/lavin/Projects/Radiance Backport for 1.20.1`.
- Upstream `Minecraft-Radiance/Radiance` v0.1.5-alpha deltas: NOT integrated. Cherry-pick window opens after v1.0 ships.
- Reason: avoid changing two axes at once (MC version backport + upstream rebase).

### 4.2 Native baseline (MCVR)
The MCVR side ships as `core.dll`. It is built from a separate C++ repository. New JNI symbols are added during this backport (handshake, ABI validation, buffer abstraction), so MCVR must be rebuilt — there is no way to use the upstream 1.21.4 binary.

Staged across Implementation Checkpoint 0a / 0b / 0c to avoid the chicken-and-egg of "add JNI symbols before MCVR knows about them":

- **0a (proof of toolchain):** clone MCVR, install Vulkan SDK + glslang + VS 2022 build tools, run `./gradlew compileJava` against the **unmodified** Java tree (still on 1.21.4 mappings) to generate JNI headers, then build MCVR against those headers. Proves the engineer can build C++ at all. **Result:** an unmodified `core.dll` matching the unmodified Java tree.
- **0b (Java JNI surface lands):** all new JNI declarations land in Java; 1.20.1 yarn migration begins; **compile-quarantine of deferred mixins** (§4.5); JNI headers regenerate.
- **0c (MCVR fork branch):** create an `mc/1.20.1` branch of the local MCVR fork. Implement the new JNI symbols. Add ordinal table for `mcVersionId == 12001`. Rebuild. **Result:** a `core.dll` matching the 1.20.1 Java tree at the alpha-0 surface.

For v1.0, MCVR's fork supports **only `mcVersionId = 12001`** (MC 1.20.1). Maintaining `12104` (MC 1.21.4) compatibility in the fork doubles the C++ ordinal-table maintenance burden for a first-time C++ engineer with no upstreaming agreement; defer it to OQ-12. The handshake protocol (§4.3) is forward-compatible — adding `12104` later is one new branch in a switch.

Mixed-version pairing (1.21.4 native against 1.20.1 Java) is **explicitly prohibited** — `Constants.java` enum tables would silently mismatch.

If the engineer cannot complete 0a in ~2 weeks of part-time work, slow down and pair-program through the C++ side with Claude Code rather than rushing it. The OSS-collaborator escalation path that earlier drafts called "Path B" has been retired — the user does all C++ work themselves with Claude Code as a pair-programming assistant on the Windows box.

### 4.3 JNI compatibility strategy
- **Java owns the ordinal table.** All Minecraft-version-sensitive ordinals (vertex format, draw mode, index type, geometry type, RT flag) are constructed on the Java side from `Constants.java` via `Constants.dumpOrdinals(): long[]` (a pure-Java method, no JNI).
- New JNI: **`RendererProxy.handshake(int mcVersionId, long[] javaOrdinals): int`** — Java passes its full structured ordinal table (format below). Native compares against its own table for that mcVersionId. Returns `0` for match; non-zero status code identifies the first mismatched section/entry.
- New JNI: **`RendererProxy.validateAbi(int mcVersionId, long[] javaOrdinals): int`** — same arguments as handshake; idempotent re-check usable at any point. Same return semantics. (Boring is good: not stateful; does not depend on a cached "last handshake" on the native side.)
- On non-zero handshake return: log fatal `Radiance: native renderer ABI mismatch (code=N). Renderer disabled.` Set `RadianceState.INIT_FAILED` (§4.7). Do not crash.
- **Call site: `RadianceClient.onInitializeClient` → `initializeNativeRenderer(...)` → `performHandshake()`.** Already wired in current code. Native loads via `System.load`; if `RendererProxy.handshake(MC_VERSION_ID, ordinals)` throws `LinkageError` (the C++ symbol is missing — e.g., MCVR not yet built with the new symbol), it is caught and the renderer transitions to `INIT_FAILED` rather than crashing the JVM. Boot continues to vanilla GL.

There is no native "dump ordinals" call. The Java side is the source of truth; native only confirms or rejects.

#### 4.3.1 Locked structured ordinal table format

`Constants.dumpOrdinals(): long[]` emits a self-describing table. **MCVR's C++ decoder must match this layout exactly.** All values are `long` (64-bit signed, native-endian on x86_64 = little-endian, transported unmodified by JNI's `jlongArray`).

**Header (3 longs):**
```
[0]  ORDINAL_TABLE_MAGIC   = 0x5241445F4F524453  (ASCII "RAD_ORDS")
[1]  ORDINAL_TABLE_VERSION = 1
[2]  ORDINAL_TABLE_SECTION_COUNT = 5
```

**Sections (always 5, in this fixed order):**

| Section ID | Constant | Source enum | Notes |
|---|---|---|---|
| 1 | `SECTION_VERTEX_FORMATS` | `Constants.VertexFormats` | Active entries first, then reserved entries from `RESERVED_VERTEX_FORMAT_ORDINALS = {10L, 11L, 12L}` (slots intentionally vacated by 1.20.1 backport — see §4.4). |
| 2 | `SECTION_DRAW_MODES` | `Constants.DrawModes` | All entries `ENTRY_ACTIVE`. |
| 3 | `SECTION_INDEX_TYPES` | `Constants.IndexTypes` | All entries `ENTRY_ACTIVE`. |
| 4 | `SECTION_GEOMETRY_TYPES` | `Constants.GeometryTypes` | All entries `ENTRY_ACTIVE`. |
| 5 | `SECTION_RAY_TRACING_FLAGS` | `Constants.RayTracingFlags` | All entries `ENTRY_ACTIVE`. |

**Per-section layout:**
```
[s+0]  section-id              (1..5, must match table above)
[s+1]  payload-length-in-longs (= 3 × entry-count)
[s+2 .. s+1+payload-length]  entry triples, each:
       [entryId, abiValue, flags]
       flags = ENTRY_ACTIVE   (0L)  → live entry
       flags = ENTRY_RESERVED (1L)  → intentional gap; abiValue is the reserved ordinal
```

For active enum entries, `entryId` is the enum's ordinal index (`enum.values()[index]`) and `abiValue` is `enum.getValue()`. For vertex format reserved entries, `entryId == abiValue == reservedOrdinal`. The reserved-entry mechanism lets MCVR distinguish "Java intentionally has a gap at slot N" from "Java's table drifted." Do not collapse it.

**Mismatch return codes (recommended convention for MCVR's C++ side, not enforced by Java):**
- `1` — magic mismatch (table is not RAD_ORDS, or wrong endianness)
- `2` — version mismatch (`ORDINAL_TABLE_VERSION` not understood)
- `3` — section count mismatch
- `4` — unknown section ID
- `5` — section-payload-length mismatch
- `6` — entry value mismatch (per-section/entry context to help triage)
- `7` — reserved-entry semantics violated (e.g., Java says reserved, native expects active)

The Java side does not parse the return code beyond "0 vs non-zero" — but logging the code helps debugging (especially when pairing through the C++ side with Claude Code). The single source of truth is `Constants.dumpOrdinals()` at the head commit when MCVR's `mc/1.20.1` branch is built; future Java changes to the table that don't bump `ORDINAL_TABLE_VERSION` are an ABI break.

If the table needs to evolve (new section, new entry layout): bump `ORDINAL_TABLE_VERSION`. Native rejects unknown versions with code `2`; both sides recompile in lockstep.

### 4.4 Buffer abstraction and JNI transport
The Java side currently leaks Minecraft buffer types across JNI:

- `BufferProxy.createAndUploadVertexIndexBuffer(BuiltBuffer)` reads `BuiltBuffer.getDrawParameters()`.
- `ChunkProxy.rebuildSingle(...)` passes per-buffer geometry metadata.
- `EntityProxy.queueBuild(...)` passes packed entity geometry, vertex format ordinals, layer counts.
- `PBRVertexConsumer` wraps `VertexConsumer`.

Three Java-side classes replace the leakage:

1. **`RadianceBufferHandle`** (POJO) — explicit fields: `vertexCount: int, indexCount: int, vertexFormatOrdinal: int, indexTypeOrdinal: int, drawModeOrdinal: int, hasData: boolean, centroidArrayPtr: long, centroidArrayLen: int`. Replaces every JNI reference to `BuiltBuffer`.
2. **`RadianceBufferAdapter`** — single static method `from(BuiltBuffer): RadianceBufferHandle` against 1.20.1's `BufferBuilder.BuiltBuffer` shape. The single source of truth for converting MC buffer types into Radiance ones.
3. **`RadianceVertexConsumer`** interface — wraps the surface used by `PBRVertexConsumer`. JNI sees `RadianceVertexConsumer`, not MC's `VertexConsumer`.

**JNI transport — locked.** `RadianceBufferHandle` serializes to a fixed-layout 40-byte direct `ByteBuffer`. Native reads via `MemoryUtil.memAddress(buf)` plus documented offsets. Layout:

```
offset 0  (4 bytes): vertexCount        (int32, LE)
offset 4  (4 bytes): indexCount         (int32, LE)
offset 8  (4 bytes): vertexFormatOrdinal (int32, LE)
offset 12 (4 bytes): indexTypeOrdinal   (int32, LE)
offset 16 (4 bytes): drawModeOrdinal    (int32, LE)
offset 20 (4 bytes): hasData            (int32, LE; 0 or 1; padded for 8-byte alignment)
offset 24 (8 bytes): centroidArrayPtr   (uint64, LE)
offset 32 (4 bytes): centroidArrayLen   (int32, LE)
offset 36 (4 bytes): pad                (zero)
```

Total: 40 bytes. Java provides `RadianceBufferHandle.toByteBuffer(): ByteBuffer` (allocates a direct ByteBuffer with this layout) and `RadianceBufferHandle.fromByteBuffer(ByteBuffer): RadianceBufferHandle` (for round-trip testing). This is the **only** JNI vehicle for buffer handles. No POJO reflection on the native side. No alternative shape.

A Java-side unit test rounds a handle through `toByteBuffer` → `fromByteBuffer` and asserts equality. Lands in Implementation Checkpoint 0b.

All proxy classes (`BufferProxy`, `ChunkProxy`, `EntityProxy`) accept either Radiance-owned types (the `ByteBuffer` from `toByteBuffer`) or primitives across JNI. The MC version's `BuiltBuffer`, `VertexConsumer`, `RenderLayer.MultiPhase`, etc. are never part of the JNI contract.

### 4.5 Compile-quarantine + access widener migration
**The biggest remaining issue with the previous draft was assuming runtime mixin allowlisting prevents compile-time breakage. It does not.** `MixinPlugin.ENABLED_MIXINS` only controls whether a mixin applies at runtime; it does not stop `javac` from compiling source files that import 1.21+-only classes (`CloudRenderer`, `SkyRendering`, standalone `BuiltBuffer`, etc.). Without a compile-quarantine strategy, `./gradlew compileJava` fails before any mixin runtime gate is consulted.

**Locked compile-quarantine strategy:** create a parallel source root `src/deferred/java/`. It is **not** a Loom source set; Loom processes only `src/main/java/`. Deferred mixin source files live there, mirroring the main package structure (e.g., `src/deferred/java/com/radiance/mixins/vulkan_render_integration/CloudRendererMixins.java`).

Rules:
- **Files in `src/deferred/java/` are NOT compiled.** They are git-tracked but invisible to `javac` and Loom.
- **`radiance.mixins.json` only lists classes that exist in `src/main/java/`.** JSON has no comment syntax and Mixin's parser is strict, so deferred mixins are **removed from the `mixins`/`client` arrays entirely** and recorded in a sibling `_deferred_until_implemented` string array (Mixin ignores keys with a leading underscore). That sibling array is the audit log of which mixins are deferred, why (1.21+ API name), and which checkpoint owns the rewrite.
- **`MixinPlugin.ENABLED_MIXINS` only contains classes listed in `radiance.mixins.json`.** Three layers of agreement: source root + JSON + allowlist.
- **Each checkpoint moves a deferred mixin's source file** from `src/deferred/java/` back to `src/main/java/` via `git mv`, ports it to compile against 1.20.1 yarn, adds it to `radiance.mixins.json`, then adds it to `MixinPlugin.ENABLED_MIXINS`. All four steps in one checkpoint.

Initial deferred set (moved to `src/deferred/java/` in Implementation Checkpoint 0b):
- `mixins/vulkan_render_integration/CloudRendererMixins.java` (imports 1.21+ `CloudRenderer`)
- `mixins/vulkan_render_integration/SectionBuilderMixins.java` (imports 1.21+ `SectionBuilder`)
- `mixins/vulkan_render_integration/BuiltBufferMixins.java` (imports 1.21+ standalone `BuiltBuffer`)
- `mixins/vulkan_render_integration/WorldRendererMixins.java` (deferred WHOLE — the originally-planned split into Core + SkyWeather was not viable; replacements are written fresh in Checkpoints C/D — see §4.6)
- `mixins/vulkan_render_integration/RenderLayerMixins.java` (uses 1.21+ `RenderLayer.of(...)` builder shape)
- `mixins/vulkan_render_integration/VideoWarningManagerWarningPatternLoaderMixins.java` (uses 1.21+ `WarningPatternLoader.buildWarnings()` signature)
- `mixins/vulkan_render_integration/ReloadableResourceManagerImplMixins.java` (uses 1.21+ `registerReloader` signature variant)
- `mixins/vanilla_resource_tracker/ReloadableTextureMixins.java` (imports 1.21+ `ReloadableTexture`)
- `mixins/vulkan_options/VideoOptionsScreenMixins.java` (uses 1.21+ SimpleOption callback shapes)
- Any mixin discovered to have 1.21+-only imports during the move (the list above is the known set; more may emerge).

**Access widener classification** (related pre-flight): every entry in `src/main/resources/radiance.accesswidener` is classified as:
- (a) Target exists in 1.20.1 yarn — keep entry verbatim.
- (b) Target does not exist — drop entry, add the depending mixin to the deferred set.
- (c) Target exists with a different shape — rewrite entry; the depending mixin must be rewritten in the same checkpoint.

No mixin's AW entries land before the mixin compiles. No AW entry survives if its target does not exist in 1.20.1 yarn.

### 4.6 Renderer bring-up path

Each milestone enables strictly the mixins required for what it claims to demonstrate. Earlier "tier-3 = low risk yarn renames" framing was wrong: boot-path mixins (`Window`, `MinecraftClient`, `RenderSystem`, `GameRenderer`, `BufferRenderer`, `GLX`, `GlStateManager`) are high-risk because they touch render init, framebuffer ownership, and the render loop.

**`WorldRendererMixins` handling (amended 2026-05-11 after 0b shipped):** the original plan was to split the single `WorldRendererMixins.java` into `WorldRendererCoreMixins` (alpha-2) and `WorldRendererSkyWeatherMixins` (beta-1). Execution proved that wasn't viable — the upstream file's `redirectRender(...)` interleaves sky uniforms, cloud rendering, chunks, and entity dispatch in a single `@Inject`, so a clean split before rewriting the body is impossible. **Revised plan: defer the whole `WorldRendererMixins.java` after Checkpoint 0b. Checkpoint C will reconstitute the terrain/chunk responsibilities by writing a fresh `WorldRendererCoreMixins.java` against 1.20.1 yarn (sourcing the relevant inject points fresh, not splitting the upstream file). Checkpoint D does the same for sky/weather/cloud.** The alpha-2 / beta-1 milestone tables below still describe the *target* mixin-set names because the names are forward-looking; execution will rebuild them, not bisect the original.

| Milestone | Mixins enabled | What it actually demonstrates |
|---|---|---|
| **alpha-0** | None of the renderer mixins. Only the four `vanilla_resource_tracker.*` resource-shadow mixins. | Mod loads, native loads, handshake validates, **vanilla MC still renders normally** (vanilla world load works). |
| **alpha-1** | + Boot-path: `Window`, `MinecraftClient`, `RenderSystem`, `GameRenderer`, `BufferRenderer`, `GLX`, `GlStateManager`. `MinecraftClientMixins` is the mixin that calls `RendererProxy.initRenderer(window)` once Window is available — see §6 FR-09. | Vulkan init runs to completion. Main menu loads. **Verification is log-based** (no F3 — F3 is in-world). |
| **alpha-2** | + World/chunk/buffer path: `WorldRendererCoreMixins`, `BuiltBufferMixins`, `RenderLayerMixins`, `ChunkBuilder`, `ChunkBuilderBuiltChunk`, `BuiltChunkStorage`, `ClientChunkManager`, `SectionBuilder`-equivalent (1.20.1 = `ChunkBuilder$BuiltChunk$RebuildTask`), plus the buffer abstraction from §4.4. | Superflat creative world loads and terrain renders through the Vulkan path. F3 verification (in-world) confirms Vulkan API description. |
| **beta-1** | + Entity / particle / sky / cloud: `EntityRenderDispatcher`, `ItemRenderer`, `HeldItemRenderer`, `BlockColors`, `BlockModelRenderer`, `FluidRenderer`, `LightmapTextureManager`, `Particle`, `ParticleManager`, `BillboardParticle`, `LightningEntityRenderer`, `BannerBlockEntityRenderer`, `RenderPhase`, `RenderPhaseLightmap`, `RenderPhaseTarget`, `Screen`, `DrawContext`, `ScreenshotRecorder`, plus `CloudRendererMixins`-equivalent (1.20.1 = `WorldRenderer.renderClouds`) and **`WorldRendererSkyWeatherMixins`**. Plus essentials-only `RadianceSettingsScreen` reachable via `O` key. | Real overworld + nether + end. Entities, particles, sky, clouds, weather. F2 screenshots. Settings persist. |
| **beta-2** | + RT pipeline + DLSS + tone mapping fully wired through `Pipeline.assembleDefault`. Pipeline YAML modules active. | Ray-traced lighting visible. DLSS active when DLLs present. Multi-dimension RT. |
| **v1.0** | All of beta-2. Optional: `VideoOptionsScreenMixins` full rewrite (otherwise essentials stay behind the `O` key — see OQ-09). | 24-hour soak passes. License questions resolved. KNOWN-ISSUES finalized. |

Tier-1 / Tier-2 / Tier-3 nomenclature is **retired**.

### 4.7 Renderer state, mixin guards, and native crash containment

**`RadianceState`** is a single global enum maintained by `RadianceClient` and updated on lifecycle events:

| State | Meaning | Set by |
|---|---|---|
| `UNINITIALIZED` | Mod not yet initialized. | Default. |
| `INIT_FAILED` | Handshake failed or fatal init error. | `RadianceClient` on handshake non-zero. |
| `BOOT_OK` | Native loaded, handshake passed, but `initRenderer` not yet called. Resource tracking is OK to begin. | `RadianceClient` after handshake `0`. |
| `RENDERER_ACTIVE` | `initRenderer(window)` succeeded; the Vulkan path is live. | `MinecraftClientMixins` after a successful `initRenderer`. |
| `RENDERER_DISABLED` | Vulkan-feature gate failed at init, or runtime disabled. Renderer mixins must let vanilla run. | `MinecraftClientMixins` on init failure, or any code that explicitly disables. |

**Two predicates** (do not collapse them into one):

- **`RadianceState.isResourceTrackingEnabled()`** — true for `BOOT_OK` and `RENDERER_ACTIVE`. Used by the four `vanilla_resource_tracker.*` mixins so they can begin shadowing resources as soon as the native side is loaded — the Vulkan path needs to know about resources loaded **before** `initRenderer` runs (textures loaded during early MC bootstrap). Resource tracking off would starve the renderer when it later activates.
- **`RadianceState.isRendererActive()`** — true only for `RENDERER_ACTIVE`. Used by every render-suppression mixin. False during `BOOT_OK` (Vulkan not yet up), `INIT_FAILED`, and `RENDERER_DISABLED`.

**Mixin guard patterns by injection type — locked.** "Renderer disabled" must be implemented correctly per mixin type:

| Mixin annotation | Guard pattern when `!isRendererActive()` |
|---|---|
| `@Inject(at = HEAD/TAIL/RETURN, cancellable = false)` | `if (!isRendererActive()) return;` — the inject simply does nothing extra; vanilla logic runs as written. |
| `@Inject(cancellable = true)` | `if (!isRendererActive()) return;` — do **NOT** call `ci.cancel()`. Vanilla method completes normally. |
| `@Redirect` | `if (!isRendererActive()) { return originalMethodInvocation(args); }` — manually invoke the original target you redirected. The redirect annotation replaces the original call, so "let vanilla run" requires you to call it yourself. |
| `@ModifyArg` / `@ModifyVariable` | `if (!isRendererActive()) return original;` — return the unmodified argument/variable. |
| `@WrapOperation` (MixinExtras) | `if (!isRendererActive()) return original.call(args);` — call through with the captured `Operation`. |
| `@Overwrite` | **Avoid.** If unavoidable, branch explicitly: `if (!isRendererActive()) { /* paste vanilla implementation here */; return; } /* Radiance behavior */`. The vanilla code must be hand-copied — this is one of the reasons `@Overwrite` is `requireAnnotations: true` in `radiance.mixins.json`. |

A helper `RadianceState.runIfActive(Runnable)` is provided for the simplest `@Inject` cases, but redirect/modify guards must be inlined per call site because the "let vanilla run" path differs structurally.

**Native crash containment:**
- "Native crashes surface as Java exceptions" is **not generally possible**. JNI segfaults terminate the JVM.
- Preflight every native call with Java-side argument validation (null, range, ordinal-in-range, ByteBuffer length).
- When `-Dradiance.dev_logging=true`, log `[radiance.jni] entering <method> args=[…]` at INFO before each native call.
- Only catch JNI exceptions native explicitly throws.
- `KNOWN-ISSUES.md` for every release: *"If the JVM terminates without a Java exception (segfault), the cause is in the native renderer. Capture `.minecraft/logs/latest.log`, the JVM `hs_err_pid*.log`, and any Windows `.dmp` from Werfault."*
- **Vulkan-feature gate at init**: query the GPU's supported Vulkan features. If any required feature (1.3 baseline, ray-tracing pipeline extension, descriptor indexing, buffer device address) is missing, log a clear ERROR and set `RadianceState.RENDERER_DISABLED`.

### 4.8 Disabled features per stage

| Milestone | Disabled / not yet wired |
|---|---|
| alpha-0 | All renderer mixins; all UI screens beyond `DlssMissingScreen`; all RT; all settings UI. Resource-tracker mixins are active and shadowing resources. |
| alpha-1 | World/chunk/buffer mixins; entity/particle/sky/cloud mixins; settings UI; RT; DLSS. |
| alpha-2 | Entity/particle/sky/cloud mixins (`WorldRendererSkyWeatherMixins` still deferred); settings UI; RT; DLSS. |
| beta-1 | RT pipeline; DLSS; HDR; PsychoVisual; non-essential settings UI screens (long-tail sub-screens). |
| beta-2 | HDR; DLSS-G; FSR3-FG; full `VideoOptionsScreenMixins` rewrite; PsychoVisual sub-screen polish. |
| v1.0 | HDR; DLSS-G; FSR3-FG; PBR adapter; Linux; macOS. (`VideoOptionsScreenMixins` rewrite is optional v1.0 — OQ-09.) |

## 5. Release Milestones

Each milestone is a separate GitHub Release with a git tag. Pass criteria are testable, not aspirational. Handshake success and DLSS UX are independent — DLSS-DLL absence does not excuse handshake failure.

### alpha-0 — "boot, load, vanilla still works"
**Maps to:** Implementation Checkpoint 0a + 0b + 0c + Checkpoint A.

**Pass criteria:**
- `./gradlew compileJava` succeeds against 1.20.1 yarn (with deferred mixins quarantined per §4.5).
- `./gradlew runClient` reaches the Minecraft main menu without crash.
- `.minecraft/radiance/` exists after first launch and contains required runtime assets: `core.dll`, `shaders/`, and `modules/`. Optional artifacts (`core.lib`, `sl.interposer.dll`, `sl.common.dll`, `sl.reflex.dll`, `sl.pcl.dll`, `NvLowLatencyVk.dll`) may be absent; absence logs WARN and does not fail alpha-0.
- `latest.log` shows a successful `System.load` of `core.dll`. Streamline DLLs are resolved by Windows DLL search order from the same directory; no explicit `System.load` per Streamline DLL.
- `RendererProxy.handshake(12001, javaOrdinals)` returns `0`. The log line confirms this.
- **Loading a vanilla superflat creative world succeeds and renders correctly through vanilla OpenGL.** No renderer mixins are active.

**Fail conditions:** JVM crash on startup; native library cannot be loaded; handshake returns non-zero; vanilla world load fails.

**Artifact contents:** jar, `.sha256`, README ("alpha-0 — boots, validates ABI, does not yet render through Vulkan"), KNOWN-ISSUES.md (initial), source git tag.

### alpha-1 — "Vulkan boot path"
**Maps to:** Implementation Checkpoint B.

**Pass criteria:**
- All alpha-0 criteria.
- Boot-path mixins enabled (PRD §4.6 alpha-1 row), including `MinecraftClientMixins` which calls `RendererProxy.initRenderer(window)`.
- **Log-based verification (no F3 — F3 requires world load):**
  - `latest.log` contains `[radiance] RendererProxy.initRenderer returned successfully`.
  - `latest.log` contains a line emitted by `MinecraftClientMixins` confirming `RenderSystem.apiDescription` was assigned.
  - `latest.log` contains `[radiance] RadianceState transition: BOOT_OK -> RENDERER_ACTIVE`.
- Main menu renders normally.
- 5 minutes at the main menu without crash.
- **No claim about world rendering.** Loading a world is permitted to crash; KNOWN-ISSUES.md must say so.

**Fail conditions:** crash on startup; `initRenderer` doesn't return success; main menu fails to render.

**Artifact contents:** as alpha-0 + KNOWN-ISSUES.md noting world load is not yet supported.

### alpha-2 — "world/chunk/buffer bridge — superflat renders"
**Maps to:** Implementation Checkpoint C.

**Pass criteria:**
- All alpha-1 criteria.
- World/chunk/buffer mixins enabled (PRD §4.6 alpha-2 row).
- `RadianceBufferHandle` / `RadianceBufferAdapter` / `RadianceVertexConsumer` (§4.4) implemented and used by every JNI proxy call site that previously passed MC buffer types.
- Loaded into a vanilla superflat creative world (Fabric API + Radiance only).
- Terrain renders (not a black screen).
- **F3 debug screen (in-world) shows a Vulkan API description** (currently `"Vulkan 1.4"` per the hardcoded string in `RendererProxy.initRenderer` — see OQ-10).
- Player movement does not crash and does not introduce obscuring artifacts.
- Block place + break works without crash.
- 5 minutes of continuous play without crash.

**Fail conditions:** black screen on world load; crash within 5 minutes; place/break crashes; movement leaves persistent visual artifacts. **A black screen here is almost certainly an ordinal mismatch** — re-run `RendererProxy.validateAbi(12001, ordinals)` and inspect dev-logging output before assuming a mixin bug.

**Artifact contents:** as alpha-1.

### beta-1 — "playable on a clean Fabric profile"
**Maps to:** Implementation Checkpoint D.

**Pass criteria:**
- All alpha-2 criteria.
- Entity/particle/sky/cloud mixins enabled, including the moved `WorldRendererSkyWeatherMixins` (PRD §4.6 beta-1 row).
- Real overworld save loads and renders (terrain + entities + particles + weather + sky + clouds, even with some defects).
- Nether and end dimensions render.
- At least 5 entity types render correctly: zombie, creeper, lightning bolt, item drop, primed TNT.
- F2 screenshot produces a valid PNG showing the rendered frame.
- Pressing `O` opens the essentials-only `RadianceSettingsScreen`. The full `VideoOptionsScreenMixins` rewrite is NOT a beta-1 requirement (OQ-09).
- Essential settings (tone mapping mode, exposure compensation) persist across restart via `.minecraft/radiance/options.properties`.
- 30 minutes of continuous gameplay does not crash.

**Fail conditions:** crash within 30 minutes; entities don't render; nether or end don't render; screenshot is garbage; settings don't persist.

**Artifact contents:** as alpha-2 + `CRASH-REPORTING.md`.

### beta-2 — "ray tracing + DLSS"
**Maps to:** Implementation Checkpoint E.

**Pass criteria:**
- All beta-1 criteria.
- RT pipeline live: `Pipeline.assembleDefault()` runs the RT → tone mapping → post-render path successfully.
- Ray-traced lighting visible in the overworld.
- Tone mapping is applied.
- DLSS path works when user supplies NVIDIA DLSS DLLs.
- `DlssMissingScreen` displays when DLLs absent.
- Pipeline YAML round-trips through `savePipeline`/`loadPipeline`.

**Fail conditions:** RT output is NaN / black / garbage; DLSS doesn't load when DLLs present; pipeline YAML corrupts.

**Artifact contents:** as beta-1.

### v1.0 — "GitHub-stable release"
**Maps to:** Implementation Checkpoint F + soak test.

**Pass criteria:**
- All beta-2 criteria.
- 24-hour soak test passes (no crash, no progressive memory growth via `jcmd <pid> GC.heap_info` snapshots every 4 hours).
- All v1.0 functional requirements (§6) pass on a clean Fabric 1.20.1 profile.
- README, KNOWN-ISSUES.md, CRASH-REPORTING.md complete.
- Streamline DLL redistribution license verified (or DLLs moved to user-supplied — §10 G10).
- OQ-07 (core.lib runtime requirement) resolved.

**Fail conditions:** any FR fails; soak test crashes or shows monotonic heap growth; G10 unresolved.

**Artifact contents:** as beta-2 + signed git tag + SHA-256 + finalized KNOWN-ISSUES.md.

## 6. Functional Requirements

Grouped by the milestone they first apply to.

### alpha-0 — boot
- **FR-01** The mod jar declares `depends.minecraft: 1.20.1` and `depends.fabric: *` in `fabric.mod.json`.
- **FR-02** On first launch, the mod creates `.minecraft/radiance/` if absent.
- **FR-03** The mod extracts `core.dll` (REQUIRED — alpha-0 fails if absent), `shaders/`, and `modules/` from the jar to `.minecraft/radiance/`. Optional artifacts extracted via `copyOptionalFileFromResource` (logged with WARN if absent, never a fatal error): `core.lib` (link-time artifact; OQ-07 tracks whether it's runtime-needed), the four Streamline DLLs (`sl.interposer.dll`, `sl.common.dll`, `sl.reflex.dll`, `sl.pcl.dll`), `NvLowLatencyVk.dll` (Reflex stack — if any one is missing, the loop breaks early since they are co-dependent; Reflex simply becomes unavailable). The optional-natives behavior is implemented by `RadianceClient.copyOptionalFileFromResource(...)`; do not regress alpha-0 release validation by treating these as required.
- **FR-04** The mod calls `System.load(core.dll absolute path)` and logs success or failure with full path and OS error code. Streamline DLLs are NOT individually `System.load`'d (Windows DLL search order resolves them from `.minecraft/radiance/` when MCVR's `core.dll` references them).
- **FR-05** The mod calls `RendererProxy.handshake(12001, Constants.dumpOrdinals())` from `RadianceClient.performHandshake()` (invoked by `initializeNativeRenderer` immediately after `System.load`). Logs the return value at INFO. Failure modes:
  - `LinkageError` (C++ symbol missing — e.g., MCVR built without the `handshake` export): caught; `RadianceState.set(INIT_FAILED)`; log `"[radiance] RendererProxy.handshake could not be called. Renderer disabled."`. Do not crash the JVM.
  - Non-zero return (ABI mismatch per the §4.3.1 codes): `RadianceState.set(INIT_FAILED)`; log `"Radiance: native renderer ABI mismatch (code=N). Renderer disabled."`.
  - Zero return (success): `RadianceState.set(BOOT_OK)`. The four resource-tracker mixins begin shadowing.
- **FR-06** If `nvngx_dlss.dll` and `nvngx_dlssd.dll` are absent, log a WARN with filename, NVIDIA download URL, target folder. **Independent of FR-05** — DLSS absence does not affect handshake.
- **FR-07** If DLSS DLLs are absent, `DlssMissingScreen` opens at the first opportunity. Buttons: "Continue without DLSS", "Open download URL".
- **FR-08** With no renderer mixins active, vanilla MC rendering is unaffected — vanilla world load and play work normally.

### alpha-1 — Vulkan boot path
- **FR-09** **`RendererProxy.initRenderer(window)` is called from `MinecraftClientMixins`, NOT `RadianceClient.onInitializeClient`.** `RadianceClient` does not own a `Window`; the existing code already takes this responsibility in `MinecraftClientMixins`. `RadianceClient` owns: handshake, state transitions, `DlssMissingScreen` flow. `MinecraftClientMixins` owns: `initRenderer` invocation, `RadianceState.BOOT_OK -> RENDERER_ACTIVE` transition.
- **FR-10** Verification at alpha-1 is **log-based**, not F3-based. The mod emits at INFO:
  - `[radiance] RendererProxy.initRenderer returned successfully`
  - `[radiance] RenderSystem.apiDescription set to '<value>'` (emitted from `MinecraftClientMixins` post-init)
  - `[radiance] RadianceState transition: BOOT_OK -> RENDERER_ACTIVE`
  F3 verification is alpha-2 (in-world).
- **FR-11** Main menu renders normally.
- **FR-12** 5 minutes at the main menu without crash.

### alpha-2 — world bridge
- **FR-13** **`RadianceBufferHandle` is the only buffer descriptor passed across JNI**, serialized via the §4.4 40-byte ByteBuffer layout. Native consumes it via `MemoryUtil.memAddress` + offsets. (Verifiable by `grep` for `BuiltBuffer\|VertexConsumer` in `src/main/java/com/radiance/client/proxy/` — should match only `RadianceBufferAdapter.java`.)
- **FR-14** A vanilla superflat creative world loads and renders through the Vulkan path.
- **FR-15** Player movement, block place, block break work without crash.
- **FR-16** F3 debug screen (in-world) shows a Vulkan API description.
- **FR-17** 5 minutes of continuous play without crash.

### beta-1 — full coverage
- **FR-18** Overworld saves with non-flat terrain render.
- **FR-19** Nether and end dimensions render.
- **FR-20** Day-night cycle, sun, moon, weather render.
- **FR-21** At least 5 entity types render correctly.
- **FR-22** Particles render (smoke, flame, splash).
- **FR-23** F2 produces a valid PNG screenshot.
- **FR-24** Pressing `O` opens an essentials-only `RadianceSettingsScreen`.
- **FR-25** Essential settings (tone mapping mode, exposure compensation) persist across restart in `.minecraft/radiance/options.properties` with the schema versioned.
- **FR-26** Pipeline graph persists in `.minecraft/radiance/pipeline.yaml`. Load failure falls back to `Pipeline.assembleDefault()` and rewrites the file.

### beta-2 — RT + DLSS
- **FR-27** Ray-traced lighting renders.
- **FR-28** Tone mapping mode is selectable; default reasonable.
- **FR-29** DLSS quality option appears and applies when NVIDIA DLLs are loaded.
- **FR-30** Full RT-related sub-screens (Exposure, Sun, Moon, Cloud, etc.) open without crash.

### v1.0 — release hardening
- **FR-31** All beta-2 FRs hold under a 24-hour soak test.
- **FR-32** Crash reports surface "Radiance v\<version\> (1.20.1)" via vanilla `CrashReportSection`.
- **FR-33** `-Dradiance.dev_logging=true` enables verbose pipeline / mixin / handshake logging, including JNI preflight argument logging (§4.7).
- **FR-34** `.minecraft/logs/latest.log` is sufficient for the user/contributor to diagnose any non-segfault failure.

## 7. Non-functional Requirements

- **NF-01** Native segfaults terminate the JVM. Recovery is not implemented. KNOWN-ISSUES.md documents this; CRASH-REPORTING.md tells the user how to capture diagnostics.
- **NF-02** Vulkan-feature gate: missing required GPU features result in a logged ERROR and `RadianceState.RENDERER_DISABLED`, **not** a crash.
- **NF-03** No network calls originate from the mod.
- **NF-04** **Radiance-owned** config / native / module / shader I/O is rooted at `.minecraft/radiance/`. Vanilla-owned I/O (screenshots in `.minecraft/screenshots/`, logs in `.minecraft/logs/`, saves in `.minecraft/saves/`) is unchanged.
- **NF-05** Off-heap allocations (LWJGL `MemoryUtil`) in `Pipeline.build` and `RadianceBufferHandle.toByteBuffer()` are released via `try`/`finally` or by lifecycle ownership documented in the call site.
- **NF-06** Mod startup adds at most 5 seconds to client cold start vs. vanilla Fabric MC 1.20.1.

Performance is a v1.0-only NFR:
- **NF-07 (v1.0)** On NVIDIA RTX 3060 or better, 1080p, default render distance, default DLSS quality: ≥30 FPS in a flat overworld scene during stable play.

## 8. Hard Dependencies

| Dependency | Version policy | Source | License | Notes |
|---|---|---|---|---|
| Minecraft Java Edition | 1.20.1 (exact) | Mojang | Mojang EULA | |
| Java Runtime | 17 | Mojang launcher bundles this for 1.20.1 | OpenJDK | Required at runtime; build also targets 17. |
| Fabric Loader | **Tested at 0.15.11** for release validation. README states ranges only after testing wider. | fabricmc.net | Apache 2.0 | OQ-08. |
| Fabric API | 0.92.6+1.20.1 (or any newer 1.20.1 line) | fabricmc.net | Apache 2.0 | User installs separately. |
| Yarn mappings | 1.20.1+build.10 | fabricmc.net | (build-time only) | |
| SnakeYAML | 2.5 | Maven Central | Apache 2.0 | Already a dep. |
| MCVR (`core.dll`) | matched to this Java tree's JNI headers (§4.2). Supports `mcVersionId = 12001` only in v1.0 (OQ-12). | this project's MCVR fork | (verify in §10 G10) | |
| Streamline SDK DLLs | as bundled (5 files) | NVIDIA Streamline GitHub | NVIDIA Streamline License | Redistribution check is a v1.0 release gate (G10). MCVR's `core.dll` resolves them via Windows DLL search order from `.minecraft/radiance/`; no Java-side `System.load` per Streamline DLL. |
| NVIDIA DLSS DLLs | `nvngx_dlss.dll`, `nvngx_dlssd.dll` | NVIDIA DLSS GitHub | NVIDIA DLSS SDK License | **NEVER** redistributed. User-supplied. |
| Vulkan-capable GPU | 1.3 + ray-tracing pipeline extensions + descriptor indexing + buffer device address | hardware | | See §8.1. |
| OS | Windows 10 (1903+) or Windows 11 x64 | Microsoft | | |
| MSVC redistributable | latest VC++ 2017–2026 redist | Microsoft | | Per upstream README's "Windows Fix" note. |

### 8.1 Hardware floor (RT-required)

Because the renderer requires Vulkan ray-tracing pipeline extensions, the realistic floors are:
- **NVIDIA: RTX-class** (Turing / RTX 20-series, 2018+). Pre-RTX (Maxwell, Pascal, Volta) excluded — they support Vulkan 1.3 baseline but lack RT cores.
- **AMD: RDNA2** (RX 6000-series). Earlier RDNA / Polaris excluded.
- **Intel: Arc** may work depending on driver maturity; Intel iGPUs do not.

The README must state this explicitly.

## 9. Explicit Non-Goals

| # | Non-goal | Reason |
|---|---|---|
| NG-01 | Modrinth, CurseForge, or any third-party marketplace listing | GitHub-only. |
| NG-02 | Modpack-compatibility guarantee or testing against modded profiles | Compatibility asserted only against a clean Fabric profile until beta-1; never guaranteed beyond what was tested. |
| NG-03 | Linux native (`libcore.so`) | v1.1 candidate. |
| NG-04 | macOS / MoltenVK | Separate project. |
| NG-05 | HDR10 swapchain output | Driver-stability deferred. |
| NG-06 | DLSS-G or FSR3-FG frame generation | Reflex hookup is post-v1. |
| NG-07 | NRD denoiser parameter tuning | Defaults only. |
| NG-08 | PBR resource pack adapter | Post-v1. |
| NG-09 | Performance parity with the 1.21.4 upstream build | 1.20.1's chunk-builder is older. |
| NG-10 | Bundled NVIDIA DLSS binaries | NVIDIA SDK license forbids redistribution. |
| NG-11 | Auto-update, telemetry, install metrics | Not built. |
| NG-12 | Forge / NeoForge ports | Out of scope. |
| NG-13 | Marketing / launch coordination | Not a launch. |
| NG-14 | Server-side rendering | Mod is `environment: client` by design. |
| NG-15 | Configuration migration tool from upstream's 1.21.4 install | Schemas similar but not identical; users start fresh. |
| NG-16 | Runtime fallback to vanilla GL deeper than `RadianceState.RENDERER_DISABLED` | The mixin set assumes Vulkan; a true GL fallback would require a parallel mixin set. |
| NG-17 | AMD/FSR3 functional claim in v1.0 | AMD hardware not in test matrix. |
| NG-18 | Pre-RTX hardware (NVIDIA Maxwell/Pascal/Volta, AMD pre-RDNA2) | RT pipeline extensions required. |
| NG-19 | Continued MC 1.21.4 ABI support in the MCVR fork | OQ-12. The fork supports `mcVersionId = 12001` only for v1.0. |

## 10. Engineering Risk Gates

Each gate blocks a specific release. Independently verifiable.

| Gate | Description | Blocks | Cleared by |
|---|---|---|---|
| **G1** | **(Original — superseded by G1+G3-recovery, see below. Also superseded: an interim "G1-recovery" row that lived here briefly during the third pass; it has been removed to avoid clutter.)** An unmodified `core.dll` exists, was built against this Java tree's unmodified (1.21.4) JNI headers, and `System.load`s on the test rig without OS-level error. | alpha-0 | Implementation Checkpoint 0a. |
| **G1+G3-recovery** | Replaces G1 and G3. A `core.dll` exists, was built on the user's Windows machine against the current 1.20.1 Java headers (which include `handshake`/`validateAbi` declarations from 0b), and `System.load`s without OS-level error. **MCVR's C++ `RendererProxy_handshake` symbol exists, decodes the structured ordinal table per PRD §4.3.1, and returns `0` for `mcVersionId == 12001`** against the head Java commit. `RadianceState` transitions `UNINITIALIZED → BOOT_OK`. The four resource-tracker mixins begin shadowing without runtime error. The Java `runClient` reaches the main menu via vanilla GL (no renderer mixins active). `shaders/` and `modules/` MUST be packaged into the jar and extracted successfully (their absence sets `RENDERER_DISABLED` rather than `BOOT_OK`, which fails the gate). Optional natives absent (`core.lib`, Streamline DLLs) are logged WARN but do not fail the gate. | alpha-0 | Implementation Checkpoint 0c+A (Part 4). |
| **G2** | All new JNI declarations land in Java; deferred mixins are compile-quarantined (§4.5); JNI headers regenerate. | alpha-0 | Implementation Checkpoint 0b. (MCVR-side build of the new symbols folded into G1+G3-recovery, so G2 is now Java-side-only.) |
| **G3** | **(Folded into G1+G3-recovery above.)** Was originally a separate Checkpoint A gate for handshake success. After the handshake call site moved into the alpha-0 boot path, G3 cannot pass without the C++ side also passing — so G1 and G3 are reached together by Checkpoint 0c+A. | alpha-0 | Folded into G1+G3-recovery. |
| **G4** | `RadianceBufferHandle` / `RadianceBufferAdapter` / `RadianceVertexConsumer` are in place; the §4.4 ByteBuffer transport is implemented; `BuiltBuffer` and `VertexConsumer` no longer cross JNI. (`grep -r BuiltBuffer\|VertexConsumer src/main/java/com/radiance/client/proxy/` matches only the adapter file.) | alpha-2 | Implementation Checkpoint C. |
| **G5** | `radiance.accesswidener` contains zero entries whose target classes/members do not exist in 1.20.1 yarn. Each surviving mixin compiles. Deferred mixins live in `src/deferred/java/` and are not compiled. | alpha-1 | Per-AW classification + compile-quarantine in Implementation Checkpoint 0b. |
| **G6** | `RendererProxy.initRenderer(window)` (called from `MinecraftClientMixins`) returns success. The three log lines from FR-10 appear in `latest.log`. Main menu renders. | alpha-1 | Implementation Checkpoint B. |
| **G7** | Superflat terrain renders through the Vulkan path. F3 (in-world) reports a Vulkan API description. Place/break works. | alpha-2 | Implementation Checkpoint C verification. |
| **G8** | Settings written via the `O`-key menu round-trip through `.minecraft/radiance/options.properties` after a client restart. | beta-1 | Implementation Checkpoint D. |
| **G9** | DLSS DLLs, when present, are loaded; the DLSS quality option appears; rendering uses DLSS. | beta-2 | Implementation Checkpoint E (requires DLSS-capable hardware — see OQ-06). |
| **G10** | 24-hour soak test passes: no crash; `jcmd GC.heap_info` shows no monotonic growth across 4-hour snapshots. Streamline DLL redistribution licensed (or DLLs moved to user-supplied). | v1.0 | Implementation Checkpoint F. |

## 11. Test Plan

For every release, run on a **clean Fabric 1.20.1 profile** (no other mods, fresh `.minecraft/`).

### 11.1 Test environment
- Fresh Windows 10/11 x64 Minecraft Launcher install.
- Fabric Loader installed via the official Fabric installer (the version pinned in §8 — 0.15.11 for v1.0).
- Fabric API jar in `mods/`.
- Radiance jar in `mods/`.
- Tested twice: with and without DLSS DLLs in `.minecraft/radiance/`.
- For beta-1+: also test on the reference RTX hardware (OQ-02).

### 11.2 Test sequence
1. Launch client. Inspect log: `System.load` of `core.dll`, handshake return code `0`. Streamline DLL load is implicit (no per-DLL log line).
2. **Without DLSS:** confirm `DlssMissingScreen` displays on first screen. Confirm handshake still returned `0` independent of DLSS state.
3. **With DLSS:** confirm `DlssMissingScreen` does NOT display.
4. Reach the main menu.
5. (alpha-1+) Inspect `latest.log` for the three FR-10 lines (`initRenderer returned successfully`, `apiDescription set to ...`, `RadianceState transition: BOOT_OK -> RENDERER_ACTIVE`).
6. (alpha-0) Click "Singleplayer", create vanilla superflat, confirm renders **through vanilla OpenGL**. Move around.
7. (alpha-2+) Click "Singleplayer", create vanilla superflat creative world. Confirm it renders **through the Vulkan path**. **Press F3, confirm Vulkan API description in API line.** Walk 100 blocks. Place 10 blocks. Break them.
8. (beta-1+) Open settings via `O` key. Change tone mapping mode. Save. Quit to title. Re-enter. Confirm tone mapping persisted.
9. (beta-1+) Press F2. Open the resulting PNG outside the game; confirm valid image.
10. (beta-1+) Test with overworld save; nether portal; end portal.
11. (beta-1+) Run for 30 minutes of continuous play without crash.
12. (beta-2+) Confirm RT lighting visible; DLSS quality option works.
13. (v1.0) 24-hour soak. `jcmd <pid> GC.heap_info` snapshots every 4 hours; no monotonic growth.
14. Always: inspect `.minecraft/logs/latest.log`; document any WARN/ERROR in KNOWN-ISSUES.md.

## 12. Compatibility Statement

Every release's README and KNOWN-ISSUES.md must restate the following exactly:

> **Tested only against:** clean Fabric 1.20.1 profile + Fabric Loader 0.15.11 + Fabric API 0.92.x + Radiance + (optional) NVIDIA DLSS DLLs, on Windows 10/11 x64 with an NVIDIA RTX-class GPU.
>
> **Other Fabric Loader versions in the 0.15.x line may work but were not tested for this release.**
>
> **Untested with all other mods.** This includes Sodium, Iris, Optifine, Oculus, ModernFix, Lithium, Phosphor, and every other Fabric mod. Some are known-incompatible by design (Iris, Oculus, Optifine, Sodium-with-shader-features replace the same render systems Radiance replaces). Most are not validated.
>
> **AMD / FSR3:** the Java path supports FSR3 module configuration. AMD hardware was not in the test matrix for this release. AMD users should expect issues.
>
> **Pre-RTX NVIDIA, pre-RDNA2 AMD, Intel iGPUs:** unsupported. RT pipeline extensions required.
>
> **macOS, Linux:** unsupported. Will not load on macOS (no Vulkan path). Linux native is not built for this release.

## 13. Open Questions

| # | Question | Owner | Resolved by |
|---|---|---|---|
| OQ-01 | Will the user pursue C++ MCVR work themselves, or seek a collaborator? | User | End of Implementation Checkpoint 0c+A (clears G1+G3-recovery; original 0a/G1, planned 0a-recovery/G1-recovery, and Checkpoint A/G3 framings all superseded after the handshake call site landed in `RadianceClient.onInitializeClient` mid-checkpoint). See Part 4. |
| OQ-02 | Reference test rig spec (GPU model, OS version, RAM)? | User | Before alpha-1 verification. |
| OQ-03 | Streamline DLL redistribution license — verified, or DLLs moved to user-supplied? | User / legal | Before v1.0 (clears G10). |
| OQ-04 | Version-number convention for git tags (e.g., `alpha-0`, `0.1.3-alpha+mc1.20.1-alpha-0`, `1.20.1-0.1`)? | User | Before alpha-0 ships. |
| OQ-05 | Does the project want a `CONTRIBUTING.md` and accept PRs? | User | Before v1.0. |
| OQ-06 | Is the user's reference rig DLSS-capable? If not, beta-2 gate G9 needs an external tester. | User | Before beta-2. |
| OQ-07 | Is `core.lib` required at runtime? `.lib` files are typically link-time only. If MCVR's `core.dll` does not need it at runtime, drop the extraction. | User / inspect MCVR | Before v1.0. |
| OQ-08 | Fabric Loader version policy — pin to 0.15.11 for tested release validation (current decision); allow wider after testing more versions. | User | Decided: pin tested version, allow wider via documentation. |
| OQ-09 | Should the full `VideoOptionsScreenMixins` rewrite ship in v1.0, or stay deferred (essentials-only via `O` key indefinitely)? | User | Before v1.0. |
| OQ-10 | `RenderSystem.apiDescription = "Vulkan 1.4"` is hardcoded in `RendererProxy.initRenderer`. Should the FR test against the literal string, or against "any non-empty string identifying Vulkan"? Hardcoded version may be inaccurate. | User | Before alpha-1 ships. Default for now: FR tests for "string contains 'Vulkan'", not the literal "Vulkan 1.4". |
| OQ-11 | Compile-quarantine implementation — `src/deferred/java/` source root (current decision) vs. `sourceSets.main.java.exclude`. Both work; `src/deferred/java/` keeps git history clean and is more visible. Confirm before Checkpoint 0b. | User | Before Implementation Checkpoint 0b. Default: separate source root. |
| OQ-12 | Maintain MC 1.21.4 ABI in the MCVR fork? Default for v1.0: NO (12001 only). Revisit if upstreaming becomes a goal. | User | Before v1.0. |

## 14. What this PRD is not

It is not a launch plan, marketing document, modpack-compatibility statement, or commitment to any timeline. It is a contract for what each staged GitHub Release demonstrates and what the locked engineering decisions are.

---

# Part 2 — Implementation Plan

## Context

Radiance is a Fabric mod that swaps Minecraft's OpenGL renderer for a Vulkan + ray-tracing backend. The Java side (~138 files, ~60 mixins, ~152 native declarations, 18 GUI screen classes + 3 widget/helper classes) is a thin Fabric/JNI bridge to a separate C++ project, MCVR, which ships as `core.dll`. Upstream targets MC 1.21.4. This plan ports it to MC 1.20.1 Fabric, Windows-only. (Counts are pre-0b; the 0b move-to-deferred reduced the active mixin count — see Part 3 status note.)

The local checkout is PEQHUB/Radiance @ main (a fork of Minecraft-Radiance/Radiance) at `mod_version=0.1.3-alpha`.

User decisions (now locked in PRD §4):

- **Source baseline:** PEQHUB v0.1.3-alpha (PRD §4.1).
- **Native baseline:** MCVR fork with `mc/1.20.1` branch + ABI handshake (PRD §4.2 / §4.3) — staged through Checkpoints 0a / 0b / 0c. v1.0 supports `mcVersionId = 12001` only.
- **Compile-quarantine for deferred mixins:** `src/deferred/java/` parallel source root, NOT compiled (PRD §4.5).
- **Buffer abstraction:** `RadianceBufferHandle` POJO + 40-byte direct ByteBuffer JNI transport (PRD §4.4).
- **`RadianceState`:** two predicates, `isResourceTrackingEnabled()` and `isRendererActive()`, with per-injection-type guard patterns (PRD §4.7).
- **`MinecraftClientMixins` calls `initRenderer(window)`**, not `RadianceClient`.
- **`WorldRendererMixins` handling: deferred whole after 0b** (the original "locked split" plan was not viable — see PRD §4.6 amendment). Checkpoint C will write a fresh `WorldRendererCoreMixins.java` against 1.20.1 yarn from scratch (terrain/chunk responsibilities) rather than bisect the upstream file; Checkpoint D does the same for `WorldRendererSkyWeatherMixins.java` (sky/weather/cloud).
- **Platform:** Windows-only for v1.0; Linux deferred (NG-03).
- **VideoOptionsScreenMixins:** off the critical path (OQ-09).
- **MCVR access:** user's first C++ project ever. Dominant project risk; addressed by Checkpoint 0a.

Checkpoint → milestone mapping:

| Checkpoint | Ships in milestone |
|---|---|
| Checkpoint 0a (MCVR builds unmodified — proves toolchain) | gates alpha-0 (G1) |
| Checkpoint 0b (Java JNI surface + 1.20.1 yarn migration + compile-quarantine) | gates alpha-0 (G2 partial, G5) |
| Checkpoint 0c+A (MCVR handshake decoder + Java alpha-0 boot path; replaces the original 0c and A entries after `RadianceClient.performHandshake()` was wired in mid-checkpoint) | alpha-0 (G1+G3-recovery) |
| Checkpoint B (boot-path mixins; MinecraftClientMixins calls initRenderer) | alpha-1 (G6) |
| Checkpoint C (world/chunk/buffer bridge + WorldRendererCoreMixins) | alpha-2 (G4, G7) |
| Checkpoint D (entities/particles/sky/cloud + WorldRendererSkyWeatherMixins + settings essentials) | beta-1 (G8) |
| Checkpoint E (RT, tone mapping, DLSS) | beta-2 (G9) |
| Checkpoint F (hardening, soak, optional VideoOptionsScreenMixins, license) | v1.0 (G10) |

## Critical files to create or modify

**Modify:**
- `gradle.properties` — version pins
- `build.gradle` — Loom plugin version, Java target, runClient launcher. (Source-set exclusion for `src/deferred/` is **not needed** — Loom defaults pick up only `src/main/`. The deferred root is invisible by being outside the configured source set.)
- `src/main/resources/fabric.mod.json` — MC/loader depends range
- `src/main/resources/radiance.mixins.json` — `compatibilityLevel`, active `mixins`/`client` arrays plus `_deferred_until_implemented` audit array (JSON has no comments — deferred entries are removed and tracked via the underscore-prefixed sibling array per §4.5)
- `src/main/resources/radiance.accesswidener` — strip 1.21+-only entries; rewrite per §4.5
- `src/main/java/com/radiance/mixin_related/MixinPlugin.java` — replace global `ENABLED` boolean with per-mixin allowlist
- `src/main/java/com/radiance/client/constant/Constants.java` — stub 1.21+-only vertex formats; add `dumpOrdinals(): long[]`
- `src/main/java/com/radiance/client/RadianceClient.java` — handshake, `RadianceState` initialization, `BOOT_OK` transition. Does NOT call `initRenderer`.
- `src/main/java/com/radiance/client/proxy/vulkan/RendererProxy.java` — add `handshake(int, long[])` and `validateAbi(int, long[])` JNI declarations
- `src/main/java/com/radiance/client/proxy/vulkan/BufferProxy.java` — accept `RadianceBufferHandle` (via ByteBuffer), not `BuiltBuffer`
- `src/main/java/com/radiance/client/proxy/world/{ChunkProxy,EntityProxy}.java` — same
- `src/main/java/com/radiance/mixins/vulkan_render_integration/MinecraftClientMixins.java` — perform `initRenderer(window)` once Window is available; transition `BOOT_OK -> RENDERER_ACTIVE`
- All ~50 non-deferred mixins under `src/main/java/com/radiance/mixins/` — yarn renames and per-injection-type guard patterns
- Most of the 18 GUI screen classes — yarn renames; the 3 widget/helper classes need targeted fixes
- `.github/workflows/build-linux.yml` — Java toolchain 21 → 17; Linux row stays disabled or `continue-on-error` for v1.0

**Create (new files):**
- `src/main/java/com/radiance/client/RadianceState.java` — global state enum + `isResourceTrackingEnabled()` + `isRendererActive()` + helper `runIfActive(Runnable)` (PRD §4.7)
- `src/main/java/com/radiance/client/proxy/buffer/RadianceBufferHandle.java` — POJO + `toByteBuffer()` + `fromByteBuffer()` per the §4.4 layout
- `src/main/java/com/radiance/client/proxy/buffer/RadianceBufferAdapter.java` — `static RadianceBufferHandle from(BuiltBuffer)` (1.20.1 yarn `BufferBuilder.BuiltBuffer`)
- `src/main/java/com/radiance/client/proxy/buffer/RadianceVertexConsumer.java` — interface implemented by `PBRVertexConsumer`
- ~~`src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererCoreMixins.java`~~ — **revised:** written fresh in Checkpoint C (NOT split out from `WorldRendererMixins.java`; the upstream file is deferred whole, see PRD §4.6 amendment).
- ~~`src/deferred/java/com/radiance/mixins/vulkan_render_integration/WorldRendererSkyWeatherMixins.java`~~ — **revised:** written fresh in Checkpoint D (sky/weather/cloud, ditto).
- (Test) `src/test/java/com/radiance/client/proxy/buffer/RadianceBufferHandleTest.java` — round-trip test for the §4.4 ByteBuffer layout

**Move to `src/deferred/java/` in Checkpoint 0b** (per PRD §4.5, full list):
- `mixins/vulkan_render_integration/CloudRendererMixins.java`
- `mixins/vulkan_render_integration/SectionBuilderMixins.java`
- `mixins/vulkan_render_integration/BuiltBufferMixins.java`
- `mixins/vulkan_render_integration/RenderLayerMixins.java`
- `mixins/vulkan_render_integration/VideoWarningManagerWarningPatternLoaderMixins.java`
- `mixins/vulkan_render_integration/ReloadableResourceManagerImplMixins.java`
- `mixins/vanilla_resource_tracker/ReloadableTextureMixins.java`
- `mixins/vulkan_options/VideoOptionsScreenMixins.java`
- `mixins/vulkan_render_integration/WorldRendererMixins.java` — deferred whole (the originally-planned split was not viable; replacements are written fresh in Checkpoint C/D).
- Any other mixin that fails compile against 1.20.1 yarn during Checkpoint 0b — discovered iteratively. Each move is a single `git mv`.

## Checkpoint 0a — Prove MCVR can be built unmodified (gates alpha-0 / G1) — **HISTORICAL / SUPERSEDED**

> **READER WARNING (added 2026-05-11, updated fourth pass):** Checkpoint 0a never executed in this order. Checkpoint 0b shipped first; then `RadianceClient.performHandshake()` was wired in mid-checkpoint, which moved the executable scope to **Checkpoint 0c+A** (build MCVR with the §4.3.1 handshake decoder against the current Java head, complete the alpha-0 boot wiring end-to-end). That work lives in **Part 4** below. The original 0a sketch is preserved here for audit only. Do not execute it. In particular: **the original Step 5 ("`core.dll`/`core.lib` end up in `src/main/resources/`") is wrong under the current canonical layout — native artifacts go in `natives/<platform>/`** (see Part 4 W12 for the rule that overrides this).

1. Clone `https://github.com/Minecraft-Radiance/MCVR` next to this repo.
2. Install: Vulkan SDK + glslangValidator, Visual Studio 2022 build tools, latest MSVC redistributable. (Reference: `.github/workflows/build-linux.yml` lines 57–74.)
3. Run `./gradlew compileJava` against the unmodified Java tree (still 1.21.4) to generate JNI headers under `src/main/native/include/`.
4. Build MCVR: `cmake -S . -B build -DCMAKE_BUILD_TYPE=Release -DJAVA_PROJECT_ROOT_DIR="<this repo>" -DUSE_AMD=ON -DMCVR_ENABLE_NRD=ON -DMCVR_ENABLE_FFX_UPSCALER=ON`, then `cmake --build build --config Release --parallel`, then `cmake --install build --config Release`.
5. ~~Verify: `core.dll` + `core.lib` (and Streamline DLLs) end up in `src/main/resources/`.~~ **Superseded:** native artifacts now belong in `natives/<platform>/` (Part 4 W12). Streamline DLLs remain under `src/main/resources/` for historical reasons but are not the rule for new native build outputs.

(End of historical sketch. The executable replacement is **Part 4 — Checkpoint 0c+A**.)

## Checkpoint 0b — Java JNI surface + 1.20.1 yarn migration + compile-quarantine (gates alpha-0 / G2, G5) — **HISTORICAL / SHIPPED**

> **READER WARNING (added 2026-05-11):** Checkpoint 0b SHIPPED as commit `a456701`. The narrative below is the original three-strand sketch and is preserved for audit. The authoritative record of what was actually executed is **Part 3** (the bite-sized plan and its post-merge status note); the deviations from the sketch (Loom version held at 1.11, `WorldRendererMixins` deferred WHOLE not split, ~67-not-14 deferred files, JSON `_deferred_until_implemented` audit array) are documented there. Do not re-execute this section.

After 0a. **Java-only checkpoint.** Three orthogonal strands of work; commit each separately for bisectability.

### Strand 1 — Compile-quarantine (PRD §4.5)
- `git mv` each file in the deferred set (listed above) from `src/main/java/...` to `src/deferred/java/...` (preserving package paths).
- Comment out the corresponding entries in `src/main/resources/radiance.mixins.json` (don't delete — the comments document what's deferred and which checkpoint owns them).
- Split `src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java` into `WorldRendererCoreMixins.java` (terrain/chunk injects, stays in `src/main/java/`) and `WorldRendererSkyWeatherMixins.java` (sky/weather/cloud injects, moves to `src/deferred/java/`).
- Verify Loom does not pick up `src/deferred/`: `./gradlew compileJava --info` should show only `src/main/java/` in the source set listing.

### Strand 2 — 1.20.1 yarn migration

Migration table:

| File / property | From | To |
|---|---|---|
| `gradle.properties` `minecraft_version` | `1.21.4` | `1.20.1` |
| `gradle.properties` `yarn_mappings` | `1.21.4+build.8` | `1.20.1+build.10` |
| `gradle.properties` `loader_version` | `0.18.3` | `0.15.11` |
| `gradle.properties` `loom_version` | `1.14-SNAPSHOT` (cosmetic) | `1.6-SNAPSHOT` |
| `gradle.properties` `fabric_version` | `0.119.4+1.21.4` | `0.92.6+1.20.1` |
| `build.gradle` plugin id `'fabric-loom'` line 2 | `'1.11-SNAPSHOT'` | `'1.6-SNAPSHOT'` (authoritative) |
| `build.gradle` `targetJavaVersion` line 86 | `21` | `17` |
| `build.gradle` `runClient` `javaLauncher` block | force JDK 21 | force JDK 17 |
| `radiance.mixins.json` `compatibilityLevel` | `JAVA_21` | `JAVA_17` |

Stub `Constants.VertexFormats` for 1.20.1 yarn: comment out `POSITION_TEXTURE_LIGHT_COLOR` (ordinal 10) and `POSITION_TEXTURE_COLOR_NORMAL` (ordinal 11). **Do NOT renumber** (PRD §4.4 — leave the gap).

Strip from `radiance.accesswidener` (PRD §4.5):
- lines 64–65 (SkyRendering SUN_TEXTURE/MOON_PHASES_TEXTURE) — re-add in Checkpoint D.
- line 70 (CloudRenderer.ViewMode) — drop entirely.
- line 43 (VideoWarningManager.WarningPatternLoader) — drop; mixin deferred.
- lines 66–67 (OptionListWidget.WidgetEntry) — re-add in Checkpoint D.
- line 72 (SimpleOption.CyclingCallbacks) — re-add in Checkpoint D.

Walk every remaining (non-deferred) mixin file and apply yarn renames as needed for 1.20.1.

### Strand 3 — JNI surface and Java-side state

- Add JNI declarations on `RendererProxy`:
  - `public static native int handshake(int mcVersionId, long[] javaOrdinals);`
  - `public static native int validateAbi(int mcVersionId, long[] javaOrdinals);`
- Add `Constants.dumpOrdinals(): long[]` — pure Java; concatenates the ordinal tables in a documented order (vertex format ordinals first, then draw mode, index type, geometry type, RT flags). The order is part of the JNI contract.
- Create `RadianceState.java`:
  ```java
  public enum RadianceState {
      UNINITIALIZED, INIT_FAILED, BOOT_OK, RENDERER_ACTIVE, RENDERER_DISABLED;
      private static volatile RadianceState current = UNINITIALIZED;
      public static synchronized void set(RadianceState s) { current = s; }
      public static RadianceState get() { return current; }
      public static boolean isResourceTrackingEnabled() {
          return current == BOOT_OK || current == RENDERER_ACTIVE;
      }
      public static boolean isRendererActive() { return current == RENDERER_ACTIVE; }
      public static void runIfActive(Runnable r) { if (isRendererActive()) r.run(); }
  }
  ```
- Create the §4.4 buffer abstraction:
  - `RadianceBufferHandle.java` (POJO with the 8 fields from §4.4).
  - `RadianceBufferHandle.toByteBuffer(): ByteBuffer` allocates a 40-byte direct ByteBuffer (LittleEndian) and writes per the §4.4 layout.
  - `RadianceBufferHandle.fromByteBuffer(ByteBuffer): RadianceBufferHandle` reads the same layout (used for tests).
  - `RadianceBufferAdapter.from(BuiltBuffer)` — implementation against 1.20.1 `BufferBuilder.BuiltBuffer`.
  - `RadianceVertexConsumer` interface.
  - `RadianceBufferHandleTest` — round-trip test.

`MixinPlugin.java`: replace `public static boolean ENABLED = true` with `public static final Set<String> ENABLED_MIXINS = Set.of(…)`; have `shouldApplyMixin` return `ENABLED_MIXINS.contains(mixinClassName)`. Seed with **only**:
- `vanilla_resource_tracker.NamespaceResourceManagerMixins`
- `vanilla_resource_tracker.TextureManagerMixins`
- `vanilla_resource_tracker.AbstractTextureMixins`
- `vanilla_resource_tracker.NativeImageMixins`

These four use `RadianceState.isResourceTrackingEnabled()` (not `isRendererActive()`) so they begin shadowing as soon as the native loads. They must call vanilla through (not cancel) when `isResourceTrackingEnabled()` is false (i.e., before handshake or after `INIT_FAILED`).

Verification: `./gradlew compileJava` succeeds. `./gradlew test` passes (the `RadianceBufferHandleTest` round-trip). Do **not** run `./gradlew runClient` here — it will UnsatisfiedLinkError on the first JNI call. Continue to 0c.

## Checkpoint 0c — MCVR `mc/1.20.1` branch, 12001 only (gates alpha-0 / G2)

After 0b. Native-only work.

1. Create branch `mc/1.20.1` on the local MCVR fork.
2. Implement `RendererProxy_handshake(JNIEnv*, jclass, jint mcVersionId, jlongArray javaOrdinals)`:
   - For v1.0, accept only `mcVersionId == 12001`. Reject (return non-zero with code) any other value.
   - Decode `javaOrdinals` into a local table.
   - Compare against the embedded MC-1.20.1 table.
   - Return `0` for match, non-zero status code identifying the first mismatched table.
3. Implement `RendererProxy_validateAbi(JNIEnv*, jclass, jint mcVersionId, jlongArray javaOrdinals)`: identical comparison logic. Re-callable at any time. No state.
4. Update C++ ordinal tables for 12001. Drop any 1.21.4 / 12104 conditional branches unless OQ-12 says to keep them.
5. Replace any C++ code that consumed MC's `BuiltBuffer` shape directly with reads of the §4.4 ByteBuffer layout (40 bytes, native byte order = LittleEndian on x86_64). Document the offset constants in MCVR's source.
6. Rebuild: `cmake --build build --config Release --parallel`, `cmake --install build --config Release`.

Verification: the new `core.dll` is in `natives/windows/` (canonical native-artifact location per Part 4 W12 — NOT `src/main/resources/`, which is reserved for static Java resources). mtime newer than 0a's. If MCVR's `cmake --install` rule still writes to `src/main/resources/`, fix that rule in MCVR (or `move` the output as documented in Part 4 W12); do not perpetuate the legacy layout.

## Checkpoint A — Java alpha-0 (gates alpha-0 / G3)

Wire `RadianceClient.onInitializeClient`:

1. Extract native files (existing logic).
2. `System.load(core.dll absolute path)`. Log the path and any OS error code on failure.
3. Build the Java ordinals via `Constants.dumpOrdinals()`.
4. Call `RendererProxy.handshake(12001, ordinals)`. On non-zero, set `RadianceState.INIT_FAILED`, log fatal with the mismatch code, do **NOT** crash.
5. On `0`, set `RadianceState.BOOT_OK`. The four resource-tracker mixins now begin shadowing (they check `isResourceTrackingEnabled()`).
6. **Do NOT call `initRenderer` here.** That belongs to `MinecraftClientMixins`, which is not yet enabled in alpha-0.
7. The DLSS-missing flow (existing logic) is unaffected — it runs independently.

The four resource-tracker mixins must use `isResourceTrackingEnabled()`, not `isRendererActive()`. They call vanilla through when the predicate is false.

Verification (alpha-0 G3):
- `./gradlew compileJava` succeeds against 1.20.1 yarn (with the §4.5 deferred set quarantined).
- `./gradlew runClient` reaches the main menu.
- Native files extracted; handshake returns `0`.
- **Vanilla superflat world load works** (no Vulkan rendering — vanilla GL).
- DLSS DLLs absent → `DlssMissingScreen` displays cleanly.
- DLSS DLLs present → no `DlssMissingScreen`.

Fail-open: if any of the four resource-tracker mixins crashes, disable it from the allowlist. Alpha-0 still satisfies G3 with zero mixins.

## Checkpoint B — Boot-path Vulkan init (gates alpha-1 / G6)

**HIGH RISK, not "yarn-rename-only."** Boot-path mixins touch render init, framebuffer ownership, render loop.

Add to `MixinPlugin.ENABLED_MIXINS`, validating each at runtime before adding the next:

1. `vulkan_render_integration.GLXMixins`
2. `vulkan_render_integration.WindowMixins`
3. `vulkan_render_integration.MinecraftClientMixins` — **this is the mixin that calls `RendererProxy.initRenderer(window)`** (FR-09). The hook is on a method where `Window` is available (existing radiance code already does this — preserve the inject point). On success, transition `RadianceState.set(RENDERER_ACTIVE)`. On failure (Vulkan-feature gate trips or `initRenderer` throws), log ERROR and `RadianceState.set(RENDERER_DISABLED)`.
4. `vulkan_render_integration.GlStateManagerMixins` (28 injects on `com.mojang.blaze3d.platform.GlStateManager` — verify each redirect target for 1.20.1).
5. `vulkan_render_integration.RenderSystemMixins`
6. `vulkan_render_integration.BufferRendererMixins`
7. `vulkan_render_integration.GameRendererMixins`

**Apply per-injection-type guards (PRD §4.7).** Walk each enabled mixin and rewrite its bodies according to the guard table. The patterns:

- `@Inject(cancellable=true)`:
  ```java
  if (!RadianceState.isRendererActive()) return;  // do not cancel; vanilla completes
  // Radiance behavior
  ci.cancel();
  ```
- `@Redirect`:
  ```java
  if (!RadianceState.isRendererActive()) {
      return target.invokeOriginal(args);  // call the original target manually
  }
  // Radiance redirect logic
  ```
- `@ModifyArg` / `@ModifyVariable`:
  ```java
  if (!RadianceState.isRendererActive()) return original;
  // Radiance-modified value
  ```
- `@Overwrite`: avoid; if necessary, hand-copy the vanilla code into an `if (!isRendererActive())` branch.

Verification (alpha-1 G6, log-based — F3 is alpha-2):
- `./gradlew runClient` reaches main menu.
- `latest.log` contains:
  - `[radiance] RendererProxy.initRenderer returned successfully`
  - `[radiance] RenderSystem.apiDescription set to '<value>'`
  - `[radiance] RadianceState transition: BOOT_OK -> RENDERER_ACTIVE`
- Main menu renders.
- 5 minutes at the main menu without crash.
- KNOWN-ISSUES.md notes world load is not yet supported.

Fail-open: if any of the 7 mixins crashes, disable it. Alpha-1 ships with the largest stable subset that keeps the main menu rendering.

Risk: HIGH. Allocate 2 weeks. Boot-path mixins were the original Tier-3 framing's worst lie.

## Checkpoint C — World/chunk/buffer bridge (gates alpha-2 / G4, G7)

The single largest milestone. Allocate 4 weeks.

Implement the buffer abstraction for real (PRD §4.4):
- `RadianceBufferHandle` already created in 0b — ensure `toByteBuffer()` is wired.
- `RadianceBufferAdapter.from(BuiltBuffer)` — implement against 1.20.1's `BufferBuilder.BuiltBuffer.getParameters()`.
- `RadianceVertexConsumer` — wire `PBRVertexConsumer` (in `src/main/java/com/radiance/client/vertex/`) to implement it.
- Update `BufferProxy.createAndUploadVertexIndexBuffer` signature: accepts `ByteBuffer` (the result of `RadianceBufferHandle.toByteBuffer()`), not `BuiltBuffer`. Update all callers. (The native-side reads via `MemoryUtil.memAddress`.)
- Update `ChunkProxy.rebuildSingle` similarly.
- Update `EntityProxy.queueBuild` similarly.

Move from `src/deferred/java/` back to `src/main/java/` (via `git mv`):
- `mixins/vulkan_render_integration/BuiltBufferMixins.java` — port for 1.20.1 `BufferBuilder.BuiltBuffer`; use `RadianceBufferAdapter`.
- `mixins/vulkan_render_integration/RenderLayerMixins.java` — re-target the LIGHTNING layer for 1.20.1 `RenderLayer.of(...)`; AW `getId()` widener already in place.
- `mixins/vulkan_render_integration/SectionBuilderMixins.java` — re-target at `ChunkBuilder$BuiltChunk$RebuildTask` (1.20.1 equivalent; AW line 50 already exposes it).

**Write a fresh `WorldRendererCoreMixins.java` from scratch** at `src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererCoreMixins.java`. The 0b split did not happen — the upstream `WorldRendererMixins.java` is deferred WHOLE (see PRD §4.6 amendment). Sourcing strategy: read 1.20.1 yarn `WorldRenderer` directly, identify the inject points needed for terrain/chunk responsibilities (chunk render dispatch, framebuffer write, terrain submission), and write the mixin against those — do NOT bisect the upstream 1.21.4 file. Then add the new class to `radiance.mixins.json` and `MixinPlugin.ENABLED_MIXINS`.

Add to allowlist:
- `vulkan_render_integration.WorldRendererCoreMixins`
- `vulkan_render_integration.BuiltBufferMixins`
- `vulkan_render_integration.RenderLayerMixins`
- `vulkan_render_integration.ChunkBuilderMixins`
- `vulkan_render_integration.ChunkBuilderBuiltChunkMixins`
- `vulkan_render_integration.BuiltChunkStorageMixins`
- `vulkan_render_integration.ClientChunkManagerMixins`
- `vulkan_render_integration.SectionBuilderMixins`

Re-add the AW entries needed by these mixins (PRD §4.5).

Verify `Constants.GeometryTypes.getGeometryType` works against 1.20.1's `RenderPhase` constants. All transparency constants exist in 1.20.1; `multiPhase.phases.transparency` field path matches AW (line 56).

Apply per-injection-type guards (Checkpoint B pattern).

Verification (alpha-2 G4 + G7):
- `grep -r 'BuiltBuffer\|VertexConsumer' src/main/java/com/radiance/client/proxy/` matches only the adapter file. Clears G4.
- Load a vanilla superflat creative world. Terrain renders through Vulkan. Walk 100 blocks. Place + break. 5 minutes without crash.
- F3 (in-world) shows the Vulkan API description. Clears G7.

If a black screen happens: re-call `RendererProxy.validateAbi(12001, Constants.dumpOrdinals())`, log the result; check `Constants.dumpOrdinals()` against MCVR's expectation. JNI ABI bug, not Java mixin bug. Do not assume yarn-mapping issue without ruling out ordinals first.

Fail-open: bisect the mixin set. Terrain rendering with translucent-water glitches is a beta-1 KNOWN-ISSUE, not an alpha-2 blocker.

Risk: HIGHEST. The project lives or dies here.

## Checkpoint D — Entities, particles, sky, clouds, settings essentials (gates beta-1 / G8)

Move from `src/deferred/java/` back to `src/main/java/` (rewriting against 1.20.1 yarn during the move):
- `mixins/vulkan_render_integration/CloudRendererMixins.java` — REWRITE: 1.20.1 has no `CloudRenderer`; retarget at `WorldRenderer.renderClouds(MatrixStack, Matrix4f, float, double, double, double)` (verify exact 1.20.1 yarn signature).

**Write a fresh `WorldRendererSkyWeatherMixins.java` from scratch** at `src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererSkyWeatherMixins.java`. The 0b split did not happen and there is no SkyWeather file in `src/deferred/java/` — only the whole `WorldRendererMixins.java` (see PRD §4.6 amendment). Sourcing strategy: read 1.20.1 yarn `WorldRenderer` directly, identify the sky/weather/cloud inject points (`renderSky(MatrixStack, Matrix4f, float, Camera, boolean, Runnable)`, weather/precipitation, the `WorldRenderer.SUN`/`MOON_PHASES` `Identifier` constants), and write the mixin against those. Re-add the corresponding AW entries dropped in 0b (`WorldRenderer.SUN`, `WorldRenderer.MOON_PHASES`, plus any `RenderPhase`/transparency state needed by the new injects). 1.20.1 has no `SkyRendering` class — do NOT port references to it.

Add to allowlist:
- `vulkan_render_integration.{EntityRenderDispatcher,ItemRenderer,HeldItemRenderer,BlockColors,BlockModelRenderer,FluidRenderer,LightmapTextureManager}Mixins`
- `vulkan_render_integration.{Particle,ParticleManager,BillboardParticle,LightningEntityRenderer,BannerBlockEntityRenderer}Mixins`
- `vulkan_render_integration.{RenderPhase,RenderPhaseLightmap,RenderPhaseTarget}Mixins`
- `vulkan_render_integration.{Screen,DrawContext,ScreenshotRecorder}Mixins` (verify each `DrawContext` redirect target exists in 1.20.1's narrower surface)
- `vulkan_render_integration.CloudRendererMixins`
- `vulkan_render_integration.WorldRendererSkyWeatherMixins`

Optionally move out of deferred and re-enable:
- `mixins/vulkan_render_integration/ReloadableResourceManagerImplMixins.java` — `registerReloader(ResourceReloader)` exists in 1.20.1 with same name; likely a one-line yarn fix.
- `mixins/vulkan_render_integration/VideoWarningManagerWarningPatternLoaderMixins.java` — verify 1.20.1 `WarningPatternLoader.buildWarnings()`. If awkward, leave deferred (cosmetic).

Apply per-injection-type guards.

Wire essentials-only `RadianceSettingsScreen`:
- Port the screen via yarn renames.
- Port `vulkan_options.GameOptionsScreenMixins` (low risk — only shadows fields).
- Port `KeyInputHandler.java` — `KeyBindingHelper.registerKeyBinding(...)` exists in Fabric API 0.92.6+1.20.1. Keybind `O` (GLFW_KEY_O — confirmed; README narrative of "K key" was wrong).
- Port the small set of sub-screens needed for essentials (tone mapping, exposure). Defer the long-tail sub-screens (PsychoV, Emissive Block, Environmental, etc.) to v1.0.
- Port `Options.readOptions` / `Options.overwriteConfig`; verify `pipeline.yaml` round-trips through `Pipeline.savePipeline` / `Pipeline.loadPipeline`.

Do NOT enable `vulkan_options.VideoOptionsScreenMixins`. It stays deferred per OQ-09.

Verification (beta-1 G8): real overworld save loads; nether and end render; 5+ entity types render; particles render; F2 produces valid PNG; `O` key opens essentials screen; tone mapping setting persists across restart; 30 minutes of play without crash.

Fail-open: drop individual mixins from the allowlist; document.

Risk: medium.

## Checkpoint E — RT, tone mapping, DLSS (gates beta-2 / G9)

Wire the RT pipeline:
- Verify the seven YAML modules in `src/main/resources/modules/` parse via `client/pipeline/config/`.
- `Pipeline.assembleDefault()` runs the RT → tone mapping → post-render path.
- HDR10 format-upgrade branch in `Pipeline.build` (lines 173–198) stays gated by `Options.hdrEnabled && Options.isHdrSupported()` — leave HDR off (NG-05).

Wire DLSS:
- `DlssMissingScreen` already triggers on missing DLLs (Checkpoint A).
- DLSS DLLs are loaded by MCVR via Windows DLL search order from `.minecraft/radiance/`.
- The DLSS quality option in `Options` becomes available; `nativeSetDlssQuality(...)` is wired.

Add the RT/tone-mapping sub-screens to the `O`-key menu (Exposure, Sun, Moon, Cloud, Sky, Water, Area Light, Post-processing).

Verification (beta-2 G9): RT lighting visible; tone mapping mode selectable; DLSS works on DLSS-capable hardware (OQ-06); pipeline YAML round-trips; nether and end RT-render. 30+ minutes of play.

Fail-open: ship beta-2 with DLSS hard-disabled if unstable. RT + tone mapping alone still demonstrates the pipeline.

Risk: medium.

## Checkpoint F — Hardening, soak, license, GitHub Release (gates v1.0 / G10)

24-hour soak test on the reference rig. `jcmd <pid> GC.heap_info` snapshots every 4 hours. No crash; heap not monotonically growing.

Optional: full `VideoOptionsScreenMixins` rewrite (OQ-09). If shipping it, ~10 days; move from `src/deferred/java/` and rewrite for 1.20.1 SimpleOption shapes. Otherwise, document that vanilla Video Options stays vanilla; settings live behind the `O` key.

Optional: long-tail sub-screens (PsychoV, Emissive Block, Environmental, Camera Controls, Light Type Detail). Each needs yarn renames. As many as time permits; the rest get a placeholder note.

License verification (G10):
- Streamline DLLs: confirm NVIDIA Streamline License permits binary redistribution. If not, move to user-supplied (update `RadianceClient.onInitializeClient` and README).
- OQ-07: confirm whether `core.lib` is needed at runtime. If not, drop the extraction.

Author release artifacts (PRD §3):
- `README.md` — install instructions, RTX-class GPU requirements, MSVC redistributable note, Compatibility Statement (verbatim from PRD §12).
- `KNOWN-ISSUES.md` — every observed defect, severity-tagged. The "native segfaults terminate the JVM" notice from PRD §4.7. Any deferred sub-screens.
- `CRASH-REPORTING.md` — capturing `latest.log`, `hs_err_pid*.log`, Werfault `.dmp`.
- SHA-256 sum.
- Signed git tag.

The `buildAllPlatforms` task in `build.gradle` lines 120–140 hardcodes `cmd /c gradlew.bat`. For v1.0 (Windows-only) this is fine; v1.1 should add OS detection.

Verification: end-user clones the release, drops jar in `.minecraft/mods` next to Fabric API, launches, plays the full PRD §11.2 sequence successfully.

## Top 5 implementation risks

1. **MCVR is a C++ project and the user has no C++ experience.** Dominant risk. Checkpoint 0c+A must clear before any renderer milestone can honestly ship.
2. **JNI ordinal drift.** Java owns the table (PRD §4.3). Handshake + `validateAbi` are the contract. Black-screen bugs in alpha-2+ should be diagnosed as ordinal mismatch first.
3. **Buffer abstraction landing.** PRD §4.4 with the locked 40-byte ByteBuffer transport. The `RadianceBufferHandle` triad is in Checkpoint C and gates G4. Skip → version-fragile code.
4. **Boot-path mixins are NOT low-risk yarn renames.** `WindowMixins`, `MinecraftClientMixins`, `RenderSystemMixins`, `GameRendererMixins`, `BufferRendererMixins` touch render init, framebuffer, render loop. 2 weeks for Checkpoint B. Fail-open per-mixin.
5. **Compile-quarantine drift.** `MixinPlugin.ENABLED_MIXINS` does NOT prevent compile breakage. The `src/deferred/java/` strategy is the locked answer (PRD §4.5). Do not delete deferred files; do not list them in `radiance.mixins.json` until they're back in `src/main/java/`.

## Verification (full sequence, before v1.0)

PRD §11.2 is canonical; this is the dev's quick reference.

1. Drop the jar + Fabric API 0.92.6+1.20.1 in `.minecraft/mods`.
2. Launch with bundled Java 17. Confirm log: `RadianceClient` init, `System.load` of `core.dll`, handshake return code `0`.
3. (alpha-1+) Inspect log for `initRenderer returned successfully`, `apiDescription set to ...`, `RadianceState transition: BOOT_OK -> RENDERER_ACTIVE`.
4. Create a vanilla superflat creative world. Confirm renders without black-screen. (alpha-2+) F3 shows Vulkan API description.
5. Press `O` — `RadianceSettingsScreen` opens. Open every shipped sub-screen.
6. Esc → Options → Video Settings — Radiance options present (only if VideoOptions rewrite shipped per OQ-09; otherwise vanilla Video Settings).
7. Switch to overworld save — terrain, water, sky, clouds, entities, particles, weather render. RT lighting visible.
8. F3+T resource reload — does not crash.
9. F2 screenshot — produces valid PNG.
10. Quit and relaunch — `pipeline.yaml` round-trips, options persist, DLSS state remembered.
11. Drop NVIDIA DLSS DLLs into `.minecraft/radiance/`, relaunch — `DlssMissingScreen` does not appear; DLSS option becomes available.
12. v1.0 only: 24-hour soak with `jcmd` heap snapshots.

If any step fails, identify the checkpoint that owns the regression and fix before the corresponding milestone re-ships.

---

# Part 3 — Bite-Sized Execution Plan: Checkpoint 0b (Java) — HISTORICAL

> **READER WARNING (added 2026-05-11):** Part 3 below is the original bite-sized plan that was executed for Checkpoint 0b. It is preserved verbatim for audit (and to make plan deviations traceable), **but it is NOT active instructions.** Several specifics did not survive execution — Loom version, the `WorldRendererMixins` split, the 14-vs-67-deferred-files count, the `Constants.PBR_TRIANGLE` retention, the AW strip set. The authoritative summary of what actually shipped is the "Part 3 — Status Note: Checkpoint 0b SHIPPED" block immediately after Task 15. **For executable next-step work, jump to Part 4.**


> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the Java tree to 1.20.1 yarn, create the `RadianceState` + buffer abstraction scaffolding behind ByteBuffer JNI transport, and compile-quarantine 1.21+-only mixins to `src/deferred/java/` so that `./gradlew compileJava` and `./gradlew test` both succeed. No client launch in this plan — that requires MCVR rebuild (Checkpoint 0c) and Java alpha-0 wiring (Checkpoint A).

**Architecture:** Three orthogonal strands committed separately for bisectability — (1) Yarn/Loom/Java migration; (2) compile-quarantine of mixins whose imports do not exist in 1.20.1 yarn; (3) new Java classes (`RadianceState`, `RadianceBufferHandle`, `RadianceBufferAdapter`, `RadianceVertexConsumer`, `Constants.dumpOrdinals`) with JUnit 5 round-trip tests. JNI declarations land too but no native binding exists yet (resolved in Checkpoint 0c).

**Tech Stack:** Fabric Loom 1.6-SNAPSHOT, Yarn 1.20.1+build.10, Fabric Loader 0.15.11, Fabric API 0.92.6+1.20.1, Java 17, Mixin compatibility level JAVA_17, JUnit 5 (newly added), LWJGL MemoryUtil for direct ByteBuffers.

**Repository:** `/Users/lavin/Projects/Radiance Backport for 1.20.1`. All paths in this plan are relative to that root.

**Prerequisite:** Implementation Checkpoint 0a complete — `core.dll` built against the unmodified 1.21.4 Java tree, MCVR clone present, Vulkan SDK + VS 2022 build tools installed (PRD §4.2).

---

### Task 1: Add JUnit 5 dependencies and a test source set to build.gradle

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: Add JUnit dependencies**

Edit `build.gradle`. Add the following lines inside the existing `dependencies { ... }` block, immediately after `include "org.yaml:snakeyaml:2.5"`:

```groovy
    testImplementation platform("org.junit:junit-bom:5.10.2")
    testImplementation "org.junit.jupiter:junit-jupiter"
    testRuntimeOnly "org.junit.platform:junit-platform-launcher"
```

Add the following block at the top level of `build.gradle` (anywhere after `dependencies { ... }`):

```groovy
test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Verify dependencies resolve**

Run: `./gradlew dependencies --configuration testCompileClasspath`
Expected: output lists `org.junit.jupiter:junit-jupiter:5.10.2`. No errors.

- [ ] **Step 3: Create the test source root**

Run: `mkdir -p src/test/java/com/radiance/client/proxy/buffer`
Run: `mkdir -p src/test/java/com/radiance/client`
Expected: directories created without error.

- [ ] **Step 4: Verify `gradle test` runs (with no tests yet)**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL with `> Task :test NO-SOURCE` (because no test files exist yet).

- [ ] **Step 5: Commit**

```bash
git add build.gradle src/test
git commit -m "build: add JUnit 5 test infrastructure"
```

---

### Task 2: Migrate gradle.properties to MC 1.20.1

**Files:**
- Modify: `gradle.properties`

- [ ] **Step 1: Update gradle.properties**

Replace the existing 1.21.4 lines with the 1.20.1 pins. The full file should look like:

```properties
# Done to increase the memory available to gradle.
org.gradle.jvmargs=-Xmx1G
org.gradle.daemon=false
# Fabric Properties
# check these on https://modmuss50.me/fabric.html
minecraft_version=1.20.1
yarn_mappings=1.20.1+build.10
loader_version=0.15.11
loom_version=1.6-SNAPSHOT
# Mod Properties
mod_version=0.1.3-alpha
maven_group=com.radiance
archives_base_name=Radiance
# Dependencies
# check this on https://modmuss50.me/fabric.html
fabric_version=0.92.6+1.20.1
```

- [ ] **Step 2: Verify (build is expected to fail at this point — gradle properties alone aren't enough)**

Run: `./gradlew compileJava`
Expected: FAIL. Errors mention 1.21+-only classes (`SectionBuilder`, `CloudRenderer`, `SkyRendering`, etc.) — these are the deferred mixins to be quarantined in Task 5. Also expect Loom plugin version mismatch errors.

- [ ] **Step 3: Commit**

```bash
git add gradle.properties
git commit -m "build: pin gradle properties to MC 1.20.1 / Fabric API 0.92.6 / Loader 0.15.11"
```

---

### Task 3: Update build.gradle for Loom 1.6 + Java 17

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: Update the loom plugin id and Java target**

In `build.gradle`:

- Change line 2 from `id 'fabric-loom' version '1.11-SNAPSHOT'` to `id 'fabric-loom' version '1.6-SNAPSHOT'`.
- Change line 86 (or wherever `def targetJavaVersion = 21` lives) from `def targetJavaVersion = 21` to `def targetJavaVersion = 17`.
- In the `tasks.named("runClient", JavaExec) { ... }` block (around lines 22–29), replace `JavaLanguageVersion.of(21)` with `JavaLanguageVersion.of(17)` and update the surrounding comment from `Java 21` to `Java 17`.

- [ ] **Step 2: Verify gradle accepts the new loom version**

Run: `./gradlew --version`
Expected: prints Gradle 8.14.1 with no plugin-resolution errors.

Run: `./gradlew help`
Expected: BUILD SUCCESSFUL. The Loom plugin resolves.

- [ ] **Step 3: Commit**

```bash
git add build.gradle
git commit -m "build: downgrade Loom to 1.6, target Java 17"
```

---

### Task 4: Update fabric.mod.json and radiance.mixins.json for 1.20.1

**Files:**
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `src/main/resources/radiance.mixins.json`

- [ ] **Step 1: Verify fabric.mod.json**

Open `src/main/resources/fabric.mod.json`. The `depends` block already uses `${minecraft_version}` interpolation, which now resolves to `1.20.1` from gradle.properties. No edit required, but confirm the file looks like:

```json
  "depends": {
    "fabricloader": ">=${loader_version}",
    "fabric": "*",
    "minecraft": "${minecraft_version}"
  },
```

- [ ] **Step 2: Update radiance.mixins.json compatibilityLevel**

Edit `src/main/resources/radiance.mixins.json`. Change `"compatibilityLevel": "JAVA_21"` to `"compatibilityLevel": "JAVA_17"`.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/radiance.mixins.json
git commit -m "build: lower mixin compatibilityLevel to JAVA_17"
```

---

### Task 5: Strip 1.21+-only access widener entries

**Files:**
- Modify: `src/main/resources/radiance.accesswidener`

- [ ] **Step 1: Remove the AW entries that reference 1.21+-only classes**

Edit `src/main/resources/radiance.accesswidener`. **Delete the following lines** (they reference classes that do not exist in 1.20.1; depending mixins are deferred in Task 6 and re-added in later checkpoints):

```
accessible field net/minecraft/client/render/SkyRendering SUN_TEXTURE Lnet/minecraft/util/Identifier;
accessible field net/minecraft/client/render/SkyRendering MOON_PHASES_TEXTURE Lnet/minecraft/util/Identifier;
```

```
accessible class net/minecraft/client/render/CloudRenderer$ViewMode
```

```
accessible class net/minecraft/client/resource/VideoWarningManager$WarningPatternLoader
```

```
accessible class net/minecraft/client/gui/widget/OptionListWidget$WidgetEntry
accessible method net/minecraft/client/gui/widget/OptionListWidget$WidgetEntry <init> (Ljava/util/List;Lnet/minecraft/client/gui/screen/Screen;)V
```

```
accessible class net/minecraft/client/option/SimpleOption$CyclingCallbacks
```

(`OptionListWidget$WidgetEntry` and `SimpleOption$CyclingCallbacks` will be re-added in Checkpoint D when their dependent screens get ported. `SkyRendering` constants will be re-added in Checkpoint D as `WorldRenderer.SUN`/`MOON_PHASES`. The other two are dropped permanently.)

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/radiance.accesswidener
git commit -m "build: strip 1.21+-only access widener entries"
```

---

### Task 6: Create the deferred source root and quarantine 1.21+-only mixins

**Files:**
- Create: `src/deferred/java/` (new source root, NOT in any source set)
- Move: 8 mixin source files from `src/main/java/com/radiance/mixins/...` to `src/deferred/java/com/radiance/mixins/...`
- Modify: `src/main/resources/radiance.mixins.json` (comment out moved mixins)

- [ ] **Step 1: Create the deferred source root mirror structure**

```bash
mkdir -p src/deferred/java/com/radiance/mixins/vulkan_render_integration
mkdir -p src/deferred/java/com/radiance/mixins/vanilla_resource_tracker
mkdir -p src/deferred/java/com/radiance/mixins/vulkan_options
```

- [ ] **Step 2: Move the 1.21+-only mixin files to deferred**

Run each of these commands:

```bash
git mv src/main/java/com/radiance/mixins/vulkan_render_integration/CloudRendererMixins.java \
       src/deferred/java/com/radiance/mixins/vulkan_render_integration/CloudRendererMixins.java

git mv src/main/java/com/radiance/mixins/vulkan_render_integration/SectionBuilderMixins.java \
       src/deferred/java/com/radiance/mixins/vulkan_render_integration/SectionBuilderMixins.java

git mv src/main/java/com/radiance/mixins/vulkan_render_integration/BuiltBufferMixins.java \
       src/deferred/java/com/radiance/mixins/vulkan_render_integration/BuiltBufferMixins.java

git mv src/main/java/com/radiance/mixins/vulkan_render_integration/RenderLayerMixins.java \
       src/deferred/java/com/radiance/mixins/vulkan_render_integration/RenderLayerMixins.java

git mv src/main/java/com/radiance/mixins/vulkan_render_integration/VideoWarningManagerWarningPatternLoaderMixins.java \
       src/deferred/java/com/radiance/mixins/vulkan_render_integration/VideoWarningManagerWarningPatternLoaderMixins.java

git mv src/main/java/com/radiance/mixins/vulkan_render_integration/ReloadableResourceManagerImplMixins.java \
       src/deferred/java/com/radiance/mixins/vulkan_render_integration/ReloadableResourceManagerImplMixins.java

git mv src/main/java/com/radiance/mixins/vanilla_resource_tracker/ReloadableTextureMixins.java \
       src/deferred/java/com/radiance/mixins/vanilla_resource_tracker/ReloadableTextureMixins.java

git mv src/main/java/com/radiance/mixins/vulkan_options/VideoOptionsScreenMixins.java \
       src/deferred/java/com/radiance/mixins/vulkan_options/VideoOptionsScreenMixins.java
```

- [ ] **Step 3: Comment out the moved mixin entries in radiance.mixins.json**

Edit `src/main/resources/radiance.mixins.json`. For each of the moved mixins, change the line from active to a commented form. Since JSON does not natively support comments and Mixin's parser is strict, the cleanest approach is to delete the entries from the JSON entirely and add a sibling section above the `mixins` array documenting deferral:

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.radiance.mixins",
  "compatibilityLevel": "JAVA_17",
  "plugin": "com.radiance.mixin_related.MixinPlugin",
  "_deferred_until_implemented": [
    "vanilla_resource_tracker.ReloadableTextureMixins (1.21+ ReloadableTexture)",
    "vulkan_options.VideoOptionsScreenMixins (1.21+ SimpleOption shapes)",
    "vulkan_render_integration.CloudRendererMixins (1.21+ CloudRenderer)",
    "vulkan_render_integration.SectionBuilderMixins (1.21+ SectionBuilder)",
    "vulkan_render_integration.BuiltBufferMixins (1.21+ BuiltBuffer)",
    "vulkan_render_integration.RenderLayerMixins (1.21+ RenderLayer.of shape)",
    "vulkan_render_integration.VideoWarningManagerWarningPatternLoaderMixins (1.21+ buildWarnings shape)",
    "vulkan_render_integration.ReloadableResourceManagerImplMixins (1.21+ registerReloader variant)",
    "vulkan_render_integration.WorldRendererSkyWeatherMixins (after split in Task 7)"
  ],
  "mixins": [...],
  ...
}
```

Then DELETE the corresponding strings from the `mixins` array and from the `client` array. The `_deferred_until_implemented` key is informational only — Mixin ignores keys with leading underscores.

- [ ] **Step 4: Verify Loom does not pick up src/deferred/**

Run: `./gradlew compileJava --info 2>&1 | grep -i "source"`
Expected: only `src/main/java` appears in the source-set listing. `src/deferred/java` does NOT appear.

- [ ] **Step 5: Commit**

```bash
git add -A src/deferred/ src/main/java/com/radiance/mixins/ src/main/resources/radiance.mixins.json
git commit -m "build: compile-quarantine 1.21+-only mixins to src/deferred/java/"
```

---

### Task 7: Split WorldRendererMixins into Core + SkyWeather (move SkyWeather to deferred)

**Files:**
- Modify: `src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java` (will be replaced)
- Create: `src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererCoreMixins.java`
- Create: `src/deferred/java/com/radiance/mixins/vulkan_render_integration/WorldRendererSkyWeatherMixins.java`
- Modify: `src/main/resources/radiance.mixins.json`

- [ ] **Step 1: Open WorldRendererMixins.java and identify the two responsibility groups**

Read `src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java`. Group the methods:

- **Core (alpha-2):** any inject/redirect targeting `render`, terrain submission, `ChunkRenderingDataPreparer`, entity submission paths, framebuffer write.
- **SkyWeather (beta-1):** anything targeting `renderSky`, `renderClouds`, `renderWeather`, references to `SkyRendering`, `SUN_TEXTURE`, `MOON_PHASES_TEXTURE`.

- [ ] **Step 2: Create WorldRendererCoreMixins.java with the core-group methods**

Create `src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererCoreMixins.java`. Copy the package declaration, all imports needed by the core-group methods (drop imports for `SkyRendering` and 1.21+ sky-only types), the `@Mixin(WorldRenderer.class)` annotation, and the core-group methods only. Class name: `WorldRendererCoreMixins`. The file must compile against 1.20.1 yarn (verify after Step 5).

- [ ] **Step 3: Create WorldRendererSkyWeatherMixins.java with the sky-group methods, in deferred**

Create `src/deferred/java/com/radiance/mixins/vulkan_render_integration/WorldRendererSkyWeatherMixins.java`. Same package, but holds only the sky/weather/cloud-group methods. References to 1.21+ `SkyRendering` are OK here because this file is not compiled. Class name: `WorldRendererSkyWeatherMixins`.

- [ ] **Step 4: Delete the original WorldRendererMixins.java**

```bash
git rm src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererMixins.java
```

- [ ] **Step 5: Update radiance.mixins.json**

In `radiance.mixins.json`'s `client` array, replace `"vulkan_render_integration.WorldRendererMixins"` with `"vulkan_render_integration.WorldRendererCoreMixins"`. Add `WorldRendererSkyWeatherMixins` to the `_deferred_until_implemented` list (already added in Task 6 step 3 — verify it's there).

- [ ] **Step 6: Commit**

```bash
git add -A src/main/java/com/radiance/mixins/vulkan_render_integration/ \
            src/deferred/java/com/radiance/mixins/vulkan_render_integration/ \
            src/main/resources/radiance.mixins.json
git commit -m "refactor: split WorldRendererMixins into Core (alpha-2) + SkyWeather (beta-1, deferred)"
```

---

### Task 8: Stub 1.21+-only entries in Constants.VertexFormats

**Files:**
- Modify: `src/main/java/com/radiance/client/constant/Constants.java`

- [ ] **Step 1: Comment out the two 1.21+-only vertex formats**

In `src/main/java/com/radiance/client/constant/Constants.java`, around lines 80–127 (the `VertexFormats` enum), replace the two enum values that reference 1.21+-only formats. The lines to comment out:

```java
        POSITION_TEXTURE_LIGHT_COLOR(
            net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_LIGHT_COLOR, 10),
        POSITION_TEXTURE_COLOR_NORMAL(
            net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, 11),
```

Replace with a comment block (do NOT renumber `PBR_TRIANGLE` — leave the ordinal gap, per PRD §4.4):

```java
        // Ordinals 10 and 11 reserved for 1.21+-only formats POSITION_TEXTURE_LIGHT_COLOR
        // and POSITION_TEXTURE_COLOR_NORMAL. These do not exist in 1.20.1 yarn. The slots
        // remain reserved so MCVR's ordinal table stays version-stable. Re-add when
        // backporting to 1.21.x. See PRD §4.4 / §4.6.
```

- [ ] **Step 2: Verify ./gradlew compileJava succeeds**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. (Tasks 2–7 stripped the AW, quarantined 1.21+-only mixins, split WorldRenderer; Task 8 finishes the immediate compile breakage.)

If it still fails, the failing file's imports will tell you which additional mixin needs deferring. Move it to `src/deferred/java/...`, comment it out of `radiance.mixins.json`, repeat. Document any newly-discovered deferred files in a follow-up commit.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/radiance/client/constant/Constants.java
git commit -m "chore: stub 1.21+-only vertex format ordinals for 1.20.1 compile"
```

---

### Task 9: Implement RadianceState (test-driven)

**Files:**
- Create: `src/test/java/com/radiance/client/RadianceStateTest.java`
- Create: `src/main/java/com/radiance/client/RadianceState.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/radiance/client/RadianceStateTest.java`:

```java
package com.radiance.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadianceStateTest {

    @AfterEach
    void reset() {
        RadianceState.set(RadianceState.UNINITIALIZED);
    }

    @Test
    void defaultStateIsUninitialized() {
        assertEquals(RadianceState.UNINITIALIZED, RadianceState.get());
    }

    @Test
    void resourceTrackingDisabledWhenUninitialized() {
        assertFalse(RadianceState.isResourceTrackingEnabled());
        assertFalse(RadianceState.isRendererActive());
    }

    @Test
    void resourceTrackingEnabledAfterBootOk() {
        RadianceState.set(RadianceState.BOOT_OK);
        assertTrue(RadianceState.isResourceTrackingEnabled());
        assertFalse(RadianceState.isRendererActive());
    }

    @Test
    void rendererActiveAfterRendererActive() {
        RadianceState.set(RadianceState.RENDERER_ACTIVE);
        assertTrue(RadianceState.isResourceTrackingEnabled());
        assertTrue(RadianceState.isRendererActive());
    }

    @Test
    void rendererInactiveAfterDisabled() {
        RadianceState.set(RadianceState.RENDERER_DISABLED);
        assertFalse(RadianceState.isResourceTrackingEnabled());
        assertFalse(RadianceState.isRendererActive());
    }

    @Test
    void rendererInactiveAfterInitFailed() {
        RadianceState.set(RadianceState.INIT_FAILED);
        assertFalse(RadianceState.isResourceTrackingEnabled());
        assertFalse(RadianceState.isRendererActive());
    }

    @Test
    void runIfActiveSkipsWhenInactive() {
        RadianceState.set(RadianceState.BOOT_OK);
        boolean[] ran = {false};
        RadianceState.runIfActive(() -> ran[0] = true);
        assertFalse(ran[0]);
    }

    @Test
    void runIfActiveExecutesWhenActive() {
        RadianceState.set(RadianceState.RENDERER_ACTIVE);
        boolean[] ran = {false};
        RadianceState.runIfActive(() -> ran[0] = true);
        assertTrue(ran[0]);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.radiance.client.RadianceStateTest`
Expected: FAIL with compilation error — `RadianceState` does not exist.

- [ ] **Step 3: Implement RadianceState**

Create `src/main/java/com/radiance/client/RadianceState.java`:

```java
package com.radiance.client;

public final class RadianceState {

    public enum State {
        UNINITIALIZED, INIT_FAILED, BOOT_OK, RENDERER_ACTIVE, RENDERER_DISABLED
    }

    public static final State UNINITIALIZED = State.UNINITIALIZED;
    public static final State INIT_FAILED = State.INIT_FAILED;
    public static final State BOOT_OK = State.BOOT_OK;
    public static final State RENDERER_ACTIVE = State.RENDERER_ACTIVE;
    public static final State RENDERER_DISABLED = State.RENDERER_DISABLED;

    private static volatile State current = State.UNINITIALIZED;

    private RadianceState() {
    }

    public static synchronized void set(State next) {
        current = next;
    }

    public static State get() {
        return current;
    }

    public static boolean isResourceTrackingEnabled() {
        State s = current;
        return s == State.BOOT_OK || s == State.RENDERER_ACTIVE;
    }

    public static boolean isRendererActive() {
        return current == State.RENDERER_ACTIVE;
    }

    public static void runIfActive(Runnable r) {
        if (isRendererActive()) {
            r.run();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.radiance.client.RadianceStateTest`
Expected: 8 tests, 0 failures, 0 errors. BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/radiance/client/RadianceState.java \
        src/test/java/com/radiance/client/RadianceStateTest.java
git commit -m "feat: add RadianceState with isResourceTrackingEnabled/isRendererActive predicates"
```

---

### Task 10: Implement RadianceBufferHandle with ByteBuffer round-trip (test-driven)

**Files:**
- Create: `src/test/java/com/radiance/client/proxy/buffer/RadianceBufferHandleTest.java`
- Create: `src/main/java/com/radiance/client/proxy/buffer/RadianceBufferHandle.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/radiance/client/proxy/buffer/RadianceBufferHandleTest.java`:

```java
package com.radiance.client.proxy.buffer;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadianceBufferHandleTest {

    @Test
    void byteBufferLayoutIs40BytesLittleEndian() {
        RadianceBufferHandle h = new RadianceBufferHandle(
            42, 84, 7, 1, 4, true, 0xCAFEBABEL, 16);
        ByteBuffer buf = h.toByteBuffer();
        assertEquals(40, buf.remaining());
        assertEquals(java.nio.ByteOrder.LITTLE_ENDIAN, buf.order());
    }

    @Test
    void roundTripPreservesAllFields() {
        RadianceBufferHandle original = new RadianceBufferHandle(
            42, 84, 7, 1, 4, true, 0xCAFEBABEL, 16);
        ByteBuffer buf = original.toByteBuffer();
        RadianceBufferHandle decoded = RadianceBufferHandle.fromByteBuffer(buf);

        assertEquals(original.vertexCount, decoded.vertexCount);
        assertEquals(original.indexCount, decoded.indexCount);
        assertEquals(original.vertexFormatOrdinal, decoded.vertexFormatOrdinal);
        assertEquals(original.indexTypeOrdinal, decoded.indexTypeOrdinal);
        assertEquals(original.drawModeOrdinal, decoded.drawModeOrdinal);
        assertEquals(original.hasData, decoded.hasData);
        assertEquals(original.centroidArrayPtr, decoded.centroidArrayPtr);
        assertEquals(original.centroidArrayLen, decoded.centroidArrayLen);
    }

    @Test
    void roundTripWithFalseHasData() {
        RadianceBufferHandle original = new RadianceBufferHandle(
            0, 0, 0, 0, 0, false, 0L, 0);
        RadianceBufferHandle decoded = RadianceBufferHandle.fromByteBuffer(original.toByteBuffer());
        assertEquals(false, decoded.hasData);
    }

    @Test
    void byteBufferIsDirectAllocated() {
        RadianceBufferHandle h = new RadianceBufferHandle(
            1, 2, 3, 4, 5, true, 6L, 7);
        ByteBuffer buf = h.toByteBuffer();
        assertTrue(buf.isDirect(), "ByteBuffer must be direct for JNI consumption");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.radiance.client.proxy.buffer.RadianceBufferHandleTest`
Expected: FAIL with compilation error — `RadianceBufferHandle` does not exist.

- [ ] **Step 3: Implement RadianceBufferHandle**

Create `src/main/java/com/radiance/client/proxy/buffer/RadianceBufferHandle.java`:

```java
package com.radiance.client.proxy.buffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Radiance-owned buffer descriptor. Replaces every JNI reference to MC's BuiltBuffer.
 * Serializes to a fixed 40-byte direct ByteBuffer (LittleEndian) per PRD §4.4.
 *
 * Layout (offsets in bytes):
 *   0  (4): vertexCount         int32 LE
 *   4  (4): indexCount          int32 LE
 *   8  (4): vertexFormatOrdinal int32 LE
 *  12  (4): indexTypeOrdinal    int32 LE
 *  16  (4): drawModeOrdinal     int32 LE
 *  20  (4): hasData             int32 LE (0 or 1)
 *  24  (8): centroidArrayPtr    uint64 LE
 *  32  (4): centroidArrayLen    int32 LE
 *  36  (4): pad                 (zero)
 */
public final class RadianceBufferHandle {

    public static final int LAYOUT_SIZE_BYTES = 40;

    public final int vertexCount;
    public final int indexCount;
    public final int vertexFormatOrdinal;
    public final int indexTypeOrdinal;
    public final int drawModeOrdinal;
    public final boolean hasData;
    public final long centroidArrayPtr;
    public final int centroidArrayLen;

    public RadianceBufferHandle(int vertexCount, int indexCount, int vertexFormatOrdinal,
                                int indexTypeOrdinal, int drawModeOrdinal, boolean hasData,
                                long centroidArrayPtr, int centroidArrayLen) {
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.vertexFormatOrdinal = vertexFormatOrdinal;
        this.indexTypeOrdinal = indexTypeOrdinal;
        this.drawModeOrdinal = drawModeOrdinal;
        this.hasData = hasData;
        this.centroidArrayPtr = centroidArrayPtr;
        this.centroidArrayLen = centroidArrayLen;
    }

    public ByteBuffer toByteBuffer() {
        ByteBuffer buf = ByteBuffer.allocateDirect(LAYOUT_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0, vertexCount);
        buf.putInt(4, indexCount);
        buf.putInt(8, vertexFormatOrdinal);
        buf.putInt(12, indexTypeOrdinal);
        buf.putInt(16, drawModeOrdinal);
        buf.putInt(20, hasData ? 1 : 0);
        buf.putLong(24, centroidArrayPtr);
        buf.putInt(32, centroidArrayLen);
        buf.putInt(36, 0); // pad
        buf.position(0).limit(LAYOUT_SIZE_BYTES);
        return buf;
    }

    public static RadianceBufferHandle fromByteBuffer(ByteBuffer buf) {
        ByteBuffer view = buf.order() == ByteOrder.LITTLE_ENDIAN
            ? buf
            : buf.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        return new RadianceBufferHandle(
            view.getInt(0),
            view.getInt(4),
            view.getInt(8),
            view.getInt(12),
            view.getInt(16),
            view.getInt(20) != 0,
            view.getLong(24),
            view.getInt(32));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.radiance.client.proxy.buffer.RadianceBufferHandleTest`
Expected: 4 tests, 0 failures, 0 errors. BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/radiance/client/proxy/buffer/RadianceBufferHandle.java \
        src/test/java/com/radiance/client/proxy/buffer/RadianceBufferHandleTest.java
git commit -m "feat: add RadianceBufferHandle with 40-byte direct ByteBuffer JNI transport"
```

---

### Task 11: Add RadianceVertexConsumer interface and RadianceBufferAdapter stub

**Files:**
- Create: `src/main/java/com/radiance/client/proxy/buffer/RadianceVertexConsumer.java`
- Create: `src/main/java/com/radiance/client/proxy/buffer/RadianceBufferAdapter.java`

- [ ] **Step 1: Create the interface**

Create `src/main/java/com/radiance/client/proxy/buffer/RadianceVertexConsumer.java`:

```java
package com.radiance.client.proxy.buffer;

/**
 * Radiance-owned vertex consumer surface. Replaces every JNI reference to MC's VertexConsumer
 * so the JNI contract does not depend on MC version. Methods are added in Implementation
 * Checkpoint C as PBRVertexConsumer is wired to this interface.
 */
public interface RadianceVertexConsumer {
    // Methods land in Checkpoint C with PBRVertexConsumer integration.
}
```

- [ ] **Step 2: Create the adapter stub**

Create `src/main/java/com/radiance/client/proxy/buffer/RadianceBufferAdapter.java`:

```java
package com.radiance.client.proxy.buffer;

/**
 * Single source of truth for converting MC's BuiltBuffer to a RadianceBufferHandle.
 * The real implementation lands in Implementation Checkpoint C against 1.20.1's
 * BufferBuilder.BuiltBuffer.getParameters() shape. Stubbed here so callers can compile
 * before that work begins.
 */
public final class RadianceBufferAdapter {

    private RadianceBufferAdapter() {
    }

    // Real signature lands in Checkpoint C:
    // public static RadianceBufferHandle from(net.minecraft.client.render.BufferBuilder.BuiltBuffer buf)
    // For now, only the empty class exists so other code can reference the package.
}
```

- [ ] **Step 3: Verify ./gradlew compileJava + test still pass**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL. The existing 12 tests (8 RadianceState + 4 RadianceBufferHandle) still pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/radiance/client/proxy/buffer/
git commit -m "feat: add RadianceVertexConsumer interface and RadianceBufferAdapter stub (real impl in Checkpoint C)"
```

---

### Task 12: Add Constants.dumpOrdinals() with test

**Files:**
- Modify: `src/main/java/com/radiance/client/constant/Constants.java`
- Create: `src/test/java/com/radiance/client/constant/ConstantsDumpOrdinalsTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/radiance/client/constant/ConstantsDumpOrdinalsTest.java`:

```java
package com.radiance.client.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstantsDumpOrdinalsTest {

    @Test
    void dumpOrdinalsReturnsNonEmptyTable() {
        long[] ords = Constants.dumpOrdinals();
        assertNotNull(ords);
        assertTrue(ords.length > 0, "ordinal table must contain at least one entry");
    }

    @Test
    void dumpOrdinalsIsDeterministic() {
        long[] a = Constants.dumpOrdinals();
        long[] b = Constants.dumpOrdinals();
        assertTrue(java.util.Arrays.equals(a, b),
            "dumpOrdinals must produce the same table on every call (used for ABI handshake)");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.radiance.client.constant.ConstantsDumpOrdinalsTest`
Expected: FAIL with compilation error — `Constants.dumpOrdinals` does not exist.

- [ ] **Step 3: Add dumpOrdinals to Constants.java**

In `src/main/java/com/radiance/client/constant/Constants.java`, add the following method at the end of the `Constants` class (just before the closing `}`):

```java
    /**
     * Constructs the Java-side ordinal table for the JNI ABI handshake (PRD §4.3 / §4.4).
     * The order is part of the JNI contract: vertex format ordinals first, then draw mode,
     * then index type, then geometry type, then RT flags. MCVR validates this exact order.
     */
    public static long[] dumpOrdinals() {
        java.util.List<Long> out = new java.util.ArrayList<>();
        for (VertexFormats v : VertexFormats.values()) out.add((long) v.getValue());
        for (DrawModes d : DrawModes.values()) out.add((long) d.getValue());
        for (IndexTypes i : IndexTypes.values()) out.add((long) i.getValue());
        for (GeometryTypes g : GeometryTypes.values()) out.add((long) g.getValue());
        for (RayTracingFlags r : RayTracingFlags.values()) out.add((long) r.getValue());
        long[] arr = new long[out.size()];
        for (int idx = 0; idx < arr.length; idx++) arr[idx] = out.get(idx);
        return arr;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.radiance.client.constant.ConstantsDumpOrdinalsTest`
Expected: 2 tests, 0 failures, 0 errors. BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/radiance/client/constant/Constants.java \
        src/test/java/com/radiance/client/constant/ConstantsDumpOrdinalsTest.java
git commit -m "feat: add Constants.dumpOrdinals() pure-Java table for JNI handshake"
```

---

### Task 13: Add handshake and validateAbi JNI declarations to RendererProxy

**Files:**
- Modify: `src/main/java/com/radiance/client/proxy/vulkan/RendererProxy.java`

- [ ] **Step 1: Add the two new native declarations**

Open `src/main/java/com/radiance/client/proxy/vulkan/RendererProxy.java`. Find the existing block of `public static native` declarations near the top of the class (after the field declarations and `initFolderPath` / `initRenderer` declarations). Add the following two declarations:

```java
    /**
     * Java passes its ordinal table to native; native compares against its own. Returns 0 on
     * match, non-zero status code on mismatch (encodes which table mismatched).
     * PRD §4.3 / §4.4. Called once from RadianceClient.onInitializeClient after System.load.
     */
    public static native int handshake(int mcVersionId, long[] javaOrdinals);

    /**
     * Idempotent re-check of the ABI table. Same arguments and return semantics as handshake.
     * Used by debug tooling and at every render-loop start when -Dradiance.dev_logging=true.
     */
    public static native int validateAbi(int mcVersionId, long[] javaOrdinals);
```

- [ ] **Step 2: Verify compileJava picks up the new declarations and regenerates JNI headers**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

Run: `ls src/main/native/include/ | grep RendererProxy`
Expected: a `.h` file exists for RendererProxy. Open it and confirm it contains:

```c
JNIEXPORT jint JNICALL Java_com_radiance_client_proxy_vulkan_RendererProxy_handshake
  (JNIEnv *, jclass, jint, jlongArray);

JNIEXPORT jint JNICALL Java_com_radiance_client_proxy_vulkan_RendererProxy_validateAbi
  (JNIEnv *, jclass, jint, jlongArray);
```

These are the symbols MCVR's `mc/1.20.1` branch must implement in Checkpoint 0c.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/radiance/client/proxy/vulkan/RendererProxy.java \
        src/main/native/include/
git commit -m "feat: add RendererProxy.handshake(int, long[]) and validateAbi(int, long[]) JNI decls"
```

---

### Task 14: Convert MixinPlugin from global flag to per-mixin allowlist (test-driven)

**Files:**
- Create: `src/test/java/com/radiance/mixin_related/MixinPluginTest.java`
- Modify: `src/main/java/com/radiance/mixin_related/MixinPlugin.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/radiance/mixin_related/MixinPluginTest.java`:

```java
package com.radiance.mixin_related;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinPluginTest {

    @Test
    void resourceTrackerCoreMixinsAreEnabled() {
        MixinPlugin plugin = new MixinPlugin();
        assertTrue(plugin.shouldApplyMixin(
            "anyTarget",
            "com.radiance.mixins.vanilla_resource_tracker.NamespaceResourceManagerMixins"));
        assertTrue(plugin.shouldApplyMixin(
            "anyTarget",
            "com.radiance.mixins.vanilla_resource_tracker.TextureManagerMixins"));
        assertTrue(plugin.shouldApplyMixin(
            "anyTarget",
            "com.radiance.mixins.vanilla_resource_tracker.AbstractTextureMixins"));
        assertTrue(plugin.shouldApplyMixin(
            "anyTarget",
            "com.radiance.mixins.vanilla_resource_tracker.NativeImageMixins"));
    }

    @Test
    void renderIntegrationMixinsAreNotYetEnabled() {
        MixinPlugin plugin = new MixinPlugin();
        assertFalse(plugin.shouldApplyMixin(
            "anyTarget",
            "com.radiance.mixins.vulkan_render_integration.WorldRendererCoreMixins"));
        assertFalse(plugin.shouldApplyMixin(
            "anyTarget",
            "com.radiance.mixins.vulkan_render_integration.MinecraftClientMixins"));
    }

    @Test
    void unknownMixinIsNotEnabled() {
        MixinPlugin plugin = new MixinPlugin();
        assertFalse(plugin.shouldApplyMixin(
            "anyTarget",
            "com.radiance.mixins.never.DefinedMixins"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.radiance.mixin_related.MixinPluginTest`
Expected: tests run but assertions fail — current `MixinPlugin.shouldApplyMixin` returns `ENABLED` (true) for all inputs, so the second test (`renderIntegrationMixinsAreNotYetEnabled`) fails.

- [ ] **Step 3: Replace the global flag with an allowlist**

Edit `src/main/java/com/radiance/mixin_related/MixinPlugin.java`. Replace the `public static boolean ENABLED = true;` field and the `shouldApplyMixin` method with:

```java
    /**
     * Per-mixin allowlist (PRD §4.5 / §4.6). Mixins not listed here are skipped at runtime
     * even if `radiance.mixins.json` declares them. Each Implementation Checkpoint adds the
     * mixins it owns. The allowlist is canonical; `radiance.mixins.json` is structural.
     *
     * Current scope: alpha-0 — only the four resource-tracker mixins are applied. Vulkan
     * rendering mixins are added starting in Checkpoint B.
     */
    public static final java.util.Set<String> ENABLED_MIXINS = java.util.Set.of(
        "com.radiance.mixins.vanilla_resource_tracker.NamespaceResourceManagerMixins",
        "com.radiance.mixins.vanilla_resource_tracker.TextureManagerMixins",
        "com.radiance.mixins.vanilla_resource_tracker.AbstractTextureMixins",
        "com.radiance.mixins.vanilla_resource_tracker.NativeImageMixins"
    );

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return ENABLED_MIXINS.contains(mixinClassName);
    }
```

Remove the now-unused `public static boolean ENABLED = true;` line.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.radiance.mixin_related.MixinPluginTest`
Expected: 3 tests, 0 failures, 0 errors. BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/radiance/mixin_related/MixinPlugin.java \
        src/test/java/com/radiance/mixin_related/MixinPluginTest.java
git commit -m "feat: convert MixinPlugin to per-mixin allowlist (alpha-0 enables only the 4 resource trackers)"
```

---

### Task 15: Final verification — full build + full test suite

**Files:** none modified

- [ ] **Step 1: Run the complete test suite**

Run: `./gradlew clean test`
Expected: BUILD SUCCESSFUL. Test report shows 17 tests total (8 RadianceState + 4 RadianceBufferHandle + 2 ConstantsDumpOrdinals + 3 MixinPlugin), 0 failures, 0 errors.

- [ ] **Step 2: Run the full build (without runClient — that needs MCVR)**

Run: `./gradlew clean build -x test`
Expected: BUILD SUCCESSFUL. The output `build/libs/Radiance-0.1.3-alpha-fabric-1.20.1-windows.jar` exists. (The jar is shippable in the sense that it compiles; it cannot run yet because `core.dll` was built against the 1.21.4 Java tree in Checkpoint 0a and does not have the new `handshake`/`validateAbi` symbols.)

- [ ] **Step 3: Inspect the JNI headers regenerated by compileJava**

Run: `ls -la src/main/native/include/ | head -20`
Expected: `.h` files exist for the proxy classes including `RendererProxy.h`. The `RendererProxy.h` contains `Java_com_radiance_client_proxy_vulkan_RendererProxy_handshake` and `Java_com_radiance_client_proxy_vulkan_RendererProxy_validateAbi` — these are what MCVR's `mc/1.20.1` branch needs to implement in Checkpoint 0c.

- [ ] **Step 4: Confirm src/deferred/ is invisible to Loom**

Run: `./gradlew compileJava --info 2>&1 | grep -E "(source|java)" | head -30`
Expected: no occurrence of `src/deferred` in the source-set listing. Only `src/main/java` is processed.

- [ ] **Step 5: Final commit (if anything was modified during verification — typically nothing)**

```bash
git status
# expected: clean working tree
```

If clean, the prior task's commit is the last one for Checkpoint 0b. The branch is now ready for Checkpoint 0c (MCVR rebuild against the new JNI headers from Step 3).

---

## Self-Review

**1. Spec coverage.** Walking PRD §4.5 (compile-quarantine) — covered by Tasks 6, 7. PRD §4.4 (buffer abstraction) — covered by Tasks 10, 11. PRD §4.3 (JNI handshake) — covered by Tasks 12 (dumpOrdinals), 13 (declarations). PRD §4.7 (RadianceState two predicates) — covered by Task 9. PRD §4.6 WorldRenderer split — covered by Task 7. Yarn migration — Tasks 2, 3, 4. Access widener strip — Task 5. MixinPlugin allowlist — Task 14.

**Gap:** The `RadianceVertexConsumer` interface is created empty in Task 11 with the methods deferred to Checkpoint C. This is acceptable per PRD §4.4: PBRVertexConsumer wiring lands when its first JNI consumer (in Checkpoint C) needs it.

**Gap:** Per-injection-type guard patterns (PRD §4.7 table) are not exercised in this plan. They land in Checkpoint B when the first renderer mixin is enabled. This is intentional — Checkpoint 0b enables zero renderer mixins, so there is nothing to guard yet.

**2. Placeholder scan.** No occurrences of TBD, TODO, "implement later," "fill in details," "similar to Task N." Each step has either an exact command or a complete code block.

**3. Type consistency.** `RadianceState.set(...)` accepts a `RadianceState.State` enum value but the test calls `RadianceState.set(RadianceState.UNINITIALIZED)` — verified that `UNINITIALIZED` is a `public static final State` constant in `RadianceState.java` (Task 9 Step 3). `dumpOrdinals()` returns `long[]` consistent across Task 12 implementation and Task 13 JNI signature `handshake(int, long[])`.

---

## Execution Handoff

Plan complete. Two execution options:

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session via superpowers:executing-plans, batch execution with checkpoints for review.

Note: this Part 3 plan covers Implementation Checkpoint 0b only. Subsequent checkpoints (0c, A, B, C, D, E, F) need their own bite-sized plans written when each comes up — by the same skill, in their own document or appended here. The PRD (Part 1) is the contract those plans implement against.

---

# Part 3 — Status Note: Checkpoint 0b SHIPPED (2026-05-10)

All 15 tasks of Part 3 executed via subagent-driven-development. Merged to `main` as commit `a456701` (a `--no-ff` merge of 17 feature-branch commits). Post-merge `./gradlew clean test` BUILD SUCCESSFUL with 17 unit tests passing.

**Plan deviations during execution (all justified, all accepted by final code review):**

- **Loom 1.6 → 1.11.** Loom 1.6.12 calls `Problems.forNamespace` removed in Gradle 8.13+; the wrapper is 8.14.1. Kept Loom 1.11-SNAPSHOT (which Task 2's incidental compile-against-1.20.1 already proved works). Documented in commit `c75be67` and PRD §4.2 should be updated next time the PRD is touched.
- **WorldRendererMixins split → defer whole.** The file's monolithic `redirectRender(ObjectAllocator, RenderTickCounter, …)` interleaves sky uniforms, cloud rendering, chunks, and entity dispatch in a single `@Inject`. Splitting was not viable pre-rewrite. Deferred whole. The PRD §4.6 "split locked" should be amended to "defer whole; rewrite in C/D."
- **Compile-quarantine 14 files → 67 files.** Plan envisioned ~14 deferred files. Reality required 67 (PRD §4.5 was optimistic — 1.21+ APIs leak across `client/proxy/`, `client/vertex/`, `client/cloud/`, GUI screens, not just mixins). Each deferred file documented in `radiance.mixins.json`'s `_deferred_until_implemented` index.
- **`Constants.PBR_TRIANGLE` removed entirely** (not just renumbered). Its referenced enum value `PBRVertexFormats.PBR_TRIANGLE` lives in `client/vertex/PBRVertexFormats.java`, which had to be deferred. Ordinal 12 reserved with a comment for re-population at Checkpoint C.
- **`Constants.GeometryTypes.getGeometryType()` stubbed to throw** `UnsupportedOperationException` because its body needs `BufferProxy`/`ChunkProxy`/`EntityProxy` (deferred). Verified zero callers in active main.
- **Several in-place patches:** `KeyInputHandler` O-key now no-op (RadianceSettingsScreen deferred), `DlssMissingScreen` uses 1.20.1 `renderBackground(DrawContext)` signature (1.20.1 lacks the multi-arg overload), `EmissiveBlock`/`LightSourceRegistry` commented out 1.21+ `COPPER_BULB` registrations. Each marked with a comment pointing at its re-enablement target.
- **AW restoration: 4 entries** that Task 5 over-stripped (`OptionListWidget$WidgetEntry`, `SimpleOption$CyclingCallbacks`, `RenderPhase$Lightmap`, `RenderPhase$Target`) were re-added during Task 8b because their inner-class targets DO exist in 1.20.1 — just `protected`. The earlier strip was correct for the truly-1.21+ entries (SkyRendering constants, CloudRenderer$ViewMode) but wrong for these four.
- **AW deletion: 1 more** (`TrueTypeFont.getInfo()Lorg/lwjgl/util/freetype/FT_Face;`) added in commit `0ad3246` — LWJGL 3.3.1 in 1.20.1 doesn't have the freetype binding.

**Known runtime defects flagged for Checkpoint A (NOT blocking 0b):**

- 5 mixin runtime warnings from Loom: `GlStateManager._clear`, `AbstractTexture.setClamp`, `Screen.applyBlur`, `GameOptionsScreen` `@Shadow` field. Currently MOOTED because `MixinPlugin.ENABLED_MIXINS` only enables 4 mixins (the 4 resource trackers); the warnings will fire for real when those mixins are promoted in Checkpoint B/C/D.
- `radiance.mixins.json` lists 28 active mixin classes; `MixinPlugin.ENABLED_MIXINS` enables 4. Checkpoint B/C/D promote each as it's verified against 1.20.1 yarn. The MixinPlugin javadoc explicitly says so.
- `runClient` cannot execute end-to-end until Checkpoint 0c rebuilds MCVR with the new `handshake`/`validateAbi` symbols implemented.

---

# Part 4 — Bite-Sized Execution Plan: Checkpoint 0c+A (MCVR with handshake symbol; complete alpha-0 wiring) — solo + Claude Code

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Name note (continued ordering drift, 2026-05-11 fourth pass):** the PRD's original sequence was 0a → 0b → 0c → A. Reality: 0b shipped first (Part 3 status), then a planned "0a-recovery" Part 4 was drafted around an unmodified-MCVR build. Between drafts, the Java workspace evolved further: `RadianceClient.performHandshake()` was wired into `initializeNativeRenderer`, `Constants.dumpOrdinals()` became a structured table (PRD §4.3.1), `core.lib` and Streamline DLLs became optional via `copyOptionalFileFromResource`, and `ModuleEntry` plus its tests grew. Part 4 is therefore re-targeted as **Checkpoint 0c+A** — build MCVR's `mc/1.20.1` branch with the new `handshake`/`validateAbi` symbols implementing the §4.3.1 decoder, against the current Java head, and complete the alpha-0 boot wiring end-to-end. The original 0a/0a-recovery framings are superseded — clearing G1+G3-recovery is the new pass criterion.

**Goal:** Resolve PRD OQ-01 by clearing PRD G1+G3-recovery (PRD §10): produce a `core.dll` from MCVR's `mc/1.20.1` branch built on the user's Windows machine against the current 1.20.1 Java headers, with the C++ `RendererProxy_handshake` decoder implementing §4.3.1's structured-table format. The Java client launches, `System.load` succeeds, `RendererProxy.handshake(12001, ordinals)` returns `0`, `RadianceState` transitions `UNINITIALIZED → BOOT_OK`, the four resource-tracker mixins begin shadowing without runtime error, and the main menu renders via vanilla GL.

**Architecture:** Two workstreams — (0) **Mac-side prep** (~20 min, blocks the Windows build: commits the dirty work as the new alpha-0 baseline and pushes to GitHub), (1) sequential **Windows build + C++ decoder** (the user's Windows machine, ~30–60 hours of work). Workstream 1 is solo + Claude Code: the user runs Claude Code on the Windows box (or shares a worktree) and pairs through the toolchain install, the MCVR clone, the `mc/1.20.1` branch creation, the §4.3.1 decoder implementation, and the W13 smoke test. The checkpoint completes when the Windows build produces `core.dll` whose `handshake` symbol returns `0` AND `runClient` reaches the main menu via vanilla GL with `RadianceState.BOOT_OK`.

**User-confirmed constraints (from clarifying questions before this plan):**
- Has access to a Windows machine with admin rights to install dev tooling.
- Willing to learn the minimum C++/Vulkan/CMake needed, paired with Claude Code on the Windows box.
- Does NOT want to pursue OSS-collaborator outreach (prior plan drafts included a separate outreach workstream; that has been removed at the user's request — they will do all C++ work themselves with Claude Code as a pair-programming assistant).

**Tech Stack (Windows side):** Visual Studio 2022 Build Tools (MSVC v143, C++20), Vulkan SDK 1.3.x (LunarG), CMake 3.27+ (Windows installer), Git for Windows, **Temurin JDK 17** (matches the repo's `targetJavaVersion = 17` and the Loom `runClient` launcher; do NOT use JDK 21), Claude Code on Windows (or sharing the Windows worktree from the Mac session). MCVR's external deps (NRD, FFX, GLM, STB, VMA, GLFW) are git submodules pulled by `git clone --recurse-submodules`. The C++ work scope on top of an unmodified MCVR build: implement `RendererProxy_handshake(JNIEnv*, jclass, jint, jlongArray)` and `RendererProxy_validateAbi(...)` per PRD §4.3.1 — the structured-ordinal-table decoder is roughly 100–200 lines of C++ with a fixture for `mcVersionId == 12001` (the only ID accepted in v1.0).

**Honest scope estimate:** 30–60 hours part-time. Toolchain install (~2–4 hrs), reading MCVR's existing JNI conventions (~8–16 hrs, faster with Claude Code), C++ handshake-decoder implementation against §4.3.1 (~10–20 hrs solo + Claude Code), cmake config/build/install + debug iterations (~4–8 hrs), Java-side smoke test on Windows (~2–4 hrs), BUILD-WINDOWS.md (~1–2 hrs). Add +16 hrs if a graphics-debug rabbit hole opens up. Realistically: **2–4 weeks at half-time, 1.5–2 weeks at full-time.**

**Strategic fallback (NOT this plan, just flagged):** if 0c+A stalls past 4 weeks of focused effort, the user can pivot to dropping RT entirely and shipping a Vulkan-rasterization-only 1.20.1 mod by forking VulkanMod. Not in scope here; flag it for OQ-01's resolution if 0c+A runs over.

**Current repo state preflight (verified 2026-05-11 fourth pass against working tree):**
- `git log -1` → `a456701 Merge Checkpoint 0b: Java foundation for MC 1.20.1 backport (alpha-0 surface)`. Clean tree, but **`git status` shows 10 modified files plus an untracked `src/test/java/com/radiance/client/pipeline/` directory** — this is the new alpha-0 wiring (handshake call site, structured ordinals, optional natives, ModuleEntry refactor + tests). M0 (NEW below) commits and pushes this as the new baseline; **clone in W7 must include it**, otherwise the smoke test will revert to the older surface.
- `origin/main` matches `a456701` only because the dirty work isn't pushed yet. After M0, both move forward.
- `gh auth status` → verified `lavindeep` is logged in with `gist + repo + workflow` scopes. (Earlier drafts of this plan included an Outreach-1/2/3 workstream blocked on gh auth; that workstream has been removed at the user's request.)
- Mac shell currently has no Java on PATH. Mac-side `./gradlew test` requires `JAVA_HOME` exported manually (see M3).
- `src/main/resources/` contains Streamline DLLs (5 files) and `modules/` but **no `core.dll`, no `core.lib`, no `shaders/` directory**. Expected (no MCVR build yet); see W12 / W13 for the canonical native-artifact layout.
- `src/main/java/com/radiance/client/RadianceClient.java` **DOES call** `RendererProxy.handshake(MC_VERSION_ID, ordinals)` from `performHandshake()` (line 153, invoked by `initializeNativeRenderer` line 121). `LinkageError` is caught gracefully → `INIT_FAILED`. This means the W13 smoke test inherently exercises the handshake symbol; an MCVR built without the handshake decoder will reach main menu but log `INIT_FAILED` rather than `BOOT_OK`. **W13 requires `BOOT_OK` to clear G1+G3-recovery.**
- `Constants.dumpOrdinals()` is structured (PRD §4.3.1): `[magic, version, section-count, [section-id, payload-length, [entry-id, abi-value, flags]…]…]` — five sections, with vertex-format slots 10/11/12 emitted as `ENTRY_RESERVED` entries. C++ decoder must match exactly.
- Test count is **21** (`@Test` methods across `RadianceStateTest` (8), `RadianceBufferHandleTest` (4), `ConstantsDumpOrdinalsTest` (4 — expanded from 2 to cover the structured layout), `MixinPluginTest` (3), and the new `ModuleEntryTest` (2)). Earlier Part 4 drafts said 17 — outdated.
- `src/deferred/java/` contains 66 quarantined files. Doesn't affect this plan.

---

## Mac-side prep workstream (do this first — blocks workstreams 1 and 2)

### Task M0: Commit + push the dirty alpha-0 wiring as the new baseline

**Where:** the Mac. ~15 minutes.

**Why:** the working tree contains real new alpha-0 wiring on top of `a456701`: handshake call site in `RadianceClient`, structured ordinal table in `Constants`, optional `core.lib` + Streamline DLL extraction, `ModuleEntry` refactor with disk-vs-classpath precedence, three resource-tracker mixin guard-pattern updates, an `Options` tweak, and a new `ModuleEntryTest`. The Windows clone in W7 must include this work; the C++ handshake decoder in W9–W11 implements the structured-table format that lives in this commit. Pushing first prevents the entire Windows workstream from being against a stale baseline.

- [ ] **Step 1: Inspect the dirty state**

```bash
cd "/Users/lavin/Projects/Radiance Backport for 1.20.1"
git status
git diff --stat HEAD
```
Expected: `git status` shows ~10 modified files (CLAUDE.md, build.gradle, RadianceClient.java, Constants.java, Options.java, ModuleEntry.java, three resource-tracker mixins, ConstantsDumpOrdinalsTest.java) and one untracked directory (`src/test/java/com/radiance/client/pipeline/`). `git diff --stat` shows roughly +413/-80 across 10 files.

- [ ] **Step 2: Run the test suite to confirm nothing is broken**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || echo /Applications/Eclipse.app/Contents/Eclipse/plugins/org.eclipse.justj.openjdk.hotspot.jre.full.macosx.aarch64_21.0.7.v20250502-0916/jre)"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test
```
Expected: BUILD SUCCESSFUL, **21 tests passing** (8 RadianceState + 4 RadianceBufferHandle + 4 ConstantsDumpOrdinals + 3 MixinPlugin + 2 ModuleEntry). If anything fails, do NOT commit — fix first.

- [ ] **Step 3: Stage the changes in two thematic commits (so the history bisects cleanly)**

Commit 1 — handshake call-site wiring + structured ordinal table + optional natives:

```bash
git add src/main/java/com/radiance/client/RadianceClient.java \
        src/main/java/com/radiance/client/constant/Constants.java \
        src/main/java/com/radiance/client/option/Options.java \
        src/main/java/com/radiance/mixins/vanilla_resource_tracker/NamespaceResourceManagerMixins.java \
        src/main/java/com/radiance/mixins/vanilla_resource_tracker/NativeImageMixins.java \
        src/main/java/com/radiance/mixins/vanilla_resource_tracker/TextureManagerMixins.java \
        src/test/java/com/radiance/client/constant/ConstantsDumpOrdinalsTest.java
git commit -m "feat(alpha-0): wire RendererProxy.handshake into RadianceClient init; lock structured ordinal table format

- RadianceClient.performHandshake() invokes RendererProxy.handshake(12001, Constants.dumpOrdinals())
  from initializeNativeRenderer; LinkageError caught -> INIT_FAILED (graceful)
- Constants.dumpOrdinals() emits structured table per PRD §4.3.1: magic 'RAD_ORDS',
  version 1, 5 sections, entry triples [id, abi-value, flags] with reserved-entry
  semantics for vertex format slots 10/11/12
- core.lib + Streamline DLLs now optional via copyOptionalFileFromResource (FR-03)
- Resource-tracker mixins use isResourceTrackingEnabled() guard
- ConstantsDumpOrdinalsTest expanded to verify magic/version/section/entry layout"
```

Commit 2 — ModuleEntry refactor + test:

```bash
git add src/main/java/com/radiance/client/pipeline/ModuleEntry.java \
        src/test/java/com/radiance/client/pipeline/
git commit -m "feat(alpha-0): ModuleEntry prefers disk module over classpath when both present

Adds ModuleEntryTest covering:
- discoveredDiskModuleLoadsFromDiscoveredPathEvenWhenResourcePathCollides
- manuallyCreatedModuleEntryLoadsClasspathResource"
```

Commit 3 — build + docs catch-up (REQUIRED, not optional — `build.gradle` now performs the JNI header cleanup that W8 expects to see, and the `runClient` `--gameDir` argument that W13 Step 3a wipes against; without these committed, the Windows clone won't have the same build behavior):

```bash
git add CLAUDE.md build.gradle
git commit -m "build/docs: JNI header cleanup in build.gradle and runClient gameDir; CLAUDE.md catch-up"
```

- [ ] **Step 4: Push to origin/main**

```bash
git log --oneline origin/main..main
git push origin main
```
Expected: 2–3 commits push. **Capture the new head SHA — it's the reference for "current 1.20.1 Java surface" used by W7 (clone instructions) and W14 (BUILD-WINDOWS.md) throughout the rest of Part 4.**

- [ ] **Step 5: Re-verify the test suite from a clean working tree**

```bash
git status   # expected: clean
./gradlew clean test
```
Expected: 21 tests pass on a clean build. The new HEAD is now both the local and remote reference for the rest of this checkpoint.

### Task M1: Push the 0b Java work to GitHub — **OBSOLETE as of 2026-05-11 second review pass**

**Status:** local `main` and `origin/main` both pointed at `a456701` after the second review pass. M0 (above, fourth pass) supersedes M1 — it pushes the additional alpha-0 wiring on top of `a456701`. Skip M1; do M0 instead.

If you ever need the original M1 procedure for reference:

```bash
cd "/Users/lavin/Projects/Radiance Backport for 1.20.1"
git log --oneline origin/main..main   # confirm what would push
git push origin main                   # push (use -u origin main if upstream missing)
```

### Task M2: Repair `gh` authentication — **OPTIONAL**

**Where:** the Mac. ~5 minutes. **Optional** now that the Outreach workstream has been removed. The user no longer files public GitHub artifacts as part of this checkpoint, so `gh` auth is only needed if they want to view issues, create release artifacts via `gh release` later, or use the CLI for incidental Git/GitHub work. Skip if not relevant.

- [ ] **Step 1: Check current state**

```bash
gh auth status
```
Expected if already logged in: `Logged in to github.com account <name>` with scopes including `repo`. If invalid, proceed to Step 2.

- [ ] **Step 2: Re-authenticate**

```bash
gh auth login --hostname github.com --git-protocol https --web
```
Follow the browser flow. Pick HTTPS (or SSH if your repo remote is SSH — match what `git remote -v` shows).

- [ ] **Step 3: Re-verify**

```bash
gh auth status
gh repo view lavindeep/Radiance >/dev/null && echo "auth + repo access OK"
```
Expected: both commands succeed silently.

### Task M3 (optional): Restore a working Java on the Mac

**Where:** the Mac. ~5 minutes. Optional — only needed if you want to keep doing Mac-side `./gradlew test` sanity runs in parallel with the Windows build.

- [ ] **Step 1: Pick a JDK home**

If the Eclipse-bundled JDK 21 from the 0b plan is still present and Gradle is OK with it, you can keep using it:
```
/Applications/Eclipse.app/Contents/Eclipse/plugins/org.eclipse.justj.openjdk.hotspot.jre.full.macosx.aarch64_21.0.7.v20250502-0916/jre
```
Verify it still exists:
```bash
ls "/Applications/Eclipse.app/Contents/Eclipse/plugins" | grep openjdk
```

**For a fresh install, use JDK 17, not 21.** The repo targets Java 17; matching the build target is the cleanest baseline. Install via Homebrew:
```bash
brew install --cask temurin@17
```
The Eclipse-bundled JDK 21 is fine for Gradle if it already works (Gradle is forward-compatible with newer JDKs), but JDK 17 is the canonical choice.

- [ ] **Step 2: Export to shell — pick the block that matches your Step 1 choice**

Add **one** of these blocks to `~/.zshrc` (or run ad-hoc per shell). Do not paste both — they set `JAVA_HOME` to different roots.

**If you kept the Eclipse-bundled JDK 21:**
```bash
export JAVA_HOME="/Applications/Eclipse.app/Contents/Eclipse/plugins/org.eclipse.justj.openjdk.hotspot.jre.full.macosx.aarch64_21.0.7.v20250502-0916/jre"
export PATH="$JAVA_HOME/bin:$PATH"
```

**If you installed Temurin JDK 17 via Homebrew (recommended for fresh installs):**
```bash
# Path varies by Homebrew prefix and Temurin patch version. The /usr/libexec/java_home
# helper resolves whichever 17 is installed; it works on both Apple Silicon and Intel.
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export PATH="$JAVA_HOME/bin:$PATH"
```

- [ ] **Step 3: Verify**

```bash
java -version
cd "/Users/lavin/Projects/Radiance Backport for 1.20.1" && ./gradlew test
```
Expected: java prints a version; full suite passes (**21 tests** as of M0 — 8 RadianceState + 4 RadianceBufferHandle + 4 ConstantsDumpOrdinals + 3 MixinPlugin + 2 ModuleEntry).

---

## Outreach workstream — **REMOVED**

> **REMOVED 2026-05-11 (sixth pass):** the user has decided to do all C++ work themselves with Claude Code as a pair-programming assistant on the Windows box, rather than recruit an OSS collaborator. Outreach-1 (MCVR GitHub issue), Outreach-2 (Radiance Discord), and Outreach-3 (Iris/Sodium/Reddit cross-posts) are deleted from this plan. No public posts will be made. If the user later changes their mind, prior drafts of this plan in git history contain the rendered issue/Discord templates.

---

## Windows toolchain workstream (solo + Claude Code on the Windows box)

### Task 0a-W1: Verify Windows machine baseline

**Where:** the Windows machine (not the Mac).

- [ ] **Step 1: Confirm OS and architecture**

```cmd
winver
systeminfo | findstr /R /C:"OS Name" /C:"OS Version" /C:"System Type"
```
Expected: Windows 10 (1903+) or Windows 11, 64-bit, x64 architecture. (32-bit and ARM64 are not supported by MCVR's bundled deps.)

- [ ] **Step 2: Confirm at least 50 GB free disk space**

Visual Studio Build Tools + Vulkan SDK + MCVR git submodules + build artifacts ≈ 30–40 GB. Plus your existing software.

- [ ] **Step 3: Note your Windows username and home dir**

Used for all subsequent path references. Default: `C:\Users\<username>`.

### Task 0a-W2: Install Visual Studio 2022 Build Tools

**Where:** Windows machine.

- [ ] **Step 1: Download the Visual Studio 2022 Build Tools installer**

URL: https://visualstudio.microsoft.com/downloads/#build-tools-for-visual-studio-2022 (look for "Build Tools for Visual Studio 2022" — it's smaller than the full IDE and sufficient for MCVR).

- [ ] **Step 2: Run the installer; select these workloads**

In the installer's Workloads tab, check:
- ✓ "Desktop development with C++"

In the Individual Components tab, verify these are included (they should be auto-selected):
- MSVC v143 C++ x64/x86 build tools (latest)
- Windows 11 SDK (10.0.22621 or newer)
- C++ CMake tools for Windows (this gives you cmake, but a standalone install in W4 is also fine)
- Git for Windows (or install standalone in W5)

Click Install. Allow ~30 minutes; ~10 GB download.

- [ ] **Step 3: Verify the install**

Open the "x64 Native Tools Command Prompt for VS 2022" from the Start Menu. Run:

```cmd
cl.exe
link.exe
```
Expected: each prints its version banner. `cl.exe` should report version 19.40 or higher (MSVC 2022 v143).

### Task 0a-W3: Install Vulkan SDK

- [ ] **Step 1: Download from LunarG**

URL: https://vulkan.lunarg.com/sdk/home (pick the latest Windows installer). As of writing, 1.3.296.0 or newer.

- [ ] **Step 2: Run the installer with default options**

Defaults install to `C:\VulkanSDK\<version>\`. Allow ~5 minutes; ~3 GB.

- [ ] **Step 3: Verify environment variables and tooling**

```cmd
echo %VULKAN_SDK%
glslangValidator.exe --version
vulkaninfo.exe --summary
```
Expected: `VULKAN_SDK` is set to `C:\VulkanSDK\<version>`. `glslangValidator` prints a version. `vulkaninfo` reports your GPU's Vulkan capabilities (look for `apiVersion: 1.3.x`).

If `vulkaninfo` reports an apiVersion below 1.3, your GPU drivers need updating. NVIDIA: GeForce Experience or nvidia.com/drivers. AMD: amd.com/drivers. Intel Arc: intel.com.

### Task 0a-W4: Install CMake (standalone)

Even if VS bundled CMake, the standalone CMake gives you a more recent version and a system-wide command-line `cmake`.

- [ ] **Step 1: Download from kitware**

URL: https://cmake.org/download/. Get the Windows x64 Installer (`cmake-3.X.Y-windows-x86_64.msi`). Pick CMake ≥ 3.27.

- [ ] **Step 2: Install with "Add CMake to system PATH"**

Tick that checkbox during install.

- [ ] **Step 3: Verify**

Open a new Command Prompt (so PATH refreshes):
```cmd
cmake --version
```
Expected: `cmake version 3.27.x` or newer.

### Task 0a-W5: Install Git for Windows

- [ ] **Step 1: Download from git-scm**

URL: https://git-scm.com/download/win.

- [ ] **Step 2: Install with these recommended options**
- Use Git Bash + Git from the Windows Command Prompt
- Use OpenSSH (default)
- LF line endings (matches the Mac repo)

- [ ] **Step 3: Verify**

```cmd
git --version
```
Expected: 2.40.x or newer.

- [ ] **Step 4: Configure your identity (matches your Mac config)**

```cmd
git config --global user.name "Lavindeep Dhillon"
git config --global user.email "lavindeepdhillon@gmail.com"
```

### Task 0a-W6: Install Temurin JDK 17

The repo targets Java 17 (`build.gradle` `targetJavaVersion = 17`, `runClient` forces `JavaLanguageVersion.of(17)`, `radiance.mixins.json` `compatibilityLevel: JAVA_17`). Use JDK 17, not 21 — the Mac's JDK 21 was an Eclipse-bundled convenience, not a baseline; matching the actual build target on Windows avoids whole classes of bytecode-version surprises and matches what the MC 1.20.1 launcher ships at runtime.

- [ ] **Step 1: Download Temurin 17 LTS for Windows x64**

URL: https://adoptium.net/temurin/releases/?version=17. Pick the Windows x64 MSI installer.

- [ ] **Step 2: Install with "Set JAVA_HOME" and "Add to PATH" boxes ticked**

Defaults install to `C:\Program Files\Eclipse Adoptium\jdk-17.0.X.X-hotspot\`.

- [ ] **Step 3: Verify**

```cmd
java -version
javac -version
echo %JAVA_HOME%
```
Expected: java/javac report `17.0.x`. JAVA_HOME points at the JDK 17 install.

### Task 0a-W7: Clone the Radiance Java fork to Windows

- [ ] **Step 1: Pick a working directory**

Suggested: `C:\Users\<username>\Projects\` (mirror your Mac path roughly; avoid spaces in path).

```cmd
mkdir C:\Users\%USERNAME%\Projects
cd C:\Users\%USERNAME%\Projects
```

- [ ] **Step 2: Clone the fork**

```cmd
git clone https://github.com/lavindeep/Radiance.git Radiance-1201
cd Radiance-1201
```

(Renaming to `Radiance-1201` avoids path-space issues vs the Mac's `Radiance Backport for 1.20.1` — Windows tooling tolerates spaces but a few CMake / shell tools historically don't.)

- [ ] **Step 3: Verify the clone reflects the post-M0 Mac state**

```cmd
git log -1
git log --oneline -5
```
Expected: HEAD is the new SHA captured in M0 Step 4 (NOT `a456701` — that's the pre-M0 baseline). The most recent commits should include `feat(alpha-0): wire RendererProxy.handshake into RadianceClient init...` and `feat(alpha-0): ModuleEntry prefers disk module...`. If the clone shows `a456701` as HEAD, M0 hasn't been pushed yet — go back to the Mac and complete M0 before proceeding.

Also run the test suite as a Windows-side baseline:
```cmd
gradlew.bat test
```
Expected: 21 tests pass on Windows too. If counts disagree with the Mac, investigate before proceeding.

### Task 0a-W8: Generate JNI headers locally on Windows

- [ ] **Step 1: Wipe stale headers before regenerating**

`src/main/native/include/` is **not** tracked in git (the `.h` files are build artifacts generated by `javac -h` / Loom). However, on a Windows clone the directory may exist from a prior local `compileJava` run and contain stale headers — including ones for classes that have since been deferred. `javac -h` updates and adds headers when it sees `native` declarations, but it does **NOT** reliably delete headers for classes that no longer compile. Stale headers inside the directory are then visible to MCVR's CMake and silently shape the C++ symbol surface — exactly the kind of contamination that makes "built against current 1.20.1 Java headers" untrue. The wipe below is a belt-and-suspenders check: `build.gradle` (post-M0 commit 3) already deletes top-level `*.h` files before `compileJava`, but this explicit `rmdir` covers the recursive case too.

```cmd
cd C:\Users\%USERNAME%\Projects\Radiance-1201
rmdir /S /Q src\main\native\include
mkdir src\main\native\include
```

(If you want to keep the old headers as an audit reference rather than delete them, move them aside instead: `move src\main\native\include src\main\native\include.0b-snapshot`. Either way, the directory MCVR reads must contain only freshly-regenerated headers.)

- [ ] **Step 2: Regenerate from the current 1.20.1 Java tree**

```cmd
gradlew.bat compileJava
```

(The `.bat` form is the Windows entrypoint; the bare `gradlew` won't work in cmd.exe.)

Expected: `BUILD SUCCESSFUL` after a few minutes (cold cache pulls down Loom + 1.20.1 yarn + Fabric API). The very first run on Windows downloads ~500 MB of dependencies.

- [ ] **Step 3: Verify the regenerated header set is the active one**

```cmd
dir src\main\native\include\
type src\main\native\include\com_radiance_client_proxy_vulkan_RendererProxy.h | findstr /C:"handshake" /C:"validateAbi"
```
Expected: a `.h` file named `com_radiance_client_proxy_vulkan_RendererProxy.h` exists, and `findstr` matches lines for both `handshake` and `validateAbi` (the JNI prototypes added in 0b). Spot-check: no `.h` files corresponding to deferred classes (e.g., `com_radiance_client_proxy_world_ChunkProxy.h`) should appear, because those Java sources are in `src/deferred/java/` and are not compiled.

- [ ] **Step 4: Run the test suite as a sanity check**

```cmd
gradlew.bat test
```
Expected: 21 tests pass (8 RadianceState + 4 RadianceBufferHandle + 4 ConstantsDumpOrdinals + 3 MixinPlugin + 2 ModuleEntry). Same as the Mac post-M0. If you see only 17, your clone is at `a456701` not the post-M0 HEAD — go back to W7 Step 3.

### Task 0a-W9: Clone MCVR (canonical) alongside Radiance

The user's PEQHUB/MCVR fork has only minimal commits per Agent 2; using the canonical Minecraft-Radiance/MCVR is the cleaner base. A fork can be made later if changes need pushing.

- [ ] **Step 1: Clone with submodules**

```cmd
cd C:\Users\%USERNAME%\Projects
git clone --recurse-submodules https://github.com/Minecraft-Radiance/MCVR.git MCVR
cd MCVR
```

The `--recurse-submodules` is critical — MCVR depends on NRD, FFX, GLM, STB, VMA, GLFW as submodules. Initial clone takes 5–15 minutes (~2–3 GB).

- [ ] **Step 2: Verify the submodules populated**

```cmd
dir extern
```
Expected: subdirectories for each external dep are present and non-empty.

- [ ] **Step 3: Check the README and WindowsTraps.txt for current build instructions**

```cmd
type README.md
type WindowsTraps.txt 2>nul
```

The `WindowsTraps.txt` (per Agent 1's research) lists known Windows-specific gotchas. Read it before running cmake — it may save you hours of debugging.

### Task 0a-W10: Create the MCVR `mc/1.20.1` fork branch and implement the §4.3.1 handshake decoder

This is the C++ scope-of-work added to Part 4 in the fourth review pass — the original Part 4 assumed an unmodified MCVR could clear the gate, but `RadianceClient.performHandshake()` now runs at boot and a missing `handshake` symbol means the renderer transitions to `INIT_FAILED` instead of `BOOT_OK`. The whole point of G1+G3-recovery is `BOOT_OK`, so MCVR's `mc/1.20.1` branch must implement the decoder.

Solo + Claude Code: pair-program through this with Claude Code on the Windows box. The structured-table format (PRD §4.3.1) is a self-contained spec — you don't need to be a C++ expert to write the decoder, just careful about pointer arithmetic and JNI lifecycle. The C++ scope is roughly 100–200 lines plus a reference table. Run the steps below in the Windows session; let Claude Code handle the boilerplate (the JNIEXPORT decorations, the `GetLongArrayElements` / `ReleaseLongArrayElements` pair, the per-section validation arithmetic) and focus your own attention on the MCVR-side conventions and the embedded `mcVersionId = 12001` reference table values.

- [ ] **Step 1: Fork MCVR or create a local branch on the canonical clone**

If you intend to PR upstream, fork `Minecraft-Radiance/MCVR` to `lavindeep/MCVR` via the GitHub UI, then re-add it as a remote in your Windows MCVR clone:
```cmd
cd C:\Users\%USERNAME%\Projects\MCVR
git remote add fork https://github.com/lavindeep/MCVR.git
```
If you'd rather work locally first and decide later, just create the branch on the existing clone:
```cmd
cd C:\Users\%USERNAME%\Projects\MCVR
git checkout -b mc/1.20.1
```

- [ ] **Step 2: Locate the JNI bridge file and the existing ordinal-table conventions**

```cmd
findstr /S /I /M "RendererProxy" *.cpp *.h *.hpp
findstr /S /I /M "Java_com_radiance" *.cpp *.h *.hpp
```
Expected: a small set of `.cpp` files containing the existing `Java_com_radiance_client_proxy_vulkan_RendererProxy_*` exports. Identify where existing ordinal tables (vertex format etc.) are constructed on the C++ side — your handshake decoder needs to compare against the same data. If the project has a clear "constants per mcVersion" convention, use it; otherwise, embed an `mcVersion12001` table inline in a new `handshake.cpp`.

- [ ] **Step 3: Write `RendererProxy_handshake` and `RendererProxy_validateAbi` against PRD §4.3.1**

Implement the two exported symbols. **The decoder must validate, in order:**

1. `javaOrdinals` is non-null (return non-zero with a clear code if `GetLongArrayElements` returns null or the input array reference is null).
2. Magic equals `ORDINAL_TABLE_MAGIC` (return code 1).
3. Version equals `ORDINAL_TABLE_VERSION` (return code 2).
4. Section count equals `ORDINAL_TABLE_SECTION_COUNT` (return code 3).
5. Sections appear in the §4.3.1 fixed order (1 → 2 → 3 → 4 → 5). An out-of-order or unknown section ID returns code 4.
6. Each section's `payload-length-in-longs` is non-negative AND a multiple of 3 (one entry triple = 3 longs). Reject otherwise with code 5.
7. Cumulative cursor never overruns `len`. If a payload-length-in-longs would walk past `len`, return code 5.
8. After consuming all 5 sections, cursor must equal `len` exactly (no trailing data). Reject otherwise with code 5.
9. Per-entry `entryId`/`abiValue`/`flags` match the embedded reference table; mismatch returns code 6 (entry value) or code 7 (reserved-entry semantics violated).

Reference shape (adapt to MCVR's existing helper utilities, namespace conventions, and logging style — and add the validations above; the shape below is illustrative not complete):

```cpp
#include <jni.h>
#include <cstdint>

namespace {
constexpr int64_t ORDINAL_TABLE_MAGIC          = 0x5241445F4F524453LL; // "RAD_ORDS"
constexpr int64_t ORDINAL_TABLE_VERSION        = 1;
constexpr int64_t ORDINAL_TABLE_SECTION_COUNT  = 5;

constexpr int64_t SECTION_VERTEX_FORMATS    = 1;
constexpr int64_t SECTION_DRAW_MODES        = 2;
constexpr int64_t SECTION_INDEX_TYPES       = 3;
constexpr int64_t SECTION_GEOMETRY_TYPES    = 4;
constexpr int64_t SECTION_RAY_TRACING_FLAGS = 5;

constexpr int64_t ENTRY_ACTIVE   = 0;
constexpr int64_t ENTRY_RESERVED = 1;

// Reference table for mcVersionId == 12001 (MC 1.20.1).
// Each section is a sequence of (entryId, abiValue, flags) triples.
// Vertex-format slots 10/11/12 are reserved (intentional gaps from the 1.21->1.20 backport).
// Replace the tables below with the canonical values that match Constants.java in
// the head Java commit; the JUnit test ConstantsDumpOrdinalsTest in the Java repo
// is the fixture authority.

struct EntryTriple { int64_t entryId; int64_t abiValue; int64_t flags; };

// TODO when implementing: copy values from a recent run of
//   ./gradlew test --tests com.radiance.client.constant.ConstantsDumpOrdinalsTest --info
// or read Constants.VertexFormats/DrawModes/IndexTypes/GeometryTypes/RayTracingFlags directly.
const EntryTriple kVertexFormatsExpected[] = { /* (active entries first, then reserved 10/11/12) */ };
const EntryTriple kDrawModesExpected[]     = { /* ... */ };
const EntryTriple kIndexTypesExpected[]    = { /* ... */ };
const EntryTriple kGeometryTypesExpected[] = { /* ... */ };
const EntryTriple kRayTracingFlagsExpected[] = { /* ... */ };

int compareSection(const int64_t* javaPayload, int64_t javaPayloadLen,
                   const EntryTriple* expected, int64_t expectedCount) {
  if (javaPayloadLen != expectedCount * 3) return 5; // section-payload-length mismatch
  for (int64_t i = 0; i < expectedCount; ++i) {
    int64_t jId    = javaPayload[i * 3 + 0];
    int64_t jVal   = javaPayload[i * 3 + 1];
    int64_t jFlags = javaPayload[i * 3 + 2];
    if (jId    != expected[i].entryId)   return 6;
    if (jVal   != expected[i].abiValue)  return 6;
    if (jFlags != expected[i].flags)     return 7;
  }
  return 0;
}

int decodeAndCompare(const int64_t* table, jsize len) {
  if (len < 3) return 1;
  if (table[0] != ORDINAL_TABLE_MAGIC)       return 1;
  if (table[1] != ORDINAL_TABLE_VERSION)     return 2;
  if (table[2] != ORDINAL_TABLE_SECTION_COUNT) return 3;

  jsize cursor = 3;
  for (int64_t s = 0; s < ORDINAL_TABLE_SECTION_COUNT; ++s) {
    if (cursor + 2 > len) return 5;
    int64_t sectionId     = table[cursor];
    int64_t payloadLength = table[cursor + 1];
    if (cursor + 2 + payloadLength > len) return 5;
    const int64_t* payload = table + cursor + 2;

    int rc;
    switch (sectionId) {
      case SECTION_VERTEX_FORMATS:
        rc = compareSection(payload, payloadLength, kVertexFormatsExpected,
                            sizeof(kVertexFormatsExpected) / sizeof(EntryTriple));
        break;
      case SECTION_DRAW_MODES:
        rc = compareSection(payload, payloadLength, kDrawModesExpected,
                            sizeof(kDrawModesExpected) / sizeof(EntryTriple));
        break;
      case SECTION_INDEX_TYPES:
        rc = compareSection(payload, payloadLength, kIndexTypesExpected,
                            sizeof(kIndexTypesExpected) / sizeof(EntryTriple));
        break;
      case SECTION_GEOMETRY_TYPES:
        rc = compareSection(payload, payloadLength, kGeometryTypesExpected,
                            sizeof(kGeometryTypesExpected) / sizeof(EntryTriple));
        break;
      case SECTION_RAY_TRACING_FLAGS:
        rc = compareSection(payload, payloadLength, kRayTracingFlagsExpected,
                            sizeof(kRayTracingFlagsExpected) / sizeof(EntryTriple));
        break;
      default:
        return 4; // unknown section ID
    }
    if (rc != 0) return rc;
    cursor += 2 + payloadLength;
  }
  return 0;
}
} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_radiance_client_proxy_vulkan_RendererProxy_handshake(
    JNIEnv* env, jclass, jint mcVersionId, jlongArray javaOrdinals) {
  if (mcVersionId != 12001) return 2; // version mismatch (only 12001 supported in v1.0)
  jsize len = env->GetArrayLength(javaOrdinals);
  jlong* table = env->GetLongArrayElements(javaOrdinals, nullptr);
  int rc = decodeAndCompare(reinterpret_cast<const int64_t*>(table), len);
  env->ReleaseLongArrayElements(javaOrdinals, table, JNI_ABORT);
  return rc;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_radiance_client_proxy_vulkan_RendererProxy_validateAbi(
    JNIEnv* env, jclass cls, jint mcVersionId, jlongArray javaOrdinals) {
  return Java_com_radiance_client_proxy_vulkan_RendererProxy_handshake(
      env, cls, mcVersionId, javaOrdinals);
}
```

The reference tables (`kVertexFormatsExpected` etc.) are the part that requires care. Two pragmatic ways to populate them:

  - (a) Temporarily add `System.out.println(java.util.Arrays.toString(Constants.dumpOrdinals()))` to one of the tests in `ConstantsDumpOrdinalsTest` (Java's `--info` flag does NOT print test stdout for these — you actually have to add the print). Run `./gradlew test --tests com.radiance.client.constant.ConstantsDumpOrdinalsTest` once, capture the emitted long array from the test report (`build/reports/tests/test/.../*.html` or stdout if Gradle's `testLogging.showStandardStreams` is on), copy the values into MCVR's reference tables, then **revert the temporary `println` before committing** (and document in the C++ source which Java commit SHA the table was captured from).
  - (b) Read `Constants.java`'s enum `getValue()` returns directly and translate the structure by hand against §4.3.1. Lower risk of fixture drift, more tedious.

Either way, **bump `ORDINAL_TABLE_VERSION` on both sides if the Java enum tables ever change** — that's the breakage signal §4.3.1 commits to.

- [ ] **Step 4: Add the new file to MCVR's CMakeLists / sources list**

Whatever pattern MCVR uses to enumerate `.cpp` files (glob, explicit list, per-target `target_sources`) — add `handshake.cpp` (or wherever you put the implementation) so it links into `core.dll`. Re-run cmake configure to pick up the change.

- [ ] **Step 5: Verify the symbols will be exported**

After build (W11 below), `dumpbin /exports` should show both functions. You'll do that in W11 Step 3.

- [ ] **Step 6: Commit on the `mc/1.20.1` branch**

```cmd
git add handshake.cpp CMakeLists.txt
git commit -m "feat(handshake): implement RendererProxy.handshake and validateAbi for mcVersion 12001 per Radiance PRD §4.3.1"
```

If you forked, push to your fork; otherwise it's local-only for now. Either way, the rest of W11–W14 builds against this branch.

### Task 0a-W11: Configure and build MCVR's `mc/1.20.1` branch

- [ ] **Step 1: From MCVR's directory, run cmake configure**

Open the **"x64 Native Tools Command Prompt for VS 2022"** (not the regular cmd.exe — VS's variant sets `LIB`/`INCLUDE` env vars MSVC needs). Make sure you're on the `mc/1.20.1` branch.

```cmd
cd C:\Users\%USERNAME%\Projects\MCVR
git status   # confirm branch is mc/1.20.1
cmake -S . -B build -G "Visual Studio 17 2022" -A x64 ^
  -DCMAKE_BUILD_TYPE=Release ^
  -DJAVA_PROJECT_ROOT_DIR="C:/Users/%USERNAME%/Projects/Radiance-1201" ^
  -DUSE_AMD=ON -DMCVR_ENABLE_NRD=ON -DMCVR_ENABLE_FFX_UPSCALER=ON
```

(The `^` are line continuations in cmd. Use forward slashes in the Java path — CMake prefers them on Windows.)

Expected: cmake configures without error, prints `Configuring done` and `Generating done`. If a dep is missing (e.g., Vulkan SDK not found), the error message names which dep — install it and re-run.

- [ ] **Step 2: Run the build**

```cmd
cmake --build build --config Release --parallel
```

Expected: a long build (~10–30 minutes for first run, depending on CPU cores). At the end: `Build succeeded.` with the count of warnings (warnings are normal — MCVR pulls in third-party libs that emit them).

- [ ] **Step 3: Verify the handshake symbol is exported**

```cmd
dumpbin /exports build\Release\core.dll | findstr handshake
dumpbin /exports build\Release\core.dll | findstr validateAbi
```
Expected: both commands print at least one matching line (the JNI-mangled export `Java_com_radiance_client_proxy_vulkan_RendererProxy_handshake` and `..._validateAbi`). If either is missing, the new `handshake.cpp` was not linked — return to W10 Step 4 and check the `CMakeLists.txt` change.

- [ ] **Step 4: If the build fails**

The first failure is almost always an environment/dep issue. Categorize the error:
- **"Cannot find Vulkan SDK"** — `VULKAN_SDK` env var is unset; restart the cmd shell after VS install.
- **"Cannot find FT_Face" / freetype** — MCVR's NRD or FFX submodule may need its own deps; check the submodule's README.
- **"unresolved external symbol"** at link time — usually a CMake configuration problem (missing link library); the error names the symbol.
- **"file not found: vulkan.h"** — `INCLUDE` path doesn't have Vulkan SDK; restart shell or `set INCLUDE=%VULKAN_SDK%\Include;%INCLUDE%`.
- **MSVC out-of-memory (rare)** — drop `--parallel` to single-thread.
- **Compile error in handshake.cpp** — likely a fixture-table typo or missing JNI include; fix locally and rebuild.

**Stale-header-wipe consequence (specific to this checkpoint):** W8 deleted every header under `src/main/native/include/` and only headers for currently-compiling Java classes were regenerated. That means headers for the deferred proxy classes — `BufferProxy`, `ChunkProxy`, `EntityProxy`, and any other class moved to `src/deferred/java/` in 0b — no longer exist on disk. **MCVR may `#include` those headers.** If the build error is `cannot open include file 'com_radiance_client_proxy_world_ChunkProxy.h'` (or any other deferred-class header), branch:

- **(a) MCVR's missing-header `#include`s are in code paths gated by preprocessor flags.** Disable the relevant CMake option and rebuild. Acceptable.
- **(b) MCVR's missing-header `#include`s are unconditional.** This is a hard incompatibility between MCVR and the current 1.20.1 Java surface. **STOP** and re-plan — do NOT restore stale headers from a snapshot, do NOT temporarily un-defer the corresponding Java classes to make headers reappear, and do NOT comment out the `#include` in MCVR. Any of those would mean the resulting `core.dll` was built against headers that don't match the active Java tree, which would invalidate the G1+G3-recovery claim ("`core.dll` was built against the current 1.20.1 Java headers"). The correct re-plan option: extend Checkpoint 0c+A to also implement a stub `RadianceBufferHandle`-consuming buffer adapter on the C++ side (rough analogue of Checkpoint C-prep work, but on the C++ side), paired through with Claude Code.

If you can't unstick within 4 hours of focused debugging on a non-stale-header failure mode, save the full error log, hand it back to Claude Code in your Windows session, and iterate; do NOT mark the task complete until the build succeeds and `dumpbin /exports` shows both symbols.

### Task 0a-W12: Install MCVR's outputs into the Java tree

**Canonical output location for native artifacts:** `natives/windows/` at the repo root, NOT `src/main/resources/`. `build.gradle`'s `processResources` task copies `natives/${platform}/` into the jar at build time (per CLAUDE.md). `src/main/resources/` is reserved for static Java resources (mixin JSON, accesswidener, fabric.mod.json, modules YAML). Existing Streamline DLLs sit in `src/main/resources/` for historical reasons; leave them alone for now (cleanup is a future task), but DO NOT add new native build outputs there. Future MCVR `cmake --install` rules should target `natives/windows/`; if MCVR's current install rule writes to `src/main/resources/`, treat that as a bug to fix in MCVR rather than a layout to perpetuate.

- [ ] **Step 1: Run cmake install**

```cmd
cmake --install build --config Release
```

Expected: copies `core.dll` (and possibly `core.lib`, Streamline DLLs, and the SPIR-V `shaders/` tree) into wherever MCVR's current install rule targets. Inspect MCVR's `CMakeLists.txt` `install(...)` rules to know the exact destination — likely `${JAVA_PROJECT_ROOT_DIR}/src/main/resources/` per the MCVR README (which is what we want to migrate away from but is the current reality).

- [ ] **Step 2: Move install outputs to canonical location, then verify**

If MCVR installed to `src/main/resources/`, move the build outputs to `natives/windows/`:

```cmd
mkdir C:\Users\%USERNAME%\Projects\Radiance-1201\natives\windows 2>nul
move C:\Users\%USERNAME%\Projects\Radiance-1201\src\main\resources\core.dll C:\Users\%USERNAME%\Projects\Radiance-1201\natives\windows\
if exist C:\Users\%USERNAME%\Projects\Radiance-1201\src\main\resources\core.lib move C:\Users\%USERNAME%\Projects\Radiance-1201\src\main\resources\core.lib C:\Users\%USERNAME%\Projects\Radiance-1201\natives\windows\
```

(Leave Streamline DLLs in `src/main/resources/` for now — they were already there.)

Then verify:

```cmd
dir C:\Users\%USERNAME%\Projects\Radiance-1201\natives\windows\core.*
dir C:\Users\%USERNAME%\Projects\Radiance-1201\src\main\resources\sl.*
dir C:\Users\%USERNAME%\Projects\Radiance-1201\src\main\resources\shaders
dir C:\Users\%USERNAME%\Projects\Radiance-1201\src\main\resources\modules
```

Expected: `natives/windows/core.dll` exists with a recent timestamp. `core.lib` may or may not exist (per OQ-07 it's a link-time artifact and may be unnecessary — note its presence/absence in W14). Streamline DLLs are still in `src/main/resources/`.

**Critical: `shaders/` and `modules/` are required for W13 / G1+G3-recovery.** `RadianceClient.onInitializeClient` extracts both via `copyFolderFromResource`. If either is missing, the current code sets `RadianceState.RENDERER_DISABLED` rather than `BOOT_OK` — the JVM no longer crashes, but **the gate is not cleared** because the alpha-0 pass criterion is `BOOT_OK`, not "main menu reached." There is therefore no "tolerate missing shaders" escape hatch that clears G1+G3-recovery; the only acceptable path is to make sure both directories are bundled.

  - **Fix MCVR install/resource packaging.** Inspect MCVR's `CMakeLists.txt` for the install step; there is most likely an `install(DIRECTORY shaders/ DESTINATION ...)` rule. Verify its destination, fix it if wrong, re-run `cmake --install`. SPIR-V `.spv` files must end up at `src/main/resources/shaders/` so `processResources` packages them into the jar. The `modules/` directory should already be present under `src/main/resources/modules/` from the Radiance repo itself; verify it's there.

If MCVR cannot be configured to install `shaders/` to `src/main/resources/shaders/` cleanly, that is a real MCVR-side bug to fix — not a Java-side compatibility shim. Earlier drafts of this plan suggested a "committed alpha-0 compatibility patch" as an alternative; **that path is not viable** because the resulting boot ends in `RENDERER_DISABLED`, and `RENDERER_DISABLED ≠ BOOT_OK`. Do NOT rely on local-only / branch-only patches either: a "yes it boots, but only on my feature branch" outcome is not the gate.

### Task 0a-W13: Build the Radiance jar on Windows and smoke-test load

- [ ] **Step 1: Re-run gradle build with the new natives present**

```cmd
cd C:\Users\%USERNAME%\Projects\Radiance-1201
gradlew.bat build -Pplatform=windows
```

Expected: `BUILD SUCCESSFUL`. The output jar is at `build\libs\Radiance-0.1.3-alpha-fabric-1.20.1-windows.jar`. Its size should be larger than the Mac-built version (the Mac build had no `core.dll`; this one bundles it). With W12's canonical-location move, `build.gradle`'s `processResources` reads from `natives/windows/` and copies into the jar root.

- [ ] **Step 2: Verify jar contents before smoke-testing (per-artifact checks)**

A single `findstr` with alternation only proves *something* matched. Verify each artifact independently. **Required-vs-optional split matches FR-03:**

```cmd
set JAR=build\libs\Radiance-0.1.3-alpha-fabric-1.20.1-windows.jar

REM Required (W13 fails if any is blank):
jar tf %JAR% | findstr /C:"core.dll"
jar tf %JAR% | findstr /C:"modules/ray_tracing.yaml"
jar tf %JAR% | findstr /C:"modules/dlss.yaml"
jar tf %JAR% | findstr /C:"modules/tone_mapping.yaml"
jar tf %JAR% | findstr /C:"modules/post_render.yaml"
jar tf %JAR% | findstr /C:"shaders/"

REM Optional (blank output is acceptable; absence only produces a WARN at runtime):
jar tf %JAR% | findstr /C:"core.lib"
jar tf %JAR% | findstr /C:"sl.interposer.dll"
jar tf %JAR% | findstr /C:"sl.common.dll"
jar tf %JAR% | findstr /C:"sl.reflex.dll"
jar tf %JAR% | findstr /C:"sl.pcl.dll"
jar tf %JAR% | findstr /C:"NvLowLatencyVk.dll"
```

Expected: every **Required** line prints at least one match. A blank line for any required artifact means it's missing from the jar; **do not run the smoke test in that state** — re-check W12 (especially the `natives/windows/` move) and `processResources` in `build.gradle`. Common failures for required artifacts:

- Empty `shaders/` line → MCVR's CMake install didn't emit shaders into the resource tree W12 verified. Fix MCVR's `install()` rule. **Required for G1+G3-recovery** — `shaders/` absence at runtime sets `RENDERER_DISABLED`, not `BOOT_OK`, which fails the gate.
- Empty `core.dll` line → `natives/windows/core.dll` is missing or the gradle `platform` property wasn't passed (`gradlew.bat build -Pplatform=windows`).
- Empty `modules/*.yaml` lines → modules were not bundled. They live under `src/main/resources/modules/` and should be picked up by `processResources` automatically; check they're still present in the source tree.

Optional-artifact absences are informational. They will produce WARN logs from `copyOptionalFileFromResource` at runtime but do not affect W13's pass/fail.

- [ ] **Step 3a: Wipe the actual Loom run directory's Radiance state**

`build.gradle` configures `runClient` with `--gameDir ../mc-test/instance` (relative to the project root). So Radiance state lives at `..\mc-test\instance\radiance\`, NOT `run\.minecraft\radiance\` (an earlier draft of this plan got the path wrong). `Options.readOptions()` takes a different code path on first launch (no `options.properties` present → defaults applied in Java without invoking many native setters) versus a dirty re-run (existing options drive a chain of `nativeSet*` calls). To make the smoke test deterministic and reproducible across machines, run the Radiance state from a known clean slate:

```cmd
rmdir /S /Q ..\mc-test\instance\radiance
```

If the directory does not exist, the command is a no-op; that's fine. If you've customized the `--gameDir` argument locally, wipe whatever absolute path that points at instead. **Wiping `radiance\` does NOT destroy world saves** (those live under `..\mc-test\instance\saves\`) — only Radiance-owned config (`options.properties`, `pipeline.yaml`) and extracted natives.

**The W13 smoke test is defined against this clean state.** A "works on my machine" run that depended on stale options or a half-extracted Radiance directory is not a passing run — re-do this step before reporting W13 cleared.

- [ ] **Step 3b: Smoke test — `runClient` reaches main menu via vanilla GL with `RadianceState.BOOT_OK`**

```cmd
gradlew.bat runClient
```

**Important:** `RadianceClient.onInitializeClient` calls `RendererProxy.handshake(MC_VERSION_ID, Constants.dumpOrdinals())` from `performHandshake()` (line 153). `LinkageError` is caught gracefully → `INIT_FAILED`, but the W13 pass criterion is **`BOOT_OK`, not just "client reached main menu."** A handshake graceful-failure result indicates an MCVR build problem (missing symbol or non-zero return code); it does not clear G1+G3-recovery.

Expected outcomes (each must hold for W13 to pass):

1. Loom downloads MC 1.20.1 client jar + assets (first run only).
2. The Minecraft client launches.
3. `RadianceClient.onInitializeClient` runs.
4. `latest.log` shows `[radiance] System.load succeeded for <path>\core.dll` with no OS-level error.
5. **`latest.log` shows `[radiance] RendererProxy.handshake(12001, javaOrdinals.length=N) returned 0`** (the `0` is the critical assertion — it means the C++ decoder accepted the structured table per §4.3.1). N is the total ordinal-table length in longs (will be in the dozens — matches `Constants.dumpOrdinals().length` from Java).
6. After the zero-return log line, the current code calls `RadianceState.set(RadianceState.BOOT_OK)`. **Note: there is no explicit "transition: BOOT_OK" log line in the current code.** Verify `BOOT_OK` either by (i) inspection — confirm the handshake-returned-0 log line is the last alpha-0 transition signal, no `INIT_FAILED` log appears later, and the resource-tracker mixins do not log "tracking disabled"; or (ii) wiring a one-line transition log in `RadianceState.set(...)` as a small follow-up commit if you want explicit confirmation. Either is acceptable; just don't grep for a transition log that doesn't exist.
7. The existing folder-extraction calls (`copyFolderFromResource("shaders")`, `copyFolderFromResource("modules")`) complete without exception AND the post-extract state is `BOOT_OK` (not `RENDERER_DISABLED`). The only way this passes is (i) `shaders/` and `modules/` were packaged into the jar (verified in Step 2's required-checks block) and the copy succeeds normally. **There is no longer a "committed compatibility patch" alternative — that path ends in `RENDERER_DISABLED`, which fails the gate.** Local-only / branch-only / try-catch-in-your-editor patches similarly do NOT count.
8. Other native calls reached during init (anything `RadianceClient` and pipeline/module init invoke at the head commit) complete without `UnsatisfiedLinkError`. The exact set: read `RadianceClient` and `Pipeline` at the head commit before running the smoke test; the code is the source of truth.
9. The four resource-tracker mixins (from the alpha-0 allowlist) attach without runtime errors. They check `RadianceState.isResourceTrackingEnabled()` which is true once `BOOT_OK` is set.
10. Main menu renders normally via vanilla OpenGL — no renderer mixins are active in alpha-0, so there is no Vulkan path being driven yet.
11. Optional natives — `core.lib`, the 5 Streamline DLLs — absent or present, doesn't matter; the `copyOptionalFileFromResource` path logs WARN if they're missing and continues. Per FR-03 (PRD §6).
12. DLSS DLLs absent → `DlssMissingScreen` displays cleanly on first frame. DLSS DLLs present → no `DlssMissingScreen`. Either is acceptable.

**This is the W13 pass criterion (G1+G3-recovery from PRD §10):** `core.dll` was built on the user's machine from MCVR's `mc/1.20.1` branch, loaded via `System.load`, the C++ `RendererProxy_handshake` symbol returned `0` for the structured §4.3.1 table at `mcVersionId == 12001`, every other native invoked during alpha-0 boot completed without `UnsatisfiedLinkError`, `RadianceState` reached `BOOT_OK`, and Java boot reached the main menu via vanilla GL. Anything less is a partial result — record it but don't claim the gate clear.

Common failure modes and triage:

- **`latest.log` shows `RendererProxy.handshake could not be called. Renderer disabled.`** (LinkageError caught): the C++ `handshake` symbol is missing. Re-run `dumpbin /exports build\Release\core.dll | findstr handshake` from W11 Step 3 — if it prints nothing, the new `handshake.cpp` wasn't linked or the export wasn't `extern "C" JNIEXPORT`. Fix in MCVR and rebuild.
- **`latest.log` shows `Radiance: native renderer ABI mismatch (code=N).`** with non-zero code: the structured table didn't match. Cross-reference the code against §4.3.1's recommended convention (1=magic, 2=version, 3=section count, 4=unknown section, 5=payload-length, 6=entry value, 7=reserved-entry semantics) to localize. Most common cause: stale C++ reference table from a different Java commit. Re-extract the fixture from `ConstantsDumpOrdinalsTest` and rebuild MCVR.
- **`UnsatisfiedLinkError` for a non-handshake native invoked during init**: the C++ side did not export that symbol. Inspect `dumpbin /exports core.dll | findstr <symbol>` and confirm there's a matching `JNIEXPORT` in MCVR. The exception message names the offending Java method.
- **`NoSuchFileException` on `shaders/`**: the `shaders/` tree did not get packaged into the jar. Return to W12 and pursue option (a) — fix MCVR's `install()` rule so SPIR-V `.spv` files end up at `src/main/resources/shaders/` — or option (b), commit a real alpha-0 compatibility change on `main`. Do NOT apply a local try/catch and re-run; that would not honestly clear the gate.
- **JVM segfault with `hs_err_pid*.log`**: C++-side bug (likely in the handshake decoder — out-of-bounds payload read, missing null check, etc.). Capture all artifacts and pair-debug with Claude Code; the `hs_err_pid*.log` stack trace usually points right at the failing line.
- **MC client never reaches main menu, no Java exception**: Loom or Fabric mismatch; verify `gradlew.bat compileJava` worked in W8.

If you cannot pass W13 within 4 hours of focused debugging on a specific failure mode, capture the full `latest.log` + any `hs_err_pid*.log` and hand them to Claude Code for triage in your Windows session.

- [ ] **Step 3c: Stop the client cleanly**

Close the Minecraft window. Verify gradle exits without error.

### Task 0a-W14: Document the working setup in BUILD-WINDOWS.md

This makes the work reproducible — future-you can replicate the build without re-discovering every gotcha, and a future contributor (if you ever decide to PR upstream) can follow it cold.

**Files:**
- Create: `BUILD-WINDOWS.md` at the repo root.

- [ ] **Step 1: Author the doc**

Open the Radiance-1201 repo on Windows. Create `BUILD-WINDOWS.md` with this content (adjust paths/versions to match what you actually installed):

```markdown
# Building Radiance + MCVR on Windows

This is the canonical Windows build setup for the 1.20.1 backport. Tested on
Windows <version> with Visual Studio 2022 Build Tools <version>.

## Prerequisites

- Windows 10 (1903+) or Windows 11 x64.
- Visual Studio 2022 Build Tools with the "Desktop development with C++" workload.
  - MSVC v143, Windows 11 SDK 10.0.22621+.
- Vulkan SDK 1.3.x from https://vulkan.lunarg.com/sdk/home (sets `VULKAN_SDK` env var).
- CMake 3.27+ from https://cmake.org/download/ (in PATH).
- Git for Windows from https://git-scm.com/download/win.
- Temurin JDK 17 from https://adoptium.net/temurin/releases/?version=17 (sets `JAVA_HOME`). The repo targets Java 17; do NOT use JDK 21.

GPU: NVIDIA RTX-class (Turing+, 2018+) OR AMD RDNA2 (RX 6000+) OR Intel Arc.
Pre-RTX hardware will not run the result (RT extensions required).

## Build steps

1. Clone Radiance:
   `git clone https://github.com/lavindeep/Radiance.git Radiance-1201`

2. Clone MCVR alongside it (canonical, with submodules):
   `git clone --recurse-submodules https://github.com/Minecraft-Radiance/MCVR.git MCVR`

3. Generate JNI headers (from Radiance-1201):
   `gradlew.bat compileJava`

4. Configure MCVR (from MCVR, in **x64 Native Tools Command Prompt for VS 2022**):
   ```
   cmake -S . -B build -G "Visual Studio 17 2022" -A x64 ^
     -DCMAKE_BUILD_TYPE=Release ^
     -DJAVA_PROJECT_ROOT_DIR="C:/path/to/Radiance-1201" ^
     -DUSE_AMD=ON -DMCVR_ENABLE_NRD=ON -DMCVR_ENABLE_FFX_UPSCALER=ON
   ```

5. Build MCVR:
   `cmake --build build --config Release --parallel`

6. Install MCVR. By default, MCVR's current install rule may write to `Radiance-1201/src/main/resources/`. The canonical Radiance layout is `natives/<platform>/` for native build outputs (`build.gradle`'s `processResources` reads from there). After install, move outputs to the canonical location. **The `move` commands below assume you ran `cmake --install` from inside the MCVR directory; the relative paths walk one level up into the sibling Radiance-1201 checkout. Use absolute paths if your layout differs:**
   ```
   cmake --install build --config Release
   set RADIANCE_REPO=C:\Users\%USERNAME%\Projects\Radiance-1201
   mkdir "%RADIANCE_REPO%\natives\windows" 2>nul
   move "%RADIANCE_REPO%\src\main\resources\core.dll" "%RADIANCE_REPO%\natives\windows\"
   if exist "%RADIANCE_REPO%\src\main\resources\core.lib" move "%RADIANCE_REPO%\src\main\resources\core.lib" "%RADIANCE_REPO%\natives\windows\"
   ```
   Streamline DLLs already live in `src/main/resources/` for historical reasons; leave them. SPIR-V `shaders/` should land in `src/main/resources/shaders/` (resource path, not native).

7. Build the jar (from Radiance-1201):
   `gradlew.bat build -Pplatform=windows`

8. Run the client (from Radiance-1201):
   `gradlew.bat runClient`

## Known issues encountered during this setup

<TODO: list any that came up. Common ones from Agent 1's research:
- MSVC redistributable mismatch (see upstream README's Windows Fix section)
- Vulkan SDK not on PATH after install — restart shell
- WindowsTraps.txt in MCVR repo lists project-specific gotchas
>

## After this point

Per the PRD, Checkpoint 0c implements the new JNI symbols (handshake,
validateAbi, buffer adapter consumption) on a `mc/1.20.1` branch of the MCVR
fork. This BUILD-WINDOWS.md describes how to build the unmodified MCVR;
adapt the cmake invocation to point at the fork branch when 0c work begins.
```

- [ ] **Step 2: Commit the doc**

```cmd
cd C:\Users\%USERNAME%\Projects\Radiance-1201
git add BUILD-WINDOWS.md
git commit -m "docs: BUILD-WINDOWS.md — canonical Windows build setup for Radiance + MCVR"
git push origin main
```

(Pushing to `lavindeep/Radiance` main is fine — the doc is purely additive and the user owns the fork.)

- [ ] **Step 3: Pull on the Mac to keep both machines in sync**

```bash
# On the Mac:
cd "/Users/lavin/Projects/Radiance Backport for 1.20.1"
git pull origin main
```

### Task 0a-W15: Mark G1+G3-recovery cleared in the PRD

This is the bookkeeping that closes OQ-01.

**Files:**
- Modify: `/Users/lavin/.claude-anthropic/plans/ultraplan-terminated-remote-session-shiny-kettle.md`

- [ ] **Step 1: Update OQ-01 in PRD §13**

Find the `OQ-01` row in the Open Questions table. Change the "Resolved by" cell to:
```
RESOLVED 2026-XX-XX via Checkpoint 0c+A. Path: user implemented the §4.3.1 handshake decoder on MCVR's mc/1.20.1 branch and built core.dll locally on Windows, paired with Claude Code throughout. BUILD-WINDOWS.md committed to the Radiance repo at <new-head-sha-after-W14>. MCVR fork branch: <fork-URL>/tree/mc/1.20.1. No OSS outreach was conducted (user chose solo + Claude Code; the Outreach workstream was removed from this plan).
```

(Plug in the actual date, head SHA, fork URL, and GitHub issue URL.)

- [ ] **Step 2: Mark G1+G3-recovery as cleared in PRD §10**

Find the `G1+G3-recovery` row in the Engineering Risk Gates table. Change "Cleared by" to:
```
Implementation Checkpoint 0c+A (Part 4). CLEARED 2026-XX-XX. core.dll built from MCVR mc/1.20.1 branch; RendererProxy.handshake returned 0 against current Java head; RadianceState reached BOOT_OK; main menu renders via vanilla GL.
```

- [ ] **Step 3: Add a status note to PRD §4.2**

After the "If the engineer cannot complete 0a in ~2 weeks of part-time work, slow down and pair-program through the C++ side with Claude Code rather than rushing it..." line (current text after the sixth-pass rewrite that retired Path B), add:

```
**Status (2026-XX-XX): user implemented the §4.3.1 handshake decoder on MCVR's mc/1.20.1 branch and built core.dll locally on Windows, paired with Claude Code throughout. The Java alpha-0 boot path reaches RadianceState.BOOT_OK. See Part 4 status note below.**
```

- [ ] **Step 4: Add Part 4 status note at the bottom of Part 4**

Append to Part 4 of this plan file:

```markdown
## Part 4 — Status Note: Checkpoint 0c+A SHIPPED (2026-XX-XX)

MCVR's mc/1.20.1 branch implements RendererProxy_handshake and RendererProxy_validateAbi
per PRD §4.3.1. core.dll built on user's Windows machine via the BUILD-WINDOWS.md flow.
Java client launches; System.load succeeds; RendererProxy.handshake(12001, ordinals)
returns 0; RadianceState transitions UNINITIALIZED -> BOOT_OK; main menu renders via
vanilla GL. PRD G1+G3-recovery cleared. OQ-01 resolved (user built MCVR themselves with Claude Code; OSS-collaborator outreach was retired before being attempted).

Next planning target: Checkpoint B (alpha-1 boot-path mixins).
```

- [ ] **Step 5: Update the plan file (no commit — file lives outside the repo)**

The plan file at `/Users/lavin/.claude-anthropic/plans/ultraplan-terminated-remote-session-shiny-kettle.md` is **outside** the Radiance repo working tree. It is the user's personal plans directory under `~/.claude-anthropic/`, not a tracked file. Do NOT `git add` it — it cannot be committed to the Radiance repo (and you'd just get a "did not match any files" error or accidentally try to add a path outside the worktree).

Two acceptable outcomes:

- (a) **Local-only bookkeeping (default):** edit the plan file in place via your editor or the Claude Code `Edit` tool. The status note lives in the plan file and is implicitly versioned by the `.claude-anthropic/` directory if you choose to back that up.
- (b) **Repo-local doc (alternative):** if you want the status visible to future contributors who clone the Radiance repo, copy the Part 1 PRD section into `docs/PRD.md` at the repo root and the Part 4 status note into `docs/CHECKPOINTS.md`, then commit those:
  ```cmd
  cd C:\Users\%USERNAME%\Projects\Radiance-1201
  mkdir docs
  REM ... author docs/PRD.md and docs/CHECKPOINTS.md ...
  git add docs/
  git commit -m "docs: capture PRD + Checkpoint 0a/0b status in repo"
  git push origin main
  ```

The plan file under `~/.claude-anthropic/plans/` and the repo-local `docs/` are two different artifacts serving two different audiences — pick (a) if status only matters to you, (b) if it matters to anyone who clones the repo. They are not mutually exclusive.

---

## Self-Review

**1. Spec coverage (sixth pass — Checkpoint 0c+A, outreach removed).** OQ-01 strands after the 2026-05-11 sixth review: Mac-side prep (M0 commits dirty alpha-0 wiring; M1 obsolete; M2 optional gh re-auth; M3 optional Java) and sequential Windows build (W1–W14, plus W15 bookkeeping). The big-change history: third pass added W10 NEW for the §4.3.1 handshake decoder (`RadianceClient.performHandshake()` is wired into init now, so the C++ decoder is mandatory for `BOOT_OK`); sixth pass removes the OSS-collaborator outreach workstream at the user's request and reframes the C++ work as solo + Claude Code throughout. W11 builds the fork branch and verifies `dumpbin /exports` shows the new symbols. W13 asserts `BOOT_OK` (not just "main menu reached"), wipes the correct Loom directory (`..\mc-test\instance\radiance` per `build.gradle`'s `--gameDir`), and triages handshake failure modes by §4.3.1 return code. PRD G1+G3-recovery (PRD §10) is the explicit pass criterion at W13. PRD §13 OQ-01 bookkeeping is W15.

**Cross-section consistency (fourth-pass clean-up):**
- PRD §4.3 + §4.3.1 (NEW): structured ordinal table format locked. Header (magic/version/section-count) + 5 sections in fixed order + entry triples with `ENTRY_ACTIVE` / `ENTRY_RESERVED` flags + reserved vertex-format slots `{10,11,12}` + recommended return-code conventions for MCVR. Becomes the C++ decoder spec.
- PRD §4.3 (revised): handshake call site documented (`RadianceClient.performHandshake()` invoked from `initializeNativeRenderer`); `LinkageError` graceful catch documented.
- PRD §6 FR-03: `core.lib` and the 5 Streamline DLLs marked optional via `copyOptionalFileFromResource`; alpha-0 release validation no longer requires their presence (matches code direction confirmed by user).
- PRD §6 FR-04, FR-05: rewritten around the actual `performHandshake()` flow — `BOOT_OK` on zero, `INIT_FAILED` on either non-zero return or `LinkageError`.
- PRD §10: G1, G1+G3-recovery, and G3 all marked superseded by G1+G3-recovery. G2 narrowed to Java-side only (MCVR-side build folded into G1+G3-recovery).
- PRD §13 OQ-01: resolved-by points at Checkpoint 0c+A.
- Part 4 header / Goal / Architecture: rewritten around 0c+A scope.
- Part 4 M0 (NEW): commits dirty alpha-0 wiring as new baseline; pushes to GitHub before clone.
- Part 4 W7: clone instructions point at the new HEAD (post-M0); cross-references the captured SHA.
- Part 4 W10 (NEW): C++ handshake decoder implementation with reference shape and §4.3.1 fixture-extraction guidance.
- Part 4 W11: configures + builds the `mc/1.20.1` branch; `dumpbin /exports` confirms symbols.
- Part 4 W13: wipe target corrected to `..\mc-test\instance\radiance`; expected outcomes assert `BOOT_OK` and a zero handshake return; failure modes triaged by §4.3.1 return codes.
- Part 4 Outreach workstream: deleted in sixth pass at user's request. Prior rendered issue/Discord templates remain in git history if needed later.

**2. Placeholder scan.** Intentional placeholders: `%RADIANCE_REPO%` (set inline in the BUILD-WINDOWS.md `move` commands), and the date / fork URL in W15 (known only at completion). No accidental TODOs in the executable steps; the C++ reference tables in W10 Step 3 are intentionally `TODO`-marked because they are the user's fixture-extraction work, with two acceptable extraction paths documented inline.

**3. Type / dependency consistency.** New types introduced in §4.3.1 (magic/version/section/entry layout) are consistent across PRD §4.3.1, the Java implementation in `Constants.dumpOrdinals()`, and the W10 reference C++ shape. JDK 17 consistent across Windows W6, Mac M3 (with Eclipse JDK 21 fallback flagged), and BUILD-WINDOWS.md prerequisites. Test count `21` consistent across M0 Step 5, M3 Step 3, and W7 Step 3.

**4. Realistic effort (sixth-pass revised).** 1 active Mac-prep task (M0 ~15 min commit/push) + 2 optional Mac-prep tasks (M2 gh re-auth ~5 min if needed for incidental work; M3 optional Java ~5 min) + 14 Windows tasks + 1 bookkeeping task. Mac prep is ~15–20 min total. Windows is **30–60 hours** because MCVR-side handshake-decoder implementation is in scope; pairing with Claude Code on the Windows box accelerates the C++ boilerplate but doesn't change the calendar floor much. Total calendar time: 2–4 weeks part-time, 1.5–2 weeks full-time. The Outreach workstream has been removed at the user's request — no parallel waiting on responses.

**5. Strategic fallback flagged but not in scope.** If 0c+A stalls past 4 weeks of focused effort, the user can pivot to forking VulkanMod (drop RT entirely, ship Vulkan-rasterization-only). That conversation re-opens if needed; outreach is no longer a fallback option since it was retired.

---

## Execution Handoff

Plan complete. Two execution options:

**1. User-driven + Claude Code (recommended for this plan)** — The user does M0 + optional M2/M3 on the Mac, then drives W1–W14 on the Windows box with Claude Code as a pair-programming assistant. Bite-sized steps means each task is checkable independently. Outreach has been removed.

**2. Subagent-driven (only M0)** — M0 is automatable from a Mac claude-code session (this session already did it). The remaining tasks are physically on the Windows box and benefit most from interactive pairing rather than subagent dispatch.

Recommended sequence:
1. **First — DONE in this session** — M0 (commit + push the dirty alpha-0 wiring). HEAD = `095273e` as of M0 Step 4.
2. **Optional** — M2 (`gh` re-auth) and M3 (Mac JDK 17) if relevant to incidental work. Neither is on the critical path now that outreach is dropped.
3. **Second** — W1–W6 (Windows toolchain installs: VS 2022 Build Tools, Vulkan SDK, CMake, Git for Windows, Temurin JDK 17). Install Claude Code on Windows too if it isn't already there.
4. **Third** — W7–W9 (clone Radiance at HEAD `095273e` or later; clone MCVR with submodules).
5. **Fourth** — **W10 (the C++ work)** — create the `mc/1.20.1` branch and implement the §4.3.1 handshake decoder. Solo + Claude Code: have Claude Code generate the JNIEXPORT boilerplate, the per-section validation arithmetic, and the test fixture extraction from `ConstantsDumpOrdinalsTest`; you focus on the MCVR-side conventions and the reference-table values.
6. **Fifth** — W11 (build MCVR, verify symbol exports with `dumpbin /exports`), W12 (install + relocate to `natives/windows/`).
7. **Sixth** — W13 with the per-artifact jar verification, the corrected wipe-target (`..\mc-test\instance\radiance`), and the `BOOT_OK`-asserting smoke test.
8. **Seventh** — W14 (BUILD-WINDOWS.md) and W15 (PRD bookkeeping for G1+G3-recovery).
9. Report back when W13 succeeds, at which point Checkpoint B (alpha-1 boot-path mixins) becomes the next planning target.
