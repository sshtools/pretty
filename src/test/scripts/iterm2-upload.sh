#!/bin/bash
if [ $# -eq 0 ]; then
  echo "Usage: upload file"
  exit 1
fi
fn=$(basename "${1}")
enc=$(echo -n "${fn}"|base64 -w 0)
echo -en "\033]1337;File=inline=0;size="$(cat "${1}"|wc -c)";name=${enc}:"
base64 -w 0 < "$1"
echo -en "\a\n"
