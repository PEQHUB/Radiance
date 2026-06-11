# GPT-5.5 Pro Handoff: Rewrite RadSER V4 Architecture

This is the execution brief for the next GPT-5.5 Pro pass. Do not perform another narrow symptom patch. Plan and orchestrate a full V4 rendering architecture rewrite with explicit ownership, static audits, build/deploy proof, and runtime proof.

## Current Heads

- MCVR `fix/texture-loader-v4-critical-correctness`: `be63bf8cdd2977b454eabb12e37731f0ebc9be48`
- Radiance `rehab/texture-loader-v4-finish-single-path`: `65772960fa9e7ee7ba49872ceda795a0287a6e31`
- DebugBridge `rehab/texture-loader-v4-finish-single-path`: unchanged in the latest local execution

Latest deployed hashes:

- Radiance jar: `4836229FAD254CE9D8F93DAB9418A0BA2DF63910A39B4D5D8EFDECBFEEB71E18`
- `core.dll`: `DF6AF06E69E80CA1ECE8E551F29E32D50C797C725F7CE7FD4C8D4E7594C8B0F5`

## Fresh Runtime Evidence

Fresh logs from the failed run:

- `C:\Users\Administrator\AppData\Roaming\PrismLauncher\instances\1.21.4\minecraft\logs\latest.log`
- `C:\Users\Administrator\AppData\Roaming\PrismLauncher\instances\1.21.4\minecraft\radiance\logs\render_diag.log`
- `C:\Users\Administrator\AppData\Roaming\PrismLauncher\instances\1.21.4\minecraft\radiance\logs\crash_ring.txt`
- `C:\Users\Administrator\AppData\Roaming\PrismLauncher\instances\1.21.4\minecraft\hs_err_pid1240.log`

First missing marker:

```text
RT descriptors missing V4 texture resources finalized=0 v4Ready=1 fallbackReady=1 readyMaterialPages=10 pendingMaterialMipPages=0 spriteReg=1 materialReg=1 rules=0; skipping RT
```

Interpretation:

- V4 material pages are ready.
- Descriptor fallback arrays are ready.
- Sprite registry exists.
- Material registry exists.
- Texture rule buffer is missing.
- RT skips every frame before descriptor refresh and world trace.
- The fix must not merely hide `rules=0`; it must make every shader-consumed descriptor/buffer always valid through either V4 publication or an explicit V4-owned default fallback.

Diagnostic crash:

```text
EXCEPTION_STACK_OVERFLOW (0xc00000fd)
Problematic frame: C [core.dll+0x510187]
Java frame: com.radiance.client.proxy.vulkan.RendererProxy.nativeBuildInfoJson()
```

Likely path: DebugBridge `cmdBuildInfo()` calls `RendererProxy.nativeBuildInfoJson()` inside broad validation/capture commands. Diagnostics are not safe until native build-info querying is fixed or isolated.

## Workflow Failure

The previous process failed because it moved one gate at a time:

- First it made V4 uploads publish before legacy finalize.
- Then it hard-cut RT descriptors away from legacy block arrays.
- Now the next gate is missing texture rules.

That is not a V4 architecture. It is a sequence of local bypasses. GPT-5.5 Pro must define the complete V4 frame contract before coding.

## Required V4 Architecture Contract

Every RT shader-consumed resource must be valid every frame:

- V4 material pages or V4 fallback page 0.
- V4 sprite registry or explicit empty fallback buffer.
- V4 material registry or explicit default material table.
- Texture rule registry or explicit default rule buffer.
- Descriptor fallback arrays with valid image views/samplers/layouts/lifetime.
- TLAS/SBT/output images/blue-noise/shader-pack runtime resources.

No RT frame may skip solely because optional V4 content is empty. Empty content must mean valid default content.

## Required Subagent Workers

Use low-thinking-effort subagents as implementers with disjoint write scopes.

Worker A: Native V4 RT descriptors and rule fallback

- Owns `MCVR/src/core/render/modules/world/ray_tracing/`, `texture_system.*`, `texture_rule_registry.*`.
- Remove fatal `rules=0` skip.
- Add always-valid texture rule fallback buffer.
- Prove `refresh-slot-v4` and RT world trace execute.

Worker B: Java V4 publication lifecycle

- Owns `SpriteAtlasTextureMixins.java`, `TextureLoadScheduler.java`, `ResourceMaterialRegistry.java`, `TextureArrayBridge*.java`.
- Make resource reload one transaction: pages, sprite registry, material table, texture rules, commit.
- Fail closed without reviving fixed/legacy paths.

Worker C: DebugBridge and diagnostic safety

- Owns `DebugBridge.java`, `BuildIdentityCommands.java`, `RendererProxy.java`, and native `RendererProxy.cpp`.
- Make `buildInfo`, `validationSnapshot`, `captureContext`, and `textureValidation` non-crashing.
- Fix or disable unsafe `nativeBuildInfoJson()` calls.

Worker D: Shader/output proof

- Owns ray-tracing shaders and any native proof markers.
- Prove first-hit albedo/material/rule fetch and output image content.
- Classify black as no hits, material fetch black, lighting zero, composite zero, or present issue.

## Required Static Audits

Run and explain:

```powershell
rg -n "legacy|Legacy|fixed|blockAlbedoArrayId|blockSpecularArrayId|blockNormalArrayId|blockFlagArrayId|isFinalized\(|TextureArrayBridge(?!V4)" C:\RadSER\Radiance\src C:\RadSER\MCVR\src C:\RadSER\DebugBridge\src --pcre2
rg -n "rules=0|textureRuleBuffer|TextureRuleRegistry|uploadTextureRules|nativeBuildInfoJson|cmdBuildInfo" C:\RadSER\Radiance\src C:\RadSER\MCVR\src C:\RadSER\DebugBridge\src
```

Every remaining hit must be classified as deleted, dead compile-only code, non-world support, hard-failed compatibility stub, or intentional V4 fallback.

## Runtime Acceptance

Required markers:

```text
RT textureFlush begin finalized=0 v4Active=1
v4Ready=1
fallbackReady=1
readyMaterialPages > 0
pendingMaterialMipPages = 0
spriteReg=1
materialReg=1
rules=1 OR textureRulesDefaultFallback=1
refresh-slot-v4 appears
RT descriptors end
RT main trace executes
capture histogram nonblack
no fatal diagnostic crash
```

Unsafe to call fixed if:

- Black screen remains.
- RT skips on missing descriptors/buffers.
- Diagnostics crash the JVM.
- Any world RT descriptor can be null.
- V4-only depends on legacy block arrays, legacy finalize, or fixed compatibility uploads.

