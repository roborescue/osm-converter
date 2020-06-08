/*
 * Copyright (c) .aa2009, Rercsearch Group on the Foundations of Artificial
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

import java.awt.Point;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataSetListener;
import org.openstreetmap.josm.data.osm.event.NodeMovedEvent;
import org.openstreetmap.josm.data.osm.event.PrimitivesAddedEvent;
import org.openstreetmap.josm.data.osm.event.PrimitivesRemovedEvent;
import org.openstreetmap.josm.data.osm.event.RelationMembersChangedEvent;
import org.openstreetmap.josm.data.osm.event.TagsChangedEvent;
import org.openstreetmap.josm.data.osm.event.WayNodesChangedEvent;
import org.openstreetmap.josm.tools.Pair;

import rcr.export.LegacyImporter;
import rcr.export.RCRLegacyMap;
import rcr.export.RCRRoad;
import rescuecore.RescueConstants;
import tools.AreaTools;
import tools.MPTools;

public class RCRDataSet implements DataSetListener {

  // static String[] relevantAreaTags = new String[] { "highway", "building" };

  private DataSet                             data          = new DataSet();
  public final RCRLegacyMap                   sourceMap;

  public Map<Way, EntranceGenerator.Entrance> entrances     = new HashMap<>();
  private boolean                             entrancesDirty;

  public Map<Way, Collection<RCRRoad>>        gmlSegments   = new HashMap<>();

  boolean                                     refreshErrors = false;
  private Set<OsmPrimitive>                   unconnected   = new HashSet<>();
  private Set<OsmPrimitive>                   overlapping   = new HashSet<>();

  Random                                      random        = new Random();

  private Bounds                              bounds;
  private LatLon                              boundsMin;                      // change
                                                                              // to
                                                                              // EastNorth?


  public RCRDataSet( DataSet _data ) {
    sourceMap = null;

    for ( Node n : _data.getNodes() ) {
      addPrimitive( new Node( n ) );
    }
    for ( Way w : _data.getWays() ) {
      List<Node> nodes = new LinkedList<Node>();
      for ( Node n : w.getNodes() ) {
        nodes.add( (Node) data.getPrimitiveById( n.getPrimitiveId() ) );
      }
      Way newWay = new Way( w );
      newWay.setNodes( nodes );
      addPrimitive( newWay );
    }
    for ( Relation e : _data.getRelations() ) {
      List<RelationMember> members = new LinkedList<RelationMember>();
      for ( RelationMember m : e.getMembers() ) {
        OsmPrimitive osm = data
            .getPrimitiveById( m.getMember().getPrimitiveId() );
        members.add( new RelationMember( m.getRole(), osm ) );
      }
      Relation newRel = new Relation( e );
      newRel.setMembers( members );
      addPrimitive( newRel );
    }
    for ( DataSource source : _data.dataSources ) {
      data.dataSources.add( new DataSource( source.bounds, source.origin ) );
    }
    entrancesDirty = true;
    updateElements();
    data.addDataSetListener( this );
  }


  public RCRDataSet( DataSet source, Bounds bounds ) {
    sourceMap = null;

    boundsMin = bounds.getMin();
    this.bounds = bounds;
    data.dataSources.add( new DataSource( bounds, "RCR-Converter" ) );
    data.setUploadDiscouraged( true );

    System.out.println( bounds );

    RoadGenerator rgen = new RoadGenerator( this, source );
    rgen.generateRoads( bounds );

    assert sanityCheck();

    // removeDisconnectedWays();

    addBuildings( source.getWays(), source.getRelations(), bounds );

    BuildingGenerator bgen = new BuildingGenerator( this, source );
    bgen.generateBuildings( bounds );

    rgen.removeRoadsInShapes();
    cleanupLonelyNodes();

    entrancesDirty = true;

    data.addDataSetListener( this );
  }


  public RCRDataSet( RCRLegacyMap fromMap ) {
    boundsMin = new LatLon( 50, 7 );
    sourceMap = fromMap;

    LegacyImporter importer = new LegacyImporter( this );
    importer.importMap( fromMap );

    updateElements();
    entrancesDirty = true;

    data.dataSources
        .add( new DataSource( bounds, "RCR-Converter imported map" ) );
    data.addDataSetListener( this );
  }


  public void updateElements() {
    double minLat = Double.MAX_VALUE;
    double minLon = Double.MAX_VALUE;
    double maxLat = -Double.MAX_VALUE;
    double maxLon = -Double.MAX_VALUE;
    for ( Node n : data.getNodes() ) {
      if ( n.isDeleted() || n.getCoor() == null ) continue;

      if ( n.getCoor().lat() < minLat ) minLat = n.getCoor().lat();
      if ( n.getCoor().lon() < minLon ) minLon = n.getCoor().lon();
      if ( n.getCoor().lat() > maxLat ) maxLat = n.getCoor().lat();
      if ( n.getCoor().lon() > maxLon ) maxLon = n.getCoor().lon();
    }
    boundsMin = new LatLon( minLat, minLon );
    bounds = new Bounds( new LatLon( minLat, minLon ),
        new LatLon( maxLat, maxLon ) );
  }


  public List<Relation> getRelationsOf( OsmPrimitive osm, String role ) {
    List<Relation> result = new ArrayList<Relation>();

    for ( Relation rel : OsmPrimitive.getFilteredList( osm.getReferrers(),
        Relation.class ) ) {
      for ( RelationMember m : rel.getMembers() ) {
        if ( m.refersTo( osm ) && role.equals( m.getRole() ) ) {
          result.add( rel );
          break;
        }
      }
    }
    return result;
  }


  public void fillBuildingBlocks( Collection<Way> blocks ) {
    for ( Way w : blocks ) {
      if ( !w.isDeleted() && w.hasTag( "rcr:type", "block" ) ) {
        w.put( "rcr:type", "filled_block" );
        BuildingGenerator bg = new BuildingGenerator( this, null );
        bg.placeRandomHouse( w );
      }
    }
  }


  public void genTB() {
    int lastNN = 77000;
    List<Node> outline = new ArrayList<Node>();

    LatLon coord = new LatLon( 48.0141, 7.8339 );
    Node n0 = new Node( coord );
    n0.setOsmId( lastNN++, 0 );
    coord = new LatLon( 48.0136, 7.8339 );
    Node n1 = new Node( coord );
    n1.setOsmId( lastNN++, 0 );
    coord = new LatLon( 48.0136, 7.8341 );
    Node n2 = new Node( coord );
    n2.setOsmId( lastNN++, 0 );
    coord = new LatLon( 48.0141, 7.8341 );
    Node n3 = new Node( coord );
    n3.setOsmId( lastNN++, 0 );

    n0.put( "rcr:type", "node" );
    n1.put( "rcr:type", "node" );
    n2.put( "rcr:type", "node" );
    n3.put( "rcr:type", "node" );

    outline.add( n0 );
    outline.add( n1 );
    outline.add( n2 );
    outline.add( n3 );
    addPrimitive( n0 );
    addPrimitive( n1 );
    addPrimitive( n2 );
    addPrimitive( n3 );

    /*
     * bnode.put("rcr:type", "building"); bnode.put("rcr:outline",
     * String.valueOf(building.id)); bnode.put("rcr:building_code", "0");
     * bnode.put("rcr:floors", "2"); double minDist = Double.MAX_VALUE; Node
     * entrance = null; for (Node node : nodes) { if
     * (!"node".equals(node.get("rcr:type"))) continue; double dist =
     * node.coor.distance(bnode.coor); if (dist < minDist) { minDist = dist;
     * entrance = node; } } if(entrance!=null) bnode.put("rcr:entrances",
     * String.valueOf(entrance.id)); outlineMap.put(building.id, building);
     */

    // gen way from outline
    Way w = new Way( 42000 );
    for ( Node n : outline ) {
      w.addNode( n );
    }
    // w.incomplete = false;

    w.addNode( outline.get( 0 ) );
    System.out.println( "closed " + w.isClosed() );

    // TODO is this all?
    w.put( "building", "yes" );

    makeBuilding( w, true );

  }


  public static Collection<Way> getWaysAtNode( Node n ) {
    Collection<OsmPrimitive> refs = n.getReferrers();
    Collection<Way> result = new LinkedList<Way>();

    for ( OsmPrimitive p : refs ) {
      if ( p instanceof Way ) {
        result.add( (Way) p );
      }
    }
    return result;
  }


  public static void addHumanoid( OsmPrimitive n, int type ) {
    String key;
    switch ( type ) {
      case RescueConstants.TYPE_CIVILIAN:
        key = "rcr:civilians";
        break;
      case RescueConstants.TYPE_AMBULANCE_TEAM:
        key = "rcr:ambulanceteams";
        break;
      case RescueConstants.TYPE_POLICE_FORCE:
        key = "rcr:policeforces";
        break;
      case RescueConstants.TYPE_FIRE_BRIGADE:
        key = "rcr:firebrigades";
        break;
      default:
        return;
    }
    addHumanoid( n, key );
  }


  public static void addHumanoid( OsmPrimitive n, String type ) {
    int count = 1;
    if ( n.get( type ) != null ) {
      count = parseInt( n, type, 0 ) + 1;
    }
    n.put( type, Integer.toString( count ) );
  }


  public DataSet getData() {
    return data;
  }


  public static int parseInt( OsmPrimitive obj, String key, int defaultValue ) {
    String val = obj.get( key );
    if ( val == null ) return defaultValue;
    try {
      return Integer.parseInt( val );
    } catch ( NumberFormatException e ) {
    }
    return defaultValue;
  }


  public static long parseLong( OsmPrimitive obj, String key,
      long defaultValue ) {
    String val = obj.get( key );
    if ( val == null ) return defaultValue;
    try {
      return Long.parseLong( val );
    } catch ( NumberFormatException e ) {
    }
    return defaultValue;
  }


  public double getScale() {
    return RCRPlugin.settings.scale();
  }


  public void cleanupRemovedElements() {
    // cleanup nodes
    for ( Node n : data.getNodes() ) {
      if ( !"removed_node".equals( n.get( "rcr:type" ) ) ) continue;
      // assert waysOfNode.get(n) == null || waysOfNode.get(n).isEmpty();
      // waysOfNode.remove(n);
      System.out.println( "remove " + n.getId() );
      removePrimitive( n );
    }

    for ( Way w : data.getWays() ) {
      if ( !w.hasTag( "rcr:type", "deleted_way" ) ) continue;
      removePrimitive( w );
    }

  }


  public void cleanupLonelyNodes() {
    for ( Node n : data.getNodes() ) {
      if ( n.getReferrers().isEmpty() ) {
        removePrimitive( n );
      }
    }
  }


  public Point osm2rescueCoords( Node e ) {
    EastNorth coord = Main.getProjection().latlon2eastNorth( e.getCoor() );
    EastNorth minEN = Main.getProjection().latlon2eastNorth( boundsMin );

    Point p = new Point();
    p.x = (int) ( getScale() * 1000 * ( coord.getX() - minEN.getX() ) );
    p.y = (int) ( getScale() * 1000 * ( coord.getY() - minEN.getY() ) );
    return p;
  }


  public LatLon rcr2osmCoord( int x, int y ) {
    EastNorth minEN = Main.getProjection().latlon2eastNorth( boundsMin );

    double east = ( (double) x ) / ( getScale() * 1000 ) + minEN.east();
    double north = ( (double) y ) / ( getScale() * 1000 ) + minEN.north();
    return Main.getProjection()
        .eastNorth2latlon( new EastNorth( east, north ) );
  }


  private void addBuildings( Collection<Way> ways, Collection<Relation> rels,
      Bounds bounds ) {
    BBox bbox = bounds.toBBox();

    for ( Relation r : rels ) {
      if ( MPTools.isMultipolygon( r, bounds, "building" ) ) {
        // List<Way> buildings = BuildingGenerator.segmentComplexShape(r);
        List<Way> buildings = MPTools.segmentMP( r );
        for ( Way b : buildings ) {
          System.out.println(
              "adding building " + b.getId() + " as part of mp " + r.getId() );
          makeBuilding( b, true );
        }
      }
    }

    for ( Way way : ways ) {
      if ( !bbox.bounds( way.getBBox() ) ) {
        continue;
      }
      if ( !way.hasKey( "building" )
          || MPTools.isPartOfMP( way, "building" ) ) {
        continue;
      }

      String streetname = null;
      for ( OsmPrimitive osm : way.getReferrers( false ) ) {
        if ( !( osm instanceof Relation ) ) continue;
        Relation r = (Relation) osm;
        if ( r.hasTag( "type", "associatedStreet" ) ) {
          if ( "name".equals( r.get( "name" ) ) ) {
            streetname = r.get( "name" );
          } else {
            for ( RelationMember m : r.getMembers() ) {
              if ( m.getRole().equals( "street" )
                  && m.getMember().hasKey( "name" ) ) {
                streetname = m.getMember().get( "name" );
                break;
              }
            }
          }
        }
      }
      // Way newWay = new Way(way);
      // addPrimitive(newWay);
      Way newBuilding = makeBuilding( way, true );
      if ( newBuilding != null && !newBuilding.hasKey( "addr:street" )
          && streetname != null ) {
        newBuilding.put( "addr:street", streetname );
      }
    }
  }


  public boolean canCreateEntrances() {
    return !entrancesDirty;
  }


  /**
   * Returns the ways which represent a building and are not
   * <emph>directly</emph> connected to the main road network.
   *
   * @param ways
   * @return
   */
  private List<Way> findNonEntranceBuildings( Collection<Way> ways ) {
    List<Way> result = new ArrayList<>();
    Set<Way> mainGroup = findLargestConnectedGroup();
    for ( Way w : ways ) {
      if ( !w.hasTag( "rcr:type", "building" ) ) {
        continue;
      }
      boolean connected = false;
      for ( Way c : findNeighbours( w ) ) {
        if ( c.hasTag( "rcr:type", "road" ) && mainGroup.contains( c ) ) {
          connected = true;
          break;
        }
      }
      if ( !connected ) {
        result.add( w );
      }
    }
    return result;
  }


  public void generateEntrances() {
    data.cleanupDeletedPrimitives();
    entrances.clear();
    checkConnectivity();
    EntranceGenerator gen = new EntranceGenerator( this );
    for ( Way w : findNonEntranceBuildings( data.getWays() ) ) {
      boolean toRoadsOnly = !unconnected.contains( w );
      EntranceGenerator.Entrance e = gen.getEntrance( w, toRoadsOnly );
      if ( e != null ) {
        entrances.put( w, e );
      }
    }
    entrancesDirty = false;
    checkConnectivity();
  }


  public void realizeEntrances() {
    if ( entrancesDirty ) return;

    data.beginUpdate();
    EntranceGenerator gen = new EntranceGenerator( this );
    for ( Way w : data.getWays() ) {
      if ( w.hasTag( "rcr:type", "building" ) && entrances.containsKey( w ) ) {
        gen.createEntranceWay( entrances.get( w ) );
      }
    }
    entrancesDirty = false;
    entrances.clear();
    data.endUpdate();
  }


  public Way makeBuilding( Way building, boolean copyWay ) {
    if ( building.getNodesCount() < 3 ) {
      return null;
    }
    if ( copyWay ) {
      building = new Way( building );
      List<Node> nodes = new LinkedList<Node>();
      for ( Node old : building.getNodes() ) {
        Node n = (Node) data.getPrimitiveById( old.getPrimitiveId() );
        if ( n == null ) {
          n = new Node( old );
          addPrimitive( n );
        }
        nodes.add( n );
      }

      building.setNodes( nodes );
      building.put( "rcr:type", "building" );
      addPrimitive( building );
    }

    int type = 0;
    // anyways all Bs id = 0 not good
    building.put( "rcr:type", "building" );
    building.put( "rcr:building_code", String.valueOf( type ) );
    building.put( "rcr:floors", "2" );

    building.put( "rcr:building", "1" );
    return building;
  }


  public void addPrimitive( OsmPrimitive osm ) {
    // For testing, as josm doesn't always show ids
    // osm.put("rcr:osmid", String.valueOf(osm.id));
    if ( !osm.isNew() ) {
      osm.setVisible( false );
    }
    data.addPrimitive( osm );
  }


  public void removePrimitive( OsmPrimitive osm ) {
    System.out.println( "remove " + osm.getId() );
    data.removePrimitive( osm );
    osm.setDeleted( true );
  }


  public Node mergeNodes( Collection<Node> nodes, boolean average ) {
    assert sanityCheck();
    HashSet<Node> found = new HashSet<Node>();
    Iterator<Node> iter = nodes.iterator();
    List<Relation> connectedBuildings = new ArrayList<Relation>();
    Node main = iter.next();
    found.add( main );
    double avgLat = main.getCoor().lat();
    double avgLon = main.getCoor().lon();
    int count = 1;
    while ( iter.hasNext() ) {
      Node n = iter.next();
      // duplicates won't screw up the merging now
      if ( found.contains( n ) ) continue;
      found.add( n );
      avgLat += n.getCoor().lat();
      avgLon += n.getCoor().lon();
      count++;
      n.put( "rcr:type", "removed_node" );
      for ( Way w : getWaysAtNode( n ) ) {
        System.out
            .println( "removing node " + n.getId() + " from " + w.getId() );
        List<Node> wNodes = w.getNodes();
        for ( int i = 0; i < wNodes.size(); i++ ) {
          if ( wNodes.get( i ) == n ) {
            wNodes.set( i, main );
          }
        }
        w.setNodes( wNodes );
        removeDuplicateNodes( w );
        // waysOfNode.get(main).add(w);
      }
      Collection<Relation> buildings = getRelationsOf( n, "rcr:entrance" );
      for ( Relation r : buildings ) {
        r.removeMembersFor( n );
        connectedBuildings.add( r );
      }
      // waysOfNode.remove(n);
    }
    if ( average ) {
      main.setCoor( new LatLon( avgLat / count, avgLon / count ) );
      // main.eastNorth = Main.proj.latlon2eastNorth(main.coor);
    }
    for ( Relation r : connectedBuildings ) {
      r.addMember( new RelationMember( "rcr:entrance", main ) );
    }
    assert sanityCheck();
    return main;
  }


  public boolean sanityCheck() {
    for ( Way w : data.getWays() ) {
      if ( !"road".equals( w.get( "rcr:type" ) ) || w.isDeleted() ) continue;
      // System.out.println(w.id);
      for ( Node n : w.getNodes() ) {
        /*
         * if (!waysOfNode.containsKey(n)) { System.err.println("no way set: " +
         * w +", " + n); return false; }
         */
        if ( !getWaysAtNode( n ).contains( w ) ) {
          System.err.println( "way not in way set: " + w + ", " + n );
          return false;
        }
      }
    }
    return true;
  }


  public void checkConnectivity() {
    Set<Way> mainGroup = findLargestConnectedGroup();
    unconnected.clear();
    for ( Way w : data.getWays() ) {
      if ( w.hasTag( "rcr:type", "building", "road" ) ) {
        if ( !entrances.containsKey( w ) && !mainGroup.contains( w ) ) {
          unconnected.add( w );
        }
      }
    }
  }


  public void checkConnectivity( BBox bounds ) {
    Set<Way> mainGroup = findLargestConnectedGroup();
    unconnected.clear();
    for ( Way w : data.searchWays( bounds ) ) {
      if ( w.hasTag( "rcr:type", "building", "road" ) ) {
        if ( !entrances.containsKey( w ) && !mainGroup.contains( w ) ) {
          unconnected.add( w );
        }
      }
    }
  }


  private boolean segmentsTouch( Node n1, Node n2, Node n3, Node n4 ) {
    return ( n1 == n3 || n1 == n4 || n2 == n3 || n2 == n4 );
  }


  public void checkOverlaps( BBox bounds ) {
    for ( Way w : data.searchWays( bounds ) ) {
      overlapping.remove( w );
      if ( w.hasTag( "rcr:type", "building", "road" ) && checkOverlap( w ) ) {
        overlapping.add( w );
        System.err.println( "" + w.getId() + " is overlapping" );
      }
    }
  }


  public void checkOverlaps() {
    overlapping.clear();
    for ( Way w : data.getWays() ) {
      if ( w.hasTag( "rcr:type", "building", "road" ) && checkOverlap( w ) ) {
        overlapping.add( w );
        System.err.println( "" + w.getId() + " is overlapping" );
      }
    }
  }


  public static boolean isAreaShape( OsmPrimitive osm ) {
    if ( !( osm instanceof Way ) ) {
      return false;
    }
    Way w = (Way) osm;
    return w.isArea() && ( ( w.hasTag( "rcr:type", "road" ) && w.hasAreaTags() )
        || ( w.hasTag( "rcr:type", "building" ) ) );
  }


  private boolean checkOverlap( Way w ) {
    if ( isAreaShape( w ) ) {
      List<Way> other = data.searchWays( w.getBBox() );
      for ( Way w2 : other ) {
        if ( w != w2 && isAreaShape( w2 ) ) {
          if ( AreaTools.waysIntersect( w, w2 ) ) {
            return true;
          }
        }
      }
    } else if ( w.hasTag( "rcr:type", "road" ) ) {
      // Intersections with centerline first
      if ( checkRoadOverlap( w, 0.0 ) ) {
        return true;
      } else if ( !w.hasAreaTags() && checkRoadOverlap( w ) ) {
        return true;
      }
    }
    return false;
  }


  public boolean checkRoadOverlap( Way w ) {
    double width = parseInt( w, "rcr:width", Constants.DEFAULT_LANE_WIDTH )
        / 1000.;
    return checkRoadOverlap( w, width );
  }


  public boolean checkRoadOverlap( Way w, double width ) {
    // Create bbox that includes the road + width
    EastNorth topLeft = Main.getProjection()
        .latlon2eastNorth( w.getBBox().getTopLeft() );
    EastNorth bottomRight = Main.getProjection()
        .latlon2eastNorth( w.getBBox().getBottomRight() );
    topLeft = topLeft.add( -width / 2, width / 2 );
    bottomRight = bottomRight.add( width / 2, -width / 2 );
    BBox bounds = new BBox( Main.getProjection().eastNorth2latlon( topLeft ),
        Main.getProjection().eastNorth2latlon( bottomRight ) );

    List<Way> other = data.searchWays( bounds );
    for ( Pair<Node, Node> n1 : w.getNodePairs( false ) ) {
      Point2D p1 = Vector.asPoint( n1.a.getEastNorth() );
      Point2D p2 = Vector.asPoint( n1.b.getEastNorth() );
      Line2D l1a = null, l1b = null;

      if ( width == 0 ) {
        l1a = new Line2D.Double( p1, p2 );
      } else {
        Point2D offset = Vector
            .times( Vector.normalVec( Vector.diff( p2, p1 ) ), width / 2 );

        l1a = new Line2D.Double( Vector.sum( p1, offset ),
            Vector.sum( p2, offset ) );
        l1b = new Line2D.Double( Vector.diff( p1, offset ),
            Vector.diff( p2, offset ) );
      }

      for ( Way w2 : other ) {
        if ( w2.hasTag( "rcr:type", "building", "road" ) ) {
          for ( Pair<Node, Node> n2 : w2.getNodePairs( false ) ) {
            if ( segmentsTouch( n1.a, n1.b, n2.a, n2.b ) ) {
              continue;
            }
            Line2D l2 = new Line2D.Double(
                Vector.asPoint( n2.a.getEastNorth() ),
                Vector.asPoint( n2.b.getEastNorth() ) );
            if ( l1a.intersectsLine( l2 )
                || ( l1b != null && l1b.intersectsLine( l2 ) ) ) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }


  public void setRefreshErrors( boolean enabled ) {
    refreshErrors = enabled;
  }


  public boolean hasError( OsmPrimitive osm ) {
    if ( osm instanceof Way && osm.hasAreaTags() && !( (Way) osm ).isArea() ) {
      return true;
    }
    if ( unconnected.contains( osm ) ) {
      return true;
    }
    if ( overlapping.contains( osm ) ) {
      return true;
    }
    return false;
  }


  public void removeDuplicateNodes( Way w ) {
    List<Node> nodes = w.getNodes();
    Iterator<Node> it = nodes.iterator();
    Node prev = null;
    while ( it.hasNext() ) {
      Node n = it.next();
      if ( prev == n ) it.remove();
      prev = n;
    }
    w.setNodes( nodes );
  }


  public void removeDisconnectedWays() {
    HashSet<Way> allWays = new HashSet<Way>();
    for ( Way w : data.getWays() ) {
      if ( !w.isDeleted() && w.hasTag( "rcr:type", "road" ) ) allWays.add( w );
    }
    int roadCount = allWays.size();

    List<Set<Way>> clusters = new LinkedList<Set<Way>>();
    Set<Way> largest = null;
    while ( !allWays.isEmpty() ) {
      Way w = allWays.iterator().next();
      Set<Way> cluster = findConnectedWays( w );
      clusters.add( cluster );
      allWays.removeAll( cluster );
      System.out.println( "cluster size: " + cluster.size() );
      if ( largest == null || cluster.size() > largest.size() )
        largest = cluster;
    }
    if ( largest.size() < roadCount * 0.7 )
      System.err.println( "Warning: probably disconnected large clusters" );

    for ( Set<Way> cluster : clusters ) {
      if ( cluster == largest ) continue;
      for ( Way w : cluster )
        removeWayWithNodes( w );
    }
  }


  private void removeWayWithNodes( Way w ) {
    for ( Node n : w.getNodes() ) {
      removePrimitive( n );
    }
    removePrimitive( w );
  }


  private static boolean waysShareEdge( Way w1, Way w2 ) {
    for ( Pair<Node, Node> p1 : w1.getNodePairs( false ) ) {
      for ( Pair<Node, Node> p2 : w2.getNodePairs( false ) ) {
        if ( ( p1.a == p2.a && p1.b == p2.b )
            || ( p1.a == p2.b && p1.b == p2.a ) ) {
          return true;
        }
      }
    }
    return false;
  }


  public static Collection<Way> findNeighbours( Way w ) {
    List<Way> result = new ArrayList<>();
    System.out.println( "neigbours of :" + w.getId() );
    for ( Node n : w.getNodes() ) {
      for ( Way w2 : getWaysAtNode( n ) ) {
        if ( w != w2 && w2.hasTag( "rcr:type", "road", "building" ) ) {
          if ( w2.hasTag( "rcr:type", "road" ) && !w2.hasAreaTags() ) {
            // connection to linear road
            System.out.println( "  to linear road :" + w2.getId() );
            result.add( w2 );
          } else if ( w.hasTag( "rcr:type", "road" ) && !w.hasAreaTags() ) {
            // connection *from* linear road
            System.out.println( "  from linear road :" + w2.getId() );
            result.add( w2 );
          } else if ( waysShareEdge( w, w2 ) ) {
            // connection via shared edges
            System.out.println( "  shared edge :" + w2.getId() );
            result.add( w2 );
          }
        }
      }
    }
    return result;
  }


  public Set<Way> findLargestConnectedGroup() {
    Set<Way> allWays = new HashSet<>( data.getWays() );
    Set<Way> result = null;
    while ( !allWays.isEmpty() ) {
      Set<Way> cluster = findConnectedWays( allWays.iterator().next() );
      allWays.removeAll( cluster );
      if ( result == null || result.size() < cluster.size() ) {
        result = cluster;
      }
    }
    return result;
  }


  public Set<Way> findConnectedWays( Way w ) {
    System.out.println( "find ways from " + w.getId() );
    HashSet<Way> result = new HashSet<Way>();
    Deque<Way> open = new ArrayDeque<Way>();
    result.add( w );
    open.add( w );
    while ( !open.isEmpty() ) {
      w = open.pop();
      for ( Way w2 : findNeighbours( w ) ) {
        if ( !result.contains( w2 ) ) {
          open.push( w2 );
          result.add( w2 );
        }
      }
    }
    return result;
  }


  public void removeEntrance( Way e ) {
    if ( e.hasTag( "rcr:entrance", "yes" ) && e.hasTag( "rcr:type", "road" ) ) {
      this.data.removePrimitive( e );
      for ( Node n : e.getNodes() ) {
        if ( n.hasTag( "rcr:autogenerated", "entrance" ) ) {
          boolean hasOtherEntrance = false;
          for ( OsmPrimitive w : n.getReferrers() ) {
            if ( w != e && w.hasTag( "rcr:entrance", "yes" )
                && w.hasTag( "rcr:type", "road" ) ) {
              hasOtherEntrance = true;
              break;
            }
          }
          if ( !hasOtherEntrance ) {
            for ( OsmPrimitive w : n.getReferrers() ) {
              if ( w != e && w instanceof Way ) {
                ( (Way) w ).removeNode( n );
              }
            }
            this.data.removePrimitive( n );
          }
        }
      }
    }
  }


  public static BBox
      getBoundsFromWays( Collection<? extends OsmPrimitive> primitives ) {
    BBox bounds = null;
    for ( OsmPrimitive osm : primitives ) {
      if ( osm instanceof Way ) {
        if ( bounds == null ) {
          bounds = new BBox( (Way) osm );
        } else {
          bounds.add( osm.getBBox() );
        }
      }
    }
    return bounds;
  }


  @Override
  public void primitivesAdded( PrimitivesAddedEvent event ) {
    if ( refreshErrors ) {
      BBox bounds = getBoundsFromWays( event.getPrimitives() );
      if ( bounds != null ) {
        checkOverlaps( bounds );
      }
    }
    entrancesDirty = true;
  }


  @Override
  public void primitivesRemoved( PrimitivesRemovedEvent event ) {
    if ( refreshErrors ) {
      BBox bounds = getBoundsFromWays( event.getPrimitives() );
      if ( bounds != null ) {
        checkOverlaps( bounds );
        checkConnectivity( bounds );
      }
    }
    for ( OsmPrimitive osm : event.getPrimitives() ) {
      if ( osm instanceof Way ) {
        gmlSegments.remove( osm );
      }
    }
    entrancesDirty = true;
  }


  @Override
  public void tagsChanged( TagsChangedEvent event ) {
    if ( event.getPrimitive() instanceof Way ) {
      BBox bounds = new BBox( (Way) event.getPrimitive() );
      if ( refreshErrors ) {
        checkOverlaps( bounds );
      }
      gmlSegments.remove( event.getPrimitive() );
    }
    entrancesDirty = true;
  }


  @Override
  public void nodeMoved( NodeMovedEvent event ) {
    if ( event.getNode().getDataSet() == null ) {
      // can happen when undoing node additions
      return;
    }

    if ( refreshErrors ) {
      BBox bounds = getBoundsFromWays( event.getNode().getReferrers() );
      if ( bounds != null ) {
        checkOverlaps( bounds );
      }
    }
    for ( OsmPrimitive osm : event.getNode().getReferrers() ) {
      if ( osm instanceof Way ) {
        gmlSegments.remove( osm );
      }
    }
    entrancesDirty = true;
  }


  @Override
  public void wayNodesChanged( WayNodesChangedEvent event ) {
    if ( refreshErrors ) {
      checkOverlaps( new BBox( event.getChangedWay() ) );
      checkConnectivity( new BBox( event.getChangedWay() ) );
    }
    gmlSegments.remove( event.getChangedWay() );
    entrancesDirty = true;
  }


  @Override
  public void relationMembersChanged( RelationMembersChangedEvent event ) {
    // TODO Auto-generated method stub

  }


  @Override
  public void otherDatasetChange( AbstractDatasetChangedEvent event ) {
    // TODO Auto-generated method stub

  }


  @Override
  public void dataChanged( DataChangedEvent event ) {
    checkOverlaps();
    checkConnectivity();
    // TODO Auto-generated method stub

  }

}
