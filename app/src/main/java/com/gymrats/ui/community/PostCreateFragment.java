package com.gymrats.ui.community;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import com.gymrats.R;

import java.util.ArrayList;

public class PostCreateFragment extends Fragment {

    // 표시 이름과 DB에 저장할 ID는 같은 순서로 관리
    private static final String[] BODY_NAMES = {
            "가슴", "등", "하체", "어깨", "팔", "복근"
    };

    private static final String[] BODY_IDS = {
            "chest", "back", "legs", "shoulders", "arms", "abs"
    };

    private static final String[] EQUIPMENT_NAMES = {
            "바벨", "덤벨", "케이블", "머신", "맨몸", "밴드", "케틀벨"
    };

    private static final String[] EQUIPMENT_IDS = {
            "barbell", "dumbbell", "cable", "machine",
            "bodyweight", "band", "kettlebell"
    };

    // 선택한 항목의 상태
    private final boolean[] secondarySelected =
            new boolean[BODY_NAMES.length];

    private final boolean[] equipmentSelected =
            new boolean[EQUIPMENT_NAMES.length];

    private int primaryPosition = 0;

    public PostCreateFragment() {
        super(R.layout.fragment_post_create);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 화면 회전 등으로 Fragment가 다시 만들어지면 선택값 복원
        if (savedInstanceState != null) {
            primaryPosition = savedInstanceState.getInt("primaryPosition", 0);
            restoreSelection(savedInstanceState, "secondary", secondarySelected);
            restoreSelection(savedInstanceState, "equipment", equipmentSelected);
        }
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        // 작성 화면을 닫고 이전 화면으로 복귀
        view.findViewById(R.id.btnBack).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack()
        );

        Spinner primarySpinner =
                view.findViewById(R.id.spinnerPrimaryBodyPart);
        Button secondaryButton =
                view.findViewById(R.id.btnSecondaryBodyParts);
        Button equipmentButton =
                view.findViewById(R.id.btnEquipment);

