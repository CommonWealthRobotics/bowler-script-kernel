#!/usr/bin/env python3
"""Regenerates the generated part of the STEP test corpus.

Run with OCCT's python bindings available, for example:

    /opt/cadoodle/lib/BowlerStudioInstall/BowlerStudioInstall/build123d/\
cadoodle-b123d_v0.0.9/py/bin/python3 generate.py

The generated files are committed, so this only needs running to refresh them.
The hand-collected files (screw_ap214.step, revolve_ap214.step,
inch_units_ap214.step) are not touched; see README.md for their provenance.
"""
import os

from OCP.BRepPrimAPI import (
    BRepPrimAPI_MakeBox,
    BRepPrimAPI_MakeCone,
    BRepPrimAPI_MakeCylinder,
    BRepPrimAPI_MakeSphere,
    BRepPrimAPI_MakeTorus,
)
from OCP.BRepBuilderAPI import BRepBuilderAPI_MakeEdge, BRepBuilderAPI_MakeWire
from OCP.STEPControl import STEPControl_Reader, STEPControl_StepModelType, STEPControl_Writer
from OCP.gp import gp_Pnt

HERE = os.path.dirname(os.path.abspath(__file__))


def write_step(name, shape, mode=STEPControl_StepModelType.STEPControl_AsIs):
    path = os.path.join(HERE, name)
    writer = STEPControl_Writer()
    writer.Transfer(shape, mode)
    status = writer.Write(path)
    print("%-28s %s" % (name, "written" if str(status).endswith("RetDone") else "FAILED " + str(status)))


def make_wire(segments):
    """A wire from connected ((x,y,z), (x,y,z)) segments, so the file is wireframe only."""
    builder = BRepBuilderAPI_MakeWire()
    for start, end in segments:
        builder.Add(BRepBuilderAPI_MakeEdge(gp_Pnt(*start), gp_Pnt(*end)).Edge())
    return builder.Wire()


def main():
    write_step("box_brep.step", BRepPrimAPI_MakeBox(10.0, 10.0, 10.0).Shape())
    write_step("cylinder_brep.step", BRepPrimAPI_MakeCylinder(40.0, 100.0).Shape())
    write_step("sphere_brep.step", BRepPrimAPI_MakeSphere(25.0).Shape())
    write_step("cone_brep.step", BRepPrimAPI_MakeCone(30.0, 10.0, 50.0).Shape())
    write_step("torus_brep.step", BRepPrimAPI_MakeTorus(30.0, 8.0).Shape())

    # a closed square wire: curve geometry with no faces at all
    write_step("wireframe.step",
               make_wire([((0.0, 0.0, 0.0), (50.0, 0.0, 0.0)),
                          ((50.0, 0.0, 0.0), (50.0, 30.0, 0.0)),
                          ((50.0, 30.0, 0.0), (0.0, 30.0, 0.0)),
                          ((0.0, 30.0, 0.0), (0.0, 0.0, 0.0))]))


if __name__ == "__main__":
    main()
