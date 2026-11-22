"""Headless STEP validation and 3MF export; no mesh repair."""
import argparse
import importlib.util
import json
import math
from pathlib import Path

import FreeCAD
import Mesh
import Part


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("step")
    parser.add_argument("output")
    parser.add_argument("report")
    parser.add_argument("--expected-volume", type=float)
    parser.add_argument("--exporter", required=True)
    args = parser.parse_args()
    shape = Part.read(args.step)
    assert not shape.isNull(), "STEP contains no shape"
    assert shape.isValid(), "STEP shape is invalid"
    assert shape.Solids, "STEP contains no solids"
    for index, solid in enumerate(shape.Solids):
        assert solid.isValid(), f"Solid {index} is invalid"
        assert solid.isClosed(), f"Solid {index} is open"
        assert math.isfinite(solid.Volume) and solid.Volume > 0, f"Solid {index} has invalid volume"
    if args.expected_volume is not None:
        assert abs(shape.Volume - args.expected_volume) <= max(1e-5, args.expected_volume * 1e-6), (
            f"STEP volume {shape.Volume} differs from source {args.expected_volume}")
    document = FreeCAD.newDocument("RoundTrip")
    try:
        feature = document.addObject("Part::Feature", "ImportedStep")
        feature.Shape = shape
        document.recompute()
        spec = importlib.util.spec_from_file_location("cadoodle_3mf", args.exporter)
        exporter = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(exporter)
        exporter.export_shapes([feature.Shape], args.output)
        mesh = Mesh.Mesh(args.output)
        assert mesh.CountFacets > 0, "Exported mesh is empty"
        report = {"solids": len(shape.Solids), "volume": shape.Volume,
                  "facets": mesh.CountFacets}
        Path(args.report).write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report), flush=True)
    finally:
        FreeCAD.closeDocument(document.Name)


if __name__ == "__main__":
    main()
