package com.stecker.backend.model;

/** What a staff account may do. An admin can do everything staff can. */
public enum StaffRole {
    /** Waiters: work with orders. */
    STAFF,
    /** Additionally manages staff accounts and the menu. */
    ADMIN
}
