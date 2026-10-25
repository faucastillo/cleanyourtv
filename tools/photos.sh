#!/usr/bin/env bash
# Send my-photos/ to the glass Home's photo card (this fork's glass_home.photo_card). Each picture is turned upright
# from its EXIF orientation, scaled to at most 1600 px and saved as JPEG, then the TV's photos/ folder is replaced
# with them and Home restarts to show one.
#
# Usage: TV_HOST=192.168.1.50 tools/photos.sh [folder]     # default folder: my-photos/
#        TV_HOST=192.168.1.50 tools/photos.sh --clear
set -euo pipefail
ARG="${1:-}"
case "$ARG" in
  --clear) ;;
  "") SRC="$(cd "$(dirname "$0")/.." && pwd)/my-photos" ;;
  /*) SRC="$ARG" ;;
  *) SRC="$(pwd)/$ARG" ;;
esac
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY=venv/bin/python
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt pillow" >&2; exit 1; }
"$PY" -I -c 'import PIL' 2>/dev/null || { echo "run: tools/venv/bin/pip install pillow" >&2; exit 1; }
PKG=com.gathin.tvhome
DIR=/sdcard/Android/data/$PKG/files
URI=content://$PKG.theme
TMP=/data/local/tmp/cyt-photo
tv() { "$PY" -I tv.py "$@"; }
# As tools/theme.sh: write through the launcher's ThemeProvider so the file belongs to the launcher, cp as fallback.
put() { echo "rm -f $DIR/photos/$1 2>/dev/null; { o=\$(content write --uri $URI/photos/$1 < $TMP-$1 2>&1) && case \"\$o\" in *'Unsupported operation'*) false ;; esac; } || cp $TMP-$1 $DIR/photos/$1 2>/dev/null; rm -f $TMP-$1"; }
CLEAR="mkdir -p $DIR/photos 2>/dev/null; rm -f $DIR/photos/* 2>/dev/null; content delete --uri $URI/photos 2>/dev/null"
RESTART="am force-stop $PKG; am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null"

if [ "$ARG" = --clear ]; then
  tv "$CLEAR; $RESTART; echo cleared" | tail -n 1
  exit 0
fi
[ -d "$SRC" ] || { echo "no folder: $SRC" >&2; exit 1; }
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
"$PY" -I - "$SRC" "$OUT" <<'EOF'
import os, sys
from PIL import Image, ImageOps
src, out = sys.argv[1], sys.argv[2]
n = 0
for name in sorted(os.listdir(src)):
    if not name.lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
        continue
    try:
        im = ImageOps.exif_transpose(Image.open(os.path.join(src, name))).convert("RGB")
    except Exception as e:
        print(f"skipped {name}: {e}", file=sys.stderr)
        continue
    im.thumbnail((1600, 1600), Image.LANCZOS)
    n += 1
    im.save(os.path.join(out, f"photo-{n:03d}.jpg"), quality=86, optimize=True)
print(f"{n} photos ready")
EOF
COUNT=$(ls "$OUT" | wc -l)
[ "$COUNT" -gt 0 ] || { echo "no photos in $SRC" >&2; exit 1; }
tv "$CLEAR" >/dev/null
for F in "$OUT"/*.jpg; do
  NAME=$(basename "$F")
  "$PY" -I b64push.py "$F" "$TMP-$NAME" >/dev/null
  tv "$(put "$NAME")" >/dev/null
  echo "sent $NAME"
done
tv "$RESTART; echo done" | tail -n 1
echo "$COUNT photos on the TV. Remove them with: tools/photos.sh --clear"
