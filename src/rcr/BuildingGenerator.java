/* Copyright (c) 2009, Research Group on the Foundations of Artificial
 * Intelligence, Department of Computer Science, Albert-Ludwigs
 * University Freiburg.
 * All rights reserved.
 *
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the
 * distribution.
 * The name of the
 * author may not be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
*/

package rcr;

import static rcr.RCRDataSet.parseInt;
import static rcr.Vector.fromLine;
import static rcr.Vector.length;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Pair;

import tools.AdvArea;
import tools.MPTools;

public class BuildingGenerator {
	private RCRDataSet m_data;
	private DataSet m_source;

	private List<AdvArea> areaPolys = new LinkedList<AdvArea>();
	Random random = new Random();

	boolean db = false;
	private static double minBuildingSize = 300;
	
	public BuildingGenerator(RCRDataSet data, DataSet source) {
		m_data = data;
		m_source = source;
	}
	
	/**
	 * Based on the sourceData this function tries to fill
	 * unmapped areas with buildings.
	 * @param bounds 
	 */
	public void generateBuildings(Bounds bounds) {
		List<AdvArea> polygons = getFreePolygons(m_source, bounds);
		areaPolys = createWayPolys();
		List<AdvArea> forbidden = new ArrayList<AdvArea>();
		List<AdvArea> split = new ArrayList<AdvArea>();
		List<AdvArea> cuts = new ArrayList<AdvArea>();
		List<AdvArea> firstSplit = new ArrayList<AdvArea>();

		List<AdvArea> filteredPolygons = filterFreePolys(polygons, m_source, bounds, forbidden, split, cuts, firstSplit);
		System.out.println("Filtered " + polygons.size() + " retained " + filteredPolygons.size() + " blocks.");
		
		List<AdvArea> containedAres = new ArrayList<AdvArea>();
		List<AdvArea> containingAres = new ArrayList<AdvArea>();
		List<AdvArea> filteredContained = filterContainedPolys(filteredPolygons, m_source, bounds, containingAres, containedAres);
		System.out.println("Contained filter " + filteredPolygons.size() + " -> " + filteredContained.size());
		createNewBuildings(filteredContained);


		for(AdvArea a : containedAres) {
	//		Way w = makeBuildingBlock(a);
	//		w.put("rcr:type", "containedblock");
	//		m_data.addPrimitive(w);
		}
		for(AdvArea a : containingAres) {
	//		Way w = makeBuildingBlock(a);
	//		w.put("rcr:type", "containingblock");
	//		m_data.addPrimitive(w);
		}
		
		for(AdvArea a : filteredPolygons) {
			//Way w = makeBuildingBlock(a);
			//w.put("rcr:type", "filteredblock");
		//	m_data.addPrimitive(w);
	}
		for(AdvArea a : forbidden) {
			//Way w = makeBuildingBlock(a);
			//w.put("rcr:type", "forbiddenblock");
			//m_data.addPrimitive(w);
		}
		for(AdvArea a : split) {
			//Way w = makeBuildingBlock(a);
			//w.put("rcr:type", "splitblock");
			//m_data.addPrimitive(w);
		}
		for(AdvArea a : cuts) {
			//Way w = makeBuildingBlock(a);
			//w.put("rcr:type", "cutblock");
			//m_data.addPrimitive(w);
		}
		for(AdvArea a : firstSplit) {
			//Way w = makeBuildingBlock(a);
			//w.put("rcr:type", "firstsplit");
			//m_data.addPrimitive(w);
		}
	}

	private List<AdvArea> filterContainedPolys(List<AdvArea> filteredPolygons,
			DataSet mSource, Bounds bounds, List<AdvArea> containingAreas, List<AdvArea> containedAreas) {
		List<AdvArea> ret = new ArrayList<AdvArea>();

		//TODO: bounding boxes
		for(AdvArea p : filteredPolygons) {
			boolean containsOther = false;
			for(AdvArea p2 : filteredPolygons) {
				if(p == p2)
					continue;
				if(p.contains(p2)) {
					containsOther = true;
					containingAreas.add(p);
					containedAreas.add(p2);
					break;
				}
				if(p.area() > p2.area()) {		// possible containment
					// Do "about" containment check accounting for rounding
					// errors from conversion, if intersection is about same area, it's about the same.
					AdvArea pCopy = new AdvArea(p);
					pCopy.intersect(p2);

					double deltaIntersect = Math.abs(pCopy.area() - p2.area());
					if(p2.area() > 10.0 && deltaIntersect < 10.0) {	//TODO: min block area filten
						containsOther = true;
						containingAreas.add(p);
						containedAreas.add(p2);
						break;
					}
				}
				//TODO: advanced split 3ways deal
			}
			if(!containsOther) {
				ret.add(p);
			}

		}
		
		return ret;
	}

