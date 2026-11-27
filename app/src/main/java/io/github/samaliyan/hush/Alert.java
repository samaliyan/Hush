package io.github.samaliyan.hush;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * "Sound only for real messages": the messaging app's notifications are set to silent by the user, and
 * Hush plays the phone's notification sound for messages it keeps. Follows the phone's sound mode:
 * silent → nothing, vibrate → vibration, normal → the sound, Do Not Disturb → nothing.
 */
final class Alert {
    private Alert() {
    }

    static void play(Context c) throws InterruptedException {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null && nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL) return;
        AudioManager am = c.getSystemService(AudioManager.class);
        int mode = am != null ? am.getRingerMode() : AudioManager.RINGER_MODE_NORMAL;
        if (mode == AudioManager.RINGER_MODE_SILENT) return;
        if (mode == AudioManager.RINGER_MODE_VIBRATE) {
            vibrate(c);
            return;
        }
        Ringtone r = RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        if (r == null) return;
        r.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        r.play();
        // keep the process alive while it plays (the caller is a short background task)
        for (int i = 0; i < 40 && r.isPlaying(); i++) Thread.sleep(100);
    }

    @SuppressWarnings("deprecation")
    private static void vibrate(Context c) {
        try {
            Vibrator v = c.getSystemService(Vibrator.class);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 180, 120, 180}, -1));
        } catch (Exception ignored) {
            // no vibrator access
        }
    }
}
