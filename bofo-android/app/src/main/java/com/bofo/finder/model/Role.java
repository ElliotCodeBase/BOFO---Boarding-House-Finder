package com.bofo.finder.model;

public enum Role {
    STUDENT, OWNER, ADMIN;
    public String db() { return name().toLowerCase(); }
    public static Role of(String s) { return s == null ? STUDENT : Role.valueOf(s.toUpperCase()); }
}
