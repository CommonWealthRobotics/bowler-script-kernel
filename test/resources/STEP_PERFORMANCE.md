# STEP export profiling

## Reproduce

Run from the kernel directory with a JDK compatible with the dependencies (this
measurement used Zulu FX JDK 25.0.4.1). No new dependencies are needed.

```sh
./gradlew :testClasses :printTestClasspath > build/step-profile-classpath.log
CP=$(sed -n '/^CLASSPATH_START$/,/^CLASSPATH_END$/p' build/step-profile-classpath.log | sed '1d;$d')
java --enable-preview -Xmx8g -cp "$CP" junit.bowler.StepExportProfile \
  testStepFiles/Marvelous_Champion_Nametag.3mf build/step-profile/nametag 6
java --enable-preview -Xmx8g -cp "$CP" junit.bowler.StepExportProfile \
  testStepFiles/Charming_Troll_Group_of_3.3mf build/step-profile/troll 6
PYTHONPATH=/usr/lib/freecad/lib /usr/bin/python3 \
  test/resources/scripts/step_import_profile.py build/step-profile/nametag/profile.step
```

The Java main uses real `TickToc` markers around construction, serialization and
writing. Loading the source 3MF is excluded. Discard the first iteration and
compare medians of the remaining five; durations in the first TickToc column
are cumulative from run start. Internal `toStep`/tessellation markers further
separate BRep work. The disk measurement uses the harness's `Files.writeString`,
not the production `STEPFile.write` call. The Python tool measures `Part.read`
only, excluding validity checks, and asserts valid closed solids. It is not a
measurement of the entire FreeCAD UI conversion pipeline. Run benchmark
processes alone; do not run integration tests simultaneously with timings.

## Measurement, 2026-11-22

Baseline implementation: kernel `cca7cbc3`. Optimized implementation: deferred
feature edges and one materialization of the entity text. Six iterations per
Java process; first discarded. Same fixture, JVM/classpath and 8 GiB heap.

| Fixture | Stage | Baseline median | Optimized median |
| --- | --- | ---: | ---: |
| Nametag | Constructor | 139 ms | 96 ms |
| Nametag | Constructor + serialization | 395 ms | 351 ms |
| Nametag | Harness including disk | 429 ms | 403 ms |
| Troll | Constructor | 14 ms | 9 ms |
| Troll | Constructor + serialization | 36 ms | 31 ms |
| Troll | Harness including disk | 37 ms | 32 ms |

Warm cumulative constructor+serialization observations (ms):

- Nametag baseline: 462, 532, 395, 383, 380; optimized: 488, 385, 351, 350, 341.
- Troll baseline: 50, 45, 35, 33, 36; optimized: 33, 30, 33, 31, 29.

Thus Java export wall time improved about 11% for nametag and 14% for troll
in these runs, not an order-of-magnitude gain. TickToc has millisecond resolution;
these are local measurements rather than statistical performance guarantees.
Despite its name the current troll 3MF contains only 1,750 triangles; nametag
(49,812 prepared triangles) is the larger representative case.

The baseline and optimized `DATA;` sections were **byte-for-byte identical** for
both fixtures (header timestamp excluded), with unchanged sizes 36,050,533 and
1,061,102 characters respectively. No geometry, topology, cleanup, tolerance or
planarity rule was changed. Snapshot copies retain constructor-time feature edge
semantics even if exposed CSG arrays are later edited. Extraction is synchronized
and cached on first `getEdges()`; exporting alone does not perform it. The cost
is retaining copied vertex/index arrays for the lifetime of each export object.

### FreeCAD findings and rejected experiment

FreeCAD 0.21.2 nametag baseline `Part.read` timings: 30.478, 38.350, 36.865 s.
Import is overwhelmingly slower than Java export. An experiment sharing only
exact point/direction/vector entities reduced file size by 19.3% (36.05 MB to
29.09 MB), but import took 32.923, 39.787, 37.689 s and Java serialization did
not improve. **That implementation was removed.** Smaller text alone did not
solve import cost.

Final unchanged-geometry nametag output imported in 41.427, 40.252, 39.710 s;
these final validation runs overlapped the integration suite and are not a
controlled speed comparison. Troll import baseline was .778/.522/.563 s and
optimized .538/.511/.529 s in that validation process. Since emitted geometry
is identical, no FreeCAD import speedup is claimed. Both forms validated with
identical solid/face/edge counts and volume:

- Nametag: 1 solid, 25,639 faces, 50,543 edges, volume 12824.907261855631.
- Troll: 1 solid, 754 faces, 1,627 edges, volume 9645.749593638964.

Planner diagnostics found 1 fallback region/1,365 fallback triangles for
nametag and none for troll; broad fallback was not the dominant source of
faces. No risky geometric simplification was retained.

## Validation

```sh
./gradlew spotlessApply --offline
PYTHONPATH=/usr/lib/freecad/lib ./gradlew :test --offline \
  --tests '*DeferredFeatureEdgesTest' --tests '*StepExportProgressTest' \
  --tests '*ManifoldBRepPlanTest' --tests '*StepTessellationAssociationTest' \
  --tests '*FreecadStepRoundTripIntegrationTest'
```

Result: **26 passed**, including all six FreeCAD integration tests; final test
build 2m28s. The new tests cover delayed traversal, constructor-time snapshots,
cache reuse, varied extraction parameters, and unchanged export after requesting
edges. Existing active tests cover exact mesh association, topology, holes,
slivers, snapping, placement and FreeCAD validity/volume/3MF round trips.

An expanded run also included `StepExportCoverageTest` and
`CharmingTrollBRepCoverageTest`: 13 pre-existing ignored tests were skipped and
`writtenLoopsPairWithTheirNeighbours` failed with 146 unpaired edges. That test
calls the legacy writer, not the active exporter, and fails identically with
baseline and optimized classes in the current workspace. It was not modified.

### Local environment and artifacts

The local commands used:

```sh
export JAVA_HOME="$PWD/../../../temp/jdk/zulu25.36.205-ca-fx-jdk25.0.4.1-linux_x64"
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="$PWD/../../../temp/step-geometry-gradle-home"
```

The default Gradle home lacked the cached manifold dependency, and system Java
21 could not read the existing Java25 classes. The workspace Gradle cache and
JDK above resolved both without dependency changes. The cached composite
classpath was `../../../temp/simplify-classpath.txt`; baseline sources were
read with `git show cca7cbc3:<path>` into `build/step-profile/baseline-src`, then
compiled into a separate classpath prefix (no checkout or other git writes).
A concurrent external commit captured the first implementation during this
work; its predecessor was used for the matched baseline, not the moving HEAD.

Local raw timing logs, generated STEP files, baseline classes and validation
logs remain in `build/step-profile/` (generated artifacts, not source files).
The first `test` command selected subproject test tasks too and failed because
java-bowler had no matching tests; using the explicit kernel `:test` task above
completed successfully.
