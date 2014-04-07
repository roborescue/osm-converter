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

package rcr.export;

import static rescuecore.RescueConstants.PROPERTY_BLOCK;
import static rescuecore.RescueConstants.PROPERTY_BUILDING_CODE;
import static rescuecore.RescueConstants.PROPERTY_BUILDING_IMPORTANCE;
import static rescuecore.RescueConstants.PROPERTY_FLOORS;
import static rescuecore.RescueConstants.PROPERTY_HEAD;
import static rescuecore.RescueConstants.PROPERTY_LINES_TO_HEAD;
import static rescuecore.RescueConstants.PROPERTY_LINES_TO_TAIL;
import static rescuecore.RescueConstants.PROPERTY_TAIL;
import static rescuecore.RescueConstants.PROPERTY_WIDTH;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import rescuecore.IntProperty;
import rescuecore.objects.AmbulanceCenter;
import rescuecore.objects.AmbulanceTeam;
import rescuecore.objects.Building;
import rescuecore.objects.Civilian;
import rescuecore.objects.FireBrigade;
import rescuecore.objects.FireStation;
import rescuecore.objects.Humanoid;
import rescuecore.objects.Node;
import rescuecore.objects.PoliceForce;
import rescuecore.objects.PoliceOffice;
import rescuecore.objects.RealObject;
import rescuecore.objects.Refuge;
import rescuecore.objects.Road;
import rescuecore.tools.MapFiles;

/**
 * This is a map representation without a rescuecore.Memory.
 */
public class RCRLegacyMap {
	public static class GalPolygon {
		int level;
		int[] xs;
		int[] ys;
		int numPoints;

		GalPolygon(int l, int[] x, int[] y, int num) {
			level = l;
			xs = x;
			ys = y;
			numPoints = num;
		}
	}
	
	private class RoadSorter implements Comparator<Road> {

		public int compare(Road r1, Road r2) {
			Node h1 = (Node)ids.get(r1.getHead());
			Node t1 = (Node)ids.get(r1.getTail());
			Node h2 = (Node)ids.get(r2.getHead());
			Node t2 = (Node)ids.get(r2.getTail());
			int x1 = (h1.getX()+t1.getX())/2;
			int y1 = (h1.getY()+t1.getY())/2;
			int x2 = (h2.getX()+t2.getX())/2;
			int y2 = (h2.getY()+t2.getY())/2;
			if (x1 < x2) return -1;
			if (x1 > x2) return 1;
			if (y1 < y2) return -1;
			if (y2 > y1) return 1;
			return 0;
		}
	}

	public static HashMap<String, Integer> tags2RCRConstants = new HashMap<String, Integer>();
	static {
		tags2RCRConstants.put("rcr:width",  PROPERTY_WIDTH);
		tags2RCRConstants.put("rcr:block",  PROPERTY_BLOCK);
		tags2RCRConstants.put("rcr:lines_to_head",  PROPERTY_LINES_TO_HEAD);
		tags2RCRConstants.put("rcr:lines_to_tail",  PROPERTY_LINES_TO_TAIL);
		
		tags2RCRConstants.put("rcr:floors",  PROPERTY_FLOORS);
		tags2RCRConstants.put("rcr:building_code",  PROPERTY_BUILDING_CODE);
		tags2RCRConstants.put("rcr:building_importance)",  PROPERTY_BUILDING_IMPORTANCE);
	}
	
	public static int[] roadCompareAttributes = new int[] {
		PROPERTY_BLOCK, PROPERTY_WIDTH, PROPERTY_LINES_TO_HEAD, PROPERTY_LINES_TO_TAIL,
		PROPERTY_HEAD, PROPERTY_TAIL
	};

	public static int[] buildingCompareAttributes = new int[] {
		PROPERTY_FLOORS, PROPERTY_BUILDING_CODE
	};
	
	private HashMap<Integer, RealObject> ids = new HashMap<Integer, RealObject>(); 
	List<rescuecore.objects.Node> nodes;
	List<Road> roads;
	/** This might (and should) also contain Stations/Refuges */
	List<Building> buildings;
	/** List for all agents and civilians */
	List<Humanoid> humanoids;
	List<GalPolygon> quakePolygons;
	
	private boolean valid = false;
	
	private RCRLegacyMap m_source = null;
	
