package com.example.alertagent.domain;

public enum AlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static AlertSeverity max(AlertSeverity left, AlertSeverity right) {
        if (left == null) {
            return right == null ? MEDIUM : right;
        }
        if (right == null) {
            return left;
        }
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
