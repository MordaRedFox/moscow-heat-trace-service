package ru.moscow.heat.spatial;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

/**
 * Неизменяемый отчет о валидации консистентности набора данных загрузки.
 * Java 11: final поля, геттеры, unmodifiable списки, equals/hashCode/toString
 */
@Getter
@ToString
@EqualsAndHashCode
public final class ConsistencyReport {

    /** Флаг валидности: true тогда и только тогда, когда список ошибок пуст */
    private final boolean valid;

    /** Список критических ошибок, препятствующих трассировке */
    private final List<String> errors;

    /** Список предупреждений, не блокирующих трассировку */
    private final List<String> warnings;

    public ConsistencyReport(List<String> errors, List<String> warnings) {
        List<String> errList = errors != null ? List.copyOf(errors) : Collections.emptyList();
        List<String> warnList = warnings != null ? List.copyOf(warnings) : Collections.emptyList();
        this.errors = errList;
        this.warnings = warnList;
        this.valid = errList.isEmpty();
    }
}
