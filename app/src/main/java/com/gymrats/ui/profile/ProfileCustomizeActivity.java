package com.gymrats.ui.profile;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gymrats.R;
import com.gymrats.model.ProfileModule;

import java.util.ArrayList;
import java.util.List;

//프로필 커스터마이즈 : 내 정보 화면에 보일 모듈의 표시 여부와 순서를 정해 users/{uid}.profileModules에 저장
public class ProfileCustomizeActivity extends AppCompatActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<ProfileModule> modules = new ArrayList<>();

    private ProfileModuleAdapter adapter;
    private TextView tvNickname, tvItemInfo;
    private Button btnSave;
    private DocumentReference userRef;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_customize);

        //로그인 안했으면 uid == null, 화면을 닫음, finish()만으론 아래 코드가 실행되므로 return으로 막음
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            finish();
            return;
        }
        userRef = db.collection("users").document(uid);

        tvNickname = findViewById(R.id.tvNickname);
        tvItemInfo = findViewById(R.id.tvItemInfo);
        btnSave = findViewById(R.id.btnSave);

        //뒤로가기는 저장하지 않고 닫고, 저장버튼을 누르면 저장하고 닫음
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save());
        findViewById(R.id.btnItems).setOnClickListener(v ->
                Toast.makeText(this, "준비 중입니다", Toast.LENGTH_SHORT).show());

        //어뎁터를 만들고 로드해야함(순서 중요)
        //load가 끝나면 adapter.notifyDataSetChanged()를 부르기 때문에 setupModuleList()를 먼저 호출
        setupModuleList();
        load();
    }

    private void setupModuleList() {
        //RecyclerView 기본 설정
        RecyclerView rv = findViewById(R.id.rvModules);
        rv.setLayoutManager(new LinearLayoutManager(this));

        //ItemTouchHelper = 끌어서 순서 바꾸기
        ItemTouchHelper touchHelper = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                    @Override
                    //끌던 줄이 다른 줄 위로 지나가면 List에서 자리 바꿈
                    public boolean onMove(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder from,
                                          @NonNull RecyclerView.ViewHolder to) {
                        adapter.move(from.getBindingAdapterPosition(), to.getBindingAdapterPosition());
                        return true;
                    }

                    @Override
                    //일단은 안쓰는데 만들어만 놨음, 밀어서 지우는 기능
                    public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                    }

                    @Override
                    public boolean isLongPressDragEnabled() {
                        return false; //손잡이(⠿)를 눌렀을 때만 끌기
                    }

                    @SuppressLint("NotifyDataSetChanged")
                    @Override
                    //끌기가 끝났을때 첫 줄이 바뀌었을 수도 있어서 adapter.notifyDataSetChanged()로 ↑ 버튼 다시 그림
                    public void clearView(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder holder) {
                        super.clearView(recyclerView, holder); //끌기때 생긴 그림자나 위치같은 효과 제거
                        adapter.notifyDataSetChanged();
                    }
                });

        //손잡이를 누르면 touchHelper::startDrag(holder)가 불리도록 어뎁터에 넘김
        adapter = new ProfileModuleAdapter(modules, touchHelper::startDrag);
        rv.setAdapter(adapter);
        touchHelper.attachToRecyclerView(rv); // ItemTouchHelper를 RecyclerView에 연결
    }

    @SuppressLint("NotifyDataSetChanged")
    //firestore 요청은 비동기여서 요청은 동시에 보내지만 도착은 불분명, 각각 도착하는대로 Listener 실행
    private void load() {
        //유저 문서 요청
        userRef.get().addOnSuccessListener(doc -> {
            tvNickname.setText(doc.getString("nickname"));

            //어뎁터와 같은 리스트를 공유하므로 새 리스트로 바꾸지 않고 clear함
            modules.clear();
            //clear된 List에 firestore에서 읽은 새 내용을 채움
            modules.addAll(ProfileModule.fromFirestore(doc.get("profileModules")));
            adapter.notifyDataSetChanged(); //데이터가 통째로 바뀌었으니 전체 다시 그림

            List<?> displayed = (List<?>) doc.get("displayedAchievements");
            int achievementCount = displayed == null ? 0 : displayed.size();
            adapter.setSubtitle(ProfileModule.ACHIEVEMENTS, achievementCount + "개 전시 중");
        });

        //트로피 개수 요청
        userRef.collection("trophies").whereEqualTo("isDisplayed", true)
                .count().get(AggregateSource.SERVER)
                .addOnSuccessListener(snap ->
                        adapter.setSubtitle(ProfileModule.TROPHIES, snap.getCount() + "개 전시 중"));

        //아이템 개수 요청
        userRef.collection("items")
                .count().get(AggregateSource.SERVER)
                .addOnSuccessListener(snap ->
                        tvItemInfo.setText("보유 아이템 " + snap.getCount() + "개 적용 가능"));
    }

    private void save() {
        //저장중 버튼을 여러번 누르는걸 막음, 비동기라 결과가 오기전에 또 누를수도 있기 때문
        btnSave.setEnabled(false);

        //내 문서의 특정 필드 하나만 바꿈
        userRef.update("profileModules", ProfileModule.toFirestore(modules))
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "저장했습니다", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "저장하지 못했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}

