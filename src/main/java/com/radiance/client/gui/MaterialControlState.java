package com.radiance.client.gui;

public record MaterialControlState(boolean enabled, String disabledReason) {
    public static final MaterialControlState ENABLED = new MaterialControlState(true, null);

    public static MaterialControlState disabled(String reason) {
        return new MaterialControlState(false, reason);
    }
}
