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

package tools;

import static rcr.Vector.dotPNorm;
import static rcr.Vector.diff;
import static rcr.Vector.epsilon;
import static rcr.Vector.fromLine;
import static rcr.Vector.length;
import static rcr.Vector.normalVec;
import static rcr.Vector.sum;
import static rcr.Vector.times;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

import rcr.Vector;

/**
 * A polygon class in metric UTM coordinates
 * 
 */
public class AdvArea extends Area {
	private static final long serialVersionUID = 6428423903567988330L;
	
	private static final double mergeDistance = 0.3;
	
	private Node m_coordRef;
	
	public List<Node> source;
	
	public long nref;
	public boolean dirRef;
	public long ref;	/// for giving some optional reference
	
	public AdvArea() {
	}
	
	// deg to decimeter
	int [] fakeConv(Node n) {
		int [] ret = new int[2];
		double earth = 40*1000*1000*10.0;	// 40.000km in dm
		double oneDegree = earth/2.0/180.0;	// 1° in dm
		double la = n.getCoor().lat() - 47.0;	//TODO: HUGE HACK
		double lo = n.getCoor().lon() - 7.0;
		double dmX = la/oneDegree;
		double dmY = lo/oneDegree;
		ret[0] = (int) dmX;
		ret[1] = (int) dmY;
		return ret;
	}
	
	boolean db = false; 
	public AdvArea(List<Node> nodes) {
		super(fromNodeList(nodes));
		this.source = nodes;
		m_coordRef = nodes.get(0);
	}
	
	public AdvArea(List<Node> nodes, long ref) {
		super(fromNodeList(nodes));
		this.ref = ref;
		this.source = nodes;
		m_coordRef = nodes.get(0);
	}
	
	public AdvArea(List<? extends Point2D> points, Node refNode) {
		super(fromPoints(points));
		m_coordRef = refNode;
	}
	
	public AdvArea(Shape shape, Node refNode) {
		super(shape);
		m_coordRef = refNode;
	}

	public AdvArea(AdvArea a) {
		super(a);
		m_coordRef = a.m_coordRef;
		this.ref = a.ref;
		this.nref = a.nref;
		this.dirRef = a.dirRef;
	}
	
	public List<Point2D.Double> toPoints() {
		double[] point = new double[6];
		List<Point2D.Double> result = new ArrayList<Point2D.Double>();
		Point2D.Double start = null;
		PathIterator i = this.getPathIterator(null);
		for(;!i.isDone(); i.next()) {
			int type = i.currentSegment(point); 
			Point2D.Double p = new Point2D.Double(point[0], point[1]);
			switch(type) {
			case PathIterator.SEG_MOVETO:
				start = p;
				result.add(p);
				break;
			case PathIterator.SEG_LINETO:
				if (p.distance(start) > mergeDistance) {
					result.add(p);
				}
				break;
			case PathIterator.SEG_CLOSE:
				break;
			default:
				assert(false);
			}
		}
		//System.out.println(result.size() + " vertices");
		/*for(int j=0; j<result.size(); j++) {
			Point2D.Double p = result.get(j);
			for (int k=j+1; k<result.size(); k++) {
				if ( p.distance(result.get(k)) < mergeDistance )
				{
					System.out.println("remove " + result.get(k) +" , close to " + p + " (diff = " + diff(p, result.get(k))+")");
					result.remove(k);
					k--;
				}
			}
		}*/
		//if (nearZero(diff(result.get(0), result.get(result.size()-1)))) {
		//	result.remove(result.size()-1);
		//}
		return result;
	}

