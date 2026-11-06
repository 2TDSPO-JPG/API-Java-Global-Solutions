package br.com.saneia.api.user;

import com.fasterxml.jackson.annotation.JsonValue;

/** Papéis do contrato: resident (morador), operator (operadora) e admin (administração). */
public enum Role {
    RESIDENT("resident"),
    OPERATOR("operator"),
    ADMIN("admin");

    private final String apiValue;

    Role(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String apiValue() {
        return apiValue;
    }

    /** Autoridade usada pelo Spring Security (ROLE_RESIDENT, ROLE_OPERATOR, ROLE_ADMIN). */
    public String authority() {
        return "ROLE_" + name();
    }
}
