package org.example.auction;

public record AuctionDepositResult(
        boolean accepted,
        String message,
        double requiredDeposit,
        double lockedDeposit,
        AuctionStatus status
) {
    public static AuctionDepositResult accepted(
            String message,
            double requiredDeposit,
            double lockedDeposit,
            AuctionStatus status
    ) {
        return new AuctionDepositResult(true, message, requiredDeposit, lockedDeposit, status);
    }

    public static AuctionDepositResult rejected(
            String message,
            double requiredDeposit,
            double lockedDeposit,
            AuctionStatus status
    ) {
        return new AuctionDepositResult(false, message, requiredDeposit, lockedDeposit, status);
    }
}
