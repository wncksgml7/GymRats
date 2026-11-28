package com.gymrats.ui.worldcup;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.gymrats.R;
import com.gymrats.databinding.FragmentWorldCupRankingBinding;

public class WorldCupRankingFragment extends Fragment {

    public WorldCupRankingFragment() {
        super(R.layout.fragment_world_cup_ranking);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        FragmentWorldCupRankingBinding binding =
                FragmentWorldCupRankingBinding.bind(view);

        binding.btnBackRanking.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        addRankingRow(binding, 1, "덤벨 플라이", "운동하는 민수", 40, 75, 0);
        addRankingRow(binding, 2, "원암 덤벨 로우", "김코치", 30, 68, 0);
        addRankingRow(binding, 3, "고블릿 스쿼트", "하체요정", 20, 62, 0);
        addRankingRow(binding, 4, "레터럴 레이즈", "어깨깡패", 10, 54, 0);
    }

    private void addRankingRow(
            FragmentWorldCupRankingBinding binding,
            int rank,
            String title,
            String author,
            int championRate,
            int matchWinRate,
            int thumbnailRes
    ) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(16), 0, dp(16));

        int[] rankColors = {0xFFAC7F27, 0xFF768197, 0xFF9E6235};
        int rankColor = rank <= 3 ? rankColors[rank - 1] : 0xFF121826;
        TextView rankView = rankingText(String.valueOf(rank), 18, rankColor);
        rankView.setGravity(Gravity.CENTER);
        rankView.setTypeface(null, Typeface.BOLD);
        row.addView(rankView, dp(28), -2);

        ImageView thumbnail = new ImageView(requireContext());
        GradientDrawable thumbnailShape = new GradientDrawable();
        thumbnailShape.setColor(0xFFECEEF5);
        thumbnailShape.setCornerRadius(dp(12));
        thumbnail.setBackground(thumbnailShape);
        thumbnail.setClipToOutline(true);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (thumbnailRes != 0) {
            thumbnail.setImageResource(thumbnailRes);
        }
        row.addView(thumbnail, dp(58), dp(66));

        LinearLayout details = new LinearLayout(requireContext());
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailsParams =
                new LinearLayout.LayoutParams(0, -2, 1f);
        detailsParams.setMarginStart(dp(12));
        row.addView(details, detailsParams);

        TextView titleView = rankingText(title, 15, 0xFF121826);
        titleView.setTypeface(null, Typeface.BOLD);
        details.addView(titleView);

        TextView authorView = rankingText(author, 12, 0xFF626C7F);
        authorView.setPadding(0, dp(4), 0, dp(4));
        details.addView(authorView);

        addRateBar(details, "우승 선택률", championRate);
        addRateBar(details, "1:1 승률", matchWinRate);

        binding.rankingContainer.addView(row, -1, -2);

        View divider = new View(requireContext());
        divider.setBackgroundColor(0xFFE7E9F0);
        binding.rankingContainer.addView(divider, -1, dp(1));
    }

    private void addRateBar(LinearLayout parent, String label, int percent) {
        LinearLayout line = new LinearLayout(requireContext());
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(dp(22));

        line.addView(rankingText(label, 11, 0xFF626C7F), dp(62), -2);

        ProgressBar bar = new ProgressBar(
                requireContext(), null, android.R.attr.progressBarStyleHorizontal
        );
        bar.setMax(100);
        bar.setProgress(percent);
        bar.setProgressTintList(ColorStateList.valueOf(0xFF5865D8));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFECEEF5));
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.addView(bar, new LinearLayout.LayoutParams(0, dp(8), 1f));

        TextView value = rankingText(percent + "%", 11, 0xFF121826);
        value.setGravity(Gravity.END);
        line.addView(value, dp(36), -2);

        parent.addView(line, -1, -2);
    }

    private TextView rankingText(String text, int size, int color) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}