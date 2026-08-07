# springcli

[![CI](https://github.com/Amithkrishna29z/springcli/actions/workflows/ci.yml/badge.svg)](https://github.com/Amithkrishna29z/springcli/actions/workflows/ci.yml)

A cross-platform command-line tool that scaffolds **Spring Boot** projects by consuming the
official [Spring Initializr API](https://start.spring.io) — instead of hand-rolling templates. It
fetches live metadata (Boot versions, Java versions, dependencies, …), lets you pick options through
an interactive wizard or flags, downloads the generated `starter.zip`, and extracts it into a new
project directory.

Works on **Windows, macOS, and Linux** with a single Java 21 fat-jar.

---

## Install

Download the installer for your OS from the
**[latest release](https://github.com/Amithkrishna29z/springcli/releases/latest)** and double-click
it. Each installer bundles a Java runtime and adds `springcli` to your `PATH`, so **no Java or manual
setup is required** — after installing, open a terminal and run `springcli`.

The links below always point at the **newest** release, and **re-running a newer installer upgrades
your existing install in place** (no duplicates):

| OS | Installer (always latest) | How to install |
|----|---------------------------|----------------|
| **Windows** | [`springcli-setup.exe`](https://github.com/Amithkrishna29z/springcli/releases/latest/download/springcli-setup.exe) | Double-click and follow the wizard |
| **macOS** (Apple Silicon) | [`springcli.pkg`](https://github.com/Amithkrishna29z/springcli/releases/latest/download/springcli.pkg) | Double-click and follow the installer |
| **Linux** (Debian/Ubuntu) | [`springcli-amd64.deb`](https://github.com/Amithkrishna29z/springcli/releases/latest/download/springcli-amd64.deb) | `sudo apt install ./springcli-amd64.deb` |

Verify it works:

```bash
springcli version
springcli new my-app
```

New projects include a sensible default dependency set — **Spring Web, Spring Data JPA, Spring Boot
DevTools, Validation, and Lombok** — which you can change in the wizard (toggle any on/off) or
override with `--deps` in non-interactive mode.

> Prefer to build from source, or need a different package format? See
> [Build](#build) and [One-click installers](#one-click-installers-bundled-runtime--on-path-no-java-required).

---

## Features

- 🧙 **Interactive wizard** (`springcli new`) — guided prompts with sensible defaults.
- ⚡ **Non-interactive mode** (`springcli new my-app --yes ...`) — scriptable, CI-friendly.
- 🔎 **Live metadata** fetched from `start.spring.io/metadata/client` (cached per run).
- 🔍 **Dependency search & listing** (`search`, `list`).
- 📦 Downloads and extracts `starter.zip`, then deletes the archive.
- 🩺 **Environment doctor** (`doctor`) — checks Java / Maven / Git.
- 💾 **Saved defaults** (`config`) — persist your group id, Java version, dependencies, etc.
- 🧭 **New-user guide** (`guide`) and **shell completion** (`completion`) for bash/zsh.
- ⬆️ **Self-update** (`update` / `modify`) — startup notice, and update-to-latest from the terminal.
- 🎨 Coloured, user-friendly progress output (auto-disabled when piped or `NO_COLOR` is set).
- 🧩 Optional post-generation actions: `--git`, `--open` (VS Code), `--build`.
- 🛡️ Safe ZIP extraction (path-traversal / "zip-slip" protection).

---

## Requirements

- **Java 21+** (to run the jar and for generated projects that target Java 21)
- **Maven 3.9+** (only to build springcli itself)
- Internet access to reach `start.spring.io`

---

## Build

```bash
mvn clean package
```

This produces a runnable fat-jar at `target/springcli.jar` and runs the unit tests.

Run it:

```bash
java -jar target/springcli.jar --help
```

## One-click installers (bundled runtime + on PATH, no Java required)

The [`scripts/`](scripts) folder builds a **double-click installer per OS**. Each bundles a trimmed
Java runtime *and* adds `springcli` to the `PATH`, so after installing, end users just open a
terminal and run `springcli` — no Java, no manual PATH edits.

> **Installers cannot be cross-compiled** — build each one on its own OS (locally or via a CI
> matrix). Every script produces a self-contained jpackage *app-image* first, then wraps it in the
> platform's native installer.

| OS | Command | Output | Extra prerequisite | PATH mechanism |
|----|---------|--------|--------------------|----------------|
| Windows | `powershell -ExecutionPolicy Bypass -File scripts\package-windows.ps1` | `dist\springcli-1.0.0-setup.exe` | [Inno Setup 6](https://jrsoftware.org/isdl.php) (`ISCC.exe`) | Installer appends install dir to system `PATH` |
| macOS | `./scripts/package-mac.sh` | `dist/springcli-1.0.0.pkg` | none (built-in `pkgbuild`) | `postinstall` symlinks into `/usr/local/bin` |
| Linux | `./scripts/package-linux.sh [deb\|rpm]` | `dist/springcli_1.0.0_amd64.deb` | `dpkg` or `rpmbuild` | jpackage auto-creates `/usr/bin/springcli` symlink |

**Why not jpackage's own `.msi`?** jpackage can't add the install dir to `PATH` (its `overrides.wxi`
only overrides WiX *variables*, not add an `<Environment>` component), so a PATH-enabled `.msi` would
need a full, version-fragile `main.wxs` override. Inno Setup's PATH handling is a battle-tested,
reliable pattern, so the Windows installer uses it to wrap the jpackage app-image.

> **Test status:** the jpackage app-image step is verified on Windows (produces a standalone,
> runnable `springcli.exe`). The final installer wrappers (Inno Setup on Windows, `pkgbuild` on
> macOS) were authored but not compiled here, since `ISCC.exe`/macOS were unavailable on the build
> machine — run each script on the target OS to produce the installer.

To get a **portable** build instead of an installer, produce a jpackage *app-image* and zip it:

```bash
jpackage --type app-image --name springcli --input <dir-with-jar> \
  --main-jar springcli.jar --main-class cli.Main --win-console --dest dist
# → dist/springcli/  → run dist/springcli/springcli.exe (or bin/springcli); add its folder to PATH
```

### Optional: a `springcli` shortcut

**Linux / macOS** — create a wrapper on your `PATH`:

```bash
printf '#!/usr/bin/env sh\nexec java -jar /full/path/to/springcli.jar "$@"\n' | sudo tee /usr/local/bin/springcli
sudo chmod +x /usr/local/bin/springcli
```

**Windows (PowerShell profile)**:

```powershell
function springcli { java -jar C:\path\to\springcli.jar @args }
```

---

## Usage

### Create a project (interactive)

```bash
springcli new
# or pre-fill the name:
springcli new my-app
```

The wizard asks for project name, group/artifact/package, build tool, language, Spring Boot
version, Java version, packaging, and lets you **search and multi-select dependencies** before
showing a summary and generating the project into `./<artifactId>`.

### Create a project (non-interactive)

```bash
springcli new my-app --yes \
  --group com.acme \
  --artifact my-app \
  --package com.acme.myapp \
  --type maven-project \
  --language java \
  --packaging jar \
  --boot-version 3.3.2 \
  --java-version 21 \
  --deps web,data-jpa,postgresql \
  --git --open --build
```

| Flag | Description |
|------|-------------|
| `-y, --yes` | Skip the wizard; resolve everything from flags + defaults. |
| `--group` | Group ID (default `com.example`). |
| `--artifact` | Artifact ID (defaults to the project name). |
| `--package` | Base package (defaults to `group.artifact`). |
| `--description` | Project description. |
| `--type` | `maven-project` or `gradle-project`. |
| `--language` | `java`, `kotlin`, or `groovy`. |
| `--packaging` | `jar` or `war`. |
| `--boot-version` | Spring Boot version (validated against metadata). |
| `--java-version` | Java version (validated against metadata). |
| `--deps` | Comma-separated dependency ids (e.g. `web,data-jpa`). |
| `--force` | Overwrite a non-empty destination directory. |
| `--git` | Run `git init` in the new project. |
| `--open` | Open the project in VS Code if `code` is on the PATH. |
| `--build` | Run `mvnw clean install` (or `gradlew build`) afterwards. |

### Search dependencies

```bash
springcli search web
```

### List all dependencies by category

```bash
springcli list
```

### Saved defaults

Store your preferred defaults once and every `springcli new` will use them (so you stop retyping
your group id, Java version, etc.). Saved to `~/.springcli/config.json`.

```bash
springcli config set groupId com.acme
springcli config set javaVersion 21
springcli config set dependencies web,data-jpa,lombok
springcli config list            # show all saved defaults
springcli config get groupId
springcli config unset dependencies
springcli config path            # print the config file location
```

Keys: `groupId`, `javaVersion`, `language`, `packaging`, `type`, `dependencies`
(`dependencies` is a comma-separated list).

**Precedence** when creating a project: explicit flag / wizard input **&gt;** saved config **&gt;**
built-in default. In the wizard your saved values appear as the pre-filled defaults.

### Check your environment

```bash
springcli doctor
```

### Update

Check whether a newer release is available, and optionally download the installer for your OS:

```bash
springcli update              # report current vs latest; offers to install if newer
springcli update --download   # download the installer for your OS and open it
```

When a newer version exists, `springcli update` prompts to download and install it right there in the
terminal. springcli also shows a small **"update available" notice at startup** — a throttled,
best-effort check (at most once/day, cached, never blocking) that you can turn off with
`SPRINGCLI_NO_UPDATE_CHECK=1`.

Because a running executable can't reliably overwrite itself, the installer hands off to the native
package (which upgrades your existing install in place). Requires an internet connection.

To manage an existing install from the terminal, use `modify` — it shows your version and install
location, then runs the update-to-latest flow:

```bash
springcli modify
```

### Getting-started guide

New to the tool? Print a friendly walkthrough of the common commands:

```bash
springcli guide
```

### Shell completion

Generate a bash/zsh tab-completion script (completes subcommands and options):

```bash
# Enable for the current shell:
source <(springcli completion)          # bash

# zsh:
autoload -U +X compinit && compinit
autoload -U +X bashcompinit && bashcompinit
source <(springcli completion)

# Install permanently (bash), e.g.:
springcli completion > ~/.local/share/bash-completion/completions/springcli
```

> Generated by picocli, so completion always matches the current commands and flags.
> (bash/zsh; PowerShell/fish are not produced.)

### Version

```bash
springcli version
```

---

## Configuration

| Environment variable | Effect |
|----------------------|--------|
| `SPRINGCLI_BASE_URL` | Override the Initializr base URL (e.g. a self-hosted instance). |
| `SPRINGCLI_DEBUG` | Print full stack traces on unexpected errors. |
| `SPRINGCLI_NO_UPDATE_CHECK` | Disable the startup "update available" notice. |
| `NO_COLOR` | Disable coloured output. |

---

## Architecture

The code follows a clean, layered design with clear single responsibilities, making the services
independently unit-testable (HTTP is mocked in tests).

```
cli/         Main (entry point + banner + global error handling), ServiceFactory (composition root)
commands/    NewCommand, SearchCommand, ListCommand, VersionCommand, DoctorCommand  (Picocli)
service/     InitializrClient (HTTP), MetadataService (parse/cache/search/validate),
             ProjectGenerator (download→extract→cleanup), ZipExtractor (safe unzip)
prompts/     InteractiveWizard (guided prompts, dependency search & multi-select)
model/       Metadata (Initializr client metadata), ProjectRequest (immutable, builder)
util/        Ansi (colour output), FileUtils
exception/   SpringCliException hierarchy (Network/Validation/Extraction)
```

Key design decisions:

- **`InitializrClient` takes an injected `HttpClient` and base URL** so it can be tested with a mock
  client and pointed at a stub server — no real network in tests.
- **`MetadataService` caches** the parsed metadata for the lifetime of one execution, so a single run
  never hits the network twice.
- **`ProjectGenerator`** writes the download to a temp ZIP, extracts it, and always deletes the ZIP
  (in a `finally` block), rolling back a partially-written target on failure.
- **`ZipExtractor`** normalises and validates every entry path against the destination to prevent
  zip-slip path traversal.
- A **global `IExecutionExceptionHandler`** renders `SpringCliException`s as clean, coloured messages
  and reserves stack traces for genuinely unexpected failures (`SPRINGCLI_DEBUG=1`).

---

## Error handling

The tool produces clear messages for: network failures, invalid Spring Boot / Java versions, unknown
dependencies, an existing non-empty destination folder, malformed metadata, and ZIP extraction
failures.

---

## Testing

```bash
mvn test
```

Unit tests cover `MetadataService` (parse/cache/search/validation), `InitializrClient` (query
building + HTTP behaviour with a mocked `HttpClient`), `ZipExtractor` (extraction, directory
creation, zip-slip rejection, corrupt archives), and command parsing (Picocli wiring).

See [`docs/EXAMPLES.md`](docs/EXAMPLES.md) for sample terminal output.

---

## License

Provided as an open-source developer tool. Add your preferred license here.
