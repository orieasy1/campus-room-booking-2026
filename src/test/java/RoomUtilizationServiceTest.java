package itm.oss.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class RoomUtilizationServiceTest {

    private static final String ROOM = "R101";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 2);
    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(17, 0); // 480분

    private Booking booking(int id, String start, String end) {
        return new Booking(
                id,
                "s" + id,
                ROOM,
                LocalDateTime.parse("2026-10-02T" + start),
                LocalDateTime.parse("2026-10-02T" + end),
                2,
                "Study",
                BookingStatus.ACTIVE);
    }

    private RoomUsage summarize(List<Booking> bookings) {
        return RoomUtilizationService.summarize(bookings, ROOM, DATE, OPEN, CLOSE);
    }

    private void assertUsage(RoomUsage usage, int count, long minutes, String percent) {
        assertEquals(count, usage.getBookingCount());
        assertEquals(minutes, usage.getBookedMinutes());
        assertEquals(new BigDecimal(percent), usage.getUtilizationPercent());
    }

    @Test
    void noBookings_returnsZero() {
        assertUsage(summarize(List.of()), 0, 0, "0.00");
    }

    @Test
    void bookingInsideWindow_countsFullDuration() {
        assertUsage(summarize(List.of(booking(1, "10:00", "11:00"))), 1, 60, "12.50");
    }

    @Test
    void bookingStartsBeforeOpen_isClipped() {
        // 08:30-10:00 -> 09:00-10:00 = 60분
        assertUsage(summarize(List.of(booking(1, "08:30", "10:00"))), 1, 60, "12.50");
    }

    @Test
    void bookingEndsAfterClose_isClipped() {
        // 16:30-18:00 -> 16:30-17:00 = 30분
        assertUsage(summarize(List.of(booking(1, "16:30", "18:00"))), 1, 30, "6.25");
    }

    @Test
    void bookingCoversWholeWindow_is100Percent() {
        assertUsage(summarize(List.of(booking(1, "08:00", "18:00"))), 1, 480, "100.00");
    }

    @Test
    void bookingExactlyMatchesWindow_is100Percent() {
        assertUsage(summarize(List.of(booking(1, "09:00", "17:00"))), 1, 480, "100.00");
    }

    @Test
    void multipleBookings_areSummed() {
        // 60 + 90 = 150분 -> 150/480*100 = 31.25
        List<Booking> list = List.of(
                booking(1, "10:00", "11:00"),
                booking(2, "13:00", "14:30"));
        assertUsage(summarize(list), 2, 150, "31.25");
    }

    @Test
    void partialOverlapOnBothEnds_eachClippedAndCounted() {
        // 08:30-10:00 -> 60분, 16:30-18:00 -> 30분 = 90분 -> 18.75
        List<Booking> list = List.of(
                booking(1, "08:30", "10:00"),
                booking(2, "16:30", "18:00"));
        assertUsage(summarize(list), 2, 90, "18.75");
    }

    @Test
    void bookingEndingExactlyAtOpen_isIgnored() {
        assertUsage(summarize(List.of(booking(1, "08:00", "09:00"))), 0, 0, "0.00");
    }

    @Test
    void bookingStartingExactlyAtClose_isIgnored() {
        assertUsage(summarize(List.of(booking(1, "17:00", "18:00"))), 0, 0, "0.00");
    }

    @Test
    void bookingEntirelyOutsideWindow_isIgnored() {
        assertUsage(summarize(List.of(booking(1, "06:00", "08:00"))), 0, 0, "0.00");
    }

    @Test
    void percentage_roundsDownBelowHalf() {
        // 창 09:00-12:00(180분), 1분 -> 0.5555... -> 0.56
        RoomUsage usage = RoomUtilizationService.summarize(
                List.of(booking(1, "10:00", "10:01")),
                ROOM, DATE, LocalTime.of(9, 0), LocalTime.of(12, 0));
        assertUsage(usage, 1, 1, "0.56");
    }

    @Test
    void percentage_roundsHalfUpOnExactTie() {
        // 창 09:00-22:20(800분), 1분 -> 정확히 0.125 -> HALF_UP이면 0.13 (HALF_EVEN이면 0.12)
        RoomUsage usage = RoomUtilizationService.summarize(
                List.of(booking(1, "10:00", "10:01")),
                ROOM, DATE, LocalTime.of(9, 0), LocalTime.of(22, 20));
        assertUsage(usage, 1, 1, "0.13");
    }
}