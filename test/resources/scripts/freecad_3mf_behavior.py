"""Run with FreeCAD's Python: freecad_3mf_behavior.py /path/to/freecad_3mf.py."""

import importlib.util
import math
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import xml.etree.ElementTree as ET
import zipfile

import FreeCAD
import Mesh
import Part


MODULE_PATH = Path(sys.argv[1]).resolve()
spec = importlib.util.spec_from_file_location("freecad_3mf", MODULE_PATH)
exporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exporter)
NS = {"m": exporter.CORE}


def read_meshes(path):
    with zipfile.ZipFile(path) as archive:
        assert set(archive.namelist()) == {"3D/3dmodel.model", "[Content_Types].xml", "_rels/.rels"}
        model = ET.fromstring(archive.read("3D/3dmodel.model"))
        relationships = ET.fromstring(archive.read("_rels/.rels"))
        assert relationships[0].attrib["Target"] == "/3D/3dmodel.model"
        ET.fromstring(archive.read("[Content_Types].xml"))
    assert model.attrib["unit"] == "millimeter"
    objects = model.findall("m:resources/m:object", NS)
    items = model.findall("m:build/m:item", NS)
    assert [obj.attrib["id"] for obj in objects] == [item.attrib["objectid"] for item in items]
    assert all("transform" not in item.attrib for item in items)
    return [(obj.attrib.get("name"),
             [tuple(float(vertex.attrib[axis]) for axis in ("x", "y", "z"))
              for vertex in obj.findall("m:mesh/m:vertices/m:vertex", NS)],
             [tuple(int(triangle.attrib[index]) for index in ("v1", "v2", "v3"))
              for triangle in obj.findall("m:mesh/m:triangles/m:triangle", NS)])
            for obj in objects]


def volume(vertices, triangles):
    # Translate near the origin to avoid cancellation in the test's volume integral.
    origin = FreeCAD.Vector(*vertices[0])
    points = [FreeCAD.Vector(*point) - origin for point in vertices]
    return abs(math.fsum(points[a].dot(points[b].cross(points[c])) / 6
                         for a, b, c in triangles))


