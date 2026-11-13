# Build & run

Run these commands in the project folder, in PowerShell or Command Prompt.

## Requirements
- **JDK 21.** Check with `java -version`.
- **Maven:** not needed. The first `mvnw` run downloads it automatically.

## Commands

| Task | Command |
|---|---|
| Run the app | `.\mvnw javafx:run` |
| Compile only | `.\mvnw compile` |
| Run the tests | `.\mvnw test` |
| 5 GB benchmark (slow, needs ~6 GB free disk) | `.\mvnw test -Phuge` |
| Build the portable app | `.\mvnw -Ppackage package` |
| Build an MSI installer (needs [WiX Toolset 3](https://wixtoolset.org/)) | `.\mvnw -Ppackage package -Djpackage.type=msi "-Djpackage.extra=--win-menu --win-shortcut"` |
| Delete build output | `.\mvnw clean` |

## The portable app
`.\mvnw -Ppackage package` creates:

```
target\installer\KOMA\KOMA.exe
```

That folder includes its own Java runtime. Copy it anywhere and double-click `KOMA.exe`; Java doesn't need to be installed on that PC.

## Troubleshooting
- **`PKIX path building failed` while downloading:** an antivirus or proxy is inspecting HTTPS.
  `.mvn/jvm.config` already tells Maven to trust the Windows certificate store; make sure that file is present.
- **`java -version` doesn't show 21:** install JDK 21 and put it first on `PATH`, or set `JAVA_HOME` to it.
