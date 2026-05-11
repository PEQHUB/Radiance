# Building Radiance + MCVR on Windows

Canonical Windows build setup for the Radiance 1.20.1 backport. This document
captures the **actual** toolchain and workflow used to clear PRD G1+G3-recovery
on 2026-05-11. It corrects several points in `docs/PLAN.md` Part 4 W2/W6/W11
that were wrong about real-world Loom/CMake/MCVR behavior.

Tested on **Windows 11 Home build 26200 (x64)** with the toolchain versions
below.

## Prerequisites

| Tool | Version installed | Why |
|---|---|---|
| Windows 10 (1903+) / 11 x64 | 11 Home, build 26200 | MCVR's bundled deps require 64-bit Windows. |
| Visual Studio 2022 **Build Tools** | MSVC v14.44.35207 / `cl.exe` v19.44.35215 / `link.exe` v14.44.35215.0 | C++23, MSBuild. Pick "Build Tools" (not the full IDE) — smaller. Workload: ☑ **Desktop development with C++**. |
| Vulkan SDK (LunarG) | **1.4.341.0** | Bundles `glslang`, `glslc`, headers, `vulkaninfoSDK.exe`. Sets `VULKAN_SDK=C:\VulkanSDK\1.4.341.0`. |
| CMake (standalone) | **3.31.12** | **Use 3.x — NOT 4.x** (see [CMake version trap](#cmake-version-trap) below). |
| Git for Windows | 2.51.0.windows.1 | autocrlf=true is fine for this repo. |
| Temurin JDK **17** | **17.0.19+10** | Build target / `runClient` runtime via Loom toolchain. |
| Temurin JDK **21** | **21.0.11+10** | **Gradle daemon JVM** (Loom 1.11-SNAPSHOT requires it). See [JDK 17 vs 21](#jdk-17-vs-21) below. |
| GPU | NVIDIA RTX 5070 Ti (Vulkan 1.4.329, driver 596.21) | RT + DLSS-capable; matches v1.0 target hardware. |

`JAVA_HOME` should point at the **JDK 21** install (Gradle picks this up); the
JDK 17 install just needs to exist on disk so Loom's toolchain auto-detect can
find it for `compileJava --release 17` and `runClient`.

## Layout

This repo lives at `C:\Users\<user>\Documents\Projects\Radiance-1201`. MCVR
clones alongside at `C:\Users\<user>\Documents\Projects\MCVR`. The Loom dev
client gamedir is at `C:\Users\<user>\Documents\Projects\mc-test\instance`
(set by `build.gradle:29`, relative to the project — does NOT get created
automatically; see [Gamedir trap](#gamedir-trap) below).

The plan template's `C:\Users\%USERNAME%\Projects\Radiance-1201` is wrong for
this setup; substitute `Documents\Projects\` accordingly.

## Build steps

1. Clone Radiance:
   ```
   git clone https://github.com/lavindeep/Radiance.git Radiance-1201
   ```

2. Clone MCVR alongside it, with all submodules:
   ```
   git clone --recurse-submodules https://github.com/Minecraft-Radiance/MCVR.git MCVR
   ```
   ~2-3 GB after submodules; takes 2-5 min on a fast connection.

3. Check out the `mc/1.20.1` branch in MCVR (this is where the §4.3.1
   handshake decoder lives — see `MCVR/src/core/middleware/com_radiance_client_proxy_vulkan_RendererProxy_Handshake.cpp`):
   ```
   cd MCVR
   git checkout mc/1.20.1
   ```

4. Generate JNI headers (from Radiance-1201):
   ```
   gradlew.bat compileJava
   ```
   First run is cold cache: downloads Loom + Yarn 1.20.1 + Fabric API
   (~500 MB). Outputs `.h` files into `src/main/native/include/`.

5. Configure MCVR. **You can use a regular PowerShell** — the `Visual Studio 17 2022`
   CMake generator uses MSBuild, which auto-discovers MSVC via `vswhere`. The
   "x64 Native Tools Command Prompt for VS 2022" mentioned in the upstream
   README is only required if you fall back to the `Ninja` generator or call
   `cl.exe` directly.
   ```
   cmake -S . -B build -G "Visual Studio 17 2022" -A x64 `
     -DCMAKE_BUILD_TYPE=Release `
     -DJAVA_PROJECT_ROOT_DIR="C:/Users/<user>/Documents/Projects/Radiance-1201" `
     -DUSE_AMD=ON -DMCVR_ENABLE_NRD=ON -DMCVR_ENABLE_FFX_UPSCALER=ON
   ```
   (The backticks are PowerShell line continuations. Use forward slashes in
   the Java path — CMake prefers them on Windows.) Configure takes ~90s on
   cold cache.

6. Build MCVR:
   ```
   cmake --build build --config Release --parallel
   ```
   First build ~15-25 min depending on cores. Output: `build/src/core/Release/core.dll`
   (~30 MB) and `core.lib` (~36 KB).

7. Verify the handshake symbol is exported:
   ```
   dumpbin /exports build\src\core\Release\core.dll | findstr handshake
   dumpbin /exports build\src\core\Release\core.dll | findstr validateAbi
   ```
   Both must print at least one line each. The mangled symbols are
   `Java_com_radiance_client_proxy_vulkan_RendererProxy_handshake` and `..._validateAbi`.

8. Install MCVR outputs into Radiance's resource tree:
   ```
   cmake --install build --config Release
   ```
   This writes `core.dll`/`core.lib` to `Radiance-1201/src/main/resources/`
   and the SPIR-V `shaders/` tree there too.

9. Move `core.dll`/`core.lib` to the canonical native location. `processResources`
   in `Radiance-1201/build.gradle` reads natives from `natives/<platform>/`,
   NOT `src/main/resources/`. Move (don't copy — keep the resource tree clean):
   ```powershell
   $repo = 'C:\Users\<user>\Documents\Projects\Radiance-1201'
   New-Item -ItemType Directory -Force "$repo\natives\windows" | Out-Null
   Move-Item "$repo\src\main\resources\core.dll" "$repo\natives\windows\" -Force
   Move-Item "$repo\src\main\resources\core.lib" "$repo\natives\windows\" -Force -ErrorAction SilentlyContinue
   ```
   Leave Streamline DLLs (`sl.*.dll`, `NvLowLatencyVk.dll`) in `src/main/resources/`
   — they're historical. SPIR-V `shaders/` stays at `src/main/resources/shaders/`
   so `processResources` packages them into the jar.

10. Pre-create the dev gamedir parent (Loom does NOT create it recursively):
    ```powershell
    New-Item -ItemType Directory -Force C:\Users\<user>\Documents\Projects\mc-test\instance\mods | Out-Null
    ```

11. Build the jar (from Radiance-1201):
    ```
    gradlew.bat build -Pplatform=windows
    ```
    Output: `build\libs\Radiance-0.1.3-alpha-fabric-1.20.1-windows.jar` (~110 MB).

12. Run the dev client (from Radiance-1201):
    ```
    gradlew.bat runClient
    ```
    Look for these log lines in order:
    ```
    [radiance] Pre-loaded libxess.dll from <path>
    [radiance] System.load succeeded for <path>\core.dll
    [radiance] RendererProxy.handshake(12001, javaOrdinals.length=130) returned 0
    ```
    The third line — handshake **returns 0 with `length=130`** — is the gate
    signal. Anything else fails G1+G3-recovery.

## Known issues encountered during this setup

These are the things that genuinely cost time on the first build. The
`docs/PLAN.md` Part 4 template got several of them wrong; this section is the
authoritative correction.

### JDK 17 vs 21

The plan said "Use JDK 17, not 21." That's **wrong for Loom 1.11-SNAPSHOT**
which requires JVM 21 to even resolve. The first `gradlew.bat compileJava`
attempt with JDK 17 only crashed with:

> Could not resolve net.fabricmc:fabric-loom:1.11-SNAPSHOT.
> Dependency requires at least JVM runtime version 21. This build uses a Java 17 JVM.

The distinction is:
- **Gradle daemon JVM** = JDK 21 (Loom plugin requirement)
- **`compileJava --release 17`** = produces Java 17 bytecode (Gradle handles this independently of daemon JVM)
- **`runClient`** runtime = Java 17 (via Loom toolchain auto-provision)

Install both JDKs. Point `JAVA_HOME` at 21. Gradle picks up JDK 17 for the
toolchain by scanning standard install directories like
`C:\Program Files\Eclipse Adoptium\`.

### CMake version trap

CMake 4.x is **incompatible** with several MCVR submodules. Multiple
`extern/*/CMakeLists.txt` files have explicit version-range upper bounds:

- `glfw`: `VERSION 3.4...3.28 FATAL_ERROR`
- `nrd`: `3.22...3.30`
- `volk`: `3.5...3.30`
- `vma`: `3.15...3.26`
- `glm`: `3.6...3.14 FATAL_ERROR`
- `json` (nlohmann): `3.5...4.0`

Any of those will fail-fast with CMake 4.x. Plus CMake 4.0+ made
`cmake_minimum_required(VERSION <3.5)` a hard error, killing
`tiny-process-library` (FFX dep).

**Install the latest CMake 3.x** (3.31.x or 3.30.x) from the "Previous Releases"
section of https://cmake.org/download/, NOT the top "Latest Release" if that
shows 4.x.

### Loader version pin

`gradle.properties` originally pinned `loader_version=0.15.11`. Fabric API
0.92.6+1.20.1 requires Fabric Loader **0.16.10+** — older pin causes
`FormattedException` at Knot init time before `RadianceClient.onInitializeClient`
ever runs. The current `gradle.properties` is `0.16.10` and that works.

### MCVR deferred-class .cpp files

Four MCVR `src/core/middleware/com_radiance_client_proxy_*.cpp` files
reference JNI headers for Java classes that don't exist on the 1.20.1
Radiance head:

- `BufferProxy.cpp`, `ChunkProxy.cpp`, `EntityProxy.cpp` — Java classes
  deferred in `src/deferred/java/` for the 1.21→1.20 backport.
- `ShaderProxy.cpp` — Java class removed from Radiance entirely.

The `mc/1.20.1` branch's `src/core/CMakeLists.txt` excludes them via
`list(FILTER SOURCE_FILES EXCLUDE REGEX ...)` (see commit `bee0add` +
`ef54555`). When Checkpoint C eventually promotes the deferred proxies, revert
those exclusions and ship real C++ implementations.

### libxess.dll preload

MCVR built with `-DMCVR_ENABLE_XESS=ON` (the default when `extern/xess` is
populated) statically imports `libxess.dll` into `core.dll`'s PE header. The
Windows loader **does not include the directory of an explicitly-loaded DLL
when resolving its transitive dependencies** — so placing `libxess.dll` next
to `core.dll` is necessary but not sufficient.

`RadianceClient.initializeNativeRenderer` extracts `libxess.dll` to the
gamedir `radiance/` AND calls `System.load(libxess.dll)` BEFORE
`System.load(core.dll)`. Once `libxess` is in the JVM's loaded-modules table,
Windows resolves it by name when loading `core.dll`. See `RadianceClient.java`
lines 108-126 and the `feat(alpha-0): ... pre-load libxess.dll` commit.

Variant DLLs (`libxess_dx11.dll`, `libxess_fg.dll`) are extracted optionally
because XeSS only needs them at upscale time, not at load time.

### Vulkan SDK binary names

Recent LunarG SDKs renamed `vulkaninfo.exe` to `vulkaninfoSDK.exe` inside
`%VULKAN_SDK%\Bin\`. A separate runtime-only `vulkaninfo.exe` ships in
`C:\Windows\system32\` from your GPU driver. Either reports `apiVersion`
correctly; just don't trip on the rename.

### Gamedir trap

`build.gradle:29` passes `--gameDir ../mc-test/instance` to `runClient`. The
JVM tries to create `<gamedir>/mods` via `Files.createDirectory` (single-level),
which fails if the parent `mc-test/instance` doesn't already exist. Result:

> RuntimeException: Could not create directory ...mc-test\instance\mods

Pre-create the path manually (step 10 above).

### XeSS DLL jar bloat (tech debt)

MCVR's `cmake --install` drops 125 MB of `libxess*.dll` into
`Radiance-1201/src/main/resources/` (via the `install(FILES XESS_RUNTIME_DLLS DESTINATION ...)`
rule). These files end up in the jar via `processResources`'s default behavior
and inflate the jar from ~30 MB to ~110 MB. Not a gate blocker, but a future
cleanup: either filter them out of `processResources` or fix MCVR's install
rule to drop them under `natives/windows/` like core.dll.

### DLSS DLL workflow

DLSS DLLs (`nvngx_dlss.dll`, `nvngx_dlssd.dll`) are **never bundled** —
NVIDIA's license forbids redistribution. The mod logs WARN and shows
`DlssMissingScreen` if absent. For ray reconstruction support, download
from https://github.com/NVIDIA/DLSS/tree/main/lib/Windows_x86_64/rel and
drop into the gamedir's `radiance/` subfolder. Same workflow applies whether
the mod is distributed via GitHub Releases or CurseForge.

## After this point

Per the PRD, Checkpoint 0c+A is the alpha-0 release gate (PRD G1+G3-recovery).
With this BUILD-WINDOWS.md complete and the gate cleared, follow-on work
(alpha-1 Vulkan boot path, alpha-2 world bridge, etc.) is tracked in
`docs/PLAN.md` Part 2 Checkpoints B–F.
