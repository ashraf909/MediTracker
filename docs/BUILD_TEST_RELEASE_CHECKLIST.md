# Build, Test and Release Checklist

- Confirm `app/google-services.json` exists locally and is ignored by Git.
- Confirm no Admin JSON, family key, patient record, token or keystore is staged.
- Run `git status` and inspect every file.
- Run `./gradlew.bat clean lintDebug testDebugUnitTest assembleDebug`.
- Confirm the build reports `BUILD SUCCESSFUL`.
- Install `app/build/outputs/apk/debug/app-debug.apk` on an Android phone.
- Complete the manual tests in the Member 2 guide.
- Create a GitHub Release and attach the APK there.
- Record the tested app version and date in the release description.
