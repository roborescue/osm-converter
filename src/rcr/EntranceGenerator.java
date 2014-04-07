package rcr;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Pair;

import tools.AreaTools;


public class EntranceGenerator {

	static double ENTRANCE_WIDTH = Constants.DEFAULT_ENTRANCE_WIDTH/1000.0;
	static double MIN_ENTRANCE_WALL_LENGTH = 2.0;
	static double MIN_ENTRANCE_LENGTH = 0.5;
	static double MAX_ENTRANCE_LENGTH = 50.0;
	
	static double DEVIATION_THRESHOLD = 0.5;
	
	RCRDataSet rcrdata;
	DataSet data;
	Set<Way> mainConnectedGroup;
	Set<EntranceCandidate> plannedEntrances = new HashSet<>();
	Map<Way, Set<Way>> connectivityCache = new HashMap<>();
	
	private class Endpoint {
		public final Way way;
		public final Point2D point;
		public final Node node;
		public final Point2D preferredDirection;
		
		public Endpoint(Way w, Node n, Point2D direction) {
			way = w;
			node = n;
			point = node2Point(n);
			preferredDirection = direction;
		}
		
		public Endpoint(Way w, Point2D p, Point2D direction) {
			way = w;
			node = null;
			point = p;
			preferredDirection = direction;
		}

		public Pair<Node, Node> getSegment() {
			if (node != null) {
				return Pair.create(node, node);
			}
			Vector.debug = true;
			System.out.println("point: " + point);
			for (Pair<Node, Node> p : way.getNodePairs(false)) {
				Line2D segment = new Line2D.Double(node2Point(p.a), node2Point(p.b));
				System.out.println(segment.getP1() +" -- " + segment.getP2() + " : " + segment.ptSegDist(point));
				if (Vector.pointOnLine(point, segment.getP1(), segment.getP2())) {
					System.out.println("    ok");
					Vector.debug = false;
					return p;
				}
			}
			assert false;
			return null;
		}
		
		public double getX() {
			return point.getX();
		}

		public double getY() {
			return point.getY();
		}
		
		public double distance(Endpoint other) {
			return point.distance(other.point);
		}
		
		public boolean isBuilding() {
			return way.hasTag("rcr:type", "building");
		}
		
		public Node getCloseNode() {
			if (node != null) {
				return node;
			}
			Pair<Node, Node> segment = getSegment();
			if (node2Point(segment.a).distance(point) < ENTRANCE_WIDTH/2 ) {
				return segment.a;
			}
			if (node2Point(segment.b).distance(point) < ENTRANCE_WIDTH/2 ) {
				return segment.b;
			}
			return null;
		}
		
		
	}
	
	private class EntranceCandidate
	{
		public Endpoint buildingEndpoint;
		public Endpoint roadEndpoint;
		public String debugMsg = null;
				
		
		public EntranceCandidate(Way b, Point2D e, Point2D dir) {
			buildingEndpoint = new Endpoint(b, e, dir);
		}
		
		public EntranceCandidate(Way b, Node e, Point2D dir) {
			buildingEndpoint = new Endpoint(b, e, dir);
		}
		
		public EntranceCandidate(EntranceCandidate other) {
			this.buildingEndpoint = other.buildingEndpoint;
			this.roadEndpoint = other.roadEndpoint;
		}
		
		
//		public Pair<Node, Node> getBuildingSegment() {
//			if (this.buildingNode != null)
//				return Pair.create(this.buildingNode, this.buildingNode);
//			return getSegment(this.building);
//		}
//
//		public Pair<Node, Node> getRoadSegment() {
//			if (this.roadNode != null)
//				return Pair.create(this.roadNode, this.roadNode);
//			return getSegment(this.road);
//		}
		
		public EntranceCandidate addRoadPoint(Way r, Point2D e, Point2D dir) {
			EntranceCandidate result = new EntranceCandidate(this);
			assert !Double.isNaN(e.getX()) && !Double.isNaN(e.getY());
			result.roadEndpoint = new Endpoint(r, e, dir);
			return result;
		}

		public EntranceCandidate addRoadPoint(Way r, Node e, Point2D dir) {
			EntranceCandidate result = new EntranceCandidate(this);			
			result.roadEndpoint = new Endpoint(r, e, dir);
			return result;
		}
		
