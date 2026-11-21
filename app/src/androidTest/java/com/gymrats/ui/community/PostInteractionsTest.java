package com.gymrats.ui.community;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gymrats.MainActivity;
import com.gymrats.R;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PostInteractionsTest {
    private static <T> T await(Task<T> task) throws Exception { return Tasks.await(task, 30, TimeUnit.SECONDS); }

    @Test public void savesReactionsAtomicallyOnLocalEmulator() throws Exception {
        // 명시적으로 로컬 테스트를 요청한 경우에만 실행하며 운영 Firebase에는 쓰지 않습니다.
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("localFirebase")));
        FirebaseApp app = FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                new FirebaseOptions.Builder().setProjectId("demo-gymrats").setApplicationId("1:123:android:test")
                        .setApiKey("fake-api-key").build(), "interaction-test-" + UUID.randomUUID());
        FirebaseAuth auth = FirebaseAuth.getInstance(app);
        String host = InstrumentationRegistry.getArguments().getString("firebaseHost", "10.0.2.2");
        auth.useEmulator(host, 9099);
        FirebaseFirestore db = FirebaseFirestore.getInstance(app);
        db.useEmulator(host, 8080);
        try {
            String uid = await(auth.signInAnonymously()).getUser().getUid();
            PostInteractions repo = new PostInteractions(db, uid);
            String post = "android-test";
            long before = PostInteractions.count(await(repo.post(post).get()), "likeCount");
            await(repo.setLiked(post, true));
            await(repo.setLiked(post, true));
            assertEquals(before + 1, PostInteractions.count(await(repo.post(post).get()), "likeCount"));
            await(repo.setLiked(post, false));
            assertEquals(before, PostInteractions.count(await(repo.post(post).get()), "likeCount"));
            String review = UUID.randomUUID().toString(), comment = UUID.randomUUID().toString();
            long reviews = PostInteractions.count(await(repo.post(post).get()), "reviewCount");
            await(repo.addEntry(post, "reviews", review, "동작이 쉬워서 자극을 느끼기 좋았습니다.", 4));
            await(repo.addEntry(post, "reviews", review, "동작이 쉬워서 자극을 느끼기 좋았습니다.", 4));
            assertEquals(reviews + 1, PostInteractions.count(await(repo.post(post).get()), "reviewCount"));
            await(Tasks.whenAll(
                    repo.addEntry(post, "reviews", UUID.randomUUID().toString(), "동시 등록 첫 번째 후기입니다.", 3),
                    repo.addEntry(post, "reviews", UUID.randomUUID().toString(), "동시 등록 두 번째 후기입니다.", 5)));
            assertEquals(reviews + 3, PostInteractions.count(await(repo.post(post).get()), "reviewCount"));
            await(repo.addEntry(post, "comments", comment, "초보자는 몇 회씩 하면 좋을까요?", 0));
            for (String kind : new String[]{"reviews", "comments"}) {
                String id = kind.equals("reviews") ? review : comment;
                String reply = UUID.randomUUID().toString();
                await(repo.addReply(repo.post(post).collection(kind).document(id), reply, "자세를 먼저 익혀 보세요."));
                await(repo.addReply(repo.post(post).collection(kind).document(id), reply, "자세를 먼저 익혀 보세요."));
                assertEquals(1, PostInteractions.count(await(repo.post(post).collection(kind).document(id).get()), "replyCount"));
            }
            await(repo.setBookmarked(post, true));
            Object savedAt = await(repo.bookmark(post).get()).get("savedAt");
            await(repo.setBookmarked(post, true));
            assertEquals(savedAt, await(repo.bookmark(post).get()).get("savedAt"));
            assertEquals(0, ((java.util.List<?>) await(repo.bookmark(post).get()).get("collectionIds")).size());
            await(repo.setBookmarked(post, false));
            assertFalse(await(repo.bookmark(post).get()).exists());
            exerciseDiscussion(repo);
        } finally {
            await(db.terminate());
            app.delete();
        }
    }

    public static class DiscussionPreview extends Fragment {
        static PostInteractions repository;
        private PostDiscussion discussion;
        public DiscussionPreview() { super(R.layout.fragment_post_detail); }
        @Override public void onViewCreated(View view, Bundle state) {
            view.findViewById(R.id.scrollPostDetail).setVisibility(View.VISIBLE);
            view.findViewById(R.id.progressPostDetail).setVisibility(View.GONE);
            view.findViewById(R.id.textVideoPlaybackStatus).setVisibility(View.GONE);
            ((TextView) view.findViewById(R.id.textDetailTitle)).setText("티바로우가 없다면?");
            ((TextView) view.findViewById(R.id.textDetailDescription)).setText("등에 자극을 집중하면서 천천히 당겨 주세요.");
            discussion = new PostDiscussion(this, view, "android-test", repository);
        }
        @Override public void onStart() { super.onStart(); discussion.start(); }
        @Override public void onStop() { discussion.stop(); super.onStop(); }
    }

    private void exerciseDiscussion(PostInteractions repo) throws Exception {
        assumeTrue(FirebaseAuth.getInstance().getCurrentUser() != null);
        DiscussionPreview.repository = repo;
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        long before = PostInteractions.count(await(repo.post("android-test").get()), "commentCount");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, new DiscussionPreview()).commitNow();
                activity.findViewById(R.id.bottomNavigation).setVisibility(View.GONE);
                activity.findViewById(R.id.btnCommentsTab).performClick();
                LinearLayout composer = activity.findViewById(R.id.commentComposer);
                ((EditText) composer.getChildAt(0)).setText("화면에서 등록하는 자유 댓글 테스트입니다.");
                composer.getChildAt(1).performClick();
            });
            boolean saved = false;
            for (int i = 0; i < 40; i++) {
                if (PostInteractions.count(await(repo.post("android-test").get()), "commentCount") == before + 1) { saved = true; break; }
                Thread.sleep(100);
            }
            assertTrue("화면에서 작성한 댓글이 저장되어야 합니다.", saved);
            instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.commentComposer).getVisibility());
                activity.findViewById(R.id.btnReviewsTab).performClick();
                assertEquals(View.GONE, activity.findViewById(R.id.commentComposer).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.btnWriteReview).getVisibility());
                ((ScrollView) activity.findViewById(R.id.scrollPostDetail)).smoothScrollTo(0, 350);
            });
            instrumentation.waitForIdleSync();
            Thread.sleep(400);
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            if (screenshot != null) {
                try (FileOutputStream output = new FileOutputStream(new File(instrumentation.getTargetContext().getCacheDir(), "discussion-preview.png"))) {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
                }
                screenshot.recycle();
            }
        } finally { DiscussionPreview.repository = null; }
    }

    @Test public void reviewDraftSurvivesRotationAndRequiresRating() throws Exception {
        // 기존 로그인 세션을 그대로 사용하고 등록 버튼은 누르지 않습니다.
        assumeTrue(FirebaseAuth.getInstance().getCurrentUser() != null);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                NavHostFragment host = (NavHostFragment) activity.getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
                Bundle args = new Bundle();
                args.putString("postId", "preview-only");
                args.putString("postTitle", "티바로우가 없다면?");
                host.getNavController().navigate(R.id.communityGraph);
                host.getNavController().navigate(R.id.reviewCreateFragment, args);
            });
            instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.btnSubmitReview).isEnabled());
                ((EditText) activity.findViewById(R.id.editReviewContent)).setText("등에 자극이 잘 오고 동작을 따라 하기 쉬웠습니다.");
                assertFalse(activity.findViewById(R.id.btnSubmitReview).isEnabled());
                ((RatingBar) activity.findViewById(R.id.reviewStars)).setRating(4);
                assertTrue(activity.findViewById(R.id.btnSubmitReview).isEnabled());
                assertEquals(View.GONE, activity.findViewById(R.id.bottomNavigation).getVisibility());
            });
            scenario.recreate();
            instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals(4f, ((RatingBar) activity.findViewById(R.id.reviewStars)).getRating(), 0f);
                assertEquals("등에 자극이 잘 오고 동작을 따라 하기 쉬웠습니다.", ((EditText) activity.findViewById(R.id.editReviewContent)).getText().toString());
                assertTrue(activity.findViewById(R.id.btnSubmitReview).isEnabled());
            });
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            if (screenshot != null) {
                try (FileOutputStream output = new FileOutputStream(new File(instrumentation.getTargetContext().getCacheDir(), "review-preview.png"))) {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
                }
                screenshot.recycle();
            }
        }
    }
}
