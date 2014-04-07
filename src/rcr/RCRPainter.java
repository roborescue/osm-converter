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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.paint.WireframeMapRenderer;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.tools.Pair;

import rcr.export.RCRRoad;
import rescuecore.RescueConstants;

public class RCRPainter extends WireframeMapRenderer {

	private volatile RCRDataSet data;
	public volatile boolean showErrors;

	/**
     * {@inheritDoc}
     */
    public RCRPainter(Graphics2D g, RCRDataSet data, NavigatableComponent nc, boolean isInactiveMode) {
        super(g, nc, isInactiveMode);
    	this.data = data;
    }
	
    @Override 
    public void render(DataSet data, boolean virtual, Bounds box) {
    	super.render(data, virtual, box);
    }
    
    public void visit(Way w) {
    	if (w.hasTag("rcr:type", "block")) {
    		drawArea(w, new Color(70,70,70,15));
    	}
    	if (w.hasTag("rcr:type", "filteredblock")) {
    		drawArea(w, new Color(0,100,100,20));
    	}
    	if (w.hasTag("rcr:type", "forbiddenblock")) {
    		drawArea(w, new Color(100,0,0,20));
    	}
    	if (w.hasTag("rcr:type", "splitblock")) {
    		drawArea(w, new Color(0,100,0,20));
    	}
    	if (w.hasTag("rcr:type", "cutblock")) {
    		drawArea(w, new Color(100,100,0,20));
    	}
    	if (w.hasTag("rcr:type", "firstsplit")) {
    		drawArea(w, new Color(0,0,100,20));
    	}
    	if (w.hasTag("rcr:type", "containedblock")) {
    		drawArea(w, new Color(0,100,0,20));
    	}
    	if (w.hasTag("rcr:type", "containingblock")) {
    		drawArea(w, new Color(100,0,0,20));
    	}
    	if (w.hasTag("rcr:type", "road")) {
    		drawRoad(w);
    	}
    	if (w.hasTag("rcr:type", "building")) {
    		drawBuilding(w);
    	}
    	super.visit(w);
    }

    public void visit(Node n) {
    	if (n.get("rcr:ambulances") != null)
    		drawAgent(n, Color.WHITE);
    	if (n.get("rcr:firebrigades") != null)
    		drawAgent(n, Color.RED);
    	if (n.get("rcr:policeforces") != null)
    		drawAgent(n, Color.YELLOW);
    	if (n.get("rcr:civilians") != null)
    		drawAgent(n, Color.GREEN);
    	
    	super.visit(n);
    }
    
    private void buildingError(Way outline, Node node) {
    	if (outline != null)
    		drawArea(outline, Color.RED);
    	else if (node != null)
    		drawNode(node, Color.RED, taggedNodeSize, true);
    }

    public void drawBuilding(Way b) {
    	Color c = Color.GRAY;
    	if (showErrors && data.hasError(b)) 
    		c = Color.RED;
    	else if (b.get("rcr:fire") != null)
    		c = Color.ORANGE;
    	else if (b.hasTag("rcr:building_type", "refuge"))
    		c = Color.GREEN;
    	else if (b.hasTag("rcr:building_type", "ambulancecenter", "policeoffice", "firestation"))
    		c = Color.WHITE;
        	
    	drawArea(b, c);

    	if (data.entrances.containsKey(b)) {
    		Line2D e = data.entrances.get(b).getLine();
    		Point p1 = nc.getPoint(Vector.asEastNorth(e.getP1()));
    		Point p2 = nc.getPoint(Vector.asEastNorth(e.getP2()));
    		g.setColor(Color.WHITE);
    		g.drawLine(p1.x, p1.y, p2.x, p2.y);
    	}
    	
    }
    
