package com.gymrats.ui.home;

import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.widget.ScrollView;
import android.util.Log;
import android.widget.ScrollView;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.gymrats.R;

import java.util.ArrayList;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private static final String PREFS = "gymrats_home_widgets";
    private static final String ADDED = "workout_timer_added";
    private static final String ROUTINE_ADDED = "routine_widget_added";
    private static final String ORDER = "widget_order";
    private static final String ELAPSED = "workout_timer_elapsed";
    private static final String STARTED = "workout_timer_started";
    private static final String RUNNING = "workout_timer_running";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (timerRunning && timerText != null && isAdded()) {
                renderTimer();
                handler.postDelayed(this, 250);
            }
        }
    };

    private SharedPreferences prefs;

    private LinearLayout widgetContainer;
    private View routineWidget;
    private View timerCard;

    private TextView timerText;
    private Button startButton;
    private Button pauseButton;
    private Button resetButton;

    private final ArrayList<String> widgetOrder = new ArrayList<>();

    private long elapsedBeforeStart;
    private long startedAt;

    private boolean timerRunning;
    private boolean timerAdded;
    private boolean routineAdded;

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);

        prefs = requireContext().getSharedPreferences(
                PREFS, Context.MODE_PRIVATE);

        timerAdded = prefs.getBoolean(ADDED, false);
        routineAdded = prefs.getBoolean(ROUTINE_ADDED, true);

        elapsedBeforeStart = prefs.getLong(ELAPSED, 0L);
        startedAt = prefs.getLong(STARTED, 0L);
        timerRunning = prefs.getBoolean(RUNNING, false);

        String savedOrder = prefs.getString(ORDER, "");

        if (!savedOrder.isEmpty()) {
            for (String item : savedOrder.split(",")) {
                if (!widgetOrder.contains(item)) {
                    widgetOrder.add(item);
                }
            }
        } else {
            widgetOrder.add("routine");
            if (timerAdded) {
                widgetOrder.add("timer");
            }
        }

        if (routineAdded && !widgetOrder.contains("routine")) {
            widgetOrder.add("routine");
        }

        if (timerAdded && !widgetOrder.contains("timer")) {
            widgetOrder.add("timer");
        }

        if (!routineAdded) {
            widgetOrder.remove("routine");
        }

        if (!timerAdded) {
            widgetOrder.remove("timer");
        }
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle state) {

        super.onViewCreated(view, state);

        widgetContainer = view.findViewById(R.id.widgetContainer);
        routineWidget = view.findViewById(R.id.routineWidget);
        ScrollView scrollView = view.findViewById(R.id.homeScrollView);

        scrollView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE) {

                v.getParent().requestDisallowInterceptTouchEvent(true);
            }

            return false;
        });

        view.findViewById(R.id.btnAddWidget)
                .setOnClickListener(v -> showWidgetSheet());

        if (timerAdded) {
            createWorkoutTimerWidget();
        }

        renderWidgets();


        scrollView.post(() -> {
            View content = scrollView.getChildAt(0);

            Log.d("HOME_SCROLL",
                    "화면 높이: " + scrollView.getHeight()
                            + ", 콘텐츠 높이: " + content.getHeight()
                            + ", 스크롤 가능: "
                            + scrollView.canScrollVertically(1));
        });
    }

    // -------------------------------
    // 위젯 추가 화면
    // -------------------------------

    private void showWidgetSheet() {

        BottomSheetDialog dialog =
                new BottomSheetDialog(requireContext());

        LinearLayout sheet = createSheet();

        addHandle(sheet);

        // 제목 영역
        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(requireContext());
        title.setText("위젯 추가");
        title.setTextColor(0xFF171717);
        title.setTextSize(23);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        header.addView(title,
                new LinearLayout.LayoutParams(0, -2, 1));

        TextView close = new TextView(requireContext());
        close.setText("×");
        close.setTextSize(25);
        close.setTextColor(0xFF777777);
        close.setGravity(Gravity.CENTER);
        close.setBackground(roundedBackground(0xFFF5F6F8, 50));

        header.addView(close,
                new LinearLayout.LayoutParams(dp(42), dp(42)));

        close.setOnClickListener(v -> dialog.dismiss());

        sheet.addView(header);

        addSubtitle(sheet, "홈에 필요한 위젯을 추가해보세요.");

        // 오늘의 루틴
        addWidgetRow(sheet,
                "오늘의 루틴",
                "저장한 운동 루틴을 빠르게 확인하세요.",
                routineAdded,
                () -> {
                    routineAdded = true;

                    if (!widgetOrder.contains("routine")) {
                        widgetOrder.add("routine");
                    }

                    saveState();
                    saveWidgetOrder();
                    renderWidgets();
                    dialog.dismiss();
                },
                () -> {
                    routineAdded = false;
                    widgetOrder.remove("routine");

                    saveState();
                    saveWidgetOrder();
                    renderWidgets();
                });

        // 세트 사이 휴식 타이머
        addWidgetRow(sheet,
                "세트 사이 휴식 타이머",
                "세트 사이 휴식 시간을 관리하세요.",
                true,
                null,
                null);

        // 운동 타이머
        addWidgetRow(sheet,
                "운동 타이머",
                "운동 경과 시간을 확인하세요.",
                timerAdded,
                () -> {
                    timerAdded = true;

                    if (!widgetOrder.contains("timer")) {
                        widgetOrder.add("timer");
                    }

                    createWorkoutTimerWidget();

                    saveState();
                    saveWidgetOrder();
                    renderWidgets();
                    dialog.dismiss();
                },
                () -> {
                    timerAdded = false;
                    widgetOrder.remove("timer");

                    saveState();
                    saveWidgetOrder();
                    renderWidgets();
                });

        // 위젯 관리 메뉴
        View divider = new View(requireContext());
        divider.setBackgroundColor(0xFFEAEAEA);

        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(-1, dp(1));

        dividerParams.topMargin = dp(10);

        sheet.addView(divider, dividerParams);

        LinearLayout manage = new LinearLayout(requireContext());
        manage.setGravity(Gravity.CENTER_VERTICAL);
        manage.setPadding(0, dp(18), 0, dp(5));

        TextView gear = new TextView(requireContext());
        gear.setText("⚙");
        gear.setTextSize(21);
        gear.setTextColor(0xFF777777);

        manage.addView(gear);

        TextView manageText = new TextView(requireContext());
        manageText.setText("  위젯 관리");
        manageText.setTextSize(15);
        manageText.setTextColor(0xFF555555);
        manageText.setTypeface(null, android.graphics.Typeface.BOLD);

        manage.addView(manageText,
                new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = new TextView(requireContext());
        arrow.setText("›");
        arrow.setTextSize(26);
        arrow.setTextColor(0xFF999999);

        manage.addView(arrow);

        manage.setOnClickListener(v -> {
            dialog.dismiss();
            showManageSheet();
        });

        sheet.addView(manage);

        dialog.setContentView(sheet);
        dialog.show();
    }

    // -------------------------------
    // 위젯 관리 화면
    // -------------------------------

    private void showManageSheet() {

        BottomSheetDialog dialog =
                new BottomSheetDialog(requireContext());

        LinearLayout sheet = createSheet();

        addHandle(sheet);

        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = new TextView(requireContext());
        back.setText("‹");
        back.setTextSize(30);
        back.setTextColor(0xFF555555);
        back.setGravity(Gravity.CENTER);

        header.addView(back,
                new LinearLayout.LayoutParams(dp(40), dp(42)));

        TextView title = new TextView(requireContext());
        title.setText("위젯 관리");
        title.setTextSize(19);
        title.setTextColor(0xFF171717);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);

        header.addView(title,
                new LinearLayout.LayoutParams(0, -2, 1));

        TextView done = new TextView(requireContext());
        done.setText("완료");
        done.setTextSize(15);
        done.setTextColor(0xFF5B6BD5);
        done.setTypeface(null, android.graphics.Typeface.BOLD);
        done.setGravity(Gravity.CENTER);

        header.addView(done,
                new LinearLayout.LayoutParams(dp(40), dp(42)));

        sheet.addView(header);

        back.setOnClickListener(v -> {
            dialog.dismiss();
            showWidgetSheet();
        });

        done.setOnClickListener(v -> {
            saveWidgetOrder();
            renderWidgets();
            dialog.dismiss();
        });

        TextView subtitle = new TextView(requireContext());
        subtitle.setText("위젯의 순서를 변경하거나 제거할 수 있어요.");
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xFF999999);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(22), 0, dp(20));

        sheet.addView(subtitle);

        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);

        sheet.addView(list);

        populateManageList(list);

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void populateManageList(LinearLayout list) {

        list.removeAllViews();

        for (int i = 0; i < widgetOrder.size(); i++) {

            final int position = i;
            String id = widgetOrder.get(i);

            String name = id.equals("routine")
                    ? "오늘의 루틴"
                    : "운동 타이머";

            String symbol = id.equals("routine") ? "▦" : "◷";

            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(12), 0, dp(12));

            // 아이콘 타일
            TextView icon = new TextView(requireContext());
            icon.setText(symbol);
            icon.setTextSize(22);
            icon.setTextColor(0xFF5B6BD5);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(roundedBackground(0xFFF0F2FF, 15));

            LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(dp(46), dp(46));

            iconParams.rightMargin = dp(12);

            row.addView(icon, iconParams);

            // 위젯 이름
            TextView label = new TextView(requireContext());
            label.setText(name);
            label.setTextSize(14);
            label.setTextColor(0xFF222222);
            label.setTypeface(null, android.graphics.Typeface.BOLD);

            row.addView(label,
                    new LinearLayout.LayoutParams(0, -2, 1));

            // 위로 이동
            Button up = smallButton("↑", 0xFF777777);

            // 아래로 이동
            Button down = smallButton("↓", 0xFF777777);

            // 삭제
            Button remove = smallButton("−", 0xFFE05B5B);

            up.setEnabled(position > 0);
            down.setEnabled(position < widgetOrder.size() - 1);

            up.setAlpha(position > 0 ? 1f : 0.3f);
            down.setAlpha(position < widgetOrder.size() - 1 ? 1f : 0.3f);

            up.setOnClickListener(v -> {
                moveWidget(position, -1);
                populateManageList(list);
            });

            down.setOnClickListener(v -> {
                moveWidget(position, 1);
                populateManageList(list);
            });

            remove.setOnClickListener(v -> {
                removeWidget(id);
                renderWidgets();
                populateManageList(list);
            });

            row.addView(up);
            row.addView(down);
            row.addView(remove);

            list.addView(row);

            View divider = new View(requireContext());
            divider.setBackgroundColor(0xFFF0F0F0);

            list.addView(divider,
                    new LinearLayout.LayoutParams(-1, dp(1)));
        }
    }

    // -------------------------------
    // 위젯 순서 변경 및 삭제
    // -------------------------------

    private void moveWidget(int position, int direction) {

        int newPosition = position + direction;

        if (newPosition < 0 || newPosition >= widgetOrder.size()) {
            return;
        }

        String current = widgetOrder.get(position);

        widgetOrder.set(position, widgetOrder.get(newPosition));
        widgetOrder.set(newPosition, current);
    }

    private void removeWidget(String id) {

        widgetOrder.remove(id);

        if (id.equals("routine")) {
            routineAdded = false;
        }

        if (id.equals("timer")) {
            timerAdded = false;
        }

        saveState();
        saveWidgetOrder();
    }

    private void renderWidgets() {

        if (widgetContainer == null) return;

        widgetContainer.removeAllViews();

        for (String id : widgetOrder) {

            if (id.equals("routine") && routineAdded
                    && routineWidget != null) {

                if (routineWidget.getParent() instanceof ViewGroup) {
                    ((ViewGroup) routineWidget.getParent())
                            .removeView(routineWidget);
                }

                widgetContainer.addView(routineWidget);
            }

            if (id.equals("timer") && timerAdded
                    && timerCard != null) {

                if (timerCard.getParent() instanceof ViewGroup) {
                    ((ViewGroup) timerCard.getParent())
                            .removeView(timerCard);
                }

                widgetContainer.addView(timerCard);
            }
        }
    }

    private void saveWidgetOrder() {

        StringBuilder result = new StringBuilder();

        for (String id : widgetOrder) {
            if (result.length() > 0) {
                result.append(",");
            }
            result.append(id);
        }

        prefs.edit()
                .putString(ORDER, result.toString())
                .putBoolean(ROUTINE_ADDED, routineAdded)
                .putBoolean(ADDED, timerAdded)
                .apply();
    }

    // -------------------------------
    // 운동 타이머
    // -------------------------------

    private void createWorkoutTimerWidget() {

        if (timerCard != null || widgetContainer == null) return;

        timerCard = LayoutInflater.from(requireContext())
                .inflate(R.layout.widget_workout_timer,
                        widgetContainer, false);

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

        elapsedBeforeStart +=
                SystemClock.elapsedRealtime() - startedAt;

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

        if (timerRunning) {
            elapsed += SystemClock.elapsedRealtime() - startedAt;
        }

        long seconds = elapsed / 1000L;

        timerText.setText(String.format(
                Locale.getDefault(),
                "%02d:%02d:%02d",
                seconds / 3600L,
                (seconds % 3600L) / 60L,
                seconds % 60L
        ));
    }

    private void updateButtons() {

        if (startButton == null) return;

        startButton.setVisibility(
                timerRunning ? View.GONE : View.VISIBLE);

        pauseButton.setVisibility(
                timerRunning ? View.VISIBLE : View.GONE);

        resetButton.setVisibility(
                elapsedBeforeStart > 0L || timerRunning
                        ? View.VISIBLE : View.GONE);
    }

    private void saveState() {

        if (prefs == null) return;

        prefs.edit()
                .putBoolean(ADDED, timerAdded)
                .putBoolean(ROUTINE_ADDED, routineAdded)
                .putLong(ELAPSED, elapsedBeforeStart)
                .putLong(STARTED, startedAt)
                .putBoolean(RUNNING, timerRunning)
                .apply();
    }

    // -------------------------------
    // 화면 구성 보조 메서드
    // -------------------------------

    private LinearLayout createSheet() {

        LinearLayout sheet = new LinearLayout(requireContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(24), dp(18), dp(24), dp(28));
        sheet.setBackgroundColor(0xFFFFFFFF);

        return sheet;
    }

    private void addHandle(LinearLayout sheet) {

        View handle = new View(requireContext());
        handle.setBackgroundColor(0xFFD8D8D8);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(dp(42), dp(4));

        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = dp(20);

        sheet.addView(handle, params);
    }

    private void addTitle(LinearLayout sheet, String text) {

        TextView title = new TextView(requireContext());
        title.setText(text);
        title.setTextColor(0xFF171717);
        title.setTextSize(23);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        sheet.addView(title);
    }

    private void addSubtitle(LinearLayout sheet, String text) {

        TextView subtitle = new TextView(requireContext());
        subtitle.setText(text);
        subtitle.setTextColor(0xFF888888);
        subtitle.setTextSize(14);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.topMargin = dp(6);
        params.bottomMargin = dp(18);

        sheet.addView(subtitle, params);
    }

    private void addWidgetRow(
            LinearLayout parent,
            String name,
            String description,
            boolean added,
            Runnable addAction,
            Runnable removeAction) {

        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(13), 0, dp(13));

        // 위젯 아이콘
        String symbol;

        if (name.equals("오늘의 루틴")) {
            symbol = "▦";
        } else if (name.equals("운동 타이머")) {
            symbol = "◷";
        } else {
            symbol = "⌛";
        }

        TextView icon = new TextView(requireContext());
        icon.setText(symbol);
        icon.setTextSize(23);
        icon.setTextColor(0xFF5B6BD5);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundedBackground(0xFFF0F2FF, 15));

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(dp(46), dp(46));

        iconParams.rightMargin = dp(12);

        row.addView(icon, iconParams);

        // 텍스트 영역
        LinearLayout textBox = new LinearLayout(requireContext());
        textBox.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(requireContext());
        title.setText(name);
        title.setTextColor(0xFF222222);
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView desc = new TextView(requireContext());
        desc.setText(description);
        desc.setTextColor(0xFF999999);
        desc.setTextSize(12);

        LinearLayout.LayoutParams descParams =
                new LinearLayout.LayoutParams(-1, -2);

        descParams.topMargin = dp(3);

        textBox.addView(title);
        textBox.addView(desc, descParams);

        row.addView(textBox,
                new LinearLayout.LayoutParams(0, -2, 1));

        // 버튼 상태
        final boolean[] isAdded = {added};
        final boolean[] confirmed = {added};

        Button button = new Button(requireContext());

        button.setTextSize(12);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(dp(58));
        button.setMinimumHeight(dp(36));
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setStateListAnimator(null);

        Runnable updateButton = () -> {

            if (isAdded[0] || confirmed[0]) {

                button.setText("추가됨");
                button.setTextColor(0xFF999999);
                button.setBackground(
                        roundedBackground(0xFFF3F4F6, 11));

            } else {

                button.setText("추가");
                button.setTextColor(0xFFFFFFFF);
                button.setBackground(
                        roundedBackground(0xFF5B6BD5, 11));
            }
        };

        updateButton.run();

        button.setEnabled(
                addAction != null || removeAction != null);

        button.setOnClickListener(v -> {

            // 이미 홈 화면에 추가된 위젯 삭제
            if (isAdded[0]) {

                if (removeAction != null) {
                    removeAction.run();
                }

                isAdded[0] = false;
                confirmed[0] = false;

                updateButton.run();
                return;
            }

            // 첫 번째 클릭
            if (!confirmed[0]) {

                confirmed[0] = true;
                updateButton.run();

                return;
            }

            // 두 번째 클릭: 실제 추가
            if (addAction != null) {
                addAction.run();
                isAdded[0] = true;
            }
        });

        row.addView(button,
                new LinearLayout.LayoutParams(dp(58), dp(36)));

        parent.addView(row);

        View divider = new View(requireContext());
        divider.setBackgroundColor(0xFFF0F0F0);

        parent.addView(divider,
                new LinearLayout.LayoutParams(-1, dp(1)));
    }
    private Button smallButton(String text, int color) {

        Button button = new Button(requireContext());

        button.setText(text);
        button.setTextSize(19);
        button.setTextColor(color);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(dp(34));
        button.setMinimumHeight(dp(36));
        button.setPadding(0, 0, 0, 0);
        button.setStateListAnimator(null);

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(0xFFFFFFFF);
        background.setCornerRadius(dp(18));

        if (color == 0xFFE05B5B) {
            background.setStroke(dp(1), 0xFFE05B5B);
        }

        button.setBackground(background);

        return button;
    }

    private GradientDrawable roundedBackground(
            int color, int radius) {

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(color);
        background.setCornerRadius(dp(radius));

        return background;
    }

    // -------------------------------
    // 생명주기
    // -------------------------------

    @Override
    public void onResume() {
        super.onResume();

        if (timerRunning && timerText != null) {
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }

        renderTimer();
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(ticker);
        saveState();
        saveWidgetOrder();

        super.onPause();
    }

    @Override
    public void onDestroyView() {

        handler.removeCallbacks(ticker);

        timerText = null;
        startButton = null;
        pauseButton = null;
        resetButton = null;

        timerCard = null;
        routineWidget = null;
        widgetContainer = null;

        super.onDestroyView();
    }
    private int dp(float value) {
        return (int) (value *
                getResources().getDisplayMetrics().density + 0.5f);
    }

}
