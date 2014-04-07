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

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class SettingsDialog extends JDialog {

	private JTextField civs = new JTextField("80");
	private JTextField ats = new JTextField("8");
	private JTextField fbs = new JTextField("8");
	private JTextField pfs = new JTextField("8");
	private JTextField refs = new JTextField("4");
	private JTextField acs = new JTextField("1");
	private JTextField fss = new JTextField("1");
	private JTextField pos = new JTextField("1");
	private JTextField fires = new JTextField("6");
	
	private JTextField minH = new JTextField("1");
	private JTextField maxH = new JTextField("20");

	private JTextField scale = new JTextField("1.0");
	
	public SettingsDialog(Frame owner) {
		super(owner, "Rescue settings");
		
		setModal(true);

        JPanel scenarioSettings = new JPanel(new GridLayout(9, 2));
//        add(scenarioSettings, BorderLayout.NORTH);
        scenarioSettings.add(new JLabel("civilians"));
        scenarioSettings.add(civs);
        scenarioSettings.add(new JLabel("ambulances"));
        scenarioSettings.add(ats);
        scenarioSettings.add(new JLabel("fire brigades"));
        scenarioSettings.add(fbs);
        scenarioSettings.add(new JLabel("police forces"));
        scenarioSettings.add(pfs);
        scenarioSettings.add(new JLabel("refuges"));
        scenarioSettings.add(refs);
        scenarioSettings.add(new JLabel("ambulance centres"));
        scenarioSettings.add(acs);
        scenarioSettings.add(new JLabel("fire stations"));
        scenarioSettings.add(fss);
        scenarioSettings.add(new JLabel("police offices"));
        scenarioSettings.add(pos);
        scenarioSettings.add(new JLabel("fires"));
        scenarioSettings.add(fires);
        
        JPanel mapSettings = new JPanel(new GridLayout(3, 2));
        add(mapSettings, BorderLayout.CENTER);
        mapSettings.add(new JLabel("min floors"));
        mapSettings.add(minH);
        mapSettings.add(new JLabel("max floors"));
        mapSettings.add(maxH);
        mapSettings.add(new JLabel("export scale"));
        mapSettings.add(scale);

        JButton okBtn = new JButton("Ok");
        okBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setVisible(false);
			}
        });
        add(okBtn, BorderLayout.SOUTH);
        pack();
	}

	private int parseInt(JTextField field, int def) {
		int res = def;
		try {
			res = Integer.parseInt(field.getText());
		} 
		catch (NumberFormatException e) {}
		return res;
	}
	
	public int civCount() {
		return parseInt(civs, 80);
	}

	public int atCount() {
		return parseInt(ats, 8);
	}

	public int fbCount() {
		return parseInt(fbs, 8);
	}

	public int pfCount() {
		return parseInt(pfs, 8);
	}

	public int refugeCount() {
		return parseInt(refs, 4);
	}

	public int fireCount() {
		return parseInt(fires, 6);
	}

	public int acCount() {
		return parseInt(acs, 1);
	}

	public int fsCount() {
		return parseInt(fss, 1);
	}

	public int poCount() {
		return parseInt(pos, 1);
	}

	public int minFloors() {
		return parseInt(minH, 1);
	}
	public int maxFloors() {
		return parseInt(maxH, 1);
	}

	public double scale() {
		try {
			return Double.parseDouble(scale.getText());
		} 
		catch (NumberFormatException e) {
			return 1.0;
		}
	}

}
