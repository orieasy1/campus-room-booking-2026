package itm.oss.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Issue 7 - filter and validation tests for RoomUtilizationService.summarize().
 * Covers: cancelled / other-room / other-date bookings are ignored,
 * and every invalid input throws IllegalArgumentException.
 */
class RoomUtilizationFilterAndValidationTest {

    private static final String ROOM = "R101";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 2);
    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(17, 0); // 480 minutes

    private Booking booking(int id, String roomId, LocalDate date,
                            LocalTime start, LocalTime end, BookingStatus status) {
        return new Booking(
                id,
                "s1",
                roomId,
                LocalDateTime.of(date, start),
                LocalDateTime.of(date, end),
                2,
                "Study",
                status);
    }

    private void assertEmptyUsage(RoomUsage usage) {
        assertEquals(0, usage.getBookingCount());
        assertEquals(0L, usage.getBookedMinutes());
        assertEquals(new BigDecimal("0.00"), usage.getUtilizationPercent());
    }

    // ---------------------------------------------------------------
    // Filter tests
    // ---------------------------------------------------------------

    @Test
    void cancelledBookingIsIgnored() {
        List<Booking> bookings = List.of(
                booking(1, ROOM, DATE, LocalTime.of(10, 0), LocalTime.of(11, 0),
                        BookingStatus.CANCELLED));

        RoomUsage usage = RoomUtilizationService.summarize(bookings, ROOM, DATE, OPEN, CLOSE);

        assertEmptyUsage(usage);
    }

    @Test
    void bookingForOtherRoomIsIgnored() {
        List<Booking> bookings = List.of(
                booking(1, "R102", DATE, LocalTime.of(10, 0), LocalTime.of(11, 0),
                        BookingStatus.ACTIVE));

        RoomUsage usage = RoomUtilizationService.summarize(bookings, ROOM, DATE, OPEN, CLOSE);

        assertEmptyUsage(usage);
    }

    @Test
    void bookingOnOtherDateIsIgnored() {
        List<Booking> bookings = List.of(
                booking(1, ROOM, DATE.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0),
                        BookingStatus.ACTIVE));

        RoomUsage usage = RoomUtilizationService.summarize(bookings, ROOM, DATE, OPEN, CLOSE);

        assertEmptyUsage(usage);
    }

    @Test
    void bookingOnPreviousDateIsIgnored() {
        List<Booking> bookings = List.of(
                booking(1, ROOM, DATE.minusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0),
                        BookingStatus.ACTIVE));

        RoomUsage usage = RoomUtilizationService.summarize(bookings, ROOM, DATE, OPEN, CLOSE);

        assertEmptyUsage(usage);
    }

    @Test
    void mixedBookingsCountOnlyActiveSameRoomSameDate() {
        List<Booking> bookings = List.of(
                // the only valid booking: 10:00-11:00 = 60 minutes
                booking(1, ROOM, DATE, LocalTime.of(10, 0), LocalTime.of(11, 0),
                        BookingStatus.ACTIVE),
                // cancelled, same room/date -> ignored
                booking(2, ROOM, DATE, LocalTime.of(13, 0), LocalTime.of(15, 0),
                        BookingStatus.CANCELLED),
                // other room -> ignored
                booking(3, "R102", DATE, LocalTime.of(11, 0), LocalTime.of(12, 0),
                        BookingStatus.ACTIVE),
                // other date -> ignored
                booking(4, ROOM, DATE.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0),
                        BookingStatus.ACTIVE));

        RoomUsage usage = RoomUtilizationService.summarize(bookings, ROOM, DATE, OPEN, CLOSE);

        assertEquals(1, usage.getBookingCount());
        assertEquals(60L, usage.getBookedMinutes());
        assertEquals(new BigDecimal("12.50"), usage.getUtilizationPercent()); // 60 / 480
    }

    @Test
    void cancelledBookingDoesNotReduceOrAddToActiveBooking() {
        List<Booking> bookings = List.of(
                booking(1, ROOM, DATE, LocalTime.of(9, 0), LocalTime.of(11, 0),
                        BookingStatus.ACTIVE),
                booking(2, ROOM, DATE, LocalTime.of(14, 0), LocalTime.of(16, 0),
                        BookingStatus.CANCELLED));

        RoomUsage usage = RoomUtilizationService.summarize(bookings, ROOM, DATE, OPEN, CLOSE);

        assertEquals(1, usage.getBookingCount());
        assertEquals(120L, usage.getBookedMinutes());
        assertEquals(new BigDecimal("25.00"), usage.getUtilizationPercent()); // 120 / 480
    }

    @Test
    void bookingStartingOnPreviousDateIsClippedToWindow() {
        // 2026-10-01 22:00 -> 2026-10-02 10:00, counted only from 09:00 to 10:00
        List<Booking> bookings = List.of(
                new Booking(1, "s1", ROOM,
                        LocalDateTime.of(DATE.minusDays(1), LocalTime.of(22, 0)),
                        LocalDateTime.of(DATE, LocalTime.of(10, 0)),
                        2, "Study", BookingStatus.ACTIVE));

        RoomUsage usage = RoomUtilizationService.summarize(bookings, ROOM, DATE, OPEN, CLOSE);

        assertEquals(1, usage.getBookingCount());
        assertEquals(60L, usage.getBookedMinutes());
        assertEquals(new BigDecimal("12.50"), usage.getUtilizationPercent());
    }

    @Test
    void bookingEndingOnNextDateIsClippedToWindow() {
        // 2026-10-02 16:00 -> 2026-10-03 01:00, counted only from 16:00 to 17:00
        List<Booking> bookings = List.of(
                new Booking(1, "s1", ROOM,
                        LocalDateTime.of(DATE, LocalTime.of(16, 0)),
                        LocalDateTime.of(DATE.plusDays(1), LocalTime.of(1, 0)),
                        2, "Study", BookingStatus.ACTIVE));

        RoomUsage usage = RoomUtilizationService.summarize(bookings, ROOM, DATE, OPEN, CLOSE);

        assertEquals(1, usage.getBookingCount());
        assertEquals(60L, usage.getBookedMinutes());
        assertEquals(new BigDecimal("12.50"), usage.getUtilizationPercent());
    }

    // ---------------------------------------------------------------
    // Validation tests (all must throw IllegalArgumentException)
    // ---------------------------------------------------------------

    @Test
    void nullRoomIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> RoomUtilizationService.summarize(List.of(), null, DATE, OPEN, CLOSE));
    }

    @Test
    void emptyRoomIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> RoomUtilizationService.summarize(List.of(), "", DATE, OPEN, CLOSE));
    }

    @Test
    void blankRoomIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> RoomUtilizationService.summarize(List.of(), "   ", DATE, OPEN, CLOSE));
    }

    @Test
    void nullDateThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> RoomUtilizationService.summarize(List.of(), ROOM, null, OPEN, CLOSE));
    }

    @Test
    void nullOpenTimeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> RoomUtilizationService.summarize(List.of(), ROOM, DATE, null, CLOSE));
    }

    @Test
    void nullCloseTimeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> RoomUtilizationService.summarize(List.of(), ROOM, DATE, OPEN, null));
    }

    @Test
    void openEqualToCloseThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> RoomUtilizationService.summarize(List.of(), ROOM, DATE, OPEN, OPEN));
    }

    @Test
    void openAfterCloseThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> RoomUtilizationService.summarize(List.of(), ROOM, DATE, CLOSE, OPEN));
    }
}