    public void visit(Relation r) {
    	if (r.hasTag("rcr:type", "building")) {
    		List<Node> entrances = new ArrayList<Node>();
    		Way outline = null;
    		Node node = null;
    		for (RelationMember m : r.getMembers()) {
    			if (m.getRole().equals("rcr:entrance"))
    				entrances.add(m.getNode());
    			else if (m.getRole().equals("rcr:node"))
    				node = m.getNode();
    			else if (m.getRole().equals("rcr:outline"))
    				outline = m.getWay();
    		}
    		
    		if (showErrors && (outline == null || node == null || entrances.isEmpty())) {
    			buildingError(outline, node);
    			return;
    		}
    		
    		if (outline == null || node == null)
    			return;
    		
    		drawBuilding(outline);
    		        	
    		for (Node e: entrances) {
    			if (showErrors && !e.hasTag("rcr:type", "node")) {
    				drawNode(e, Color.RED, taggedNodeSize, true);
    			}
    			Point rcrP1 = data.osm2rescueCoords(node);
    			Point rcrP2 = data.osm2rescueCoords(e);
    			if (showErrors && rcrP1.distance(rcrP2) > RescueConstants.MAX_EXTINGUISH_DISTANCE/data.getScale()) {
                    g.setColor(Color.RED);
    			}
    			else {
                    g.setColor(Color.DARK_GRAY);
    			}
    			
                Point p1 = nc.getPoint(node.getEastNorth());
                Point p2 = nc.getPoint(e.getEastNorth());
                g.drawLine(p1.x, p1.y, p2.x, p2.y);
    		}
    	}
    }
    
    public void drawRoad(Way w) {
    	Color areaColor = new Color(100, 100, 150, 50);
    	Color offsetColor = new Color(200, 200, 200, 150);
    	    	
    	if (data.gmlSegments.containsKey(w)) {
    		offsetColor = new Color(200, 250, 200, 150);
    		if (showErrors && data.hasError(w)) {
    			offsetColor = Color.RED;
    		}
    		for (RCRRoad r : data.gmlSegments.get(w)) {
    			for (Line2D l : r.getOutline()) {
    				Point p1 = nc.getPoint(Vector.asEastNorth(l.getP1()));
    				Point p2 = nc.getPoint(Vector.asEastNorth(l.getP2()));
    				drawSegment(p1, p2, offsetColor, false);
    				
    			}
    		}
    		return;
    	}

    	if (showErrors && data.hasError(w)) {
    		offsetColor = Color.RED;
    		areaColor = new Color(255, 0, 0, 50);
//    		for (Pair<Node, Node> pair : w.getNodePairs(false)) {
//    			Point p1 = nc.getPoint(pair.a.getEastNorth());
//    			Point p2 = nc.getPoint(pair.b.getEastNorth());
//                drawSegment(p1, p2, Color.RED, false);
//    		}
    	}
    	
		if (w.hasAreaTags() && w.isArea()) {
			drawArea(w, areaColor);			
		}
		else {
			double dist = RCRDataSet.parseInt(w, "rcr:width", 6000) / 2000.0;
			for (Pair<Node, Node> pair : w.getNodePairs(false)) {
				EastNorth c1 = pair.a.getEastNorth();
				EastNorth c2 = pair.b.getEastNorth();

				EastNorth offset = Vector.asEastNorth(Vector.offset(
						Vector.asPoint(c1), Vector.asPoint(c2), dist));
				Point p1 = nc.getPoint(c1.add(offset));
				Point p2 = nc.getPoint(c2.add(offset));
				drawSegment(p1, p2, offsetColor, false);

				offset = Vector.asEastNorth(Vector.offset(Vector.asPoint(c1),
						Vector.asPoint(c2), -dist));
				p1 = nc.getPoint(c1.add(offset));
				p2 = nc.getPoint(c2.add(offset));
				drawSegment(p1, p2, offsetColor, false);
			}
    	}
    }
    
    public void drawAgent(Node n, Color color) {
        Point p = nc.getPoint(n.getEastNorth());
        int radius = 10;
        g.setColor(color);
    	g.fillOval(p.x - radius, p.y-radius, radius*2, radius*2);
        g.setColor(Color.BLACK);
    	g.drawOval(p.x - radius, p.y-radius, radius*2, radius*2);
        radius -=3;
    	g.drawOval(p.x - radius, p.y-radius, radius*2, radius*2);
    }
    
    protected void drawArea(Way w, Color color)
    {
        Polygon polygon = getPolygon(w);

        /* set the opacity (alpha) level of the filled polygon */
//        if (color.getAlpha() < 250) {
        	g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 200));
//        }
//        else {
//        	g.setColor(color);
//        }
        g.fillPolygon(polygon);
    }

    protected Polygon getPolygon(Way w)
    {
        Polygon polygon = new Polygon();

        for (Node n : w.getNodes())
        {
            Point p = nc.getPoint(n.getEastNorth());
            polygon.addPoint(p.x,p.y);
        }
        return polygon;
    }

}