	private static boolean hasForbiddenBuildingTags(OsmPrimitive osm) {
		return (osm.hasKey("building") ||
				osm.hasTag("amenity", "place_of_worship", "public_building", "parking") ||
				osm.hasKey("leisure") ||
				(osm.hasKey("highway") && osm.hasAreaTags()) ||
				(osm.hasKey("highway") && osm.hasTag("type", "multipolygon")) ||
				osm.hasKey("natural") ||
				osm.hasTag("landuse", "cemetery", "forest", "allotments", "plaza", "farm", "grass", "recreation_ground") ||
				osm.hasTag("waterway", "riverbank"));		
	}

	private List<AdvArea> filterFreePolys(List<AdvArea> polygons, DataSet data, Bounds bounds, List<AdvArea> forbidden, List<AdvArea> split, List<AdvArea> cuts, List<AdvArea> firstSplit) {
		for(Way w : data.searchWays(new BBox(bounds.getMin(), bounds.getMax()))) {
			/*boolean coordWithin = false;
			for(Node n: w.nodes) {
				if(n.getCoor().isWithin(bounds)) {
					coordWithin = true;
					break;
				}
			}
			if(!coordWithin) {
				continue;		// no need to add ways, that are out of bounds with each corner
			}*/
			if (hasForbiddenBuildingTags(w)) {
				forbidden.add(new AdvArea(w.getNodes(), w.getId()));
			}
			else {
				for (Relation r : MPTools.getMultipolygons(w, true)) {
					if (hasForbiddenBuildingTags(r)) {
						forbidden.add(new AdvArea(w.getNodes(), w.getId()));
						break;
					}
				}
			}
		}

		List<AdvArea> oldPolys = new ArrayList<AdvArea>();
		oldPolys.addAll(polygons);
		List<AdvArea> newPolys = new ArrayList<AdvArea>();
		for(AdvArea no : forbidden) {		// substract this from all polys
			AdvArea scaledNo = new AdvArea(no);
			scaledNo.scale(3);
			for(AdvArea a : oldPolys) {
				if(a.intersects(no)) {
					if(a.contains(no)) {	// Full containment, we need to do an artificial cut
						AdvArea cutArea = no.suggestCutArea();
						cuts.add(cutArea);
						a.subtract(cutArea);
						for(AdvArea as : a.split()) {
							firstSplit.add(new AdvArea(as));
							as.subtract(scaledNo);
							List<AdvArea> parts = as.split();
							for(AdvArea p : parts) {
								split.add(new AdvArea(p));
								newPolys.add(new AdvArea(p));
							}
						}
					} else {
						a.subtract(scaledNo);
						
						List<AdvArea> parts = a.split();
						for(AdvArea p : parts) {
							split.add(new AdvArea(p));
							newPolys.add(new AdvArea(p));
						}
					}
					
					// TODO check parts here
					// check rest area+simple(trivial?)+sideRation -> define global
				} else {			// this is ok for "no", so keep it
					newPolys.add(a);
				}
			}
			oldPolys = new ArrayList<AdvArea>(newPolys);
			newPolys.clear();
		}

		return oldPolys;	// this were the last newpolys
	}

