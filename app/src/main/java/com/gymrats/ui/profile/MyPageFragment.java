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
import com.google.android.gms.tasks.Tasks;
import android.os.Handler;
import android.os.Looper;
import com.gymrats.R;
import com.gymrats.model.ProfileModule;
import com.gymrats.ui.auth.LoginActivity;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.gymrats.ui.community.PostAdapter;
import java.util.Collections;

public class MyPageFragment extends Fragment {

    private static final int WEEKS = 12;
    private static final int DAYS = WEEKS * 7;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private TextView tvNickname, tvBio, tvFollowerCount, tvFollowingCount, tvPostCount;
    private TextView tvStreak, tvContributionTotal;
    private GridLayout gridContribution;
    private LinearLayout moduleContainer;
    private int loadRequest;
    private final Handler loadHandler = new Handler(Looper.getMainLooper());
    private Runnable loadTimeout;

    // 내가 만든 운동법 카드에 보여줄 최근 글 개수 지정
    private static final int MY_POSTS_PREVIEW = 4;
    private PostAdapter myPostsAdapter;

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

        // 아직 미구현인 것들은 준비중으로 표시
        View.OnClickListener notReady = v ->
                Toast.makeText(requireContext(), "준비 중입니다", Toast.LENGTH_SHORT).show();
        view.findViewById(R.id.btnShop).setOnClickListener(notReady);
        view.findViewById(R.id.tvAchievementAll).setOnClickListener(notReady);
        view.findViewById(R.id.tvTrophyManage).setOnClickListener(notReady);
        view.findViewById(R.id.tvCollectionCount).setOnClickListener(notReady);

