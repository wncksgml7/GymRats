package com.gymrats.ui.community;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
import com.gymrats.model.ProfileModule;
import com.gymrats.ui.profile.PublicProfileSettings;
import org.junit.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FollowTest {
    public static class Visitor extends AuthorProfileFragment {
        static FirebaseFirestore database;
        static String viewer;
        @Override protected FirebaseFirestore firestore() { return database; }
        @Override protected String viewerUid() { return viewer; }
    }
    private static <T> T await(Task<T> task) throws Exception { return Tasks.await(task, 30, TimeUnit.SECONDS); }
    private FirebaseApp app() {
        return FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                new FirebaseOptions.Builder().setProjectId("demo-gymrats").setApplicationId("1:123:android:test")
                        .setApiKey("fake-api-key").build(), "follow-test-" + UUID.randomUUID());
    }
    private String seed(FirebaseApp app) throws Exception {
        String host = InstrumentationRegistry.getArguments().getString("firebaseHost", "10.0.2.2");
        FirebaseAuth auth = FirebaseAuth.getInstance(app);
        auth.useEmulator(host, 9099);
        FirebaseFirestore db = FirebaseFirestore.getInstance(app);
        db.useEmulator(host, 8080);
        String uid = await(auth.signInAnonymously()).getUser().getUid();
        Map<String, Object> user = new HashMap<>();
        user.put("nickname", ""); user.put("bio", ""); user.put("profileImagePath", null);
        user.put("points", 0L); user.put("followerCount", 0L); user.put("followingCount", 0L); user.put("postCount", 0L);
        await(db.collection("users").document(uid).set(user));
        List<ProfileModule> modules = ProfileModule.defaults();
        for (ProfileModule module : modules) module.setVisible(false);
        await(PublicProfileSettings.save(db, uid, modules));
        return uid;
    }
    @Test public void followButtonTogglesAndBothProfilesStayConsistent() throws Exception {
        // 실제 Firebase와 기존 로그인에는 쓰지 않고 로컬 계정 두 개로 검증합니다.
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("localFirebase")));
        assumeTrue(FirebaseAuth.getInstance().getCurrentUser() != null);
        FirebaseApp a = app(), b = app();
        FirebaseFirestore db = FirebaseFirestore.getInstance(a), otherDb = FirebaseFirestore.getInstance(b);
        try {
            String uid = seed(a), target = seed(b);
            FollowRepository repo = new FollowRepository(db, uid);
            Visitor.database = db; Visitor.viewer = uid;
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> {
                    Visitor visitor = new Visitor();
                    Bundle args = new Bundle(); args.putString("userId", target); visitor.setArguments(args);
                    activity.getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, visitor).commitNow();
                });
                waitButton(scenario, "팔로우");
                scenario.onActivity(activity -> activity.findViewById(R.id.btnFollowAuthor).performClick());
                waitButton(scenario, "팔로우 중");
                await(repo.setFollowing(target, true));
                assertEquals(1L, await(db.collection("users").document(uid).get()).getLong("followingCount").longValue());
                assertEquals(1L, await(otherDb.collection("users").document(target).get()).getLong("followerCount").longValue());
                assertEquals(1L, await(db.collection("publicProfiles").document(uid).get()).getLong("followingCount").longValue());
                assertEquals(1L, await(db.collection("publicProfiles").document(target).get()).getLong("followerCount").longValue());
                assertTrue(await(otherDb.collection("users").document(target).collection("followers").document(uid).get()).exists());
                scenario.onActivity(activity -> activity.findViewById(R.id.btnFollowAuthor).performClick());
                waitButton(scenario, "팔로우");
                assertFalse(await(repo.following(target).get()).exists());
                assertEquals(0L, await(otherDb.collection("users").document(target).get()).getLong("followerCount").longValue());
                scenario.onActivity(activity -> {
                    Visitor self = new Visitor(); Bundle args = new Bundle(); args.putString("userId", uid); self.setArguments(args);
                    activity.getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, self).commitNow();
                    assertEquals(View.GONE, activity.findViewById(R.id.btnFollowAuthor).getVisibility());
                    assertNotNull(((android.widget.ImageView) activity.findViewById(R.id.imageAuthorProfile)).getDrawable());
                });
            }
        } finally {
            Visitor.database = null; Visitor.viewer = null;
            await(db.terminate()); await(otherDb.terminate()); a.delete(); b.delete();
        }
    }
    private void waitButton(ActivityScenario<MainActivity> scenario, String text) throws Exception {
        AtomicBoolean ready = new AtomicBoolean();
        for (int i = 0; i < 100; i++) {
            scenario.onActivity(activity -> {
                Button button = activity.findViewById(R.id.btnFollowAuthor);
                ready.set(button.isEnabled() && text.contentEquals(button.getText()));
            });
            if (ready.get()) return;
            Thread.sleep(100);
        }
        fail("팔로우 버튼 상태가 반영되지 않았습니다: " + text);
    }
}
