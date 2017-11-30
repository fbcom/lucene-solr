/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.spatial3d.geom;

import java.util.List;
import java.util.ArrayList;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Circular area with a center and radius.
 *
 * @lucene.experimental
 */
class GeoExactCircle extends GeoBaseCircle {
  /** Center of circle */
  protected final GeoPoint center;
  /** Cutoff angle of circle (not quite the same thing as radius) */
  protected final double cutoffAngle;
  /** Actual accuracy */
  protected final double actualAccuracy;

  /** A point that is on the world and on the circle plane */
  protected final GeoPoint[] edgePoints;

  /** Notable points for a circle -- there aren't any */
  protected static final GeoPoint[] circlePoints = new GeoPoint[0];

  /** Slices of the circle. */
  protected final List<CircleSlice> circleSlices;

  /** Constructor.
   *@param planetModel is the planet model.
   *@param lat is the center latitude.
   *@param lon is the center longitude.
   *@param cutoffAngle is the surface radius for the circle.
   *@param accuracy is the allowed error value (linear distance).
   */
  public GeoExactCircle(final PlanetModel planetModel, final double lat, final double lon, final double cutoffAngle, final double accuracy) {
    super(planetModel);
    if (lat < -Math.PI * 0.5 || lat > Math.PI * 0.5)
      throw new IllegalArgumentException("Latitude out of bounds");
    if (lon < -Math.PI || lon > Math.PI)
      throw new IllegalArgumentException("Longitude out of bounds");
    if (cutoffAngle < 0.0)
      throw new IllegalArgumentException("Cutoff angle out of bounds");
    if (cutoffAngle < Vector.MINIMUM_RESOLUTION)
      throw new IllegalArgumentException("Cutoff angle cannot be effectively zero");
    
    this.center = new GeoPoint(planetModel, lat, lon);
    this.cutoffAngle = cutoffAngle;

    if (accuracy < Vector.MINIMUM_RESOLUTION) {
      actualAccuracy = Vector.MINIMUM_RESOLUTION;
    } else {
      actualAccuracy = accuracy;
    }
    
    // We construct approximation planes until we have a low enough error estimate
    final List<ApproximationSlice> slices = new ArrayList<>(100);
    // Construct four cardinal points, and then we'll build the first two planes
    final GeoPoint northPoint = planetModel.surfacePointOnBearing(center, cutoffAngle, 0.0);
    final GeoPoint southPoint = planetModel.surfacePointOnBearing(center, cutoffAngle, Math.PI);
    final GeoPoint eastPoint = planetModel.surfacePointOnBearing(center, cutoffAngle, Math.PI * 0.5);
    final GeoPoint westPoint = planetModel.surfacePointOnBearing(center, cutoffAngle, Math.PI * 1.5);
    
    final GeoPoint edgePoint;
    if (planetModel.c > planetModel.ab) {
      // z can be greater than x or y, so ellipse is longer in height than width
      slices.add(new ApproximationSlice(center, eastPoint, Math.PI * 0.5, westPoint, Math.PI * -0.5, northPoint, 0.0));
      slices.add(new ApproximationSlice(center, westPoint, Math.PI * 1.5, eastPoint, Math.PI * 0.5, southPoint, Math.PI));
      edgePoint = eastPoint;
    } else {
      // z will be less than x or y, so ellipse is shorter than it is tall
      slices.add(new ApproximationSlice(center, northPoint, 0.0, southPoint, Math.PI, eastPoint, Math.PI * 0.5));
      slices.add(new ApproximationSlice(center, southPoint, Math.PI, northPoint, Math.PI * 2.0, westPoint, Math.PI * 1.5));
      edgePoint = northPoint;
    }
    //System.out.println("Edgepoint = " + edgePoint);

    this.circleSlices = new ArrayList<>();
    
    // Now, iterate over slices until we have converted all of them into safe SidedPlanes.
    while (slices.size() > 0) {
      // Peel off a slice from the back
      final ApproximationSlice thisSlice = slices.remove(slices.size()-1);
      // Assess it to see if it is OK as it is, or needs to be split.
      // To do this, we need to look at the part of the circle that will have the greatest error.
      // We will need to compute bearing points for these.
      final double interpPoint1Bearing = (thisSlice.point1Bearing + thisSlice.middlePointBearing) * 0.5;
      final GeoPoint interpPoint1 = planetModel.surfacePointOnBearing(center, cutoffAngle, interpPoint1Bearing);
      final double interpPoint2Bearing = (thisSlice.point2Bearing + thisSlice.middlePointBearing) * 0.5;
      final GeoPoint interpPoint2 = planetModel.surfacePointOnBearing(center, cutoffAngle, interpPoint2Bearing);
      
      // Is this point on the plane? (that is, is the approximation good enough?)
      if (Math.abs(thisSlice.plane.evaluate(interpPoint1)) < actualAccuracy && Math.abs(thisSlice.plane.evaluate(interpPoint2)) < actualAccuracy) {
        circleSlices.add(new CircleSlice(thisSlice.plane, thisSlice.endPoint1, thisSlice.endPoint2, center, thisSlice.middlePoint));
        //assert thisSlice.plane.isWithin(center);
      } else {
        // Split the plane into two, and add it back to the end
        slices.add(new ApproximationSlice(center,
          thisSlice.endPoint1, thisSlice.point1Bearing, 
          thisSlice.middlePoint, thisSlice.middlePointBearing, 
          interpPoint1, interpPoint1Bearing));
        slices.add(new ApproximationSlice(center,
          thisSlice.middlePoint, thisSlice.middlePointBearing,
          thisSlice.endPoint2, thisSlice.point2Bearing,
          interpPoint2, interpPoint2Bearing));
      }
    }
    
    this.edgePoints = new GeoPoint[]{edgePoint};

    //System.out.println("Is edgepoint within? "+isWithin(edgePoint));
  }