        // 주 운동 부위 선택 목록 구성
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                BODY_NAMES
        );
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        primarySpinner.setAdapter(adapter);
        primarySpinner.setSelection(primaryPosition);

        updateSelectionText(
                secondaryButton, BODY_NAMES, secondarySelected,
                "보조 운동 부위 선택"
        );
        updateSelectionText(
                equipmentButton, EQUIPMENT_NAMES, equipmentSelected,
                "기구 선택"
        );

        // 주 운동 부위와 보조 운동 부위가 중복되지 않도록 처리
        primarySpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View selectedView,
                            int position,
                            long id
                    ) {
                        primaryPosition = position;
                        secondarySelected[position] = false;

                        updateSelectionText(
                                secondaryButton, BODY_NAMES,
                                secondarySelected, "보조 운동 부위 선택"
                        );
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                }
        );

        // 여러 부위와 기구를 선택하는 대화상자
        secondaryButton.setOnClickListener(v ->
                showSelectionDialog(
                        "보조 운동 부위",
                        BODY_NAMES,
                        secondarySelected,
                        secondaryButton,
                        "보조 운동 부위 선택",
                        primaryPosition
                )
        );

        equipmentButton.setOnClickListener(v ->
                showSelectionDialog(
                        "필요 기구",
                        EQUIPMENT_NAMES,
                        equipmentSelected,
                        equipmentButton,
                        "기구 선택",
                        -1
                )
        );
        // 입력 검증 후 Firestore에 게시글 저장
        Button submitButton = view.findViewById(R.id.btnSubmitPost);
        submitButton.setEnabled(true);
        submitButton.setOnClickListener(v -> submitPost(view));
    }

    private void showSelectionDialog(
            String title,
            String[] names,
            boolean[] selected,
            Button button,
            String emptyText,
            int excludedIndex
    ) {
        // 취소하면 기존 선택값이 유지되도록 임시 배열 사용
        boolean[] pending = selected.clone();

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMultiChoiceItems(names, pending, (d, index, checked) -> {
                    if (index == excludedIndex) {
                        pending[index] = false;
                        ((AlertDialog) d).getListView()
                                .setItemChecked(index, false);
                        return;
                    }

                    pending[index] = checked;
                })
                .setPositiveButton("확인", (d, which) -> {
                    System.arraycopy(
                            pending, 0, selected, 0, selected.length
                    );
                    updateSelectionText(
                            button, names, selected, emptyText
                    );
                })
                .setNegativeButton("취소", null)
                .create();

        dialog.show();
    }

    private void updateSelectionText(
            Button button,
            String[] names,
            boolean[] selected,
            String emptyText
    ) {
        ArrayList<String> labels = new ArrayList<>();

        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) {
                labels.add(names[i]);
            }
        }

        button.setText(
                labels.isEmpty()
                        ? emptyText
                        : android.text.TextUtils.join(", ", labels)
        );
    }

    // 다음 단계의 Firestore 저장 코드에서 사용할 선택값
    private String getPrimaryBodyPartId() {
        return BODY_IDS[primaryPosition];
    }

    private ArrayList<String> getSecondaryBodyPartIds() {
        return collectSelectedIds(BODY_IDS, secondarySelected);
    }

    private ArrayList<String> getEquipmentIds() {
        return collectSelectedIds(EQUIPMENT_IDS, equipmentSelected);
    }

    private ArrayList<String> collectSelectedIds(
            String[] ids,
            boolean[] selected
    ) {
        ArrayList<String> result = new ArrayList<>();

        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) {
                result.add(ids[i]);
            }
        }

        return result;
    }

    // 다중 선택값을 화면 재생성 시 유지
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt("primaryPosition", primaryPosition);
        outState.putBooleanArray("secondary", secondarySelected);
        outState.putBooleanArray("equipment", equipmentSelected);
    }

    private void restoreSelection(
            Bundle state,
            String key,
            boolean[] target
    ) {
        boolean[] saved = state.getBooleanArray(key);

        if (saved != null && saved.length == target.length) {
            System.arraycopy(saved, 0, target, 0, target.length);
        }
    }
    private void submitPost(View view) {
        EditText titleInput = view.findViewById(R.id.editPostTitle);
        EditText descriptionInput =
                view.findViewById(R.id.editPostDescription);
        EditText precautionInput =
                view.findViewById(R.id.editPostPrecaution);

        Button submitButton = view.findViewById(R.id.btnSubmitPost);

        String title = titleInput.getText().toString().trim();
        String description =
                descriptionInput.getText().toString().trim();
        String precaution =
                precautionInput.getText().toString().trim();

        // 필수 입력값 확인
        if (title.isEmpty()) {
            titleInput.setError("제목을 입력해주세요.");
            titleInput.requestFocus();
            return;
        }

        if (description.isEmpty()) {
            descriptionInput.setError("운동 설명을 입력해주세요.");
            descriptionInput.requestFocus();
            return;
        }

        ArrayList<String> equipmentIds = getEquipmentIds();

        if (equipmentIds.isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    "필요 기구를 선택해주세요. 기구가 없으면 맨몸을 선택해주세요.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // 로그인 사용자의 UID를 작성자로 사용
        FirebaseUser currentUser =
                FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(
                    requireContext(),
                    "로그인이 필요합니다.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // 확정한 게시글 필드와 초기 집계값 구성
        Map<String, Object> post = new HashMap<>();
        post.put("authorId", currentUser.getUid());
        post.put("title", title);
        post.put("description", description);
        post.put("precaution", precaution);
        post.put("primaryBodyPartId", getPrimaryBodyPartId());
        post.put("secondaryBodyPartId", getSecondaryBodyPartIds());
        post.put("equipmentIds", equipmentIds);
        post.put("thumbnailPath", null);
        post.put("videoPath", null);
        post.put("status", "published");
        post.put("likeCount", 0L);
        post.put("reviewCount", 0L);
        post.put("ratingSum", 0L);
        post.put("ratingAverage", 0.0);
        post.put("createdAt", FieldValue.serverTimestamp());

        // 저장 중 연속 클릭 방지
        submitButton.setEnabled(false);
        submitButton.setText("등록 중…");

        FirebaseFirestore.getInstance()
                .collection("posts")
                .add(post)
                .addOnCompleteListener(task -> {
                    // 화면을 떠난 뒤에는 이전 화면의 View를 조작하지 않음
                    if (!isAdded() || getView() != view) {
                        return;
                    }

                    submitButton.setEnabled(true);
                    submitButton.setText("등록");

                    if (task.isSuccessful()) {
                        Toast.makeText(
                                requireContext(),
                                "게시글이 등록되었습니다.",
                                Toast.LENGTH_SHORT
                        ).show();

                        NavHostFragment.findNavController(this)
                                .popBackStack();
                    } else {
                        Toast.makeText(
                                requireContext(),
                                "등록에 실패했습니다. 네트워크 연결을 확인해주세요.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }
}