	public RCRLegacyMap() {
		nodes = new ArrayList<rescuecore.objects.Node>();
		roads = new ArrayList<Road>();
		buildings = new ArrayList<Building>();
		humanoids = new ArrayList<Humanoid>();
		quakePolygons = new ArrayList<GalPolygon>();
	}

	public RCRLegacyMap(String mapdir) {
		nodes = new ArrayList<rescuecore.objects.Node>();
		roads = new ArrayList<Road>();
		buildings = new ArrayList<Building>();
		humanoids = new ArrayList<Humanoid>();
		quakePolygons = new ArrayList<GalPolygon>();

		String nodesFile = mapdir + File.separator + "node.bin";
		String roadsFile = mapdir + File.separator + "road.bin";
		String bldgsFile = mapdir + File.separator + "building.bin";
		String gisiniFile = mapdir + File.separator + "gisini.txt";
		String galFile = mapdir + File.separator + "galpolydata.dat";
		String blockadeFile = mapdir + File.separator + "blockades.lst";
			System.out.println("Loading from " + nodesFile + " " + roadsFile + " " 
				+ bldgsFile + " " + gisiniFile + " " + galFile);

		try {
			for (Node n : MapFiles.loadNodes(nodesFile)) {
				nodes.add(n);
				ids.put(n.getID(), n);
			}
			for (Road r: MapFiles.loadRoads(roadsFile)) {
				roads.add(r);
				ids.put(r.getID(), r);
				//System.out.println(r.getID());
			}
			for (Building b: MapFiles.loadBuildings(bldgsFile)) {
				buildings.add(b);
				ids.put(b.getID(), b);
			}
			readGIS(gisiniFile);
			readPolydata(galFile);
			readBlockades(blockadeFile);
			//List<Building> fires = MapFiles.
			check(false);
		} 
		catch (IOException e) {
			valid = false;
			return;
		}
		valid = true;
	}
	
	public boolean isValid() {
		return valid;
	}
	
	public void setSource(RCRLegacyMap map) {
		m_source = map;
	}
	
