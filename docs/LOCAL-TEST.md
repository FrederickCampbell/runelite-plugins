# Local test — v1.13.1

Use a successful build on the development branch only. Download and extract the `farming-helper-local-test` artifact, preserving its folder structure. Close RuneLite, then from the extracted directory use PowerShell:

```powershell
.\tools\Install-FarmingHelper.ps1
```

Alternatively specify the standard plugin JAR with `-JarPath`. The installer validates the candidate version, copies only the plugin JAR into your local RuneLite `sideloaded-plugins` folder, and backs up its existing named copy. It does not start RuneLite, alter account credentials, install a launcher, modify plugin settings, or automate game interactions.

A compatible RuneLite developer-mode client is required for sideloading. See [official developer build instructions](https://github.com/runelite/runelite/wiki/Building-with-IntelliJ-IDEA) and [Jagex account instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts). Alternatively, from a checkout with Java 21 and Gradle dependencies available, run `gradlew.bat run` on Windows or `./gradlew run` on other platforms to start the source development launcher. Do not load a second copy of the same plugin. To undo the sideload, close the client and remove `FarmingHelper.jar` or restore its `.bak` copy.

The installer has an isolated-directory CI smoke test. Live login, actual sideload compatibility, and gameplay remain player-side acceptance checks. No Plugin Hub publication or master merge is authorized by this candidate.
