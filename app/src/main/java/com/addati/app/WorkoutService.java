package com.addati.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.*;
import java.util.Locale;

public class WorkoutService extends Service {
    public static final String ACTION_START = "com.addati.START_WORKOUT";
    public static final String ACTION_UPDATE = "com.addati.UPDATE_WORKOUT";
    public static final String ACTION_REST = "com.addati.START_REST";
    public static final String ACTION_ADD15 = "com.addati.ADD15";
    public static final String ACTION_SKIP_REST = "com.addati.SKIP_REST";
    public static final String ACTION_COMPLETE_SET = "com.addati.COMPLETE_SET";
    public static final String ACTION_STOP = "com.addati.STOP_WORKOUT";

    private static final int ID = 7001;
    private static final String CHANNEL = "active_workout";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long restEnd = 0L;
    private String workoutName = "عدّاتي";
    private String exercise = "جلسة تمرين نشطة";
    private String setLabel = "";
    private double weight = 0;
    private int reps = 0;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateNotification();
            if (restEnd > System.currentTimeMillis()) handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "التمرين النشط", NotificationManager.IMPORTANCE_DEFAULT);
            c.setDescription("متابعة الجلسة ومؤقت الراحة");
            c.setSound(null, null);
            c.enableVibration(false);
            nm.createNotificationChannel(c);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            handler.removeCallbacks(ticker);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_ADD15.equals(action)) {
            if (restEnd > 0) restEnd += 15000;
            updateNotification();
            return START_STICKY;
        }
        if (ACTION_SKIP_REST.equals(action)) {
            restEnd = 0;
            handler.removeCallbacks(ticker);
            updateNotification();
            sendCommandToWeb("skip_rest");
            return START_STICKY;
        }
        if (ACTION_COMPLETE_SET.equals(action)) {
            sendCommandToWeb("complete_set");
            updateNotification();
            return START_STICKY;
        }

        String n = intent.getStringExtra("name"); if (n != null && !n.isEmpty()) workoutName = n;
        String e = intent.getStringExtra("exercise"); if (e != null && !e.isEmpty()) exercise = e;
        String s = intent.getStringExtra("set"); if (s != null) setLabel = s;
        weight = intent.getDoubleExtra("weight", weight);
        reps = intent.getIntExtra("reps", reps);

        if (ACTION_REST.equals(action)) {
            int sec = Math.max(0, intent.getIntExtra("rest", 0));
            restEnd = System.currentTimeMillis() + sec * 1000L;
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }

        Notification nfy = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, nfy, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
        } else {
            startForeground(ID, nfy);
        }
        return START_STICKY;
    }

    private PendingIntent serviceAction(String action, int req) {
        Intent i = new Intent(this, WorkoutService.class).setAction(action);
        return PendingIntent.getService(this, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long left = Math.max(0, restEnd - System.currentTimeMillis());
        int secs = (int)(left / 1000L);
        String time = String.format(Locale.US, "%02d:%02d", secs/60, secs%60);
        boolean resting = restEnd > System.currentTimeMillis();

        String title = resting ? "راحة " + time + " • " + workoutName : workoutName;
        String detail = exercise;
        if (!setLabel.isEmpty()) detail += " • " + setLabel;
        if (weight > 0 || reps > 0) detail += " • " + trim(weight) + " كجم × " + reps;

        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(com.addati.app.R.drawable.ic_launcher)
         .setContentTitle(title)
         .setContentText(detail)
         .setContentIntent(openPi)
         .setOngoing(true)
         .setOnlyAlertOnce(true)
         .setCategory(Notification.CATEGORY_WORKOUT)
         .setColor(Color.rgb(145,255,42))
         .setShowWhen(false)
         .addAction(new Notification.Action.Builder(Icon.createWithResource(this, com.addati.app.R.drawable.ic_launcher), "✓ الجولة", serviceAction(ACTION_COMPLETE_SET, 2)).build());

        if (resting) {
            b.addAction(new Notification.Action.Builder(Icon.createWithResource(this, com.addati.app.R.drawable.ic_launcher), "+15ث", serviceAction(ACTION_ADD15, 3)).build());
            b.addAction(new Notification.Action.Builder(Icon.createWithResource(this, com.addati.app.R.drawable.ic_launcher), "تخطي", serviceAction(ACTION_SKIP_REST, 4)).build());
        }

        Notification notification = b.build();
        addXiaomiIslandExtras(notification, title, detail, resting ? time : "تمرّن");
        return notification;
    }

    private String trim(double v) {
        if (v == (long)v) return String.valueOf((long)v);
        return String.format(Locale.US, "%.1f", v);
    }

    private void addXiaomiIslandExtras(Notification n, String title, String detail, String ticker) {
        try {
            String safeTitle = json(title), safeDetail = json(detail), safeTicker = json(ticker);
            String params = "{\"param_v2\":{" +
                "\"protocol\":1," +
                "\"business\":\"fitness\"," +
                "\"enableFloat\":false," +
                "\"updatable\":true," +
                "\"ticker\":\"" + safeTicker + "\"," +
                "\"aodTitle\":\"" + safeTicker + "\"," +
                "\"param_island\":{" +
                    "\"islandProperty\":1," +
                    "\"islandTimeout\":7200," +
                    "\"highlightColor\":\"#91FF2A\"," +
                    "\"bigIslandArea\":{}," +
                    "\"smallIslandArea\":{}" +
                "}," +
                "\"baseInfo\":{" +
                    "\"title\":\"" + safeTitle + "\"," +
                    "\"content\":\"" + safeDetail + "\"," +
                    "\"colorTitle\":\"#91FF2A\"," +
                    "\"type\":2" +
                "}" +
            "}}";
            n.extras.putString("miui.focus.param", params);
        } catch (Throwable ignored) {}
    }

    private String json(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(ID, buildNotification());
    }

    private void sendCommandToWeb(String cmd) {
        MainActivity a = MainActivity.instance;
        if (a != null) {
            if ("complete_set".equals(cmd)) a.runJs("window.nativeCompletePendingSet && window.nativeCompletePendingSet()");
            if ("skip_rest".equals(cmd)) a.runJs("window.nativeSkipRest && window.nativeSkipRest()");
        } else {
            getSharedPreferences("addati_native", MODE_PRIVATE).edit().putString("pending_command", cmd).apply();
        }
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
