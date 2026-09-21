package ru.moscow.heat.geojson.service;

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.stereotype.Service;

/**
 * Трансформация координат между WGS 84 (EPSG:4326) и UTM zone 37N (EPSG:32637)
 * на базе Proj4J. Все метрические расчеты (длины, расстояния, буферы)
 * выполняются в EPSG:32637 согласно техническому приложению
 */
@Service
public class CoordinateTransformService {

    public static final int SRID_WGS84 = 4326;
    public static final int SRID_UTM_37N = 32637;

    private final CoordinateTransform toUtmTransform;
    private final CoordinateTransform toWgs84Transform;

    public CoordinateTransformService() {
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem wgs84 = crsFactory.createFromName("EPSG:4326");
        CoordinateReferenceSystem utm37n = crsFactory.createFromName("EPSG:32637");
        CoordinateTransformFactory transformFactory = new CoordinateTransformFactory();
        this.toUtmTransform = transformFactory.createTransform(wgs84, utm37n);
        this.toWgs84Transform = transformFactory.createTransform(utm37n, wgs84);
    }

    /**
     * Трансформирует геометрию из EPSG:4326 в EPSG:32637,
     * сохраняя тип геометрии и проставляя SRID 32637
     */
    public Geometry toUtm(Geometry wgs84Geometry) {
        return transform(wgs84Geometry, toUtmTransform, SRID_UTM_37N);
    }

    /**
     * Обратная трансформация из EPSG:32637 в EPSG:4326 (SRID 4326)
     */
    public Geometry toWgs84(Geometry utmGeometry) {
        return transform(utmGeometry, toWgs84Transform, SRID_WGS84);
    }

    /**
     * Трансформация одиночной точки
     * @param lon долгота, град (EPSG:4326)
     * @param lat широта, град (EPSG:4326)
     * @return массив {easting, northing} в метрах EPSG:32637
     */
    public double[] transformPoint(double lon, double lat) {
        ProjCoordinate dst = new ProjCoordinate();
        toUtmTransform.transform(new ProjCoordinate(lon, lat), dst);
        return new double[]{dst.x, dst.y};
    }

    private Geometry transform(Geometry source, CoordinateTransform transform,
                                int targetSrid) {
        Geometry copy = source.copy();
        copy.apply(new CoordinateSequenceFilter() {
            @Override
            public void filter(CoordinateSequence seq, int i) {
                ProjCoordinate src = new ProjCoordinate(
                    seq.getX(i), seq.getY(i));
                ProjCoordinate dst = new ProjCoordinate();
                transform.transform(src, dst);
                seq.setOrdinate(i, CoordinateSequence.X, dst.x);
                seq.setOrdinate(i, CoordinateSequence.Y, dst.y);
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public boolean isGeometryChanged() {
                return true;
            }
        });
        copy.setSRID(targetSrid);
        return copy;
    }
}
