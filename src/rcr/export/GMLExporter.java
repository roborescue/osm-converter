package rcr.export;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import maps.ScaleConversion;
import maps.gml.GMLDirectedEdge;
import maps.gml.GMLEdge;
import maps.gml.GMLMap;
import maps.gml.GMLNode;
import maps.gml.GMLRoad;
import maps.gml.GMLShape;
import maps.gml.formats.RobocupFormat;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Pair;

import rcr.Constants;
import rcr.RCRDataSet;
import rcr.Vector;

public class GMLExporter {

	interface Edge {
		public Line2D asLine(Node origin);

		public Line2D asLine();
	}

	private class OutlineSegment implements Edge {
		Node head;
		Node tail;
		RCRShape shape;

		Line2D line = null;

		public OutlineSegment(Node head, Node tail, RCRShape shape) {
			this.head = head;
			this.tail = tail;
			this.shape = shape;
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

		public Line2D asLine() {
			if (line == null) {
				line = new Line2D.Double(Vector.asPoint(head.getEastNorth()),
						Vector.asPoint(tail.getEastNorth()));
			}
			return line;
		}

		public Line2D asLine(Node origin) {
			return new Line2D.Double(Vector.asPoint(origin.getEastNorth()),
					Vector.asPoint(getOtherNode(origin).getEastNorth()));
		}

	}

	RCRDataSet rcrdata = null;
	DataSet data = null;

	GMLMap gml = null;
	Map<Node, GMLNode> nodeMapping = new HashMap<>();
	Set<Node> processedIntersections = new HashSet<>();
	Map<Node, List<RCRShape>> intersections = new HashMap<>();
	Map<Way, Collection<RCRRoad>> roadMapping = new HashMap<>();
	Map<Node, Collection<RCRRoad>> roadsAtNode = new HashMap<>();
	Set<GMLShape> mainConnectedGroup = new HashSet<>();

	public GMLExporter(RCRDataSet data) {
		this.rcrdata = data;
		this.data = data.getData();
		this.data.cleanupDeletedPrimitives();

		gml = new GMLMap();
		for (Way b : this.data.getWays()) {
			if (!b.isDeleted() && b.hasTag("rcr:type", "building") && b.isArea()) {
				createBuildingGeometry(b);
			}
		}
		for (Way r : this.data.getWays()) {
			if (!r.isDeleted() && r.hasTag("rcr:type", "road") && r.isArea() && r.hasAreaTags()) {
				createAreaRoadGeometry(r);
			}
		}
		for (Node n : intersections.keySet()) {
			processShapeIntersections(n);
		}
		for (Node n : this.data.getNodes()) {
			processNode(n);
		}
		for (Collection<RCRRoad> roads : roadMapping.values()) {
			for (RCRRoad rs : roads) {
				gml.createRoadFromNodes(rs.getApexes());
			}
		}
		
		fixDuplicateEdges();
		computeConnectivity();

	}

	public void exportMap(File file) {
		double minx = gml.getMinX();
		double miny = gml.getMinY();
		System.out.println("Translate by " + minx + " / " + miny);
		ScaleConversion translation = new ScaleConversion(minx, miny, rcrdata.getScale(), rcrdata.getScale());
		gml.convertCoordinates(translation);

		try {
			RobocupFormat.INSTANCE.write(gml, file);
		} catch (Exception e) {
			e.printStackTrace();
		}

		ScaleConversion inverseTranslation = new ScaleConversion(-minx, -miny,
				1.0/rcrdata.getScale(), 1.0/rcrdata.getScale());
		gml.convertCoordinates(inverseTranslation);

		// RCRLegacyMap map = toRescueMap();

	}

	GMLNode node2GML(Node n) {
		if (nodeMapping.containsKey(n)) {
			return nodeMapping.get(n);
		}
		EastNorth c = n.getEastNorth();
		GMLNode node = gml.createNode(c.getX(), c.getY());
		nodeMapping.put(n, node);
		return node;
	}

