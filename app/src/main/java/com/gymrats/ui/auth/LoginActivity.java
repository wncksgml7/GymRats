package com.gymrats.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.gymrats.MainActivity;
import com.gymrats.R;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;

    private TextInputEditText editEmail;
    private TextInputEditText editPassword;
    private Button btnLogin;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        // 이미 로그인된 사용자는 바로 메인 화면으로 이동
        if (auth.getCurrentUser() != null) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);

        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);
        TextView textGoSignUp = findViewById(R.id.textGoSignUp);

        btnLogin.setOnClickListener(v -> login());
        textGoSignUp.setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));
    }

    private void login() {
        String email = getText(editEmail);
        String password = getText(editPassword);

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "이메일과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        goToMain();
                    } else {
                        Toast.makeText(this,
                                AuthErrorMessages.from(task.getException()),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
    }

    private static String getText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
