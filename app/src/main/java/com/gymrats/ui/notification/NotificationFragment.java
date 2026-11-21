
package com.gymrats.ui.notification;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import com.gymrats.databinding.FragmentNotificationBinding;
import com.gymrats.databinding.ItemNotificationBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class NotificationFragment extends Fragment {

    private FragmentNotificationBinding binding;

    private FirebaseFirestore db;
    private ListenerRegistration notificationListener;

    // 읽지 않은 알림의 Firestore 문서 목록
    private final ArrayList<DocumentReference> unreadRefs =
            new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        binding = FragmentNotificationBinding.inflate(
                inflater, container, false
        );

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();

        // 뒤로가기
        binding.btnBack.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigateUp()
        );

        // 모두 읽음
        binding.btnReadAll.setOnClickListener(v ->
                markAllAsRead()
        );

        // Firestore 알림 조회
        loadNotifications();
    }

    private void loadNotifications() {

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            showToast("로그인이 필요합니다.");
            return;
        }

        String uid = user.getUid();

        notificationListener = db.collection("users")
                .document(uid)
                .collection("notifications")
                .orderBy(
                        "createdAt",
                        Query.Direction.DESCENDING
                )
                .addSnapshotListener((snapshot, error) -> {

                    if (binding == null) return;

                    if (error != null) {
                        Log.e("NOTIFICATION",
                                "알림 조회 실패", error);
                        showToast("알림을 불러오지 못했습니다.");
                        return;
                    }

                    if (snapshot == null) return;

                    // 기존 화면 초기화
                    binding.todayContainer.removeAllViews();
                    binding.previousContainer.removeAllViews();
                    unreadRefs.clear();

                    for (DocumentSnapshot document
                            : snapshot.getDocuments()) {

                        String type =
                                document.getString("type");

                        String message =
                                document.getString("message");

                        Boolean isRead =
                                document.getBoolean("isRead");

                        Timestamp createdAt =
                                document.getTimestamp("createdAt");

                        // isRead가 false인 알림만 미읽음 처리
                        boolean unread =
                                Boolean.FALSE.equals(isRead);

                        if (unread) {
                            unreadRefs.add(document.getReference());
                        }

                        LinearLayout container;

                        if (createdAt != null
                                && isToday(createdAt.toDate())) {
                            container = binding.todayContainer;
                        } else {
                            container = binding.previousContainer;
                        }

                        addNotification(
                                container,
                                getIcon(type),
                                message == null ? "" : message,
                                formatTime(createdAt),
                                unread,
                                document.getReference()
                        );
                    }
                });
    }

    // 개별 알림 화면 구성
    private void addNotification(
            LinearLayout container,
            String icon,
            String message,
            String time,
            boolean unread,
            DocumentReference notificationRef) {

        ItemNotificationBinding item =
                ItemNotificationBinding.inflate(
                        getLayoutInflater(),
                        container,
                        false
                );

        item.tvIcon.setText(icon);
        item.tvTitle.setText(message);
        item.tvTime.setText(time);

        item.unreadDot.setVisibility(
                unread ? View.VISIBLE : View.GONE
        );

        // 개별 알림 읽음 처리
        item.notificationRow.setOnClickListener(v -> {

            if (!unread) return;

            item.notificationRow.setEnabled(false);

            notificationRef.update("isRead", true)
                    .addOnSuccessListener(unused -> {
                        // SnapshotListener가 화면을 갱신함
                        Log.d("NOTIFICATION", "읽음 처리 성공");
                    })
                    .addOnFailureListener(e -> {
                        Log.e("NOTIFICATION",
                                "읽음 처리 실패", e);

                        item.notificationRow.setEnabled(true);
                        showToast("읽음 처리에 실패했습니다.");
                    });
        });

        container.addView(item.getRoot());
    }

    // 모든 알림 읽음 처리
    private void markAllAsRead() {

        if (unreadRefs.isEmpty()) {
            showToast("읽지 않은 알림이 없습니다.");
            return;
        }

        // 변경 중인 목록의 복사본
        ArrayList<DocumentReference> targets =
                new ArrayList<>(unreadRefs);

        binding.btnReadAll.setEnabled(false);

        // 대량 알림도 처리하도록 batch 분할
        ArrayList<Task<Void>> tasks = new ArrayList<>();
        WriteBatch batch = db.batch();
        int count = 0;

        for (DocumentReference ref : targets) {

            batch.update(ref, "isRead", true);
            count++;

            if (count == 400) {
                tasks.add(batch.commit());
                batch = db.batch();
                count = 0;
            }
        }

        if (count > 0) {
            tasks.add(batch.commit());
        }

        Tasks.whenAll(tasks)
                .addOnSuccessListener(unused -> {
                    if (binding != null) {
                        binding.btnReadAll.setEnabled(true);
                    }
                    showToast("모든 알림을 읽었습니다.");
                })
                .addOnFailureListener(e -> {
                    Log.e("NOTIFICATION",
                            "모두 읽음 실패", e);

                    if (binding != null) {
                        binding.btnReadAll.setEnabled(true);
                    }

                    showToast("일부 알림을 처리하지 못했습니다.");
                });
    }

    // 알림 종류별 아이콘
    private String getIcon(String type) {

        if ("review".equals(type)) return "☆";
        if ("follow".equals(type)) return "♙";
        if ("comment".equals(type)) return "▢";

        return "▣";
    }

    // 오늘인지 확인
    private boolean isToday(Date date) {

        Calendar today = Calendar.getInstance();
        Calendar target = Calendar.getInstance();

        target.setTime(date);

        return today.get(Calendar.YEAR)
                == target.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR)
                == target.get(Calendar.DAY_OF_YEAR);
    }

    // 알림 시간 표시
    private String formatTime(Timestamp timestamp) {

        if (timestamp == null) {
            return "시간 정보 없음";
        }

        Date date = timestamp.toDate();

        if (isToday(date)) {

            long difference = Math.max(
                    0L,
                    System.currentTimeMillis() - date.getTime()
            );

            long minutes = difference / 60000L;

            if (minutes < 1) return "방금 전";
            if (minutes < 60) return minutes + "분 전";

            return (minutes / 60) + "시간 전";
        }

        return new SimpleDateFormat(
                "M월 d일",
                Locale.KOREA
        ).format(date);
    }

    private void showToast(String message) {

        if (getContext() != null) {
            Toast.makeText(
                    requireContext(),
                    message,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    public void onDestroyView() {

        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
        }

        unreadRefs.clear();
        binding = null;

        super.onDestroyView();
    }
}
