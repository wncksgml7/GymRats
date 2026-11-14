package com.gymrats.ui.profile;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gymrats.R;
import com.gymrats.model.ProfileModule;
import com.gymrats.ui.auth.LoginActivity;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MyPageFragment extends Fragment {

    private static final int WEEKS = 12;
    private static final int DAYS = WEEKS * 7;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private TextView tvNickname, tvBio, tvFollowerCount, tvFollowingCount, tvPostCount;
    private TextView tvStreak, tvContributionTotal;
    private GridLayout gridContribution;
    private LinearLayout moduleContainer;

    // profileModules의 type → 화면의 카드 
    private final Map<String, View> moduleViews = new HashMap<>();

    public MyPageFragment() {
        super(R.layout.fragment_my_page);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvNickname = view.findViewById(R.id.tvNickname);
        tvBio = view.findViewById(R.id.tvBio);
        tvFollowerCount = view.findViewById(R.id.tvFollowerCount);
        tvFollowingCount = view.findViewById(R.id.tvFollowingCount);
        tvPostCount = view.findViewById(R.id.tvPostCount);
        tvStreak = view.findViewById(R.id.tvStreak);
        tvContributionTotal = view.findViewById(R.id.tvContributionTotal);
        gridContribution = view.findViewById(R.id.gridContribution);
        moduleContainer = view.findViewById(R.id.moduleContainer);

        moduleViews.clear();
        moduleViews.put(ProfileModule.CONTRIBUTION, view.findViewById(R.id.cardContribution));
        moduleViews.put(ProfileModule.ACHIEVEMENTS, view.findViewById(R.id.cardAchievements));
        moduleViews.put(ProfileModule.TROPHIES, view.findViewById(R.id.cardTrophies));
        moduleViews.put(ProfileModule.MY_POSTS, view.findViewById(R.id.cardMyPosts));
        moduleViews.put(ProfileModule.WORLDCUP, view.findViewById(R.id.cardWorldcup));
        moduleViews.put(ProfileModule.COLLECTIONS, view.findViewById(R.id.cardCollections));

        view.findViewById(R.id.btnEditProfile).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ProfileCustomizeActivity.class)));

        // 하위 화면은 다음주에 만들 예정
        View.OnClickListener notReady = v ->
                Toast.makeText(requireContext(), "준비 중입니다", Toast.LENGTH_SHORT).show();
        view.findViewById(R.id.btnShop).setOnClickListener(notReady);
        view.findViewById(R.id.tvAchievementAll).setOnClickListener(notReady);
        view.findViewById(R.id.tvTrophyManage).setOnClickListener(notReady);
        view.findViewById(R.id.tvMyPostsAll).setOnClickListener(notReady);
        view.findViewById(R.id.tvCollectionCount).setOnClickListener(notReady);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        TextView textEmail = view.findViewById(R.id.textEmail);
        if (user != null) {
            textEmail.setText(user.getEmail());
        }

        Button btnLogout = view.findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        applyModules(ProfileModule.defaults());
        showContributions(makeDummyContributions()); // contributions 컬렉션이 생기면 교체
    }

    @Override
    public void onResume() {
        super.onResume();
        // 프로필 꾸미기에서 저장하고 돌아왔을 때도 다시 읽도록 여기서 불러옴
        loadProfile();
    }

    private void loadProfile() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded() || getView() == null || !doc.exists()) return;
                    bindProfile(doc);
                    applyModules(ProfileModule.fromFirestore(doc.get("profileModules")));
                });

        // 게시글 수는 users에 따로 저장하지 않고 posts에서 개수만 셈
        db.collection("posts").whereEqualTo("authorId", uid)
                .count().get(AggregateSource.SERVER)
                .addOnSuccessListener(snap -> {
                    if (isAdded() && getView() != null) {
                        tvPostCount.setText(formatCount(snap.getCount()));
                    }
                });
    }

    private void bindProfile(DocumentSnapshot doc) {
        tvNickname.setText(doc.getString("nickname"));
        String bio = doc.getString("bio");
        tvBio.setText(bio == null || bio.isEmpty() ? "소개를 입력해 보세요" : bio);
        tvFollowerCount.setText(formatCount(getLong(doc, "followerCount")));
        tvFollowingCount.setText(formatCount(getLong(doc, "followingCount")));
    }

    // 카드를 저장된 순서대로 다시 넣고, 꺼 둔 모듈은 숨깁
    private void applyModules(List<ProfileModule> modules) {
        moduleContainer.removeAllViews();
        for (ProfileModule module : modules) {
            View card = moduleViews.get(module.getType());
            if (card == null) continue;
            card.setVisibility(module.isVisible() ? View.VISIBLE : View.GONE);
            moduleContainer.addView(card);
        }
    }

    private void showContributions(int[] counts) {
        gridContribution.removeAllViews();
        int size = dp(14);
        int gap = dp(2);
        int total = 0;
        for (int count : counts) {
            View cell = new View(requireContext());
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height = size;
            lp.setMargins(gap, gap, gap, gap);
            cell.setLayoutParams(lp);
            cell.setBackgroundColor(levelColor(count));
            gridContribution.addView(cell);
            total += count;
        }
        tvContributionTotal.setText("최근 " + WEEKS + "주 · 총 " + total + "회 기여");
        tvStreak.setText(currentStreak(counts) + "일 연속 기여");
    }

    // 오늘(배열 맨 끝)부터 거꾸로 세어 기여가 이어진 날 수
    private int currentStreak(int[] counts) {
        int streak = 0;
        for (int i = counts.length - 1; i >= 0 && counts[i] > 0; i--) streak++;
        return streak;
    }

    private int levelColor(int count) {
        if (count == 0) return Color.parseColor("#E6E8EC");
        if (count <= 1) return Color.parseColor("#C6E7C9");
        if (count <= 3) return Color.parseColor("#7CC489");
        return Color.parseColor("#3F8F4E");
    }

    private int[] makeDummyContributions() {
        int[] counts = new int[DAYS];
        Random random = new Random(1);
        for (int i = 0; i < DAYS; i++) counts[i] = random.nextInt(5);
        return counts;
    }

    private static long getLong(DocumentSnapshot doc, String field) {
        Long value = doc.getLong(field);
        return value == null ? 0 : value;
    }

    private static String formatCount(long n) {
        if (n >= 1000) return String.format(Locale.US, "%.1fK", n / 1000.0).replace(".0K", "K");
        return String.valueOf(n);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}