	//TODO: are there buildings nodes with ways somewhere totally else?
	//TODO: use bounds!
	/**
	 * TODO: at some time rename crossings->junctions...
	 * 
	 * This function finds unoccupied polygons in data, that should be filled
	 * with buildings. Coordinates are metric rescue coordinates, that might
	 * be converted back to OSM ways.
	 * Or coords are just UTM * factor or whatever...
	 * should be easy and non-bugprone...
	 * @param bounds 
	 */
	private List<AdvArea> getFreePolygons(DataSet data, Bounds bounds) {
		// First try to identify crossings
		// crossings are nodes, that appear in more than one way
		HashMap<Long, Node> nodes = new HashMap<Long, Node>();
		HashMap<Long, Set<Way>> crossings = new HashMap<Long, Set<Way>>();	// node id -> ways
		
		// aittrib filter againt: building/landuse/warum ist HJkirch in fr da?
		for(Way w : data.searchWays(new BBox(bounds.getMax(), bounds.getMin()))) {
			// there's probably more to exclude to get what we want
			//TODO: This is wrong - building/area have to be included
			// only NOT put into a poly, when the poly consists only of these
			// leaving it for now -> later:
			// all in and remove buildings/areas from polys
			if(w.isKeyTrue("building"))
				continue;
			if(w.isKeyTrue("leisure"))	// substract later
				continue;
			//if(parseBoolTag(w, "area"))
				//continue;
			//if(parseBoolTag(w, "landuse"))
				//continue;
			if(w.get("layer") != null)
				if(new Integer(w.get("layer")).intValue() < 0)	// no underground stuff
					continue;
			for(Node n : w.getNodes()) {
				if(n.getCoor()== null || !n.getCoor().isWithin(bounds))
					continue;
				nodes.put(n.getId(), n);
				if(crossings.get(n.getId()) == null) {
					crossings.put(n.getId(), new HashSet<Way>());
				}
				Set<Way> crossingWays = crossings.get(n.getId());
				crossingWays.add(w);
			}
		}

		System.out.print("From " + crossings.size() + " node->way entries, retained ");
		
		// to get the real crossings remove all node->way mappings, that only have one way
		// left are the >= 2 ways per node entries 
		Iterator< Entry<Long, Set<Way>> > crIt = crossings.entrySet().iterator();
		while(crIt.hasNext()) {
			Entry<Long, Set<Way>> e = crIt.next();
		
			if(e.getValue().size() <= 1)
				crIt.remove();
		}
		System.out.println(crossings.size() + " true crossings.");
		
		/// Prob: crossings might be there, but not with the same node....
		/// Waterways etc are obvious crossings!
		/// Should do stuff like *intersecting way segment = crossing
		/// Perhaps dealing with all intersecting ways additionally?!?
		
		// Now by following the crossings along the ways
		// try to find (minimal) closures -> a possbly free polygon
		List<AdvArea> polys = new ArrayList<AdvArea>();
		
		Iterator< Entry<Long, Set<Way>> > crIt2 = crossings.entrySet().iterator();
		Set<Set<Node>> foundCircles = new HashSet<Set<Node>>();	// needs to be a set as different start points/directions are considered same
		while(crIt2.hasNext()) {
			Entry<Long, Set<Way>> e = crIt2.next();
		
			Node start = nodes.get(e.getKey());
			if(start == null) {	// should never happen
				assert false;
				continue;
			}
			Set<Way> ways = e.getValue();

			// try to walk all possible directions
			for(Way w : ways) {
				Set<Long> visited = new HashSet<Long>();
				List<Node> circ = walkCircle(start, w, true, crossings, start, visited, 0, 0);
				if(circ != null && circ.size() < 3)	// degenerate case
					circ = null;
				if (circ != null) {
					Set<Node> circNodes = new HashSet<Node>();
					for (Node circN : circ)
						circNodes.add(circN);
					if (!foundCircles.contains(circNodes)) {
						foundCircles.add(circNodes);
					} else {
						circ = null;
					}
				}
				
				visited.clear();
				List<Node> circ2 = walkCircle(start, w, false, crossings, start, visited, 0, 0);
				if(circ2 != null && circ2.size() < 3)
					circ2 = null;
				if(circ2 != null) {
					Set<Node> circ2Nodes = new HashSet<Node>();
					for(Node circ2N : circ2)
						circ2Nodes.add(circ2N);
					if(!foundCircles.contains(circ2Nodes)) {
						foundCircles.add(circ2Nodes);
					} else {
						circ2 = null;
					}
				}

				if(circ != null) {
					AdvArea ap1 = new AdvArea(circ);
					ap1.ref = w.getId();
					ap1.nref = start.getId();
					ap1.dirRef = true;
					if(ap1.isSimple())
						polys.add(ap1);
				}
				if(circ2 != null) {
					AdvArea ap2 = new AdvArea(circ2);
					ap2.ref = w.getId();
					ap2.nref = start.getId();
					ap2.dirRef = false;
					if(ap2.isSimple())
						polys.add(ap2);
				}
				
			}
		}
		
		System.out.println("removing by size from " + polys.size());
		Iterator<AdvArea> polyIt = polys.iterator();
		while(polyIt.hasNext()) {
			AdvArea p = polyIt.next();
			double minPolyArea = Math.pow(20*1.0, 2.0);
			double maxPolyArea = Math.pow(500*1.0, 2.0);
			double area = p.area();
			//TODO: check this works
			if(area > maxPolyArea)
				polyIt.remove();
			else if(area < minPolyArea)
				polyIt.remove();
			else if(p.toPoints().size()<3) {
				System.out.println("Removed degenerated walkcircle area.");
				polyIt.remove();
			}
			else {

			}
			// TODO:
			// don't add to small/degenerate ones/huge
			// remove doubles
			// remove self-intersecting polys
			// remove very thin/wide ones	
		}
		System.out.println("removed by size to " + polys.size());
		
		/// * Prob: minimal? circumfence OK? can this be A* like?
		///-> somehow define: minimal = no other goes "through" it
		///-> if goes through = this is the new minimal
		/// Direct possible (right hand...?)
		/// Prob2: doubles - different start crossing -> different poly
		/// Prob3: not all ways are part of surrounding -> stop at some time
		/// dont generate huge empty spaces...
		/// Prob4: Dont find closed way (building) as itslef
		
		// Finally shrinken/discard polygons, that already contain
		// data (buildings, etc.)
		// TODO:

		return polys;
	}
	
