#!/usr/bin/env bash
# Builds a native Linux installer for springcli using the JDK's jpackage tool.
# The package bundles a trimmed Java runtime, so end users do NOT need Java installed.
#
# Prerequisites:
#   - JDK 21+ (jpackage on PATH)
#   - Maven
#   - dpkg (for .deb, default) or rpmbuild (for .rpm)
#
# PATH: jpackage's deb/rpm install to /opt/springcli and create a /usr/bin/springcli symlink by
# default, so `springcli` is on PATH right after a double-click install — no extra work needed.
# (Verify after install with: dpkg -L springcli | grep /usr/bin)
#
# Usage:  ./scripts/package-linux.sh [deb|rpm]     (default: deb)
# Output: dist/springcli_<version>_amd64.deb  (or the .rpm equivalent)

set -euo pipefail

VERSION="1.0.0"
TYPE="${1:-deb}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> Building fat jar..."
mvn -q clean package

# Stage only the runnable jar so jpackage doesn't bundle the extra shade artifacts.
OUT="dist"
STAGING="$OUT/input"
rm -rf "$STAGING"
mkdir -p "$STAGING"
cp target/springcli.jar "$STAGING/"

echo "==> Creating Linux installer (.$TYPE)..."
jpackage \
  --type "$TYPE" \
  --name springcli \
  --app-version "$VERSION" \
  --input "$STAGING" \
  --main-jar springcli.jar \
  --main-class cli.Main \
  --linux-shortcut \
  --dest "$OUT"

echo "==> Done. Package written to $OUT/"
