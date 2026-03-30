#!/usr/bin/env bash
set -euo pipefail

SHIM_DIR="${CACHET_DEVENV_TASK_SHIMS_DIR:-.devenv/task-shims}"
RUNNER="$SHIM_DIR/.devenv-task-runner"

if [[ ! -f "devenv.nix" ]]; then
  exit 0
fi

mkdir -p "$SHIM_DIR"

cat > "$RUNNER" << 'EOF'
#!/usr/bin/env bash
set -euo pipefail

task_name="$(basename "$0")"
exec devenv tasks run "$task_name"
EOF

chmod +x "$RUNNER"

find "$SHIM_DIR" -mindepth 1 -maxdepth 1 ! -name '.devenv-task-runner' -delete

task_count=0
while IFS= read -r task_name; do
  [[ -z "$task_name" ]] && continue
  ln -sf ".devenv-task-runner" "$SHIM_DIR/$task_name"
  task_count=$((task_count + 1))
done < <(grep -Eo 'scripts\."[^"]+"' devenv.nix | sed -E 's/scripts\."([^"]+)"/\1/' | sort -u)

echo "Synced $task_count task shims to $SHIM_DIR"