	private List<AdvArea> createWayPolys() {
		List<AdvArea> ret = new ArrayList<AdvArea>();
		for(Way w : m_source.getWays()) {
			if(!w.isClosed())
				continue;
			boolean ok = false;
			if(w.isKeyTrue("building"))
				ok = true;
			if(w.isKeyTrue("area"))
				ok = true;
			//if(!ok)
			//	continue;
			//Rectangle2D.Double bbox = RCRDataSet.makeBoundingBox(w.nodes);
			//FIXME: reimplement or something
			//bboxes.put(w, bbox);
			
			ret.add(new AdvArea(w.getNodes()));
		}
		return ret;
	}

	private void createNewBuildings(List<AdvArea> polygons) {
		while(polygons.size() > 0) {
			List<AdvArea> restPolys = new ArrayList<AdvArea>();
			// to finish the loop: in each step either dont add p to rest
			// or place house in p and add rest polys
			//TODO: implement - this is disabled atm for random split method
			int count = 0;
			for(AdvArea a : polygons) {
				// check placeable
				// TODO: area?
				// place random + return rest
				// List<AdvPolygon> rest = placeRandomHouse(p);
				// save rest and iterate
				// restPolys.addAll(rest);
				
				a.scale(-3);
				List<AdvArea> areas = a.split();
				for (AdvArea a2 : areas) {
					double area = a2.area();
					if (area < minBuildingSize*2)
						continue;
					if (a2.sideRatio() <= 7|| (area>100*100 && a2.sideRatio() <= 15)) {
						//placeRandomHouse(a2);
						Way block = makeBuildingBlock(a2);
						block.put("rcr:type", "block");
						block.put("rcr:ref", new Long(a.ref).toString());
						block.put("rcr:nref", new Long(a.nref).toString());
						block.put("rcr:dirref", new Boolean(a.dirRef).toString());
						m_data.addPrimitive(block);
						count++;
					}
				}
				//addPrimitive(makeBuildingWay(p.toNodeList(new long[] {0})));
			}
			System.out.println("create: retained " + count + " final blocks");
			polygons = restPolys;
		}
	}
	
	public Way makeBuildingBlock(AdvArea a) {
		Way w = new Way();
		for(Node n : a.toNodeList()) {
			Node newNode = new Node(n);
			w.addNode(newNode);
			m_data.addPrimitive(newNode);
		}
			
		w.addNode(w.firstNode());

		double area = a.area();
		//w.put("rcr:type", "block");
		int maxSize = (int) Math.max(2000, area/2*random.nextDouble());
		int minSize = (int) (minBuildingSize *(1+random.nextDouble()));
		int minFloors = RCRPlugin.settings.minFloors();
		int maxFloors = RCRPlugin.settings.maxFloors();
		w.put("rcr:minsize", String.valueOf(minSize));
		w.put("rcr:maxsize", String.valueOf(maxSize));
		w.put("rcr:gap", String.valueOf(2));
		w.put("rcr:minfloors", String.valueOf(minFloors));
		w.put("rcr:maxfloors", String.valueOf(maxFloors));

		return w;
	}
	
	public Way makeBuildingWay(List<Node> outline) {
		Way w = new Way();
		for(Node n : outline) {
			Node newNode = new Node(n);
			w.addNode(newNode);
			m_data.addPrimitive(newNode);
		}
		
		w.addNode(w.firstNode());
		//w.nodes.addAll(outline);

		//TODO is this all?
		w.put("rcr:type", "building");
		w.put("created_by", "osm2rescue autogen");
		return w;
	}
	
