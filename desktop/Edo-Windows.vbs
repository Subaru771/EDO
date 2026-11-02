' Launch with javaw (no command window); JDK/JRE 21+ must be installed.
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
base = fso.GetParentFolderName(WScript.ScriptFullName)
shell.Run "javaw -jar """ & base & "\Edo-Desktop.jar"""", 1, False
