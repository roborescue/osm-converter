package rcr;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Pair;

import tools.AreaTools;
import tools.MPTools;

public class RoadGenerator {

  RCRDataSet      rcrdata;
  DataSet         data;
  DataSet         source;

  static double   MAX_CUT_OVERLAP     = 10.0;
  static double   ROAD_WIDTH_ACCURACY = 0.1;

  static String[] highwayValues       = new String[]{ "primary", "primary_link",
      "secondary", "tertiary", "trunk", "trunk_link", "motorway",
      "motorway_link", "residential", "unclassified", "service", "road",
      "pedestrian", "footway", "living_street" };


  public RoadGenerator( RCRDataSet rcrData, DataSet sourceData ) {
    this.rcrdata = rcrData;
    this.data = rcrData.getData();
    this.source = sourceData;
  }

  // for (Way way : ways) {
  // if (!bbox.bounds(way.getBBox())) {
  // continue;
  // }
  //
  // boolean accepted = false;
  // for (AttributeFilter f : buildingWays) {
  // if (f.accept(way)) {
  // accepted = true;
  // break;
  // }
  // }
  // if (!accepted) {
  // continue;
  // }
  //
  // boolean isMP = false;
  // String streetname = null;
  // for (OsmPrimitive osm : way.getReferrers(false)) {
  // if (!(osm instanceof Relation))
  // continue;
  // Relation r = (Relation) osm;
  // if (MPTools.isPartOfMP(way, r, "building")) {
  // isMP = true;
  // break;
  // }


  public void generateRoads( Bounds bounds ) {
    for ( Relation r : source.getRelations() ) {
      if ( MPTools.isMultipolygon( r, bounds, "highway", highwayValues ) ) {
        List<Way> areas = MPTools.segmentMP( r );
        for ( Way a : areas ) {
          Way newWay = new Way( a );
          newWay.put( "area", "yes" );
          addRoad( newWay );
        }
      }
    }

    for ( Way way : source.searchWays( bounds.toBBox() ) ) {
      if ( !isRoad( way ) ) {
        continue;
      }
      if ( MPTools.isPartOfMP( way, "highway", highwayValues ) ) {
        continue;
      }

      boolean contained = true;
      for ( Node n : way.getNodes() ) {
        if ( !n.getCoor().isWithin( bounds ) ) {
          contained = false;
          break;
        }
      }
      Way newWay;
      if ( !contained ) {
        newWay = cutWay( way, bounds );
      } else {
        newWay = new Way( way );
      }

      addRoad( newWay );
    }

  }


  private void addRoad( Way w ) {
    List<Node> newNodes = new ArrayList<Node>();

    for ( Node n : w.getNodes() ) {
      if ( data.getPrimitiveById( n.getPrimitiveId() ) != null ) {
        newNodes.add( (Node) data.getPrimitiveById( n.getPrimitiveId() ) );
      } else {
        Node newNode = new Node( n );
        rcrdata.addPrimitive( newNode );
        newNodes.add( newNode );
      }
    }

    w.setNodes( newNodes );

    w.put( "rcr:type", "road" );
    if ( !w.hasAreaTags() ) {
      w.put( "rcr:width", String.valueOf( getWidth( w ) ) );
    }

    rcrdata.addPrimitive( w );
  }


  private Way cutWay( Way w, Bounds b ) {
    // Start and end are in bounds, keep entire way
    if ( w.getNode( 0 ).getCoor().isWithin( b )
        && w.getNode( w.getNodesCount() - 1 ).getCoor().isWithin( b ) ) {
      System.out.println( "way  " + w.getId() + " loops" );
      return new Way( w );
    }

    EastNorth b1 = Main.getProjection().latlon2eastNorth( b.getMax() );
    EastNorth b2 = Main.getProjection().latlon2eastNorth( b.getMin() );

    List<Node> nodes = new ArrayList<>();
    for ( Pair<Node, Node> p : w.getNodePairs( false ) ) {
      if ( p.a.getCoor().isWithin( b ) ) {
        nodes.add( p.a );
      }

      if ( p.a.getCoor().isWithin( b ) != p.b.getCoor().isWithin( b ) ) {
        // Cut segment
        Line2D segment = new Line2D.Double(
            Vector.asPoint( p.a.getEastNorth() ),
            Vector.asPoint( p.b.getEastNorth() ) );
        Rectangle2D rect = new Rectangle2D.Double(
            Math.min( b1.getX(), b2.getX() ), Math.min( b1.getY(), b2.getY() ),
            Math.abs( b1.getX() - b2.getX() ),
            Math.abs( b1.getY() - b2.getY() ) );
        Point2D cut = cutSegment( segment, rect );
        Node outsideNode = ( p.a.getCoor().isWithin( b ) ) ? p.b : p.a;
        Node insideNode = ( p.a.getCoor().isWithin( b ) ) ? p.a : p.b;

        System.out.println(
            "Cutting " + p.a.getEastNorth() + " -- " + p.b.getEastNorth() );
        if ( cut == null ) {
          assert false;
          // nodes.add(outsideNode);
        } else if ( Vector.asPoint( outsideNode.getEastNorth() )
            .distance( cut ) <= MAX_CUT_OVERLAP ) {
          // Segment may extend MAX_CUT_OVERLAP outside the bbox
          System.out.println( "  extend outside" );
          nodes.add( outsideNode );
        } else if ( Vector.asPoint( insideNode.getEastNorth() )
            .distance( cut ) <= MAX_CUT_OVERLAP ) {
          System.out.println( "  remain inside" );
          // Segment may be short of bbox by MAX_CUT_OVERLAP
        } else {
          // Actually cut the segment
          System.out.println( "  cut" );
          nodes.add( new Node( Main.getProjection()
              .eastNorth2latlon( Vector.asEastNorth( cut ) ) ) );
        }
      }

    }

    // Complete way by adding last node if inside bounds
    if ( w.getNode( w.getNodesCount() - 1 ).getCoor().isWithin( b ) ) {
      nodes.add( w.getNode( w.getNodesCount() - 1 ) );
    }

    Way newWay = new Way( w );
    newWay.setNodes( nodes );
    return newWay;
  }


