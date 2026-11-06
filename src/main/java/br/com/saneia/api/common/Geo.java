package br.com.saneia.api.common;

/** Distância geodésica (haversine), igual à usada pelo app em report-policy.js. */
public final class Geo {

    private static final double EARTH_DIAMETER_METERS = 12_742_000d;

    private Geo() {
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double angle = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return EARTH_DIAMETER_METERS * Math.asin(Math.sqrt(Math.min(1, angle)));
    }

    public static double distanceMeters(Coordinate a, Coordinate b) {
        return distanceMeters(a.latitude(), a.longitude(), b.latitude(), b.longitude());
    }
}
