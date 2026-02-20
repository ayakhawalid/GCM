@echo off
REM Run GCM client with JavaFX software rendering (avoids GPU/WebView tile glitches).
REM Uses Maven; pom.xml already has -Dprism.order=sw for javafx:run.
mvn javafx:run
