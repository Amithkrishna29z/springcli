#!/usr/bin/env bash
# Builds a one-click macOS installer (springcli-<version>.pkg) that puts springcli on PATH.
#
# Pipeline:  Maven fat jar  ->  jpackage app-image (springcli.app + bundled JRE)  ->  pkgbuild .pkg
# A postinstall script symlinks the launcher into /usr/local/bin, so end users need neither Java
# nor manual PATH edits — double-click the .pkg and `springcli` works in any terminal.
#
# Prerequisites:
#   - JDK 21+ (jpackage on PATH)
#   - Maven
#   - macOS (pkgbuild is built in; no Xcode required)
#
# Usage:  ./scripts/package-mac.sh
# Output: dist/springcli.pkg

set -euo pipefail

VERSION="1.1.0"
IDENTIFIER="dev.springcli"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> Building fat jar..."
mvn -q clean package

OUT="dist"
STAGING="$OUT/input"
APPIMAGE="$OUT/app-image"
PKGROOT="$OUT/pkgroot"
rm -rf "$STAGING" "$APPIMAGE" "$PKGROOT"
mkdir -p "$STAGING"
cp target/springcli.jar "$STAGING/"

echo "==> Building self-contained app-image (springcli.app + runtime)..."
jpackage \
  --type app-image \
  --name springcli \
  --app-version "$VERSION" \
  --input "$STAGING" \
  --main-jar springcli.jar \
  --main-class cli.Main \
  --dest "$APPIMAGE"

# Lay out the payload exactly as it should appear on the target: /Applications/springcli.app
mkdir -p "$PKGROOT/Applications"
cp -R "$APPIMAGE/springcli.app" "$PKGROOT/Applications/"

# The postinstall script must be executable for pkgbuild to run it.
chmod +x scripts/mac/pkg-scripts/postinstall

# A stable, version-less filename keeps GitHub 'latest/download' links working across releases;
# the package's internal --version still drives in-place upgrade detection.
echo "==> Building installer package (.pkg) with PATH symlink..."
pkgbuild \
  --root "$PKGROOT" \
  --identifier "$IDENTIFIER" \
  --version "$VERSION" \
  --scripts scripts/mac/pkg-scripts \
  --install-location / \
  "$OUT/springcli.pkg"

echo "==> Done. Installer written to $OUT/springcli.pkg"
