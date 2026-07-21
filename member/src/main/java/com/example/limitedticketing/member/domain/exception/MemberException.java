package com.example.limitedticketing.member.domain.exception;

import com.example.limitedticketing.shared.exception.DomainErrorType;
import com.example.limitedticketing.shared.exception.DomainException;

public final class MemberException extends DomainException {

    private MemberException(DomainErrorType errorType, String code, String message) {
        super(errorType, code, message);
    }

    public static MemberException notFound() {
        return new MemberException(DomainErrorType.NOT_FOUND, "MEMBER_NOT_FOUND", "Member not found");
    }

    public static MemberException invalid(String fieldName) {
        return new MemberException(DomainErrorType.INVALID_REQUEST, "INVALID_MEMBER", fieldName + " is required");
    }

    public static MemberException notPurchasable() {
        return new MemberException(DomainErrorType.CONFLICT, "MEMBER_NOT_PURCHASABLE", "Member is not allowed to purchase");
    }
}
