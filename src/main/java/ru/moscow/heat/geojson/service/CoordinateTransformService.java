package ru.moscow.heat.geojson.service;

import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.proj4j.*;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Slf4j
@Service
public class CoordinateTransformService {

    /** WGS84 (EPSG:4326) — то, что приходит из GeoJSON. */
    private static final String WGS84 =
            "+proj=longlat +datum=WGS84 +no_defs +type=crs";

    /** UTM zone 37N (EPSG:32637) — Москва, метры. */
    private static final String UTM37N =
            "+proj=utm +zone=37 +datum=WGS84 +units=m +no_defs +type=crs";

    private CoordinateTransformation transformation;

    @PostConstruct
    void init() {
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem src = crsFactory.createFromParameters("WGS84",  WGS84);
        CoordinateReferenceSystem dst = crsFactory.createFromParameters("UTM37N", UTM37N);
        this.transformation = new CoordinateTransformFactory()
                .createTransformation(src, dst);
        log.info("Coordinate transformation WGS84 -> UTM37N initialized");
    }

    /** Возвращает НОВУЮ геометрию в EPSG:32637, исходная не меняется. */
    public Geometry toUtm37N(Geometry source) {
        if (source == null) return null;
        Geometry copy = source.copy();
        ProjCoordinate in  = new ProjCoordinate();
        ProjCoordinate out = new ProjCoordinate();
        for (Coordinate c : copy.getCoordinates()) {
            in.x = c.x;
            in.y = c.y;
            transformation.transform(in, out);
            c.x = out.x;
            c.y = out.y;
            // z оставляем как есть
        }
        copy.geometryChanged();
        copy.setSRID(32637);
        return copy;
    }
}