	/**
	 * When running a circle: the next crossing following lastWay in lastForward has been found at
	 * nextStart, the previous Node in that way was lastNode.
	 * exitWays are all ways exiting nextStart.
	 *  --- lastNode   --lastWay-in-lastForward-->  nextStart 
	 * 
	 * We want to find a way and direction from exitWays, that is the next way to
	 * follow when running a circle.
	 * @param failedExitWays 
	 */
	private Pair<Way, Boolean> getNextWay(Node lastNode, Way lastWay, boolean lastForward, Node nextStart, Set<Way> exitWays, Set<Pair<Way, Boolean>> failedExitWays) {
		Pair<Way,Boolean> ret = null;

		double lastHeading = nextStart.getCoor().heading(lastNode.getCoor());
		double minHeadingDelta = 2 * Math.PI;
		if(db)
			System.out.println("Possible exit ways");
		for(Way ew : exitWays) {
			if(db)
				System.out.println(ew.getId());
			Node ewPre = null;
			Node ewPost = null;
			boolean ewStartFound = false;
			// first determine the nodes right before (ewPre) and right after (ewPost) nextStart
			for(Node ewN : ew.getNodes()) {
				if(ewN == nextStart) {
					ewStartFound = true;
				} else {
					if(ewStartFound) {
						ewPost = ewN;
						break;
					} else
						ewPre = ewN;
				}
			}
			// now check for the previous node (backwards) if it's good
			if(ewPre != null) {
				double heading = nextStart.getCoor().heading(ewPre.getCoor());
				double delta = heading - lastHeading;
				while(delta < 0)
					delta += 2 * Math.PI;
				if(delta < minHeadingDelta) {
					if(!((ew == lastWay) && lastForward)) {	// going in forward previously - we came from here, now going backward obviously min angle
						Pair<Way,Boolean> p = new Pair<Way,Boolean>(ew, false);
						if (!failedExitWays.contains(p)) {
							minHeadingDelta = delta;
							ret = p;
						}
					}
				}
			}
			// also check for the next node (forward) if it's good
			if(ewPost != null) {
				double heading = nextStart.getCoor().heading(ewPost.getCoor());
				double delta = heading - lastHeading;
				while(delta < 0)
					delta += 2 * Math.PI;
				if(delta < minHeadingDelta) {
					if(!((ew == lastWay) && !lastForward)) {	// going in backward previously - we came from here, now going forward obviously min angle
						Pair<Way, Boolean> p = new Pair<Way,Boolean>(ew, true);
						if (!failedExitWays.contains(p)) {
							minHeadingDelta = delta;
							ret = p;
						}
					}
				}
			}
			// the best for all ways should be final
		}
		return ret;
	}
	
//	BUG: one line polys? ARGH it's mapped that way...
	/**
	 * Perform one (recursive) step along the walk.
	 * 
	 * Start from start along w forward/backward depending on direction until
	 * a crossing is hit.
	 * If it is goal - do something (finish)
	 * If there is no crossing - do something (dead end)
	 * 
	 * Then finds the next way and direction following lowest angle clockwise.
	 * 
	 * Diversion is the count on this path, that another path, than the first choice was taken
	 * 
	 * @return the nodes to get from start to goal (excluding goal)
	 */
	private List<Node> walkCircle(Node start, Way w, boolean forward, HashMap<Long, Set<Way>> crossings, Node goal, Set<Long> visited, int depth, int diversions) {
		// follow way from start along direction until 
		// at end or a crossing hit (goal should be crossing)
		if(db)
			System.out.println("Walking: from " + start.getId() + " via " + w.getId() + " in " + forward);

		/*if(visited.contains(start.id)) {	// running in circles
			if(db)
				System.out.println("FAIL visited already\n");
			return null;
		}*/

		visited.add(start.getId());
		if(depth > 42) {
			System.out.println("walk circle: depth 42 reached");
			return null;
		}

		Node last = null;
		Node nextStart = null;
		
		// While searching for the next junction build a list of all nodes from
		// start to nextStart (the next junction)
		List<Node> startToNextStart = new ArrayList<Node>();
		List<Node> nodesList = new ArrayList<Node>();
		nodesList.addAll(w.getNodes());
		if(!forward) {
			Collections.reverse(nodesList);
		}
		boolean startFound = false;
		for (Node n : nodesList) {
			if (n == start) {
				startFound = true;
				last = n;
				startToNextStart.add(n);
				continue;
			}
			if (!startFound) {
				last = n;
				continue;
			}
			// we were at start
			if (!crossings.containsKey(n.getId())) { // non-crossing node, skip
				last = n;
				startToNextStart.add(n);
				continue;
			}

			if (n == goal) { // found goal.
				return startToNextStart;
			}
			// found a crossing
			nextStart = n;
			break;
		}

		if(last == null || nextStart == null) {
			if(db)
				System.out.println("FAIL no last/nextStart found " + last + "/" + nextStart + "\n");
			return null;
		}

		if(db)
			System.out.println("Found last " + last.getId() + " nextStart " + nextStart.getId());

		if(visited.contains(nextStart.getId())) {		// already was there.
			return null;
		}
		
		// we have last and nextStart -> determine next way and direction
		Set<Way> exitWays = crossings.get(nextStart.getId());
		Set< Pair<Way, Boolean> > failedExitWays = new HashSet< Pair<Way,Boolean> >();
		
		List<Node> wayToGoal = null;
		// try to find a way to goal
		// try this as long as the recursion fails (dead end, etc.) or until we tried every possibility (then fail)
		while (wayToGoal == null) {		// how to get out: wayToGoal found/all possibilities looked at/search path too long
			Pair<Way, Boolean> nextWayForward = getNextWay(last, w, forward,
					nextStart, exitWays, failedExitWays);
			if(nextWayForward == null)		// no more possibilities left
				return null;

			Way nextWay = nextWayForward.a;
			boolean nextForward = nextWayForward.b.booleanValue();

			if (db)
				System.out.println("Determined next way " + nextWay.getId() + " in "
						+ nextForward + "\n");

			wayToGoal = walkCircle(nextStart, nextWay, nextForward, crossings,
					goal, visited, depth + 1, diversions);
			if (wayToGoal == null) {
				//if(true) return null;	// no recursion
				if(depth == 0)	// initially follow what is given only
					return null;
				else {
					failedExitWays.add(nextWayForward);
					diversions++;
					if(diversions > 5)
						return null;
				}
			}
		}

		// we can get to goal from here!
		wayToGoal.addAll(0, startToNextStart);
		return wayToGoal;
	}

