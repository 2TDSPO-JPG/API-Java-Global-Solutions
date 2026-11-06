package br.com.saneia.api.user;

public record UserResponse(String id, String name, String email, Role role) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(String.valueOf(user.getId()), user.getName(), user.getEmail(), user.getRole());
    }
}
