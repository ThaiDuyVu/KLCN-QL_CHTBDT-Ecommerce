package com.example.backend.auth.dto;
import com.example.backend.auth.entity.UserStatus;
import jakarta.validation.constraints.NotNull;
public class UpdateUserStatusRequest {
    @NotNull(message = "status là bắt buộc")
    private UserStatus status;
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
}
