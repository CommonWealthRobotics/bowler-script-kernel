"""Lossless-coordinate 3MF serialization of FreeCAD solid tessellations.

export_shapes accepts shapes in world coordinates (including Shape.Placement).
export_document selects solid objects, suppressing grouped features of solid
parents while retaining solids inside non-shape containers such as App::Part.
No mesh repair, coordinate quantization, or GUI modules are used.
"""

import math
import os
import sys
import tempfile
import traceback
import xml.etree.ElementTree as ET
import zipfile

import FreeCAD


CORE = "http://schemas.microsoft.com/3dmanufacturing/core/2015/02"
CONTENT_TYPES = "http://schemas.openxmlformats.org/package/2006/content-types"
RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships"
MODEL_RELATIONSHIP = "http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"
TESSELLATION_DEFLECTION = 0.1


def _element(namespace, name, attributes=None):
    return ET.Element("{%s}%s" % (namespace, name), attributes or {})


def _child(parent, tag, **attributes):
    return ET.SubElement(parent, "{%s}%s" % (CORE, tag), attributes)


def _xml(element):
    ET.register_namespace("", element.tag[1:].split("}", 1)[0])
    return ET.tostring(element, encoding="utf-8", xml_declaration=True)


def _mesh(shape, placement):
    if shape.isNull() or not shape.isValid() or not shape.isClosed():
        raise ValueError("3MF requires a valid, closed, non-null solid shape")
    if not shape.Solids or not math.isfinite(shape.Volume) or shape.Volume <= 0:
        raise ValueError("3MF requires solids with positive finite volume")
    for solid in shape.Solids:
        if (not solid.isValid() or not solid.isClosed()
                or not math.isfinite(solid.Volume) or solid.Volume <= 0):
            raise ValueError("3MF requires each solid to be valid, closed and positive-volume")
    local = shape.copy()
    local.Placement = FreeCAD.Placement()
    vertices, triangles = local.tessellate(TESSELLATION_DEFLECTION)
    if not vertices or not triangles:
        raise ValueError("Solid tessellation must contain vertices and triangles")
    world = [placement.multVec(vertex) for vertex in vertices]
    if any(not math.isfinite(value) for vertex in world for value in (vertex.x, vertex.y, vertex.z)):
        raise ValueError("Tessellation contains non-finite coordinates")
    for triangle in triangles:
        if len(triangle) != 3 or any(index < 0 or index >= len(world) for index in triangle):
            raise ValueError("Tessellation contains an invalid triangle index")
    return world, triangles


def _export(entries, output):
    model = _element(CORE, "model", {"unit": "millimeter"})
    resources = _child(model, "resources")
    build = _child(model, "build")
    count = 0
    for count, (shape, placement, name) in enumerate(entries, 1):
        vertices, triangles = _mesh(shape, placement)
        attributes = {"id": str(count), "type": "model"}
        if name is not None:
            attributes["name"] = str(name)
        obj = _child(resources, "object", **attributes)
        mesh = _child(obj, "mesh")
        vertex_elements = _child(mesh, "vertices")
        for vertex in vertices:
            _child(vertex_elements, "vertex", **{
                axis: format(getattr(vertex, axis), ".17g") for axis in ("x", "y", "z")
            })
        triangle_elements = _child(mesh, "triangles")
        for triangle in triangles:
            _child(triangle_elements, "triangle", **{
                "v%d" % (index + 1): str(value) for index, value in enumerate(triangle)
            })
        # World-coordinate vertices; omitted build transform is identity.
        _child(build, "item", objectid=str(count))
    if not count:
        raise ValueError("No solid shapes to export")

    types = _element(CONTENT_TYPES, "Types")
    for extension, content_type in (
            ("rels", "application/vnd.openxmlformats-package.relationships+xml"),
            ("model", "application/vnd.ms-package.3dmanufacturing-3dmodel+xml")):
        ET.SubElement(types, "{%s}Default" % CONTENT_TYPES,
                      Extension=extension, ContentType=content_type)
    relationships = _element(RELATIONSHIPS, "Relationships")
    ET.SubElement(relationships, "{%s}Relationship" % RELATIONSHIPS,
                  Target="/3D/3dmodel.model", Id="rel0", Type=MODEL_RELATIONSHIP)
    # Validate and serialize before opening output; atomic replacement preserves existing files.
    parts = (("3D/3dmodel.model", _xml(model)),
             ("[Content_Types].xml", _xml(types)), ("_rels/.rels", _xml(relationships)))
    output = os.path.abspath(os.fspath(output))
    descriptor, temporary = tempfile.mkstemp(prefix=".freecad-3mf-", suffix=".tmp",
                                            dir=os.path.dirname(output))
    try:
        with os.fdopen(descriptor, "wb") as stream:
            with zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED) as archive:
                for path, data in parts:
                    archive.writestr(path, data)
        os.replace(temporary, output)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def export_shapes(shapes, output, names=None):
    """Write world-placed shapes; names, when supplied, must match shape count."""
    shapes = list(shapes)
    if names is None:
        names = [None] * len(shapes)
    else:
        names = list(names)
        if len(names) != len(shapes):
            raise ValueError("Names must match the number of shapes")
    _export(((shape, shape.Placement, name) for shape, name in zip(shapes, names)), output)


def _document_entries(document):
    # App::Part can synthesize Shape without owning a Shape property.
    candidates = [obj for obj in document.Objects
                  if "Shape" in obj.PropertiesList and obj.Shape.Solids]
    grouped = {child.Name for parent in candidates for child in getattr(parent, "Group", ())}
    for obj in candidates:
        if obj.Name in grouped:
            continue
        shape = obj.Shape
        placement = shape.Placement
        if callable(getattr(obj, "getGlobalPlacement", None)) and hasattr(obj, "Placement"):
            placement = obj.getGlobalPlacement() * obj.Placement.inverse() * placement
        yield shape, placement, obj.Label


def export_document(input_fcstd, output):
    """Open an FCStd document, export its top-level solids, and close it."""
    document = FreeCAD.openDocument(os.fspath(input_fcstd))
    try:
        _export(_document_entries(document), output)
    finally:
        FreeCAD.closeDocument(document.Name)


def _main():
    if os.environ.get("CADOODLE_FREECAD_EXPORT") == "1":
        source = os.environ["CADOODLE_FREECAD_INPUT"]
        output = os.environ["CADOODLE_FREECAD_OUTPUT"]
    else:
        if len(sys.argv) < 3:
            raise ValueError("Usage: freecad_3mf.py INPUT.FCStd OUTPUT.3mf")
        source, output = sys.argv[-2:]
    export_document(source, output)


if __name__ == "__main__" or os.environ.get("CADOODLE_FREECAD_EXPORT") == "1":
    exit_code = 0
    try:
        _main()
    except BaseException:
        traceback.print_exc()
        exit_code = 1
    finally:
        sys.stdout.flush()
        sys.stderr.flush()
    # FreeCAD's console remains interactive after scripts; do not exit on ordinary imports.
    os._exit(exit_code)
