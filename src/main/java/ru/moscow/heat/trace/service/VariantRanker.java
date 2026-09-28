package ru.moscow.heat.trace.service;

import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.dto.VariantResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Сервис ранжирования вариантов трассировки.
 * Сортирует список вариантов по возрастанию интегрального балла (score)
 * и присваивает ранги (1, 2, 3...).
 */
@Service
public class VariantRanker {

    private static final Comparator<VariantResult> SCORE_COMPARATOR =
            Comparator.comparingDouble(v -> v.getSummary() != null ? v.getSummary().getScore() : Double.MAX_VALUE);

    /**
     * Ранжирует варианты: сортирует по возрастанию score и проставляет rank от 1.
     *
     * @param variants исходный список вариантов
     * @return отсортированный и пронумерованный список вариантов
     */
    public List<VariantResult> rankVariants(List<VariantResult> variants) {
        if (variants == null || variants.isEmpty()) {
            return List.of();
        }

        List<VariantResult> sorted = new ArrayList<>(variants);
        sorted.sort(SCORE_COMPARATOR);

        List<VariantResult> ranked = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            ranked.add(sorted.get(i).withRank(i + 1));
        }
        return ranked;
    }
}