	GMLMap getGMLMap() {
		return gml;
	}
	
	
	private RCRRoad getSegment(Way w, Node n1, Node n2) {
		if (roadMapping.containsKey(w)) {
			for (RCRRoad rs : roadMapping.get(w)) {
				if ((rs.head == n1 && rs.tail == n2)
						|| (rs.head == n2 && rs.tail == n1)) {
					return rs;
				}
			}
		}

		double width = RCRDataSet.parseInt(w, "rcr:width",
				Constants.DEFAULT_LANE_WIDTH);
		RCRRoad rs = new RCRRoad(n1, n2, w, width, this);
		addToRoadsMap(w, rs);
		return rs;
	}

	private Collection<RCRRoad> getRoads(Node n) {
		List<RCRRoad> result = new ArrayList<>();
		for (OsmPrimitive osm : n.getReferrers()) {
			if (osm.hasTag("rcr:type", "road") && !osm.hasAreaTags()) {
				Way w = (Way) osm;
				for (Node n2 : w.getNeighbours(n)) {
					RCRRoad rs = getSegment(w, n, n2);
					result.add(rs);
				}
			}
		}
		return result;
	}


	private void addToRoadsMap(Way w, RCRRoad road) {
		if (!roadMapping.containsKey(w)) {
			roadMapping.put(w, new HashSet<RCRRoad>());
		}
		if (!roadsAtNode.containsKey(road.head)) {
			roadsAtNode.put(road.head, new HashSet<RCRRoad>());
		}
		if (!roadsAtNode.containsKey(road.tail)) {
			roadsAtNode.put(road.tail, new HashSet<RCRRoad>());
		}
		roadMapping.get(w).add(road);
		roadsAtNode.get(road.head).add(road);
		roadsAtNode.get(road.tail).add(road);
	}

	public Collection<RCRRoad> getSegments(Way w) {
		if (roadMapping.containsKey(w)) {
			return roadMapping.get(w);
		}
		return new HashSet<>();
	}
	
	public void fixDuplicateEdges() {
		final Set<GMLEdge> remaining = new HashSet<GMLEdge>(gml.getEdges());
        while (!remaining.isEmpty()) {
            GMLEdge next = remaining.iterator().next();
            remaining.remove(next);
            // Look at other edges for a duplicate
            Iterator<GMLEdge> it = remaining.iterator();
            while (it.hasNext()) {
                GMLEdge test = it.next();
                if ((test.getStart() == next.getStart() || test.getStart() == next.getEnd())
                    && (test.getEnd() == next.getStart() || test.getEnd() == next.getEnd())) {
                    // Duplicate found
                    gml.replaceEdge(test, next);
                    gml.removeEdge(test);
                    it.remove();
                }
            }
        }
 
	}
	
	public void computeConnectivity() {
		computePassableEdges();
		Set<GMLShape> allShapes = new HashSet<>();
		allShapes.addAll(gml.getRoads());
		allShapes.addAll(gml.getBuildings());
		
		while (!allShapes.isEmpty()) {
			GMLShape s = allShapes.iterator().next();
			allShapes.remove(s);
			
			Set<GMLShape> currentGroup = getConnectedShapes(s);
			allShapes.removeAll(currentGroup);
			if (currentGroup.size() > mainConnectedGroup.size()) {
				mainConnectedGroup = currentGroup;
			}
		}
		
		for (GMLShape s : gml.getBuildings()) {
			if (!mainConnectedGroup.contains(s)) {
				connectShape(s);
			}
		}
	}
	
	public Set<GMLShape> getConnectedShapes(GMLShape shape) {
		HashSet<GMLShape> result = new HashSet<>();
		Deque<GMLShape> open = new ArrayDeque<>();
		open.push(shape);
		result.add(shape);
		while (!open.isEmpty()) {
			GMLShape s = open.pop();
			for (GMLDirectedEdge de : s.getEdges()) {
				if (de.getEdge().isPassable()) {
					for (GMLShape s2 : gml.getAttachedShapes(de.getEdge())) {
						if (!result.contains(s2)) {
							result.add(s2);
							open.push(s2);
						}
					}
				}
			}
		}
		return result;
	}
	
