; Inno Setup script for a one-click springcli installer that adds the tool to the system PATH.
;
; It packages the jpackage *app-image* (a self-contained folder: springcli.exe + bundled Java
; runtime) built by package-windows.ps1, so end users need neither Java nor any manual PATH edits.
;
; Why Inno Setup instead of jpackage's own .msi: jpackage cannot add the install dir to PATH
; (its overrides.wxi only overrides WiX variables, not add an <Environment> component), so a
; PATH-enabled .msi would require a full, version-fragile main.wxs override. Inno Setup's PATH
; handling is a well-established, reliable pattern.
;
; Build with: ISCC.exe scripts\windows\springcli.iss   (see package-windows.ps1)

#define AppName "springcli"
#define AppVersion "1.5.0"
#define AppExe "springcli.exe"

[Setup]
; A fixed AppId means re-running a newer installer UPGRADES the existing install in place
; (same directory, files replaced) instead of creating a duplicate. Never change this GUID.
AppId={{8F2A5C41-3B6D-4E9A-9C1F-2A7B6E5D4C3B}
AppName={#AppName}
AppVersion={#AppVersion}
VersionInfoVersion={#AppVersion}
AppPublisher=springcli
DefaultDirName={autopf}\{#AppName}
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\{#AppExe}
OutputDir=..\..\dist
; Stable, version-less filename so GitHub 'latest/download' links never need updating.
OutputBaseFilename=springcli-setup
Compression=lzma2
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64compatible
; Admin lets us write to a system-wide PATH; the installer is still a normal double-click .exe.
PrivilegesRequired=admin
ChangesEnvironment=yes

[Files]
; Copy the entire jpackage app-image (springcli.exe sits at its root next to the runtime).
; ignoreversion: jpackage stamps the same file versions across builds, so a version compare would
; skip replacing them and a reinstall would leave the old payload in place.
Source: "..\..\dist\app-image\springcli\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Tasks]
Name: addtopath; Description: "Add springcli to the system PATH (recommended)"

[Registry]
; Append the install dir to the machine PATH only if it isn't already present.
Root: HKLM; Subkey: "SYSTEM\CurrentControlSet\Control\Session Manager\Environment"; \
  ValueType: expandsz; ValueName: "Path"; ValueData: "{olddata};{app}"; \
  Check: NeedsAddPath(ExpandConstant('{app}')); Tasks: addtopath

[Code]
const
  EnvKey = 'SYSTEM\CurrentControlSet\Control\Session Manager\Environment';
  LegacyKey = 'SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\springcli_is1';

{ True when the given directory is not already on the machine PATH. }
function NeedsAddPath(Dir: string): Boolean;
var
  OrigPath: string;
begin
  if not RegQueryStringValue(HKLM, EnvKey, 'Path', OrigPath) then
  begin
    Result := True;
    exit;
  end;
  Result := Pos(';' + Uppercase(Dir) + ';', ';' + Uppercase(OrigPath) + ';') = 0;
end;

{ Remove our entry from PATH on uninstall so we don't leave dangling segments behind. }
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  OrigPath: string;
  AppDir: string;
  P: Integer;
begin
  if CurUninstallStep <> usUninstall then
    exit;
  if not RegQueryStringValue(HKLM, EnvKey, 'Path', OrigPath) then
    exit;
  AppDir := ExpandConstant('{app}');
  P := Pos(';' + Uppercase(AppDir), Uppercase(OrigPath));
  if P > 0 then
  begin
    Delete(OrigPath, P, Length(';' + AppDir));
    RegWriteExpandStringValue(HKLM, EnvKey, 'Path', OrigPath);
  end;
end;

{ Pre-1.1.0 installers shipped without an AppId, so Windows filed that install under the key
  'springcli_is1' rather than today's GUID. Installing over one of those looks like a brand-new
  app: a second entry in Apps & Features and a second uninstaller next to the first one. Remove
  the old install first so an upgrade always leaves exactly one springcli behind. }
function LegacyUninstallString(var Cmd: string): Boolean;
begin
  Result := RegQueryStringValue(HKLM, LegacyKey, 'UninstallString', Cmd);
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Cmd: string;
  ResultCode, Waited: Integer;
begin
  Result := '';
  if not LegacyUninstallString(Cmd) then
    exit;
  if not Exec(RemoveQuotes(Cmd), '/VERYSILENT /SUPPRESSMSGBOXES /NORESTART', '',
              SW_HIDE, ewWaitUntilTerminated, ResultCode) then
    exit;
  { An Inno uninstaller relaunches itself from %TEMP% and the process we started returns straight
    away, so wait for its registry entry to go before we overwrite the files it is still deleting. }
  Waited := 0;
  while LegacyUninstallString(Cmd) and (Waited < 30000) do
  begin
    Sleep(500);
    Waited := Waited + 500;
  end;
end;
