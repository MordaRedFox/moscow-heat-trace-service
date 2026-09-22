package ru.moscow.heat.spatial;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.moscow.heat.geojson.entity.*;
import ru.moscow.heat.geojson.repository.*;

import java.util.*;

/**
 * Сервис валидации консистентности загруженного набора геоданных.
 * Выполняет комплексную проверку сессии загрузки перед началом трассировки:
 * - Критические ошибки (блокируют трассировку: valid = false);
 * - Предупреждения (фиксируют некритичные отклонения: valid = true)
 */
@Service
public class UploadConsistencyValidator {

    private final SourceRepository sourceRepo;
    private final HeatNetworkRepository heatNetworkRepo;
    private final HeatChamberRepository heatChamberRepo;
    private final OksConnectionPointRepository oksCpRepo;
    private final RestrictionRepository restrictionRepo;
    private final OksConnectionPointResolver oksResolver;
    private final RestrictionRuleRegistry restrictionRegistry;

    public UploadConsistencyValidator(SourceRepository sourceRepo,
                                      HeatNetworkRepository heatNetworkRepo,
                                      HeatChamberRepository heatChamberRepo,
                                      OksConnectionPointRepository oksCpRepo,
                                      RestrictionRepository restrictionRepo,
                                      OksConnectionPointResolver oksResolver,
                                      RestrictionRuleRegistry restrictionRegistry) {
        this.sourceRepo = Objects.requireNonNull(sourceRepo);
        this.heatNetworkRepo = Objects.requireNonNull(heatNetworkRepo);
        this.heatChamberRepo = Objects.requireNonNull(heatChamberRepo);
        this.oksCpRepo = Objects.requireNonNull(oksCpRepo);
        this.restrictionRepo = Objects.requireNonNull(restrictionRepo);
        this.oksResolver = Objects.requireNonNull(oksResolver);
        this.restrictionRegistry = Objects.requireNonNull(restrictionRegistry);
    }

    /**
     * Выполняет проверку консистентности для указанной сессии загрузки
     * @param uploadId идентификатор сессии
     * @return отчет о валидации ConsistencyReport
     */
    @Transactional(readOnly = true)
    public ConsistencyReport validate(UUID uploadId) {
        if (uploadId == null) {
            return new ConsistencyReport(List.of(
                "Идентификатор загрузки uploadId не может быть null"), List.of());
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<SourceEntity> sources = sourceRepo.findByUploadId(uploadId);
        List<HeatNetworkEntity> heatNetworks = heatNetworkRepo.findByUploadId(uploadId);
        List<HeatChamberEntity> heatChambers = heatChamberRepo.findByUploadId(uploadId);
        List<OksConnectionPointEntity> oksPoints = oksCpRepo.findByUploadId(uploadId);
        List<RestrictionEntity> restrictions = restrictionRepo.findByUploadId(uploadId);

        // 1. Проверка ошибок: ровно один source
        if (sources.isEmpty()) {
            errors.add("Набор данных должен содержать ровно один источник (source), обнаружено: 0");
        } else if (sources.size() > 1) {
            errors.add("Набор данных должен содержать ровно один источник (source), обнаружено: " + sources.size());
        }

        // 2. Проверка ошибок: хотя бы одна oks_connection_point
        if (oksPoints.isEmpty()) {
            errors.add("Набор данных не содержит ни одной точки подключения ОКС (oks_connection_point)");
        }

        // 3. Проверка ошибок: у каждой точки flow_tph > 0
        for (OksConnectionPointEntity pt : oksPoints) {
            if (pt.getFlowTph() == null || pt.getFlowTph() <= 0.0) {
                errors.add(String.format("Точка подключения ОКС (id=%s) имеет некорректный расход flow_tph: %s",
                        pt.getFeatureId(), pt.getFlowTph()));
            }
        }

        // 4. Проверка ошибок: у каждого heat_network diameter задан и > 0
        for (HeatNetworkEntity net : heatNetworks) {
            if (net.getDiameter() == null || net.getDiameter() <= 0) {
                errors.add(String.format("Участок существующей тепловой сети (id=%s) имеет некорректный условный диаметр: %s",
                        net.getFeatureId(), net.getDiameter()));
            }
        }

        // 5. Проверка ошибок: все feature_id уникальны в пределах upload
        Set<String> seenFeatureIds = new HashSet<>();
        Set<String> duplicateFeatureIds = new LinkedHashSet<>();

        checkDuplicates(sources, seenFeatureIds, duplicateFeatureIds);
        checkDuplicates(heatNetworks, seenFeatureIds, duplicateFeatureIds);
        checkDuplicates(heatChambers, seenFeatureIds, duplicateFeatureIds);
        checkDuplicates(oksPoints, seenFeatureIds, duplicateFeatureIds);
        checkDuplicates(restrictions, seenFeatureIds, duplicateFeatureIds);

        if (!duplicateFeatureIds.isEmpty()) {
            errors.add("Обнаружены неуникальные feature_id в пределах загрузки: " + duplicateFeatureIds);
        }

        // 6. Проверка предупреждений: нет ни одной heat_chamber
        if (heatChambers.isEmpty()) {
            warnings.add("В наборе данных отсутствуют существующие тепловые камеры (heat_chamber). Все врезки потребуют строительства новых камер.");
        }

        // 7. Проверка предупреждений: точки подключения не привязаны ни к одному полигону ОКС
        List<String> unboundPoints = oksResolver.findUnboundConnectionPoints(uploadId);
        if (!unboundPoints.isEmpty()) {
            warnings.add("Точки подключения ОКС не входят в полигоны ОКС (restriction_type='oks'): " + unboundPoints);
        }

        // 8. Проверка предупреждений: restriction с неизвестным restriction_type (игнорируются трассировкой)
        for (RestrictionEntity restr : restrictions) {
            String type = restr.getRestrictionType();
            if (type == null || restrictionRegistry.ruleFor(type).isEmpty()) {
                warnings.add(String.format("Ограничение (id=%s) имеет неподдерживаемый тип '%s' и будет проигнорировано при трассировке",
                        restr.getFeatureId(), type));
            }
        }

        return new ConsistencyReport(errors, warnings);
    }

    private void checkDuplicates(List<? extends AbstractGeoObject> objects,
                                 Set<String> seen,
                                 Set<String> duplicates) {
        for (AbstractGeoObject obj : objects) {
            String fid = obj.getFeatureId();
            if (fid != null && !seen.add(fid)) {
                duplicates.add(fid);
            }
        }
    }
}
