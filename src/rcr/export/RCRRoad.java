package rcr.export;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import maps.gml.GMLNode;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

import rcr.Vector;

public class RCRRoad implements GMLExporter.Edge {
	/**
	 * 
	 */
	private final GMLExporter exporter;
	Node head;
	Node tail;
	Way way;
	double width;
	private GMLNode headLeft = null;
	private GMLNode headRight = null;
	private GMLNode tailLeft = null;
	private GMLNode tailRight = null;
	private List<GMLNode> headApexes = null;
	private List<GMLNode> tailApexes = null;

	public RCRRoad(Node head, Node tail, Way w, double width, GMLExporter gmlExporter) {
		this.exporter = gmlExporter;
		this.head = head;
		this.tail = tail;
		this.width = width;
		this.way = w;
	}

	public Line2D asLine() {
		return asLine(head);
	}

	public Line2D asLine(Node origin) {
		return new Line2D.Double(Vector.asPoint(origin.getEastNorth()),
				Vector.asPoint(getOtherNode(origin).getEastNorth()));
	}

	public void setLeft(Node n, GMLNode gn) {
		if (n == head) {
			this.headLeft = gn;
		} else if (n == tail) {
			this.tailRight = gn;
		} else {
			assert false;
		}
	}

	public void setRight(Node n, GMLNode gn) {
		if (n == head) {
			this.headRight = gn;
		} else if (n == tail) {
			this.tailLeft = gn;
		} else {
			assert false;
		}
	}

	public void setEndApexes(Node n, List<GMLNode> apexes) {
		if (n == head) {
			this.headApexes = apexes;
		} else if (n == tail) {
			this.tailApexes = apexes;
		} else {
			assert false;
		}
	}

	public Node getOtherNode(Node n) {
		if (n == head) {
			return tail;
		}
		if (n == tail) {
			return head;
		}
		assert false;
		return null;
	}

	public Line2D offsetLine(double offset) {
		return offsetLine(offset, head);
	}

	public Line2D offsetLine(double offset, Node originNode) {
		assert (originNode == head || originNode == tail);
		Point2D origin = Vector.asPoint(originNode.getEastNorth());
		Point2D dir = Vector.diff(
				Vector.asPoint(getOtherNode(originNode).getEastNorth()),
				origin);
		Point2D normal = Vector.normalVec(dir);
		Point2D offsetOrigin = Vector.sum(origin,
				Vector.times(normal, offset));

		return new Line2D.Double(offsetOrigin,
				Vector.sum(offsetOrigin, dir));
	}

	public Line2D offsetLine(double offsetHead, double offsetTail,
			Node originNode) {
		assert (originNode == head || originNode == tail);
		Point2D p1 = Vector.asPoint(originNode.getEastNorth());
		Point2D p2 = Vector
				.asPoint(getOtherNode(originNode).getEastNorth());
		Point2D normal = Vector.normalVec(Vector.diff(p2, p1));

		return new Line2D.Double(Vector.sum(p1,
				Vector.times(normal, offsetHead)), Vector.sum(p2,
				Vector.times(normal, offsetTail)));
	}

	public List<Line2D> getOutline() {
		List<Line2D> result = new ArrayList<>();
		Point2D hl = headLeft != null ? new Point2D.Double(headLeft.getX(),
				headLeft.getY()) : Vector.asPoint(head.getEastNorth());
		Point2D hr = headRight != null ? new Point2D.Double(
				headRight.getX(), headRight.getY()) : Vector.asPoint(head
				.getEastNorth());
		Point2D tl = tailLeft != null ? new Point2D.Double(tailLeft.getX(),
				tailLeft.getY()) : Vector.asPoint(tail.getEastNorth());
		Point2D tr = tailRight != null ? new Point2D.Double(
				tailRight.getX(), tailRight.getY()) : Vector.asPoint(tail
				.getEastNorth());
		if (headApexes != null && headApexes.size() >= 2) {
			Point2D prev = null;
			for (GMLNode n : headApexes) {
				Point2D p = new Point2D.Double(n.getX(), n.getY());
				if (prev != null) {
					result.add(new Line2D.Double(prev, p));
				}
				prev = p;
			}
		} else if (hr != hl) {
			result.add(new Line2D.Double(hr, hl));
		}

		if (hl != tl) {
			result.add(new Line2D.Double(hl, tl));
		}

		if (tailApexes != null && tailApexes.size() >= 2) {
			Point2D prev = null;
			for (GMLNode n : tailApexes) {
				Point2D p = new Point2D.Double(n.getX(), n.getY());
				if (prev != null) {
					result.add(new Line2D.Double(prev, p));
				}
				prev = p;
			}
		} else if (tl != tr) {
			result.add(new Line2D.Double(tl, tr));
		}

		if (tr != hr) {
			result.add(new Line2D.Double(tr, hr));
		}

		return result;
	}