		public double getPreferredDirectionMismatch() {
			Point2D dir = Vector.diff(roadEndpoint.point, buildingEndpoint.point);
			double dotB = Vector.dotPNorm(dir, buildingEndpoint.preferredDirection);
			double dotR = Vector.dotPNorm(dir, roadEndpoint.preferredDirection);
			return Math.abs(dotB) * Math.abs(dotR);
		}
		
		public double getNormalCosAngles() {
			if (this.buildingEndpoint.preferredDirection== null || this.roadEndpoint.preferredDirection == null) {
				return 0.1;
			}
			return Math.abs(Vector.dotPNorm(this.buildingEndpoint.preferredDirection, this.roadEndpoint.preferredDirection));
		}
		
		public double getLength() {
			return this.buildingEndpoint.distance(this.roadEndpoint);
		}
		
		public Line2D getLine() {
			return new Line2D.Double(this.buildingEndpoint.point, this.roadEndpoint.point);
		}
		
		public BBox getBounds() {
			if (this.roadEndpoint == null) {
				return null;
			}
			LatLon b1 = Main.getProjection().eastNorth2latlon(
					new EastNorth(this.buildingEndpoint.getX(), this.buildingEndpoint.getY()));
			LatLon b2 = Main.getProjection().eastNorth2latlon(
					new EastNorth(this.roadEndpoint.getX(), this.roadEndpoint.getY()));
		
			return new BBox(b1, b2);
		}
		
		public String toString() {
			String fromEntrance = (this.buildingEndpoint.node != null) ? " from Entrance " + this.buildingEndpoint.node.getId() : "";
			if (this.roadEndpoint.way != null) {
				String b2b = (this.roadEndpoint.way.hasTag("rcr:type", "building")) ? " B2B" : "";
				return "Entrance: from " + this.buildingEndpoint.way.getId() + " to " + this.roadEndpoint.way.getId() + ", length=" + this.getLength() + ", cosAngle=" + this.getNormalCosAngles() + b2b + fromEntrance; 
			}
			return "Entrance: from " + this.buildingEndpoint.way.getId() + fromEntrance;
		}
		
	}

	/**
	 * Public wrapper around EntranceCandidate
	 * @author goebelbe
	 *
	 */
	public class Entrance {
		private EntranceCandidate candidate;
		
		Entrance (EntranceCandidate candidate) {
			this.candidate = candidate;
		}
		
		EntranceCandidate getEntrance() {
			return this.candidate;
		}
		
		public Line2D getLine() {
			return candidate.getLine();
		}
	}
	
	private class EntranceComp implements Comparator<EntranceCandidate> {

		@Override
		public int compare(EntranceCandidate e1, EntranceCandidate e2) {
			// Penalize sharp angles between building and road
			if (e1.getPreferredDirectionMismatch() < DEVIATION_THRESHOLD ) {
				if (e2.getPreferredDirectionMismatch() >= DEVIATION_THRESHOLD) {
					return -1;
				}
			}
			else if (e2.getPreferredDirectionMismatch() < DEVIATION_THRESHOLD) {
				return 1;
			}
			
			//Penalize Building-Building entrances
			if (e1.roadEndpoint.isBuilding()) {
				if (!e2.roadEndpoint.isBuilding()) {
					return -1;
				}
			}
			else if (e2.roadEndpoint.isBuilding()) {
				return 1;
			}
			
			//Prefer entrances to already connected entities
			if (!isConnected(e1.roadEndpoint.way)) {
				if (isConnected(e2.roadEndpoint.way)) {
					return -1;
				}
			}
			else if (!isConnected(e2.roadEndpoint.way)) {
				return 1;
			}

			// Prefer entrances from marked entrances
			if (e1.buildingEndpoint.node == null) {
				if (e2.buildingEndpoint.node != null) {
					return -1;
				}
			}
			else if (e2.buildingEndpoint.node == null) {
				return 1;
			}
			
			
			return -Double.compare(e1.getLength(), e2.getLength());
		}
	
	}
	
	
	public EntranceGenerator(RCRDataSet data) {
		this.rcrdata = data;
		this.data = data.getData();
		this.mainConnectedGroup = this.rcrdata.findLargestConnectedGroup();
	}

