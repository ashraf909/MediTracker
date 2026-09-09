# Firebase Setup for Team Members

1. Open the MediTracker Firebase project.
2. Enable **Authentication → Anonymous**.
3. Create the **Cloud Firestore** database.
4. Register an Android application with package name `com.meditracker.app`.
5. Download `google-services.json`.
6. Put it privately at `app/google-services.json`.
7. Never commit that file or the Firebase Admin service-account JSON.

To publish the included Firestore rules:

```powershell
firebase login
firebase use YOUR_FIREBASE_PROJECT_ID
firebase deploy --only firestore:rules --config firebase/firebase.json
```

The models describe data. `FirebaseRepository.java` reads and writes it. `SessionManager.java` remembers whether this installation is a patient or caregiver and stores its family key locally.
