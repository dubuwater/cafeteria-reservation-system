package kr.ac.cbnu.cafeteria.web.dto;

public record LoginRequest(String id, String pw) {
    @Override
    public String toString() {
        return "LoginRequest[credentials omitted]";
    }
}