	/**
	 * Compute passable edges between roads or between roads and buildings.
	 */
	public void computePassableEdges() {
        final Collection<GMLEdge> edges = gml.getEdges();
        for (GMLEdge next : edges) {
            Collection<GMLShape> shapes = gml.getAttachedShapes(next);
            if (shapes.size() == 2) {
                Iterator<GMLShape> it = shapes.iterator();
                GMLShape first = it.next();
                GMLShape second = it.next();
                if (first instanceof GMLRoad || second instanceof GMLRoad) {
                    next.setPassable(true);
                    GMLDirectedEdge firstEdge = findDirectedEdge(first.getEdges(), next);
                    GMLDirectedEdge secondEdge = findDirectedEdge(second.getEdges(), next);
                    first.setNeighbour(firstEdge, second.getID());
                    second.setNeighbour(secondEdge, first.getID());
                }
                else {
                    makeImpassable(next, shapes);
                }
            }
            else {
                makeImpassable(next, shapes);
            }
        }
	}

	/** 
	 * Make edges passable s.t. we can reach the nearest connected shape.
	 * @param shape
	 */
	public void connectShape(GMLShape shape) {
		List<GMLShape> path = findConnection(shape);
		if (path != null) {
			for (GMLShape s : path) {
				if (!mainConnectedGroup.contains(s)) {
					mainConnectedGroup.addAll(getConnectedShapes(s));
				}
			}
			GMLShape s1 = null;
			for (GMLShape s2 : path) {
				if (s1 != null) {
					for (GMLDirectedEdge e1 : s1.getEdges()) {
						GMLDirectedEdge e2 = findDirectedEdge(s2.getEdges(), e1.getEdge());
						if (e2 != null) {
							s1.setNeighbour(e1, s2.getID());
							s2.setNeighbour(e2, s1.getID());
							e1.getEdge().setPassable(true);
							break;
						}
					}
				}
				s1 = s2;
			}
		}
	}
	
	private List<GMLShape> findConnection(GMLShape shape) {
		//use BFS to find the closest connected shape.
		System.out.println("connecting: " + shape);
		Deque<GMLShape> open = new ArrayDeque<>();
		HashMap<GMLShape, GMLShape> closed = new HashMap<>();
		closed.put(shape, null);
		open.add(shape);
		GMLShape goal = null;
		while (!open.isEmpty() && goal == null) {
			GMLShape s = open.pop();			
			for (GMLDirectedEdge de : s.getEdges()) {
				for (GMLShape s2 : gml.getAttachedShapes(de.getEdge())) {
					if (!closed.containsKey(s2)) {
						closed.put(s2, s);
						open.addLast(s2);
					}
					if (mainConnectedGroup.contains(s2)) {
						goal = s2;
						break;
					}
				}
			}			
		}
		if (goal != null) {
			List<GMLShape> path = new ArrayList<>();
			path.add(goal);
			GMLShape parent = closed.get(goal);
			while (parent != null) {
				path.add(parent);
				parent = closed.get(parent);
			}
			System.out.println("found path of length " + path.size());
			return path;		
		}
		return null;		
	}
	

    private void makeImpassable(GMLEdge edge, Collection<GMLShape> attached) {
        edge.setPassable(false);
        for (GMLShape shape : attached) {
            shape.setNeighbour(edge, null);
        }
    }

    private GMLDirectedEdge findDirectedEdge(List<GMLDirectedEdge> possible, GMLEdge target) {
        for (GMLDirectedEdge next : possible) {
            if (next.getEdge() == target) {
                return next;
            }
        }
        return null;
    }
	
	// private GMLNode getIntersection()

