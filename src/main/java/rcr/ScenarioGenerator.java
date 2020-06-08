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

import static rcr.RCRDataSet.addHumanoid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.openstreetmap.josm.data.osm.Node;

public class ScenarioGenerator {

  RCRDataSet      m_data;
  Random          m_random                  = new Random();

  private int     m_refuges                 = RCRPlugin.settings.refugeCount();
  private int     fires                     = RCRPlugin.settings.fireCount();
  private int     civs                      = RCRPlugin.settings.civCount();
  private int     amb                       = RCRPlugin.settings.atCount();
  private int     fb                        = RCRPlugin.settings.fbCount();
  private int     pf                        = RCRPlugin.settings.pfCount();
  private int     m_fs                      = RCRPlugin.settings.fsCount();;
  private int     m_po                      = RCRPlugin.settings.poCount();;
  private int     m_ac                      = RCRPlugin.settings.acCount();;

  private boolean m_burningSpecialBuildings = false;


  public ScenarioGenerator( RCRDataSet data ) {
    m_data = data;
  }


  String[] agentTags    = new String[]{ "rcr:ambulances", "rcr:firebrigades",
      "rcr:policeforces", "rcr:civilians" };

  String[] buildingTags = new String[]{ "rcr:building_type", "rcr:fire" };


  public void clearScenario() {
    for ( Node n : m_data.getData().getNodes() ) {
      for ( String tag : buildingTags )
        n.remove( tag );
      for ( String tag : agentTags )
        n.remove( tag );
    }
  }


  public void makeScenario() {
    clearScenario();
    ArrayList<Node> positions = new ArrayList<Node>();
    ArrayList<Node> buildings = new ArrayList<Node>();

    for ( Node n : m_data.getData().getNodes() ) {
      if ( n.isDeleted() ) continue;

      if ( n.hasTag( "rcr:type", "node" ) )
        positions.add( n );
      else if ( n.hasTag( "rcr:type", "building" ) ) {
        positions.add( n );
        buildings.add( n );
      }
    }

    if ( buildings.isEmpty() ) return;

    placeBuildings( "firestation", m_fs, buildings );
    placeBuildings( "policeoffice", m_po, buildings );
    placeBuildings( "ambulancecenter", m_ac, buildings );
    placeBuildings( "refuge", m_refuges, buildings );

    int c = buildings.size();

    int maxTries = fires * 2;
    while ( fires > 0 && maxTries > 0 ) {
      Node b = buildings.get( Math.abs( m_random.nextInt() ) % c );
      if ( !b.hasKey( "rcr:fire" ) ) {
        if ( m_burningSpecialBuildings || !b.hasKey( "rcr:building_type" ) ) {
          b.put( "rcr:fire", "true" );
          fires--;
        }
      }
      maxTries--;
    }

    while ( civs > 0 ) {
      Node b = buildings.get( Math.abs( m_random.nextInt() ) % c );
      addHumanoid( b, "rcr:civilians" );
      civs--;
    }

    c = positions.size();

    while ( amb > 0 ) {
      Node n = positions.get( Math.abs( m_random.nextInt() ) % c );
      addHumanoid( n, "rcr:ambulances" );
      amb--;
    }
    while ( fb > 0 ) {
      Node n = positions.get( Math.abs( m_random.nextInt() ) % c );
      addHumanoid( n, "rcr:firebrigades" );
      fb--;
    }
    while ( pf > 0 ) {
      Node n = positions.get( Math.abs( m_random.nextInt() ) % c );
      addHumanoid( n, "rcr:policeforces" );
      pf--;
    }

  }


  private void placeBuildings( String type, int count, List<Node> nodes ) {
    int maxTries = count * 2;
    while ( count > 0 && maxTries > 0 ) {
      Node b = nodes.get( Math.abs( m_random.nextInt() ) % nodes.size() );
      if ( !b.hasKey( "rcr:building_type" ) ) {
        b.put( "rcr:building_type", type );
        count--;
      }
      maxTries--;
    }
  }

}
