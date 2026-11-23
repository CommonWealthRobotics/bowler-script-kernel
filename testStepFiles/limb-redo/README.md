# Offline limb history fixture

`history.doodle` and `robot.xml` are copies of the supplied Doodle-208-Mirthful_Gravicycle project (16 operations, saved cursor 9). They supplement the older one-limb Doodle-208 fixture without changing it.

`junit.bowler.OfflineLimbRedoTest` deserializes the history without initialization, then installs offline dependencies before invoking the real `CaDoodleFile.initialize()`:

- The first five shape/controller setup operations become identity operations; their timeline slots are preserved.
- Both `AddRobotLimb` operations and every `ModifyLimb` remain real operations, including their `process`, `undo`, and `redo` methods.
- Local XML supplies the wheel and leg DH chains. Engine declarations are removed from the parsed elements; no script engines are installed. Initial registration is reconstructed from each first modification's `basePrevious`, because the saved robot XML contains the final pose.
- A real `MobileBaseBuilder` overrides only `getCadManager()`. The existing Mockito dependency supplies a no-op renderer and eight local cubes per limb (the history selects mesh suffixes through `_7`). Actual builder `build` and `applyModification` run unchanged.
- The image provider is mocked and restored after each test. Builder XML, CSG database, and BOM output use a newly created workspace `build/offline-limb-redo-*` directory, removed afterward. The original fixture files are never written.

Coverage includes initialization at cursors 5, 9, 11, and 16; two cycles of sequential undo to cursor 5 and redo through 16; live limb presence, base XYZ, and joint targets at every step; detached original limb poses; and snapshots observed by update/save-suggestion callbacks. Expected undo poses use the next modification's persisted precommit snapshot, including the leg's unrecorded interactive base change before operation 15.

`OfflineLimbRenderProbe` also injects real SDK pose-feedback events and requires exactly one base render notification for each live limb and none for removed limbs. The notification invokes the real renderer's pose sampler; its real transform calculation then updates shared joint Affines on cached, unattached MeshViews. Tests verify finite joint matrices, transformed mesh points, and affine identity across limb recreation. Unrelated asynchronous PID notifications are excluded from the synchronous event count.

This is not a full UI/CAD integration test: it does not run the JavaFX render thread, real generated meshes, task-space convergence, scripts, controllers, or hardware. Model snapshots remain observational; injected render events and detached-node affine updates are confined to the render probe.

Run from the application root:

```sh
./gradlew --offline :BowlerStudio:libraries:bowler-script-kernel:test --tests junit.bowler.OfflineLimbRedoTest
```

The existing Mockito 1.x implementation requires the test JVM's `java.base/java.lang` opening on modern JDKs; the kernel test task provides it. No dependency was added.
