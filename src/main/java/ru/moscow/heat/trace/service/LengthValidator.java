package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.moscow.heat.spatial.DiameterSpec;
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.trace.model.RouteSegment;

import java.util.List;
import java.util.Optional;

/**
 * Проверяет предельную допустимую длину непрерывной части сети одного ДУ
 * (план, шаг 9; ТЗ, п.2.4).
 * <p>
 * При корректной работе {@link DiameterAssigner} нарушений быть не должно
 * (он сам увеличивает ДУ при превышении) — этот валидатор служит защитной
 * проверкой перед сборкой {@code TraceResult}, а также ловит случаи, когда
 * сегменты собраны не через {@code DiameterAssigner} (например, временный
 * путь в {@code TraceOrchestrator} с diameterMm=0 — такой путь провалит
 * проверку, что ожидаемо на текущем этапе).
 */
@Service
@RequiredArgsConstructor
public class LengthValidator {

    public static final class ValidationResult {
        private final boolean valid;
        private final String violationDetails;

        public ValidationResult(boolean valid, String violationDetails) {
            this.valid = valid;
            this.violationDetails = violationDetails;
        }

        public boolean isValid() {
            return valid;
        }

        public String getViolationDetails() {
            return violationDetails;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }
    }

    private final DiameterTable diameterTable;

    /**
     * Проверяет, что для каждого непрерывного участка одного ДУ его
     * суммарная длина не превышает {@code maxLengthM} из {@link DiameterTable}.
     * Сегменты должны быть в порядке следования по маршруту (иначе
     * группировка "подряд идущих одного ДУ" не имеет смысла).
     *
     * @param segmentsInRouteOrder сегменты одного маршрута с назначенным ДУ,
     *                             упорядоченные по ходу трассы
     * @return результат проверки; при первом же нарушении возвращается сразу
     */
    public ValidationResult validate(List<RouteSegment> segmentsInRouteOrder) {
        if (segmentsInRouteOrder.isEmpty()) {
            return ValidationResult.ok();
        }

        int currentDiameter = segmentsInRouteOrder.get(0).getDiameterMm();
        double accumulatedLength = 0.0;

        for (RouteSegment segment : segmentsInRouteOrder) {
            if (segment.getDiameterMm() != currentDiameter) {
                ValidationResult check = checkGroup(currentDiameter, accumulatedLength);
                if (!check.isValid()) {
                    return check;
                }
                currentDiameter = segment.getDiameterMm();
                accumulatedLength = 0.0;
            }
            accumulatedLength += segment.getLengthM();
        }

        return checkGroup(currentDiameter, accumulatedLength);
    }

    private ValidationResult checkGroup(int diameterMm, double lengthM) {
        Optional<DiameterSpec> spec = diameterTable.findByDiameter(diameterMm);
        if (spec.isEmpty()) {
            return new ValidationResult(false,
                    "Диаметр " + diameterMm + " мм отсутствует в нормативной таблице");
        }
        if (lengthM > spec.get().getMaxLengthM()) {
            return new ValidationResult(false, String.format(
                    "Превышена предельная длина для ДУ %d мм: %.1f м > %.1f м",
                    diameterMm, lengthM, spec.get().getMaxLengthM()));
        }
        return ValidationResult.ok();
    }
}
