package itm.oss.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class RoomUtilizationService {

    private RoomUtilizationService() {
    }

    /**
     * Issue 7: summarizes how much a room is used during its operating
     * window on the given date.
     *
     * <p>Following docs/ASSUMPTIONS.md, {@code bookings} must be a non-null
     * list with non-null elements; this method does not validate it.
     *
     * @throws IllegalArgumentException if roomId is null/blank, any of
     *         date/openTime/closeTime is null, or openTime is not before closeTime
     */
    public static RoomUsage summarize(
            List<Booking> bookings,
            String roomId,
            LocalDate date,
            LocalTime openTime,
            LocalTime closeTime) {

        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("roomId must not be null or blank");
        }
        if (date == null || openTime == null || closeTime == null) {
            throw new IllegalArgumentException("date, openTime and closeTime must not be null");
        }
        if (!openTime.isBefore(closeTime)) {
            throw new IllegalArgumentException("openTime must be before closeTime");
        }

        LocalDateTime windowStart = LocalDateTime.of(date, openTime);
        LocalDateTime windowEnd = LocalDateTime.of(date, closeTime);

        int bookingCount = 0;
        long bookedMinutes = 0;

        for (Booking booking : bookings) {
            if (booking.getStatus() != BookingStatus.ACTIVE
                    || !roomId.equals(booking.getRoomId())) {
                continue;
            }

            // Clip the booking to the operating window.
            LocalDateTime start = booking.getStart().isAfter(windowStart)
                    ? booking.getStart() : windowStart;
            LocalDateTime end = booking.getEnd().isBefore(windowEnd)
                    ? booking.getEnd() : windowEnd;

            if (start.isBefore(end)) {
                bookingCount++;
                bookedMinutes += Duration.between(start, end).toMinutes();
            }
        }

        long availableMinutes = Duration.between(windowStart, windowEnd).toMinutes();
        BigDecimal utilizationPercent = BigDecimal.valueOf(bookedMinutes)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(availableMinutes), 2, RoundingMode.HALF_UP);

        return new RoomUsage(bookingCount, bookedMinutes, utilizationPercent);
    }
}