	private void processNode(Node n) {
		List<RCRRoad> roads = new ArrayList<>(getRoads(n));
		if (roads.isEmpty()) {
			return;
		}
		if (processedIntersections.contains(n)) {
			System.out.println("Skipping Node " + n.getId());
			return;
		}
		processedIntersections.add(n);

		System.out.println("Processing Node " + n.getId());
		System.out.println("We have " + roads.size() + " roads");
		if (roads.size() == 1) {
			RCRRoad r = roads.get(0);
			Point2D left = r.offsetLine(r.width / 2000, n).getP1();
			Point2D right = r.offsetLine(-r.width / 2000, n).getP1();
			r.setLeft(n, gml.createNode(left.getX(), left.getY()));
			r.setRight(n, gml.createNode(right.getX(), right.getY()));
			return;
		}

		LineAngleComperator comp = new LineAngleComperator(n);
		Collections.sort(roads, comp);

		List<GMLNode> intersectionApexes = new ArrayList<>();
		RCRRoad prev = roads.get(roads.size() - 1);
		for (RCRRoad r : roads) {
			Point2D intersection = prev.intersect(r);
			if (intersection == null) {
				assert false;
			}
			System.out.println("  Found intersection with road");
			GMLNode intersectNode = gml.createNode(intersection.getX(),
					intersection.getY());
			prev.setRight(n, intersectNode);
			r.setLeft(n, intersectNode);
			prev = r;
			intersectionApexes.add(intersectNode);
		}
		if (intersectionApexes.size() > 2) {
			gml.createRoadFromNodes(intersectionApexes);
		}
	}
	
	private static boolean isShapeWay(OsmPrimitive w) {
		if (!(w instanceof Way) || !((Way) w).isArea()) {
			return false;
		}
		if (w.hasTag("rcr:type", "building")) {
			return true;
		}
		if (w.hasAreaTags() && w.hasTag("rcr:type", "road")) {
			return true;
		}
		return false;
	}


	private void createAreaRoadGeometry(Way w) {
		System.out.println("Processing area road " + w.getId());
		assert w.isClosed();

		RCRShape r = RCRShape.makeRoad(w, this);

		for (Iterator<Node> it = w.getNodes().listIterator(1); it.hasNext();  ) {
			Node n = it.next();
			
			Collection<RCRRoad> roads = getRoads(n);
			if (roads.isEmpty()) {
				continue;
			}
			
			if (!intersections.containsKey(n)) {
				intersections.put(n, new ArrayList<RCRShape>());
			}
			intersections.get(n).add(r);
		}		
	}
	