	private void readGIS(String filename) throws IOException {
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(filename));
			String line = in.readLine();
			while (line != null) {
				if (!line.contains("=")) {
					line = in.readLine();
					continue;
				}
				String[] elems = line.trim().split("=");
				String key = elems[0].trim();
				int value = Integer.parseInt(elems[1]);
				
				if ("FireStation".equals(key)) {
					Building b = (Building) ids.get(value);
					FireStation fs = new FireStation(b);
					fs.setID(b.getID());
					int index = buildings.indexOf(b);
					buildings.set(index, fs);
					ids.put(value, fs);
				}
				else if ("PoliceOffice".equals(key)) {
					Building b = (Building) ids.get(value);
					PoliceOffice po = new PoliceOffice(b);
					po.setID(b.getID());
					int index = buildings.indexOf(b);
					buildings.set(index, po);
					ids.put(value, po);
				}
				else if ("AmbulanceCenter".equals(key)) {
					Building b = (Building) ids.get(value);
					AmbulanceCenter ac = new AmbulanceCenter(b);
					ac.setID(b.getID());
					int index = buildings.indexOf(b);
					buildings.set(index, ac);
					ids.put(value, ac);
				}
				else if ("Refuge".equals(key)) {
					Building b = (Building) ids.get(value);
					Refuge ref = new Refuge(b);
					ref.setID(b.getID());
					int index = buildings.indexOf(b);
					buildings.set(index, ref);
					ids.put(value, ref);
				}
				else if ("FirePoint".equals(key)) {
					Building b = (Building) ids.get(value);
					b.setIgnition(true, 1, null);
					//System.out.println(b + "  " + b.isIgnited());
				}
				else if ("Civilian".equals(key)) {
					Civilian civ = new Civilian();
					civ.setPosition(value, 0, null);
					humanoids.add(civ);
				}
				else if ("FireBrigade".equals(key)) {
					FireBrigade fb = new FireBrigade();
					fb.setPosition(value, 0, null);
					humanoids.add(fb);
				}
				else if ("PoliceForce".equals(key)) {
					PoliceForce pf = new PoliceForce();
					pf.setPosition(value, 0, null);
					humanoids.add(pf);
				}
				else if ("AmbulanceTeam".equals(key)) {
					AmbulanceTeam at = new AmbulanceTeam();
					at.setPosition(value, 0, null);
					humanoids.add(at);
				}
				else if (key.startsWith("ImportantBuilding")) {
					int id = Integer.parseInt(key.substring(17).trim());
					Building b = (Building) ids.get(id);

					b.setImportance(value, 1, this);
				}
				line = in.readLine();
			}
		}
		finally {
			if (in != null)
				in.close();
		}
	}
	
	private void readBlockades(String filename) throws IOException {
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(filename));
			Collections.sort(roads, new RoadSorter());
			String line = in.readLine();
			int i = 0;
			while (line != null && i < roads.size()) {
				int block = Integer.parseInt(line);
				roads.get(i).setBlock(block, 1, this);
				line = in.readLine();
				i++;
			}
		}
		finally {
			if (in != null)
				in.close();
		}
	}

	private void writeBlockades(String filename) throws IOException {
		this.updateIds();
		PrintWriter out = null;
		try {
			out = new PrintWriter(new FileWriter(new File(filename)));
			Collections.sort(roads, new RoadSorter());
			for (Road r : roads) {
				out.println(r.getBlock());
			}
		}
		finally {
			if (out != null) {
				out.flush();
				out.close();
			}
		}
	}
	
	public void save(String mapdir) throws IOException {
		String nodesFile = mapdir + File.separator + "node.bin";
		String roadsFile = mapdir + File.separator + "road.bin";
		String bldgsFile = mapdir + File.separator + "building.bin";
		String gisiniFile = mapdir + File.separator + "gisini.txt";
		String galFile = mapdir + File.separator + "galpolydata.dat";
		String blockadeFile = mapdir + File.separator + "blockades.lst";
		System.out.println("Saving to " + nodesFile + " " + roadsFile + " " 
				+ bldgsFile + " " + gisiniFile + " " + galFile);

		// Build the list of fires here, as we don't want the
		// "ignited" property stored in the .bin file
		List<Building> fires = new ArrayList<Building>();
		for(Building f : buildings) {
			if(f.isIgnited())
				fires.add(f);
				f.setIgnition(false, 1, null);
		}

		// Same for blockades
		writeBlockades(blockadeFile);
		for(Road r : roads) {
			if(r.getBlock() > 0)
				r.setBlock(0, 1, null);
		}
		
		// If the geometry hasn't changed (much), write the original files.
		// This will prevent the fire simulator from recalculating all rays 
		// and accumulating rounding errors. 
		if (m_source != null) {
			m_source.updateIds();
			this.updateIds();
			
			if (compareNodes(m_source)) {
				System.out.println("Reusing node file");
				MapFiles.writeNodes(nodesFile, m_source.nodes);
			} else
				MapFiles.writeNodes(nodesFile, nodes);
				
			if (compareRoads(m_source)) {
				System.out.println("Reusing roads file");
				MapFiles.writeRoads(roadsFile, m_source.roads);
			} else
				MapFiles.writeRoads(roadsFile, roads);
			
			if (compareBuildings(m_source)) {
				System.out.println("Reusing buildings file");
				MapFiles.writeBuildings(bldgsFile, m_source.buildings);
			} else
				MapFiles.writeBuildings(bldgsFile, buildings);
		
		}
		else {
			MapFiles.writeNodes(nodesFile, nodes);
			MapFiles.writeRoads(roadsFile, roads);
			MapFiles.writeBuildings(bldgsFile, buildings);
		}


		PrintWriter polyPw = new PrintWriter(new File(galFile));
		writePolydata(polyPw);
		polyPw.close();
		for (Node n : nodes) {
			ids.put(n.getID(), n);
		}
		
		PrintWriter gisPw = new PrintWriter(new File(gisiniFile));

		List<FireStation> fss = new ArrayList<FireStation>();
		List<PoliceOffice> pos = new ArrayList<PoliceOffice>();
		List<AmbulanceCenter> acs = new ArrayList<AmbulanceCenter>();
		List<Refuge> rfs = new ArrayList<Refuge>();
		for(Building b : buildings) {
			if(b instanceof FireStation)
				fss.add((FireStation)b);
			if(b instanceof PoliceOffice)
				pos.add((PoliceOffice)b);
			if(b instanceof AmbulanceCenter)
				acs.add((AmbulanceCenter) b);
			if(b instanceof Refuge)
				rfs.add((Refuge) b);
		}
		MapFiles.writeGISMotionlessObjects(gisPw, fss, pos, acs, rfs);
		
		List<FireBrigade> fbs = new ArrayList<FireBrigade>();
		List<PoliceForce> pfs = new ArrayList<PoliceForce>();
		List<AmbulanceTeam> ats = new ArrayList<AmbulanceTeam>();
		List<Civilian> cvs = new ArrayList<Civilian>();
		for(Humanoid h : humanoids) {
			if(h instanceof FireBrigade)
				fbs.add((FireBrigade) h);
			if(h instanceof PoliceForce)
				pfs.add((PoliceForce) h);
			if(h instanceof AmbulanceTeam)
				ats.add((AmbulanceTeam) h);
			if(h instanceof Civilian)
				cvs.add((Civilian) h);
				
		}
		MapFiles.writeGISMovingObjects(gisPw, fbs, pfs, ats, cvs);

		MapFiles.writeGISFires(gisPw, fires);
		
		MapFiles.writeGISImportantBuildings(gisPw, buildings);	// mapfiles filters important already
		//writeOldGIS(gisPw, fss, pos, acs, fbs, pfs, ats, cvs, rfs, fires);
		gisPw.close();
	}
	
	public String toString() {
		String ret = "Map: Nodes: " + nodes.size() + " Roads: " + roads.size() + " Buildings: " + buildings.size() + "\n";
		Point min = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);
		Point max = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
		for(rescuecore.objects.Node n : nodes) {
			if(n.getX() < min.x)
				min.x = n.getX();
			if(n.getY() < min.y)
				min.y = n.getY();
			if(n.getX() > max.x)
				max.x = n.getX();
			if(n.getY() > max.y)
				max.y = n.getY();
		}
		ret += "Bounds: min: " + min + " max: " + max + "\n";
		return ret;
	}
	
	public String toLongString() {
		StringBuffer ret = new StringBuffer();
		ret.append(toString());
		ret.append("Nodes:\n");
		for(rescuecore.objects.Node n : nodes)
			ret.append(n.toLongString() + "\n");
		ret.append("Roads:\n");
		for(Road r : roads)
			ret.append(r.toLongString() + "\n");
		ret.append("Buildings:\n");
		for(Building b : buildings)
			ret.append(b.toLongString() + "\n");	
		ret.append("Humanoids:\n");
		for(Humanoid h : humanoids)
			ret.append(h.toLongString() + "\n");
		
		return ret.toString();
	}
	
	private boolean checkBackRef(rescuecore.objects.Node node, int edge) {
		if (node == null)
			return true;
		for (int e : node.getEdges()) {
			if (e == edge) {
				return true;
			}
		}
		return false;
	}
	
	public boolean check(boolean modify) {
		HashMap<Integer, RealObject> ids = new HashMap<Integer, RealObject>();
		boolean passed = true;
		for (Building b : buildings) {
			if (ids.containsKey(b.getID())) {
				System.err.println("Building with duplicate id: " + b.getID());
				passed = false;
			}
			ids.put(b.getID(), b);
		}
		for (rescuecore.objects.Node n : nodes) {
			if (ids.containsKey(n.getID())) {
				System.err.println("Node with duplicate id: " + n.getID());
				passed = false;
			}
			ids.put(n.getID(), n);
		}
		for (Road r : roads) {
			if (ids.containsKey(r.getID())) {
				System.err.println("Roads with duplicate id: " + r.getID());
				passed = false;
			}
			ids.put(r.getID(), r);
		}
				
		// check entrances and entrance consistency
		for (Building b : buildings) {
			if (b.getEntrances().length == 0) {
				System.err.println("Building " + b.getID() + " has no entrance.");
				passed =  false;
			}
			for (int e : b.getEntrances()) {
				rescuecore.objects.Node n = (Node) ids.get(e);
				if (n == null) {
					System.err.println("Entrance " + e + " of building " + b.getID() + " not found.");
					passed =  false;
				}
				else if (!checkBackRef(n, b.getID())) {
					System.err.println("Entrance " + e + " of building " + b.getID() + " has no edge pointing back.");
					passed = false;
				}
			}
		}
		
		//check road consistency
		for (Road r : roads) {
			rescuecore.objects.Node head = (Node) ids.get(r.getHead());
			rescuecore.objects.Node tail = (Node) ids.get(r.getTail());
			if (head == null) {
				System.err.println("Road " + r.getID() + " has no head.");
				passed = false;
			}
			if (tail == null) {
				System.err.println("Road " + r.getID() + " has no tail.");
				passed = false;
			}
			if (head == tail) {
				System.err.println("Head and Tail of Road " + r.getID() + " are the same.");
				passed = false;
			}
			if (!checkBackRef(head, r.getID())) {
				System.err.println("Head " + head.getID() + " of road " + r.getID() + " has no edge pointing back.");
				passed = false;
			}
			if (!checkBackRef(tail, r.getID())) {
				System.err.println("Head " + tail.getID() + " of road " + r.getID() + " has no edge pointing back.");
				passed = false;
			}
			if(r.getLinesToHead() == 0 || r.getLinesToTail() == 0) {
				System.err.println("Road " + r.getID() + " is a one-way street.");
				passed = false;
			}
			for (Road r2 : roads) {
				if (r2 != r) {
					 if (r.getHead() == r2.getHead() && r.getTail() == r2.getTail()
							 || r.getHead() == r2.getTail() && r.getTail() == r2.getHead()) {
							System.err.println("Roads " +r.getID() + " and " + r2.getID() + " connect the same nodes ("+r.getHead()+", "+r.getTail()+ ").");
							passed = false;
					 }
				}
			}
		}
		
		for (rescuecore.objects.Node n : nodes) {
			if (n.getEdges().length == 0) {
				System.err.println("Node " + n.getID() + " has no edges.");
				passed = false;
			}
			boolean roadsFromEdge = false;
			for (int e : n.getEdges()) {
				RealObject o = ids.get(e);
				if (o == null) {
					System.err.println("Edge " + e + " of node " + n.getID() + " not found");
					passed = false;
				}
				else if (o instanceof Road) {
					if (((Road)o).getHead() != n.getID() && ((Road)o).getTail() != n.getID()) {
						System.err.println("Road " + e + " is edge of node " + n.getID() + " but neither Head or Tail.");
						passed = false;
					}
					roadsFromEdge = true;
				}
				else if (o instanceof Building) {
					boolean found = false;
					for (int ent : ((Building)o).getEntrances() ) {
						if (ent == n.getID()) {
							found = true;
							break;
						}
					}
					if (!found) {
						System.err.println("Building " + e + " is edge of node " + n.getID() + " but node is no entrance");
						passed = false;
					}
				} 
				else {
					System.err.println("Edge is neither Road nor Building");
					passed = false;
				}
				
			}
			if (!roadsFromEdge) {
				System.err.println("Node " + n.getID() + " has no roads.");
				passed = false;
			}
		}
		
		//check for connected graph
		List<Node> open = new ArrayList<Node>();
		HashSet<RealObject> reached = new HashSet<RealObject>();
		open.add(nodes.get(0));
		reached.add(nodes.get(0));
			
		while (!open.isEmpty()) {
			Node n = open.remove(0);
			for (int e : n.getEdges()) {
				RealObject o = ids.get(e);
				reached.add(o);
				if (o instanceof Road) {
					Road r = (Road) o;
					Node next = null;
					if (r.getHead() == n.getID())
						next = (Node) ids.get(r.getTail());
					else if (r.getTail() == n.getID())
						next = (Node) ids.get(r.getHead());
					else
						assert false;
										
					if (!reached.contains(next)) {
						reached.add(next);
						open.add(next);
					}
				}
			}
		}
		
		if (reached.size() != nodes.size() + roads.size() + buildings.size()) {
			System.err.println("road graph is not connected");
			passed = false;
		}
		
		return passed;
	}
	
	
	
	private void writeOldGIS(PrintWriter out, List<FireStation> fs, 
			List<PoliceOffice> po, List<AmbulanceCenter> ac, 
			List<FireBrigade> f, List<PoliceForce> p, 
			List<AmbulanceTeam> a, List<Civilian> c, 
			List<Refuge> r, List<Building> fires) {
		out.println("[MotionLessObject]");
		out.println("FireStationNum="+fs.size());
		out.println("PoliceOfficeNum="+po.size());
		out.println("AmbulanceCenterNum="+ac.size());
		out.println("RefugeNum="+r.size());
		for (int i=0;i<fs.size();++i) out.println("FireStation"+i+"=0,0,"+fs.get(i).getID()+",0");
		for (int i=0;i<po.size();++i) out.println("PoliceOffice"+i+"=0,0,"+po.get(i).getID()+",0");
		for (int i=0;i<ac.size();++i) out.println("AmbulanceCenter"+i+"=0,0,"+ac.get(i).getID()+",0");
		for (int i=0;i<r.size();++i) out.println("Refuge"+i+"=0,0,"+r.get(i).getID()+",0");
		out.println("[MovingObject]");
		out.println("FireBrigadeNum="+f.size());
		out.println("PoliceForceNum="+p.size());
		out.println("AmbulanceTeamNum="+a.size());
		out.println("CivilianNum="+c.size());
		for (int i=0;i<f.size();++i) out.println("FireBrigade"+i+"=0,0,"+f.get(i).getPosition()+",0,0,"+f.get(i).getPositionExtra()+",0");
		for (int i=0;i<p.size();++i) out.println("PoliceForce"+i+"=0,0,"+p.get(i).getPosition()+",0,0,"+p.get(i).getPositionExtra()+",0");
		for (int i=0;i<a.size();++i) out.println("AmbulanceTeam"+i+"=0,0,"+a.get(i).getPosition()+",0,0,"+a.get(i).getPositionExtra()+",0");
		for (int i=0;i<c.size();++i) out.println("Civilian"+i+"=0,0,"+c.get(i).getPosition()+",0,0,"+c.get(i).getPositionExtra()+",0");
		out.println("[Fires]");
		out.println("FirePointNum="+fires.size());
		for (int i=0;i<fires.size();++i) out.println("FirePoint"+i+"=0,0,"+fires.get(i).getID()+",0");
	}

	private void readPolydata(String filename) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(filename));
		
		String line = in.readLine();
		String[] elems = line.split(",");
		int sum = Integer.parseInt(elems[0]);
		int count = Integer.parseInt(elems[1]);

		for (int i=0; i < count; i++) {
			line = in.readLine();
			elems = line.split(",");
			int level = Integer.parseInt(elems[0]);
			int numPoints = Integer.parseInt(elems[1]);
			int[] x = new int[numPoints];
			int[] y = new int[numPoints];
			for (int j=0; j < numPoints; j++) {
				line = in.readLine();
				elems = line.split(",");
				x[j] = Integer.parseInt(elems[0]);
				y[j] = Integer.parseInt(elems[1]);
			}
			GalPolygon poly = new GalPolygon(level, x, y, numPoints);
			quakePolygons.add(poly);
		}
		in.close();
	}

	private void writePolydata(PrintWriter out) throws IOException {
		// olydata format is:
		// Line 1: <number of values to read, including the <level>,<number of vertices> pairs>,<number of polygons>
		// Each polygon then has:
		// <level>,<number of vertices>
		// Followed by <number of vertices> pairs of coordinates
		int sum = 0;
		for (GalPolygon poly : quakePolygons) {
			sum += poly.numPoints*2;
			sum += 2;
		}
		out.print(sum);
		out.print(",");
		out.println(quakePolygons.size());
		for (GalPolygon poly : quakePolygons) {
			out.print(poly.level);
			out.print(",");
			out.println(poly.numPoints);
			for (int j=0;j<poly.numPoints;++j) {
				out.print(poly.xs[j]);
				out.print(",");
				out.println(poly.ys[j]);
			}
		}
	}
	
	
	private void updateIds() {
		for (Node n : nodes) {
			ids.put(n.getID(), n);
		}
		for (Road r : roads) {
			ids.put(r.getID(), r);
		}
		for (Building b : buildings) {
			ids.put(b.getID(), b);
		}
		/*for (Humanoid h : humanoids) {
			ids.put(h.getID(), h);
		}*/
	}
	
	private boolean compareNodes(RCRLegacyMap other) {
		HashSet<Integer> edges = new HashSet<Integer>();
		System.out.println("comparing nodes");
		
		if(this.nodes.size() != other.nodes.size()) {
			System.out.println("Node size mismatch");
			return false;
		}
		
		for (Node n : this.nodes) {
			RealObject o = other.ids.get(n.getID());
			if (o == null || !(o instanceof Node)) {
				System.out.printf("node %d not found\n", n.getID());
				return false;
			}
			Node n2 = (Node) o;
			if (n.getX() != n2.getX() || n.getY() != n2.getY()) {
				if (Math.abs(n.getX()-n2.getX()) > 2 || Math.abs(n.getY()-n2.getY()) > 2) {
					System.out.printf("Coordinate mismatch: (%d,%d) vs. (%d,%d)\n", 
							n.getX(), n.getY(), n2.getX(), n2.getY());
					return false;
				}
			}
			
			if (n.getEdges().length != n2.getEdges().length) {
				System.out.printf("Edge size mismatch %d vs. %d\n", n.getEdges().length, n2.getEdges().length);
				return false;
			}
			edges.clear();
			for (int e : n2.getEdges()) {
				edges.add(e);
			}
			for (int e : n.getEdges()) {
				if (!edges.contains(e)) {
					System.out.printf("Edge %d not found\n", e);
					return false;
				}
			}
			
		}
		return true;
	}

	private boolean compareBuildings(RCRLegacyMap other) {
		HashSet<Integer> entrances = new HashSet<Integer>();
		System.out.println("comparing buildings");
		
		if(this.buildings.size() != other.buildings.size()) {
			System.out.println("Building size mismatch");
			return false;
		}
		
		for (Building b : this.buildings) {
			RealObject o = other.ids.get(b.getID());
			if (o == null || !(o instanceof Building)) {
				System.out.printf("building %d not found\n", b.getID());
				return false;
			}
			Building b2 = (Building) o;
			if (b.getX() != b2.getX() || b.getY() != b2.getY()) {
				if (Math.abs(b.getX()-b2.getX()) > 2 || Math.abs(b.getY()-b2.getY()) > 2) {
					System.out.printf("Coordinate mismatch: (%d,%d) vs. (%d,%d)\n", 
							b.getX(), b.getY(), b2.getX(), b2.getY());
					return false;
				}
			}
			
			if (b.getApexes().length != b2.getApexes().length) {
				System.out.println("Apex size mismatch");
				return false;
			}
			for (int i=0; i < b.getApexes().length; i++) {
				if (Math.abs(b.getApexes()[i] - b2.getApexes()[i]) > 2) {
					System.out.printf("Apex mismatch: %d vs. %d\n", b.getApexes()[i], b2.getApexes()[i]);
					return false;
				}
			}

			if (Math.abs(b.getGroundArea() - b2.getGroundArea()) > 4) {
				System.out.printf("Area mismatch on %d: %d vs. %d\n", b.getID(), b.getGroundArea(), b2.getGroundArea());
				//return false;
			}
			
			if (b.getEntrances().length != b2.getEntrances().length) {
				System.out.println("Entrance size mismatch");
				return false;
			}
			
			entrances.clear();
			for (int e : b2.getEntrances()) {
				entrances.add(e);
			}
			for (int e : b.getEntrances()) {
				if (!entrances.contains(e)) {
					System.out.printf("Entrance %d not found\n", e);
					return false;
				}
			}

			for (int prop : buildingCompareAttributes) {
				IntProperty p1 = (IntProperty) b.getProperty(prop);
				IntProperty p2 = (IntProperty) b2.getProperty(prop);
				if (p1.getValue() != p2.getValue()) {
					System.out.printf("Mismatch on %d: %s vs. %s\n", b.getID(), p1.toString(), p2.toString());
					return false;
				}
			}
		}
		return true;
	}
	
	public boolean compareRoads(RCRLegacyMap other) {
		System.out.println("comparing roads");
		
		if(this.roads.size() != other.roads.size()) {
			System.out.println("Road size mismatch");
			return false;
		}
		
		for (Road r : this.roads) {
			RealObject o = other.ids.get(r.getID());
			if (o == null || !(o instanceof Road)) {
				System.out.printf("node %d not found\n", r.getID());
				return false;
			}
			Road r2 = (Road) o;

			if (Math.abs(r.getLength() - r2.getLength()) > 2) {
				System.out.printf("Length mismatch on %d: %d vs. %d\n", r.getID(), r.getLength(), r2.getLength());
				return false;
			}

			for (int prop : roadCompareAttributes) {
				IntProperty p1 = (IntProperty) r.getProperty(prop);
				IntProperty p2 = (IntProperty) r2.getProperty(prop);
				if (p1.getValue() != p2.getValue()) {
					System.out.printf("Mismatchon %d: %s vs. %s", r.getID(), p1.toString(), p2.toString());
					return false;
				}
			}
		}
		return true;
	}
	
}
