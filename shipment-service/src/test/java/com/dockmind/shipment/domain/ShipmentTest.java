package com.dockmind.shipment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShipmentTest {

    private static final Instant START = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant END = START.plus(Duration.ofHours(2));

    private static Shipment shipment(int pallets) {
        return new Shipment("tenant-a", "PO-1001", Direction.INBOUND, pallets,
                Set.of(DockCapability.REFRIGERATED), Priority.STANDARD, START, END);
    }

    @Test
    void newShipmentIsBookedWithGeneratedId() {
        Shipment s = shipment(10);

        assertThat(s.getId()).isNotNull();
        assertThat(s.getStatus()).isEqualTo(ShipmentStatus.BOOKED);
        assertThat(s.getRequiredCapabilities()).containsExactly(DockCapability.REFRIGERATED);
        assertThat(s.getVersion()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Shipment.MAX_PALLETS + 1})
    void rejectsPalletCountOutOfRange(int pallets) {
        assertThatThrownBy(() -> shipment(pallets))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("palletCount");
    }

    @Test
    void rejectsWindowThatDoesNotMoveForward() {
        assertThatThrownBy(() -> new Shipment("tenant-a", "PO-1", Direction.INBOUND, 5,
                Set.of(), Priority.STANDARD, START, START))
                .hasMessageContaining("windowEnd");
    }

    @Test
    void rejectsBlankOrOverlongReference() {
        assertThatThrownBy(() -> new Shipment("tenant-a", " ", Direction.INBOUND, 5,
                Set.of(), Priority.STANDARD, START, END)).hasMessageContaining("reference");
        assertThatThrownBy(() -> new Shipment("tenant-a", "x".repeat(65), Direction.INBOUND, 5,
                Set.of(), Priority.STANDARD, START, END)).hasMessageContaining("at most 64");
    }

    @Test
    void capabilitiesCannotBeModifiedFromOutside() {
        Shipment s = shipment(5);

        assertThatThrownBy(() -> s.getRequiredCapabilities().add(DockCapability.HAZMAT))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void bookedShipmentCanArriveOnce() {
        Shipment s = shipment(5);
        Instant arrival = START.plus(Duration.ofMinutes(30));

        s.markArrived(arrival);

        assertThat(s.getStatus()).isEqualTo(ShipmentStatus.ARRIVED);
        assertThat(s.getActualArrivalAt()).isEqualTo(arrival);
        assertThatThrownBy(() -> s.markArrived(arrival)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void arrivedShipmentCannotBeCancelled() {
        Shipment s = shipment(5);
        s.markArrived(START);

        assertThatThrownBy(s::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ARRIVED");
    }

    @Test
    void lateOnlyAfterWindowCloses() {
        Shipment s = shipment(5);

        assertThat(s.isLate(END)).isFalse();
        assertThat(s.isLate(END.plusSeconds(1))).isTrue();
    }
}
