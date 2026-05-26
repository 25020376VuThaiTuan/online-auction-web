package org.example.auction;

public record AuctionExtensionConfig(
        long triggerWindowSeconds,
        long extensionSeconds,
        int extensionCount,
        int maxExtensions
) {
    public static AuctionExtensionConfig defaults() {
        return new AuctionExtensionConfig(
                AuctionRules.DEFAULT_EXTENSION_TRIGGER_SECONDS,
                AuctionRules.DEFAULT_EXTENSION_SECONDS,
                0,
                AuctionRules.DEFAULT_MAX_EXTENSIONS
        );
    }

    public AuctionExtensionConfig {
        triggerWindowSeconds = Math.max(0L, triggerWindowSeconds);
        extensionSeconds = Math.max(0L, extensionSeconds);
        extensionCount = Math.max(0, extensionCount);
        maxExtensions = maxExtensions < 0 ? Integer.MAX_VALUE : maxExtensions;
    }
}
