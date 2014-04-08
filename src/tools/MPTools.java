package tools;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Pair;

import rcr.Vector;

public class MPTools {
	
	final static double SPLIT_HARD_SNAP_DISTANCE = 0.1;
	final static double SPLIT_SOFT_SNAP_DISTANCE = 2.0;
	final static double SPLIT_EXCENTRICITY_PENALTY = 1.0;
	
	final static boolean DEBUG = false;
	
	private static class WaySegment implements Comparable<WaySegment>{
		double location;
		List<Node> nodes;

		public WaySegment(List<Node> nodes) {
			this.nodes = new ArrayList<>(nodes);
			location = -1;
		}

		public WaySegment() {
			this.nodes = new ArrayList<>();
			location = -1;
		}
		
		public WaySegment(List<Node> nodes, Line2D split) {
			this.nodes = nodes;
			if (!nodes.isEmpty()) {
				calculateSplitNode(split);
			}
		}

		/**
		 * Calculate the distance to the first intersections along the split line.
		 * Make sure that the first node is closest to the start of the line.
		 * @param line
		 */
		private void calculateSplitNode(Line2D line) {
			Point2D p1 = Vector.asPoint(nodes.get(0).getEastNorth());
			Point2D p2 = Vector.asPoint(nodes.get(nodes.size()-1).getEastNorth());
			if (line.getP1().distance(p1) > line.getP1().distance(p2)) {
				Collections.reverse(nodes);
			}
			location = Math.min(line.getP1().distance(p1), line.getP1().distance(p2));
		}
		
		public Node getStartNode() {
			return nodes.get(0);
		}

		public Node getEndNode() {
			return nodes.get(nodes.size()-1);
		}
		
		public boolean isClosed() {		
			return nodes.size() >= 3 && nodes.get(0) == nodes.get(nodes.size()-1);
		}
		
		public Way asWay() {
			Way w = new Way();
			w.setNodes(this.nodes);
			return w;
		}
		
		public boolean canAppend(WaySegment other) {
			if (nodes.isEmpty() || other.nodes.isEmpty()) {
				return true;
			}
			return (getStartNode() == other.getStartNode() || getEndNode() == other.getStartNode()); 
		}

		public boolean canJoin(WaySegment other) {
			if (nodes.isEmpty() || other.nodes.isEmpty()) {
				return true;
			}
			return (getStartNode() == other.getStartNode() || getStartNode() == other.getEndNode()
					|| getEndNode() == other.getStartNode() || getEndNode() == other.getEndNode()); 
		}

		public void joinWith(WaySegment other, Line2D line) {
			joinWith(other);
			calculateSplitNode(line);
		}
		
		public void joinWith(WaySegment other) {
			if (nodes.isEmpty()) {
				nodes.addAll(other.nodes);
			}
			else if (getStartNode() == other.getEndNode()) {
				nodes.remove(0);
				nodes.addAll(0, other.nodes);
			}
			else if (getStartNode() == other.getStartNode()) {
				Collections.reverse(nodes);
				nodes.remove(nodes.size()-1);
				nodes.addAll(other.nodes);
			}
			else if (getEndNode() == other.getStartNode()) {
				nodes.remove(nodes.size()-1);
				nodes.addAll(other.nodes);
			}
			else if (getEndNode() == other.getEndNode()) {
				nodes.remove(nodes.size()-1);
				Collections.reverse(nodes);
				nodes.addAll(0,other.nodes);
			}
			else {
				assert false;
			}
			
		}
		
		public String toString() {
			if (nodes.isEmpty()) {
				return "empty Segment";
			}
			return "Segment ["+nodes.size()+" nodes] " + nodes.get(0) +  " -- " + nodes.get(nodes.size()-1) + ", position on line: " + location;						
		}

		@Override
		public int compareTo(WaySegment o) {
			return Double.compare(this.location, o.location);
		}
	}	

