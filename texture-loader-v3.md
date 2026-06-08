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
- `sparseAuxBatchUpload`: true in the existing fixed-array compatibility path.
- CTM residency uses renderer-owned material pages and sparse material-table
  updates after a successful page upload.

## Not Yet Complete

- `tieredArrays`: false. The current native status still reports
  `single_fixed_layer_array`, so 16x16, 32x32, and 64x64 sprites can still be
  expanded to the fixed layer size.
- `diskCacheEnabled`: false. DebugBridge reports this explicitly through
  `textureCacheStatus`.
- Full vanilla block atlas GL-upload bypass is not proven complete in this
  checkout. Runtime logs must still be used to verify the block-atlas gap.
- CTM prewarm/first-frame readiness is still a compatibility scheduler path,
  not a fully budgeted no-pop-in residency system.

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
- If native rejects an invalid upload, Java should log a failed page upload and
  continue with fallback material state rather than hard-crashing the JVM.

## Known Limits

- This pass fixes the known memory-corruption crash class and improves
  diagnostics, but it does not complete all texture-loader-v3 architecture
  goals from the long-form request.
- Patrix 128x runtime completion must not be claimed until the validation matrix
  has been run in-game and the DebugBridge/status/log evidence supports it.
