#!/usr/bin/env bash
# PreToolUse hook: auto-allow compound Bash commands when every
# subcommand matches an already-allowed prefix.
# If any subcommand is unrecognised, exit silently (normal permission flow).

set -euo pipefail

cmd=$(jq -r '.tool_input.command')

# Split on compound operators: &&  ||  ;  |
# Order matters: replace multi-char operators before single-char ones.
mapfile -t parts < <(
  echo "$cmd" \
    | sed 's/ *&& */\n/g' \
    | sed 's/ *|| */\n/g' \
    | sed 's/ *; */\n/g'  \
    | sed 's/ *| */\n/g'
)

for part in "${parts[@]}"; do
  # trim leading/trailing whitespace
  part="${part#"${part%%[![:space:]]*}"}"
  part="${part%"${part##*[![:space:]]}"}"
  [ -z "$part" ] && continue

  case "$part" in
    git\ *|git)       ;;
    gh\ *|gh)         ;;
    echo\ *|echo)     ;;
    devenv\ *)        ;;
    true|false|:)     ;;
    read\ *|cat\ *)   ;;
    head\ *|tail\ *)  ;;
    wc\ *|sort\ *)    ;;
    grep\ *|sed\ *)   ;;
    xargs\ *)         ;;
    *) exit 0 ;;  # unknown → fall through to normal permission prompt
  esac
done

# Every subcommand matched an allowed prefix
cat <<'EOF'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow","permissionDecisionReason":"all subcommands individually allowed"}}
EOF