	public Way makeEntrance(Way building, boolean toRoadsOnly) {
		//DEBUG!
		List<EntranceCandidate> entranceStarts = getEntrancePointCandidates(building);
		List<EntranceCandidate> entrances = new ArrayList<>();
		
		for (EntranceCandidate e : entranceStarts) {
			List<EntranceCandidate> entranceEnds = getEntranceRoads(e);
			entrances.addAll(entranceEnds);
		}
		
		if (!entrances.isEmpty())
		{
			Collections.sort(entrances, new EntranceComp());
			Collections.reverse(entrances);
			boolean foundBest = false;
			for (EntranceCandidate best  : entrances) {
				System.out.println("candidate: " + best);
				if (!checkIntersections(best) && (!toRoadsOnly || !best.roadEndpoint.isBuilding())) {
					Way w  = createEntranceWay(best);
					if (!foundBest) {
						w.put("rcr:best", "yes");
					}
					return w;
				}
				else {
					//assert checkIntersections(best);
					Way w = createEntranceWay(best);
					w.put("rcr:pruned", "yes");
					w.put("rcr:intersect_check", best.debugMsg);
//					if (checkIntersections(best)) {
//						w.put("rcr:intersected", "yes");
						w.put("rcr:width", "0");
//					}
				}
			}
		}
		return null;
		
//		return createEntranceWay(getEntrance(building, toRoadsOnly));
	}
	
	public Way createEntranceWay(Node n1, Node n2) {
		//Make sure there are two buildings involved
		Way b1 = null;
		Way b2 = null;
		for (OsmPrimitive osm : n1.getReferrers()) {
			if (osm.hasTag("rcr:type", "building")) {
				b1 = (Way) osm;
				break;
			}
		}
		for (OsmPrimitive osm : n2.getReferrers()) {
			if (osm != b1 && osm.hasTag("rcr:type", "building")) {
				b2 = (Way) osm;
				break;
			}
		}
		if (b1 == null || b2 == null) {
			return null;
		}
		
		Way entrance = new Way();
		entrance.addNode(n1);
		entrance.addNode(n2);
		entrance.put("rcr:type", "road");
		entrance.put("rcr:entrance", "yes");
		entrance.put("rcr:width", Integer.toString((int) (ENTRANCE_WIDTH * 1000)));
		this.data.addPrimitive(entrance);
		return entrance;
	}

	public Way makeEntrance(Way building, Collection<Way> roads) {
		return createEntranceWay(getEntrance(building, roads));
	}
	
	public Way createEntranceWay(Entrance e) {
		if (e == null) {
			return null;
		}
		return createEntranceWay(e.getEntrance());
	}
	
	private Way createEntranceWay(EntranceCandidate e) {
		if (e == null || e.getLength() < Vector.epsilon) {
			return null;
		}
		Node start = null;
		Node end = null;

		start = e.buildingEndpoint.getCloseNode();
		if (start == null) {
			EastNorth c = new EastNorth(e.buildingEndpoint.getX(), e.buildingEndpoint.getY());
			start = new Node(Main.getProjection().eastNorth2latlon(c));
			start.put("rcr:autogenerated", "entrance");
			start.put("rcr:type", "node");
			this.data.addPrimitive(start);
			int index = e.buildingEndpoint.way.getNodes().indexOf(e.buildingEndpoint.getSegment().a);
			assert index != -1;
			e.buildingEndpoint.way.addNode(index+1, start);
		}
		
		end = e.roadEndpoint.getCloseNode();
		if (end == null) {
			EastNorth c = new EastNorth(e.roadEndpoint.getX(), e.roadEndpoint.getY());
			end = new Node(Main.getProjection().eastNorth2latlon(c));
			end.put("rcr:autogenerated", "entrance");
			end.put("rcr:type", "node");
			this.data.addPrimitive(end);
			int index = e.roadEndpoint.way.getNodes().indexOf(e.roadEndpoint.getSegment().a);
			assert index != -1;
			e.roadEndpoint.way.addNode(index+1, end);
		}
		
		Way entrance = new Way();
		entrance.addNode(start);
		entrance.addNode(end);
		entrance.put("rcr:type", "road");
		entrance.put("rcr:entrance", "yes");
		entrance.put("rcr:width", Integer.toString((int) (ENTRANCE_WIDTH * 1000)));

		//DEBUG
		Point2D dir = Vector.diff(e.roadEndpoint.point, e.buildingEndpoint.point);
		double dotB = Vector.dotPNorm(dir, e.buildingEndpoint.preferredDirection);
		double dotR = Vector.dotPNorm(dir, e.roadEndpoint.preferredDirection);

		entrance.put("rcr:_bnormal", ""+e.buildingEndpoint.preferredDirection);
		entrance.put("rcr:_rnormal", ""+e.roadEndpoint.preferredDirection);
		entrance.put("rcr:_bnode", ""+e.buildingEndpoint.node);
		entrance.put("rcr:_rnode", ""+e.roadEndpoint.node);
		entrance.put("rcr:_bdotP", ""+dotB);
		entrance.put("rcr:_rdotP", ""+dotR);
		entrance.put("rcr:_mismatch", Double.toString(e.getPreferredDirectionMismatch()));
		
		this.data.addPrimitive(entrance);
		return entrance;
		
	}	
	
