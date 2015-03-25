# Geoserver MVT Extension

## Overview
This extension for the **Geoserver** adds the possibility to deliver [Mapnik Vector Tiles](https://github.com/mapbox/mapnik-vector-tile/) in Protocol Buffers outputformat as result of an **WMS** request. It has been developed and tested with [Geoserver 2.6.2](http://geoserver.org) but might also work with preceding versions.
The resulting Vector Tiles can e.g. be rendered by a WebGL JS client like [Mapbox GL JS](https://www.mapbox.com/mapbox-gl-js/api/). For the final integration with Mapbox GL JS an additional component that converts the slippy map tile requests into WMS requests is needed.

## Getting Started
In order to get startet the build target ```gs-mvt-2.6.2.jar``` and depending ```protobuf-java-2.6.1.jar``` have to be copied to the geoserver's lib directiory ```geoserver/WEB-INF/lib```. After starting the Geoserver the format ```application/x-protobuf``` is shown in the WMS Format list of the Layer preview.

## WMS Query
Mandatory for the WMS **getMap** request are the ```bbox```, ```witdh``` and ```height``` parameters. The ```bbox``` describes the requested source coordinate system. The ```width``` and ```height``` the target coordinate system (tile local). The MVT format uses a local coordinate system within each tile. By default this is 0 to 256 on x and y axis. An example WMS Request could be: 
```
http://localhost/geoserver/test/wms?STYLES=&LAYERS=test:streetsegments&FORMAT=application/x-protobuf&SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&SRS=EPSG:4326&bbox=9.499057554653266,46.410057509506515,17.1504898071289,49.0165481567383&WIDTH=256&HEIGHT=256
```
As result all features of the requested clip are returned. A [Douglas-Peucker algorithm](http://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm) simplifies these geometries. By default a maximum distance of ```0.1``` target system units are considered by the algorithm.

##Styling
Since the vector features are usualy styled on client side only the filter rules and not the symbolizers of a **Geoserver Style Sheet (SLD)** are considered. Using scale denominators the filter rules can be adjusted so that for example some features are not delivered at higher zoom levels. This prevents unnecessary delivery of features that are not rendered on the client anyway.

Following example shows an Geoserver Style Sheet (SLD) filtering features by a frc value.

```xml
<?xml version="1.0" encoding="ISO-8859-1"?>
<StyledLayerDescriptor version="1.0.0" xmlns="http://www.opengis.net/sld" xmlns:ogc="http://www.opengis.net/ogc"
  xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://www.opengis.net/sld http://schemas.opengis.net/sld/1.0.0/StyledLayerDescriptor.xsd">
  <NamedLayer>
    <Name>test_filter</Name>
    <UserStyle>
      <Name>test</Name>
      <Title>Test Filter Style for Geoserver</Title>
      <Abstract>A sample style for pbf tiles that filters features according to its frc property</Abstract>
      <FeatureTypeStyle>
        <FeatureTypeName>Feature</FeatureTypeName>        
        <Rule>          
          <MaxScaleDenominator>500000</MaxScaleDenominator>          
          <ogc:Filter>            
              <ogc:PropertyIsGreaterThanOrEqualTo>
                <ogc:PropertyName>frc</ogc:PropertyName>
                <ogc:Literal>3</ogc:Literal>
              </ogc:PropertyIsGreaterThanOrEqualTo>                
          </ogc:Filter>         
        </Rule>
        <Rule>          
          <MaxScaleDenominator>1000000</MaxScaleDenominator>          
          <ogc:Filter>            
              <ogc:PropertyIsEqualTo>
                <ogc:PropertyName>frc</ogc:PropertyName>
                <ogc:Literal>2</ogc:Literal>
              </ogc:PropertyIsEqualTo>              
          </ogc:Filter>          
        </Rule>
        <Rule>          
          <MaxScaleDenominator>2000000</MaxScaleDenominator>          
          <ogc:Filter>            
              <ogc:PropertyIsEqualTo>
                <ogc:PropertyName>frc</ogc:PropertyName>
                <ogc:Literal>1</ogc:Literal>
              </ogc:PropertyIsEqualTo>              
          </ogc:Filter>           
        </Rule> 
        <Rule>
        	<ogc:Filter>            
              <ogc:PropertyIsEqualTo>
                <ogc:PropertyName>frc</ogc:PropertyName>
                <ogc:Literal>0</ogc:Literal>
              </ogc:PropertyIsEqualTo>              
          </ogc:Filter>        
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
```
In this example features with the property ```frc=0``` are never filtered. All other features containg a frc value are filtered if the requested ScaleDenominator exceeds the defined MaxScaleDenominator.
