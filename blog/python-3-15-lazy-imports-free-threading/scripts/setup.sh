#!/usr/bin/env bash
# Create one virtualenv per interpreter under .venvs/, each with the two
# third-party packages fleetctl imports (rich, requests - both pure Python, so
# they install on a release candidate before binary wheels exist).
#
#   ./scripts/setup.sh                 # 3.14, 3.14t, 3.15, 3.15t
#   PYTHONS="3.15 3.15t" ./scripts/setup.sh
#
# Needs a recent uv: 0.8.17 does not list the 3.15 builds, 0.12.19 does.
set -euo pipefail
cd "$(dirname "$0")/.."

pythons="${PYTHONS:-3.14 3.14t 3.15 3.15t}"
uv python install $pythons

for p in $pythons; do
  venv=".venvs/$p"
  uv venv --quiet --allow-existing --python "$p" "$venv"
  VIRTUAL_ENV="$venv" uv pip install --quiet --python "$venv/bin/python" rich requests
  "$venv/bin/python" - "$p" <<'PY'
import sys, sysconfig
ft = bool(sysconfig.get_config_var("Py_GIL_DISABLED"))
print(f"{sys.argv[1]:6} -> {sys.version.split()[0]:10} free-threaded={ft}")
PY
done