	public static Map<String, String> getMPTags(Relation mp) {
		assert mp.isMultipolygon();

		Map<String, String> combinedTags = new HashMap<>();
		combinedTags.putAll(mp.getKeys());
		combinedTags.remove("type");

		for (RelationMember m: mp.getMembers()) {
			if (m.hasRole("outer") && m.isWay()) {
				combinedTags.putAll(m.getMember().getKeys());
			}
		}
		return combinedTags;
	}
	
	public static Pair<List<Way>, List<Way>> getMPWays(Relation mp) {
		assert mp.isMultipolygon();

		List<Way> outer = new ArrayList<>();
		List<Way> inner = new ArrayList<>();
		
		for (RelationMember m: mp.getMembers()) {
			if (m.hasRole("outer") && m.isWay()) {
				outer.add(m.getWay());
			}
			else if (m.hasRole("inner") && m.isWay()) {
				inner.add(m.getWay());
			}
		}
		return Pair.create(assembleWays(outer), assembleWays(inner));
	}
	
	/**
	 * Take a list of (possibly open) ways and join them into closed ways if possible.
	 * @param ways
	 * @return a list of closed ways
	 */
	public static List<Way> assembleWays(List<Way> ways) {
		List<Way> result = new ArrayList<>();
		List<Way> open = new ArrayList<>(ways);
		
		while (!open.isEmpty()) {
			Way w = open.remove(open.size()-1);
			if (w.isClosed()) {
				result.add(w);
			}
			else {
				List<Node> newNodes = null;
				for (Way w2 : open) {
					List<Node> head = null, tail = null;
					if (w.firstNode() == w2.firstNode() || w.lastNode() == w2.lastNode() ) {
						head = new ArrayList<>(w.getNodes());
						Collections.reverse(head);
						tail = w2.getNodes();
					}
					else if (w.lastNode() == w2.firstNode()) {
						head = new ArrayList<>(w.getNodes());
						tail = w2.getNodes();
					}
					else if (w.firstNode() == w2.lastNode()) {
						head = new ArrayList<>(w2.getNodes());
						tail = w.getNodes();
					}
					if (head != null) {
						if (head.get(head.size()-1) == tail.get(0)) {
							head.remove(head.size()-1);
							head.addAll(tail);
						}
						else {
							head.remove(0);
							head.addAll(0, tail);
							
						}
						open.remove(w2);
						newNodes = head;
						break;
					}
				}
				if (newNodes != null) {
					// creating a new way every time might be a bit wasteful, but ensures we don't stomp
					// upon other's data
					Way newWay = new Way(w);
					newWay.setNodes(newNodes);
					open.add(newWay);
				}
			}
		}
		return result;
	}
	
	public static List<Way> segmentMP(Relation mp) {
		Map<String, String> combinedTags = getMPTags(mp);
		Pair<List<Way>, List<Way>> ways = getMPWays(mp);
		List<Way> outer = ways.a;

		List<Way> result = segmentMPRecursive(ways.a, ways.b);
		for (Way w : result) {
			if (!outer.contains(w)) {
				//FIXME: add tags to unchanged outer rings
				w.setKeys(combinedTags);
			}
		}
		return result;		
	}
	
	public static Rectangle2D getBoundingRect(Relation mp) {
		Pair<List<Way>, List<Way>> ways = getMPWays(mp);
		return getBoundingRect(ways.a); 
	}
	
	public static Rectangle2D getBoundingRect(List<Way> ways) {
		BBox bounds = new BBox(ways.get(0));
		for (Way way : ways) {
			bounds.addPrimitive(way, 0);
		}
		EastNorth b1 = Main.getProjection().latlon2eastNorth(bounds.getBottomRight());
		EastNorth b2 = Main.getProjection().latlon2eastNorth(bounds.getTopLeft());
		double x = Math.min(b1.getX(), b2.getX());
		double y = Math.min(b1.getY(), b2.getY());
		double w = Math.max(b1.getX(), b2.getX()) - x;
		double h = Math.max(b1.getY(), b2.getY()) - y;
		return new Rectangle2D.Double(x,y,w,h);
	}
	