  /**
   * Constructor for deserialization.
   * @param planetModel is the planet model.
   * @param inputStream is the input stream.
   */
  public GeoExactCircle(final PlanetModel planetModel, final InputStream inputStream) throws IOException {
    this(planetModel, 
      SerializableObject.readDouble(inputStream),
      SerializableObject.readDouble(inputStream),
      SerializableObject.readDouble(inputStream),
      SerializableObject.readDouble(inputStream));
  }

  @Override
  public void write(final OutputStream outputStream) throws IOException {
    SerializableObject.writeDouble(outputStream, center.getLatitude());
    SerializableObject.writeDouble(outputStream, center.getLongitude());
    SerializableObject.writeDouble(outputStream, cutoffAngle);
    SerializableObject.writeDouble(outputStream, actualAccuracy);
  }

  @Override
  public double getRadius() {
    return cutoffAngle;
  }

  @Override
  public GeoPoint getCenter() {
    return center;
  }

  @Override
  protected double distance(final DistanceStyle distanceStyle, final double x, final double y, final double z) {
    return distanceStyle.computeDistance(this.center, x, y, z);
  }

  @Override
  protected void distanceBounds(final Bounds bounds, final DistanceStyle distanceStyle, final double distanceValue) {
    // TBD: Compute actual bounds based on distance
    getBounds(bounds);
  }

  @Override
  protected double outsideDistance(final DistanceStyle distanceStyle, final double x, final double y, final double z) {
    if (circleSlices.size() == 0) {
      return 0.0;
    }
    if (circleSlices.size() == 1) {
      return distanceStyle.computeDistance(planetModel, circleSlices.get(0).circlePlane, x, y, z);
    }
    double outsideDistance = Double.POSITIVE_INFINITY;
    for (final CircleSlice slice : circleSlices) {
      final double distance = distanceStyle.computeDistance(planetModel, slice.circlePlane, x, y, z, slice.plane1, slice.plane2);
      if (distance < outsideDistance) {
        outsideDistance = distance;
      }
    }
    return outsideDistance;
  }

