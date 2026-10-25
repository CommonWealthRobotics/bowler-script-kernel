"""Validate STEP files with FreeCAD's own built-in checks.

Run it from FreeCAD's interpreter:

    freecadcmd -c "import sys; sys.path.insert(0, '.');
                   from check_step import check; check('a.step', 'b.step')"

`check` prints one block per file and returns True only when every check passes.

Every test below is FreeCAD/OpenCascade's own; nothing here re-implements a
tolerance. The checks, and why each matters for this exporter:

  isValid()
      False means OCC could not build the topology at all.

  check(runBopCheck)
      The detailed structural check. It reports "Self-intersecting wire" and
      "Unorientable shape", which are exactly the errors that make a face come
      back garbled, and it raises rather than returning on some of them.

  Precision.confusion()
      1e-7. OCC merges any two vertices closer than this as it reads the file,
      so a model whose real features are finer than this cannot survive the
      round trip: edges lose an endpoint and their partner face is left with a
      free edge. Comparing the shortest edge against it says whether a file is
      inside OCC's resolution.

  Free edges
      An edge used by only one face. A closed solid has none; they are what
      makes OCC give up on the shell and hand back a Compound of loose pieces.

  Infinite-area faces
      A face whose area is Precision.infinite() (2e100) is one OCC failed to
      construct: it keeps the face but no loop, which reads as a large plane
      lying across the model.

  Solid count
      One solid is what a watertight mesh should produce.
"""

import Part


def check(*paths, bopCheck=False):
	"""Validate each STEP file; True only when all of them pass.

	`bopCheck` adds OCC's boolean-operation self-check, which is thorough but
	takes minutes on a mesh with thousands of faces.
	"""
	ok = True
	for path in paths:
		ok = report(path, bopCheck) and ok
	return ok


def report(path, bopCheck=False):
	shape = Part.read(path)
	problems = []

	print(f"== {path}")
	print(f"   ShapeType           {shape.ShapeType}")
	print(f"   isValid()           {shape.isValid()}")
	print(f"   solids/shells/faces {len(shape.Solids)}/{len(shape.Shells)}/{len(shape.Faces)}")
	print(f"   edges/vertexes      {len(shape.Edges)}/{len(shape.Vertexes)}")
	print(f"   Volume              {shape.Volume}")

	if not shape.isValid():
		problems.append("isValid() is False")

	# check() reports the structural errors and raises on some of them
	try:
		errors = shape.check(bopCheck) or []
		reported = [e for e in errors if e and e != "No error"]
		if reported:
			problems.append(f"check(): {len(reported)} error(s), first {reported[0]!r}")
	except Exception as e:
		problems.append(f"check() raised: {e}")

	# OCC's own merge distance, and the finest edge the file actually carries
	confusion = Part.Precision.confusion()
	lengths = [e.Length for e in shape.Edges if e.Length > 0]
	shortest = min(lengths) if lengths else 0.0
	print(f"   shortest edge       {shortest:.6g}   (Precision.confusion {confusion:g})")
	if 0 < shortest < confusion:
		problems.append(f"shortest edge {shortest:.3g} is below OCC's merge distance {confusion:g}")

	# a closed solid has every edge shared by exactly two faces
	free = [e for e in shape.Edges if len(shape.ancestorsOfType(e, Part.Face)) == 1]
	print(f"   free edges          {len(free)}")
	if free:
		problems.append(f"{len(free)} edge(s) used by only one face")

	# a face OCC could not build has no loop, so its area is the infinite sentinel
	infinite = Part.Precision.infinite()
	huge = [f for f in shape.Faces if f.Area >= infinite]
	print(f"   infinite-area faces {len(huge)}")
	if huge:
		problems.append(f"{len(huge)} face(s) OCC could not construct (area {infinite:g})")

	if len(shape.Solids) != 1:
		problems.append(f"{len(shape.Solids)} solid(s) instead of one")

	for problem in problems:
		print(f"   PROBLEM: {problem}")
	print(f"   -> {'OK' if not problems else 'FAILED'}\n")
	return not problems
