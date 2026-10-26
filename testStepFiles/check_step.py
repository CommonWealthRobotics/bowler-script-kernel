"""Validate STEP files: FreeCAD/OCC's own checks plus a scan of the raw STEP entities.

CI use (one command; exit status 0 = pass, nonzero = fail):

    STEP_FILES="out/*.step" freecadcmd check_step.py | tee gate.log
    test "${PIPESTATUS[0]}" -eq 0 && grep -q "^GATE: PASS" gate.log

Interactive / library use:

    freecadcmd -c "import sys; sys.path.insert(0, '.');
                   from check_step import check; check('a.step', 'b.step')"

`check` prints one block per file and returns True only when every file passes.
Post-import checks see the shape AFTER OCC's import-time healing, so the raw
STEP text is also scanned for what the exporter actually wrote.

Note: when STEP_FILES is set, importing this module runs the gate and exits.
"""

import glob
import math
import os
import re
import sys
import traceback

import Part

# Policy knob: sewing/healing inflates sub-shape tolerances; above this the
# shape is "valid" only because OCC loosened it. Units: mm.
MAX_TOLERANCE = 1e-4


def check(*paths, bopCheck=True, expectedVolume=None, volumeRelTol=1e-3,
          maxTolerance=MAX_TOLERANCE):
    """Validate each STEP file; True only when all pass.

    bopCheck        OCC's boolean-operation self-check; the only test that finds
                    faces intersecting each other. Slow on huge meshes.
    expectedVolume  optional; fail if |Volume - expected| / expected > volumeRelTol.
    """
    ok = True
    for path in paths:
        ok = report(path, bopCheck, expectedVolume, volumeRelTol, maxTolerance) and ok
    return ok


def scanText(path):
    """Entity counts and the file's own length uncertainty, from the raw STEP text."""
    with open(path, errors="ignore") as f:
        text = f.read()
    names = ("MANIFOLD_SOLID_BREP", "BREP_WITH_VOIDS", "OPEN_SHELL",
             "SHELL_BASED_SURFACE_MODEL", "GEOMETRIC_CURVE_SET")
    counts = {n: len(re.findall(r"\b" + n + r"\s*\(", text)) for n in names}
    uncertainties = [float(u) for u in re.findall(
        r"UNCERTAINTY_MEASURE_WITH_UNIT\s*\(\s*LENGTH_MEASURE\s*\(\s*([0-9.eE+-]+)\s*\)", text)]
    return counts, (max(uncertainties) if uncertainties else None)


def edgeUseCounts(shape):
    """Occurrences of each edge across all face wires.

    Counting wire occurrences (not distinct faces) means a seam edge, used twice by
    one face, correctly counts 2. A closed manifold solid has every edge exactly 2.
    """
    buckets = {}
    for face in shape.Faces:
        for wire in face.Wires:
            for e in wire.OrderedEdges:
                bucket = buckets.setdefault(e.hashCode(), [])
                for entry in bucket:
                    if entry[0].isSame(e):
                        entry[1] += 1
                        break
                else:
                    bucket.append([e, 1])
    return [entry for bucket in buckets.values() for entry in bucket]


def isDegenerate(e):
    return getattr(e, "Degenerated", False) or e.Length == 0  # sphere poles, cone apexes