  private Point2D cutSegment( Line2D l, Rectangle2D r ) {
    Point2D p1 = new Point2D.Double( r.getMinX(), r.getMinY() );
    Point2D p2 = new Point2D.Double( r.getMinX(), r.getMaxY() );
    Point2D p3 = new Point2D.Double( r.getMaxX(), r.getMaxY() );
    Point2D p4 = new Point2D.Double( r.getMaxX(), r.getMinY() );

    Point2D i = Vector.getIntersectionPoint( l.getP1(), l.getP2(), p1, p2 );
    if ( i != null ) return i;
    i = Vector.getIntersectionPoint( l.getP1(), l.getP2(), p2, p3 );
    if ( i != null ) return i;
    i = Vector.getIntersectionPoint( l.getP1(), l.getP2(), p3, p4 );
    if ( i != null ) return i;
    i = Vector.getIntersectionPoint( l.getP1(), l.getP2(), p4, p1 );
    return i;
  }


  private boolean isRoad( Way r ) {
    return r.hasTag( "highway", highwayValues );

  }


  private int getNumLanes( Way r ) {
    if ( r.hasKey( "lanes" ) ) {
      return RCRDataSet.parseInt( r, "lanes", 1 );
    } else if ( r.isKeyTrue( "oneway" ) ) {
      return 1;
    }

    if ( r.hasTag( "highway", "service", "footway" ) ) {
      return 1;
    }
    return 2;

  }


  private int getWidth( Way r ) {
    if ( r.hasKey( "width" ) ) {
      try {
        return (int) ( Double.parseDouble( r.get( "width" ) ) * 1000 );
      } catch ( NumberFormatException e ) {
      }
    }

    int laneWidth = Constants.DEFAULT_LANE_WIDTH;
    int additionalWidth = 1000;
    if ( r.hasTag( "highway", "footway" ) ) {
      laneWidth = Constants.FOOTWAY_WIDTH;
      additionalWidth = 0;
    } else if ( r.hasTag( "highway", "service" ) ) {
      laneWidth = Constants.SERVICE_DEFAULT_WIDTH;
      additionalWidth = 500;
      if ( r.hasTag( "service", "driveway" ) ) {
        laneWidth = Constants.SERVICE_DRIVEWAY_WIDTH;
        additionalWidth = 0;
      }
    }
    return laneWidth * getNumLanes( r ) + additionalWidth;
  }


  public void removeRoadsInShapes() {
    List<Way> toRemove = new ArrayList<>();
    List<Way> toAdd = new ArrayList<>();

    for ( Way r : data.getWays() ) {
      if ( !r.hasTag( "rcr:type", "road" ) || r.hasAreaTags() ) {
        continue;
      }
      System.out.println( "Checking road " + r.getId() );
      List<List<Node>> outsideNodes = new ArrayList<>();
      List<Node> current = new ArrayList<>();
      for ( Pair<Node, Node> p : r.getNodePairs( false ) ) {
        if ( segmentInShape( p.a, p.b ) ) {
          if ( current.size() > 1 ) {
            outsideNodes.add( current );
          }
          current = new ArrayList<>();
        } else {
          if ( current.isEmpty() ) {
            current.add( p.a );
          }
          current.add( p.b );
        }
      }
      if ( current.size() > 1 ) {
        outsideNodes.add( current );
      }
      if ( outsideNodes.isEmpty() ) {
        toRemove.add( r );
      } else if ( outsideNodes.size() == 1 ) {
        r.setNodes( outsideNodes.get( 0 ) );
      } else {
        Iterator<List<Node>> it = outsideNodes.iterator();
        r.setNodes( it.next() );
        while ( it.hasNext() ) {
          Way w2 = new Way();
          w2.setKeys( r.getKeys() );
          w2.setNodes( it.next() );
          toAdd.add( w2 );
        }
      }
    }

    for ( Way r : toRemove ) {
      data.removePrimitive( r );
    }
    for ( Way r : toAdd ) {
      data.addPrimitive( r );
    }
  }


  private boolean segmentInShape( Node n1, Node n2 ) {
    BBox bounds = new BBox( n1 );
    bounds.add( n2.getCoor() );

    Line2D l = new Line2D.Double( Vector.asPoint( n1.getEastNorth() ),
        Vector.asPoint( n2.getEastNorth() ) );

    for ( Way w : data.searchWays( bounds ) ) {
      if ( RCRDataSet.isAreaShape( w )
          && AreaTools.lineContainedInPolygon( l, w ) ) {
        System.out.println( "contained in polygon" );
        return true;
      }
    }
    return false;
  }


  public double getMaxRoadWidth( Way road, double increment ) {
    double width = RCRDataSet.parseInt( road, "rcr:width",
        Constants.DEFAULT_LANE_WIDTH ) / 1000.;
    if ( rcrdata.checkRoadOverlap( road, 0 ) ) {
      return 0.0;
    }

    double min = 0.0;
    double max = width;
    while ( max - min > ROAD_WIDTH_ACCURACY ) {
      assert max > min;
      double w = ( max + min ) / 2;
      if ( rcrdata.checkRoadOverlap( road, w ) ) {
        max = w;
      } else {
        min = w;
      }
    }
    return min;
  }

}
