package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import ru.moscow.heat.spatial.ChamberCostTable;
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.spatial.GeometryUtils;
import ru.moscow.heat.trace.dto.ExistingChamberTieIn;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TraceResult;
import ru.moscow.heat.trace.dto.VariantResult;
import ru.moscow.heat.trace.dto.VariantSummary;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.trace.model.NewChamber;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VariantGeneratorTest {

    private final VariantGenerator generator = new VariantGenerator(
            null, null, null, null, null, null, null, null
    );

    @Test
    @DisplayName("Дедупликация: варианты с идентичными множествами врезок объединяются")
    void deduplicatesIdenticalTieIns() {
        ExistingChamberTieIn tieIn1 = new ExistingChamberTieIn("chamber-100", List.of("s1"));
        ExistingChamberTieIn tieIn2 = new ExistingChamberTieIn("chamber-100", List.of("s2"));

        VariantSummary s1 = new VariantSummary("v1", null, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 100.0, 1.0, List.of());
        VariantSummary s2 = new VariantSummary("v2", null, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 150.0, 2.0, List.of());

        VariantResult r1 = new VariantResult("v1", List.of(), List.of(), List.of(tieIn1), List.of(), s1);
        VariantResult r2 = new VariantResult("v2", List.of(), List.of(), List.of(tieIn2), List.of(), s2);

        List<VariantResult> deduplicated = generator.deduplicateVariants(List.of(r1, r2));

        assertThat(deduplicated).hasSize(1);
        assertThat(deduplicated.get(0).getVariantId()).isEqualTo("v1");
    }

    @Test
    @DisplayName("Дедупликация: варианты со score отличающимся менее чем на 0.1% отбрасываются")
    void deduplicatesCloseScores() {
        ExistingChamberTieIn tieIn1 = new ExistingChamberTieIn("chamber-100", List.of("s1"));
        ExistingChamberTieIn tieIn2 = new ExistingChamberTieIn("chamber-200", List.of("s2"));

        // score 1.000 и 1.0005 (отличие 0.05% < 0.1%)
        VariantSummary s1 = new VariantSummary("v1", null, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 100.0, 1.000, List.of());
        VariantSummary s2 = new VariantSummary("v2", null, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 100.0, 1.0005, List.of());

        VariantResult r1 = new VariantResult("v1", List.of(), List.of(), List.of(tieIn1), List.of(), s1);
        VariantResult r2 = new VariantResult("v2", List.of(), List.of(), List.of(tieIn2), List.of(), s2);

        List<VariantResult> deduplicated = generator.deduplicateVariants(List.of(r1, r2));

        assertThat(deduplicated).hasSize(1);
        assertThat(deduplicated.get(0).getVariantId()).isEqualTo("v1");
    }

    @Test
    @DisplayName("Дедупликация: различные варианты с разными врезками и отличием score > 0.1% сохраняются")
    void keepsDistinctVariants() {
        ExistingChamberTieIn tieIn1 = new ExistingChamberTieIn("chamber-100", List.of("s1"));
        ExistingChamberTieIn tieIn2 = new ExistingChamberTieIn("chamber-200", List.of("s2"));

        VariantSummary s1 = new VariantSummary("v1", null, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 100.0, 1.0, List.of());
        VariantSummary s2 = new VariantSummary("v2", null, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 120.0, 1.5, List.of());

        VariantResult r1 = new VariantResult("v1", List.of(), List.of(), List.of(tieIn1), List.of(), s1);
        VariantResult r2 = new VariantResult("v2", List.of(), List.of(), List.of(tieIn2), List.of(), s2);

        List<VariantResult> deduplicated = generator.deduplicateVariants(List.of(r1, r2));

        assertThat(deduplicated).hasSize(2);
        assertThat(deduplicated.get(0).getVariantId()).isEqualTo("v1");
        assertThat(deduplicated.get(1).getVariantId()).isEqualTo("v2");
    }

    @Test
    @DisplayName("Врезка группы ОКС создает платную камеру разветвления per TP 2.1 и уточнение 13")
    void testBranchingNodeCreatedAsPaidNewChamber() {
        OksConnectionPointRepository oksRepo = mock(OksConnectionPointRepository.class);
        TieInCandidateService candidateService = mock(TieInCandidateService.class);
        DiameterTable diameterTable = new DiameterTable();
        CostCalculator costCalc = new CostCalculator(diameterTable);
        ChamberCostCalculator chamberCalc = new ChamberCostCalculator(new ChamberCostTable());
        VariantScoreCalculator scoreCalc = new VariantScoreCalculator();
        VariantRanker ranker = new VariantRanker();
        ru.moscow.heat.geojson.service.CoordinateTransformService transformService = new ru.moscow.heat.geojson.service.CoordinateTransformService();
        GeometryUtils geomUtils = new GeometryUtils(transformService);
        GeometryFactory gf = new GeometryFactory();

        VariantGenerator generatorWithMocks = new VariantGenerator(
                oksRepo, candidateService, diameterTable, costCalc, chamberCalc, scoreCalc, ranker, geomUtils
        );

        UUID uploadId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();

        // 2 ОКС точки рядом (< 200 м), чтобы они сгруппировались
        OksConnectionPointEntity p1 = new OksConnectionPointEntity();
        p1.setFeatureId("oks-1");
        p1.setUploadId(uploadId);
        p1.setGeometry(gf.createPoint(new Coordinate(414000.0, 6180000.0)));
        p1.setFlowTph(25.0);

        OksConnectionPointEntity p2 = new OksConnectionPointEntity();
        p2.setFeatureId("oks-2");
        p2.setUploadId(uploadId);
        p2.setGeometry(gf.createPoint(new Coordinate(414050.0, 6180000.0)));
        p2.setFlowTph(25.0);

        when(oksRepo.findByUploadId(uploadId)).thenReturn(List.of(p1, p2));

        Point tieInPt = gf.createPoint(new Coordinate(414025.0, 6180100.0));
        TieInCandidate cand = TieInCandidate.builder()
                .id("cand-1")
                .connectionPointId("oks-1")
                .heatNetworkId("net-1")
                .type(ru.moscow.heat.trace.dto.TieInType.EXISTING_CHAMBER)
                .existingChamberId("ch-existing-1")
                .tieInPoint(tieInPt)
                .targetPoint(tieInPt)
                .cost(5_000_000L)
                .build();

        when(candidateService.findCandidatesForAllPoints(uploadId))
                .thenReturn(Map.of("oks-1", List.of(cand), "oks-2", List.of(cand)));

        TraceResult result = generatorWithMocks.generateTraceResult(uploadId, traceId);
        assertThat(result.getVariants()).isNotEmpty();

        // Ищем вариант v1 (Grouping)
        VariantResult v1 = result.getVariants().stream()
                .filter(v -> "v1".equals(v.getVariantId()))
                .findFirst()
                .orElse(null);
        assertThat(v1).isNotNull();

        // Должна быть создана разветвительная камера в allChambers
        assertThat(v1.getChambers()).isNotEmpty();
        NewChamber branchChamber = v1.getChambers().stream()
                .filter(c -> c.getId().startsWith("ch-branch-"))
                .findFirst()
                .orElse(null);
        assertThat(branchChamber).isNotNull();
        // Стоимость камеры 3..12 млн руб
        assertThat(branchChamber.getCost()).isBetween(BigDecimal.valueOf(3_000_000L), BigDecimal.valueOf(12_000_000L));
        // И эта стоимость должна входить в chamberConstructionCost сводки
        assertThat(v1.getSummary().getChamberConstructionCost()).isGreaterThanOrEqualTo(branchChamber.getCost());
    }
}
