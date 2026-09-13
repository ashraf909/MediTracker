package com.meditracker.app.ui;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class AlarmAudioManager implements TextToSpeech.OnInitListener {
    private final Context context;
    private MediaPlayer player;
    private TextToSpeech speech;
    private String pendingSpeech;

    public AlarmAudioManager(Context context) { this.context = context.getApplicationContext(); speech = new TextToSpeech(context, this); }
    public void startAlarm() {
        stopAlarm();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            player.setLooping(true); player.prepare(); player.start();
        } catch (Exception ignored) {}
    }
    public void speak(String value) {
        pendingSpeech = value;
        if (speech != null) speech.speak(value, TextToSpeech.QUEUE_FLUSH, null, "meditracker-reminder");
    }
    public void stopAlarm() { if (player != null) { try { player.stop(); } catch (Exception ignored) {} player.release(); player = null; } }
    public void close() { stopAlarm(); if (speech != null) { speech.stop(); speech.shutdown(); speech = null; } }
    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && speech != null) {
            speech.setLanguage(LocaleManager.isBangla(context) ? new Locale("bn", "BD") : Locale.US);
            speech.setSpeechRate(LocaleManager.isBangla(context) ? 0.88f : 0.95f);
            if (pendingSpeech != null) speak(pendingSpeech);
        }
    }
}
