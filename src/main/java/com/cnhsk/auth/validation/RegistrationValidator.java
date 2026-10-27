package com.cnhsk.auth.validation;

import java.util.regex.Pattern;

import com.cnhsk.auth.dto.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class RegistrationValidator implements ConstraintValidator<ValidRegistration, RegisterRequest> {

    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    @Override
    public boolean isValid(RegisterRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();
        String password = request.password();
        if (password != null && !password.isEmpty()) {
            if (!LETTER.matcher(password).find() || !DIGIT.matcher(password).find()
                    || !password.equals(password.strip())) {
                addViolation(context, "password", "Mật khẩu cần 8–72 ký tự, gồm chữ và số.");
                valid = false;
            }
            if (request.email() != null && password.equalsIgnoreCase(request.email())) {
                addViolation(context, "password", "Mật khẩu không được trùng email.");
                valid = false;
            }
        }
        if (request.password() != null && request.confirmPassword() != null
                && !request.password().equals(request.confirmPassword())) {
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
