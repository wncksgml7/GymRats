package com.gymrats.ui.profile;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gymrats.model.ProfileModule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 내 페이지와 방문 페이지의 모듈 설정을 함께 저장합니다. */
public final class PublicProfileSettings {
    private PublicProfileSettings() { }

    public static Task<Void> save(FirebaseFirestore db, String uid, List<ProfileModule> modules) {
        DocumentReference user = db.collection("users").document(uid);
        DocumentReference profile = db.collection("publicProfiles").document(uid);
        List<Map<String, Object>> values = ProfileModule.toFirestore(modules);
        return db.runTransaction(tx -> {
            DocumentSnapshot privateDoc = tx.get(user);
            DocumentSnapshot publicDoc = tx.get(profile);
            if (!privateDoc.exists()) throw new IllegalStateException("회원 정보를 먼저 불러와 주세요.");
            tx.update(user, "profileModules", values);
            if (publicDoc.exists()) {
                // 다른 담당자가 추가한 공개 프로필 필드는 보존합니다.
                tx.update(profile, "profileModules", values,
                        "profileImagePath", privateDoc.getString("profileImagePath"));
            } else {
                // 공개 프로필이 없던 기존 계정도 꾸미기 저장 시 생성합니다.
                Map<String, Object> data = new HashMap<>();
                data.put("nickname", privateDoc.getString("nickname"));
                data.put("followerCount", privateDoc.getLong("followerCount"));
                data.put("followingCount", privateDoc.getLong("followingCount"));
                data.put("profileModules", values);
                data.put("profileImagePath", privateDoc.getString("profileImagePath"));
                tx.set(profile, data);
            }
            return null;
        });
    }
}
