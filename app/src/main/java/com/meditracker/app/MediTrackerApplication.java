package com.meditracker.app;

import android.app.Application;
import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.meditracker.app.alarms.MedicineAlarmScheduler;
import com.meditracker.app.ui.LocaleManager;

public class MediTrackerApplication extends Application {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.wrap(base));
    }
    @Override public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        MedicineAlarmScheduler.ensureChannel(this);
        MedicineAlarmScheduler.restore(this);
    }
}
