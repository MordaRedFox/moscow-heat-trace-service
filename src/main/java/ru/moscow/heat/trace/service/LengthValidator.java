package ru.moscow.heat.trace.service;

import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.model.RouteSegment;

import java.util.List;

/**
 * Проверяет предельную допустимую длину непрерывной части сети одного ДУ
 * (план, шаг 9; ТЗ, п.2.4).
 * <p>
 * Разъяснение №2: проверяется отдельно по каждому непрерывному пути,
 * общий участок учитывается в каждом пути, длины параллельных ветвей
 * не суммируются. В MVP итерации 5 (без объединения ОКС) — один путь
 * на ОКС, поэтому проверяется просто сумма длин между сменами ДУ на
 * маршруте этого ОКС.
 */
@Service
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

    /**
     * Проверяет, что для каждого непрерывного участка одного ДУ его
     * суммарная длина не превышает maxLengthM из {@code DiameterTable}.
     * <p>
     * При корректной работе {@code DiameterAssigner} нарушений быть не
     * должно (он сам увеличивает ДУ при превышении) — этот валидатор
     * служит защитной проверкой перед сборкой {@code TraceResult}.
     *
     * @param segmentsWithDiameter сегменты одного маршрута с уже назначенным ДУ
     * @return результат проверки
     */
    public ValidationResult validate(List<RouteSegment> segmentsWithDiameter) {
        // TODO: сгруппировать подряд идущие сегменты с одинаковым diameterMm,
        // просуммировать lengthM, сравнить с DiameterTable.maxLengthM(diameter).
        throw new UnsupportedOperationException("TODO: итерация 5, шаг 9");
    }
}
