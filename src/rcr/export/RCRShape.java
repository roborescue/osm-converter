package rcr.export;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import maps.gml.GMLBuilding;
import maps.gml.GMLDirectedEdge;
import maps.gml.GMLNode;
import maps.gml.GMLRoad;
import maps.gml.GMLShape;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Pair;

import rcr.RCRDataSet;
import rcr.Vector;

class RCRShape {
	public Way way;
	public Set<Node> usedNodes;
	public GMLShape shape;
	private GMLExporter export;
	
	protected RCRShape(Way w, Set<Node> used, GMLShape gmlShape, GMLExporter exporter) {
		way = w;
		usedNodes = used;
		shape = gmlShape;
		export = exporter;
	}
	
	/**
	 * Checks whether a node can be omitted from this shape. This is the case iff
	 *  a) The node is on a straight line (i.e. the angle between the adjacent edges is 280°)
	 *  b) no other shape shares the node
	 * @param n
	 * @param neighbours
	 * @return
	 */
	public static boolean nodeIsRedundant(Node n, Way w) {
	    for (OsmPrimitive osm : n.getReferrers()) {
	        if (osm != w && RCRDataSet.isAreaShape(osm)) {
	            return false;
	        }	           
	    }
		Point2D p = Vector.asPoint(n.getEastNorth());
		Point2D p1 = null;
		Point2D p2 = null;
		for (Node n2 : w.getNeighbours(n)) {
			if (p1 == null) {
				p1 = Vector.asPoint(n2.getEastNorth());
			} else if ( p2 == null ){
				p2 = Vector.asPoint(n2.getEastNorth());
			}
			else {
				assert false;
			}
		}
		return Vector.pointOnLine(p, p1, p2);
	}

	public void splitEdge(Node n) {
		if (usedNodes.contains(n)) {
			System.out.println(""+ this.way.getId() + ": Don't split on " + n);
			return;
		}
		usedNodes.add(n);
		GMLNode gn = export.node2GML(n);
		System.out.println(""+ this.way.getId() + ": split on " + n + "/"+ gn);
		GMLDirectedEdge e = getEdgeFromNode(gn);
		assert e != null;
		splitEdge(e, gn);
	}
	
	public void splitEdge(GMLDirectedEdge e, GMLNode n) {
		assert shape.getEdges().contains(e);
		export.getGMLMap().splitEdge(e.getEdge(), n);
		export.getGMLMap().removeEdge(e.getEdge());
		
	}
	
	public GMLDirectedEdge getEdgeFromNode(GMLNode n) {
		Point2D p = new Point2D.Double(n.getX(), n.getY());
		for (GMLDirectedEdge e : shape.getEdges()) {
			Point2D p1 = new Point2D.Double(e.getStartNode().getX(), e.getStartNode().getY());
			Point2D p2 = new Point2D.Double(e.getEndNode().getX(), e.getEndNode().getY());
			if (Vector.pointOnLine(p, p1, p2)) {
				return e;
			}
		}
		return null;
	}
	