	private void createBuildingGeometry(Way w) {
		System.out.println("Processing building " + w.getId());
		assert w.isClosed();

		RCRShape b = RCRShape.makeBuilding(w, this);

		for (Iterator<Node> it = w.getNodes().listIterator(1); it.hasNext();  ) {
			Node n = it.next();
			
			Collection<RCRRoad> roads = getRoads(n);
			if (roads.isEmpty()) {
				continue;
			}
			
			if (!intersections.containsKey(n)) {
				intersections.put(n, new ArrayList<RCRShape>());
			}
			intersections.get(n).add(b);
		}
	}

	
	private void processShapeIntersections(Node n) {
		assert intersections.containsKey(n);
		assert !processedIntersections.contains(n);
		processedIntersections.add(n);

		for (List<Edge> edges : getEnclosedSegments(n, getRoads(n), intersections.get(n))) {
			RCRShape start = ((OutlineSegment) edges.remove(0)).shape;
			RCRShape end = ((OutlineSegment) edges.remove(edges.size()-1)).shape;
			List<RCRRoad> roads = new ArrayList<>();
			for (Edge e : edges) {
				roads.add((RCRRoad) e);
			}
			connectRoadsAndShapes(roads, start, end);
		}
	}
	
	
	private Node getCommonNode(Collection<RCRRoad> roads, Collection<RCRShape> shapes) {
		Set<Node> candidates = null;
		for (RCRRoad r : roads) {
			List<Node> rs = Arrays.asList(r.head, r.tail);
			if (candidates == null) {
				candidates = new HashSet<>(rs);
			}
			else {
				candidates.retainAll(rs);
			}
			if (candidates.isEmpty()) {
				return null;
			}
		}
		for (RCRShape s : shapes) {
			if (candidates == null) {
				candidates = new HashSet<>(s.way.getNodes());
			}
			else {
				candidates.retainAll(s.way.getNodes());
			}
			if (candidates.isEmpty()) {
				return null;
			}
		}
		return candidates.iterator().next();
	}
	
	
	/**
	 * Build geometry for the intersections of n roads that join at one point
	 * between two shape outlines. All roads and shapes are assumed to connect at a single, unique node. 
	 * In clockwise orientation around that node, the order of elements is <tt>startShape</tt>,
	 * the elements of <tt>roads</tt> (in order) and finally <tt>endShape</tt>.
	 * 
	 * <tt>startShape</tt> and <tt>endShape</tt> may be identical.
	 * 
	 * @param roads
	 * @param startShape
	 * @param endShape
	 */
	private void connectRoadsAndShapes(List<RCRRoad> roads,
			RCRShape startShape, RCRShape endShape) {
		Node head = getCommonNode(roads, Arrays.asList(startShape, endShape));
		assert head != null;
		
		List<GMLNode> roadApexes = new ArrayList<>();
		RCRRoad prev = null;
		System.out.println("We have " + roads.size() + " roads");
		for (RCRRoad r : roads) {
			if (prev == null) {
				Pair<Point2D, GMLDirectedEdge> intersection = startShape
						.intersect(r.offsetLine(r.width / 2000, head));
				if (intersection == null) {
					startShape.splitEdge(head); // split on center if center
													// was skipped during shape
													// creation
					r.setLeft(head, node2GML(head));
					roadApexes.add(node2GML(head));
				} else {
					GMLNode splitNode = gml.createNode(intersection.a.getX(),
							intersection.a.getY());
					System.out.println("  Found first intersection with building: " + splitNode);
					startShape.splitEdge(intersection.b, splitNode);
					r.setLeft(head, splitNode);
					roadApexes.add(splitNode);
				}
			} else {
				Point2D intersection = prev.intersect(r);
				if (intersection == null) {
					assert false;
				}
				System.out.println("  Found intersection with road");
				GMLNode intersectNode = gml.createNode(intersection.getX(),
						intersection.getY());
				prev.setRight(head, intersectNode);
				r.setLeft(head, intersectNode);
				roadApexes.add(intersectNode);
			}
			prev = r;
		}
		if (prev != null) {
			Pair<Point2D, GMLDirectedEdge> intersection = endShape
					.intersect(prev.offsetLine(-prev.width / 2000, head));
			if (intersection != null) {
				GMLNode splitNode = gml.createNode(intersection.a.getX(),
						intersection.a.getY());
				System.out.println("  Found second intersection with building: " + splitNode);
				
				endShape.splitEdge(intersection.b, splitNode);
				prev.setRight(head, splitNode);
				roadApexes.add(splitNode);
			} else {
				endShape.splitEdge(head); // split on head if it was skipped
											// during shape creation
				prev.setRight(head, node2GML(head));
				roadApexes.add(node2GML(head));
			}

		}
		
		List<GMLNode> capApexes;
		if (startShape == endShape) {
			capApexes = startShape.getShortestSegment(
					roadApexes.get(roadApexes.size() - 1), roadApexes.get(0));
		} 
		else {
			//Stitch the cap together from two buildings
			capApexes = endShape.getShortestSegment(
					roadApexes.get(roadApexes.size() - 1), node2GML(head));
			capApexes.addAll(startShape.getShortestSegment(
					node2GML(head), roadApexes.get(0)));
		}
		if (roadApexes.size() > 2) {
			// More than one road meets the shape, so we need to add an
			// area for the intersection
			if (capApexes.size() > 2) {
				capApexes.remove(0);
				capApexes.remove(capApexes.size() - 1);
				roadApexes.addAll(capApexes);
			}
			gml.createRoadFromNodes(roadApexes);
		} else if (roadApexes.size() == 2) {
			assert prev != null;
			// FIXME: different shapes
			if (capApexes.size() > 2) {
				prev.setEndApexes(head, capApexes);
			}
		}
	}
	

	class LineAngleComperator implements Comparator<Edge> {

		Node origin;

		public LineAngleComperator(Node origin) {
			this.origin = origin;
		}

		@Override
		public int compare(Edge l1, Edge l2) {
			return Double.compare(getAngle(l1), getAngle(l2));
		}

		private double getAngle(Edge l) {
			Point2D p = Vector.fromLine(l.asLine(this.origin));
			double angle = Math.atan2(p.getX(), p.getY());
			if (angle < 0) {
				angle += 2 * Math.PI;
			}
			return angle;
		}

	}

