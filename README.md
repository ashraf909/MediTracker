# MediTracker Native Android (Java + XML)

MediTracker is a native Android medicine-reminder application built with Java and XML. It does not use React, React Native, Kotlin, Compose, Capacitor, or a WebView.

## Implemented

- Java Activities and XML layouts
- patient/caregiver role selection
- new and returning patient flows
- exact family-key caregiver pairing
- Firebase Anonymous Authentication
- real-time Cloud Firestore listener
- empty new-patient schedule
- multiple daily times per medicine
- add, edit, delete, Taken, and reset actions
- fixed pill template
- Bangla patient/alarm text and speech
- native exact alarms with sound and vibration
- alarm restoration after reboot/app update
- ten-minute Snooze
- full-screen native `AlarmActivity`
- Firebase token registration
- caregiver reminder API and closed-app FCM service

## Build

Clone the repository, then add your own Firebase Android configuration at `app/google-services.json`. Android Studio creates `local.properties` for the local Android SDK. Both files are intentionally ignored by Git.

```powershell
.\gradlew.bat assembleDebug
```

APK:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Android lint:

```powershell
.\gradlew.bat lintDebug
```

## Important source locations

- `app/src/main/java/com/meditracker/app/data/FirebaseRepository.java`: Auth, Firestore, Taken transaction, push request
- `app/src/main/java/com/meditracker/app/data/SessionManager.java`: saved device role and family code
- `app/src/main/java/com/meditracker/app/ui/`: all native screens and RecyclerView adapter
- `app/src/main/java/com/meditracker/app/alarms/`: exact scheduling, notification receiver, reboot restore
- `app/src/main/java/com/meditracker/app/notifications/`: Firebase messaging service
- `app/src/main/res/layout/`: XML screens
- `app/src/main/res/values-bn/`: Bangla translations
- `app/src/main/AndroidManifest.xml`: Android components and permissions
- `backend/server/index.js`: caregiver reminder and schedule-refresh notification API
- `firebase/firestore.rules`: Firestore access rules

Additional guides:

- `docs/FIREBASE_SETUP_FOR_TEAM.md`: Firebase configuration
- `docs/BUILD_TEST_RELEASE_CHECKLIST.md`: build and release checks
- `docs/MANUAL_PHONE_TESTS.md`: real-device test checklist

## Required real-device checks

After building the configured project, verify Android alarm behavior on physical phones:

1. Install the newest APK and open it once.
2. Allow notifications, exact alarms, and full-screen alarm access.
3. Connect one installation as patient and another client as caregiver.
4. Add a dose a few minutes ahead.
5. Test open, backgrounded, and closed patient app.
6. Test Snooze and Taken.
7. Restart the phone and check future alarm restoration.
8. Send a caregiver reminder while the patient app is closed.

Do not claim those device checks passed until they have been performed on a real phone.
