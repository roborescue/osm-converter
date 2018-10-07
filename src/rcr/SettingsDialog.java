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

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.openstreetmap.josm.Main;

public class SettingsDialog extends JDialog {

	private static String prefix = "rcr-converter.";

	private JTextField civs = new JTextField("80");
	private JTextField ats = new JTextField("8");
	private JTextField fbs = new JTextField("8");
	private JTextField pfs = new JTextField("8");
	private JTextField refs = new JTextField("4");
	private JTextField acs = new JTextField("1");
	private JTextField fss = new JTextField("1");
	private JTextField pos = new JTextField("1");
	private JTextField fires = new JTextField("6");

	private JTextField entranceWidth = new JTextField(Integer.toString(Constants.DEFAULT_ENTRANCE_WIDTH));
	private JTextField minEntranceLength = new JTextField(Integer.toString((int) (1000 * Constants.MIN_ENTRANCE_LENGTH)));
	private JTextField maxEntranceLength = new JTextField(Integer.toString((int) (1000 * Constants.MAX_ENTRANCE_LENGTH)));

	private JTextField minRoadWidth = new JTextField(Integer.toString(Constants.MINIMUM_ROAD_WIDTH));

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

        JPanel entranceSettings = new JPanel(new GridLayout(3,2));
        entranceSettings.setBorder(BorderFactory.createTitledBorder("Entrances"));
        add(entranceSettings, BorderLayout.NORTH);
        entranceSettings.add(new JLabel("entrance width"));
        entranceSettings.add(entranceWidth);
        entranceSettings.add(new JLabel("min. entrance length"));
        entranceSettings.add(minEntranceLength);
        entranceSettings.add(new JLabel("max. entrance length"));
        entranceSettings.add(maxEntranceLength);

        JPanel mapSettings = new JPanel(new GridLayout(4, 2));
        add(mapSettings, BorderLayout.CENTER);
        mapSettings.add(new JLabel("min. road width"));
        mapSettings.add(minRoadWidth);
        mapSettings.add(new JLabel("min. floors"));
        mapSettings.add(minH);
        mapSettings.add(new JLabel("max. floors"));
        mapSettings.add(maxH);
        mapSettings.add(new JLabel("export scale"));
        mapSettings.add(scale);

        JPanel buttons = new JPanel(new GridLayout(1, 2));
        JButton okBtn = new JButton("Ok");
        okBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				saveSettings();
				setVisible(false);
			}
        });
        buttons.add(okBtn);
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				loadSettings();
				setVisible(false);
			}
        });
        buttons.add(cancelBtn);
        add(buttons, BorderLayout.SOUTH);
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

	private void loadSettings() {
		loadInt("entranceWidth", entranceWidth, Constants.DEFAULT_ENTRANCE_WIDTH);
		loadDouble("minEntranceLength", minEntranceLength, Constants.MIN_ENTRANCE_LENGTH);
		loadDouble("maxEntranceLength", maxEntranceLength, Constants.MAX_ENTRANCE_LENGTH);

		loadInt("minRoadWidth", minRoadWidth, Constants.MINIMUM_ROAD_WIDTH);
		loadInt("minFloors", minH, 1);
		loadInt("maxFloors", maxH, 20);
		loadDouble("exportScale", scale, 1.0);
	}

	private void saveSettings() {
		trySaveInt("entranceWidth", entranceWidth);
		trySaveDouble("minEntranceLength", minEntranceLength);
		trySaveDouble("maxEntranceLength", maxEntranceLength);

		trySaveInt("minRoadWidth", minRoadWidth);
		trySaveInt("minFloors", minH);
		trySaveInt("maxFloors", maxH);
		trySaveDouble("exportScale", scale);
	}

	private void loadInt(String subkey, JTextField text, int def) {
    int value = (int) Main.pref.getDouble(prefix+subkey, def);
		text.setText(Integer.toString(value));
	}

	private void loadDouble(String subkey, JTextField text, double def) {
		double value = Main.pref.getDouble(prefix+subkey, def);
		text.setText(Double.toString(value));
	}

	private void trySaveInt(String subkey, JTextField text) {
		try {
      //int value = Integer.parseInt(text.getText());
      double value = Double.parseDouble( text.getText() );
      //Main.pref.putInt(prefix+subkey, value);
      Main.pref.putDouble(prefix+subkey, value);
		}
		catch (NumberFormatException e) {
			// do nothing
		}
	}

	private void trySaveDouble(String subkey, JTextField text) {
		try {
			double value = Double.parseDouble(text.getText());
			Main.pref.putDouble(prefix+subkey, value);
		}
		catch (NumberFormatException e) {
			// do nothing
		}
	}

	public void loadAndShow() {
		loadSettings();
		setVisible(true);
	}

	public int getEntranceWidth() {
		return parseInt(entranceWidth, Constants.DEFAULT_ENTRANCE_WIDTH);
	}

	public double getMaxEntranceLength() {
		int lengthMM = parseInt(maxEntranceLength, (int) (1000 * Constants.MAX_ENTRANCE_LENGTH));
		return (double) lengthMM / 1000;
	}

	public double getMinEntranceLength() {
		int lengthMM = parseInt(minEntranceLength, (int) (1000 * Constants.MIN_ENTRANCE_LENGTH));
		return (double) lengthMM / 1000;
	}

	public int getMinRoadWidth() {
		return parseInt(minRoadWidth, Constants.MINIMUM_ROAD_WIDTH);
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
