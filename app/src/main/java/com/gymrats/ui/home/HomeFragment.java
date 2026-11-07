package com.gymrats.ui.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gymrats.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Locale;

public class HomeFragment extends Fragment {
    private static final String PREFS = "gymrats_home_widgets";
    private static final String ADDED = "workout_timer_added";
    private static final String ELAPSED = "workout_timer_elapsed";
    private static final String STARTED = "workout_timer_started";
    private static final String RUNNING = "workout_timer_running";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (timerRunning && timerText != null && isAdded()) {
                renderTimer();
                handler.postDelayed(this, 250);
            }
        }
    };

    private SharedPreferences prefs;
    private LinearLayout widgetContainer;
    private TextView timerText;
    private Button startButton, pauseButton, resetButton;
    private View timerCard;
    private Button moveUpButton, moveDownButton;
    private long elapsedBeforeStart, startedAt;
    private boolean timerRunning, timerAdded;

    public HomeFragment() { super(R.layout.fragment_home); }

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        timerAdded = prefs.getBoolean(ADDED, false);
        elapsedBeforeStart = prefs.getLong(ELAPSED, 0L);
        startedAt = prefs.getLong(STARTED, 0L);
        timerRunning = prefs.getBoolean(RUNNING, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        widgetContainer = view.findViewById(R.id.widgetContainer);
        view.findViewById(R.id.btnAddWidget).setOnClickListener(v -> showWidgetSheet());
        if (timerAdded) addWorkoutTimerWidget();
    }

    private void showWidgetSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        LinearLayout sheet = new LinearLayout(requireContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(24), dp(20), dp(24), dp(28));
        sheet.setBackgroundColor(0xFFFFFFFF);

        View handle = new View(requireContext());
        handle.setBackgroundColor(0xFFD8D8D8);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(42), dp(4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = dp(20);
        sheet.addView(handle, hp);

        TextView title = new TextView(requireContext());
        title.setText("위젯 추가");
        title.setTextColor(0xFF171717);
        title.setTextSize(23);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        sheet.addView(title);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText("홈에 필요한 위젯을 추가해보세요.");
        subtitle.setTextColor(0xFF888888);
        subtitle.setTextSize(14);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(6);
        sp.bottomMargin = dp(18);
        sheet.addView(subtitle, sp);

        addWidgetRow(sheet, "오늘의 루틴", "저장한 운동 루틴을 빠르게 확인하세요.", "추가됨", true, null);
        addWidgetRow(sheet, "세트 사이 휴식 타이머", "세트 사이 휴식 시간을 관리하세요.", "추가", false,
                () -> android.widget.Toast.makeText(requireContext(), "휴식 타이머는 다음 단계에서 연결할게요.", android.widget.Toast.LENGTH_SHORT).show());
        addWidgetRow(sheet, "운동 타이머", "운동 경과 시간을 확인하세요.",
                timerAdded ? "추가됨" : "추가", timerAdded, () -> {
                    timerAdded = true;
                    prefs.edit().putBoolean(ADDED, true).apply();
                    if (timerText == null) addWorkoutTimerWidget();
                    dialog.dismiss();
                });

        TextView manage = new TextView(requireContext());
        manage.setText("⚙  위젯 관리");
        manage.setTextColor(0xFF555555);
        manage.setTextSize(15);
        manage.setPadding(0, dp(20), 0, dp(8));
        sheet.addView(manage);

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void addWidgetRow(LinearLayout parent, String name, String desc,
                              String actionLabel, boolean disabled, Runnable action) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(13), 0, dp(13));

        LinearLayout textBox = new LinearLayout(requireContext());
        textBox.setOrientation(LinearLayout.VERTICAL);
        TextView nameView = new TextView(requireContext());
        nameView.setText(name);
        nameView.setTextColor(0xFF222222);
        nameView.setTextSize(15);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView descView = new TextView(requireContext());
        descView.setText(desc);
        descView.setTextColor(0xFF999999);
        descView.setTextSize(12);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-2, -2);
        dp.topMargin = dp(4);
        textBox.addView(nameView);
        textBox.addView(descView, dp);
        row.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1));

        Button actionButton = new Button(requireContext());
        actionButton.setText(actionLabel);
        actionButton.setTextSize(12);
        actionButton.setAllCaps(false);
        actionButton.setEnabled(!disabled && action != null);
        actionButton.setBackgroundTintList(ColorStateList.valueOf(
                disabled || action == null ? 0xFFF3F4F6 : 0xFF5B6BD5));
        actionButton.setTextColor(disabled || action == null ? 0xFF999999 : 0xFFFFFFFF);
        if (action != null && !disabled) actionButton.setOnClickListener(v -> action.run());
        row.addView(actionButton, new LinearLayout.LayoutParams(dp(86), dp(44)));
        parent.addView(row);
        View divider = new View(requireContext());
        divider.setBackgroundColor(0xFFF0F0F0);
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void addWorkoutTimerWidget() {
        if (widgetContainer == null || timerText != null) return;
        timerCard = LayoutInflater.from(requireContext())
                .inflate(R.layout.widget_workout_timer, widgetContainer, false);

        widgetContainer.addView(timerCard);

        moveUpButton = timerCard.findViewById(R.id.btnMoveUp);
        moveDownButton = timerCard.findViewById(R.id.btnMoveDown);

        moveUpButton.setOnClickListener(v -> moveWidget(-1));
        moveDownButton.setOnClickListener(v -> moveWidget(1));
        timerText = timerCard.findViewById(R.id.tvWorkoutElapsed);
        startButton = timerCard.findViewById(R.id.btnTimerStart);
        pauseButton = timerCard.findViewById(R.id.btnTimerPause);
        resetButton = timerCard.findViewById(R.id.btnTimerReset);
        startButton.setOnClickListener(v -> startTimer());
        pauseButton.setOnClickListener(v -> pauseTimer());
        resetButton.setOnClickListener(v -> resetTimer());
        renderTimer();
        updateButtons();
        if (timerRunning) {
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }
    }

    private void startTimer() {
        if (timerRunning) return;
        startedAt = SystemClock.elapsedRealtime();
        timerRunning = true;
        saveState();
        updateButtons();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void pauseTimer() {
        if (!timerRunning) return;
        elapsedBeforeStart += SystemClock.elapsedRealtime() - startedAt;
        timerRunning = false;
        handler.removeCallbacks(ticker);
        saveState();
        renderTimer();
        updateButtons();
    }

    private void resetTimer() {
        timerRunning = false;
        elapsedBeforeStart = 0L;
        startedAt = 0L;
        handler.removeCallbacks(ticker);
        saveState();
        renderTimer();
        updateButtons();
    }

    private void renderTimer() {
        if (timerText == null) return;
        long elapsed = elapsedBeforeStart;
        if (timerRunning) elapsed += SystemClock.elapsedRealtime() - startedAt;
        long seconds = elapsed / 1000L;
        timerText.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d",
                seconds / 3600L, (seconds % 3600L) / 60L, seconds % 60L));
    }

    private void updateButtons() {
        if (startButton == null) return;
        startButton.setVisibility(timerRunning ? View.GONE : View.VISIBLE);
        pauseButton.setVisibility(timerRunning ? View.VISIBLE : View.GONE);
        resetButton.setVisibility(elapsedBeforeStart > 0L || timerRunning ? View.VISIBLE : View.GONE);
    }

    private void saveState() {
        if (prefs == null) return;
        prefs.edit().putBoolean(ADDED, timerAdded).putLong(ELAPSED, elapsedBeforeStart)
                .putLong(STARTED, startedAt).putBoolean(RUNNING, timerRunning).apply();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onResume() {
        super.onResume();
        if (timerRunning && timerText != null) {
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }
        renderTimer();
    }

    @Override public void onPause() {
        handler.removeCallbacks(ticker);
        saveState();
        super.onPause();
    }

    @Override public void onDestroyView() {
        handler.removeCallbacks(ticker);
        timerText = null;
        startButton = null;
        pauseButton = null;
        resetButton = null;
        widgetContainer = null;
        super.onDestroyView();
    }
    private void moveWidget(int direction) {
        if (timerCard == null || widgetContainer == null) return;

        int currentPosition = widgetContainer.indexOfChild(timerCard);
        int newPosition = currentPosition + direction;

        if (newPosition < 0 || newPosition >= widgetContainer.getChildCount()) {
            return;
        }

        widgetContainer.removeView(timerCard);
        widgetContainer.addView(timerCard, newPosition);
    }
}
