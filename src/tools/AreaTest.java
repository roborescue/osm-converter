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

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class AreaTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		List<Point2D> pts = new ArrayList<Point2D>();
		pts.add(new Point2D.Double(0, 0));
		pts.add(new Point2D.Double(1, 0));
		pts.add(new Point2D.Double(1, 1));
		pts.add(new Point2D.Double(0, 1));
		AdvArea a = new AdvArea(pts, null);
		
		List<Point2D> ptst = new ArrayList<Point2D>();
		ptst.add(new Point2D.Double(0.95, 0.95));
		ptst.add(new Point2D.Double(0.7, 0.95));
		ptst.add(new Point2D.Double(0.95, 0.7));
		AdvArea at = new AdvArea(ptst, null);		

		List<Point2D> pts2 = new ArrayList<Point2D>();
		pts2.add(new Point2D.Double(0, 1.5));
		pts2.add(new Point2D.Double(1.5, 0));
		pts2.add(new Point2D.Double(1.5, 0.3));
		pts2.add(new Point2D.Double(0.3, 1.5));
		AdvArea a2 = new AdvArea(pts2, null);
		
		a.subtract(a2);
		a.subtract(at);
		
		List<AdvArea> sp1 = a.split();
		System.out.println(sp1);
		
		List<AdvArea> all = new ArrayList<AdvArea>();
		for(AdvArea asp1 : sp1) {
			asp1.subtract(at);
			List<AdvArea> ll = asp1.split();
			all.addAll(ll);
		}
		
		//List<AdvArea> l = a.split();
		System.out.println(all);
			
	}

}
