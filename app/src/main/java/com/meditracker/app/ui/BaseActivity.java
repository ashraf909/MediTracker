package com.meditracker.app.ui;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;

/** Ensures every activity uses the same saved app language. */
public abstract class BaseActivity extends AppCompatActivity {
    @Override protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.wrap(newBase));
    }

    protected void changeLanguage(String language) {
        if (language.equals(LocaleManager.language(this))) return;
        LocaleManager.setLanguage(this, language);
        recreate();
    }
}
