"""Run using system Python with PYTHONPATH=/usr/lib/freecad/lib."""
import json
import sys
import time
import FreeCAD
import Part

for filename in sys.argv[1:]:
    for run in range(3):
        start = time.perf_counter()
        shape = Part.read(filename)
        elapsed = time.perf_counter() - start
        assert shape.isValid() and shape.Solids
        assert all(s.isClosed() and s.isValid() for s in shape.Solids)
        print(json.dumps(dict(file=filename, run=run, read_seconds=elapsed,
                              solids=len(shape.Solids), faces=len(shape.Faces),
                              edges=len(shape.Edges), volume=shape.Volume)), flush=True)
        del shape
