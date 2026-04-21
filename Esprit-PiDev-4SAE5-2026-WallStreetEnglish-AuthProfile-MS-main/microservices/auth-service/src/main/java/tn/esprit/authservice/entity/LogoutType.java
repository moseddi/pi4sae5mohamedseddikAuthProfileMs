package tn.esprit.authservice.entity;

public enum LogoutType {
    VOLUNTARY,   // user clicked Sign out
    TIMEOUT,     // session expired
    FORCED       // admin revoked the session
}