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
#define AppVersion "1.0.0"
#define AppExe "springcli.exe"

[Setup]
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=springcli
DefaultDirName={autopf}\{#AppName}
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\{#AppExe}
OutputDir=..\..\dist
OutputBaseFilename=springcli-{#AppVersion}-setup
Compression=lzma2
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64compatible
; Admin lets us write to a system-wide PATH; the installer is still a normal double-click .exe.
PrivilegesRequired=admin
ChangesEnvironment=yes

[Files]
; Copy the entire jpackage app-image (springcli.exe sits at its root next to the runtime).
Source: "..\..\dist\app-image\springcli\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs

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
