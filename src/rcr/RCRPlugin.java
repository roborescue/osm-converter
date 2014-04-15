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

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.DiskAccessAction;
import org.openstreetmap.josm.actions.UploadAction;
import org.openstreetmap.josm.actions.upload.UploadHook;
import org.openstreetmap.josm.data.APIDataSet;
import org.openstreetmap.josm.data.osm.visitor.paint.MapRendererFactory;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

import rcr.export.RCRLegacyMap;

public class RCRPlugin extends Plugin {

	private class CreateAction extends AbstractAction {
		public CreateAction() {
			super("Create rescue map");
		}

		public void actionPerformed(ActionEvent e) {
			RCRMapLayer rcrLayer = RCRMapLayer.fromOsmData(Main.main.getEditLayer().data);
			if (rcrLayer != null)
				Main.main.addLayer(rcrLayer);
		}
	}

	private class ImportAction extends AbstractAction {
		public ImportAction() {
			super("Import legacy rescue map");
		}

		public void actionPerformed(ActionEvent e) {
			JFileChooser fc = new JFileChooser(new File("/home/mog/rescue/rescue-0.50.0/maps/"));
			
			//JFileChooser fc = new JFileChooser();	TODO: use this
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if(fc.showOpenDialog(JOptionPane.getFrameForComponent(dialog)) != JFileChooser.APPROVE_OPTION)
				return;
			File file = fc.getSelectedFile();
			RCRLegacyMap map = new RCRLegacyMap(file.getPath());
			if (map.isValid()) {
				RCRMapLayer rcrLayer = RCRMapLayer.fromRCRMap(map);
				if (rcrLayer != null) 
					Main.main.addLayer(rcrLayer);
			}
			
		}
	}

	
	public class LoadAction extends DiskAccessAction {

	    public LoadAction() {
	        super("Load rescue map", null, "", null);
	    }

	    public void actionPerformed(ActionEvent e) {
	        JFileChooser fc = createAndOpenFileChooser(true, true, null);
	        if (fc == null)
	            return;
	        File[] files = fc.getSelectedFiles();
	        for (int i = files.length; i > 0; --i)
	            openFile(files[i-1]);
	    }

	    /**
	     * Open the given file.
	     */
	    public void openFile(File file) { 
	        try {
	            System.out.println("Open file: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
	            RCRImporter importer = new RCRImporter();
	            if (importer.acceptFile(file))
	            	importer.importData(file, NullProgressMonitor.INSTANCE);
	        } catch (IOException x) {
	            x.printStackTrace();
	            JOptionPane.showMessageDialog(Main.parent, tr("Could not read \"{0}\"", file.getName()) + "\n"
	                    + x.getMessage());
	        } catch (IllegalDataException x) {
	            x.printStackTrace();
	            JOptionPane.showMessageDialog(Main.parent, tr("Could not read \"{0}\"", file.getName()) + "\n"
	                    + x.getMessage());
	        }
	    }

	}

	private class SettingsAction extends AbstractAction {
		public SettingsAction() {
			super("Settings");
		}

		public void actionPerformed(ActionEvent e) {
			RCRPlugin.settings.loadAndShow();
		}
	}
	
	private static class NoUploadHook implements UploadHook {

		public boolean checkUpload(APIDataSet apiDataSet) {
            JOptionPane.showMessageDialog(Main.parent,"You can't upload Rescue maps to OpenStreetMap.");
			return false;
		}
		
	}
	
	public static boolean active = false;

	private JMenu menu;

	private RCRDialog dialog;
	public static SettingsDialog settings;

	private JMenuItem createMenu = new JMenuItem(new CreateAction());
	private JMenuItem loadMenu = new JMenuItem(new LoadAction());
	private JMenuItem importMenu = new JMenuItem(new ImportAction());
	private JMenuItem settingsMenu = new JMenuItem(new SettingsAction());

	private UploadHook noUpload = new NoUploadHook();
	
	public RCRPlugin(PluginInformation info) {
		super(info);
		dialog = new RCRDialog(this);
		MapView.addLayerChangeListener(dialog);
		settings = new SettingsDialog((JFrame) Main.parent);
		
		JMenuBar mainMenu = Main.main.menu;
		menu = new JMenu(tr("Rescue"));
		mainMenu.add(menu);
		menu.setVisible(true);

		menu.add(createMenu);
		createMenu.setVisible(true);
		menu.add(loadMenu);
		loadMenu.setVisible(true);
		menu.add(importMenu);
		importMenu.setVisible(true);
		menu.add(settingsMenu);
		settingsMenu.setVisible(true);
		
		MapRendererFactory.getInstance().register(
                RCRPainter.class,
                "Rescue Map Renderer",
                "Renders a RCR map as simple wire frame.");
		
	}

	@Override
	public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
		if (oldFrame == null && newFrame != null) { // map frame added
			// add the dialog
			newFrame.addToggleDialog(dialog);

			createMenu.setVisible(true);
			UploadAction.registerUploadHook(noUpload);

			// add a listener to the plugin toggle button
			/*final JToggleButton toggle = (JToggleButton) dialog.getToggleAction().;
			active = dialog.isDialogShowing();
			dialog.getToggleAction().addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					active = toggle.isSelected();
					if (toggle.isSelected()) {
				        // repaint view, so that changes get visible
				        Main.map.mapView.repaint();
					}
				}
			});*/
		} else if (oldFrame != null && newFrame == null) { // map frame removed
			 // disable
			 createMenu.setVisible(false);
		}
	}

	// @Override
	// public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
	// if (oldFrame != null && newFrame == null) {
	// // disable
	// addGridMenu.setVisible(false);
	// if (edit.getMenuComponentCount() == 1)
	// edit.setVisible(false);
	// } else if (oldFrame == null && newFrame != null) {
	// // enable
	// addGridMenu.setVisible(true);
	// if (edit.getMenuComponentCount() == 1)
	// edit.setVisible(true);
	// }
	// }

}
