package com.edubase.auth.service.abstracts;


import com.edubase.auth.dto.AuthenticationRequest;
import com.edubase.auth.dto.AuthenticationResponse;
import com.edubase.auth.dto.RegisterRequest;
import com.edubase.auth.dto.UserResponse;

public interface AuthenticationService {

    public UserResponse register(RegisterRequest request);

    public AuthenticationResponse authenticate(AuthenticationRequest request);

    public String reactivateAccount(String token);

    public void logout(String token, String authenticatedEmail);

    public void deactivate(String email);

    public void changePassword(String oldPassword, String newPassword, String authenticatedEmail);

    AuthenticationResponse grantInstructorRoleForCurrentUser(String authenticatedEmail);
}
