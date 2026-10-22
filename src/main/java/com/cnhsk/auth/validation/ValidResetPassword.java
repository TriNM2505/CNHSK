package com.cnhsk.auth.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = ResetPasswordValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidResetPassword {
    String message() default "Mật khẩu cần 8–72 ký tự, gồm chữ và số.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
