package com.meditracker.app.ui;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/** Stores and applies the language selected by the user. English is the default. */
public final class LocaleManager {
    public static final String ENGLISH = "en";
    public static final String BANGLA = "bn";
    private static final String PREFS = "meditracker_language";
    private static final String KEY = "language";

    private LocaleManager() {}

    public static String language(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, ENGLISH);
    }

    public static boolean isBangla(Context context) { return BANGLA.equals(language(context)); }

    public static void setLanguage(Context context, String language) {
        String safe = BANGLA.equals(language) ? BANGLA : ENGLISH;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, safe).apply();
    }

    public static Context wrap(Context context) {
        Locale locale = new Locale(language(context));
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        return context.createConfigurationContext(configuration);
    }
}
