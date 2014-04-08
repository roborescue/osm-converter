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

/* Copyright (c) 2008, Henrik Niehaus
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of the project nor the names of its 
 *    contributors may be used to endorse or promote products derived from this 
 *    software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package rcr;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataSetListenerAdapter;
import org.openstreetmap.josm.gui.MapView.LayerChangeListener;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.Shortcut;

import rcr.export.GMLExporter;

public class RCRDialog extends ToggleDialog implements DataSetListenerAdapter.Listener, LayerChangeListener, 
        ActionListener, MouseListener, SelectionChangedListener {

    private RCRPlugin rcrPlugin;

    private JButton createRCRLayer = new JButton("make rescue map");
    private JButton createEntrances = new JButton("create entrances");
    private JButton buildings = new JButton("make buildings");
    private JButton createGeometry = new JButton("create geometry");
    private JButton saveMap = new JButton("save map");
    private JToggleButton showErrors = new JToggleButton("show errors");
//    private JTextField scaleAmount =  new JTextField("4");
    
    private JButton makeBuilding = new JButton("B");
    private JButton makeEntrance = new JButton("+E");
    private JButton removeEntrance = new JButton("-E");
    private JButton fitRoads = new JButton(">R<");
    private JToggleButton refugeBtn = new JToggleButton("R");
    private JToggleButton fireBtn = new JToggleButton("F");
    
    private List<JComponent> mapSpecificControls = new ArrayList<JComponent>();
    
    private DataSetListenerAdapter listenerAdapter;
    
    public RCRDialog(final RCRPlugin plugin) {
        super(tr("Rescue Simulation Converter"), "icon_rescue",
                tr("OPen the RoboCup-Rescue map converter"), Shortcut.registerShortcut(
                        "view:rcr", tr("Toggle: {0}", tr("Open RCR converter")), KeyEvent.VK_R,
                        Shortcut.SHIFT), 150);

        rcrPlugin = plugin;
        listenerAdapter = new DataSetListenerAdapter(this);


        // create dialog buttons
        GridLayout layout = new GridLayout(3, 2);
        JPanel toolPanel = new JPanel();
        JPanel buttonPanel = new JPanel(layout);
        add(toolPanel, BorderLayout.NORTH);
        add(buttonPanel, BorderLayout.SOUTH);

        createRCRLayer.setEnabled(true);
        createRCRLayer.addActionListener(this);
        
        mapSpecificControls.add(saveMap);
        saveMap.addActionListener(this);
        mapSpecificControls.add(createEntrances);
        createEntrances.addActionListener(this);
        mapSpecificControls.add(createGeometry);
        createGeometry.addActionListener(this);
        
        mapSpecificControls.add(fitRoads);
        fitRoads.addActionListener(this);
        mapSpecificControls.add(buildings);
        buildings.addActionListener(this);

        mapSpecificControls.add(showErrors);
        showErrors.addActionListener(this);
        
        mapSpecificControls.add(makeBuilding);
        mapSpecificControls.add(makeEntrance);
        mapSpecificControls.add(removeEntrance);
        mapSpecificControls.add(refugeBtn);
        mapSpecificControls.add(fireBtn);
        makeBuilding.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				makeBuilding();
			}});
        makeEntrance.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				makeEntrance();
			}});
        removeEntrance.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				removeEntrance();
			}});
        refugeBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				toggleRefuge();
			}});
        fireBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				toggleFire();
			}});
        
        toolPanel.add(makeBuilding);
        toolPanel.add(makeEntrance);
        toolPanel.add(removeEntrance);
        toolPanel.add(fitRoads);
//        toolPanel.add(refugeBtn);
//        toolPanel.add(fireBtn);
                
        buttonPanel.add(createRCRLayer);
        buttonPanel.add(createEntrances);
        buttonPanel.add(createGeometry);
        buttonPanel.add(buildings);
        buttonPanel.add(showErrors);
        buttonPanel.add(saveMap);
        
    	for (JComponent com : mapSpecificControls) {
    		com.setEnabled(false);
    	}
        
    }

    public synchronized void update() {
    	if (getRescueData() != null) {
    		if (getRescueData().canCreateEntrances()) {
    			createEntrances.setText("create entrances");
    		}
    		else {
    			createEntrances.setText("regenerate entrances");
    		}
    	}
    }

    public void activeLayerChange(Layer oldLayer, Layer newLayer) {
    	if (oldLayer instanceof RCRMapLayer) {
    		((RCRMapLayer) oldLayer).data.removeDataSetListener(listenerAdapter); 
    		//FIXME: not needed, if we add the listener only once
    		DataSet.removeSelectionListener(this); 
    	}
        if (newLayer instanceof RCRMapLayer) {
        	for (JComponent com : mapSpecificControls) {
        		com.setEnabled(true);
        	}
    		((RCRMapLayer) newLayer).data.addDataSetListener(listenerAdapter); 
    		DataSet.addSelectionListener(this); 
        	createRCRLayer.setEnabled(false);
        }
        else {
        	for (JComponent com : mapSpecificControls) {
        		com.setEnabled(false);
        	}
        	createRCRLayer.setEnabled(true);
        }
        update();
    }

    public void layerAdded(Layer newLayer) {
        if (newLayer instanceof RCRMapLayer) {
        	for (JComponent com : mapSpecificControls) {
        		com.setEnabled(true);
        	}
            Main.map.mapView.moveLayer(newLayer, 0);
        }
        else {
        	for (JComponent com : mapSpecificControls) {
        		com.setEnabled(false);
        	}
        }
    }

    public void layerRemoved(Layer oldLayer) {
    }

    public void processDatasetEvent(AbstractDatasetChangedEvent event) { 
        update();
    }

    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {

        }
    }

    public void mousePressed(MouseEvent e) {

    }

    public void mouseReleased(MouseEvent e) {

    }


    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }
    
    
    private RCRDataSet getRescueData() {
    	if (!(Main.main.getEditLayer() instanceof RCRMapLayer)) {
    		return null;
    	}
		return ((RCRMapLayer) Main.main.getEditLayer()).rcrData;
    }
    
	public void actionPerformed(ActionEvent e) {
		if (!Main.main.hasEditLayer())
			return;
		
		if (e.getSource() == createRCRLayer) {
			RCRMapLayer mapLayer = RCRMapLayer.fromOsmData(Main.main.getEditLayer().data);
			if (mapLayer != null) {
				Main.main.addLayer(mapLayer);
				setShowErrors();
			}
		}
		
		RCRDataSet data = getRescueData();
		if (data == null) {
			return;
		}

		
		if (e.getSource() == createEntrances) {
			data.updateElements();
			System.out.println("can create:" + data.canCreateEntrances());
			if (data.canCreateEntrances()) {
				data.realizeEntrances();
			}
			else {
				data.generateEntrances();
			}
			update();
			Main.map.mapView.repaint();
		}
		
		/*if (e.getSource() == buildings) {
			RCRDataSet data = (RCRDataSet) Main.main.editLayer().data;
			Collection<OsmPrimitive> selWays = data.getSelectedWays();
			if (selWays.size() > 0){
				BuildingGenerator bg = new BuildingGenerator(data, null);
				double val = Double.parseDouble(scaleAmount.getText());
				Way w = (Way) selWays.iterator().next();
				AdvArea a = new AdvArea(w.nodes);
				a.cleanScale(val);
				List<AdvArea> areas = a.split();
				for (AdvArea a2 : areas) {
					data.addPrimitive(bg.makeBuildingWay(a2.toNodeList()));
				}
				//data.makeBuildingWay(outline))
				//data.simplifyWay(w);
				Main.main.editLayer().setModified(true);
				Main.main.editLayer().fireDataChange();
				Main.map.mapView.repaint();
			}
		}*/
		
		if (e.getSource() == buildings)  {
			fillBuildingBlocks();
			Main.map.mapView.repaint();
		}
		
		if (e.getSource() == fitRoads) {
			fitRoads();
			Main.map.mapView.repaint();
		}
		if (e.getSource() == createGeometry) {
			createGeometry();
		}
		
		if (e.getSource() == showErrors) {
			setShowErrors();
		}
		
		if (e.getSource() == saveMap) {
			saveGML();
		}
	}
	
	private void createGeometry() {
		RCRDataSet data = getRescueData();
		GMLExporter export = new GMLExporter(data);
		for (Way w : data.getData().getWays()) {
			if (!export.getSegments(w).isEmpty()) {
				data.gmlSegments.put(w, export.getSegments(w));
			}
		}
		Main.map.mapView.repaint();		
	}
	
	private void saveGML() {
		RCRDataSet data = getRescueData();
		GMLExporter export = new GMLExporter(data);
				
		for (Way w : data.getData().getWays()) {
			if (!export.getSegments(w).isEmpty()) {
				data.gmlSegments.put(w, export.getSegments(w));
			}
		}
				
		JFileChooser fc = new JFileChooser(new File("/home_local/dornroot/teaching/mas08/rescue/rescue-0.50.0/maps/"));
		fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if(fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			return;
		File file = fc.getSelectedFile();
		System.out.println("SAVE TO: " + file.getPath());
		export.exportMap(file);		
	}
	
	private Collection<Way> getSelectedWaysOrAll() {
		RCRDataSet data = getRescueData();
		Collection<Way> selWays = data.getData().getSelectedWays();
		if (selWays.isEmpty()) {
			return data.getData().getWays();
		}
		return selWays;
	}
	
	
	private void fitRoads() {
		RCRDataSet data = getRescueData();
		RoadGenerator rg = new RoadGenerator(data, null);
		for (Way w : data.getData().getSelectedWays()) {
			if (w.hasTag("rcr:type", "road") && !w.hasAreaTags()) {
				double maxWidth = rg.getMaxRoadWidth(w, 0.5);
				if (maxWidth * 1000 >= Constants.MINIMUM_ROAD_WIDTH) {
					w.put("rcr:width", Integer.toString((int) (maxWidth * 1000)));
				}
			}
		}
		
	}
	
	private void fillBuildingBlocks() {
		getRescueData().fillBuildingBlocks(getSelectedWaysOrAll());
		Main.map.mapView.repaint();
		
	}
	
	private void setShowErrors() {
		RCRDataSet data = getRescueData();
		if (data != null) {
			RCRMapLayer map = (RCRMapLayer) Main.main.getEditLayer();
			map.setShowErrors(showErrors.isSelected());
			data.setRefreshErrors(showErrors.isSelected());
			if (map.showErrors()) {
				data.updateElements();
				data.checkConnectivity();
				data.checkOverlaps();
			}
			Main.map.mapView.repaint();
		}
	}
	
	private void makeBuilding() {
		RCRDataSet data = ((RCRMapLayer) Main.main.getEditLayer()).rcrData;
		
		Collection<Way> selWays = data.getData().getSelectedWays();
		for (OsmPrimitive o : selWays) {
			Way w = (Way) o;
			if (w.get("rcr:type") == null)
				data.makeBuilding(w, false);
		}
		Main.map.mapView.repaint();
	}
	
	private void makeEntrance() {
		RCRDataSet data = ((RCRMapLayer) Main.main.getEditLayer()).rcrData;
		data.updateElements();
		List<Way> buildings = new ArrayList<>();
		List<Way> roads = new ArrayList<>();
		List<Node> nodes = new ArrayList<Node>();
		for (OsmPrimitive o : data.getData().getSelected()) {
			if (o.hasTag("rcr:type", "building"))
				buildings.add((Way) o);
			else if (o.hasTag("rcr:type", "road"))
				roads.add((Way) o);
			else if (o instanceof Node)
				nodes.add((Node) o);
		}
		EntranceGenerator eg = new EntranceGenerator(data);
		if (nodes.size() == 2) {
			eg.createEntranceWay(nodes.get(0), nodes.get(1));
		}
		else if (buildings.size() == 2 && roads.isEmpty()) {
			eg.makeEntrance(buildings.get(0), buildings);
		}
		else if (!buildings.isEmpty() && !roads.isEmpty()) {
			for (Way b : buildings) { 
				eg.makeEntrance(b, roads);
			}
		}
		else if (!buildings.isEmpty()) {
			for (Way b : buildings) { 
				eg.makeEntrance(b, false);
			}
		}
		Main.map.mapView.repaint();
	}

	private void removeEntrance() {
		RCRDataSet data = ((RCRMapLayer) Main.main.getEditLayer()).rcrData;
		data.updateElements();
		
		for (OsmPrimitive osm : data.getData().getSelected()) {
			if (osm instanceof Node) {
				// Remove entrances using this node
				for (OsmPrimitive w : ((Node) osm).getReferrers()) {
					if (w instanceof Way) {
						data.removeEntrance((Way) w);
					}
				}
			}
			else if (osm instanceof Way && osm.hasTag("rcr:type", "building")) {
				// Remove entrances from this building
				for (Node n : ((Way) osm).getNodes()) {
					for (OsmPrimitive w : n.getReferrers()) {
						if (w instanceof Way) {
							data.removeEntrance((Way) w);
						}
					}
				}
			}
			else if (osm instanceof Way) {
				// Remove this way if it is an entrance
				data.removeEntrance((Way) osm);
			}
		}
		
		Main.map.mapView.repaint();
	}

	private void toggleRefuge() {
		RCRDataSet data = ((RCRMapLayer) Main.main.getEditLayer()).rcrData;
		boolean refuge = refugeBtn.isSelected();
		for (OsmPrimitive o : data.getData().getSelectedNodes()) {
			if (o.hasTag("rcr:type", "building")) {
				if (refuge)
					o.put("rcr:building_type", "refuge");
				else
					o.remove("rcr:building_type");
			}
		}
		Main.map.mapView.repaint();
	}
	
	private void toggleFire() {
		RCRDataSet data = ((RCRMapLayer) Main.main.getEditLayer()).rcrData;
		boolean fire = fireBtn.isSelected();
		for (OsmPrimitive o : data.getData().getSelectedNodes()) {
			if (o.hasTag("rcr:type", "building")) {
				if (fire)
					o.put("rcr:fire", "true");
				else
					o.remove("rcr:fire");
			}
		}
		Main.map.mapView.repaint();
	}

	public void selectionChanged(Collection<? extends OsmPrimitive> newSelection) {
		int refuges = 0;
		int fires = 0;
		int total = 0;
		for (OsmPrimitive o : newSelection) {
			if (o instanceof Node && o.hasTag("rcr:type", "building")) {
				if (o.hasTag( "rcr:building_type", "refuge"))
					refuges++;
				if (o.isKeyTrue("rcr:fire"))
					fires++;
				total++;
			}
		}
		refugeBtn.setSelected(total >0 && refuges==total);
		fireBtn.setSelected(total >0 && fires==total);
		refugeBtn.setEnabled(total > 0);
		fireBtn.setEnabled(total > 0);

			
	}

}