	public static boolean isMultipolygon (Relation rel, Bounds bounds, String key, String... values) {
		if (!rel.isMultipolygon()) {
			return false;
		}
		Map<String, String> combinedTags = new HashMap<>();
		combinedTags.putAll(rel.getKeys());
		combinedTags.remove("type");
		
		BBox bbox = bounds.toBBox();
		for (RelationMember m: rel.getMembers()) {
			if (m.hasRole("outer") && m.getWay() != null) {
				//FIXME: could break for key=val1 on relation and key=val2 on outer ring
				combinedTags.putAll(m.getMember().getKeys());
				if (!bbox.bounds(m.getWay().getBBox())) {
					return false;
				}
			}
		}
		if (combinedTags.containsKey(key)) {
			if (values.length == 0) {
				return true;
			}
			for (String v : values) {
				if (combinedTags.get(key).equals(v)) {
					return true;
				}
			}
		}
		return false;
	}
	
	
	public static Collection<Relation> getMultipolygons(Way w, boolean outerOnly) {
		List<Relation> result = new ArrayList<>();
		for (OsmPrimitive osm : w.getReferrers()) {
			if ((osm instanceof Relation) && ((Relation) osm).isMultipolygon()) {
				Relation r = (Relation) osm;
				if (!outerOnly) {
					result.add(r);
				}
				else {
					for (RelationMember m : r.getMembers()) {
						if (m.hasRole("outer") && m.getWay() == w) {
							result.add(r);
							break;
						}
					}
				}
			}
		}
		return result;
	}
			
	
	public static boolean isPartOfMP(Way w, String key, String... values) {
		for (Relation r : getMultipolygons(w, false)) {			
			boolean hasTag = values.length > 0 ? r.hasTag(key, values) : r.hasKey(key);
			boolean innerHasTag = false;
			boolean isOuter = false;
			boolean isInner = false;

			for (RelationMember m : r.getMembers()) {
				if (m.hasRole("outer") && m.getWay() != null) {
					hasTag |= values.length > 0 ? m.getWay().hasTag(key, values) : m.getWay().hasKey(key);
					isOuter |= (m.getWay() == w);
				} else if (m.hasRole("inner") && m.getWay() == w) {
					innerHasTag |= values.length > 0 ? m.getWay().hasTag(key, values) : m.getWay().hasKey(key);
					isOuter |= true;
				}
			}
			if (hasTag && isOuter) {
				return true;
			}
			if (hasTag && isInner && innerHasTag) {
				// FIXME: what is the semantics of identical tags on outer/inner
				// ?
				return true;
			}
		}
		return false;
		
	}
	