	/**
	 * Create a random house in a polygon.
	 * @param p
	 * @return
	 */
	public void placeRandomHouse(Way way) {
		AdvArea p = new AdvArea(way.getNodes());
				
		BBox bbox = new BBox(way);
		
		System.out.println("bb:" + bbox);
		
		List<Way> waysInPoly = new ArrayList<Way>();
		for (Way w : m_data.getData().searchWays(bbox)) {
			if (w.hasTag("rcr:type", "building") || w.hasTag("rcr:type", "road")) {
				if (intersectsWay(p, w))
					waysInPoly.add(w);
			}
		}
		System.out.println(waysInPoly.size() + " ways in p");
		
		int minSize = parseInt(way, "rcr:minsize", (int) minBuildingSize);
		int maxSize = parseInt(way, "rcr:maxsize", 3000);
		int gap = parseInt(way, "rcr:gap", 2);
		int minFloors= parseInt(way, "rcr:minfloors", 1);
		int maxFloors = parseInt(way, "rcr:maxfloors", 5);
		
		double angle = findAngle(p);
		
		//System.out.println("Best rotation: pi*" + angle/Math.PI);
		p.cleanScale(-2);
		p = p.rotate(-angle);
		
		List<AdvArea> houses = recursiveSplit(p, minSize, maxSize, gap);
		if (houses == null) {
			 p.cleanScale(-2);
			 houses = new ArrayList<AdvArea>();
			 houses.add(p);
		}
				
		for (AdvArea h : houses) {
			if (h == null) // Should never...
				continue;
			h=h.rotate(angle);
			//System.out.println("empty: " +h.isEmpty());
			if(h.isEmpty() || checkPolyWayIntersection(h, areaPolys))
				continue;
			
			int shrinkCount = 0;
			while (checkWayIntersection(h, waysInPoly) && shrinkCount < 5)
				h.cleanScale(-3);
			if (h.isEmpty() || checkWayIntersection(h, waysInPoly))
				continue;

			List<Node> houseNodes = h.toNodeList();
			//System.out.println("nodes: " + houseNodes.size());
			Way w1 = makeBuildingWay(houseNodes);
			m_data.addPrimitive(w1);

			Way b= m_data.makeBuilding(w1, false);
			int floors = minFloors + random.nextInt(maxFloors-minFloors);
			b.put("rcr:floors", String.valueOf(floors));
		}
	}

