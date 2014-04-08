package rcr.export;

import java.util.HashMap;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;

import rcr.RCRDataSet;
import rcr.export.RCRLegacyMap.GalPolygon;
import rescuecore.objects.AmbulanceCenter;
import rescuecore.objects.Building;
import rescuecore.objects.FireStation;
import rescuecore.objects.Humanoid;
import rescuecore.objects.PoliceOffice;
import rescuecore.objects.Refuge;
import rescuecore.objects.Road;

public class LegacyImporter {
	
	private RCRDataSet rcrdata;
	
	private HashMap<Long, OsmPrimitive> idToObject = new HashMap<Long, OsmPrimitive>();
	
	public LegacyImporter(RCRDataSet data)
	{
		this.rcrdata = data;
	}

	public void importMap(RCRLegacyMap map)
	{
		for (rescuecore.objects.Node n : map.nodes) {
			rcr2osmNode(n);
		}
		for (Road r : map.roads) {
			rcr2osmRoad(r);
		}
		for (Building b : map.buildings) {
			rcr2osmBuilding(b);
		}
		for (Humanoid h : map.humanoids) {
			OsmPrimitive pos = null; 
			if (idToObject.containsKey((long) h.getPosition()))
				pos = idToObject.get((long) h.getPosition());
			else {
				System.out.println("not found:" + h.getPosition());
				continue;
			}
			
			RCRDataSet.addHumanoid(pos, h.getType());

		}
		for (GalPolygon poly : map.quakePolygons) {
			rcr2osmQuakePolygon(poly);
		}
		
	}
	
	private void rcr2osmNode(rescuecore.objects.Node n) {
		LatLon c = rcrdata.rcr2osmCoord(n.getX(), n.getY());
		org.openstreetmap.josm.data.osm.Node node = new Node(c);
		//System.out.println(n.getID());
		if (n.getID() > 0) {
			node.setOsmId(n.getID(), 1);
		} else {
//			System.out.println("!!!!!!!!!!!!!!!!!!!!!!");
			node.clearOsmMetadata();
		}
		//System.out.println("incomplete:" + node.incomplete);
		node.put("rcr:type", "node");
		node.put("rcr:id", Integer.toString(n.getID()));
		rcrdata.addPrimitive(node);
		idToObject.put((long) n.getID(), node);
		//System.out.println(node.id);
	}

	private void rcr2osmRoad(Road r) {
		Node head = (Node) idToObject.get((long) r.getHead());
		Node tail = (Node) idToObject.get((long) r.getTail());
		System.out.println(r.getHead() + " - " +r.getTail());
		//System.out.println(head + " " + tail +" (" + r.getHead() +", " + r.getTail() + ")" );
		Way road = new Way();
		road.put("rcr:id", Integer.toString(r.getID()));
		road.put("rcr:type", "road");
		road.put("highway", "road");
		//road.incomplete = false;
		road.addNode(head);
		road.addNode(tail);
		
		road.put("rcr:width", Integer.toString(r.getWidth()));
		road.put("rcr:block", Integer.toString(r.getBlock()));
		road.put("rcr:lines_to_head", Integer.toString(r.getLinesToHead()));
		road.put("rcr:lines_to_tail", Integer.toString(r.getLinesToTail()));
		
		rcrdata.addPrimitive(road);
		idToObject.put((long) r.getID(), road);
	}

	private void rcr2osmBuilding(Building b) {
		Way outline = new Way();
		outline.put("rcr:id", Integer.toString(b.getID()));
		outline.put("rcr:type", "building");
		rcrdata.addPrimitive(outline);
		
		int[] apexes = b.getApexes();
		for (int i=0; i < apexes.length; i+=2) {
			LatLon c = rcrdata.rcr2osmCoord(apexes[i], apexes[i+1]);
			if (outline.getNodesCount() > 0 && c.equals(outline.getNode(0).getCoor())) {
				outline.addNode(outline.getNode(0));
			}
			else {
				Node n = new Node(c);
				rcrdata.addPrimitive(n);
				outline.addNode(n);
			}
		}
		
		LatLon c = rcrdata.rcr2osmCoord(b.getX(), b.getY());
		Node bNode = new Node(c);
		bNode.setOsmId(b.getID(), 1);
		bNode.put("rcr:id", Integer.toString(b.getID()));
		bNode.put("rcr:type", "building");
		bNode.put("rcr:building_code", Integer.toString(b.getBuildingCode()));
		bNode.put("rcr:floors", Integer.toString(b.getFloors()));
		if (b.getImportance() > 1)
			outline.put("rcr:importance", Integer.toString(b.getImportance()));
		
		if (b instanceof Refuge) 
			outline.put("rcr:building_type", "refuge");
		else if (b instanceof FireStation) 
			outline.put("rcr:building_type", "firestation");
		else if (b instanceof PoliceOffice) 
			outline.put("rcr:building_type", "policeoffice");
		else if (b instanceof AmbulanceCenter) 
			outline.put("rcr:building_type", "ambulancecenter");
		
		if (b.isIgnited()) {
			outline.put("rcr:fire","true");
		}
			
		rcrdata.addPrimitive(bNode);
		idToObject.put((long) b.getID(), bNode);

//		Relation rel = new Relation();
//		rel.put("rcr:type", "building");
//		rel.addMember(new RelationMember("rcr:outline", outline));
//		rel.addMember(new RelationMember("rcr:node", bNode));
//		for (int id : b.getEntrances()) {
//			//bNode.put("rcr:entrances", Integer.toString(id));
//			Node n = (Node) idToObject.get((long) id);
//			//if (n == null)
//			//	continue;
//			rel.addMember(new RelationMember("rcr:entrance", n));
//		}
//		rcrdata.addPrimitive(rel);
		
		//outlineMap.put(outline.id, outline);
	}

	private void rcr2osmQuakePolygon(GalPolygon poly) {
		Way w = new Way();
		for (int i=0; i < poly.numPoints; i++) {
			LatLon c = rcrdata.rcr2osmCoord(poly.xs[i], poly.ys[i]);
			Node n = new Node(c);
			w.addNode(n);
			rcrdata.addPrimitive(n);
		}
		w.put("rcr:type", "galpoylgon");
		w.put("rcr:level", String.valueOf(poly.level));
		rcrdata.addPrimitive(w);
	}
	
}