        view.findViewById(R.id.tvMyPostsAll).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.myPostsFragment));

        // 통계 줄: 게시글 → 내가 만든 운동법 목록, 댓글 → 내가 쓴 후기·댓글 목록
        view.findViewById(R.id.boxPostCount).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.myPostsFragment));
        view.findViewById(R.id.boxCommentCount).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.myCommentsFragment));

        // 내가 만든 운동법 카드: 커뮤니티와 같은 게시글 카드를 2열로 표시
        myPostsAdapter = new PostAdapter(postId -> MyPostsHelper.openPost(this, postId));
        RecyclerView rvMyPosts = view.findViewById(R.id.rvMyPosts);
        rvMyPosts.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvMyPosts.setNestedScrollingEnabled(false);
        rvMyPosts.setAdapter(myPostsAdapter);

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

        view.findViewById(R.id.btnRetryMyPage).setOnClickListener(v -> loadProfile());
        showContributions(makeDummyContributions()); // contributions 컬렉션이 생기면 교체
    }

    @Override
    public void onResume() {
        super.onResume();
        // 프로필 꾸미기에서 저장하고 돌아왔을 때도 다시 읽도록 여기서 불러옴
        loadProfile();
    }

    private void loadProfile() {
        View view = getView();
        if (view == null) return;
        int request = ++loadRequest;
        cancelLoadTimeout();
        view.findViewById(R.id.scrollMyPageContent).setVisibility(View.GONE);
        view.findViewById(R.id.myPageLoadState).setVisibility(View.VISIBLE);
        view.findViewById(R.id.progressMyPage).setVisibility(View.VISIBLE);
        view.findViewById(R.id.btnRetryMyPage).setVisibility(View.GONE);
        ((TextView) view.findViewById(R.id.textMyPageLoadState)).setText(R.string.my_page_loading);
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            showLoadError(view, R.string.my_page_user_missing);
            return;
        }
        var profileTask = db.collection("users").document(uid).get();
        var countTask = db.collection("posts").whereEqualTo("authorId", uid)
                .count().get(AggregateSource.SERVER);
        loadTimeout = () -> {
            if (getView() != view || request != loadRequest) return;
            loadRequest++;
            showLoadError(view, R.string.my_page_load_failed);
        };
        loadHandler.postDelayed(loadTimeout, 15000);

        // 두 조회가 끝난 뒤 저장된 구성으로 한 번에 표시
        Tasks.whenAllComplete(profileTask, countTask).addOnCompleteListener(done -> {
            if (!isAdded() || getView() != view || request != loadRequest) return;
            cancelLoadTimeout();
            if (!profileTask.isSuccessful()) {
                showLoadError(view, R.string.my_page_load_failed);
                return;
            }
            DocumentSnapshot doc = profileTask.getResult();
            if (doc == null || !doc.exists()) {
                showLoadError(view, R.string.my_page_user_missing);
                return;
            }
            bindProfile(doc);
            applyModules(ProfileModule.fromFirestore(doc.get("profileModules")));

            // 집계 조회 실패를 게시글 0개로 잘못 표시하지 않음
            tvPostCount.setText(countTask.isSuccessful() ? formatCount(countTask.getResult().getCount())
                    : getString(R.string.my_page_count_unavailable));
            view.findViewById(R.id.myPageLoadState).setVisibility(View.GONE);
            view.findViewById(R.id.scrollMyPageContent).setVisibility(View.VISIBLE);
            //프로필을 먼저 보여 준 뒤 따로 불러옴(게시글 조회가 늦거나 실패해도 화면은 일단 열림)
            loadMyPosts(view, request, uid);
            loadCommentCount(view, request, uid);
        });
    }

    // 내가 만든 운동법 카드: 통계는 전체(최대 50개)로 계산하고 카드에는 최근 4개만 표시
    private void loadMyPosts(View view, int request, String uid) {
        TextView stats = view.findViewById(R.id.tvMyPostsStats);
        stats.setText("운동법 불러오는 중…");
        MyPostsHelper.query(db, uid).get().addOnCompleteListener(task -> {
            //화면이 새로 바뀌었거나 새 요청이 있으면 현재 결과는 버림
            if (!isAdded() || getView() != view || request != loadRequest) return;
            if (!task.isSuccessful()) {
                myPostsAdapter.submitPosts(Collections.emptyList());
                stats.setText("운동법을 불러오지 못했습니다.");
                return;
            }
            List<DocumentSnapshot> posts = task.getResult().getDocuments();
            myPostsAdapter.submitPosts(posts.subList(0, Math.min(MY_POSTS_PREVIEW, posts.size())));
            stats.setText(posts.isEmpty() ? "아직 작성한 운동법이 없습니다." : MyPostsHelper.summary(posts));
        });
    }

    // 후기·댓글 수. 실패하면(색인·규칙 미설정 등) 0 대신 "—" 표시
    private void loadCommentCount(View view, int request, String uid) {
        TextView count = view.findViewById(R.id.tvCommentCount);
        MyCommentsHelper.countAll(db, uid).addOnCompleteListener(task -> {
            if (!isAdded() || getView() != view || request != loadRequest) return;
            count.setText(task.isSuccessful() ? formatCount(task.getResult())
                    : getString(R.string.my_page_count_unavailable));
        });
    }

    private void showLoadError(View view, int message) {
        view.findViewById(R.id.progressMyPage).setVisibility(View.GONE);
        view.findViewById(R.id.btnRetryMyPage).setVisibility(View.VISIBLE);
        ((TextView) view.findViewById(R.id.textMyPageLoadState)).setText(message);
    }

    private void cancelLoadTimeout() {
        if (loadTimeout != null) loadHandler.removeCallbacks(loadTimeout);
        loadTimeout = null;
    }

    @Override public void onDestroyView() {
        loadRequest++;
        cancelLoadTimeout();
        ProfileImageLoader.clear(requireView().findViewById(R.id.ivProfile));
        ((RecyclerView) requireView().findViewById(R.id.rvMyPosts)).setAdapter(null);
        myPostsAdapter = null;
        moduleViews.clear();
        super.onDestroyView();
    }

    private void bindProfile(DocumentSnapshot doc) {
        ProfileImageLoader.show(requireView().findViewById(R.id.ivProfile), doc.getString("profileImagePath"));
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
