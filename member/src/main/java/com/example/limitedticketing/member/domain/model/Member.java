package com.example.limitedticketing.member.domain.model;

public class Member {

    private final Long id;
    private final String externalId;
    private final String name;
    private final MemberStatus status;

    private Member(Long id, String externalId, String name, MemberStatus status) {
        this.id = id;
        this.externalId = requireText(externalId, "External ID");
        this.name = requireText(name, "Name");
        this.status = status;
    }

    public static Member register(String externalId, String name) {
        return new Member(null, externalId, name, MemberStatus.ACTIVE);
    }

    public static Member reconstitute(Long id, String externalId, String name, MemberStatus status) {
        return new Member(id, externalId, name, status);
    }

    public void ensurePurchasable() {
        if (status != MemberStatus.ACTIVE) {
            throw new IllegalStateException("Member is not allowed to purchase");
        }
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public MemberStatus getStatus() {
        return status;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
