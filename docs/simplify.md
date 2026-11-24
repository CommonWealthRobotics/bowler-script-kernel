# Simplify mesh operation

`new Simplify().setNames(selectedNames)` replaces only named incoming objects in
place, preserving names, appearance and stored properties. Missing and duplicate
selection names do not create objects. No external tools or new dependencies are
required. The JSON operation type is `Simplify`.

Each setting has a fluent `setX(value)`, `getX()`, and `xParameter()` returning a
`LengthParameter` backed by the document database. Parameters are operation-scoped
and shared across selected objects. Regeneration starts from the incoming mesh,
not the previously simplified mesh. The following table lists defaults and units:

| Setting | Default | Meaning/options |
| --- | --- | --- |
| simplify | 0.5 | Native simplify distance tolerance in model units; UI bounds 0–10 |
| calculateNormals | 0 | Native normal channel index; integer choices 0, 1, 2, 3 |
| smoothByNormals | 0 | Native normal channel index; integer choices 0, 1, 2, 3 |
| normalAngle | 60 | Normal crease angle in degrees; bounds 0–180 |
| smoothOut | 0 | Smooth-out crease angle in degrees; bounds 0–180 |
| smoothness | 0 | Smooth-out sharp-edge blend; bounds 0–1 |
| refine | 0 | Edge subdivision count; integer choices 0, 1, 2, 3, 4, 8 |
| refineToLength | 0 | Maximum edge length in model units; bounds 0–100 |
| refineToTolerance | 0 | Smooth-surface tolerance in model units; bounds 0–10 |

Nonpositive method controls disable that stage; `normalAngle` and `smoothness`
are ancillary settings, not stage switches. Channel zero is reserved as the off
value; positive channel indices are passed unchanged to native Manifold. Angles
and tolerances accept decimals; native integer controls reject fractional values.
UI bounds are option metadata, not additional algorithmic clamps.

## Native pipeline

`MeshProcessing` preserves one native manifold across normal calculation,
smoothing and refinement; separate CSG round-trips would discard property channels
and tangents, making smoothing ineffective. It invokes simplify first, then
calculateNormals, smoothByNormals, smoothOut, refine, refineToLength and
refineToTolerance in that order, skipping disabled stages.

A selected normal channel is populated with `calculateNormals(normalAngle)` before
`smoothByNormals` if it was not already populated. Enabling calculateNormals alone
also converts its normals to tangents. When smoothing is enabled without an
explicit refinement stage, `refine(2)` bakes its curved surface into the output
mesh. `refineToTolerance` without smoothing leaves planar geometry unchanged, as
specified by native Manifold. `refine(1)` intentionally performs native identity
refinement; choose 2 or more to tessellate a smoothed surface.

Keys use `<operation-name>_CaDoodle_Simplify_<setting>`;
`Simplify.isParameterKey` and `ownsParameterKey` support UI filtering and listeners.
No enum or string settings exist in these native methods.
