@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

:: Ensure Android SDK build-tools (zipalign & apksigner) and sign.bat are in PATH
set "PATH=%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0;F:\chen\intel;%PATH%"

echo === [1/4] Building Release APK (assembleRelease)... ===
call gradlew.bat assembleRelease
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Gradle build failed!
    exit /b %ERRORLEVEL%
)

echo === [2/4] Signing APK with: sign app-release-unsigned.apk arara.apk ===
if not exist "release" mkdir "release"
pushd "app\build\outputs\apk\release"
call sign "app-release-unsigned.apk" "arara.apk"
set "SIGN_ERR=%ERRORLEVEL%"
popd
if %SIGN_ERR% neq 0 (
    echo [ERROR] APK signing failed!
    exit /b %SIGN_ERR%
)
copy /y "app\build\outputs\apk\release\arara.apk" "arara.apk" >nul
copy /y "app\build\outputs\apk\release\arara.apk" "release\arara.apk" >nul

echo === [3/4] Committing signed APK to repository... ===
git add release/arara.apk
git commit -m "chore(release): update signed arara.apk"

echo === [4/4] Pushing to GitHub to trigger automatic Release publishing... ===
git -c http.proxy= -c https.proxy= push origin main
echo === Done! Signed APK will be published to https://github.com/TestersNightmare/arara/releases/latest/download/arara.apk ===