def report(path, bopCheck=True, expectedVolume=None, volumeRelTol=1e-3,
           maxTolerance=MAX_TOLERANCE):
    problems = []
    print(f"== {path}")

    # --- raw file ---------------------------------------------------------
    fileUncertainty = None
    try:
        counts, fileUncertainty = scanText(path)
        print(f"   STEP entities       {counts}")
        print(f"   file uncertainty    {fileUncertainty}")
        solidCount = counts["MANIFOLD_SOLID_BREP"] + counts["BREP_WITH_VOIDS"]
        if solidCount != 1:
            problems.append(f"file holds {solidCount} solid BREP entities, expected 1")
        for n in ("OPEN_SHELL", "SHELL_BASED_SURFACE_MODEL", "GEOMETRIC_CURVE_SET"):
            if counts[n]:
                problems.append(f"file contains {counts[n]} {n} (not a closed solid)")
    except Exception as e:
        problems.append(f"could not scan file: {e}")

    # --- read -------------------------------------------------------------
    try:
        shape = Part.read(path)
    except Exception as e:
        problems.append(f"Part.read failed: {e}")
        return finish(problems)
    if shape.isNull():
        problems.append("file read back as a null shape")
        return finish(problems)

    valid = shape.isValid()
    solids = shape.Solids
    print(f"   ShapeType           {shape.ShapeType}")
    print(f"   isValid()           {valid}")
    print(f"   solids/shells/faces {len(solids)}/{len(shape.Shells)}/{len(shape.Faces)}")
    print(f"   edges/vertexes      {len(shape.Edges)}/{len(shape.Vertexes)}")
    print(f"   Volume              {shape.Volume}")

    if not valid:
        problems.append("isValid() is False")

    # --- OCC structural check (returns None when fine, raises when not) ----
    try:
        shape.check(bopCheck)
    except Exception as e:
        problems.append(f"check() failed: {str(e).strip()[:300]}")

    # --- topology: solid, no loose pieces, closed shells --------------------
    if len(solids) != 1:
        problems.append(f"{len(solids)} solid(s) instead of one")
    if len(shape.Faces) != sum(len(s.Faces) for s in solids):
        problems.append("faces exist outside the solid(s)")
    if len(shape.Shells) != sum(len(s.Shells) for s in solids):
        problems.append("shells exist outside the solid(s)")
    if not all(sh.isClosed() for sh in shape.Shells):
        problems.append("a shell is not closed")

    # --- volume: finite, positive (negative = inside-out), optionally expected
    vol = shape.Volume
    if not math.isfinite(vol) or vol <= 0:
        problems.append(f"volume {vol} is not positive and finite")
    elif expectedVolume and abs(vol - expectedVolume) / expectedVolume > volumeRelTol:
        problems.append(f"volume {vol:.6g} differs from expected {expectedVolume:.6g} "
                        f"by more than {volumeRelTol:.1%}")

    # --- edge use: every edge exactly twice (seams count twice by construction)
    uses = edgeUseCounts(shape)
    bad = [(e, n) for e, n in uses if n != 2 and not isDegenerate(e)]
    free = sum(1 for _, n in bad if n == 1)
    nonManifold = len(bad) - free
    print(f"   free edges          {free}")
    print(f"   non-manifold edges  {nonManifold}")
    if free:
        problems.append(f"{free} edge(s) used once (open boundary)")
    if nonManifold:
        problems.append(f"{nonManifold} edge(s) used more than twice (non-manifold)")

    # --- faces OCC could not build, or degenerate ---------------------------
    infinite = Part.Precision.infinite()
    badFaces = [f for f in shape.Faces if not (0 < f.Area < infinite)]
    print(f"   bad-area faces      {len(badFaces)}")
    if badFaces:
        problems.append(f"{len(badFaces)} face(s) with zero, NaN or infinite area")

    # --- resolution: shortest edge vs the precision OCC actually merged with
    confusion = Part.Precision.confusion()
    mergeDist = max(confusion, fileUncertainty or 0.0)
    lengths = [e.Length for e in shape.Edges if not isDegenerate(e)]
    shortest = min(lengths) if lengths else 0.0
    print(f"   shortest edge       {shortest:.6g}   (merge distance {mergeDist:g})")
    if lengths and shortest < mergeDist:
        problems.append(f"shortest edge {shortest:.3g} is below merge distance {mergeDist:g}")

    # --- tolerances inflated by sewing/healing ------------------------------
    try:
        for kind in (Part.Vertex, Part.Edge, Part.Face):
            tol = shape.getTolerance(1, kind)  # 1 = maximum
            print(f"   max tol {kind.__name__:<10}  {tol:.3g}")
            if tol > maxTolerance:
                problems.append(f"max {kind.__name__} tolerance {tol:.3g} exceeds {maxTolerance:g}")
    except Exception as e:
        print(f"   (tolerance query unavailable: {e})")

    return finish(problems)


def finish(problems):
    for problem in problems:
        print(f"   PROBLEM: {problem}")
    print(f"   -> {'OK' if not problems else 'FAILED'}\n")
    return not problems


def main():
    """CI entry point. Every outcome ends in os._exit because freecadcmd ignores
    return values and can exit 0 after an uncaught exception.

    Exit codes: 0 pass, 1 check failed, 2 no files matched, 3 exception.
    A final "GATE: PASS" / "GATE: FAIL" line is printed for CI to grep.
    """
    code = 3
    try:
        # paths come from the environment because freecadcmd mangles sys.argv
        patterns = os.environ.get("STEP_FILES", "").split()
        paths = sorted(p for pat in patterns for p in glob.glob(pat))
        if not paths:
            print("GATE: FAIL (no STEP files matched STEP_FILES=%r)" % patterns)
            code = 2
        else:
            ok = check(*paths)
            print("GATE: PASS" if ok else "GATE: FAIL")
            code = 0 if ok else 1
    except BaseException:
        traceback.print_exc()
        print("GATE: FAIL (exception)")
        code = 3
    sys.stdout.flush()
    sys.stderr.flush()
    os._exit(code)


if __name__ == "__main__" or os.environ.get("STEP_FILES"):
    main()