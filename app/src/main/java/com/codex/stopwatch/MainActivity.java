package com.codex.stopwatch;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "stopwatch_state";
    private static final long TRICK_SNAP_WINDOW_MILLIS = 200L;
    private static final String BANNER_AD_UNIT_ID = "ca-app-pub-5213198969974533/7689431090";
    private static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5213198969974533/9549307677";
    private static final int BLACK = Color.rgb(0, 0, 0);
    private static final int WHITE = Color.rgb(245, 245, 247);
    private static final int SUBTLE = Color.rgb(118, 118, 128);
    private static final int DIVIDER = Color.rgb(28, 28, 30);
    private static final int SURFACE = Color.rgb(10, 10, 12);
    private static final int RING_TRACK = Color.rgb(35, 38, 42);
    private static final int GRAY_BUTTON = Color.rgb(44, 44, 46);
    private static final int GREEN_BUTTON = Color.rgb(8, 93, 36);
    private static final int GREEN_TEXT = Color.rgb(94, 255, 130);
    private static final int RED_BUTTON = Color.rgb(88, 24, 24);
    private static final int RED_TEXT = Color.rgb(255, 115, 104);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<LapEntry> laps = new ArrayList<>();

    private TextView timeView;
    private TextView leftButton;
    private TextView rightButton;
    private TimerDialView timerDialView;
    private LinearLayout lapListView;
    private AdView bannerAdView;
    private InterstitialAd interstitialAd;

    private long baseElapsedMillis;
    private long startRealtimeMillis;
    private long lastLapTotalMillis;
    private boolean running;
    private boolean trickMode;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateTime();
            if (running) {
                handler.postDelayed(this, 20L);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        restoreState();
        setContentView(createContentView());
        updateTime();
        updateControls();
        renderLaps();

        MobileAds.initialize(this, initializationStatus -> {
            loadBannerAd();
            loadInterstitialAd();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (running) {
            startTicker();
        }
        if (bannerAdView != null) {
            bannerAdView.resume();
        }
    }

    @Override
    protected void onPause() {
        saveState();
        handler.removeCallbacks(ticker);
        if (bannerAdView != null) {
            bannerAdView.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (bannerAdView != null) {
            bannerAdView.destroy();
        }
        super.onDestroy();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(BLACK);
        window.setNavigationBarColor(BLACK);
    }

    @SuppressLint("ClickableViewAccessibility")
    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackground(createRootBackground());
        root.setPadding(dp(22), dp(18), dp(22), dp(12));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    dp(22),
                    insets.getSystemWindowInsetTop() + dp(14),
                    dp(22),
                    Math.max(dp(12), insets.getSystemWindowInsetBottom() + dp(8))
            );
            return insets;
        });
        root.post(root::requestApplyInsets);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("StopGo");
        title.setTextColor(WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        title.setIncludeFontPadding(false);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1f));

        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
        ));

        Space topSpacer = new Space(this);
        root.addView(topSpacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                0.8f
        ));

        FrameLayout dialFrame = new FrameLayout(this);
        timerDialView = new TimerDialView(this);
        dialFrame.addView(timerDialView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        timeView = new TextView(this);
        timeView.setGravity(Gravity.CENTER);
        timeView.setTextColor(WHITE);
        timeView.setTextSize(46);
        timeView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        timeView.setIncludeFontPadding(false);
        timeView.setLetterSpacing(0f);
        timeView.setSingleLine(true);
        dialFrame.addView(timeView, new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        root.addView(dialFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(288)
        ));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(88)
        );
        controlsParams.topMargin = dp(18);

        leftButton = createCircleButton();
        leftButton.setOnClickListener(view -> handleLeftAction());
        controls.addView(leftButton);

        Space middleSpace = new Space(this);
        controls.addView(middleSpace, new LinearLayout.LayoutParams(0, 1, 1f));

        rightButton = createCircleButton();
        rightButton.setOnClickListener(view -> handleRightAction());
        controls.addView(rightButton);
        root.addView(controls, controlsParams);

        LinearLayout lapHeader = new LinearLayout(this);
        lapHeader.setGravity(Gravity.CENTER_VERTICAL);
        lapHeader.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lapHeaderParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
        );
        lapHeaderParams.topMargin = dp(8);
        lapHeader.addView(createLapText("랩", 14, SUBTLE, Gravity.START), new LinearLayout.LayoutParams(0, dp(64), 0.75f));
        lapHeader.addView(createSecretSegmentHeader(), new LinearLayout.LayoutParams(0, dp(64), 1.8f));
        lapHeader.addView(createLapText("전체", 14, SUBTLE, Gravity.END), new LinearLayout.LayoutParams(0, dp(64), 0.75f));
        root.addView(lapHeader, lapHeaderParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        lapListView = new LinearLayout(this);
        lapListView.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(lapListView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        root.addView(scrollView, listParams);

        bannerAdView = new AdView(this);
        bannerAdView.setAdUnitId(BANNER_AD_UNIT_ID);
        bannerAdView.setAdSize(adaptiveBannerSize());
        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bannerParams.gravity = Gravity.CENTER_HORIZONTAL;
        bannerParams.topMargin = dp(6);
        // 루트 좌우 패딩(22dp)을 상쇄해 배너를 화면 양 끝까지 채운다.
        bannerParams.leftMargin = -dp(22);
        bannerParams.rightMargin = -dp(22);
        root.addView(bannerAdView, bannerParams);

        return root;
    }

    private AdSize adaptiveBannerSize() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int adWidthDp = Math.round(metrics.widthPixels / metrics.density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidthDp);
    }

    private void loadBannerAd() {
        if (bannerAdView != null) {
            bannerAdView.loadAd(new AdRequest.Builder().build());
        }
    }

    private void loadInterstitialAd() {
        InterstitialAd.load(
                this,
                INTERSTITIAL_AD_UNIT_ID,
                new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialAd = ad;
                        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                interstitialAd = null;
                                loadInterstitialAd();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError adError) {
                                interstitialAd = null;
                                loadInterstitialAd();
                            }
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        interstitialAd = null;
                    }
                }
        );
    }

    private void showInterstitialAd() {
        if (interstitialAd != null) {
            interstitialAd.show(this);
        } else {
            // 아직 준비되지 않았으면 다음 리셋을 위해 미리 로드한다.
            loadInterstitialAd();
        }
    }

    private View createSecretSegmentHeader() {
        FrameLayout touchLayer = new FrameLayout(this);
        touchLayer.setBackgroundColor(Color.TRANSPARENT);
        touchLayer.setSoundEffectsEnabled(false);
        touchLayer.setHapticFeedbackEnabled(false);

        TextView segmentHeader = createLapText(trickMode ? "구갼" : "구간", 12, SUBTLE, Gravity.CENTER);
        segmentHeader.setBackgroundColor(Color.TRANSPARENT);
        segmentHeader.setSoundEffectsEnabled(false);
        segmentHeader.setHapticFeedbackEnabled(false);
        touchLayer.addView(segmentHeader, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        touchLayer.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                trickMode = !trickMode;
                segmentHeader.setText(trickMode ? "구갼" : "구간");
            }
            return true;
        });
        return touchLayer;
    }

    private GradientDrawable createRootBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.rgb(8, 10, 10),
                        BLACK,
                        Color.rgb(0, 5, 2)
                }
        );
        background.setDither(true);
        return background;
    }

    private TextView createCircleButton() {
        TextView button = new TextView(this);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(16);
        button.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        button.setIncludeFontPadding(false);
        button.setMinWidth(dp(76));
        button.setMinHeight(dp(76));
        button.setClickable(true);
        button.setFocusable(true);
        button.setStateListAnimator(null);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(76), dp(76)));
        return button;
    }

    private void handleLeftAction() {
        if (running) {
            recordLap();
            leftButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            return;
        }

        if (baseElapsedMillis > 0L || !laps.isEmpty()) {
            resetStopwatch();
            leftButton.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            showInterstitialAd();
        }
    }

    private void handleRightAction() {
        if (running) {
            pauseStopwatch();
        } else {
            startStopwatch();
        }
        rightButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void startStopwatch() {
        startRealtimeMillis = SystemClock.elapsedRealtime();
        running = true;
        startTicker();
        updateControls();
    }

    private void pauseStopwatch() {
        baseElapsedMillis = applyTrickSnapMillis(currentElapsedMillis());
        running = false;
        handler.removeCallbacks(ticker);
        updateTime();
        updateControls();
    }

    private void resetStopwatch() {
        running = false;
        baseElapsedMillis = 0L;
        startRealtimeMillis = 0L;
        lastLapTotalMillis = 0L;
        laps.clear();
        handler.removeCallbacks(ticker);
        updateTime();
        updateControls();
        renderLaps();
    }

    private void recordLap() {
        long total = currentElapsedMillis();
        long lapDuration = Math.max(0L, total - lastLapTotalMillis);
        lastLapTotalMillis = total;
        laps.add(0, new LapEntry(laps.size() + 1, lapDuration, total));
        renderLaps();
    }

    private void startTicker() {
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private long currentElapsedMillis() {
        if (!running) {
            return baseElapsedMillis;
        }
        return baseElapsedMillis + Math.max(0L, SystemClock.elapsedRealtime() - startRealtimeMillis);
    }

    private void updateTime() {
        long elapsedMillis = currentElapsedMillis();
        String time = formatDuration(elapsedMillis);
        timeView.setText(time);
        timeView.setTextSize(time.length() > 8 ? 39 : 46);
        if (timerDialView != null) {
            timerDialView.setState(elapsedMillis, running);
        }
    }

    private void updateControls() {
        boolean hasElapsed = currentElapsedMillis() > 0L || !laps.isEmpty();
        if (running) {
            styleButton(leftButton, "랩", GRAY_BUTTON, WHITE, true, "랩 기록");
            styleButton(rightButton, "정지", RED_BUTTON, RED_TEXT, true, "스톱워치 정지");
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            return;
        }

        styleButton(leftButton, "리셋", GRAY_BUTTON, WHITE, hasElapsed, "스톱워치 초기화");
        styleButton(rightButton, hasElapsed ? "계속" : "시작", GREEN_BUTTON, GREEN_TEXT, true,
                hasElapsed ? "스톱워치 계속" : "스톱워치 시작");
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void styleButton(
            TextView button,
            String label,
            int backgroundColor,
            int textColor,
            boolean enabled,
            String contentDescription
    ) {
        button.setText(label);
        button.setTextColor(textColor);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.38f);
        button.setContentDescription(contentDescription);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(backgroundColor);
        background.setStroke(dp(2), Color.argb(enabled ? 92 : 42, 255, 255, 255));
        button.setBackground(background);
    }

    private void renderLaps() {
        lapListView.removeAllViews();
        for (LapEntry lap : laps) {
            lapListView.addView(createDivider());
            lapListView.addView(createLapRow(lap));
        }
        if (!laps.isEmpty()) {
            lapListView.addView(createDivider());
        }
    }

    private View createLapRow(LapEntry lap) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, 0);

        TextView number = createLapText("#" + lap.number, 16, SUBTLE, Gravity.START);
        TextView lapTime = createLapText(formatDuration(lap.lapDurationMillis), 20, WHITE, Gravity.CENTER);
        TextView totalTime = createLapText(formatDuration(lap.totalMillis), 16, SUBTLE, Gravity.END);

        row.addView(number, new LinearLayout.LayoutParams(0, dp(54), 0.7f));
        row.addView(lapTime, new LinearLayout.LayoutParams(0, dp(54), 1.15f));
        row.addView(totalTime, new LinearLayout.LayoutParams(0, dp(54), 1.15f));
        return row;
    }

    private TextView createLapText(String text, int sizeSp, int color, int gravity) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setGravity(gravity | Gravity.CENTER_VERTICAL);
        view.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        return view;
    }

    private View createDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1))
        ));
        return divider;
    }

    private String formatDuration(long millis) {
        long totalCentiseconds = millis / 10L;
        long centiseconds = totalCentiseconds % 100L;
        long totalSeconds = totalCentiseconds / 100L;
        long seconds = totalSeconds % 60L;
        long totalMinutes = totalSeconds / 60L;
        long minutes = totalMinutes % 60L;
        long hours = totalMinutes / 60L;

        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, centiseconds);
        }
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centiseconds);
    }

    private long applyTrickSnapMillis(long elapsedMillis) {
        if (!trickMode) {
            return elapsedMillis;
        }

        long nearestSecond = Math.round(elapsedMillis / 1000f);
        if (nearestSecond < 1L) {
            return elapsedMillis;
        }

        long targetMillis = nearestSecond * 1000L;
        long distanceFromTarget = elapsedMillis - targetMillis;
        if (Math.abs(distanceFromTarget) <= TRICK_SNAP_WINDOW_MILLIS) {
            return distanceFromTarget <= 0L ? targetMillis : targetMillis + 10L;
        }
        return elapsedMillis;
    }

    private void saveState() {
        StringBuilder serializedLaps = new StringBuilder();
        for (LapEntry lap : laps) {
            if (serializedLaps.length() > 0) {
                serializedLaps.append(';');
            }
            serializedLaps
                    .append(lap.number)
                    .append(',')
                    .append(lap.lapDurationMillis)
                    .append(',')
                    .append(lap.totalMillis);
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean("running", running)
                .putLong("baseElapsedMillis", baseElapsedMillis)
                .putLong("startRealtimeMillis", startRealtimeMillis)
                .putLong("lastLapTotalMillis", lastLapTotalMillis)
                .putBoolean("trickMode", trickMode)
                .putString("laps", serializedLaps.toString())
                .apply();
    }

    private void restoreState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        running = prefs.getBoolean("running", false);
        baseElapsedMillis = prefs.getLong("baseElapsedMillis", 0L);
        startRealtimeMillis = prefs.getLong("startRealtimeMillis", 0L);
        lastLapTotalMillis = prefs.getLong("lastLapTotalMillis", 0L);
        trickMode = prefs.getBoolean("trickMode", false);

        if (running && startRealtimeMillis > SystemClock.elapsedRealtime()) {
            running = false;
        }

        laps.clear();
        String serializedLaps = prefs.getString("laps", "");
        if (serializedLaps == null || serializedLaps.isEmpty()) {
            return;
        }

        String[] rows = serializedLaps.split(";");
        for (String row : rows) {
            String[] parts = row.split(",");
            if (parts.length != 3) {
                continue;
            }
            try {
                laps.add(new LapEntry(
                        Integer.parseInt(parts[0]),
                        Long.parseLong(parts[1]),
                        Long.parseLong(parts[2])
                ));
            } catch (NumberFormatException ignored) {
                // Corrupt persisted rows are ignored so the timer can still open.
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class TimerDialView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcBounds = new RectF();
        private long elapsedMillis;
        private boolean active;

        TimerDialView(Activity context) {
            super(context);
        }

        void setState(long elapsedMillis, boolean active) {
            this.elapsedMillis = elapsedMillis;
            this.active = active;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) / 2f - dp(15);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(SURFACE);
            canvas.drawCircle(centerX, centerY, radius + dp(7), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(7));
            paint.setColor(RING_TRACK);
            arcBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
            canvas.drawArc(arcBounds, -90f, 360f, false, paint);

            float progress = (elapsedMillis % 60000L) / 60000f;
            paint.setColor(active ? GREEN_TEXT : Color.rgb(72, 82, 77));
            canvas.drawArc(arcBounds, -90f, Math.max(1.8f, progress * 360f), false, paint);

            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.argb(150, 150, 150, 158));
            for (int tick = 0; tick < 60; tick++) {
                double angle = Math.toRadians(tick * 6d - 90d);
                float outerX = centerX + (float) Math.cos(angle) * (radius - dp(15));
                float outerY = centerY + (float) Math.sin(angle) * (radius - dp(15));
                float innerRadius = radius - (tick % 5 == 0 ? dp(26) : dp(21));
                float innerX = centerX + (float) Math.cos(angle) * innerRadius;
                float innerY = centerY + (float) Math.sin(angle) * innerRadius;
                paint.setStrokeWidth(tick % 5 == 0 ? dp(2) : dp(1));
                canvas.drawLine(innerX, innerY, outerX, outerY, paint);
            }

            if (elapsedMillis > 0L || active) {
                double dotAngle = Math.toRadians(progress * 360d - 90d);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(active ? WHITE : SUBTLE);
                canvas.drawCircle(
                        centerX + (float) Math.cos(dotAngle) * radius,
                        centerY + (float) Math.sin(dotAngle) * radius,
                        dp(4),
                        paint
                );
            }
        }
    }

    private static class LapEntry {
        final int number;
        final long lapDurationMillis;
        final long totalMillis;

        LapEntry(int number, long lapDurationMillis, long totalMillis) {
            this.number = number;
            this.lapDurationMillis = lapDurationMillis;
            this.totalMillis = totalMillis;
        }
    }
}