	@SuppressWarnings("unused")
	private static List<Way> segmentMPRecursive(List<Way> outer, List<Way> inner) {
		if (inner.isEmpty()) {
			return outer;
		}
		
		Rectangle2D b = getBoundingRect(outer);
		System.out.printf("Bounds: (%f, %f) + (%f, %f)\n", b.getX(), b.getY(), b.getWidth(), b.getHeight());
		
		Line2D line = getBestSplit(outer, inner);
		
		Pair<List<Way>, List<Way>> split= splitMP(outer, inner, line);
		
		if (split.b.isEmpty()) {
			return split.a;
		}
		
		List<Way> outer1 = new ArrayList<>(); 
		List<Way> inner1 = new ArrayList<>();
		List<Way> outer2 = new ArrayList<>(); 
		List<Way> inner2 = new ArrayList<>();
		
		for (Way way : split.a) {
			if (isLeftOfLine(way, line)) {
				outer1.add(way);
			}
			else {
				outer2.add(way);				
			}
		}
		for (Way way : split.b) {
			if (isLeftOfLine(way, line)) {
				inner1.add(way);
			}
			else {
				inner2.add(way);				
			}
		}
		
		assert (outer1.size() < split.a.size() && outer2.size() < split.a.size()) || DEBUG;
		if ((outer1.size() == split.a.size() || outer2.size() == split.a.size()) && DEBUG) {
			System.err.println("split did not partition segments");
			System.err.println("line: " + line.getP1() + " -- " + line.getP2());
			Way lway = new Way();
			lway.addNode(new Node(Vector.asEastNorth(line.getP1())));
			lway.addNode(new Node(Vector.asEastNorth(line.getP2())));
			split.a.add(lway);
			return split.a;
		}
		
		assert(split.b.size() < inner.size() || DEBUG);
		if (split.b.size() >= inner.size() && DEBUG) {
			System.err.println("number of inner rings did not decrease. Aborting split.");
			List<Way> result = new ArrayList<>();
			result.addAll(split.a);
			result.addAll(split.b);
			Way lway = new Way();
			lway.addNode(new Node(Vector.asEastNorth(line.getP1())));
			lway.addNode(new Node(Vector.asEastNorth(line.getP2())));
			result.add(lway);
			return result;
		}
		
		List<Way> result = new ArrayList<>();
		result.addAll(segmentMPRecursive(outer1, inner1));
		result.addAll(segmentMPRecursive(outer2, inner2));
		return result;
	}
	
	public static boolean isLeftOfLine(Way w, Line2D l) {
		return isLeftOfLine(w.getNodes(), l);
	}

	public static boolean isLeftOfLine(List<Node> nodes, Line2D l) {
		Point2D normal = Vector.normalVec(Vector.fromLine(l));
		double sum = 0;
		for (Node n : nodes) {
			Point2D vec = Vector.diff(Vector.asPoint(n.getEastNorth()), l.getP1());
			sum += Vector.dotProduct(vec, normal);
		}
		return sum > 0;
	}

	public static boolean isOnLine(List<Node> nodes, Line2D l) {
		for (Node n : nodes) {
			if (l.ptLineDist(Vector.asPoint(n.getEastNorth())) > SPLIT_HARD_SNAP_DISTANCE) {
				return false;
			}
		}
		return true;
	}
	
	public static Pair<List<Way>, List<Way>> splitMP(Relation mp, Line2D line) {
		assert mp.isMultipolygon();

		Map<String, String> combinedTags = getMPTags(mp);
		Pair<List<Way>, List<Way>> ways = getMPWays(mp);
		List<Way> outer = ways.a;
		
		Pair<List<Way>, List<Way>> split = splitMP(ways.a, ways.b, line);
		for (Way w : split.a) {
			if (!outer.contains(w)) {
				//FIXME: add tags to unchanged outer rings
				w.setKeys(combinedTags);
			}
		}
		return split;
		
	}
	
	public static boolean isClockwise(List<Node> nodes) {
		double area = 0;
		EastNorth first = null, prev = null;
		for(Node n : nodes) {
			if (first == null) {
				first = n.getEastNorth();
			}
			EastNorth c = n.getEastNorth();
			if (prev != null) {
				area += prev.getX()*c.getY() - c.getX()*prev.getY();
			}
			prev = c;
		}
		if (!prev.equals(first)) {
			area += prev.getX()*first.getY() - first.getX()*prev.getY();
		}
		return area < 0;
	}
		