class ThreeMFBehavior(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.output = self.directory / "result.3mf"

    def document(self):
        doc = FreeCAD.newDocument("ThreeMFBehavior")
        name = doc.Name
        self.addCleanup(lambda: FreeCAD.closeDocument(name)
                        if name in FreeCAD.listDocuments() else None)
        return doc

    def assert_mesh(self, mesh, shape, placement):
        _, vertices, triangles = mesh
        local = shape.copy()
        local.Placement = FreeCAD.Placement()
        expected, expected_triangles = local.tessellate(0.1)
        expected = [placement.multVec(point) for point in expected]
        self.assertEqual(triangles, [tuple(triangle) for triangle in expected_triangles])
        self.assertEqual(len(vertices), len(expected))
        for actual, point in zip(vertices, expected):
            for value, coordinate in zip(actual, (point.x, point.y, point.z)):
                self.assertAlmostEqual(value, coordinate, delta=1e-12)
        self.assertAlmostEqual(volume(vertices, triangles), shape.Volume, delta=1e-9)

    def test_double_precision_multiple_objects_names_and_input_placement(self):
        first = Part.makeBox(2, 3, 4)
        first.Placement.Base = FreeCAD.Vector(-50.242050318772655, 2.123456789123456, 0)
        second = Part.makeBox(3, 4, 5)
        second.Placement = FreeCAD.Placement(FreeCAD.Vector(12, -7, 23),
                                            FreeCAD.Rotation(FreeCAD.Vector(1, 2, 3), 37))
        original = [shape.Placement.toMatrix() for shape in (first, second)]
        names = ["pièce <精密> & \"one\"", "second"]
        exporter.export_shapes([first, second], self.output, names)
        meshes = read_meshes(self.output)
        self.assertEqual(Mesh.Mesh(str(self.output)).CountFacets,
                         sum(len(mesh[2]) for mesh in meshes))
        self.assertEqual([mesh[0] for mesh in meshes], names)
        self.assertIn(-50.242050318772655, [point[0] for point in meshes[0][1]])
        for mesh, shape, matrix in zip(meshes, (first, second), original):
            self.assert_mesh(mesh, shape, shape.Placement)
            self.assertEqual(shape.Placement.toMatrix().A, matrix.A)

    def nested_document(self):
        doc = self.document()
        outer = doc.addObject("App::Part", "Outer")
        inner = doc.addObject("App::Part", "Inner")
        outer.addObject(inner)
        feature = doc.addObject("Part::Feature", "NestedBox")
        inner.addObject(feature)
        feature.Shape = Part.makeBox(2, 3, 4)
        outer.Placement = FreeCAD.Placement(FreeCAD.Vector(31, -17, 5),
                                           FreeCAD.Rotation(FreeCAD.Vector(0, 0, 1), 90))
        inner.Placement = FreeCAD.Placement(FreeCAD.Vector(4, 8, -3),
                                           FreeCAD.Rotation(FreeCAD.Vector(1, 0, 0), 28))
        feature.Placement = FreeCAD.Placement(FreeCAD.Vector(1, -2, 6),
                                             FreeCAD.Rotation(FreeCAD.Vector(0, 1, 0), 17))
        doc.recompute()
        return doc, feature, outer.Placement * inner.Placement * feature.Placement

    def test_nested_container_world_translation_and_rotation(self):
        doc, feature, world = self.nested_document()
        source = self.directory / "nested.FCStd"
        expected_shape = feature.Shape.copy()
        expected_name = feature.Label
        doc.saveAs(str(source))
        FreeCAD.closeDocument(doc.Name)
        original_document = source.read_bytes()
        exporter.export_document(source, self.output)
        meshes = read_meshes(self.output)
        self.assertEqual(len(meshes), 1)
        self.assertEqual(meshes[0][0], expected_name)
        self.assert_mesh(meshes[0], expected_shape, world)
        self.assertEqual(source.read_bytes(), original_document)

    def test_solid_parent_does_not_duplicate_grouped_features(self):
        doc = self.document()
        container = doc.addObject("App::Part", "Container")
        body = doc.addObject("Part::Feature", "SolidParent")
        container.addObject(body)
        feature = doc.addObject("Part::Feature", "SolidFeature")
        feature.Shape = Part.makeBox(2, 3, 4)
        body.addProperty("App::PropertyLinkList", "Group")
        body.Group = [feature]
        body.Shape = feature.Shape.copy()
        other = doc.addObject("Part::Feature", "Separate")
        other.Shape = Part.makeBox(3, 4, 5)
        doc.recompute()
        entries = list(exporter._document_entries(doc))
        self.assertEqual([entry[2] for entry in entries], [body.Label, other.Label])
        exporter._export(entries, self.output)
        self.assertEqual(len(read_meshes(self.output)), 2)

    def test_reject_null_open_empty_and_mismatched_names_without_output(self):
        open_face = Part.makePlane(2, 3)
        for shapes, names in (([Part.Shape()], None), ([open_face], None),
                              ([], None), ([Part.makeBox(1, 2, 3)], [])):
            with self.subTest(shapes=shapes, names=names):
                with self.assertRaises(ValueError):
                    exporter.export_shapes(shapes, self.output, names)
                self.assertFalse(self.output.exists())
        self.output.write_bytes(b"existing output")
        with self.assertRaises(ValueError):
            exporter.export_shapes([Part.makeBox(1, 2, 3), open_face], self.output)
        self.assertEqual(self.output.read_bytes(), b"existing output")

    def test_failed_write_cleans_partial_and_preserves_existing_output(self):
        self.output.write_bytes(b"existing output")
        with mock.patch.object(exporter.zipfile.ZipFile, "writestr", side_effect=OSError("write failed")):
            with self.assertRaises(OSError):
                exporter.export_shapes([Part.makeBox(1, 2, 3)], self.output)
        self.assertEqual(self.output.read_bytes(), b"existing output")
        self.assertEqual(list(self.directory.iterdir()), [self.output])

    def test_reject_nonpositive_nonfinite_volume_and_empty_mesh(self):
        solid = Part.makeBox(1, 2, 3)
        shape = mock.Mock(wraps=solid)
        shape.Placement = solid.Placement
        shape.Solids = solid.Solids
        for invalid_volume in (0, -1, float("inf"), float("nan")):
            shape.Volume = invalid_volume
            with self.subTest(volume=invalid_volume), self.assertRaises(ValueError):
                exporter.export_shapes([shape], self.output)
            self.assertFalse(self.output.exists())
        shape.Volume = solid.Volume
        local = mock.Mock()
        shape.copy.return_value = local
        for tessellation in (([], []), ([FreeCAD.Vector(0, 0, 0)], [])):
            local.tessellate.return_value = tessellation
            with self.subTest(tessellation=tessellation), self.assertRaises(ValueError):
                exporter.export_shapes([shape], self.output)
            self.assertFalse(self.output.exists())

    def test_cli_normal_and_freecad_alternate_module_name(self):
        doc, _, _ = self.nested_document()
        source = self.directory / "cli.FCStd"
        doc.saveAs(str(source))
        FreeCAD.closeDocument(doc.Name)
        environment = os.environ.copy()
        environment.pop("CADOODLE_FREECAD_EXPORT", None)
        result = subprocess.run([sys.executable, str(MODULE_PATH), str(source), str(self.output)],
                                env=environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.assertEqual(result.returncode, 0, result.stderr.decode())
        self.assertEqual(len(read_meshes(self.output)), 1)
        environment.update(CADOODLE_FREECAD_EXPORT="1", CADOODLE_FREECAD_INPUT=str(source),
                           CADOODLE_FREECAD_OUTPUT=str(self.output))
        result = subprocess.run([sys.executable, "-c",
                                 "import runpy; runpy.run_path(%r)" % str(MODULE_PATH)],
                                env=environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.assertEqual(result.returncode, 0, result.stderr.decode())
        self.assertEqual(len(read_meshes(self.output)), 1)
        environment["CADOODLE_FREECAD_INPUT"] = str(self.directory / "missing.FCStd")
        result = subprocess.run([sys.executable, "-c",
                                 "import runpy; runpy.run_path(%r)" % str(MODULE_PATH)],
                                env=environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main(argv=[sys.argv[0]], verbosity=2)
