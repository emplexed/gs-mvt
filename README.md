# Geoserver MVT Extension

## Overview
This extension for the **Geoserver** adds the possibility to deliver [Mapnik Vector Tiles](https://github.com/mapbox/mapnik-vector-tile/) in Protocol Buffers outputformat as result of an **WMS** or **Slippy Map Tile** request. It has been developed and tested with [Geoserver 2.6.2](http://geoserver.org) but might also work with preceding versions.
The resulting Vector Tiles can e.g. be rendered by WebGL JS clients like [Mapbox GL JS](https://www.mapbox.com/mapbox-gl-js/api/) or [Tangram](https://github.com/tangrams/tangram).

## Getting Started
For building the plugin the geoserver source code is required. It is available at the [boundless maven repository](http://repo.boundlessgeo.com/main/) or on [GitHub](https://github.com/geoserver).
In order to get started the result of the maven build ```gs-mvt-2.7.0.jar``` and the depending library ```protobuf-java-2.6.1.jar``` have to be copied to the geoserver's lib directory ```geoserver/WEB-INF/lib```. After starting the GeoServer the format ```application/x-protobuf``` is shown in the WMS Format list of the layer preview.

## WMS Query
Mandatory for the WMS **getMap** request are the ```bbox```, ```witdh``` and ```height``` parameters. The ```bbox``` describes the requested source coordinate system. The ```width``` and ```height``` the target coordinate system (tile local). The MVT format uses a local coordinate system within each tile. By default this is 0 to 256 on x and y axis. An example WMS Request could be: 
```
http://localhost/geoserver/test/wms?STYLES=&LAYERS=test:streetsegments&FORMAT=application/x-protobuf&SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&SRS=EPSG:4326&bbox=9.499057554653266,46.410057509506515,17.1504898071289,49.0165481567383&WIDTH=256&HEIGHT=256
```
As result all features of the requested clip are returned. Additionally a [Douglas-Peucker algorithm](http://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm) simplifies these geometries. By default the algorithm accepts a maximum distance of ```0.1``` in target system units.

## Slippy Map Tiles Request
[Slippy Map Tiles](http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames) describes the tile format used by Google, Bing and OSM. Usually a tile cache delivers the result to the client. But the **GeoWebCache** used by the **Geoserver** does not allow to be extended with additional MimeTypes (e.g. ```application/x-protobuf```) without a change to core classes. Therefore a basic Slippy Map Tile Controller has been provided with this extension, that translates the slippy map tile names into a WMS redirect. The Slippy Tile request has the following format:
```
http://localhost/geoserver/slippymap/{layers}/{z}/{x}/{y}.{format}
```
Path Variable | Description | Type
------------- | ----------- | -------
*layers* | layer or layer names | String or comma separated string list
*z* | zoom level | integer
*x* | tile column | integer
*y* | tile row | integer
*format* | output format | one of pbf,png,gif,jpg,tif,pdf,rss,kml,kmz

To adjust the WMS parameters the following Request Parameters (in query String) are additionally allowed:

Request Parameter | Description | Type
----------------- | ----------- | -----
*buffer* | buffer size | integer default 10
*tileSize* | size of result coordinate system (width and height) | integer default 256
*styles* | used styles for the layer(s) | String or comma separated Strings if more than one layer
*time* | translated to WMS time parameter | Date String
*sld* | External Style Sheed Descriptor | URL (Location of SLD to be used)
*sld_body* | Style Description | SLD XML

### Example:
```
http://localhost/geoserver/slippymap/streets/13/4390/2854.pbf?buffer=5&styles=line
```

##Styling
Since the vector features are usualy styled on client side only the filter rules and not the symbolizers of a **GeoServer Style Sheet (SLD)** are considered. Using scale denominators the filter rules can be adjusted so that for example some features are not delivered at higher zoom levels. This prevents unnecessary delivery of features that are not rendered on the client anyway.

Following example shows an GeoServer Style Sheet (SLD) filtering features by a frc value.

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
In this example features with the property ```frc=0``` are never filtered. All other features containing a frc value are filtered if the requested **ScaleDenominator** exceeds the defined **MaxScaleDenominator**.
