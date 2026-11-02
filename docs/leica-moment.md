# Leica Moment looks (work in progress, branch `wip/leica-moment`)

An attempt to use a newer build of the community Leica port, the "Leica Moment" app (`com.kobbin.leica.compat17u`, label 徕卡一瞬, version code 680019643), as an optional variant with eight Leica Essential looks instead of three. It stopped short of working; the released version keeps the classic port (`com.kobbin.leica`, 6.8.001960.0).

## What the Leica Moment build is

It isn't an update of the classic port's APK but a different package that carries the same processor plus a second engine:

- `classes11.dex`/`classes12.dex`: the classic processor, `com.codex.leicaapp.LegendaryPostProcessor`. Every method the port calls keeps its signature, and `OriginalWatermark` is unchanged. `process` now calls `com.codex.lmport.MomentBridge.process` first and falls back to `processOriginal` (the old processing) when that returns null. Without `classes15.dex` the processor throws `NoClassDefFoundError`, so copying only `classes11`/`classes12` from this build breaks Leica Essential.
- `classes13`-`15`: the second engine (`com.leicamoments.app.LeicaEngine`, `com.codex.lmoments.*`, relocated Kotlin `lmktln` and ONNX Runtime `ai.lmoxruntime`), its `lm_*` models and 27 more libraries. Leica files grow from 160 MB to about 300 MB.
- AI composition (`AiCompositionController`, `cacnet.onnx`, `nima-aesthetic.onnx`, 91 MB) and a "legend85" experiment for the 8 Elite Gen 5's DSP (V81). The processor doesn't reach either; they weren't copied.

`MomentBridge` looks (gallery order; preference `lmoment_v101`, key `style`, default 2 = M9):

| Position | Id | Look |
| --- | --- | --- |
| 0 | M9 | Classic colour |
| 1 | M3 | Black and white, silver-gelatin grain (Monopan) |
| 2 | M9P | M9+: richer colour, more contrast |
| 3 | M10R | Restrained colour, grain, vignette |
| 4 | M4 | ARRI 2049: ALEXA LUT, teal shadows, warm highlights |
| 5 | M13 | CCD: SepLUT FiveK, cool film colour, skin protection |
| 6 | ADAPTIVE | Scene classifier blending three 3D LUTs |
| 7 | ORIGINAL | Returns null, so the classic processor renders the camera's own look |

`MomentBridge.process(jpeg, task)` renders only when the task's `b.g` (module) is 256 and the choice isn't ORIGINAL, off the main thread. It reads `Sh.t` fields `a`, `b`, `f` and `legendaryPreviewResult`; 6.8.002090.2's `Sh.t`, `Sh.a`, `Sh.C` and `Sh.h` have the same fields and types as the build it was made for.

## How that build hooks the camera

- `FragmentLegendarySelected` (`c6.d` there, `c6.c` in 6.8.002090.2: component field `a` of type `r2.B`, static item handler `ms(c6.c, item)`, hide `ns()`): `initView` hides the camera's list and opens `LeicaV43Ui.show(activity, fragment)`; the fragment gets `v43Value()`, `v43Select(String)` and `v43Close()`. `LeicaV43Ui` reads `leica_v43_back/title/apply` strings from the camera's own resources, which our APK doesn't have (the patcher rebuilds without resources).
- `MomentBridge.mode()` reads a static int `com.android.camera.module.Z.a` the modder added and sets in `Camera.i8`, `u2.W.g` and `Vm.i0.A`; only the button badge and preview tint use it.
- The look button badge uses `id/v9_camera_picker_legendary_icon` by its number (0x7f0b0c22 there, 0x7f0b0c24 here).
- `PreviewTone` tints the live preview per look by rewriting the preview shaders (`glShaderSource`/`glDrawArrays` call sites); not ported.

## What the branch does

- `scripts/build-kit-zip.sh`, `aosp/tools/make-device-tree.sh` and `module/customize.sh` pick the port's files by pattern (every `classes1[1-9].dex`, the classic assets, `lm_*`, `lm-ui/`, `scene-transfer.bin`, `leica-color-v3.rgb`). For the classic APK that's the same 22 files as before. `build-kit-zip.sh` names a zip from the Leica Moment build `-standalone-moment`. The device tree's `leica.mk` lists whatever dex files it has.
- `module/customize.sh` prefers the classic port when both are in Download. Without this, the released installer picks `Leica A.apk` (it sorts before `Leica.apk`) and Leica Essential stops rendering.
- `Leica.java` loads every `classes*.dex` in the kit folder (`kit()`), and `moment()` loads `MomentBridge` when `classes15.dex` is there.
- `LeicaLooks.java` (patches `leica-looks` on `c6.c.initView` and `leica-looks-pause` on `c6.c.onPause`): its own picker built on `MomentBridge`'s public methods (`gallerySelection`, `gallerySample`, `galleryApply`, `galleryModelIndex`), with English names, replacing the camera's list; the camera's own look follows as M3 for the black and white look and M9 otherwise.
- The LSPosed module loads every kit dex and serves the new assets, so these files don't break it; it has no picker.

## Where it stopped

Tested on the phone with the standalone Moment zip:

- The picker opens from Essential mode's look button, shows the current choice, and saves it (`style` = 5 for ARRI 2049); the camera's own look follows.
- A photo reaches `MomentBridge.render` on the real task, but the engine returns the source unchanged ("Model returned unprocessed source"): it can't open `lm_lut_alexa.bin`. The picker's samples fail the same way on `lm-ui/tone-atlas.png`.
- The kit's asset loader serves nothing: right after `ResourcesProvider.loadFromDirectory(KIT)` is added to the Application's Resources (and its base context's), `getAssets().open()` fails even for `legend18/provenance.json`. The classic looks only worked on this phone because the classic processor copied its models into the camera's `files/` on 1 November and reads them from there since. A fresh standalone install probably has the same problem with the classic looks; worth checking before relying on it.

## Next steps

1. Fix the kit's asset access: find which Resources/AssetManager `Global.getApplication().getAssets()` returns in the camera, or hook `AssetManager.open`/`list` for the kit's names as the LSPosed module does. The models' `list()` calls (`ModelAssetCheck`, `copyTree`) need directory listing too, which an `AssetsProvider` alone doesn't give.
2. Then shoot every look; the M3/M9 Moment chains want the 8 Elite Gen 5's DSP and fall back to CPU or LUT chains on houji.
3. Optional: the look button badge (`ToneBadge`, by resource name) and the preview tint.
