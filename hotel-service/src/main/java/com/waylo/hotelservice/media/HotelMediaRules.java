package com.waylo.hotelservice.media;

/** The one rule for what the public may see (contract sec. 5). */
public final class HotelMediaRules {

    private HotelMediaRules() { }

    public static boolean isPubliclyServable(HotelMedia.ScanStatus scan, HotelMedia.ReviewStatus review, boolean hotelPublic) {
        return hotelPublic && scan == HotelMedia.ScanStatus.CLEAN && review == HotelMedia.ReviewStatus.APPROVED;
    }
}