	private static Point2D node2Point(Node n)
	{
		return Vector.asPoint(n.getEastNorth());	
	}
	
	private boolean isConnectedBuilding(Way b1, Way b2) {
//		System.out.println("check connectivity : " + b1.getId() + " to " + b2.getId());
		Deque<Way> open = null;
		Set<Way> closed = null;
		if (connectivityCache.containsKey(b1)) {
//			System.out.println("  return " + connectivityCache.get(b1).contains(b2));
			return connectivityCache.get(b1).contains(b2);
		}
		closed = new HashSet<>();
		closed.add(b1);
		open = new ArrayDeque<>(closed);
		
		while (!open.isEmpty()) {
			Way w = open.pop();
//			System.out.println("  pop: " + w);
			for (Way w2 : RCRDataSet.findNeighbours(w)) {
//				System.out.println("  neighbour: " + w2);
				if (w2.hasTag("rcr:type", "building") && !closed.contains(w2)) {
//					System.out.println("     push");
					open.push(w2);
					closed.add(w2);
				}
			}
		}
		for (Way w : closed) {
			connectivityCache.put(w, closed);
		}
//		System.out.println("  return " + connectivityCache.get(b1).contains(b2));
		return closed.contains(b2);
	}
	
	
	private boolean isConnected(Way b) {
		if (this.mainConnectedGroup.contains(b)) {
			return true;
		}
		HashSet<Way> closed = new HashSet<>();
		List<Way> open = new LinkedList<>();
		closed.add(b);
		open.add(b);
		while (!open.isEmpty()) {
			Way w = open.remove(0);
			for (Node n : w.getNodes()) {
				for (Way newWay : RCRDataSet.getWaysAtNode(n)) {
					if (this.mainConnectedGroup.contains(newWay)) {
						return true;
					}
					if (!closed.contains(newWay)) {
						open.add(newWay);
						closed.add(newWay);
					}
				}
			}
			//FIXME: iterating through all entrances is clearly suboptimal
			for (EntranceCandidate e : plannedEntrances) {
				Way succ = null;
				if (e.buildingEndpoint.way == w) {
					succ = e.roadEndpoint.way;
				}
				else if (e.roadEndpoint.way == w) {
					succ = e.buildingEndpoint.way;
				}
				if (succ != null && this.mainConnectedGroup.contains(succ)) {
					return true;
				}
				if (succ != null && !closed.contains(succ)) {
					open.add(succ);
					closed.add(succ);
				}
			}
		}
		return false;
	}

	
	public Entrance getEntrance(Way building, Collection<Way> roads) {
//		System.out.println("id:" + building.getId());
		List<EntranceCandidate> entranceStarts = getEntrancePointCandidates(building);
//		System.out.println("found " + entranceStarts.size() + " start points");

		List<EntranceCandidate> entrances = new ArrayList<>();
		
		for (EntranceCandidate e : entranceStarts) {
//			System.out.println("entrance node: " + e.buildingNode);
			for (Way r : roads) {
				if (r == e.buildingEndpoint.way || !(r.hasTag("rcr:type", "road", "building"))) 
					continue;
				
				for (EntranceCandidate e2 :  makeEntranceToWay(e, r)) {
					entrances.add(e2);
				}
			}
		}
		
		if (!entrances.isEmpty())
		{
			Collections.sort(entrances, new EntranceComp());
			for (int i = entrances.size(); i != 0; i--) {
				EntranceCandidate best = entrances.get(i-1);
				if (!checkIntersections(best)) {
//					System.out.println("Best entrance: " + best);
					return new Entrance(best);
				}
				else {
//					System.out.println("Failed intersection test: " +  best);
				}
			}
		}
		return null;
	}
	
