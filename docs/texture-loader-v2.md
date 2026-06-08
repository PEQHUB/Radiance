# Texture Loader V2 Notes

Last verified: 2026-06-08

This pass replaces the first demand-driven texture residency prototype with a lower-stall update path for Patrix-sized packs. It is not the full tiered-atlas ABI cutover yet.

## Implemented

- Resident material handles are merged across demand batches instead of replacing the registry on every visible batch.
- Normal residency completion is descriptor-only and does not schedule a chunk rebuild.
- Sparse auxiliary sidecars are uploaded through one JNI batch per atlas instead of one native call per sidecar layer.
- Normal and height metadata updates preserve existing sprite flags.
- Material table residency updates are sparse; completed pages update only changed material entries.
- Visible material residency requests are debounced before upload so a burst of discovered materials coalesces into fewer native calls.
- Material texture pages use persistent page/layer allocation and upload into existing page arrays when possible.
- DebugBridge exposes `textureLoaderStatus`, `textureTierStatus`, and `gpuUploadQueueStatus`.

## Current Compatibility Mode

Native status intentionally reports:

- `activeUploadMode`: `fixed_albedo_arrays_batched_sparse_aux_pooled_material_pages`
- `sparseAuxBatchUpload`: `true`
- `sparseMaterialTableUpdates`: `true`
- `materialPagePools`: `true`
- `tieredArrays`: `false`
- `asyncTransferQueueUpload`: `false`

The current shader/material ABI still fetches from the fixed sprite/material page model. True tiered sprite arrays require a shader and material-entry ABI change so each material can address the right array tier, not just a faster upload path.

## Validation Commands

Native:

```powershell
& 'C:\Program Files\CMake\bin\cmake.exe' -S 'C:\RadSER\MCVR' -B 'C:\RadSER\MCVR\build' -G 'Visual Studio 17 2022' -A x64 -DJAVA_PROJECT_ROOT_DIR='C:\RadSER\Radiance' -DMCVR_ENABLE_NRD=ON
& 'C:\Program Files\CMake\bin\cmake.exe' --build 'C:\RadSER\MCVR\build' --config Release --target core --parallel 1
& 'C:\Program Files\CMake\bin\cmake.exe' --install 'C:\RadSER\MCVR\build' --config Release --prefix 'C:\RadSER\Radiance'
```

Radiance:

```powershell
cmd.exe /c "set TEMP=C:\Users\Administrator\AppData\Local\Temp && set TMP=C:\Users\Administrator\AppData\Local\Temp && cd /d C:\RadSER\Radiance && C:\RadSER\Radiance\gradlew.bat clean build 2>&1"
```

DebugBridge:

```powershell
cmd.exe /c "cd /d C:\RadSER\DebugBridge && C:\RadSER\DebugBridge\gradlew.bat clean build 2>&1"
```

Runtime status once the game is running:

```powershell
powershell.exe -File C:\RadSER\bridge.ps1 -json '{"cmd":"textureLoaderStatus"}'
powershell.exe -File C:\RadSER\bridge.ps1 -json '{"cmd":"firstFrameReadiness"}'
powershell.exe -File C:\RadSER\bridge.ps1 -json '{"cmd":"bootPerfSummary"}'
```

## Not Done In This Pass

- True tiered sprite arrays.
- Vanilla GL block-atlas bypass.
- Persistent disk cache for transformed/generated texture data.
- Runtime Patrix crash/pop-in verification in a live Minecraft session.
