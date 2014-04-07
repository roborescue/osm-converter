package rcr.export;

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;

import rcr.RCRDataSet;
import rcr.export.RCRLegacyMap.GalPolygon;
import rescuecore.RescueObject;
import rescuecore.objects.AmbulanceCenter;
import rescuecore.objects.AmbulanceTeam;
import rescuecore.objects.Building;
import rescuecore.objects.Civilian;
import rescuecore.objects.FireBrigade;
import rescuecore.objects.FireStation;
import rescuecore.objects.Humanoid;
import rescuecore.objects.PoliceForce;
import rescuecore.objects.PoliceOffice;
import rescuecore.objects.RealObject;
import rescuecore.objects.Refuge;
import rescuecore.objects.Road;

public class LegacyExporter {

	RCRDataSet rcrdata = null;
	DataSet data = null;
	
	public LegacyExporter(RCRDataSet data)
	{
		this.rcrdata = data;
		this.data = data.getData();
	}

	
	public void exportMap(File file) {
		RCRLegacyMap map = toRescueMap();
		try {
			map.save(file.getPath());
			System.out.println("saved map to " + file.getPath());
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	
	private int nextRCRId(OsmPrimitive obj, HashMap<Integer, RescueObject> id2Object, int nextId) {
		int id = RCRDataSet.parseInt(obj, "rcr:id", -1);
		
		if (id != -1 && !id2Object.containsKey(id)) {
			return id;
		}
		else {
			//id2Object.put(n.id, nextId);
			obj.put("rcr:id", String.valueOf(nextId));
			return nextId;
		}
	}

	
	private rescuecore.objects.Node createRescueNode(Node n) {
		rescuecore.objects.Node ret = new rescuecore.objects.Node();

		int rid = RCRDataSet.parseInt(n, "rcr:id", -1);
		assert rid >= 0;
		ret.setID(rid);

		Point coords = this.rcrdata.osm2rescueCoords(n);
		ret.setX(coords.x, 0, this);
		ret.setY(coords.y, 0, this);
		return ret;
	}
	
	private Road createRescueRoad(Way w, Node head, Node tail, int id) {
		Road r = new Road();
		r.setID(id);
		
		int headId = RCRDataSet.parseInt(head, "rcr:id", -1);
		int tailId = RCRDataSet.parseInt(tail, "rcr:id", -1);
		
		assert headId != -1 && tailId != -1;

		// rescuecore.objects.Node headNode = (rescuecore.objects.Node)
		// idMap.get(headId);
		// rescuecore.objects.Node tailNode = (rescuecore.objects.Node)
		// idMap.get(tailId);
		Point ps = this.rcrdata.osm2rescueCoords(head);
		Point pe = this.rcrdata.osm2rescueCoords(tail);

		r.setHead(headId, 0, this);
		r.setTail(tailId, 0, this);

		int length = (int) Math.hypot(ps.x - pe.x, ps.y - pe.y);
		if (length <= 0) {
			length = 1;
		}
		r.setLength(length, 0, this);
		r.setLinesToHead(RCRDataSet.parseInt(w, "rcr:lines_to_head", 1), 0, this);
		r.setLinesToTail(RCRDataSet.parseInt(w, "rcr:lines_to_tail", 1), 0, this);

		r.setBlock(RCRDataSet.parseInt(w, "rcr:block", 0), 0, this);
		r.setWidth(RCRDataSet.parseInt(w, "rcr:width", 3000*(r.getLinesToHead()+r.getFreeLinesToTail())), 0, this);

		return r;
	}

	private void addEdgeToRescueNode(rescuecore.objects.Node node, RealObject road) {
		node.appendEdge(road.getID(), 0, this);
		// No idea, what these values are, but gis reads for each
		// edge:
		// 1x shortcut, 2x pocket, 3x timing
		node.appendShortcutToTurn(1, 0, this);
		node.appendPocketToTurnAcross(1, 0, this);
		node.appendPocketToTurnAcross(1, 0, this);
		node.appendSignalTiming(0, 0, this);
		node.appendSignalTiming(0, 0, this);
		node.appendSignalTiming(0, 0, this);
	}

	private Building createRescueBuilding(Node n, int id) {
		Building b;
		if (n.hasTag("rcr:building_type", "refuge"))
			b = new Refuge();
		else if (n.hasTag("rcr:building_type", "policeoffice"))
			b = new PoliceOffice();
		else if (n.hasTag("rcr:building_type", "ambulancecenter"))
			b = new AmbulanceCenter();
		else if (n.hasTag("rcr:building_type", "firestation"))
			b = new FireStation();
		else
			b = new Building();
		
		b.setID(id);

		Point p = this.rcrdata.osm2rescueCoords(n);
		b.setX(p.x, 0, this);
		b.setY(p.y, 0, this);
		b.setFloors(RCRDataSet.parseInt(n, "rcr:floors", 3), 0, this);
		b.setBuildingCode(RCRDataSet.parseInt(n, "rcr:building_code", 0), 0, this);
		if (n.isKeyTrue("rcr:fire")) {
			b.setIgnition(true, 0, this);
		}

		Relation buildingRel = null;
		for (Relation rel : this.rcrdata.getRelationsOf(n, "rcr:node")) {
			for (RelationMember m : rel.getMembers()) {
				if (m.refersTo(n) && "rcr:node".equals(m.getRole())) {
					buildingRel = rel;
					break;
				}
			}
			if (buildingRel != null)
				break;
		}
		
		if (buildingRel == null)
			return null;

		Way outline = null;
		for (RelationMember m : buildingRel.getMembers()) {
			if (m.isNode() && "rcr:entrance".equals(m.getRole())) {
				int entranceId = RCRDataSet.parseInt(m.getNode(), "rcr:id", -1);
				if (id != -1)
					b.appendEntrances(entranceId, 0, this);
			}
			if (m.isWay() && "rcr:outline".equals(m.getRole())) {
				outline = m.getWay();
			}
		}

		if (outline == null) {
			System.out.println("No outline for " + n.getId());
			return null;
		}
		
		List<Node> ol_nodes = outline.getNodes(); 
		Point prev = this.rcrdata.osm2rescueCoords(ol_nodes.get(outline.getNodesCount()-1));
		long area = 0;
		for (Node ol : ol_nodes) {
			Point olp = this.rcrdata.osm2rescueCoords(ol);
			b.appendApex(olp.x, 0, this);
			b.appendApex(olp.y, 0, this);
			area += ((long)prev.x*(long)olp.y - (long)prev.y*(long)olp.x);
			prev = olp;
		}
		if (b.getApexes().length < 2*3) {
			System.out.println("removed degenerated building");
			return null;
		}
				
		int intarea = (int) Math.abs(area/10000/2);
		n.put("rcr:area", Integer.toString(intarea));
		if (intarea < 500) {
			System.out.println("Removed too small building with area " + intarea);
			return null;
		}
		b.setGroundArea(intarea, 0, this);
		b.setTotalArea(b.getFloors()*intarea, 0, this);
		b.setImportance(RCRDataSet.parseInt(n, "rcr:importance", 1), 0, this);

		return b;
	}
	
	private GalPolygon createGalPolygon(Way w) {
		int level = RCRDataSet.parseInt(w, "rcr:level", 0);
		int[] x = new int[w.getNodesCount()];
		int[] y = new int[w.getNodesCount()];

		for (int i=0; i < w.getNodesCount(); i++) {
			Point coords = this.rcrdata.osm2rescueCoords(w.getNode(i));
			x[i] = coords.x;
			y[i] = coords.y;
		}
		
		return new GalPolygon(level, x, y, w.getNodesCount());
	}

	
	private static List<Humanoid> getHumanoids(OsmPrimitive at) {
		List<Humanoid> result = new LinkedList<Humanoid>();
		int num = RCRDataSet.parseInt(at, "rcr:ambulances", 0);
		for (int i=0; i < num; i++)
			result.add(new AmbulanceTeam());
		
		num = RCRDataSet.parseInt(at, "rcr:firebrigades", 0);
		for (int i=0; i < num; i++)
				result.add(new FireBrigade());

		num = RCRDataSet.parseInt(at, "rcr:policeforces", 0);
		for (int i=0; i < num; i++)
			result.add(new PoliceForce());
		
		num = RCRDataSet.parseInt(at, "rcr:civilians", 0);
		for (int i=0; i < num; i++)
			result.add(new Civilian());
		
		return result;
	}
	
	
	/**
	 * This function is supposed to only convert the current representation into
	 * a rescue map. No additional algorithms will be performed.
	 * 
	 * @return a valid rescuemap representing this dataset
	 */
	public RCRLegacyMap toRescueMap() {
		RCRLegacyMap map = new RCRLegacyMap();
		if (this.rcrdata.sourceMap != null)
			map.setSource(this.rcrdata.sourceMap);

		int nextFreeId = 0; // next free id

		HashMap<Integer, RescueObject> id2Object = new HashMap<Integer, RescueObject>();
		
		List<Humanoid> humanoids = new ArrayList<Humanoid>();
		List<Node> rcrNodes = new ArrayList<Node>();
		List<Way> rcrRoads = new ArrayList<Way>();
		List<Node> rcrBuildings = new ArrayList<Node>();
		List<Way> quakePolygons = new ArrayList<Way>();
		
		// Make sure we get *all* elements
		for (Node n : data.getNodes()) {
			if (n.isDeleted())
				continue;
			
			if ("node".equals(n.get("rcr:type")))
				rcrNodes.add(n);
			else if ("building".equals(n.get("rcr:type")))
				rcrBuildings.add(n);
		}
		for (Way w : data.getWays()) {
			if (w.isDeleted())
				continue;

			if ("road".equals(w.get("rcr:type")))
				rcrRoads.add(w);
			else if ("galpoylgon".equals(w.get("rcr:type")))
				quakePolygons.add(w);
		}
		
		System.out.println("BM RM SIZE " + rcrBuildings.size());

		for (Node n : rcrNodes) {
			Collection<OsmPrimitive> refs = n.getReferrers();
			boolean hasWays = false;
			for (OsmPrimitive p : refs) {
				if (p instanceof Way) {
					hasWays = true;
					break;
				}
			}
			
			if (!hasWays)
				continue;
			
			int id = nextRCRId(n, id2Object, nextFreeId);
			
			rescuecore.objects.Node node = createRescueNode(n);
			map.nodes.add(node);
			id2Object.put(id, node);

			for (Humanoid h : getHumanoids(n)) {
				humanoids.add(h);
				h.setPosition(id, 0, this);
			}
			nextFreeId = Math.max(id+1, nextFreeId);;
		}

		for (Way w : rcrRoads) {
			Node head = null;
			for (Node n : w.getNodes()) {
				if (n.isDeleted())
					continue;
				//assert waysOfNode.containsKey(n);
				if (head != null) {
					int id = nextRCRId(w, id2Object, nextFreeId);
					Road road = createRescueRoad(w, head, n, id);
					id2Object.put(id, road);
					/*if (road == null ) {
						head = n;
						continue;
					}*/
					int headId = RCRDataSet.parseInt(head, "rcr:id", -1);
					int tailId = RCRDataSet.parseInt(n, "rcr:id", -1);
					addEdgeToRescueNode((rescuecore.objects.Node) id2Object
							.get(headId), road);
					addEdgeToRescueNode((rescuecore.objects.Node) id2Object
							.get(tailId), road);
					map.roads.add(road);
					nextFreeId = Math.max(id+1, nextFreeId);;
				}
				head = n;
			}
		}

		// BUILDINGS
		System.out.println("there are " + rcrBuildings.size() + " buildings.");
		for (Node n : rcrBuildings) {
			int id = nextRCRId(n, id2Object, nextFreeId);
			Building b = createRescueBuilding(n, id);
			if (b == null)
				continue;
			
			for (int entrance : b.getEntrances()) {
				rescuecore.objects.Node eNode = (rescuecore.objects.Node) id2Object
				.get(entrance);
				if (eNode != null) {
					addEdgeToRescueNode(eNode, b);
				}
				else {
					System.err.println("Warning: Entrance " + entrance + " of " + id + " not found." );
				}
			}
			if (n.get("rcr:fire") != null)
				b.setIgnition(true, 0, this);
			map.buildings.add(b);
			id2Object.put(id, b);
			for (Humanoid h : getHumanoids(n)) {
				humanoids.add(h);
				h.setPosition(id, 0, this);
			}	
			nextFreeId = Math.max(id+1, nextFreeId);
		}
		
		for (Way w : quakePolygons) {
			map.quakePolygons.add(createGalPolygon(w));
		}
		
		/*AmbulanceTeam at = new AmbulanceTeam();
		at.setID(nextFreeId);
		nextFreeId++;
		at.setPosition(0, 0, this);
		map.humanoids.add(at);*/
		for (Humanoid h : humanoids) {
			h.setID(nextFreeId);
			map.humanoids.add(h);
			nextFreeId++;
		}

		//System.out.println("Created rescue map: \n" + map.toLongString());

		if (!map.check(true))
			System.err.println("Map seems incorrect.");

		return map;
	}

	public RCRLegacyMap toDummyRescueMap() {
		RCRLegacyMap map = new RCRLegacyMap();

		rescuecore.objects.Node n0 = new rescuecore.objects.Node();
		n0.setID(0);
		n0.setX(0, 0, this);
		n0.setY(0, 0, this);
		n0.appendEdge(100, 0, this);
		// No idea, what these values are, but gis reads for each edge:
		// 1x shortcut, 2x pocket, 3x timing
		n0.appendShortcutToTurn(0, 0, this);
		n0.appendPocketToTurnAcross(0, 0, this);
		n0.appendPocketToTurnAcross(0, 0, this);
		n0.appendSignalTiming(0, 0, this);
		n0.appendSignalTiming(0, 0, this);
		n0.appendSignalTiming(0, 0, this);

		rescuecore.objects.Node n1 = new rescuecore.objects.Node();
		n1.setID(1);
		n1.setX(100000, 0, this);
		n1.setY(200000, 0, this);
		n1.appendEdge(100, 0, this);

		n1.appendShortcutToTurn(0, 0, this);
		n1.appendPocketToTurnAcross(0, 0, this);
		n1.appendPocketToTurnAcross(0, 0, this);
		n1.appendSignalTiming(0, 0, this);
		n1.appendSignalTiming(0, 0, this);
		n1.appendSignalTiming(0, 0, this);

		map.nodes.add(n0);
		map.nodes.add(n1);

		rescuecore.objects.Road r0 = new rescuecore.objects.Road();
		r0.setID(100);
		r0.setWidth(5000, 0, this);
		r0.setHead(0, 0, this);
		r0.setTail(1, 0, this);
		r0.setLength((int) Math.hypot(100000, 100000), 0, this);

		map.roads.add(r0);

		Building b0 = new Building();
		b0.setID(1000);

		b0.setX(45000, 0, this);
		b0.setY(20000, 0, this);
		b0.setFloors(3, 0, this);
		b0.setGroundArea(10, 0, this);
		b0.setTotalArea(30, 0, this);
		b0.appendEntrances(0, 0, this);
		b0.appendEntrances(1, 0, this);

		b0.appendApex(1000, 0, this);
		b0.appendApex(0, 0, this);

		b0.appendApex(100000, 0, this);
		b0.appendApex(90000, 0, this);

		b0.appendApex(100000, 0, this);
		b0.appendApex(0, 0, this);

		map.buildings.add(b0);

		AmbulanceTeam a0 = new AmbulanceTeam();
		a0.setPosition(n0.getID(), 0, this);
		map.humanoids.add(a0);

		AmbulanceTeam a1 = new AmbulanceTeam();
		a1.setPosition(n1.getID(), 0, this);
		map.humanoids.add(a1);

		return map;
	}
	
}
