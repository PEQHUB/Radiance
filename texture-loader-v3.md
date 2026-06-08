# Texture Loader V3 Status

This file records the current truth of the resource-pack texture loader work as of
this checkout. It is intentionally conservative: a feature is listed as complete
only when the code path is implemented and visible through DebugBridge or native
logs.

## Implemented In This Pass

- Material residency page uploads now guard Java direct-buffer bounds before
  every direct write and before the JNI call.
- The first visible CTM residency batch no longer clears `pageCapacity` layers
  when only `allocation.layerCount()` layers were allocated. This fixes the
  confirmed off-heap overwrite path for small batches such as 6 materials.
- Native material layer upload now rejects invalid page/range/pointer inputs
  before calling the renderer-owned texture system.
- Native logs distinguish persistent material page allocation from subrange
  updates:
  - `[TextureArrayManager] Created material page N: SxS x C layers`
  - `[TextureSystem] Updated material page N startLayer=L layers=C`
- The atlas finalize path no longer performs an immediate duplicate full
  material table upload when runtime material bootstrap already uploaded it.
- Vanilla block sprites are routed through reserved material texture pages for
  size tiers 16, 32, 64, 128, 256, 512, and 1024. Page 0 remains a 1x1 fallback
  array set instead of a full fixed 128x128 upload.
- The fixed page-0 bootstrap upload is intentionally tiny in primary-tier mode;
  the material table points vanilla sprite materials at their tier page/layer.
- Runtime CTM dependency roots are cached under
  `<minecraft>/radiance/cache/texture-loader-v3/` with stable resource-pack
  selection keys and DebugBridge status counters.
- Runtime CTM residency writes transformed binary layer payloads under the same
  cache root. Matching later boots can skip PNG decode and pixel conversion for
  albedo/specular/normal/flag planes and copy the cached RGBA payload straight
  into the native page upload buffers.
- Vanilla tier page uploads also use the transformed layer payload cache for
  static frame-0 albedo/specular/normal/flag planes. Warm boots can reuse
  sprite-sized tier payloads before native page upload.
- CTM residency now tracks a requested-material queue distinct from
  visible-only fallback accounting. First contact with a pending compat material
  schedules a short-debounce prewarm pass, and completed uploads retire
  requested materials from the pending queue.
- Demand residency passes are bounded by material count and byte budget
  (`radser.ctmResidencyBatchMaterials`, default 128, and
  `radser.ctmResidencyBatchMiB`, default 32). Remaining requested materials are
  coalesced and rescheduled instead of draining the entire queue in one pass.
- Failed CTM residency attempts are tracked separately and removed from the
  requested queue so broken assets or native rejections do not spin forever.
- Texture animation payload scan/build is skipped by default while texture-array
  animation updates are disabled. The native path receives an empty animation
  payload instead of building data that would be discarded.
- If texture-array animation updates are explicitly enabled for diagnostics,
  transformed animation frame payloads are cached as compact byte payloads and
  reported through `textureCacheStatus` byte-cache counters.
- DebugBridge accepts validation commands for:
  - `materialPagePoolStatus`
  - `materialTableStatus`
  - `nativeUploadSafetyStatus`
  - `textureCacheStatus`
  - `resourcePackComprehension`

## Default-Enabled Current Paths

- `materialPagePools`: true in native compatibility mode.
- `sparseMaterialTableUpdates`: true when `Options.materialTableDirtyUpdates`
  is enabled.
- `sparseAuxBatchUpload`: true for the legacy fixed-array compatibility path.
- `tieredArrays`: true. Native status reports `material_page_size_tiers`.
- `vanillaMaterialPageTiers`: true.
- `diskCacheEnabled`: true for runtime CTM dependency roots and transformed
  CTM layer payloads.
- CTM residency uses renderer-owned material pages and sparse material-table
  updates after a successful page upload.
- CTM demand residency starts from the requested-material queue, so initial
  visible material contact can trigger upload work before the longer visible
  demand delay expires.

## Not Yet Complete

- The cache now stores runtime CTM dependency roots, transformed CTM residency
  layer payloads, transformed vanilla tier frame-0 layer payloads, and explicit
  animation-mode frame byte payloads.
- CTM prewarm/first-frame readiness now has a requested-material queue,
  failed-material tracking, shorter prewarm scheduling, and bounded demand
  upload passes. It is still not a fully blocking no-pop-in first-frame gate.
- Texture animation payloads are skipped in the default frozen-animation mode.
  If animation updates are explicitly enabled, frame conversion is cached, but
  the diagnostic native animation ABI is still fixed-layer.

## Validation Commands

Use DebugBridge while the game is running:

```powershell
powershell.exe -File C:\RadSER\bridge.ps1 -json '{"cmd":"textureLoaderStatus"}'
powershell.exe -File C:\RadSER\bridge.ps1 -json '{"cmd":"textureTierStatus"}'
powershell.exe -File C:\RadSER\bridge.ps1 -json '{"cmd":"textureCacheStatus"}'
powershell.exe -File C:\RadSER\bridge.ps1 -json '{"cmd":"materialPagePoolStatus"}'
powershell.exe -File C:\RadSER\bridge.ps1 -json '{"cmd":"materialTableStatus"}'
powershell.exe -File C:\RadSER\bridge.ps1 -json '{"cmd":"nativeUploadSafetyStatus"}'
powershell.exe -File C:\RadSER\bridge.ps1 -json '{"cmd":"firstFrameReadiness"}'
powershell.exe -File C:\RadSER\bridge.ps1 -json '{"cmd":"bootPerfSummary"}'
powershell.exe -File C:\RadSER\bridge.ps1 -json '{"cmd":"resourcePackComprehension"}'
```

## Runtime Log Checks

- Small CTM residency batches should not produce a JVM fatal access violation.
- A first small batch should create page 1 once, then later batches should log
  `Updated material page 1` with increasing `startLayer`.
- Residency batches should not trigger a full material-table upload immediately
  after bootstrap when bootstrap already uploaded the active table.
- `textureReloadTimeline` should show `primaryTierUpload=1`,
  `spriteLayerSize=1`, `tieredArrayPages > 0`, and no fixed 128x128 page-0
  bulk upload.
- `materialPagePoolStatus` should report six or more vanilla tier pages
  allocated for the current Patrix stack, with `layersUsed=1810`.
- `textureCacheStatus` should report `diskCacheEnabled=true`; after the second
  matching boot it should show root cache hits and layer payload cache hits.
- `firstFrameReadiness` should expose nonzero prewarm/requested counters when
  pending CTM materials are touched before residency completes, then drain the
  requested queue as uploads are marked resident or failed. Scheduler status
  should report prewarm/visible debounce and coalesced pending requests.
- If native rejects an invalid upload, Java should log a failed page upload and
  continue with fallback material state rather than hard-crashing the JVM.

## Known Limits

- This pass fixes the known memory-corruption crash class, removes the old
  full-size page-0 fixed upload from primary-tier mode, and improves
  diagnostics.
- A fully blocking first-frame CTM no-pop-in gate is still remaining
  architecture work.
- The payload cache key follows the active resource-pack selection plus
  resource-pack file size/mtime inventory.
- Patrix 128x runtime completion must not be claimed until the validation matrix
  has been run in-game and the DebugBridge/status/log evidence supports it.
