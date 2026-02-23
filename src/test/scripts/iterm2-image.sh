#!/bin/bash
if [ $# -eq 0 ]; then
  echo "Usage: divider file"
  exit 1
fi
printf '\033]1337;File=inline=1;width=50%%;height=10;preserveAspectRatio=0'
printf ":"
base64 -w 0 < "$1"
printf '\a\n'
