# clipboard-agent

Fix of race condition on Windows in AWT.

# Building

- Execute `gradlew build`
- Locate the file `build/libs/clipboard-agent.jar`

- if jdk compil's problem use this dockerfile

# For NetBeans:

- Navigate to your NetBeans install directory
- Enter `etc` folder.
- Open `netbeans.conf`
- Add `-J-javaagent:whereis/clipboard-agent.jar` to `netbeans_default_options`.
- To test that the agent works, it is recommended that you run NetBeans from the terminal the first time.

Normal log during agent initialization:

```
Patched sun.awt.datatransfer.SunClipboard
Patched sun.awt.windows.WClipboard
Applied the clipboard patch
```
