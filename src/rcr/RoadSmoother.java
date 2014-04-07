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

import static rcr.Vector.dotPNorm;
import static rcr.Vector.diff;
import static rcr.Vector.sum;
import static rcr.Vector.times;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

public class RoadSmoother {
	private RCRDataSet m_data;

	public RoadSmoother(RCRDataSet data) {
		m_data = data;
	}
	
	static final double SMOOTH_FACTOR = 0.5; 
	public Collection<Node> smoothRoads(Node node) {
		Set<Node> next = nextNodes(node);
		Set<Node> nextnext = nextNodes(next);
		LatLon prevDir = null;
		
		Set<Node> visited = new HashSet<Node>();
		
		while (!nextnext.isEmpty()) {
			if (next.size() > 1)
				return next;
			Node n2 = next.iterator().next();
			if (visited.contains(n2))
				return new ArrayList<Node>();
			visited.add(n2);
			
			Node n3 = null;
			double eval = -10;
			if (nextnext.size() == 1) {
				n3 = nextnext.iterator().next();
				eval = evaluateSmoothingSolution(prevDir, node, n2, n3);
			}
			else {
				for (Node n3cand : nextnext) {
					double thisEval = evaluateSmoothingSolution(prevDir, node, n2, n3cand);
					System.out.println("eval: " + thisEval);
					if (thisEval> eval) {
						eval = thisEval;
						n3 = n3cand;
					}
				}
			}
			
			if (eval >= 0.2) {
				LatLon p = Vector.projection(node.getCoor(), n3.getCoor(), n2.getCoor());
				double x = n2.getCoor().lon() + (p.lon() - n2.getCoor().lon()) * SMOOTH_FACTOR;
				double y = n2.getCoor().lat() + (p.lat() - n2.getCoor().lat()) * SMOOTH_FACTOR;
				n2.setCoor(new LatLon(y,x));
		        //n2.eastNorth = Main.proj.latlon2eastNorth(n2.coor);
			}
			else if (eval > -9) {
				//double ca = Vector.cosAngle(prevDir, Vector.diff(node.coor, n3.coor));
				System.out.printf("Dont smooth %d - %d - %d\n", node.getId(), n2.getId(), n3.getId());
				System.out.println("Eval: " + eval);
			}
			else
				System.err.println("Should not happen");
			
	        prevDir = Vector.diff(node.getCoor(), n3.getCoor());
	        node = n2;
	        next.clear();
	        next.add(n3);
	        nextnext = nextNodes(n3);
		}
		return new ArrayList<Node>();
	}

	public static final double ANGLE_SMOOTH_THRESHOLD = 0;
	private double evaluateSmoothingSolution(LatLon prevDir, Node nprev, Node n, Node nnext) {
		if (prevDir == null)
			prevDir = diff(nprev.getCoor(), n.getCoor());

		LatLon nextDir = null;
		Set<Node> onemore = nextNodes(nnext);
		for (Node n4 : onemore) {
			LatLon dir = diff(nnext.getCoor(), n4.getCoor());
			if (nextDir == null || dotPNorm(prevDir, dir) > dotPNorm(prevDir, nextDir))
				nextDir = dir;
		}
		if (nextDir == null)
			nextDir = diff(n.getCoor(), nnext.getCoor());
		
		LatLon avgInOutDir = Vector.asLatLon(times(sum(Vector.asPoint(prevDir), Vector.asPoint(nextDir)) , 0.5));
		
		double inOutdiff = dotPNorm(prevDir, nextDir);
		double smoothDiff = dotPNorm(avgInOutDir, diff(nprev.getCoor(), nnext.getCoor()));
		return (inOutdiff+smoothDiff)/2;
		//return smoothDiff;

		/*double oldAngle_prev =  prevDir == null ? 0 : cosAngle(prevDir, diff(nprev.coor, n.coor));
		double oldAngle_n    =  cosAngle(diff(nprev.coor, n.coor), diff(n.coor, nnext.coor));;
		double oldAngle_next =  nextDir == null ? 0 : Vector.cosAngle(Vector.diff(n.coor, nnext.coor), nextDir);
		
		if ((oldAngle_prev+oldAngle_next+oldAngle_n) /3 < ANGLE_SMOOTH_THRESHOLD )
			return -1;
		
		double newAngle_prev =  prevDir == null ? 0 : Vector.cosAngle(prevDir, Vector.diff(nprev.coor, nnext.coor));
		double newAngle_next =  nextDir == null ? 0 : Vector.cosAngle(Vector.diff(nprev.coor, nnext.coor), nextDir);
		
		return (newAngle_prev+newAngle_next - (oldAngle_prev+oldAngle_next))/2;*/ 
	}

	
	private Set<Node> nextNodes(Collection<Node> nodes) {
		HashSet<Node> result = new HashSet<Node>();
		for (Node n : nodes)
			result.addAll(nextNodes(n));
		return result;
	}
	
	private Set<Node> nextNodes(Node n) {
		if (n == null || m_data.getWaysAtNode(n) == null)
			return new HashSet<Node>();

		HashSet<Node> result = new HashSet<Node>();
		for (Way w : m_data.getWaysAtNode(n)) {
			if (n != w.lastNode()) {
				Node prev = null;
				for (Node n2 : w.getNodes()) {
					if (prev == n)
						result.add(n2);
					prev = n2;
				}
			}
		}
		return result;
	}
	

}
