#!/bin/sh
# Run every Stage-1 check from the repo root (PowerShell: use Git Bash / WSL, or run the .py files with `python`).
python3 tools/verify/check_all.py
python3 tools/verify/check_binding.py
python3 tools/verify/check_calls.py
sh tools/verify/javac_pass.sh
python3 tools/verify/check_sql.py   # Stage 2 (needs: pip install pglast)
