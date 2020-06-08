package tools;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Pair;

import rcr.Vector;

public class AreaTools {
	
	public static List<Line2D> getSegments(Way w) {
		List<Line2D> result = new ArrayList<>();
		for (Pair<Node, Node> p : w.getNodePairs(false)) {
			result.add(new Line2D.Double(Vector.asPoint(p.a.getEastNorth()), Vector.asPoint(p.b.getEastNorth())));
		}
		return result;
	}
	
	public static boolean lineContainedInPolygon(Line2D l, Way w) {
//		System.out.println("check line " + l.getP1() + " -- " + l.getP2() + " against way " + w);
		if (lineIntersectsWay(l, w)) {
			return false;
		}
		Point2D center = Vector.interpolate(l.getP1(), l.getP2(), 0.5);
//		System.out.println("center: " + center);
		return pointInPolygon(center, w, false);		
	}
	
	public static Rectangle2D getEastNorthBounds(OsmPrimitive osm) {
		EastNorth topLeft = Main.getProjection().latlon2eastNorth(
				osm.getBBox().getTopLeft());
		EastNorth bottomRight = Main.getProjection().latlon2eastNorth(
				osm.getBBox().getBottomRight());
		double x = topLeft.getX();
		double y = bottomRight.getY();
		double w = bottomRight.getX() - topLeft.getX();
		double h = topLeft.getY() - bottomRight.getY();
		return new Rectangle2D.Double(x, y, w, h);		
	}
	
	public static boolean waysIntersect(Way w1, Way w2) {
		for (Line2D l : getSegments(w1)) {
			if (lineIntersectsWay(l, w2)) {
				return true;
			}
		}
		return false;
	}

	public static boolean lineIntersectsWay(Line2D l, Way w) {
//		System.out.println("check line " + l.getP1() + " -- " + l.getP2() + " against way " + w);
		for (Line2D l2 : getSegments(w)) {
//			System.out.println("     segment " + l2.getP1() + " -- " + l2.getP2());
			if (!Vector.pointOnLine(l.getP1(), l2.getP1(), l2.getP2())
					&& !Vector.pointOnLine(l.getP2(), l2.getP1(), l2.getP2())) {
//				System.out.println("     check intersection");
				if (l.intersectsLine(l2)) {
//					System.out.println("         true");
					return true;
				}
			}
		}
		return false;
	}
	
	public static boolean pointInPolygon(Point2D p, Way w, boolean includeOutline) {
		assert w.isArea();
		
		boolean isInside = false;
		
		for (Line2D l : getSegments(w)) {
//			System.out.println( "  segment: " + l.getP1() + " -- " + l.getP2());
			if (l.ptSegDist(p) < Vector.epsilon) {
				// point is on the outline
//				System.out.println("On outline");
				return includeOutline;
			}
			if (Math.abs(l.getY1() - l.getY2()) < Vector.epsilon) {
//				System.out.println("  skip horizontal");
				continue;
			}
			double x1 = l.getX1();
			double x2 = l.getX2();
			double y1 = l.getY1();
			double y2 = l.getY2();
			if (y1 > y2) {
				// make sure p1 is the bottom one 
				x1 = l.getX2(); x2 = l.getX1();
				y1 = l.getY2(); y2 = l.getY1();
			}
//			System.out.printf("  x1: %f, x2: %f, y1: %f, y2: %f\n", x1, x2, y1, y2);
			// Move points that are on the same Y coordinate as the test point
			if (Math.abs(y1 - p.getY()) < Vector.epsilon) {
				y1 += 0.01;
			}
			if (Math.abs(y2 - p.getY()) < Vector.epsilon) {
				y2 += 0.01;
			}
			
			// check for intersection
			if (y1 < p.getY() && y2 > p.getY()) {
				double m = (y2 - y1) / (x2 - x1);
				double x = x1 + (p.getY() - y1)/m;
//				System.out.printf("  m=%f, x=%f\n", m, x);
				if (x >= p.getX()) {
//					System.out.println("  hit!");
					isInside = !isInside;
				}
			}			
		}
		return isInside;
	}
}
