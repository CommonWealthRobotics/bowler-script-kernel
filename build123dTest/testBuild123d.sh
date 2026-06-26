#!/bin/bash
# run_all_b123d.sh
# Reads b123dparams.json, runs build123d_cli for every object using the JSON's own
# defaults first, then a hard-coded fallback table for required params with null defaults.
# Writes coloured terminal output AND a plain-text report.txt simultaneously.

BUILD123DDIR=$HOME/bin/BowlerStudioInstall/build123d/cadoodle-b123d_v0.0.9
BUILD123D="$BUILD123DDIR/py/bin/python -m build123d_cli"
SCHEMA=b123dparams.json
REPORT=report.txt

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Write to both terminal (with colour) and report.txt (plain)
report() {
    local plain
    # Strip ANSI colour codes for the report file
    plain=$(echo -e "$1" | sed 's/\x1b\[[0-9;]*m//g')
    echo -e "$1"
    echo "$plain" >> "$REPORT"
}

# ── (re)generate schema ─────────────────────────────────────────────────────
echo "Regenerating schema → $SCHEMA"
$BUILD123D --json-schema > "$SCHEMA"

# Clear report
> "$REPORT"
report "build123d_cli batch run — $(date)"
report "========================================"

# ── build the args for one object using Python (no jq needed) ───────────────
# We call a small Python helper that reads the schema and prints CLI flags.
# It uses the JSON default when available, and falls back to the table below
# for required params with null defaults.
#
# Exit codes from the helper:
#   0  → args printed on stdout, safe to run
#   2  → object should be skipped (printed reason on stderr)

build_args() {
    local lib="$1" obj="$2"
    python3 - "$SCHEMA" "$lib" "$obj" << 'PYEOF'
import sys, json

schema_file, lib, obj = sys.argv[1], sys.argv[2], sys.argv[3]

with open(schema_file) as f:
    schema = json.load(f)

params = schema.get(lib, {}).get(obj, {}).get("parameters", [])

# Fallback values for required params whose default is null.
# These are the last resort; the JSON default is always preferred first.
FALLBACK = {
    # py_gearworks
    "number_of_teeth":    "16",
    # bd_warehouse scalars
    "size":               "M6-1",
    "length":             "10.0",
    "major_diameter":     "6.0",
    "pitch":              "1.0",
    "diameter":           "6.0",
    "thread_angle":       "30.0",
    "apex_radius":        "0.3",
    "apex_width":         "0.1",
    "root_radius":        "0.5",
    "root_width":         "0.2",
    "bcd":                "30.0",
    "bolt_hole_count":    "4",
    "bolt_hole_diameter": "5.0",
    "stub_length":        "10.0",
    "num_teeth":          "16",
    # bd_warehouse Literals (representative values)
    "nps":                "1",
    "flange_class":       "150",
    "material":           "Soft",
    "identifier":         "40",
    "pipe_identifier":    "40",
    "motor_type":         "Nema17",
    "shim_type":          "M6",
    "rail_size":          "20x20",
    "shaft_diameter":     "5",
    "inside_diameter":    "5",
    # bd_warehouse SpurGear specifics
    "module":             "1.0",
    "tooth_count":        "16",
    "pressure_angle":     "0.3490658503988659",
    "thickness":          "5.0",
    # bd_warehouse Sprocket
    # Literal length for AluminumSpacer
    # Scalars that are bool
    "eccentric":          "false",
    "fastener_type":      "iso7091",
}

# Types we cannot express as a CLI scalar — skip the whole object
OBJECT_TYPES = {"HeatSetNut", "Union", "Bearing"}

args = []
for p in params:
    name    = p["name"]
    ptype   = p["type"]
    default = p["default"]
    required = p.get("required", False)

    flag = "--" + name.replace("_", "-")

    if default is not None:
        # Use the JSON's own default
        if isinstance(default, list):
            # ndarray / tuple — pass as space-separated numbers joined inline;
            # skip emitting this flag and let the CLI use its own default
            pass
        elif isinstance(default, bool):
            args += [flag, str(default).lower()]
        else:
            args += [flag, str(default)]
    elif required:
        # Required, no JSON default — need a fallback
        if ptype in OBJECT_TYPES:
            print(f"SKIP: {lib}/{obj} — required param '{name}' is type '{ptype}' (object, not scalar)", file=sys.stderr)
            sys.exit(2)
        if "Union" in ptype:
            print(f"SKIP: {lib}/{obj} — required param '{name}' is a Union type", file=sys.stderr)
            sys.exit(2)
        val = FALLBACK.get(name)
        if val is None:
            print(f"SKIP: {lib}/{obj} — required param '{name}' (type {ptype}) has no fallback", file=sys.stderr)
            sys.exit(2)
        args += [flag, val]
    # else: optional with null default — omit flag, let CLI choose

print(" ".join(args))
sys.exit(0)
PYEOF
}

# ── counters ─────────────────────────────────────────────────────────────────
total=0; passed=0; failed=0; skipped=0

# ── iterate libraries → objects ───────────────────────────────────────────────
libs=$(python3 -c "import json,sys; d=json.load(open('$SCHEMA')); print('\n'.join(d.keys()))")

for lib in $libs; do
    objects=$(python3 -c "import json; d=json.load(open('$SCHEMA')); print('\n'.join(d['$lib'].keys()))")
    for obj in $objects; do
        total=$((total + 1))
        out_dir="${obj}"

        # Build CLI args via the Python helper
        skip_reason=""
        extra_args=$(build_args "$lib" "$obj" 2>/tmp/b123d_skip_reason)
        helper_rc=$?

        if [[ $helper_rc -eq 2 ]]; then
            skip_reason=$(cat /tmp/b123d_skip_reason)
            report "${YELLOW}  SKIP${NC} $skip_reason"
            skipped=$((skipped + 1))
            continue
        fi

        mkdir -p "$out_dir"
        cmd="$BUILD123D $lib $obj $extra_args export_directory ${out_dir}/"

        report ""
        report "${YELLOW}▶ ${lib}/${obj}${NC}"
        report "  CMD: $cmd"

        # Run, capturing both stdout and stderr
        run_output=$($cmd 2>&1)
        run_rc=$?

        # Always write the program output to the report
        if [[ -n "$run_output" ]]; then
            while IFS= read -r line; do
                report "  | $line"
            done <<< "$run_output"
        fi

        if [[ $run_rc -ne 0 ]]; then
            report "${RED}  ✘ FAILED (exit $run_rc): ${lib}/${obj}${NC}"
            failed=$((failed + 1))
        else
            stl_files=$(find "$out_dir" -maxdepth 2 -name "*.stl" 2>/dev/null)
            if [[ -n "$stl_files" ]]; then
                while IFS= read -r stl; do
                    report "${GREEN}  ✔ STL: ${stl}${NC}"
                done <<< "$stl_files"
                passed=$((passed + 1))
            else
                report "${RED}  ✘ No STL found in ${out_dir}/ (exit 0 but no output)${NC}"
                failed=$((failed + 1))
            fi
        fi
    done
done

# ── summary ───────────────────────────────────────────────────────────────────
report ""
report "========================================"
report "  Total   : $total"
report "${GREEN}  Passed  : $passed${NC}"
report "${RED}  Failed  : $failed${NC}"
report "${YELLOW}  Skipped : $skipped${NC}"
report "========================================"
report "Report written to: $REPORT"