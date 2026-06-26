#!/bin/bash
# run_all_b123d.sh
# Reads b123dparams.json and runs build123d_cli for every object,
# using default values for optional params and sensible fill-ins for required ones.

BUILD123DDIR=$HOME/bin/BowlerStudioInstall/build123d/cadoodle-b123d_v0.0.9
BUILD123D="$BUILD123DDIR/py/bin/python -m build123d_cli"
SCHEMA=b123dparams.json

# ── colour helpers ──────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'   # no colour

# ── (re)generate schema ─────────────────────────────────────────────────────
echo "Regenerating schema → $SCHEMA"
$BUILD123D --json-schema > "$SCHEMA"

# ── required-param fill-in table ────────────────────────────────────────────
# Maps parameter names that have no default (required=true, default=null)
# to a reasonable scalar value.  Add more rows as new objects appear.
required_value() {
    local param="$1"
    case "$param" in
        number_of_teeth)        echo "16" ;;
        size)                   echo "M6-1" ;;
        length)                 echo "10.0" ;;
        nps)                    echo "1" ;;
        flange_class)           echo "150" ;;
        # fastener takes an object – skip objects that need it (handled below)
        fastener)               echo "__SKIP__" ;;
        *)                      echo "__SKIP__" ;;   # unknown required param → skip
    esac
}

# ── counters ────────────────────────────────────────────────────────────────
total=0
passed=0
failed=0
skipped=0

# ── iterate libraries → objects ─────────────────────────────────────────────
libs=$(jq -r 'keys[]' "$SCHEMA")

for lib in $libs; do
    objects=$(jq -r --arg lib "$lib" '.[$lib] | keys[]' "$SCHEMA")

    for obj in $objects; do
        total=$((total + 1))
        out_dir="${obj}"

        # Build argument list from parameters
        args=""
        skip_obj=false

        # Collect parameters as a JSON array for this object
        params_json=$(jq -c --arg lib "$lib" --arg obj "$obj" \
            '.[$lib][$obj].parameters // []' "$SCHEMA")

        # Walk each parameter
        while IFS= read -r param_entry; do
            pname=$(echo "$param_entry" | jq -r '.name')
            ptype=$(echo "$param_entry" | jq -r '.type')
            required=$(echo "$param_entry" | jq -r '.required')
            default=$(echo "$param_entry" | jq -c '.default')

            # Convert param name underscores→hyphens for CLI flag
            flag="--$(echo "$pname" | tr '_' '-')"

            if [[ "$required" == "true" && "$default" == "null" ]]; then
                # Required with no default – look up a fill-in value
                fill=$(required_value "$pname")
                if [[ "$fill" == "__SKIP__" ]]; then
                    echo -e "${YELLOW}  SKIP${NC} ${lib}/${obj}: required param '${pname}' (type: ${ptype}) has no fill-in value"
                    skip_obj=true
                    break
                fi
                args="$args $flag $fill"
            fi
            # Optional params: let build123d_cli use its own defaults (no flag needed)
        done < <(echo "$params_json" | jq -c '.[]')

        if [[ "$skip_obj" == "true" ]]; then
            skipped=$((skipped + 1))
            continue
        fi

        # Create output directory
        mkdir -p "$out_dir"

        # Assemble full command
        cmd="$BUILD123D $lib $obj$args export_directory ${out_dir}/"

        echo ""
        echo -e "${YELLOW}▶ Running:${NC} $cmd"

        # Run it
        if output=$($cmd 2>&1); then
            # Check for at least one STL in the output dir
            stl_files=$(find "$out_dir" -maxdepth 2 -name "*.stl" 2>/dev/null)
            if [[ -n "$stl_files" ]]; then
                echo -e "${GREEN}✔ STL produced in ${out_dir}/:${NC}"
                while IFS= read -r stl; do
                    echo -e "   ${GREEN}${stl}${NC}"
                done <<< "$stl_files"
                passed=$((passed + 1))
            else
                echo -e "${RED}✘ Command succeeded but no STL found in ${out_dir}/${NC}"
                echo "$output"
                failed=$((failed + 1))
            fi
        else
            echo -e "${RED}✘ FAILED: ${lib}/${obj}${NC}"
            echo -e "${RED}  Exit code: $?${NC}"
            echo "$output" | sed "s/^/  ${RED}|${NC} /"
            failed=$((failed + 1))
        fi
    done
done

# ── summary ─────────────────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════"
echo -e " Total objects : $total"
echo -e " ${GREEN}Passed         : $passed${NC}"
echo -e " ${RED}Failed         : $failed${NC}"
echo -e " ${YELLOW}Skipped        : $skipped${NC}"
echo "══════════════════════════════════════════"