	/// suggest a thin area, that divides the surrounding area
	public AdvArea suggestCutArea() {
		double fact = 0;
		double longestLineLength = 0;
		Line2D longest = null;
		for(Line2D l : toSegments()) {
			if(length(fromLine(l)) > longestLineLength) {
				longestLineLength = length(fromLine(l));
				longest = l;
			}
			fact += length(fromLine(l));	// this should be way enough
		}
		if(longest == null)
			return null;

		Point2D v1 = times(Vector.normalize(fromLine(longest)), fact);
		Point2D v2 = times(Vector.normalize(fromLine(longest)), -fact);
		Point2D n = times(normalVec(fromLine(longest)), 1.0);
		
		double ox = longest.getP1().getX();
		double oy = longest.getP1().getY();

		List<Point2D> pts = new ArrayList<Point2D>();
		pts.add(new Point2D.Double(ox, oy));
		pts.add(new Point2D.Double(v1.getX() - n.getX() + ox, v1.getY() - n.getY() + oy));
		pts.add(new Point2D.Double(v1.getX() + n.getX() + ox, v1.getY() + n.getY() + oy));
		pts.add(new Point2D.Double(ox + n.getX(), oy + n.getY()));
		pts.add(new Point2D.Double(v2.getX() + n.getX() + ox, v2.getY() + n.getY() + oy));
		pts.add(new Point2D.Double(v2.getX() - n.getX() + ox, v2.getY() - n.getY() + oy));
		
		return new AdvArea(pts, this.m_coordRef);
	}
	
	public List<Line2D.Double> toSegments() {
		double[] point = new double[6];
		List<Line2D.Double> result = new ArrayList<Line2D.Double>();
		
		Point2D.Double start = null;
		Point2D.Double prev = null;
		PathIterator i = this.getPathIterator(null);
		for(;!i.isDone(); i.next()) {
			int type = i.currentSegment(point);
			Point2D.Double p = new Point2D.Double(point[0], point[1]);
			switch(type) {
			case PathIterator.SEG_MOVETO:
				prev = p;
				start = prev;
				break;
			case PathIterator.SEG_LINETO:
				result.add(new Line2D.Double(prev, p));
				//System.out.println(prev.getX()+","+prev.getY() + " -- " + p.getX()+","+p.getY()); 
				prev = p;
				break;
			case PathIterator.SEG_CLOSE:
				if (p.distance(start) > mergeDistance) {
					result.add(new Line2D.Double(p, start));
				}
				break;
			default:
				assert(false);
			}
		}
		return result;
	}

	public String toString() {
		List<Point2D.Double> points = toPoints();
		StringBuffer ret = new StringBuffer();
		ret.append("AdvPoly: " + points.size() + " points: ");
		for(Point2D p : points) {
			ret.append("(" + p.getX() + ", " + p.getY() + ") ");
		}
		return ret.toString();
	}
	public String toRefString() {
		return new String("N: " + nref + " W:" + this.ref + " D:" + dirRef);
	}

	public List<Node> toNodeList() {
		List<Node> ret = new ArrayList<Node>();
		for (Point2D.Double p : toPoints()) {
			LatLon newCoor = Main.getProjection().eastNorth2latlon(new EastNorth(p.getX(), p.getY()));

			Node newNode = new Node(newCoor);
			ret.add(newNode);
		}
		return ret;
	}
	
	public Way toWay() {
		Way w = new Way();
		w.setNodes(toNodeList());
		return w;
	}
	
	public Rectangle2D getRotatedBBox(double angle) {
		double[] point = new double[6];
		Rectangle2D bound = null;
		
		// Rotated axes
		Point2D rotXVec = new Point2D.Double(Math.cos(angle), Math.sin(angle));
		Point2D rotYVec = new Point2D.Double(-Math.sin(angle), Math.cos(angle));
		
		PathIterator i = this.getPathIterator(null);
		for(;!i.isDone(); i.next()) {
			int type = i.currentSegment(point);
			Point2D.Double p = new Point2D.Double(point[0], point[1]);
			switch(type) {
			case PathIterator.SEG_MOVETO:
			case PathIterator.SEG_LINETO:
				double x = Vector.dotProduct(p, rotXVec);
				double y = Vector.dotProduct(p, rotYVec);
				if (bound == null) {
					bound = new Rectangle2D.Double(x,y,0,0);
				}
				else {
					bound.add(x, y);
				}
				break;
			case PathIterator.SEG_CLOSE:
				break;
			default:
				assert(false);
			}
		}
		return bound;		
	}
	
	public AdvArea rotate(double angle) {
		AdvArea res = new AdvArea(this, m_coordRef);
		res.transform(AffineTransform.getRotateInstance(angle));
		return res;
		
		/*List<Point2D.Double> points = new ArrayList<Point2D.Double>();
		for (Point2D.Double p : toPoints()) {
			double x = p.x*Math.cos(angle) - p.y*Math.sin(angle);
			double y = p.x*Math.sin(angle) + p.y*Math.cos(angle);
			points.add(new Point2D.Double(x,y));
		}
		return new AdvArea(points, m_coordRef);*/
	}
	