	private static double findAngle(AdvArea p) {
		//try to find roatation that makes most lines horizontal/vertical
		List<Double> angleCandiates = new ArrayList<Double>();
		List<Double> totalLength = new ArrayList<Double>();
		for (Line2D.Double l : p.toSegments()) {
			double angle = Vector.angle(fromLine(l));
			if (angle > Math.PI)
				angle -= Math.PI;
			int bestMatch = -1;
			double bestDiff = 10000;
			for (int i=0; i < angleCandiates.size(); i++) {
				if (Math.abs(angle - angleCandiates.get(i)) < bestDiff) {
					bestMatch = i;
					bestDiff = Math.abs(angle - angleCandiates.get(i));
				}
			}
			//System.out.println("best diff " + bestDiff);
			if (bestMatch == -1 || bestDiff > Math.PI/20) {
				angleCandiates.add(angle);
				totalLength.add(length(fromLine(l)));
			}
			else {
				double newLength = totalLength.get(bestMatch) + length(fromLine(l)); 
				totalLength.set(bestMatch, newLength);
			}
		}

		double angle = 0;
		double maxLenght = 0;
		for (int i = 0; i < angleCandiates.size(); i++) {
			if (totalLength.get(i) > maxLenght) {
				maxLenght = totalLength.get(i);
				angle = angleCandiates.get(i);
			}
		}
		return angle;
	}
	
	
	/**
	 * TODO: does not work for line intersections and full containment
	 * @return true, if one node is within any way or vice versa
	 */
	private boolean checkPolyWayIntersection(AdvArea p, List<AdvArea> wayPolys) {
		for(AdvArea wp : wayPolys) {
			// check poly point in way
			for(Point2D.Double point : p.toPoints()) {
				if(wp.contains(point))
					return true;
			}
			// check way point in poly
			for(Point2D c : wp.toPoints()) {
				if(p.contains(c))
					return true;
			}
		}
		return false;
	}
	
	private boolean intersectsWay(AdvArea p, Way w) {
		if (w.isClosed()) {
			AdvArea wp = new AdvArea(w.getNodes());
			return p.intersects(wp);
		}
		for (Node n : w.getNodes()) {
			Point2D c = m_data.osm2rescueCoords(n);
			if (p.contains(c))
				return true;
		}
		return false;
	}
	
	private boolean checkWayIntersection(AdvArea p, List<Way> ways) {
		for (Way w : ways) {
			if (intersectsWay(p, w))
				return true;
		}
		return false;
	}
	

	private List<AdvArea> recursiveSplit(AdvArea a, double minArea, double maxArea, double initgap) {
		//a.scale(- random.nextDouble()*0.5 );
		double gap = random.nextDouble() * initgap;
		double area = a.area();
		//System.out.println("area: " + area);
		List<AdvArea> ret = new ArrayList<AdvArea>();
		
		if(area < minArea) {
			//ret.add(a);
			return ret;
		}
		else if (area < minArea * 2.5 ) {
			ret.add(a);
			return ret;
		}
		else if(area < maxArea) {	// sometimes stop with bigger ones.
			if(random.nextDouble() < 0.4) {
				ret.add(a);
				return ret;
			}
		}
		
		List<AdvArea> splits;
		double ratio = 0.4 + random.nextFloat()*0.2;
		Rectangle2D bbox = a.getBounds2D();
		if (bbox.getWidth() > bbox.getHeight()) {
			splits = a.split(ratio, gap, true);
		}
		else {
			splits = a.split(ratio, gap, false);
		}
		
		for(AdvArea sp : splits) {
			List<AdvArea> spHouses = recursiveSplit(sp, minArea, maxArea, initgap);
			if(spHouses != null)
				ret.addAll(spHouses);
		}
		
		return ret;
	}
	

	public static List<Way> segmentComplexShape(Relation r) {
		Map<String, String> combinedTags = new HashMap<>();
		combinedTags.putAll(r.getKeys());
		combinedTags.remove("type");

		List<Way> outerWays = new ArrayList<Way>();
		List<Way> innerWays = new ArrayList<Way>();
		for (RelationMember m : r.getMembers()) {
			if (m.getRole().equals("outer") && m.isWay()) { 
				combinedTags.putAll(m.getMember().getKeys());
				outerWays.add(m.getWay());
			} else if (m.getRole().equals("inner") && m.isWay()) { 
				innerWays.add(m.getWay());
			}
		}
		if (outerWays.size() != 1 || innerWays.size() == 0)
			return new ArrayList<Way>();
		AdvArea a = new AdvArea(outerWays.get(0).getNodes());
		
		double angle = findAngle(a);
		System.out.println("angle: " +angle);
		a = a.rotate(-angle);
		
		List<Rectangle2D> bounds = new ArrayList<Rectangle2D>();
		for (Way w : innerWays) {
			if (w.isIncomplete())
				continue;
			AdvArea b = new AdvArea(w.getNodes()).rotate(-angle);
			bounds.add(b.getBounds2D());
			a.subtract(b);
		}
		
		List<Way> ways = new ArrayList<Way>();
		for (AdvArea b : splitComplexShapeRec(a)) {
			b = b.rotate(angle);
			Way w = new Way();
			w.setNodes(b.toNodeList());
			w.addNode(w.firstNode());
			w.setKeys(combinedTags);
			ways.add(w);
		}
		return ways;
	}
	
