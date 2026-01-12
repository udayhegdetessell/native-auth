package com.tessell.entra.dto.request;

import lombok.Data;

@Data
public class CreateUserRequest {
    private String email;
    private String displayName;
    private String password;
}

