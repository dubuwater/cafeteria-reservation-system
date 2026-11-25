package kr.ac.cbnu.cafeteria.web.dto;

import kr.ac.cbnu.cafeteria.domain.Role;
import kr.ac.cbnu.cafeteria.domain.User;

public record UserDto(String userId, String name, Role role) {

    public static UserDto from(User user) {
        return new UserDto(user.getUserId(), user.getName(), user.getRole());
    }
}