	public List<GMLNode> getApexes() {
		GMLNode hr = headRight != null ? headRight : exporter.node2GML(head);
		GMLNode hl = headLeft != null ? headLeft : exporter.node2GML(head);
		GMLNode tl = tailLeft != null ? tailLeft : exporter.node2GML(tail);
		GMLNode tr = tailRight != null ? tailRight : exporter.node2GML(tail);

		List<GMLNode> result = new ArrayList<>();
		if (headApexes != null && headApexes.size() >= 2) {
			result.addAll(headApexes);
		} else if (hr != hl) {
			result.add(hr);
			result.add(hl);
		} else {
			result.add(hl);
		}

		if (tailApexes != null && tailApexes.size() >= 2) {
			result.addAll(tailApexes);
		} else if (tl != tr) {
			result.add(tl);
			result.add(tr);
		} else {
			result.add(tr);
		}

		return result;
	}

	public Point2D intersect(RCRRoad other) {
		// Assume *other* is oriented clockwise from *this*
		Node origin = null;
		if (this.head == other.head || this.head == other.tail) {
			origin = this.head;
		} else if (this.tail == other.head || this.tail == other.tail) {
			origin = this.tail;
		} else {
			assert false;
		}

		// double averageWidth = (this.width + other.width) / 2;
		// Line2D l1 = this.offsetLine(-averageWidth/2000, -this.width/2000,
		// origin);
		// Line2D l2 = other.offsetLine(averageWidth/2000, other.width/2000,
		// origin);

		Line2D l1 = null;
		Line2D l2 = null;
		if (this.width >= other.width) {
			l1 = this.offsetLine(-this.width / 2000, origin);
			l2 = other.offsetLine(other.width / 2000, origin);
		} else {
			l1 = other.offsetLine(other.width / 2000, origin);
			l2 = this.offsetLine(-this.width / 2000, origin);
		}
		Point2D dir1 = Vector.fromLine(l1);
		Point2D dir2 = Vector.fromLine(l2);

		// Roads are (close to) parallel, so just average the endpoints
		if (Math.abs(Vector.dotPNorm(dir1, dir2)) >= 0.9 - Vector.epsilon) {
			Point2D diff = Vector.diff(l2.getP1(), l1.getP1());
			return Vector.sum(l1.getP1(), Vector.times(diff, 0.5));
			// return l1.getP1();
		}

		// Compute the distance of the intersection from the origin along
		// the lines
		// Distances are normalized, so 1.0 is at the far end of the line(s)
		double dist1 = Vector.getIntersection(l1.getP1(), l1.getP2(),
				l2.getP1(), l2.getP2());
		double dist2 = Vector.getIntersection(l2.getP1(), l2.getP2(),
				l1.getP1(), l1.getP2());
		if (!Double.isNaN(dist1) && !Double.isNaN(dist2)) {
			// Degenerate case, use intersection of the *caps* as new point
			if (dist1 > 1.0 && dist2 > 1.0) {
				Point2D normal1 = Vector.normalVec(dir1);
				Point2D normal2 = Vector.normalVec(dir2);
				double dist3 = Vector.getIntersection(l1.getP2(),
						Vector.sum(l1.getP2(), normal1), l2.getP2(),
						Vector.sum(l2.getP2(), normal2));
				assert !Double.isNaN(dist3);
				return Vector.sum(l1.getP2(), Vector.times(normal2, dist3));
			}

			double distCap = 2 * Math.max(this.width / 2000,
					other.width / 1000);
			// Don't go beyond the far end of r2
			if (dist2 >= 1.0) {
				dist2 = Math.min(dist2, 1.0);
				// Limit intersection distance to 2 * max road width
				double realDist = Vector.length(dir2) * dist2;
				dist2 = Math.min(dist2, distCap / realDist);
				return Vector.sum(l2.getP1(), Vector.times(dir2, dist2));
			}

			// Don't go beyond the far end of r1
			dist1 = Math.min(dist1, 1.0);
			// Limit intersection distance to 2 * max road width (and don't
			// go beyond
			if (dist2 <= 1.0 && Math.abs(dist1) > Vector.epsilon) {
				double sign = Math.signum(dist1);
				dist1 = Math.abs(dist1);
				double realDist = Vector.length(dir1) * dist1;
				dist1 = Math.min(dist1, distCap / realDist) * sign;
			}
			return Vector.sum(l1.getP1(), Vector.times(dir1, dist1));
		}

		return null;
	}

}