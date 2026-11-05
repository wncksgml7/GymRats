package com.gymrats.ui.auth;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

// Firebase 인증 예외를 사용자에게 보여줄 한국어 메시지로 변환
final class AuthErrorMessages {

    private AuthErrorMessages() {
    }

    static String from(Exception e) {
        // WeakPassword는 InvalidCredentials의 하위 클래스라서 먼저 검사해야 함
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "비밀번호가 너무 약합니다. 6자 이상 입력해주세요.";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "이미 가입된 이메일입니다.";
        }
        if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            return "이메일 또는 비밀번호가 올바르지 않습니다.";
        }
        if (e instanceof FirebaseNetworkException) {
            return "네트워크 연결을 확인해주세요.";
        }
        return "오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
    }
}
