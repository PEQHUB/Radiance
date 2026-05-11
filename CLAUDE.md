# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Radiance is a Fabric mod for Minecraft Java Edition that **completely replaces the vanilla OpenGL renderer with a Vulkan/ray-tracing C++ renderer**. The Java code in this repo is the thin Fabric/JNI side; the actual renderer lives in a separate repository: [MCVR](https://github.com/Minecraft-Radiance/MCVR). Java versions are produced as JNI native libraries (`core.dll` / `libcore.so`) and bundled into the jar at build time.

Despite the working-directory name ("Radiance Backport for 1.20.1"), `gradle.properties` is currently set to **MC 1.21.4 / Yarn / Fabric Loader 0.18.3 / Fabric API 0.119.4 / Java 21**. Update `gradle.properties` if you actually intend to target a different MC version — do not infer the target from the folder name.

## Common commands

```bash
# Generate JNI headers into src/main/native/include — required before building MCVR
./gradlew compileJava

# Build the mod jar (Windows natives by default)
./gradlew build
./gradlew build -Pplatform=linux           # Linux jar
./gradlew buildAllPlatforms                # both — only works on Windows (uses gradlew.bat)

# Run a dev client. Game dir is hard-coded to ../mc-test/instance, window 1920x1080,
# and runtime is forced to Java 21 (LWJGL 3.3.3 is incompatible with Java 25).
./gradlew runClient
```

There are no unit tests in this repo. The native renderer is built separately via CMake in the MCVR repo (see README "Build" section and `.github/workflows/build-linux.yml` for the exact commands and required deps — Vulkan SDK + glslang, plus several X11/Wayland libs on Linux).

### Native artifact flow (important)

Build expects native libraries to be present under `natives/<platform>/` — `processResources` copies them into the jar from there:

- Windows: `natives/windows/core.dll`, `natives/windows/core.lib`
- Linux: `natives/linux/libcore.so`

These are produced by building MCVR with `-DJAVA_PROJECT_ROOT_DIR=<this repo>`, which writes them into `src/main/resources/`. The CI workflow (`.github/workflows/build-linux.yml`) is the authoritative reference: after MCVR's `cmake --install`, it **moves** `core.dll`/`core.lib`/`libcore.so` from `src/main/resources/` into `natives/<platform>/` before `./gradlew build`. Compiled SPIR-V shaders are also expected under `src/main/resources/shaders/`.

DLSS DLLs are **never** bundled — they are downloaded by the user into `.minecraft/radiance/` per the README. The mod logs a warning and shows `DlssMissingScreen` if absent.

## Architecture

### Entry points

- `com.radiance.Radiance` — common `ModInitializer` (no-op).
- `com.radiance.client.RadianceClient` — `ClientModInitializer`. This is where the real bootstrap happens: extracts `core.dll`/`libcore.so` (and optional Streamline DLLs) from the jar to `.minecraft/radiance/`, `System.load`s the native lib, copies bundled `shaders/` and `modules/` next to it, then calls `RendererProxy.initFolderPath` and `Pipeline.initFolderPath`, loads `Options`, and reloads pipeline module entries.

### Three layers

1. **Mixins (`com.radiance.mixins.*`)** — declared in `src/main/resources/radiance.mixins.json`, gated by `MixinPlugin.ENABLED` (the plugin currently always returns true; flipping the flag disables every mixin). Two top-level packages:
   - `vanilla_resource_tracker.*` — hooks textures/sprites/glyph/font systems so the Vulkan side can shadow vanilla GPU resources.
   - `vulkan_render_integration.*` — replaces or intercepts vanilla render paths (`WorldRenderer`, `GameRenderer`, `ChunkBuilder`, `RenderSystem`, particles, entities, GUI, screenshot, etc.).
   - `vulkan_options.*` — patches the vanilla Video Options screen so Radiance settings appear there.
2. **Mixin extensions (`com.radiance.mixin_related.extensions.*`)** — `IXxxExt` interfaces implemented by the mixins to expose new state/methods on vanilla classes. Method names are intentionally namespaced (`neoVoxelRT$...`) to avoid collisions.
3. **Native proxies (`com.radiance.client.proxy.*`)** — the JNI surface to the C++ renderer. `RendererProxy`, `BufferProxy`, `TextureProxy`, `PipelineStateProxy`, `DrawCommandProxy`, `WindowProxy`, plus world proxies (`PlayerProxy`, `EntityProxy`, `ChunkProxy`). Every `native` method here corresponds to an exported C++ symbol in MCVR — when adding/renaming any, regenerate the JNI headers (`./gradlew compileJava`) and update MCVR in lockstep.

### Render pipeline graph

`com.radiance.client.pipeline.Pipeline` is a directed graph of `Module`s (each is loaded from `src/main/resources/modules/*.yaml` — `ray_tracing`, `dlss`, `nrd`, `fsr3_upscaler`, `temporal_accumulation`, `tone_mapping`, `post_render`). Modules expose typed `ImageConfig` inputs/outputs and `AttributeConfig`s. `Pipeline.connect(src, dst)` wires outputs to inputs (formats must match), `connectOutput(...)` marks the final output. `Pipeline.build()` topo-sorts the graph, optionally upgrades surface formats to HDR10 (`A2B10G10R10_UNORM_PACK32`) when `Options.hdrEnabled && isHdrSupported()`, and serializes the whole thing into a packed off-heap `ByteBuffer` (LWJGL `MemoryUtil`) before calling `buildNative(long params)`. The graph is persisted to `.minecraft/radiance/pipeline.yaml` via SnakeYAML — load failures fall back to `assembleDefault()` (RT → optional DLSS → tone mapping → post render).

When editing `Pipeline.build`, be careful with the buffer layout: the C++ side reads a 56-byte `params` struct and a parallel set of pointer arrays, so changes here must be matched in MCVR.

### Access widener

`src/main/resources/radiance.accesswidener` opens up vanilla classes (font/glyph internals, `GlStateManager` substates, `RenderLayer$MultiPhase`, `ChunkBuilder` results, `SimpleOption` callbacks, etc.) so mixins and proxies can touch them directly. Add to this file rather than reflecting when you need new access.

### Other notable bits

- `UnsafeManager` uses `sun.misc.Unsafe` to allocate vanilla classes without running constructors — required for some mixin extension shadowing. JVM may need `--add-opens=java.base/sun.misc=ALL-UNNAMED` if reflection breaks.
- `Options` reads/writes `.minecraft/radiance/options.properties` with a `CURRENT_OPTIONS_VERSION` migration counter — bump it if you change the on-disk schema.
- The "K" key opens the Radiance settings screen (`KeyInputHandler`); the welcome message in chat is shown once per fresh install (gated by `Options.showWelcomeMessage`).
- `RendererProxy.initRenderer` overwrites `RenderSystem.apiDescription = "Vulkan 1.4"` — the access widener exposes this field.
