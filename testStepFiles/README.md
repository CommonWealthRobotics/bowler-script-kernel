# STEP test files

Fixtures for `junit.bowler.StepFileTest`, which round trips geometry through
`STEPFile` and loads files written by other tools. Tests run from the module
root, so the test refers to this directory as `testStepFiles`.

## Round trip fixtures

Written by `STEPFile` itself (via `GenerateRoundTripFixtures.java`), so loading
them proves an export survives a read:

| file | geometry | mesh | edges |
| --- | --- | --- | --- |
| `cuboid_export.step` | `Cube(10)` | 8 vertices, 12 triangles | 12 |
| `hole_export.step` | `Cube(20,20,4)` minus a sphere | 142 vertices, 200 triangles | 49 |
| `cylinder_export.step` | two unioned `Cylinder`s | 502 vertices, 856 triangles | 196 |

Regenerate with the module's classes on the classpath:

    java -cp <classes> GenerateRoundTripFixtures testStepFiles

## Generated BRep files

Written by `generate.py` using OCCT, and committed so the tests need no OCCT:

| file | geometry |
| --- | --- |
| `box_brep.step` | `BRepPrimAPI_MakeBox(10,10,10)`: a faceted solid |
| `cylinder_brep.step` | `BRepPrimAPI_MakeCylinder(40,100)`: planar caps, curved side |
| `cone_brep.step` | `BRepPrimAPI_MakeCone(30,10,50)` |
| `sphere_brep.step` | `BRepPrimAPI_MakeSphere(25)`: a single closed curved face |
| `torus_brep.step` | `BRepPrimAPI_MakeTorus(30,8)`: a closed curved face |
| `wireframe.step` | a closed square wire, curve geometry with no faces |

Regenerate with OCCT's python bindings, for instance:

    /opt/cadoodle/lib/BowlerStudioInstall/BowlerStudioInstall/build123d/\
cadoodle-b123d_v0.0.9/py/bin/python3 generate.py

## Files collected from elsewhere

These exercise encodings that OCCT's writer does not emit.

| file | source | what it covers |
| --- | --- | --- |
| `screw_ap214.step` | OCCT `data/step/screw.step` | an AP214 part whose edges are B-spline curves |
| `revolve_ap214.step` | stepcode `data/ap214e3/io1-cm-214.stp` | a CoCreate revolved part, circles and B-splines |
| `inch_units_ap214.step` | stepcode `data/ap214e3/dm1-id-214.stp` | a conversion based unit (inch), several solids, faces with holes |

Origins:

* OCCT: <https://github.com/Open-Cascade-SAS/OCCT> (`data/step`)
* stepcode: <https://github.com/stepcode/stepcode> (`data/ap214e3`)

## What the reader does not cover

* A curved face is not tessellated, so a solid whose faces are curved (a sphere,
  a torus, or the side of a cylinder) contributes its edges but no mesh. Faces
  that are planar, or faceted within their own loops, do tessellate.
* Assemblies are read as a flat list of shapes; the placement transforms that
  position one part relative to another are not applied, so a multi-part file
  yields its parts in the file's own coordinates.
* A surface of revolution or extrusion is read from its edges only.
