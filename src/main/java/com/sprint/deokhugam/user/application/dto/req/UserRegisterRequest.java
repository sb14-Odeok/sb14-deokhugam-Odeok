package com.sprint.deokhugam.user.application.dto.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRegisterRequest(
    @NotBlank(message = "이메일은 필수입니다.") @Email(message = "이메일 형식을 다시 확인해주세요.") String email,
    @NotBlank(message = "닉네임은 필수입니다.") @Size(max = 20, message = "닉네임은 20자를 초과할 수 없습니다.")
        String nickname,
    @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
            message = "영문, 숫자 , 특수문자를 포함해주세요.")
        String password) {}
