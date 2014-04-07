/*
 * Last change: $Date: 2004/05/04 03:09:39 $
 * $Revision: 1.5 $
 *
 * Copyright (c) 2004, The Black Sheep, Department of Computer Science, The University of Auckland
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of The Black Sheep, The Department of Computer Science or The University of Auckland nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package rescuecore.tools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;

import rescuecore.Memory;
import rescuecore.RescueConstants;
import rescuecore.RescueObject;
import rescuecore.objects.AmbulanceCenter;
import rescuecore.objects.AmbulanceTeam;
import rescuecore.objects.Building;
import rescuecore.objects.Civilian;
import rescuecore.objects.FireBrigade;
import rescuecore.objects.FireStation;
import rescuecore.objects.Node;
import rescuecore.objects.PoliceForce;
import rescuecore.objects.PoliceOffice;
import rescuecore.objects.Refuge;
import rescuecore.objects.Road;

public class MapFiles {
    private final static int GIS_TYPE_AMBULANCE_CENTER = 2;
    private final static int GIS_TYPE_FIRE_STATION = 3;
    private final static int GIS_TYPE_POLICE_OFFICE = 4;
    private final static int GIS_TYPE_REFUGE = 5;
    private final static int GIS_TYPE_CIVILIAN = 6;
    private final static int GIS_TYPE_AMBULANCE_TEAM = 7;
    private final static int GIS_TYPE_FIRE_BRIGADE = 8;
    private final static int GIS_TYPE_POLICE_FORCE = 9;
    private final static int GIS_TYPE_FIRE = 10;
    private final static String[] GIS_TYPES = {"","","AmbulanceCenter","FireStation","PoliceOffice","Refuge","Civilian","AmbulanceTeam","FireBrigade","PoliceForce","FirePoint"};


    private static int readInt(InputStream in) throws IOException {
		int result = (in.read()&0xFF) | ((in.read() << 8) & 0xFF00) | ((in.read() << 16) & 0xFF0000) | ((in.read() << 24) & 0xFF000000);
		return result;
    }

    private static void writeInt(OutputStream out, int value) throws IOException {
		out.write(value&0xFF);
		out.write((value>>8)&0xFF);
		out.write((value>>16)&0xFF);
		out.write((value>>24)&0xFF);
    }

	public static Node[] loadNodes() throws IOException {
		return loadNodes("node.bin");
	}

	public static Node[] loadNodes(String file) throws IOException {
		System.out.print("Loading nodes from "+file);
		InputStream in = null;
		try {
			in = new BufferedInputStream(new FileInputStream(file));
			// Skip something, not sure what
			readInt(in);
			readInt(in);
			readInt(in);
			int num = readInt(in);
			//			System.out.println("Loading "+num+" nodes");
			Node[] result = new Node[num];
			for (int i=0;i<num;++i) {
				int size = readInt(in);
				int id = readInt(in);
				int x = readInt(in);
				int y = readInt(in);
				int numEdges = readInt(in);
				int[] edges = new int[numEdges];
				int[] shortcut = new int[numEdges];
				int[] pocket = new int[numEdges*2];
				int[] signalTiming = new int[numEdges*3];
				for (int j=0;j<numEdges;++j) edges[j] = readInt(in);
				int signal = readInt(in);
				for (int j=0;j<numEdges;++j) shortcut[j] = readInt(in); // shortcutToTurn
				for (int j=0;j<numEdges;++j) {
					pocket[j*2] = readInt(in); // pocketToTurnAcross
					pocket[j*2 + 1] = readInt(in);
				}
				for (int j=0;j<numEdges;++j) {
					signalTiming[j*3] = readInt(in); // signalTiming
					signalTiming[j*3 + 1] = readInt(in);
					signalTiming[j*3 + 2] = readInt(in);
				}
				result[i] = new Node(x,y,edges,signal!=0,shortcut,pocket,signalTiming);
				result[i].setID(id);
				System.out.print(".");
			}
			System.out.println();
			return result;
		}
		finally {
			if (in!=null) in.close();
		}		
	}

	public static Road[] loadRoads() throws IOException {
		return loadRoads("road.bin");
	}

	public static Road[] loadRoads(String file) throws IOException {
		System.out.print("Loading roads from "+file);
		InputStream in = null;
		try {
			in = new BufferedInputStream(new FileInputStream(file));
			readInt(in);
			readInt(in);
			readInt(in);
			int num = readInt(in);
			Road[] result = new Road[num];
			for (int i=0;i<num;++i) {
				int size = readInt(in);
				int id = readInt(in);
				int head = readInt(in);
				int tail = readInt(in);
				int length = readInt(in);
				int roadKind = readInt(in);
				int carsToHead = readInt(in);
				int carsToTail = readInt(in);
				int humansToHead = readInt(in);
				int humansToTail = readInt(in);
				int width = readInt(in);
				int block = readInt(in);
				int repairCost = readInt(in);
				int median = readInt(in);
				int linesToHead = readInt(in);
				int linesToTail = readInt(in);
				int widthForWalkers = readInt(in);
				result[i] = new Road(head,tail,length,roadKind,carsToHead,carsToTail,humansToHead,humansToTail,width,block,repairCost,median!=0,linesToHead,linesToTail,widthForWalkers);
				result[i].setID(id);
				System.out.print(".");
			}
			System.out.println();
			return result;
		}
		finally {
			if (in!=null) in.close();
		}
	}

	public static Building[] loadBuildings() throws IOException {
		return loadBuildings("building.bin");
	}

	public static Building[] loadBuildings(String file) throws IOException {
		System.out.print("Loading buildings from "+file);
		InputStream in = null;
		try {
			in = new BufferedInputStream(new FileInputStream(file));
			readInt(in);
			readInt(in);
			readInt(in);
			int num = readInt(in);
			Building[] result = new Building[num];
			for (int i=0;i<num;++i) {
				int size = readInt(in);
				int id = readInt(in);
				int x = readInt(in);
				int y = readInt(in);
				int floors = readInt(in);
				int attributes = readInt(in);
				int ignition = readInt(in);
				int fieryness = readInt(in);
				int brokenness = readInt(in);
				int numEntrances = readInt(in);
				int[] entrances = new int[numEntrances];
				for (int j=0;j<numEntrances;++j) entrances[j] = readInt(in);
				int shapeID = readInt(in);
				int area = readInt(in);
				int totalArea = readInt(in);
				int code = readInt(in);
				int numApexes = readInt(in);
				int[] apexes = new int[numApexes*2];
				for (int j=0;j<numApexes;++j) {
					// Apexes
					apexes[j*2] = readInt(in);
					apexes[j*2 + 1] = readInt(in);
				}
				result[i] = new Building(x,y,floors,attributes,ignition!=0,fieryness,brokenness,entrances,code,area,totalArea,apexes,0,1);
				result[i].setID(id);
				System.out.print(".");
			}
			System.out.println();
			return result;
		}
		finally {
			if (in!=null) in.close();
		}
	}
	
	public static void writeBuildings(String file, Building[] bs) throws IOException{
		BufferedOutputStream out = null;
		try {
			out = new BufferedOutputStream(new FileOutputStream(file));
			writeInt(out,0);
			writeInt(out,0);
			writeInt(out,0);

			writeInt(out,bs.length);
			for(int i = 0; i < bs.length; i++){
				writeInt(out,getSize(bs[i]));
				writeInt(out,bs[i].getID());
				writeInt(out,bs[i].getX());
				writeInt(out,bs[i].getY());
				writeInt(out,bs[i].getFloors());
				writeInt(out,bs[i].getBuildingAttributes());
				writeInt(out,bs[i].isIgnited()?1:0);
				writeInt(out,bs[i].getFieryness());
				writeInt(out,bs[i].getBrokenness());
				int[] ent = bs[i].getEntrances();
				writeInt(out,ent.length);
				for(int j = 0; j < ent.length; j++)
					writeInt(out,ent[j]);
				writeInt(out,0/*bs[i].getBuildingShapeID()*/);
				writeInt(out,bs[i].getGroundArea());
				writeInt(out,bs[i].getTotalArea());
				writeInt(out,bs[i].getBuildingCode());
				int[] ap = bs[i].getApexes();
				writeInt(out,ap.length/2);
				for(int j = 0; j < ap.length; j++)
					writeInt(out,ap[j]);
			}
		}
		finally {
			if (out!=null) out.close();
		}
	}

	public static void writeBuildings(String file, List<Building> bs) throws IOException{
		BufferedOutputStream out = null;
		try {
			out = new BufferedOutputStream(new FileOutputStream(file));
			writeInt(out,0);
			writeInt(out,0);
			writeInt(out,0);

			writeInt(out,bs.size());
			for(Building b : bs){
				writeInt(out,getSize(b));
				writeInt(out,b.getID());
				writeInt(out,b.getX());
				writeInt(out,b.getY());
				writeInt(out,b.getFloors());
				writeInt(out,b.getBuildingAttributes());
				writeInt(out,b.isIgnited()?1:0);
				writeInt(out,b.getFieryness());
				writeInt(out,b.getBrokenness());
				int[] ent = b.getEntrances();
				writeInt(out,ent.length);
				for(int j = 0; j < ent.length; j++)
					writeInt(out,ent[j]);
				writeInt(out,0/*b.getBuildingShapeID()*/);
				writeInt(out,b.getGroundArea());
				writeInt(out,b.getTotalArea());
				writeInt(out,b.getBuildingCode());
				int[] ap = b.getApexes();
				writeInt(out,ap.length/2);
				for(int j = 0; j < ap.length; j++)
					writeInt(out,ap[j]);
			}
		}
		finally {
			if (out!=null) out.close();
		}
	}

	public static void writeNodes(String file, Node[] ns) throws IOException{
		BufferedOutputStream out = null;
		try {
			out = new BufferedOutputStream(new FileOutputStream(file));
			writeInt(out,5);
			writeInt(out,-152950000);
			writeInt(out,52050000);

			writeInt(out,ns.length);
			//			System.out.println("Writing "+ns.length+" nodes");
			for(int i = 0; i < ns.length; i++){
				writeInt(out,getSize(ns[i]));
				writeInt(out,ns[i].getID());
				writeInt(out,ns[i].getX());
				writeInt(out,ns[i].getY());
				int[] ed = ns[i].getEdges();
				writeInt(out,ed.length);
				//				System.out.println("Node "+ns[i].getID()+" has "+ed.length+" edges");
				for(int j = 0; j < ed.length; j++)
					writeInt(out,ed[j]);
				writeInt(out,ns[i].hasSignal()?1:0);
				int[] sh = ns[i].getShortcutToTurn();
				for(int j = 0; j < sh.length; j++)
					writeInt(out,sh[j]);
				int[] p = ns[i].getPocketToTurnAcross();
				for(int j = 0; j < p.length; j++)
					writeInt(out,p[j]);
				int[] st = ns[i].getSignalTiming();
				for(int j = 0; j < st.length; j++)
					writeInt(out,st[j]);
			}
		}
		finally {
			if (out!=null) out.close();
		}
	}

	public static void writeNodes(String file, List<Node> ns) throws IOException{
		BufferedOutputStream out = null;
		try {
			out = new BufferedOutputStream(new FileOutputStream(file));
			writeInt(out,5);
			writeInt(out,-152950000);
			writeInt(out,52050000);

			writeInt(out,ns.size());
			//			System.out.println("Writing "+ns.length+" nodes");
			for(Node n : ns) {
				writeInt(out,getSize(n));
				writeInt(out,n.getID());
				writeInt(out,n.getX());
				writeInt(out,n.getY());
				int[] ed = n.getEdges();
				writeInt(out,ed.length);
				//				System.out.println("Node "+n.getID()+" has "+ed.length+" edges");
				for(int j = 0; j < ed.length; j++)
					writeInt(out,ed[j]);
				writeInt(out,n.hasSignal()?1:0);
				int[] sh = n.getShortcutToTurn();
				for(int j = 0; j < sh.length; j++)
					writeInt(out,sh[j]);
				int[] p = n.getPocketToTurnAcross();
				for(int j = 0; j < p.length; j++)
					writeInt(out,p[j]);
				int[] st = n.getSignalTiming();
				for(int j = 0; j < st.length; j++)
					writeInt(out,st[j]);
			}
		}
		finally {
			if (out!=null) out.close();
		}
	}

	public static void writeRoads(String file, Road[] roads) throws IOException {
		BufferedOutputStream out = null;
		try {
			out = new BufferedOutputStream(new FileOutputStream(file));
			writeInt(out,5);
			writeInt(out,-152950000);
			writeInt(out,52050000);

			writeInt(out,roads.length);
			for(int i = 0; i < roads.length; i++){
				writeInt(out,getSize(roads[i]));
				writeInt(out,roads[i].getID());
				writeInt(out,roads[i].getHead());
				writeInt(out,roads[i].getTail());
				writeInt(out,roads[i].getLength());
				writeInt(out,roads[i].getRoadKind());
				writeInt(out,roads[i].getCarsPassToHead());
				writeInt(out,roads[i].getCarsPassToTail());
				writeInt(out,roads[i].getHumansPassToHead());
				writeInt(out,roads[i].getHumansPassToTail());
				writeInt(out,roads[i].getWidth());
				writeInt(out,roads[i].getBlock());
				writeInt(out,roads[i].getRepairCost());
				writeInt(out,roads[i].hasMedian()?1:0);
				writeInt(out,roads[i].getLinesToHead());
				writeInt(out,roads[i].getLinesToTail());
				writeInt(out,roads[i].getWidthForWalkers());
			}
		}
		finally {
			if (out!=null) out.close();
		}
	}

	public static void writeRoads(String file, List<Road> roads) throws IOException {
		BufferedOutputStream out = null;
		try {
			out = new BufferedOutputStream(new FileOutputStream(file));
			writeInt(out,5);
			writeInt(out,-152950000);
			writeInt(out,52050000);

			writeInt(out,roads.size());
			for(Road r : roads) {
				writeInt(out,getSize(r));
				writeInt(out,r.getID());
				writeInt(out,r.getHead());
				writeInt(out,r.getTail());
				writeInt(out,r.getLength());
				writeInt(out,r.getRoadKind());
				writeInt(out,r.getCarsPassToHead());
				writeInt(out,r.getCarsPassToTail());
				writeInt(out,r.getHumansPassToHead());
				writeInt(out,r.getHumansPassToTail());
				writeInt(out,r.getWidth());
				writeInt(out,r.getBlock());
				writeInt(out,r.getRepairCost());
				writeInt(out,r.hasMedian()?1:0);
				writeInt(out,r.getLinesToHead());
				writeInt(out,r.getLinesToTail());
				writeInt(out,r.getWidthForWalkers());
			}
		}
		finally {
			if (out!=null) out.close();
		}
	}

	public static void writeGISMotionlessObjects(PrintWriter out, FireStation[] fire, PoliceOffice[] police, AmbulanceCenter[] ambulance, Refuge[] refuge) {
		out.println("# Motionless Objects");
		for (int i=0;i<fire.length;++i) writeFixedObjectData(out,GIS_TYPE_FIRE_STATION,fire[i]);
		for (int i=0;i<police.length;++i) writeFixedObjectData(out,GIS_TYPE_POLICE_OFFICE,police[i]);
		for (int i=0;i<ambulance.length;++i) writeFixedObjectData(out,GIS_TYPE_AMBULANCE_CENTER,ambulance[i]);
		for (int i=0;i<refuge.length;++i) writeFixedObjectData(out,GIS_TYPE_REFUGE,refuge[i]);
	}

	public static void writeGISMotionlessObjects(PrintWriter out, List<FireStation> fire, List<PoliceOffice> police, List<AmbulanceCenter> ambulance, List<Refuge> refuge) {
		out.println("# Motionless Objects");
		for (FireStation fs : fire) writeFixedObjectData(out,GIS_TYPE_FIRE_STATION,fs);
		for (PoliceOffice po : police) writeFixedObjectData(out,GIS_TYPE_POLICE_OFFICE,po);
		for (AmbulanceCenter ac : ambulance) writeFixedObjectData(out,GIS_TYPE_AMBULANCE_CENTER, ac);
		for (Refuge rf : refuge) writeFixedObjectData(out,GIS_TYPE_REFUGE,rf);
	}

	public static void writeGISMovingObjects(PrintWriter out, FireBrigade[] fire, PoliceForce[] police, AmbulanceTeam[] ambulance, Civilian[] civ, Memory m) {
		out.println("# Moving Objects");
		for (int i=0;i<civ.length;++i) writeMovingObjectData(out,GIS_TYPE_CIVILIAN,m.lookup(civ[i].getPosition()),m);
		for (int i=0;i<ambulance.length;++i) writeMovingObjectData(out,GIS_TYPE_AMBULANCE_TEAM,m.lookup(ambulance[i].getPosition()),m);
		for (int i=0;i<fire.length;++i) writeMovingObjectData(out,GIS_TYPE_FIRE_BRIGADE,m.lookup(fire[i].getPosition()),m);
		for (int i=0;i<police.length;++i) writeMovingObjectData(out,GIS_TYPE_POLICE_FORCE,m.lookup(police[i].getPosition()),m);
	}

	public static void writeGISMovingObjects(PrintWriter out, FireBrigade[] fire, PoliceForce[] police, AmbulanceTeam[] ambulance, Civilian[] civ) {
		out.println("# Moving Objects");
		for (int i=0;i<civ.length;++i) writeMovingObjectData(out,GIS_TYPE_CIVILIAN, (civ[i].getPosition()));
		for (int i=0;i<ambulance.length;++i) writeMovingObjectData(out,GIS_TYPE_AMBULANCE_TEAM, (ambulance[i].getPosition()));
		for (int i=0;i<fire.length;++i) writeMovingObjectData(out,GIS_TYPE_FIRE_BRIGADE, (fire[i].getPosition()));
		for (int i=0;i<police.length;++i) writeMovingObjectData(out,GIS_TYPE_POLICE_FORCE, (police[i].getPosition()));
	}

	public static void writeGISMovingObjects(PrintWriter out, List<FireBrigade> fire, List<PoliceForce> police, List<AmbulanceTeam> ambulance, List<Civilian> civ) {
		out.println("# Moving Objects");
		for (Civilian cv : civ) writeMovingObjectData(out,GIS_TYPE_CIVILIAN, (cv.getPosition()));
		for (AmbulanceTeam at : ambulance) {
			writeMovingObjectData(out,GIS_TYPE_AMBULANCE_TEAM, (at.getPosition()));
			System.out.println("AMB");
		}
		for (FireBrigade fb : fire) writeMovingObjectData(out,GIS_TYPE_FIRE_BRIGADE, (fb.getPosition()));
		for (PoliceForce pf : police) writeMovingObjectData(out,GIS_TYPE_POLICE_FORCE, (pf.getPosition()));
	}
	
	public static void writeGISFires(PrintWriter out, Building[] fires) {
		out.println("# Fires");
		for (int i=0;i<fires.length;++i) {
			writeFixedObjectData(out,GIS_TYPE_FIRE,fires[i]);
		}
	}

	public static void writeGISFires(PrintWriter out, List<Building> fires) {
		out.println("# Fires");
		for (Building b : fires) {
			writeFixedObjectData(out,GIS_TYPE_FIRE,b);
		}
	}

	public static void writeGISImportantBuildings(PrintWriter out, Building[] buildings) {
		out.println("# Important buildings");
		for (int i=0;i<buildings.length;++i) {
			if (buildings[i].getImportance()>1) {
				out.print("ImportantBuilding ");
				out.print(buildings[i].getID());
				out.print("=");
				out.println(buildings[i].getImportance());
			}
		}
	}

	public static void writeGISImportantBuildings(PrintWriter out, List<Building> buildings) {
		out.println("# Important buildings");
		for(Building b : buildings) {
			if (b.getImportance()>1) {
				out.print("ImportantBuilding ");
				out.print(b.getID());
				out.print("=");
				out.println(b.getImportance());
			}
		}
	}

    private static void writeFixedObjectData(PrintWriter out, int type, Building b) {
		// Fixed objects are of them form TYPE = id
		out.print(GIS_TYPES[type]);
		out.print("=");
		out.print(b.getID());
		out.println();
    }

    private static void writeMovingObjectData(PrintWriter out, int type, RescueObject location, Memory m) {
		// Moving objects are of the form TYPE = position [,positionExtra]
		out.print(GIS_TYPES[type]);
		out.print("=");
		out.print(location.getID());
		if (location.isRoad()) {
			Road r = (Road)location;
			Node head = m.getHead(r);
			Node tail = m.getTail(r);
			int extra = (int)((Math.random()*r.getLength())/1000);
			out.print(",");
			out.print(extra);
		}
		out.println();
    }

    private static void writeMovingObjectData(PrintWriter out, int type, int location) {
		// Moving objects are of the form TYPE = position [,positionExtra]
		out.print(GIS_TYPES[type]);
		out.print("=");
		out.print(location);
		out.println();
    }
    
	private static int getSize(Building b){
		return (15 + b.getEntrances().length + b.getApexes().length)*RescueConstants.INT_SIZE;
	}
	
	private static int getSize(Node n){
		return (6 + n.getEdges().length*7)*RescueConstants.INT_SIZE;
	}

	private static int getSize(Road r){
		return 16*RescueConstants.INT_SIZE;
	}
}
