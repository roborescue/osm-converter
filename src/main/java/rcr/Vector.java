/*
 * Copyright (c) 2009, Research Group on the Foundations of Artificial
 * Intelligence, Department of Computer Science, Albert-Ludwigs University
 * Freiburg. All rights reserved. Redistribution and use in source and binary
 * forms, with or without modification, are permitted provided that the
 * following conditions are met: Redistributions of source code must retain the
 * above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution. The name of the author
 * may not be used to endorse or promote products derived from this software
 * without specific prior written permission. THIS SOFTWARE IS PROVIDED BY THE
 * AUTHOR "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package rcr;

import java.awt.Point;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.Collection;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;

public class Vector {

  public static boolean debug   = false;
  public static double  epsilon = 1.0E-6;


  @SuppressWarnings( "unchecked" )
  private static <T extends Point2D> T makePoint( T prototype, double x,
      double y ) {
    Class<T> type = (Class<T>) prototype.getClass();
    // if (type.equals(LatLon.class) || type.equals(CachedLatLon.class))
    // return (T) new LatLon(y,x);
    // else if (type.equals(EastNorth.class))
    // return (T) new EastNorth(x, y);
    if ( type.equals( Point.class ) )
      return (T) new Point( (int) x, (int) y );
    else if ( type.equals( Point2D.Double.class ) )
      return (T) new Point2D.Double( x, y );
    return null;
  }


  public static Point2D asPoint( LatLon p ) {
    return new Point2D.Double( p.getX(), p.getY() );
  }


  public static Point2D asPoint( EastNorth p ) {
    return new Point2D.Double( p.getX(), p.getY() );
  }


  public static LatLon asLatLon( Point2D p ) {
    return new LatLon( p.getY(), p.getX() );
  }


  public static EastNorth asEastNorth( Point2D p ) {
    return new EastNorth( p.getX(), p.getY() );
  }


  public static Point2D fromLine( Line2D line ) {
    return diff( line.getP2(), line.getP1() );
  }


  public static <T extends Point2D> T diff( T p1, T p2 ) {
    return makePoint( p1, p1.getX() - p2.getX(), p1.getY() - p2.getY() );
  }


  public static LatLon diff( LatLon p1, LatLon p2 ) {
    return new LatLon( p1.lat() - p2.lat(), p1.lon() - p2.lon() );
  }


  public static <T extends Point2D> T sum( T p1, T p2 ) {
    return makePoint( p1, p1.getX() + p2.getX(), p1.getY() + p2.getY() );
  }


  public static double length( Point2D p ) {
    return Math.hypot( p.getX(), p.getY() );
  }


  public static <T extends Point2D> T times( T p1, double s ) {
    return makePoint( p1, s * p1.getX(), s * p1.getY() );
  }


  public static <T extends Point2D> double dotProduct( T p1, T p2 ) {
    return p1.getX() * p2.getX() + p1.getY() * p2.getY();
  }


  public static double dotProduct( LatLon p1, LatLon p2 ) {
    return dotProduct( asPoint( p1 ), asPoint( p2 ) );
  }


  public static <T extends Point2D> double dotProduct( T from1, T to1, T from2,
      T to2 ) {
    double dx1 = to1.getX() - from1.getX();
    double dy1 = to1.getY() - from1.getY();
    double dx2 = to2.getX() - from2.getX();
    double dy2 = to2.getY() - from2.getY();

    return dx1 * dx2 + dy1 * dy2;
  }


  public static <T extends Point2D> T normalize( T p ) {
    double len = length( p );
    return makePoint( p, p.getX() / len, p.getY() / len );
  }


  public static LatLon normalize( LatLon p ) {
    return asLatLon( normalize( asPoint( p ) ) );
  }


  public static <T extends Point2D> T normalVec( T p ) {
    double len = length( p );
    return makePoint( p, -p.getY() / len, p.getX() / len );
  }


  public static <T extends Point2D> double dotPNorm( T p1, T p2 ) {
    double len1 = length( p1 );
    double len2 = length( p2 );
    return ( p1.getX() * p2.getX() + p1.getY() * p2.getY() ) / ( len1 * len2 );
  }


  public static double dotPNorm( LatLon p1, LatLon p2 ) {
    return dotPNorm( asPoint( p1 ), asPoint( p2 ) );
  }


  public static <T extends Point2D> double dotPNorm( T from, T to1, T to2 ) {
    double len1 = from.distance( to1 );
    double len2 = from.distance( to2 );

    return dotProduct( from, to1, from, to2 ) / ( len1 * len2 );
  }


  public static <T extends Point2D> T interpolate( T from, T to,
      double ratio ) {
    double x = ( 1 - ratio ) * from.getX() + ratio * to.getX();
    double y = ( 1 - ratio ) * from.getY() + ratio * to.getY();
    return makePoint( from, x, y );
  }


  public static <T extends Point2D> T offset( T p1, T p2, double dist ) {
    return times( normalVec( diff( p2, p1 ) ), dist );
  }


  public static <T extends Point2D> double angle( T p ) {
    if ( p.getX() == 0 ) {
      if ( p.getY() > 0 )
        return Math.PI / 2;
      else if ( p.getY() < 0 ) return 3 * Math.PI / 2;
      return Double.NaN;
    }
    double a = Math.atan( p.getY() / p.getX() );
    if ( a < 0 ) a += 2 * Math.PI;
    return a;
  }


  public static <T extends Point2D> T interpolate2D( T bl, T tl, T br, T tr,
      double xRatio, double yRatio ) {
    T dy0 = interpolate( bl, tl, yRatio );
    T dy1 = interpolate( br, tr, yRatio );
    return interpolate( dy0, dy1, xRatio );
  }


  public static boolean nearZero( Point2D p ) {
    return Math.abs( p.getX() ) < epsilon && Math.abs( p.getY() ) < epsilon;
  }


  public static boolean nearEqual( Point2D p1, Point2D p2 ) {
    return Math.abs( p1.getX() - p2.getX() ) < epsilon
        && Math.abs( p1.getY() - p2.getY() ) < epsilon;
  }


  public static boolean valid( Point2D p ) {
    return !Double.isNaN( p.getX() ) && !Double.isNaN( p.getY() );
  }


  public static <T extends Point2D> T projection( T v1, T v2 ) {
    double len1 = length( v1 );
    if ( len1 < epsilon ) {
      System.err.println( "Trying to project on zero-length way" );
      return makePoint( v1, 0, 0 );
    }

    double dotp = dotProduct( v1, v2 );
    double projLenRatio = dotp / ( len1 * len1 );

    return makePoint( v1, projLenRatio * v1.getX(), projLenRatio * v1.getY() );
  }


  public static <T extends Point2D> T projection( T from, T to1, T to2 ) {
    double len1 = from.distance( to1 );
    if ( len1 < epsilon ) {
      System.err.println( "Trying to project on zero-length way" );
      return makePoint( from, Double.NaN, Double.NaN );
    }

    double dotp = dotProduct( from, to1, from, to2 );
    double projLenRatio = dotp / ( len1 * len1 );

    T d = diff( to1, from );
    double x = from.getX() + projLenRatio * d.getX();
    double y = from.getY() + projLenRatio * d.getY();

    return makePoint( from, x, y );
  }


  public static LatLon projection( LatLon from, LatLon to1, LatLon to2 ) {
    return asLatLon(
        projection( asPoint( from ), asPoint( to1 ), asPoint( to2 ) ) );
  }


  public static <T extends Point2D> T orthoIntersection( T from, T to, T p ) {
    double ldist = dotProduct( from, to, from, p ) / from.distance( to ); // Distance
                                                                          // of
                                                                          // the
                                                                          // ortho
                                                                          // intersection
                                                                          // from
                                                                          // "from"
    if ( ldist < 0 || ldist > from.distance( to ) )
      return makePoint( p, Double.NaN, Double.NaN );
    T intersection = sum( from, times( normalize( diff( to, from ) ), ldist ) );
    return intersection;
  }


  public static <T extends Point2D> double orthoDistance( T from, T to1,
      T to2 ) {
    T proj = projection( from, to1, to2 );
    if ( !valid( proj ) ) return Double.NaN;

    double projDist = from.distance( proj );
    double normalDist = from.distance( to1 );
    // The roads go in different directions
    if ( ( proj.distance( to1 ) >= from.distance( to1 ) )
        && ( proj.distance( to1 ) >= proj.distance( from ) ) ) {
      return Double.MAX_VALUE;
    }

    if ( normalDist >= projDist ) {
      return proj.distance( to2 );
    }

    proj = projection( from, to2, to1 );
    if ( !valid( proj ) ) {
      assert false : "unreachable";
      return Double.NaN;
    }
    return proj.distance( to1 );
  }


  public static boolean intersect( Point2D s1, Point2D e1, Point2D s2,
      Point2D e2 ) {
    return Line2D.linesIntersect( s1.getX(), s1.getY(), e1.getX(), e1.getY(),
        s2.getX(), s2.getY(), e2.getX(), e2.getY() );
  }


  public static <T extends Point2D> boolean pointOnLine( T p, T from, T to ) {
    // we effectively use the maximum-norm as it's cheaper to compute and
    // shouldn't matter much when checking for points *on* the line
    T v1 = diff( to, from );
    T v2 = diff( p, from );
    // normalize to top right quadrant
    double x1 = v1.getX() < 0 ? -v1.getX() : v1.getX();
    double y1 = v1.getY() < 0 ? -v1.getY() : v1.getY();
    double x2 = v1.getX() < 0 ? -v2.getX() : v2.getX();
    double y2 = v1.getY() < 0 ? -v2.getY() : v2.getY();
    if ( debug )
      System.out.printf( "                   x1=%f, x2=%f, y1=%f, y2=%f\n", x1,
          x2, y1, y2 );

    // bbox check
    if ( y2 < -epsilon || y2 - y1 > epsilon || x2 < -epsilon
        || x2 - x1 > epsilon ) {
      // System.out.println(" " + "bbox fail");
      return false;
    }
    // vertical line
    if ( x1 < epsilon ) {
      // System.out.println(" " + "vertical:" + (Math.abs(x2) < epsilon));
      return Math.abs( x2 ) < epsilon;
    }

    double m1 = y1 / x1;
    double dy = y2 - m1 * x2;
    if ( debug )
      System.out.printf( "                   m1=%f, dy=%f\n", m1, dy );

    return Math.abs( dy ) < epsilon;
  }


  public static <T extends Point2D> double getIntersection( T p1, T p2, T p3,
      T p4 ) {
    T dir1 = Vector.diff( p2, p1 );
    T dir2 = Vector.diff( p4, p3 );
    double bxax = dir1.getX();
    double dycy = dir2.getY();
    double byay = dir1.getY();
    double dxcx = dir2.getX();
    // System.out.printf("bxax: %f, dycy: %f, byay: %f, dxcx: %f\n", bxax, dycy,
    // byay, dxcx);
    double cxax = p3.getX() - p1.getX();
    double cyay = p3.getY() - p1.getY();
    // System.out.printf("cxax: %f, cyay: %f\n", cxax, cyay);
    double d = ( bxax * dycy ) - ( byay * dxcx );
    double t = ( cxax * dycy ) - ( cyay * dxcx );
    // System.out.printf("d: %f, t: %f\n", d, t);
    if ( Math.abs( d ) < epsilon ) {
      // d is close to zero: lines are parallel so no intersection
      return Double.NaN;
    }
    return t / d;

  }


  public static <T extends Point2D> T getIntersectionPoint( T p1, T p2, T p3,
      T p4 ) {
    double dist = getIntersection( p1, p2, p3, p4 );
    if ( Double.isNaN( dist ) || dist < 0.0 || dist > 1.0 ) {
      return null;
    }
    double dist2 = getIntersection( p3, p4, p1, p2 );
    if ( Double.isNaN( dist2 ) || dist2 < 0.0 || dist2 > 1.0 ) {
      return null;
    }
    return interpolate( p1, p2, dist );
  }


  public static <T extends Point2D> T average( Collection<T> points ) {
    if ( points.isEmpty() ) return null;

    double sumX = 0;
    double sumY = 0;

    T prototypePoint = null;
    for ( T p : points ) {
      prototypePoint = p;
      sumX += p.getX();
      sumY += p.getY();
    }
    return makePoint( prototypePoint, sumX / points.size(),
        sumY / points.size() );

  }


  public static Point2D.Double P2DfromArray( double[] p ) {
    return new Point2D.Double( p[0], p[1] );
  }


  public static double[] ArrayFromP2D( Point2D.Double p ) {
    return new double[]{ p.getX(), p.getY() };
  }

}
