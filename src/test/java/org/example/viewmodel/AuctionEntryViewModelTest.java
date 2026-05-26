package org.example.viewmodel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionEntryViewModelTest {
    @Test
    void auctionListEntryExposesTableValuesAndFormattedRemainingTime() {
        AuctionListEntry entry = new AuctionListEntry(
                "ITEM-1",
                "Vintage Camera",
                "RUNNING",
                125.50,
                130.50,
                "26/05/2026 22:00",
                3_661L
        );

        assertEquals("ITEM-1", entry.getItemId());
        assertEquals("Vintage Camera", entry.getItemName());
        assertEquals("RUNNING", entry.getStatus());
        assertEquals(125.50, entry.getCurrentPrice(), 0.001);
        assertEquals(130.50, entry.getMinimumNextBid(), 0.001);
        assertEquals("26/05/2026 22:00", entry.getEndTimeString());
        assertEquals(3_661L, entry.getRemainingSeconds());
        assertEquals("1h 01m 01s", entry.getRemainingTime());
    }

    @Test
    void eligibilityEntryReportsCanEnterStateWithProvidedDeadline() {
        AuctionEligibilityEntry entry = new AuctionEligibilityEntry(
                "ITEM-2",
                "Studio Lamp",
                "OPEN",
                80.0,
                85.0,
                20.0,
                120.0,
                true,
                false,
                "26/05/2026 23:00",
                125L
        );

        assertEquals("ITEM-2", entry.getItemId());
        assertEquals("Studio Lamp", entry.getItemName());
        assertEquals("OPEN", entry.getStatus());
        assertEquals(80.0, entry.getCurrentPrice(), 0.001);
        assertEquals(85.0, entry.getMinimumBid(), 0.001);
        assertEquals(20.0, entry.getRequiredDeposit(), 0.001);
        assertEquals(120.0, entry.getAvailableBalance(), 0.001);
        assertEquals("Can Enter", entry.getEligibleText());
        assertTrue(entry.isEligible());
        assertFalse(entry.isDepositConfirmed());
        assertEquals("26/05/2026 23:00", entry.getEndTimeString());
        assertEquals(125L, entry.getRemainingSeconds());
        assertEquals("2m 05s", entry.getRemainingTime());
    }

    @Test
    void eligibilityEntryDefaultsBlankDeadlineAndPrioritizesConfirmedDeposit() {
        AuctionEligibilityEntry entry = new AuctionEligibilityEntry(
                "ITEM-3",
                "Road Bike",
                "RUNNING",
                300.0,
                310.0,
                50.0,
                40.0,
                false,
                true,
                " ",
                0L
        );

        assertEquals("N/A", entry.getEndTimeString());
        assertEquals("Entered", entry.getEligibleText());
        assertFalse(entry.isEligible());
        assertTrue(entry.isDepositConfirmed());
        assertEquals("Ended", entry.getRemainingTime());
    }

    @Test
    void eligibilityEntryConvenienceConstructorsDefaultDepositAndDeadlineState() {
        AuctionEligibilityEntry withoutDeposit = new AuctionEligibilityEntry(
                "ITEM-4",
                "Print",
                "OPEN",
                25.0,
                30.0,
                5.0,
                100.0,
                false
        );
        AuctionEligibilityEntry withDeposit = new AuctionEligibilityEntry(
                "ITEM-5",
                "Signed Poster",
                "OPEN",
                45.0,
                50.0,
                10.0,
                100.0,
                true,
                true
        );

        assertEquals("No", withoutDeposit.getEligibleText());
        assertFalse(withoutDeposit.isDepositConfirmed());
        assertEquals("N/A", withoutDeposit.getEndTimeString());
        assertEquals(0L, withoutDeposit.getRemainingSeconds());

        assertEquals("Entered", withDeposit.getEligibleText());
        assertTrue(withDeposit.isEligible());
        assertTrue(withDeposit.isDepositConfirmed());
    }
}