	private static double area(Way way) {
		double sum = 0;
		for (Pair<Node, Node> n : way.getNodePairs(false)) {
//			Line2D line = new Line2D.Double(n.a.getCoor(), n.b.getCoor());
			sum += n.a.getCoor().getX()*n.b.getCoor().getY() - n.b.getCoor().getX()*n.a.getCoor().getY();
		}
		return sum/2;
	}
	
	
	private static final double ANGLE_DIFF = 0.1;
//	private static <T extends Point2D> areColinear(Pair<T,T> l1, Pair<T,T> l2) {
//		int result = 1;
//		
//		T v1 = Vector.diff(l1.b, l1.a);
//		T v2 = Vector.diff(l2.b, l2.a);
//		double a = Vector.cosAngle(v1,v2);
//		if (Math.abs(a) < 1-ANGLE_DIFF) {
//			return 0;
//		}
//		
//		if (a < 0) {
//			v2 = Vector.times(v2, -1);
//			result = -1;
//		}
//		T vsum = Vector.sum(v1, v2);
//		T v3 = Vector.diff(l1.a, l2.b);
//		
//		if (Vector.cosAngle(v3,vsum) >= 1-ANGLE_DIFF) {
//			return true;
//		}
//		T v4 = Vector.diff(l2.a, l1.b);
//		if (Vector.cosAngle(v4,vsum) >= 1-ANGLE_DIFF) {
//			return true;
//		}
//		
//		return false;
//	}
	
	private static class LineComp implements Comparator<Pair<Node, Node>> {
		private LatLon direction;
		
		public LineComp(LatLon direction) {
			this.direction = Vector.normalize(direction);
		}
		
		@Override
		public int compare(Pair<Node, Node> o1, Pair<Node, Node> o2) {
			double d1a = Vector.dotProduct(o1.a.getCoor(), direction);
			double d1b = Vector.dotProduct(o1.b.getCoor(), direction);
			double d1 = Math.min(d1a, d1b);
			double d2a = Vector.dotProduct(o2.a.getCoor(), direction);
			if (d1 > d2a) {
				return 1;
			}
			double d2b = Vector.dotProduct(o2.b.getCoor(), direction);
			double d2 = Math.min(d2a, d2b);
			if (d1 > d2) {
				return 1;
			}
			else {
				return -1;
			}
		}
		
	}
	
	private static List<Way> splitBuilding(Way outer, List<Way> inner) {
		List<Way> splitWays = new ArrayList<Way>();
		//make outer ways clockwise
		if (area(outer) > 0) {
			List<Node> nodes = outer.getNodes();
			Collections.reverse(nodes);
			outer.setNodes(nodes);
		}
		List<Pair<Node, Node>> outerLines = outer.getNodePairs(false);
		
		List<Pair<Node, Node>> innerLines = new ArrayList<Pair<Node,Node>>();
		for (Way i : inner) {
			//make inner ways ccw
			if (area(i) < 0) {
				List<Node> nodes = i.getNodes();
				Collections.reverse(nodes);
				i.setNodes(nodes);
			}
			innerLines.addAll(i.getNodePairs(false));
		}
		
		List<List<Line2D>> lineStrings = new ArrayList<List<Line2D>>();
		//for (Line2D l1 : innerLines) {
		//	
		//}
		
		
		return splitWays;
	}
	
	private static List<AdvArea> splitComplexShapeRec(AdvArea a) {
		Rectangle2D bbox = a.getBounds2D();
		
		List<AdvArea> splits;
		if (bbox.getWidth() > bbox.getHeight()) {
			splits = a.split(0.5, 0.01, true);
		}
		else {
			splits = a.split(0.5, 0.01, false);
		}
		
		List<AdvArea> results = new ArrayList<AdvArea>();
		while (!splits.isEmpty()) {
			AdvArea part = splits.get(0);
			for (int i=1; i < splits.size(); i++) {
				AdvArea res = tryUnify(part, splits.get(i));
				if (res != null) {
					part = res;
					splits.remove(i);
					i=0;
				}
			}
			results.add(part);
			splits.remove(0);
		}
		
		List<AdvArea> finalResults = new ArrayList<AdvArea>();
		for (AdvArea b : results) {
			if (b.isSingular())
				finalResults.add(b);
			else
				finalResults.addAll(splitComplexShapeRec(b));
		}
		return finalResults;
	}
	
	private static AdvArea tryUnify(AdvArea a, AdvArea b) {
		if (a.getBounds2D().contains(b.getBounds2D())) {
			double area = a.area();
			a.subtract(b);
			if (a.area() < area)
				return a;
		}
		if (b.getBounds2D().contains(a.getBounds2D())) {
			double area = b.area();
			b.subtract(a);
			if (b.area() < area)
				return b;
		}
		return null;
	}
	
}