	public static Pair<List<Way>, List<Way>> splitMP(List<Way> outer, List<Way> inner, Line2D line) {
		List<Way> newOuter = new ArrayList<>();
		List<Way> newInner = new ArrayList<>();
		
		List<WaySegment> shapeSegments = new ArrayList<>();
		
		for (Way w : outer) {
			List<WaySegment> splits = splitWay(w, line, true);
			if (splits.size() > 2) {
				shapeSegments.addAll(splits);
			}
			else {
				newOuter.add(w);
			}
		}
		for (Way w : inner) {
			List<WaySegment> splits = splitWay(w, line, false);
			if (splits.size() > 2) {
				shapeSegments.addAll(splits);
			}
			else {
				newInner.add(w);
			}
		}
		
		Collections.sort(shapeSegments);
		
		Deque<WaySegment> leftStack = new ArrayDeque<>();
		Deque<WaySegment> rightStack = new ArrayDeque<>();
		WaySegment left = new WaySegment();
		WaySegment right = new WaySegment();

		// Traverse split ways in order of their intersections along the split line
		// and stitch them together into new, simple shapes. At any time we have two open 
		// ways which are stored in left/right (direction as seen from the split line orientation). 
		Node prevSplitNode = null;
		for (WaySegment e : shapeSegments) {
			System.out.println("considering segment " + e);
			
			if (prevSplitNode != null && prevSplitNode != e.getStartNode()) {
				// close the gap along the cut line with a new way segment
				System.out.println("new split node");
				WaySegment gap = new WaySegment(Arrays.asList(prevSplitNode, e.getStartNode()));
				System.out.println("  closing gap: " + gap);
				if (left.canAppend(gap)) {
					System.out.println("    add to left: " + left);
					left.joinWith(gap);
				}
				if (right.canAppend(gap)) {
					System.out.println("    add to right. " + right);
					right.joinWith(gap);
				}
			}
			prevSplitNode = e.getStartNode();
			
			if (left.isClosed()) {
				Way newWay = left.asWay();
				newWay.put("rcr:split", "yes");
				newOuter.add(newWay);
				System.out.println("  left is closed, stack has " + leftStack.size() + " elements");
				left = !leftStack.isEmpty() ? leftStack.pop() : new WaySegment();
			}
			if (right.isClosed()) {
				Way newWay = right.asWay();
				newWay.put("rcr:split", "yes");
				newOuter.add(newWay);
				System.out.println("  right is closed, stack has " + rightStack.size() + " elements");
				right = !rightStack.isEmpty() ? rightStack.pop() : new WaySegment();
			}

			// Don't append segments we can't determine if they belong to the left or
			// right side. We will close gaps when we move to the next split node.
			if (e.nodes.size() < 3 || isOnLine(e.nodes, line)) {
				continue;
			}
						
			if (isLeftOfLine(e.nodes, line)) {
				if (!left.canJoin(e)) {
					System.out.println("push left:" + left);
					leftStack.push(left);
					left = new WaySegment();
				}
				left.joinWith(e);
				System.out.println("  merge to left: " + left);
			}
			else if (!isLeftOfLine(e.nodes, line)) {
				if (!right.canJoin(e)) {
					System.out.println("push right:" + right);
					rightStack.push(right);
					right = new WaySegment();
				}
				right.joinWith(e);
				System.out.println("  merge to right: " + right);
			}			
		}
		
		assert left.nodes.size() == 0;
		assert right.nodes.size() == 0;
		assert leftStack.isEmpty();
		assert rightStack.isEmpty();

		if (DEBUG) {
			for (WaySegment e : shapeSegments) {
				Way newWay = e.asWay();
				newWay.put("rcr:isclosed", Boolean.toString(newWay.isClosed()));
				newWay.put("rcr:dist", Double.toString(e.location));
				newWay.put("rcr:splitnode", e.getStartNode().toString());
				newWay.put("rcr:nodecount", Integer.toString(e.nodes.size()));
				newWay.put("rcr:debug", "true");
				newOuter.add(newWay);
			}
		}
		
		return Pair.create(newOuter, newInner);		
	}


