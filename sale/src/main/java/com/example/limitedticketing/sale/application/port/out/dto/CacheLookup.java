package com.example.limitedticketing.sale.application.port.out.dto;

public record CacheLookup<T>(Status status, T value, CacheFillLease lease) {
    public enum Status {
        HIT, CLAIMED, BUSY, BYPASS
    }

    public static <T> CacheLookup<T> hit(T value) {
        return new CacheLookup<>(Status.HIT, value, null);
    }

    public static <T> CacheLookup<T> claimed(CacheFillLease lease) {
        return new CacheLookup<>(Status.CLAIMED, null, lease);
    }

    public static <T> CacheLookup<T> busy() {
        return new CacheLookup<>(Status.BUSY, null, null);
    }

    public static <T> CacheLookup<T> bypass() {
        return new CacheLookup<>(Status.BYPASS, null, null);
    }
}
