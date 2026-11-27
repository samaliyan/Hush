package io.github.samaliyan.hush;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Telephony;

/**
 * Runs a cleaning pass whenever the message store changes (Android starts it; nothing keeps running in
 * the background). A content-change job runs once, so it schedules itself again after each run. It cannot
 * survive a restart by itself; {@link BootReceiver} and the app schedule it again.
 */
public final class SweepJob extends JobService {
    private static final int ID = 7001;

    static void schedule(Context c) {
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js == null) return;
            JobInfo job = new JobInfo.Builder(ID, new ComponentName(c, SweepJob.class))
                    .addTriggerContentUri(new JobInfo.TriggerContentUri(Telephony.Sms.CONTENT_URI,
                            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                    .addTriggerContentUri(new JobInfo.TriggerContentUri(Telephony.MmsSms.CONTENT_URI,
                            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                    .setTriggerContentUpdateDelay(300)
                    .setTriggerContentMaxDelay(2000)
                    .build();
            js.schedule(job);
        } catch (Exception ignored) {
            // the next incoming message schedules it again
        }
    }

    @Override public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            try {
                Cleaner.autoPass(this);
            } catch (Exception ignored) {
                // permission taken away meanwhile
            }
            jobFinished(params, false);
            schedule(getApplicationContext());
        }, "hush-sweep").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        schedule(getApplicationContext());
        return false;
    }
}
