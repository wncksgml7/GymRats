package com.gymrats.ui.community;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PublicProfileTest {
    public static class Visitor extends AuthorProfileFragment {
        static FirebaseFirestore database;
        @Override protected FirebaseFirestore firestore() { return database; }
    }

    private static <T> T await(Task<T> task) throws Exception {
        return Tasks.await(task, 30, TimeUnit.SECONDS);
    }

    @Test public void savesBothProfilesAndVisitorFollowsOrderAndVisibility() throws Exception {
        // demo 프로젝트와 로컬 에뮬레이터만 사용합니다. 실제 계정 문서에는 쓰지 않습니다.
        assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("localFirebase")));
        assumeTrue(FirebaseAuth.getInstance().getCurrentUser() != null);
        FirebaseApp app = FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                new FirebaseOptions.Builder().setProjectId("demo-gymrats").setApplicationId("1:123:android:test")
                        .setApiKey("fake-api-key").build(), "profile-test-" + UUID.randomUUID());
        String host = InstrumentationRegistry.getArguments().getString("firebaseHost", "10.0.2.2");
        FirebaseAuth auth = FirebaseAuth.getInstance(app);
        auth.useEmulator(host, 9099);
        FirebaseFirestore db = FirebaseFirestore.getInstance(app);
        db.useEmulator(host, 8080);
        try {
            String uid = await(auth.signInAnonymously()).getUser().getUid();
            Map<String, Object> user = new HashMap<>();
            user.put("nickname", ""); user.put("bio", ""); user.put("profileImagePath", null);
            user.put("points", 0L); user.put("followerCount", 0L);
            user.put("followingCount", 0L); user.put("postCount", 0L);
            await(db.collection("users").document(uid).set(user));
            List<ProfileModule> modules = new ArrayList<>();
            modules.add(new ProfileModule(ProfileModule.TROPHIES, true));
            modules.add(new ProfileModule(ProfileModule.CONTRIBUTION, true));
            modules.add(new ProfileModule(ProfileModule.MY_POSTS, false));
            modules.add(new ProfileModule(ProfileModule.ACHIEVEMENTS, false));
            modules.add(new ProfileModule(ProfileModule.COLLECTIONS, false));
            modules.add(new ProfileModule(ProfileModule.WORLDCUP, false));
            await(PublicProfileSettings.save(db, uid, modules));
            assertEquals(await(db.collection("users").document(uid).get()).get("profileModules"),
                    await(db.collection("publicProfiles").document(uid).get()).get("profileModules"));
            Visitor.database = db;
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> {
                    Visitor visitor = new Visitor();
                    Bundle args = new Bundle(); args.putString("userId", uid); visitor.setArguments(args);
                    activity.getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer, visitor).commitNow();
                    activity.findViewById(R.id.bottomNavigation).setVisibility(View.GONE);
                });
                waitForLayout(scenario, R.id.cardTrophies, View.VISIBLE, true);
                // 같은 화면을 열어둔 상태에서도 새 순서와 숨김 설정이 반영되어야 합니다.
                java.util.Collections.swap(modules, 0, 1);
                modules.get(1).setVisible(false);
                await(PublicProfileSettings.save(db, uid, modules));
                waitForLayout(scenario, R.id.cardContribution, View.GONE, true);
                for (ProfileModule module : modules) module.setVisible(false);
                await(PublicProfileSettings.save(db, uid, modules));
                waitForLayout(scenario, R.id.cardContribution, View.GONE, false);
                scenario.onActivity(activity -> {
                    LinearLayout container = activity.findViewById(R.id.moduleContainer);
                    for (int i = 0; i < container.getChildCount(); i++) {
                        assertEquals(View.GONE, container.getChildAt(i).getVisibility());
                    }
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.textAuthorProfileMessage).getVisibility());
                });
            }
        } finally {
            Visitor.database = null;
            await(db.terminate());
            app.delete();
        }
    }

    private void waitForLayout(ActivityScenario<MainActivity> scenario, int first, int trophyVisibility,
                               boolean contributionVisible) throws Exception {
        AtomicBoolean matched = new AtomicBoolean();
        for (int attempt = 0; attempt < 60; attempt++) {
            scenario.onActivity(activity -> {
                LinearLayout container = activity.findViewById(R.id.moduleContainer);
                matched.set(container.getVisibility() == View.VISIBLE && container.getChildCount() == 6
                        && container.getChildAt(0).getId() == first
                        && activity.findViewById(R.id.cardTrophies).getVisibility() == trophyVisibility
                        && activity.findViewById(R.id.cardContribution).getVisibility()
                            == (contributionVisible ? View.VISIBLE : View.GONE)
                        && activity.findViewById(R.id.cardMyPosts).getVisibility() == View.GONE);
            });
            if (matched.get()) return;
            Thread.sleep(100);
        }
        fail("방문 페이지에 저장한 순서·표시 여부가 반영되지 않았습니다.");
    }
}