	/**
	 * Split one way by a line. Return each partial way as a SplitEntry /twice/, once
	 * for each possible direction. 
	 * @param w
	 * @param line
	 * @param clockwise
	 * @return List of SplitEntries
	 */
	@SuppressWarnings("unused")
	static List<WaySegment> splitWay(Way w, Line2D line, boolean clockwise) {
		if (DEBUG && w.isKeyTrue("rcr:debug")) {
			return Collections.emptyList();
		}
		assert w.isClosed();
		
		List<WaySegment> result = new ArrayList<>();
		
		System.out.println("Splitting: " + w.getId());
		
		List<Node> currentNodes = new ArrayList<>();
		for (Pair<Node, Node> p : w.getNodePairs(false)) {
			if (currentNodes.isEmpty()) {
				currentNodes.add(p.a);
			}
			System.out.println("segment " + p.a + " -- " + p.b );
			Point2D p1 = Vector.asPoint(p.a.getEastNorth());
			Point2D p2 = Vector.asPoint(p.b.getEastNorth());

			List<Node> newNodes = null;
			
			// Line intersects first node: already handled previously by next case 
			if (line.ptSegDist(p2) <= SPLIT_HARD_SNAP_DISTANCE) {
				// Line intersects second node
				currentNodes.add(p.b);
				System.out.println("add end node" + p.b);
				newNodes = new ArrayList<>();
				System.out.println(" -- new list --");
				newNodes.add(p.b);
				System.out.println("add end node" + p.b);
			}
			else {
				Point2D i = Vector.getIntersectionPoint(p1, p2, line.getP1(), line.getP2());
				
				if (i != null) {
					if (i.distance(p1) <= SPLIT_SOFT_SNAP_DISTANCE && i.distance(p1) <= i.distance(p2)) {
						//Snap to p1
						// p1 was already added
						System.out.println("snap " + i + " to head at " + p1);
						System.out.println("add" + p.a);
						newNodes = new ArrayList<>();
						System.out.println(" -- new list --");
						newNodes.add(p.a);
						System.out.println("add" + p.a);
					}
					else if (i.distance(p2) <= SPLIT_SOFT_SNAP_DISTANCE) {
						//Snap to p2
						currentNodes.add(p.b);
						System.out.println("add end node" + p.b);
						newNodes = new ArrayList<>();
						System.out.println(" -- new list --");
						newNodes.add(p.b);
						System.out.println("add end node" + p.b);
					}
					else {
						Node splitNode = new Node(Vector.asEastNorth(i));
						currentNodes.add(splitNode);
						System.out.println("split at " + i);
						System.out.println("add" + splitNode);
						newNodes = new ArrayList<>();
						System.out.println(" -- new list --");
						newNodes.add(splitNode);
						System.out.println("add" + splitNode);
					}
				}
			}
			
			if (newNodes != null) {
				result.add(new WaySegment(currentNodes, line));
				currentNodes = newNodes;
				newNodes = null;
			}
			
			if (currentNodes.isEmpty() || currentNodes.get(currentNodes.size()-1) != p.b) { 
				currentNodes.add(p.b);
				System.out.println("add" + p.b);
			}
		}
		
		if (result.size() == 1) {
			assert result.get(0).isClosed();
		}
		else if (result.size() > 1){
			//Complete first segment
			if (!currentNodes.isEmpty()) {
				if (!result.get(0).nodes.isEmpty()) {
					System.out.println("fixup first/last segment:  " + currentNodes.get(currentNodes.size()-1) + " <-> " + result.get(0).nodes.get(0) );
				}
				else {
					System.out.println("fixup first/last segment:  " + currentNodes.get(currentNodes.size()-1) + " <-> empty");			
				}
				result.get(0).joinWith(new WaySegment(currentNodes), line);
			}			
		}
				
		// Add single-node segments for end nodes, so we don't miss intersections
		// when assembling the new ways. This may add redundant segments, but those won't hurt.
		for (WaySegment s : new ArrayList<>(result)) {
			if (s.nodes.size() > 1 || !s.isClosed()) {
				result.add(new WaySegment(Collections.singletonList(s.getEndNode()), line));
			}
		}
				
		if (DEBUG) {
			for (WaySegment e : result) {
				System.out.println(e);					
				assert !e.isClosed();
			}
		}
		
		return result;
	}