	public Pair<Point2D, GMLDirectedEdge> intersect(Line2D line) {
//		System.out.println("    Line + " + line.getX1() + ", " + line.getY1()
//				+ " | " + line.getX2() + ", " + line.getY2());
		// Find closest intersection to the *end* of the line
		// Sort edges by their intersections along the line  
		List<Pair<GMLDirectedEdge, Double>> edges = new ArrayList<>();
		for (GMLDirectedEdge e : shape.getEdges()) {
			Point2D p1 = new Point2D.Double(e.getStartNode().getX(), e
					.getStartNode().getY());
			Point2D p2 = new Point2D.Double(e.getEndNode().getX(), e
					.getEndNode().getY());
//			System.out.println("    Wall + " + p1.getX() + ", " + p1.getY()
//					+ " | " + p2.getX() + ", " + p2.getY());
			double dist1 = Vector.getIntersection(line.getP1(), line.getP2(),
					p1, p2);
			double dist2 = Vector.getIntersection(p1, p2, line.getP1(),
					line.getP2());
//			System.out.println("    d1: " + dist1 + ", d2: " + dist2);
			if (dist2 >= 0.0 && dist2 <= 1.0) {
				boolean added = false;
				for (int i=0; i < edges.size(); i++) {
					if (edges.get(i).b > dist1) {
						edges.add(i, Pair.create(e, dist1));
						added = true;
						break;
					}
				}
				if (!added) {
					edges.add(Pair.create(e, dist1));
				}
			}
		}
		
		if (edges.isEmpty()) {
			return null;
		}
		
		assert edges.size() % 2 == 0;
		
		// find "best" starting point
		boolean inside = false;
		GMLDirectedEdge edge = null;
		double start = -Double.MAX_VALUE;
		for (Pair<GMLDirectedEdge, Double> p : edges) {
			inside = !inside;
			if (!inside) {
				start = p.b;
				edge = p.a;
//				if (p.b >= 0.0) {
//					break;
//				}
			}
			else {
				if (p.b >= 1.0) {
					break;
				}
			}
		}
		Point2D offset = Vector.times(Vector.fromLine(line), start);
		return Pair.create(Vector.sum(line.getP1(), offset), edge);
	}

	
	List<GMLNode> getShortestSegment(GMLNode from, GMLNode to) {
		List<GMLDirectedEdge> head = new ArrayList<>();
		List<GMLDirectedEdge> center = new ArrayList<>();
		List<GMLDirectedEdge> tail = new ArrayList<>();

		System.out.println("from:"  + from + ",  to:" + to);
		
		List<GMLDirectedEdge> current = head;
		for (GMLDirectedEdge e : shape.getEdges()) {
			System.out.println("start: " + e.getStartNode());
			if ((e.getStartNode() == from && e.getEndNode() == to)
					|| (e.getStartNode() == to && e.getEndNode() == from)) {
				System.out.println("end: " + e.getEndNode());
				List<GMLNode> result = new ArrayList<>();
				result.add(from);
				result.add(to);
				return result;
			}
			if (e.getStartNode() == from || e.getStartNode() == to) {
				if (current == head) {
					current = center;
				} else if (current == center) {
					current = tail;
				} else {
					assert false : "Duplicate node";
				}
			}
			current.add(e);
		}

		List<GMLNode> r1 = new ArrayList<>();
		double length1 = 0.0;
		r1.add(center.get(0).getStartNode());
		for (GMLDirectedEdge e : center) {
			r1.add(e.getEndNode());
			Point2D p1 = new Point2D.Double(e.getStartNode().getX(), e
					.getStartNode().getY());
			Point2D p2 = new Point2D.Double(e.getEndNode().getX(), e
					.getEndNode().getY());
			length1 += p1.distance(p2);
		}

		List<GMLDirectedEdge> other = new ArrayList<>(tail);
		other.addAll(head);

		List<GMLNode> r2 = new ArrayList<>();
		double length2 = 0.0;
		r2.add(other.get(0).getStartNode());
		for (GMLDirectedEdge e : other) {
			r2.add(e.getEndNode());
			Point2D p1 = new Point2D.Double(e.getStartNode().getX(), e
					.getStartNode().getY());
			Point2D p2 = new Point2D.Double(e.getEndNode().getX(), e
					.getEndNode().getY());
			length2 += p1.distance(p2);
		}

		List<GMLNode> result = null;
		if (length1 <= length2) {
			result = r1;
		} else {
			result = r2;
		}

		if (result.get(0) != from) {
			Collections.reverse(result);
		}

		assert result.get(0) == from && result.get(result.size() - 1) == to;
		return result;

	}
	
	public static RCRShape makeBuilding(Way w, GMLExporter export) {
		assert w.isArea();
		List<GMLNode> apexes = new ArrayList<>();
		Set<Node> used = new HashSet<Node>();

		for (Iterator<Node> it = w.getNodes().listIterator(1); it.hasNext();  ) {
			Node n = it.next();
			if (!nodeIsRedundant(n, w)) {
				used.add(n);
				GMLNode gn = export.node2GML(n);
				if (apexes.isEmpty() || gn != apexes.get(0)) {
					apexes.add(gn);
				}
			}
		}
		
		GMLBuilding b = export.getGMLMap().createBuildingFromNodes(apexes);
		b.setFloors(RCRDataSet.parseInt(w, "rcr:floors", 3));
		b.setCode(RCRDataSet.parseInt(w, "rcr:building_code", 0));
		return new RCRShape(w, used, b, export);
	}
	
	public static RCRShape makeRoad(Way w, GMLExporter export) {
		assert w.isArea();
		List<GMLNode> apexes = new ArrayList<>();
		Set<Node> used = new HashSet<Node>();

		for (Iterator<Node> it = w.getNodes().listIterator(1); it.hasNext();  ) {
			Node n = it.next();
			if (!nodeIsRedundant(n, w)) {
				used.add(n);
				GMLNode gn = export.node2GML(n);
				if (apexes.isEmpty() || gn != apexes.get(0)) {
					apexes.add(gn);
				}
			}
		}
		
		GMLRoad r = export.getGMLMap().createRoadFromNodes(apexes);
		return new RCRShape(w, used, r, export);
	}

	
}