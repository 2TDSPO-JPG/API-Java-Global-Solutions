package br.com.saneia.api.sensor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Locale;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** Sigla de uma das 27 UFs (maiúsculas ou minúsculas). Vazio fica para o @NotBlank. */
@Documented
@Constraint(validatedBy = Uf.Validator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Uf {

    String message() default "Informe uma UF válida (ex.: SP).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<Uf, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank()
                    || PostalAddress.UFS.contains(value.trim().toUpperCase(Locale.ROOT));
        }
    }
}