	public Entrance getEntrance(Way building, boolean toRoadsOnly) {
//		System.out.println("id:" + building.getId());
		List<EntranceCandidate> entranceStarts = getEntrancePointCandidates(building);
//		System.out.println("found " + entranceStarts.size() + " start points");

		List<EntranceCandidate> entrances = new ArrayList<>();
		
		for (EntranceCandidate e : entranceStarts) {
//			System.out.println("entrance node: " + e.buildingNode);
			List<EntranceCandidate> entranceEnds = getEntranceRoads(e);
//			System.out.println("found " + entranceEnds.size() + " end points");
//			for (EntranceCandidate e2 : entranceEnds)
//				System.out.println("  " + e2);
			entrances.addAll(entranceEnds);
		}
		
		if (!entrances.isEmpty())
		{
			Collections.sort(entrances, new EntranceComp());
			Collections.reverse(entrances);
			for (EntranceCandidate best  : entrances) {
				if (!checkIntersections(best) && (!toRoadsOnly || !best.roadEndpoint.isBuilding())) {
//					System.out.println("Best entrance: " + best);
//			for (EntranceCandidate e2 : entrances)
//				System.out.println("  " + e2);
					plannedEntrances.add(best);
					return new Entrance(best);
				}
				else {
//					System.out.println("Failed intersection test: " +  best);
				}
			}
		}
		return null;
	}
	
	private boolean checkIntersections(EntranceCandidate e) {
		assert e.getBounds() != null;
		assert e.getLength() > Vector.epsilon;
		
		// shorten the entrance line to avoid self intersects
		double amount = 0.01; 
		Point2D offset = Vector.times(Vector.normalize(Vector.diff(e.roadEndpoint.point, e.buildingEndpoint.point)), amount);
		Line2D shortEntrance = new Line2D.Double(Vector.sum(e.buildingEndpoint.point, offset), Vector.diff(e.roadEndpoint.point, offset));
		Line2D line = new Line2D.Double(e.buildingEndpoint.point, e.roadEndpoint.point);
		System.out.println("check entrance " +e);
		for (Way w: data.searchWays(e.getBounds())) {
			if (w.hasTag("rcr:type", "road", "building") && AreaTools.lineIntersectsWay(line, w)) {
				e.debugMsg = "intersects: " + w.getId();
				return true;
			}
			if (w.hasTag("rcr:type", "building") && AreaTools.lineContainedInPolygon(line, w)) {
				e.debugMsg = "contained: " + w.getId();
				return true;
			}
		}
		for (EntranceCandidate e2 : plannedEntrances) {
			if (shortEntrance.intersectsLine(e2.getLine())) {
				e.debugMsg = "intersects entrance: " + e2.buildingEndpoint.way.getId() + " -- " + e2.roadEndpoint.way.getId();
				return true;
			}
		}
		
		// Check if entrance lies inside building
//		Line2D longEntrance = new Line2D.Double(Vector.diff(e.buildingPoint, offset), Vector.sum(e.roadPoint, offset));
		if (AreaTools.lineContainedInPolygon(shortEntrance, e.buildingEndpoint.way)) {
			e.debugMsg = "contained in self";
			return true;
		}
//		int intersectionCount = 0;
//		for (Pair<Node, Node> pair: e.building.getNodePairs(false)) {
//			Line2D other = new Line2D.Double(node2Point(pair.a), node2Point(pair.b));
//			if (longEntrance.intersectsLine(other)) {
//				intersectionCount++;
//			}
//		}
//		if (intersectionCount > 1) {
//			return true;
//		}		
		
		return false;
	}
	