  @Override
  public boolean isWithin(final double x, final double y, final double z) {
    if (circleSlices.size() == 0) {
      return true;
    }
    for (final CircleSlice slice : circleSlices) {
      if (slice.circlePlane.isWithin(x, y, z) && slice.plane1.isWithin(x, y, z) && slice.plane2.isWithin(x, y, z)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public GeoPoint[] getEdgePoints() {
    return edgePoints;
  }

  @Override
  public boolean intersects(final Plane p, final GeoPoint[] notablePoints, final Membership... bounds) {
    if (circleSlices.size() == 0) {
      return false;
    }
    if (circleSlices.size() == 1) {
      return circleSlices.get(0).circlePlane.intersects(planetModel, p, notablePoints, circlePoints, bounds);
    }
    for (final CircleSlice slice : circleSlices) {
      if (slice.circlePlane.intersects(planetModel, p, notablePoints, slice.notableEdgePoints, bounds, slice.plane1, slice.plane2)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean intersects(GeoShape geoShape) {
    if (circleSlices.size() == 0) {
      return false;
    }
    if (circleSlices.size() == 1) {
      return geoShape.intersects(circleSlices.get(0).circlePlane, circlePoints);
    }

    for (final CircleSlice slice : circleSlices) {
      if (geoShape.intersects(slice.circlePlane, slice.notableEdgePoints, slice.plane1, slice.plane2)) {
        return true;
      }
    }
    return false;
  }


  @Override
  public void getBounds(Bounds bounds) {
    super.getBounds(bounds);
    if (circleSlices.size() == 0) {
      return;
    }
    bounds.addPoint(center);
    if (circleSlices.size() == 1) {
      bounds.addPlane(planetModel, circleSlices.get(0).circlePlane);
      return;
    }
    for (final CircleSlice slice : circleSlices) {
      bounds.addPlane(planetModel, slice.circlePlane, slice.plane1, slice.plane2);
      for (final GeoPoint point : slice.notableEdgePoints) {
        bounds.addPoint(point);
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof GeoExactCircle))
      return false;
    GeoExactCircle other = (GeoExactCircle) o;
    return super.equals(other) && other.center.equals(center) && other.cutoffAngle == cutoffAngle && other.actualAccuracy == actualAccuracy;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + center.hashCode();
    long temp = Double.doubleToLongBits(cutoffAngle);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(actualAccuracy);
    result = 31 * result + (int) (temp ^ (temp >>> 32));    
    return result;
  }

  @Override
  public String toString() {
    return "GeoExactCircle: {planetmodel=" + planetModel+", center=" + center + ", radius=" + cutoffAngle + "(" + cutoffAngle * 180.0 / Math.PI + "), accuracy=" + actualAccuracy + "}";
  }
  
  /** A temporary description of a section of circle.
   */
  protected static class ApproximationSlice {
    public final SidedPlane plane;
    public final GeoPoint endPoint1;
    public final double point1Bearing;
    public final GeoPoint endPoint2;
    public final double point2Bearing;
    public final GeoPoint middlePoint;
    public final double middlePointBearing;
    
    public ApproximationSlice(final GeoPoint center,
      final GeoPoint endPoint1, final double point1Bearing,
      final GeoPoint endPoint2, final double point2Bearing,
      final GeoPoint middlePoint, final double middlePointBearing) {
      this.endPoint1 = endPoint1;
      this.point1Bearing = point1Bearing;
      this.endPoint2 = endPoint2;
      this.point2Bearing = point2Bearing;
      this.middlePoint = middlePoint;
      this.middlePointBearing = middlePointBearing;
      // Construct the plane going through the three given points
      this.plane = SidedPlane.constructNormalizedThreePointSidedPlane(center, endPoint1, endPoint2, middlePoint);
      if (this.plane == null) {
        throw new IllegalArgumentException("Either circle is too large to fit on ellipsoid or accuracy is too high; could not construct a plane with endPoint1="+endPoint1+" bearing "+point1Bearing+", endPoint2="+endPoint2+" bearing "+point2Bearing+", middle="+middlePoint+" bearing "+middlePointBearing);
      }
      if (plane.isWithin(center) == false || !plane.evaluateIsZero(endPoint1) || !plane.evaluateIsZero(endPoint2) || !plane.evaluateIsZero(middlePoint))
        throw new IllegalStateException("SidedPlane constructor built a bad plane!!");
    }

    @Override
    public String toString() {
      return "{end point 1 = " + endPoint1 + " bearing 1 = "+point1Bearing + 
        " end point 2 = " + endPoint2 + " bearing 2 = " + point2Bearing + 
        " middle point = " + middlePoint + " middle bearing = " + middlePointBearing + "}";
    }

  }

  /** A  description of a section of circle.
   */
  protected static class CircleSlice {
    final GeoPoint[] notableEdgePoints;
    public final SidedPlane circlePlane;
    public final SidedPlane plane1;
    public final SidedPlane plane2;

    public CircleSlice(SidedPlane circlePlane, GeoPoint endPoint1, GeoPoint endPoint2, GeoPoint center, GeoPoint check) {
      this.circlePlane = circlePlane;
      this.plane1 = new SidedPlane(check, endPoint1, center);
      this.plane2 = new SidedPlane(check, endPoint2, center);
      this.notableEdgePoints = new GeoPoint[] {endPoint1, endPoint2};
    }

    @Override
    public String toString() {
      return "{circle plane = " + circlePlane + " plane 1 = "+plane1 +
          " plane 2 = " + plane2 + " notable edge points = " + notableEdgePoints  + "}";
    }
  }



}