	private static List<Line2D> getSegments(List<Way> outer, List<Way> inner) {
		List<Way> all = new ArrayList<>(outer);
		all.addAll(inner);
		return getSegments(all);
		
	}
	
	private static List<Line2D> getSegments(List<Way> ways) {
		List<Line2D> result = new ArrayList<>();
		for (Way w: ways) {
			result.addAll(AreaTools.getSegments(w));
		}
		return result;
	}
	
	private static Line2D getBestSplit(List<Way> outer, List<Way> inner) {
		SplitLineComparator comparator = new SplitLineComparator(outer, inner);
		List<Line2D> lines = getSegments(inner);
		Collections.sort(lines, comparator);
		
		// Extend best split line, so that it's guaranteed to intersect the entire MP
		BBox b = new BBox(outer.get(0));
		for (Way w : outer) {
			b.addPrimitive(w, 0);
		}
		Point2D b1 = Vector.asPoint(Main.getProjection().latlon2eastNorth(b.getTopLeft()));
		Point2D b2 = Vector.asPoint(Main.getProjection().latlon2eastNorth(b.getBottomRight()));
//		System.out.printf("BBox: %f,%f+%fx%f\n", bbox.getX(), bbox.getY(), bbox.getWidth(), bbox.getHeight());
		double maxDiameter = b1.distance(b2);
		System.out.println("diameter:" + maxDiameter);
		Line2D l0 = lines.get(lines.size()-1);
		Point2D extension = Vector.times(Vector.normalize(Vector.fromLine(l0)), maxDiameter);
		Point2D extP1 = Vector.diff(l0.getP1(), extension); 
		Point2D extP2 = Vector.sum(l0.getP2(), extension);
		System.out.println(extension);
		System.out.println(extP1);
		System.out.println(extP2);
		
		return new Line2D.Double(extP1, extP2);
	}
	
		
	private static class SplitLineComparator implements Comparator<Line2D> {

		private List<Line2D> segments;
		private Point2D centroid;
		
		public SplitLineComparator(List<Way> outer, List<Way> inner) {
			segments = getSegments(outer, inner);
			computeCentroid();
		}
		
		private void computeCentroid() {
			double cx = 0, cy = 0, area = 0;
			for (Line2D l: segments) {
				area += 0.5*(l.getX1()*l.getY2() - l.getX2()*l.getY1());
				cx += (l.getX1()+l.getX2()) * (l.getX1()*l.getY2() - l.getX2()*l.getY1());
				cy += (l.getY1()+l.getY2()) * (l.getX1()*l.getY2() - l.getX2()*l.getY1());
			}
			cx /= 6*area;
			cy /= 6*area;
			centroid = new Point2D.Double(cx, cy);
			
		}
		
		private double weightedLineFit(Line2D line) {
			Point2D direction = Vector.normalize(Vector.fromLine(line));
		
			double sum = 0;
			for (Line2D l2 : segments) {
				Point2D vec2 = Vector.fromLine(l2);
				Point2D dir2 = Vector.normalize(vec2);
				double cosA = Vector.dotProduct(direction, dir2);
//				double deviation = 1 - 2*Math.abs(cosA-0.5); // = 0 for 0 and 1, 1 for 1/2
				double deviation = 1 - 2*Math.max(Math.abs(cosA), 0.2); // = 1 for 0 and 0 for 0.2 - 1
				sum += deviation * Vector.length(vec2);
			}

			return sum;
		}
		
		
		@Override
		public int compare(Line2D arg0, Line2D arg1) {
			double fit0 = weightedLineFit(arg0);
			double fit1 = weightedLineFit(arg1);
			double cdist0 = arg0.ptLineDist(centroid) * SPLIT_EXCENTRICITY_PENALTY;
			double cdist1 = arg1.ptLineDist(centroid) * SPLIT_EXCENTRICITY_PENALTY;
			return Double.compare(fit0-cdist0, fit1-cdist1);
		}
		
	}
		
}
