package com.strobel.emercast.lib;

import com.openapi.gen.android.dto.JurisdictionMarkerDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import kotlin.Pair;

public class LocationUtils {

    final static int EARTH_RADIUS_KM = 6371;
    final static int EARTH_RADIUS_M = EARTH_RADIUS_KM*1000;

    public static double distance(double lat1, double lat2, double lon1, double lon2) {

        final int R = EARTH_RADIUS_KM; // Radius of the earth

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        distance = Math.pow(distance, 2);

        return Math.sqrt(distance);
    }

    public static List<Pair<Double, Double>> getSamplesOfCircle(double circleCenterLat, double circleCenterLong, double circleRadius, double maxSampleDistance) {
        List<Pair<Double, Double>> samplePoints = new ArrayList<>();

        doForEachSampleOfCircle(circleCenterLat, circleCenterLong, circleRadius, maxSampleDistance, samplePoints::add);

        return samplePoints;
    }

    public static void doForEachSampleOfCircle(double circleCenterLat, double circleCenterLong, double circleRadius, double maxSampleDistance, Consumer<Pair<Double, Double>> sampleConsumer) {
        // Convert the latitude and longitude to radians for calculation
        double centerLatRad = Math.toRadians(circleCenterLat);
        double centerLongRad = Math.toRadians(circleCenterLong);

        // Number of radial samples based on the maxSampleDistance
        int numRadialSteps = (int) Math.ceil(circleRadius / maxSampleDistance);

        // Loop over radial distances from 0 to circleRadius
        for (int i = 0; i <= numRadialSteps; i++) {
            double currentRadius = i * maxSampleDistance;

            // Calculate the number of angular points for this radius (to avoid dense clusters near the center)
            int numAngularSteps = (int) Math.ceil(2 * Math.PI * currentRadius / maxSampleDistance);

            // Sample around the circle for this radius
            for (int j = 0; j < numAngularSteps; j++) {
                double theta = (2 * Math.PI * j) / numAngularSteps; // Angle in radians

                // Calculate the lat/long offset for the current radius and angle
                Pair<Double, Double> samplePoint = calculatePoint(centerLatRad, centerLongRad, currentRadius, theta);

                // Add the sample point to the list

                sampleConsumer.accept(samplePoint);
            }
        }
    }

    public static boolean isRadiusWithinJurisdiction(List<JurisdictionMarkerDTO> jurisdictionMarkers, double latitude, double longitude, double radius) {
        List<Pair<Double, Double>> samples = LocationUtils.getSamplesOfCircle(latitude, longitude, radius, Math.max(radius / 10, 1));
        boolean allSamplesInsideJurisdiction = samples.stream().allMatch(
                s -> jurisdictionMarkers.stream().anyMatch(m -> LocationUtils.distance(s.getFirst(), m.getLatitude(), s.getSecond(), m.getLongitude()) <= m.getRadiusMeter())
        );
        return allSamplesInsideJurisdiction;
    }

    private static Pair<Double, Double> calculatePoint(double centerLatRad, double centerLongRad, double radiusMeters, double theta) {
        // Convert the radius from meters to angular distance
        double angularDistance = radiusMeters / EARTH_RADIUS_M;

        // Calculate new latitude
        double newLatRad = Math.asin(Math.sin(centerLatRad) * Math.cos(angularDistance) +
                Math.cos(centerLatRad) * Math.sin(angularDistance) * Math.cos(theta));

        // Calculate new longitude
        double newLongRad = centerLongRad + Math.atan2(Math.sin(theta) * Math.sin(angularDistance) * Math.cos(centerLatRad),
                Math.cos(angularDistance) - Math.sin(centerLatRad) * Math.sin(newLatRad));

        // Convert radians back to degrees
        double newLatDeg = Math.toDegrees(newLatRad);
        double newLongDeg = Math.toDegrees(newLongRad);

        return new Pair(newLatDeg, newLongDeg);
    }
}