	private List<RCRRoad> sortedSegments(Node center,
			Collection<RCRRoad> roads, Collection<Node> wallNodes) {
		List<Edge> edges = new ArrayList<>();
		edges.addAll(roads);
		for (Node n : wallNodes) {
			edges.add(new OutlineSegment(center, n, null));
		}
		Collections.sort(edges, new LineAngleComperator(center));
		List<RCRRoad> result = new ArrayList<>();

		// Find roads between two walls in clockwise order
		// Iterate twice, start adding roads to result once we encountered (at
		// least)
		// one wall. Stop once we encounter a second wall (after adding roads)
		edges.addAll(new ArrayList<>(edges));
		boolean foundWall = false;
		boolean foundRoad = false;
		for (Edge e : edges) {
			if (e instanceof OutlineSegment) {
				foundWall = true;
				if (foundRoad) {
					break;
				}
			} else if (e instanceof RCRRoad && foundWall) {
				result.add((RCRRoad) e);
				foundRoad = true;
			}
		}
		return result;
	}
	
	
	/**
	 * Return the two nodes adjacent to <tt>center</tt> so that the clockwise 
	 * angle between the first and second node is outside the building.
	 * @param center
	 * @param building
	 * @return
	 */
	private Pair<Node, Node> getOutsideNodes(Node center, Way building) {
		Node first = null, second = null;
		double area = 0;
		for(Pair<Node, Node> p : building.getNodePairs(false)) {
			EastNorth c1 = p.a.getEastNorth();
			EastNorth c2 = p.b.getEastNorth();
			area += c1.getX()*c2.getY() - c2.getX()*c1.getY();
			if (p.b == center) {
				first = p.a;
			}
			else if (p.a == center) {
				second = p.b;
			}
		}
		
		if (area > 0) {
			// positive area: counter-clockwise winding
			return Pair.create(second, first);
		}
		else {
			// negative area: clockwise winding
			return Pair.create(first, second);
		}
		
	}
	
	/**
	 * Return Lists of road segments, each enclosed by the two closest wall segments 
	 * @param center
	 * @param roads
	 * @param buildings
	 * @return
	 */
	private List<List<Edge>> getEnclosedSegments(Node center,
			Collection<RCRRoad> roads, Collection<? extends RCRShape> shapes) {

		Set<Edge> startEdges = new HashSet<>();
		Set<Edge> endEdges = new HashSet<>();
		for (RCRShape s : shapes) {
			Pair<Node, Node> p = getOutsideNodes(center, s.way);
			startEdges.add(new OutlineSegment(center, p.a, s));
			endEdges.add(new OutlineSegment(center, p.b, s));
		}
		startEdges.removeAll(endEdges);
		
		List<Edge> edges = new ArrayList<>();
		for (RCRRoad r : roads) {
			boolean ignore = false;
			for (RCRShape s : shapes) {
				// ignore roads along shape outlines
				// TODO: support this better?
				if (s.way.containsNode(r.head) && s.way.containsNode(r.tail)) {
					ignore = true;
					break;
				}
			}
			if (!ignore) {
				edges.add(r);
			}
		}
		edges.addAll(startEdges);
		edges.addAll(endEdges);

		Collections.sort(edges, new LineAngleComperator(center));

		edges.addAll(new ArrayList<>(edges)); // iterate twice
		List<List<Edge>> result = new ArrayList<>();
		List<Edge> currentArc = null;
		Edge first = null;
		for (Edge e: edges) {
			if (e == first) {
				break;
			}
			if (startEdges.contains(e)) {
				currentArc = new ArrayList<>();
				currentArc.add(e);
			}
			else if (endEdges.contains(e) && currentArc != null) {
				if (first == null) {
					first = currentArc.get(0);
				}
				if (currentArc.size() > 1) {
					currentArc.add(e);
					result.add(currentArc);
				}
				currentArc = null;
			}
			else if (currentArc != null) {
				currentArc.add(e);
			}
		}
		return result;
	}
	
}
