package com.cnhsk.auth.validation;

import java.util.regex.Pattern;

import com.cnhsk.auth.dto.ResetPasswordRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class ResetPasswordValidator implements ConstraintValidator<ValidResetPassword, ResetPasswordRequest> {

    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final String PASSWORD_MESSAGE = "Mật khẩu cần 8–72 ký tự, gồm chữ và số.";

    @Override
    public boolean isValid(ResetPasswordRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        boolean valid = true;
        context.disableDefaultConstraintViolation();
        String password = request.newPassword();
        if (password != null && !password.isEmpty()
                && (!LETTER.matcher(password).find() || !DIGIT.matcher(password).find()
                        || !password.equals(password.strip()))) {
            addViolation(context, "newPassword", PASSWORD_MESSAGE);
            valid = false;
        }
        if (password != null && request.confirmPassword() != null
                && !password.equals(request.confirmPassword())) {
            addViolation(context, "confirmPassword", "Hai mật khẩu không khớp.");
            valid = false;
        }
        return valid;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