	private List<EntranceCandidate> getEntrancePointCandidates(Way building) {
		List <EntranceCandidate> result = new ArrayList<>();
		for (Node n: building.getNodes()) {
			if (n.hasKey("entrance") || n.hasTag("building", "entrance") || n.hasKey("addr:housenumber") || 
					n.hasKey("addr:housename")) {
				if (building.getNeighbours(n).size() == 2) {
					Iterator<Node> it = building.getNeighbours(n).iterator();
					Point2D p1 = node2Point(it.next());
					Point2D p2 = node2Point(n);
					Point2D p3 = node2Point(it.next());
					Point2D dir1 = Vector.normalVec(Vector.diff(p1, p2));
					Point2D dir2 = Vector.normalVec(Vector.diff(p2, p3));
					Point2D dir = Vector.normalize(Vector.sum(dir1, dir2));
					result.add(new EntranceCandidate(building, n, dir));
				}
			}
		}
		for (Pair<Node, Node> pair: building.getNodePairs(false)) {
			Point2D p1 = node2Point(pair.a);
			Point2D p2 = node2Point(pair.b);
			if (p1.distance(p2) >= MIN_ENTRANCE_WALL_LENGTH) {
				Point2D e = Vector.interpolate(p1, p2, 0.5);
				Point2D dir = Vector.normalVec(Vector.diff(p1, p2));
				result.add(new EntranceCandidate(building, e, dir)); 
			}
		}
		return result;
	}
	
	private List<EntranceCandidate> makeEntranceToWay(EntranceCandidate e, Way w) {
		List<EntranceCandidate> results = new ArrayList<>();
		Map<Node, Point2D> normalAtPoint = new HashMap<>();
		// generate entrances perpendicular to the road segment
		for (Pair<Node, Node> p : w.getNodePairs(false)) {
			Point2D p1 = node2Point(p.a);
			Point2D p2 = node2Point(p.b);
			Point2D dir = Vector.normalVec(Vector.diff(p1, p2));
			Point2D intersection = Vector.orthoIntersection(p1, p2, e.buildingEndpoint.point);
			
			if (Vector.valid(intersection)) {
				results.add(e.addRoadPoint(w, intersection, dir));
			}
			
			// calculate average normals at nodes
			if (normalAtPoint.containsKey(p.a)) {
				normalAtPoint.put(p.a, Vector.sum(normalAtPoint.get(p.a),  dir)); 
			}
			else {
				normalAtPoint.put(p.a, dir); 				
			}
			if (normalAtPoint.containsKey(p.b)) {
				normalAtPoint.put(p.b, Vector.sum(normalAtPoint.get(p.b),  dir)); 
			}
			else {
				normalAtPoint.put(p.b, dir); 				
			}
		}
		// generate entrances at nodes (for roads only)
		if (w.hasTag("rcr:type", "road")) {
			for (Node n : w.getNodes()) {
				Point2D normal = null;
				if (n.getReferrers().size() == 1
						&& w.getNeighbours(n).size() == 1) {
					// endpoint, don't care about normal, so set it to the
					// direction of the entrance
					Point2D p = node2Point(n);
					normal = Vector.normalize(Vector.diff(
							e.buildingEndpoint.point, p));
				} else {
					normal = Vector.normalize(normalAtPoint.get(n));
				}
				results.add(e.addRoadPoint(w, n, normal));
			}
		}
		return results;
		
	}
	
	private List<EntranceCandidate> getEntranceRoads(EntranceCandidate e) {
		LatLon b1 = Main.getProjection().eastNorth2latlon(
				new EastNorth(e.buildingEndpoint.getX() - MAX_ENTRANCE_LENGTH, e.buildingEndpoint.getY() - MAX_ENTRANCE_LENGTH));
		LatLon b2 = Main.getProjection().eastNorth2latlon(
				new EastNorth(e.buildingEndpoint.getX() + MAX_ENTRANCE_LENGTH, e.buildingEndpoint.getY() + MAX_ENTRANCE_LENGTH));
		
		List<EntranceCandidate> results = new ArrayList<>();
		BBox bounds = new BBox(b1, b2);
		for (Way w: data.searchWays(bounds)) {
			if (w == e.buildingEndpoint.way || !(w.hasTag("rcr:type", "road", "building"))) 
				continue;
			if (w.hasTag("rcr:type", "building") && isConnectedBuilding(e.buildingEndpoint.way, w)) {
				continue;
			}
			for (EntranceCandidate e2 : makeEntranceToWay(e, w)) {
				if (e2.getLength() <= MAX_ENTRANCE_LENGTH && e2.getLength() >= MIN_ENTRANCE_LENGTH ) {
					results.add(e2);
				}
			}
		}
		return results;
	}
	
	
}