	public List<AdvArea> split(double ratio, double gap, boolean vertical) {
		List<AdvArea> result = new ArrayList<AdvArea>();

		Rectangle2D bbox = this.getBounds2D();
		bbox = new Rectangle2D.Double(bbox.getMinX()-1, bbox.getMinY()-1, bbox.getWidth()+2, bbox.getHeight()+2);
		Area a1;
		Area a2;
		if (vertical) {
			double x2 = bbox.getMinX() + bbox.getWidth()*ratio - gap; 
			a1 = new Area(new Rectangle2D.Double(
					bbox.getMinX(), bbox.getMinY(), bbox.getWidth()*ratio + gap, bbox.getHeight() ));
			a2 = new Area(new Rectangle2D.Double(
					x2, bbox.getMinY(), bbox.getWidth()*(1-ratio), bbox.getHeight() ));
		}
		else {
			double y2 = bbox.getMinY() + bbox.getHeight()*ratio - gap; 
			a1 = new Area(new Rectangle2D.Double(
					bbox.getMinX(), bbox.getMinY(), bbox.getWidth(), bbox.getHeight()*ratio + gap));
			a2 = new Area(new Rectangle2D.Double(
					bbox.getMinX(), y2, bbox.getWidth(), bbox.getHeight()*(1-ratio)));
		}
		AdvArea res1 = new AdvArea(this, m_coordRef);
		res1.subtract(a1);
		AdvArea res2 = new AdvArea(this, m_coordRef);
		res2.subtract(a2);
		result.addAll(res1.split());
		result.addAll(res2.split());
		return result;
	}
	
	public List<AdvArea> split() {
		double[] point = new double[6];
		List<Point2D.Double> newArea = new ArrayList<Point2D.Double>();
		List<AdvArea> result = new ArrayList<AdvArea>();
		
		Point2D.Double start = null;
		PathIterator i = this.getPathIterator(null);
		for(;!i.isDone(); i.next()) {
			int type = i.currentSegment(point); 
			Point2D.Double p = new Point2D.Double(point[0], point[1]);
			switch(type) {
			case PathIterator.SEG_MOVETO:
				start = p;
				assert newArea.isEmpty();
				newArea.add(p);
				break;
			case PathIterator.SEG_LINETO:
				if (p.distance(start) > mergeDistance && 
						p.distance(newArea.get(newArea.size()-1)) > mergeDistance) {
					newArea.add(p);
					//System.out.println("skipped point");
				}
				break;
			case PathIterator.SEG_CLOSE:
				AdvArea a = new AdvArea(newArea, m_coordRef);
				if (!a.isEmpty()) {
					result.add(a);
				}
				newArea.clear();
				break;
			default:
				assert(false);
			}
		}
		return result;
	}
	
	/**
	 * 
	 * @return false, if it intersects itself
	 */
	public boolean isSimple() {
		List<Line2D.Double> segments = toSegments();
		for(int i = 0; i < segments.size(); i++) {
			Line2D.Double l1 = segments.get(i);
			for(int j = i + 2; j < segments.size(); j++) {	// start with segment after next as next segment obviously intersects current
				if (i == 0 && j == segments.size() -1) // first and last segments intersect, too
					continue;
				if(l1.intersectsLine(segments.get(j)))
					return false;
			}
		}
		return true;
	}
	
	private AdvArea scaledArea(double amount) {
		List<Line2D.Double> segments = toSegments();
		List<Point2D.Double> points = toPoints();
		List<Point2D> newPoly = new ArrayList<Point2D>();
		//System.out.println(points.size());
		for (int i=0; i < points.size(); i++) {
			Point2D.Double p = points.get(i);
			Line2D.Double l1 = segments.get((i-1+points.size()) % points.size());
			Line2D.Double l2 = segments.get(i);
			Point2D n1 = normalVec(diff(l1.getP2(),l1.getP1()));
			Point2D n2 = normalVec(diff(l2.getP2(),l2.getP1()));
			Point2D dir = sum(n1,n2);
			if (length(dir) < 0.1*Math.abs(amount) ) {
				Point2D p2 = sum(p, times(n1, amount));
				Point2D p3 = sum(p, times(n2, amount));
				newPoly.add(p2);
				newPoly.add(p3);
			}
			else {
				double len = amount/(length(dir) * dotPNorm(n1, dir));
				Point2D offset = times(dir, len);
				Point2D p2 = sum(p, offset);
				newPoly.add(p2);
				//sanity checking:
				/*if (newPoly.size() > 1) {
					Point2D newSegment = diff(p2, newPoly.get(newPoly.size()-2));
					if (cosAngle(fromLine(l1), newSegment) < 0) {
						newPoly.remove(newPoly.size()-2);
					}
				}*/
			}
		}
		//System.out.println(newPoly.size());
		AdvArea a = new AdvArea(newPoly, m_coordRef);
		return a;
	}
	
