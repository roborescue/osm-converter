/*
 * Copyright (c) 2009, Research Group on the Foundations of Artificial
 * Intelligence, Department of Computer Science, Albert-Ludwigs University
 * Freiburg. All rights reserved. Redistribution and use in source and binary
 * forms, with or without modification, are permitted provided that the
 * following conditions are met: Redistributions of source code must retain the
 * above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution. The name of the author
 * may not be used to endorse or promote products derived from this software
 * without specific prior written permission. THIS SOFTWARE IS PROVIDED BY THE
 * AUTHOR "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package rcr;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.io.File;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.RenameLayerAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import rcr.export.RCRLegacyMap;

/**
 * This is a layer that draws a grid
 */
public class RCRMapLayer extends OsmDataLayer {

  RCRDataSet          rcrData;

  private static Icon icon       = new ImageIcon( Toolkit.getDefaultToolkit()
      .createImage( RCRPlugin.class.getResource( "/images/rescue.png" ) ) );

  private boolean     showErrors = false;


  public RCRMapLayer( RCRDataSet _rcrData ) {
    super( _rcrData.getData(), "Rescue Map", null );
    rcrData = _rcrData;
  }


  public RCRMapLayer( RCRDataSet data, String name, File file ) {
    super( data.getData(), name, file );
    rcrData = data;
  }


  public void setShowErrors( boolean showErrors ) {
    this.showErrors = showErrors;
  }


  public boolean showErrors() {
    return showErrors;
  }


  @Override
  public boolean requiresUploadToServer() {
    return false;
  }


  public static RCRMapLayer fromOsmData( DataSet data ) {
    return new RCRMapLayer( extractRescueData( data ) );

  }


  public static RCRMapLayer fromRCRMap( RCRLegacyMap fromMap ) {
    return new RCRMapLayer( new RCRDataSet( fromMap ) );
  }


  private static RCRDataSet extractRescueData( DataSet data ) {
    LatLon min = Main.map.mapView.getLatLon( 0, Main.map.mapView.getHeight() );
    LatLon max = Main.map.mapView.getLatLon( Main.map.mapView.getWidth(), 0 );
    Bounds bounds = new Bounds( min, max );
    return new RCRDataSet( data, bounds );
  }


  @Override
  public Icon getIcon() {
    return icon;
  }


  @Override
  public String getToolTipText() {
    return tr( "Rescue map layer" );
  }


  @Override
  public boolean isMergable( Layer other ) {
    return false;
  }


  @Override
  public void mergeFrom( Layer from ) {
  }


  @Override
  public void paint( final Graphics2D g, final MapView mv, Bounds box ) {
    boolean active = Main.map.mapView.getActiveLayer() == this;
    boolean inactive = !active
        && Main.pref.getBoolean( "draw.data.inactive_color", true );
    boolean virtual = !inactive && Main.map.mapView.isVirtualNodesEnabled();

    // MapRendererFactory.getInstance().activate(RCRPainter.class);
    RCRPainter painter = new RCRPainter( g, rcrData, mv, inactive );
    painter.showErrors = showErrors;

    // Rendering painter =
    // MapRendererFactory.getInstance().createActiveRenderer(g, mv, inactive);
    painter.render( data, virtual, box );

    Main.map.conflictDialog.paintConflicts( g, mv );

  }


  @Override
  public Object getInfoComponent() {
    return getToolTipText();
  }


  @Override
  public Action[] getMenuEntries() {
    return new Action[]{
        LayerListDialog.getInstance().createShowHideLayerAction(),
        LayerListDialog.getInstance().createDeleteLayerAction(),
        SeparatorLayerAction.INSTANCE, new LayerSaveAction( this ),
        new LayerSaveAsAction( this ), SeparatorLayerAction.INSTANCE,
        new RenameLayerAction( getAssociatedFile(), this ),
        SeparatorLayerAction.INSTANCE, new LayerListPopup.InfoAction( this ) };
  }
}
