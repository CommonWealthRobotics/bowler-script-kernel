#!/bin/bash
BUILD123DDIR=$HOME/bin/BowlerStudioInstall/build123d/cadoodle-b123d_v0.0.9
BUILD123D="$BUILD123DDIR/py/bin/python -m build123d_cli"
SCHEMA=b123dparams.json
$BUILD123D --json-schema >$SCHEMA

mkdir -p SpurGear/
$BUILD123D py_gearworks SpurGear --number-of-teeth 23 --height 5.0 --module 1.0 export_directory SpurGear/

cat $SCHEMA
