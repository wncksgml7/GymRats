package com.gymrats.ui.worldcup;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavHost;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gymrats.R;
import com.gymrats.databinding.FragmentWorldCupMatchBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WorldCupMatchFragment extends Fragment {

    private static final String PREFS_NAME = "world_cup";
    private static final String KEY_CHOICES = "saved_choices_real_cup_01";

    private static final List<String> CANDIDATE_POST_IDS = Arrays.asList(
            "IzF4dHrraTN3VSzviboI", "LfVqnRDllgqEcnA99fEJ",
            "RVcDYtGD55iSkD1YoVSM", "VK0KWqp1dUiuiIpAivws",
            "XOaizMryVm6AzotYIlQq", "jUTB1Bx36a2FfyalOKrO",
            "oWGlKXXNHMRFBRv1xLGr", "xZM5qw2oFUptHWBWdge6"
    );

    private FragmentWorldCupMatchBinding binding;

    private List<WorldCupCandidate> currentRound = new ArrayList<>();
    private final List<WorldCupCandidate> nextRound = new ArrayList<>();
    private final StringBuilder choices = new StringBuilder();
    private int matchIndex = 0;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentWorldCupMatchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnSelectLeft.setOnClickListener(v -> selectCandidate(0));
        binding.btnSelectRight.setOnClickListener(v -> selectCandidate(1));

        binding.btnCloseWorldCup.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());

        binding.btnSaveWorldCup.setOnClickListener(v -> {
            worldCupPrefs().edit()
                    .putString(KEY_CHOICES, choices.toString())
                    .apply();

            NavHostFragment.findNavController(this).popBackStack();
        });

        loadCandidates();
    }

    private void loadCandidates() {
        FragmentWorldCupMatchBinding loadingBinding = binding;

        binding.tvRoundStatus.setText("후보 불러오는 중…");
        binding.tvLeftCandidateTitle.setText("");
        binding.tvRightCandidateTitle.setText("");
        binding.btnSelectLeft.setEnabled(false);
        binding.btnSelectRight.setEnabled(false);
        binding.btnSaveWorldCup.setEnabled(false);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<Task<DocumentSnapshot>> requests = new ArrayList<>();

        for (String postId : CANDIDATE_POST_IDS) {
            requests.add(db.collection("posts").document(postId).get());
        }

        Tasks.whenAll(requests)
                .addOnSuccessListener(unused -> {
                    if (binding != loadingBinding) return;

                    List<WorldCupCandidate> candidates = new ArrayList<>();

                    // 요청한 순서대로 읽어 첫 라운드의 대진을 유지한다.
                    for (Task<DocumentSnapshot> request : requests) {
                        DocumentSnapshot post = request.getResult();
                        String title = post.getString("title");

                        if (!post.exists()
                                || !"published".equals(post.getString("status"))
                                || title == null
                                || title.trim().isEmpty()) {
                            Log.w("WorldCup", "유효하지 않은 후보 ID " + post.getId());
                            showCandidateLoadError();
                            return;
                        }

                        candidates.add(new WorldCupCandidate(
                                post.getId(),
                                title,
                                post.getString("thumbnailPath")
                        ));
                    }

                    String previousChoices = choices.toString();
                    if (previousChoices.isEmpty()) {
                        previousChoices = worldCupPrefs().getString(KEY_CHOICES, "");
                    }

                    currentRound = candidates;
                    nextRound.clear();
                    matchIndex = 0;
                    choices.setLength(0);

                    // 저장 기록은 결승 직전까지 최대 6개다.
                    if (previousChoices.matches("[01]{0,6}")) {
                        for (int i = 0; i < previousChoices.length(); i++) {
                            advanceMatch(previousChoices.charAt(i) - '0');
                        }
                    }

                    showMatch();
                    binding.btnSelectLeft.setEnabled(true);
                    binding.btnSelectRight.setEnabled(true);
                    binding.btnSaveWorldCup.setEnabled(true);
                })
                .addOnFailureListener(error -> {
                    if (binding != loadingBinding) return;

                    Log.e("WorldCup", "후보 조회 실패", error);
                    showCandidateLoadError();
                });
    }

    private void showMatch() {
        int candidateCount = currentRound.size();
        int matchCount = candidateCount / 2;
        int leftIndex = matchIndex * 2;

        String roundName = candidateCount == 2 ? "결승" : candidateCount + "강";

        binding.tvRoundStatus.setText(
                roundName + " · " + (matchIndex + 1) + "/" + matchCount + " 대결"
        );
        binding.progressMatch.setMax(matchCount);
        binding.progressMatch.setProgress(matchIndex);

        WorldCupCandidate leftCandidate = currentRound.get(leftIndex);
        WorldCupCandidate rightCandidate = currentRound.get(leftIndex + 1);

        binding.tvLeftCandidateTitle.setText(leftCandidate.title);
        binding.tvRightCandidateTitle.setText(rightCandidate.title);
    }

    private void selectCandidate(int side) {
        if (currentRound.size() < 2) return;

        advanceMatch(side);

        if (currentRound.size() == 1) {
            worldCupPrefs().edit().remove(KEY_CHOICES).apply();
            showWinner(currentRound.get(0));
            return;
        }

        showMatch();
    }

    private void advanceMatch(int side) {
        int selectedIndex = matchIndex * 2 + side;

        choices.append(side);
        nextRound.add(currentRound.get(selectedIndex));
        matchIndex++;

        if (matchIndex == currentRound.size() / 2) {
            currentRound = new ArrayList<>(nextRound);
            nextRound.clear();
            matchIndex = 0;
        }
    }

    private void showWinner(WorldCupCandidate winner) {
        Bundle args = new Bundle();
        args.putString("winnerTitle", winner.title);

        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(R.id.worldCupMatchFragment, true)
                .build();

        NavHostFragment.findNavController(this)
                .navigate(R.id.worldCupResultFragment, args, options);
    }

    private void showCandidateLoadError() {
        binding.tvRoundStatus.setText("후보를 불러오지 못했습니다");
        Toast.makeText(requireContext(),
                "공개 운동법 후보 8개를 확인해 주세요.",
                Toast.LENGTH_SHORT).show();
    }

    private SharedPreferences worldCupPrefs() {
        return requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}