	public void scale(double amount) {
		AdvArea a = scaledArea(amount);
		if (amount > 0) {
			this.add(a);
		}
		else {
			this.intersect(a);
		}
	}
	
	public boolean intersects(AdvArea other) {
		Area tmp = (Area) other.clone();
		tmp.intersect(this);
		
		return !tmp.isEmpty();
	}

	/**
	 * Next to functions only work for non-intersecting ones.
	 * 
	 * From: http://en.wikipedia.org/wiki/Polygon
	 * 
	 * @return area in mm^2
	 */
	public double area() {
		return area(true);
	}

	public double area(boolean absolute) {
		List<Line2D.Double> segments = toSegments();
		double sum = 0;
		for(Line2D l : segments) {
			sum += l.getX1()*l.getY2() - l.getX2()*l.getY1();
		}
		if(absolute)
			return Math.abs(0.5 * sum);
		return 0.5 * sum;
	}

	public double circumfence() {
		List<Line2D.Double> segments = toSegments();
		double sum = 0;
		for(Line2D l : segments) {
			sum += length(fromLine(l));
		}
		return sum;
	}
	
	public double sideRatio() {
		List<Line2D.Double> segments = toSegments();
		double A = 0;
		double U = 0;
		for(Line2D l : segments) {
			A += l.getX1()*l.getY2() - l.getX2()*l.getY1();
			U += length(fromLine(l));
		}
		A = Math.abs(0.5*A);
		double z = U*U/(4*A);
		if (z < 4)
			return 1;
		double ratio = z/2 -1 + 0.5*Math.sqrt(z*(z-4));
		
		//System.out.println("Ratio: " + ratio);
		return ratio;
	}

	public void cleanScale(double amount) {
		List<AdvArea> parts = this.scaledArea(amount).split();
		List<AdvArea> used_parts = new ArrayList<AdvArea>();
		if (amount >= 0) {
			used_parts = parts;
		}
		else {
			for (AdvArea a : parts) {
				double area1 = a.area();
				if (a.sideRatio() > 5)
					continue;
				a.intersect(this);
				if (area1 <= a.area() + epsilon ) {
					used_parts.add(a);
				}
			}
			this.reset();
		}
		for (AdvArea a : used_parts) {
			this.add(a);
		}
	}
	
	public boolean contains(AdvArea a) {
		for(Point2D p : a.toPoints()) {
			if(!this.contains(p))
				return false;
		}
		return true;
	}

	
	private static GeneralPath fromPoints(List<? extends Point2D> points) {
		if (points.isEmpty()) {
			return new GeneralPath(GeneralPath.WIND_NON_ZERO,0);
		}
		GeneralPath path = new GeneralPath(GeneralPath.WIND_NON_ZERO, points.size()+1);
		path.moveTo((float) points.get(0).getX(), (float) points.get(0).getY());
		for (int i=1; i < points.size(); i++) {
			Point2D p = points.get(i);
			path.lineTo((float) p.getX(), (float) p.getY());
		}
		path.closePath();
		return path;
	}
	
	private static GeneralPath fromNodeList(List<? extends Node> nodes) {
		GeneralPath path = new GeneralPath(GeneralPath.WIND_NON_ZERO, nodes.size()+1);
		EastNorth c = Main.getProjection().latlon2eastNorth(nodes.get(0).getCoor());
		path.moveTo((float) c.getX(), (float) c.getY());
		for (int i=1; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			c = Main.getProjection().latlon2eastNorth(n.getCoor());
			path.lineTo((float) c.getX(), (float) c.getY());
		}
		path.closePath();
		return path;
	}

}
