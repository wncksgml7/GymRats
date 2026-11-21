package com.gymrats.ui.community;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.gymrats.R;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 상세 화면의 후기·댓글과 각각의 답글을 관리합니다. */
final class PostDiscussion {
    private final Fragment fragment;
    private final View root;
    private final String postId;
    private final PostInteractions repository;
    private final DiscussionState state;
    private final List<ListenerRegistration> listeners = new ArrayList<>();
    private final List<ListenerRegistration> replyListeners = new ArrayList<>();
    private final Map<String, String> names = new HashMap<>();
    private final LinearLayout list;
    private final Button reviews, comments, bookmark;
    private ListenerRegistration entries;
    private boolean alive;
    private boolean liked, bookmarked, likeBusy, bookmarkBusy;
    private int limit = 20;
    private long reviewCount, commentCount;
    private String title = "운동법";

    PostDiscussion(Fragment fragment, View root, String postId) {
        this(fragment, root, postId, new PostInteractions());
    }

    PostDiscussion(Fragment fragment, View root, String postId, PostInteractions repository) {
        this.fragment = fragment;
        this.root = root;
        this.postId = postId;
        this.repository = repository;
        state = new ViewModelProvider(fragment).get(DiscussionState.class);
        list = root.findViewById(R.id.discussionList);
        reviews = root.findViewById(R.id.btnReviewsTab);
        comments = root.findViewById(R.id.btnCommentsTab);
        bookmark = root.findViewById(R.id.btnBookmark);
        reviews.setOnClickListener(v -> select("reviews"));
        comments.setOnClickListener(v -> select("comments"));
        root.findViewById(R.id.btnWriteReview).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("postId", postId);
            args.putString("postTitle", title);
            NavHostFragment.findNavController(fragment).navigate(R.id.reviewCreateFragment, args);
        });
        root.findViewById(R.id.textDetailComments).setOnClickListener(v -> select("comments"));
        root.findViewById(R.id.textDetailLikes).setOnClickListener(v -> {
            if (likeBusy || !repository.signedIn()) return;
            likeBusy = true;
            v.setEnabled(false);
            repository.setLiked(postId, !liked).addOnCompleteListener(task -> {
                likeBusy = false;
                if (!alive) return;
                v.setEnabled(true);
                if (!task.isSuccessful()) failure("좋아요를 저장하지 못했습니다.");
            });
        });
        bookmark.setOnClickListener(v -> {
            if (bookmarkBusy || !repository.signedIn()) return;
            bookmarkBusy = true;
            bookmark.setEnabled(false);
            repository.setBookmarked(postId, !bookmarked).addOnCompleteListener(task -> {
                bookmarkBusy = false;
                if (!alive) return;
                bookmark.setEnabled(true);
                if (!task.isSuccessful()) failure("북마크를 저장하지 못했습니다.");
            });
        });
        LinearLayout composer = root.findViewById(R.id.commentComposer);
        composer(composer, "comment", null);
        state.completed.observe(fragment.getViewLifecycleOwner(), version -> {
            if (!alive) return;
            composer.removeAllViews();
            composer(composer, "comment", null);
            listenEntries();
        });
        root.findViewById(R.id.btnDiscussionRetry).setOnClickListener(v -> { stop(); start(); });
    }

    void start() {
        if (alive) return;
        alive = true;
        LinearLayout composer = root.findViewById(R.id.commentComposer);
        composer.removeAllViews();
        composer(composer, "comment", null);
        root.findViewById(R.id.textDetailLikes).setEnabled(false);
        bookmark.setEnabled(false);
        ((TextView) root.findViewById(R.id.textDiscussionError)).setText("");
        root.findViewById(R.id.btnDiscussionRetry).setVisibility(View.GONE);
        listeners.add(repository.post(postId).addSnapshotListener((post, error) -> {
            if (!alive) return;
            if (error != null) { failure("반응 정보를 불러오지 못했습니다."); return; }
            if (post == null || !post.exists()) return;
            title = post.getString("title");
            reviewCount = PostInteractions.count(post, "reviewCount");
            commentCount = PostInteractions.count(post, "commentCount");
            ((TextView) root.findViewById(R.id.textDetailLikes)).setText(String.valueOf(PostInteractions.count(post, "likeCount")));
            ((TextView) root.findViewById(R.id.textDetailComments)).setText(String.valueOf(commentCount));
            Number average = (Number) post.get("ratingAverage");
            ((TextView) root.findViewById(R.id.textDetailRating)).setText(String.format(Locale.KOREA, "%.1f (%d)", average == null ? 0.0 : average.doubleValue(), reviewCount));
            tabs();
        }));
        if (repository.signedIn()) {
            listeners.add(repository.like(postId).addSnapshotListener((doc, error) -> {
                if (!alive) return;
                if (error != null) { failure("좋아요 상태를 불러오지 못했습니다."); return; }
                liked = doc != null && doc.exists();
                TextView heart = root.findViewById(R.id.textDetailLikes);
                androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(heart,
                        ColorStateList.valueOf(Color.parseColor(liked ? "#E45178" : "#88818F")));
                heart.setContentDescription(liked ? "좋아요 취소" : "좋아요 누르기");
                heart.setEnabled(!likeBusy);
                heart.setSelected(liked);
            }));
            listeners.add(repository.bookmark(postId).addSnapshotListener((doc, error) -> {
                if (!alive) return;
                if (error != null) { failure("북마크 상태를 불러오지 못했습니다."); return; }
                bookmarked = doc != null && doc.exists();
                bookmark.setText(bookmarked ? "북마크 저장됨 · 취소" : "북마크 담기");
                bookmark.setSelected(bookmarked);
                bookmark.setEnabled(!bookmarkBusy);
            }));
        }
        select(state.kind());
    }

    void stop() {
        alive = false;
        for (ListenerRegistration listener : listeners) listener.remove();
        listeners.clear();
        clearEntries();
    }

    private void clearEntries() {
        if (entries != null) entries.remove();
        entries = null;
        for (ListenerRegistration listener : replyListeners) listener.remove();
        replyListeners.clear();
    }

    private void tabs() {
        boolean review = "reviews".equals(state.kind());
        reviews.setText("운동 후기 (" + reviewCount + ")");
        comments.setText("질문·댓글 (" + commentCount + ")");
        reviews.setSelected(review);
        comments.setSelected(!review);
        reviews.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(review ? "#6750A4" : "#EAE6F0")));
        comments.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(review ? "#EAE6F0" : "#6750A4")));
        reviews.setTextColor(Color.parseColor(review ? "#FFFFFF" : "#49424F"));
        comments.setTextColor(Color.parseColor(review ? "#49424F" : "#FFFFFF"));
        root.findViewById(R.id.btnWriteReview).setVisibility(review ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.commentComposer).setVisibility(review ? View.GONE : View.VISIBLE);
    }

    private void select(String kind) {
        if (!kind.equals(state.kind())) limit = 20;
        state.kind(kind);
        tabs();
        listenEntries();
    }

    private void listenEntries() {
        clearEntries();
        if (!alive) return;
        list.removeAllViews();
        list.addView(text("불러오는 중…", false));
        entries = repository.post(postId).collection(state.kind()).orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit + 1L).addSnapshotListener((snapshot, error) -> {
                    if (!alive) return;
                    if (error != null) {
                        list.removeAllViews();
                        list.addView(text("목록을 불러오지 못했습니다.", false));
                        failure("네트워크와 Firestore 접근 권한을 확인해 주세요.");
                        return;
                    }
                    for (ListenerRegistration listener : replyListeners) listener.remove();
                    replyListeners.clear();
                    list.removeAllViews();
                    if (snapshot == null || snapshot.isEmpty()) {
                        list.addView(text("reviews".equals(state.kind()) ? "첫 운동 후기를 남겨 보세요." : "궁금한 점이나 자유로운 이야기를 남겨 보세요.", false));
                        return;
                    }
                    List<DocumentSnapshot> docs = snapshot.getDocuments();
                    for (int i = 0; i < Math.min(limit, docs.size()); i++) entry(docs.get(i));
                    if (docs.size() > limit) {
                        Button more = button("더 보기");
                        more.setOnClickListener(v -> { limit += 20; listenEntries(); });
                        list.addView(more);
                    }
                });
    }

    private void entry(DocumentSnapshot doc) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#F6F4F9"));
        background.setCornerRadius(dp(14));
        card.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        list.addView(card, params);
        author(card, doc);
        if (doc.contains("rating")) {
            int rating = (int) PostInteractions.count(doc, "rating");
            TextView stars = text("★★★★★".substring(0, Math.max(0, Math.min(5, rating))) + "  " + rating + "/5", true);
            stars.setTextColor(Color.parseColor("#A47800"));
            card.addView(stars);
        }
        card.addView(text(doc.getString("content"), false));
        String key = doc.getReference().getPath();
        Button toggle = button("답글 " + PostInteractions.count(doc, "replyCount") + "개 · 답글 달기");
        card.addView(toggle);
        LinearLayout replies = column();
        card.addView(replies);
        Runnable open = () -> {
            replies.removeAllViews();
            LinearLayout rows = column();
            replies.addView(rows);
            replyPage(doc.getReference(), rows, 20);
            composer(replies, key, doc.getReference());
        };
        if (state.expanded.contains(key)) open.run();
        else replies.setVisibility(View.GONE);
        toggle.setOnClickListener(v -> {
            if (state.expanded.remove(key)) replies.setVisibility(View.GONE);
            else {
                state.expanded.add(key);
                replies.setVisibility(View.VISIBLE);
                if (replies.getChildCount() == 0) open.run();
            }
        });
    }

    private void replyPage(DocumentReference parent, LinearLayout rows, int size) {
        final ListenerRegistration[] registration = new ListenerRegistration[1];
        registration[0] = parent.collection("replies").orderBy("createdAt").limit(size + 1L)
                .addSnapshotListener((snapshot, error) -> {
                    if (!alive) return;
                    rows.removeAllViews();
                    if (error != null) {
                        rows.addView(text("답글을 불러오지 못했습니다.", false));
                        Button retry = button("답글 다시 불러오기");
                        retry.setOnClickListener(v -> {
                            registration[0].remove();
                            replyListeners.remove(registration[0]);
                            replyPage(parent, rows, size);
                        });
                        rows.addView(retry);
                        return;
                    }
                    if (snapshot == null) return;
                    List<DocumentSnapshot> docs = snapshot.getDocuments();
                    for (int i = 0; i < Math.min(size, docs.size()); i++) {
                        LinearLayout reply = column();
                        reply.setPadding(dp(16), dp(8), 0, dp(8));
                        author(reply, docs.get(i));
                        reply.addView(text(docs.get(i).getString("content"), false));
                        rows.addView(reply);
                    }
                    if (docs.size() > size) {
                        Button more = button("답글 더 보기");
                        more.setOnClickListener(v -> {
                            registration[0].remove();
                            replyListeners.remove(registration[0]);
                            replyPage(parent, rows, size + 20);
                        });
                        rows.addView(more);
                    }
                });
        replyListeners.add(registration[0]);
    }

    private void composer(LinearLayout container, String key, DocumentReference parent) {
        EditText input = new EditText(fragment.requireContext());
        input.setHint(parent == null ? "질문이나 댓글을 남겨 주세요" : "답글을 남겨 주세요");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        input.setMaxLines(5);
        input.setText(state.draft(key));
        input.setEnabled(!state.sending.contains(key));
        input.setSaveEnabled(false);
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { state.draft(key, s.toString()); }
            public void afterTextChanged(Editable s) { }
        });
        container.addView(input, new LinearLayout.LayoutParams(-1, -2));
        Button send = button(parent == null ? "댓글 등록" : "답글 등록");
        send.setEnabled(!state.sending.contains(key));
        container.addView(send);
        send.setOnClickListener(v -> {
            String body = input.getText().toString().trim();
            if (body.isEmpty()) { input.setError("내용을 입력해 주세요."); return; }
            if (!state.sending.add(key)) return;
            send.setEnabled(false);
            input.setEnabled(false);
            String requestId = state.draft(key + ":request");
            if (requestId.isEmpty()) { requestId = UUID.randomUUID().toString(); state.draft(key + ":request", requestId); }
            Task<Void> task = parent == null ? repository.addEntry(postId, "comments", requestId, body, 0)
                    : repository.addReply(parent, requestId, body);
            task.addOnCompleteListener(result -> {
                state.sending.remove(key);
                if (result.isSuccessful()) { state.draft(key, ""); state.draft(key + ":request", ""); }
                state.completed();
                if (!alive) return;
                send.setEnabled(true);
                input.setEnabled(true);
                if (result.isSuccessful()) {
                    input.setText("");
                } else failure("등록하지 못했습니다. 입력한 내용은 유지됩니다. 다시 시도해 주세요.");
            });
        });
    }

    private void author(LinearLayout container, DocumentSnapshot doc) {
        String uid = doc.getString("authorId");
        String time = doc.getTimestamp("createdAt") == null ? "전송 중" : new SimpleDateFormat("MM.dd HH:mm", Locale.KOREA).format(doc.getTimestamp("createdAt").toDate());
        TextView name = text(names.getOrDefault(uid, "회원") + " · " + time, true);
        container.addView(name);
        if (uid == null || names.containsKey(uid)) return;
        repository.profile(uid).get().addOnSuccessListener(profile -> {
            if (!alive) return;
            String nickname = profile.getString("nickname");
            String label = nickname == null || nickname.trim().isEmpty() ? "닉네임 미설정" : nickname;
            names.put(uid, label);
            name.setText(label + " · " + time);
        });
    }

    private void failure(String message) {
        ((TextView) root.findViewById(R.id.textDiscussionError)).setText(message);
        root.findViewById(R.id.btnDiscussionRetry).setVisibility(View.VISIBLE);
        Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show();
    }
    private int dp(int value) { return Math.round(value * root.getResources().getDisplayMetrics().density); }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(fragment.requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return layout;
    }
    private TextView text(String value, boolean bold) {
        TextView view = new TextView(fragment.requireContext());
        view.setText(value);
        view.setTextSize(15);
        view.setTextColor(Color.parseColor("#292331"));
        view.setPadding(0, dp(5), 0, dp(5));
        if (bold) view.setTypeface(null, Typeface.BOLD);
        else view.setTextIsSelectable(true);
        return view;
    }
    private Button button(String title) {
        Button button = new Button(fragment.requireContext());
        button.setText(title);
        button.setMinHeight(dp(48));
        return button;
    }
}
