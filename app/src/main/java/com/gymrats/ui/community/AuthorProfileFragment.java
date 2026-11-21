package com.gymrats.ui.community;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import com.google.firebase.auth.FirebaseAuth;
import com.gymrats.ui.profile.ProfileImageLoader;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.gymrats.R;
import com.gymrats.model.ProfileModule;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AuthorProfileFragment extends Fragment {
    private final Map<String, View> cards = new HashMap<>();
    private ListenerRegistration profileListener, postsListener;
    private String userId;
    private PostAdapter adapter;
    private TextView postsMessage;
    private int generation;
    private ListenerRegistration followListener;
    private FollowRepository follows;
    private boolean following, followReady, profileReady, followBusy;

    public AuthorProfileFragment() { super(R.layout.fragment_author_profile); }

    protected FirebaseFirestore firestore() { return FirebaseFirestore.getInstance(); }
    protected String viewerUid() { return FirebaseAuth.getInstance().getUid(); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        view.findViewById(R.id.btnBack).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        userId = getArguments() == null ? null : getArguments().getString("userId");
        follows = new FollowRepository(firestore(), viewerUid());
        view.findViewById(R.id.btnFollowAuthor).setOnClickListener(v -> changeFollow(view));
        cards.put(ProfileModule.CONTRIBUTION, view.findViewById(R.id.cardContribution));
        cards.put(ProfileModule.ACHIEVEMENTS, view.findViewById(R.id.cardAchievements));
        cards.put(ProfileModule.TROPHIES, view.findViewById(R.id.cardTrophies));
        cards.put(ProfileModule.MY_POSTS, view.findViewById(R.id.cardMyPosts));
        cards.put(ProfileModule.WORLDCUP, view.findViewById(R.id.cardWorldcup));
        cards.put(ProfileModule.COLLECTIONS, view.findViewById(R.id.cardCollections));
        view.findViewById(R.id.moduleContainer).setVisibility(View.GONE);

        // 방문자에게 관리 버튼이나 샘플 통계를 보여주지 않습니다.
        for (int id : new int[]{R.id.tvAchievementAll, R.id.tvTrophyManage, R.id.tvMyPostsAll,
                R.id.tvCollectionCount, R.id.tvStreak, R.id.gridContribution}) {
            view.findViewById(id).setVisibility(View.GONE);
        }
        ((TextView) view.findViewById(R.id.tvContributionTotal)).setText("기여 기록 공개를 준비 중입니다.");
        ((TextView) view.findViewById(R.id.tvWorldcupSummary)).setText("월드컵 기록 공개를 준비 중입니다.");
        addMessage(R.id.cardAchievements, "도전과제 전시를 준비 중입니다.");
        addMessage(R.id.cardTrophies, "트로피 전시를 준비 중입니다.");
        addMessage(R.id.cardCollections, "컬렉션 공개를 준비 중입니다.");
        postsMessage = addMessage(R.id.cardMyPosts, "운동법 불러오는 중");
        ((TextView) view.findViewById(R.id.tvMyPostsStats)).setText("공개 운동법 · 최신순 최대 50개");

        adapter = new PostAdapter(postId -> {
            Bundle args = new Bundle();
            args.putString(PostDetailFragment.ARG_POST_ID, postId);
            NavHostFragment.findNavController(this).navigate(R.id.postDetailFragment, args);
        });
        RecyclerView recycler = view.findViewById(R.id.rvMyPosts);
        recycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recycler.setNestedScrollingEnabled(false);
        recycler.setAdapter(adapter);
        view.findViewById(R.id.btnRetryAuthorProfile).setOnClickListener(v -> listenProfile());
    }

    @Override public void onStart() {
        super.onStart();
        listenProfile();
    }

    private void listenProfile() {
        stopListening();
        View view = getView();
        if (view == null) return;
        if (userId == null || userId.isEmpty()) {
            ((TextView) view.findViewById(R.id.textAuthorNickname)).setText("회원 정보 없음");
            message(view, "회원 정보를 확인할 수 없습니다.", false);
            return;
        }
        int request = generation;
        profileReady = false;
        followReady = false;
        renderFollow(view);
        followMessage(view, "");
        if (viewerUid() != null && !viewerUid().equals(userId)) {
            followListener = follows.following(userId).addSnapshotListener((relationship, error) -> {
                if (generation != request || getView() != view) return;
                followReady = error == null;
                if (error == null) following = relationship != null && relationship.exists();
                else {
                    followMessage(view, "팔로우 상태를 불러오지 못했습니다. 다시 불러오기를 눌러 주세요.");
                    view.findViewById(R.id.btnRetryAuthorProfile).setVisibility(View.VISIBLE);
                }
                renderFollow(view);
            });
        }
        message(view, "프로필 불러오는 중…", false);
        profileListener = firestore().collection("publicProfiles").document(userId)
                .addSnapshotListener((profile, error) -> {
                    if (generation != request || getView() != view) return;
                    if (error != null || profile == null || !profile.exists()) {
                        profileReady = false;
                        renderFollow(view);
                        ProfileImageLoader.show(view.findViewById(R.id.imageAuthorProfile), null);
                        view.findViewById(R.id.moduleContainer).setVisibility(View.GONE);
                        stopPosts();
                        message(view, error == null ? "공개 프로필이 아직 없습니다." : "프로필을 불러오지 못했습니다.", true);
                        return;
                    }
                    String nickname = profile.getString("nickname");
                    profileReady = true;
                    renderFollow(view);
                    ProfileImageLoader.show(view.findViewById(R.id.imageAuthorProfile), profile.getString("profileImagePath"));
                    ((TextView) view.findViewById(R.id.textAuthorNickname)).setText(
                            nickname == null || nickname.trim().isEmpty() ? "닉네임 미설정" : nickname);
                    ((TextView) view.findViewById(R.id.textAuthorFollowCounts)).setText(String.format(
                            Locale.KOREA, "팔로워 %d · 팔로잉 %d",
                            PostInteractions.count(profile, "followerCount"),
                            PostInteractions.count(profile, "followingCount")));
                    List<ProfileModule> modules = ProfileModule.fromFirestore(profile.get("profileModules"));
                    applyModules(view, modules);
                    view.findViewById(R.id.moduleContainer).setVisibility(View.VISIBLE);
                    boolean visible = false;
                    for (ProfileModule module : modules) visible |= module.isVisible();
                    message(view, visible ? "" : "표시할 프로필 항목이 없습니다.", false);
                    if (cards.get(ProfileModule.MY_POSTS).getVisibility() == View.VISIBLE) {
                        if (postsListener == null) listenPosts(view, request);
                    } else stopPosts();
                });
    }

    private void applyModules(View view, List<ProfileModule> modules) {
        LinearLayout container = view.findViewById(R.id.moduleContainer);
        container.removeAllViews();
        for (ProfileModule module : modules) {
            View card = cards.get(module.getType());
            if (card == null) continue;
            card.setVisibility(module.isVisible() ? View.VISIBLE : View.GONE);
            container.addView(card);
        }
    }

    private void renderFollow(View view) {
        Button button = view.findViewById(R.id.btnFollowAuthor);
        boolean own = viewerUid() != null && viewerUid().equals(userId);
        button.setVisibility(own ? View.GONE : View.VISIBLE);
        button.setEnabled(viewerUid() != null && profileReady && followReady && !followBusy && !own);
        button.setText(followBusy ? "처리 중…" : following ? "팔로우 중" : "팔로우");
        button.setSelected(following);
        button.setContentDescription(following ? "팔로우 중, 누르면 팔로우 취소" : "팔로우하기");
    }

    private void changeFollow(View view) {
        if (followBusy || !profileReady || !followReady) return;
        followBusy = true;
        followMessage(view, "");
        renderFollow(view);
        follows.setFollowing(userId, !following).addOnCompleteListener(task -> {
            followBusy = false;
            if (getView() != view) return;
            renderFollow(view);
            if (!task.isSuccessful()) followMessage(view,
                    "팔로우를 변경하지 못했습니다. 네트워크와 접근 권한을 확인하고 다시 시도해 주세요.");
        });
    }

    private void followMessage(View view, String text) {
        TextView message = view.findViewById(R.id.textFollowMessage);
        message.setText(text);
        message.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void listenPosts(View view, int request) {
        postsMessage.setText("운동법 불러오는 중");
        postsMessage.setVisibility(View.VISIBLE);
        postsListener = firestore().collection("posts")
                .whereEqualTo("authorId", userId).whereEqualTo("status", "published")
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
                .addSnapshotListener((snapshot, error) -> {
                    if (generation != request || getView() != view) return;
                    if (error != null || snapshot == null) {
                        postsMessage.setText("운동법을 불러오지 못했습니다.");
                        postsMessage.setVisibility(View.VISIBLE);
                        message(view, "운동법 목록을 다시 불러와 주세요.", true);
                        return;
                    }
                    adapter.submitPosts(snapshot.getDocuments());
                    postsMessage.setText("공개된 운동법이 없습니다.");
                    postsMessage.setVisibility(snapshot.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private TextView addMessage(int cardId, String text) {
        MaterialCardView card = requireView().findViewById(cardId);
        LinearLayout content = (LinearLayout) card.getChildAt(0);
        TextView message = new TextView(requireContext());
        message.setText(text);
        message.setTextColor(android.graphics.Color.parseColor("#777777"));
        message.setTextSize(13);
        message.setPadding(0, Math.round(12 * getResources().getDisplayMetrics().density), 0, 0);
        content.addView(message);
        return message;
    }

    private void message(View view, String text, boolean retry) {
        TextView label = view.findViewById(R.id.textAuthorProfileMessage);
        label.setText(text);
        label.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.btnRetryAuthorProfile).setVisibility(
                retry || view.findViewById(R.id.textFollowMessage).getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
    }

    private void stopPosts() {
        if (postsListener != null) postsListener.remove();
        postsListener = null;
    }

    private void stopListening() {
        generation++;
        if (followListener != null) followListener.remove();
        followListener = null;
        if (profileListener != null) profileListener.remove();
        profileListener = null;
        stopPosts();
    }

    @Override public void onStop() {
        stopListening();
        super.onStop();
    }

    @Override public void onDestroyView() {
        stopListening();
        ProfileImageLoader.clear(requireView().findViewById(R.id.imageAuthorProfile));
        RecyclerView recycler = requireView().findViewById(R.id.rvMyPosts);
        recycler.setAdapter(null);
        cards.clear();
        adapter = null;
        postsMessage = null;
        super.onDestroyView();
    }
}
