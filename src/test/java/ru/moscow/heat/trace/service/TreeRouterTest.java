package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.service.CoordinateTransformService;
import ru.moscow.heat.spatial.OksConnectionPointResolver;
import ru.moscow.heat.trace.model.RouteTree;
import ru.moscow.heat.trace.model.TreeNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты {@link TreeRouter} (итерация 6, шаг 2).
 *
 * <p>Проверяется чистая логика объединения путей в дерево через
 * {@link TreeRouter#buildTreeFromPaths} — без реального visibility
 * graph и без A*.
 *
 * <p>Расход ОКС в построении дерева не участвует — он агрегируется
 * отдельно {@code FlowAggregator}. В хелпере {@code oks()} подменяется
 * только {@code getFeatureId()}, чтобы не было лишних mock-stub'ов.
 */
@ExtendWith(MockitoExtension.class)
class TreeRouterTest {

    @Mock
    private OksConnectionPointResolver oksResolver;

    @Mock
    private CoordinateTransformService transformService;

    private TreeRouter treeRouter;

    @BeforeEach
    void setUp() {
        // RouteSimplifier не имеет зависимостей и не вызывается
        // в buildTreeFromPaths — передаём реальный экземпляр.
        treeRouter = new TreeRouter(oksResolver, transformService,
                new RouteSimplifier());
    }

    private OksConnectionPointEntity oks(String featureId) {
        OksConnectionPointEntity entity = mock(OksConnectionPointEntity.class);
        when(entity.getFeatureId()).thenReturn(featureId);
        return entity;
    }

    @Test
    @DisplayName("3 ОКС с общим стволом → 1 точка ветвления")
    void threeOksSharedTrunk_oneBranchNode() {
        Coordinate tieIn = new Coordinate(0, 0);
        Coordinate branch = new Coordinate(100, 0);
        Coordinate oks1 = new Coordinate(100, 100);
        Coordinate oks2 = new Coordinate(200, 0);
        Coordinate oks3 = new Coordinate(100, -100);

        List<List<Coordinate>> paths = List.of(
                List.of(tieIn, branch, oks1),
                List.of(tieIn, branch, oks2),
                List.of(tieIn, branch, oks3)
        );
        List<OksConnectionPointEntity> oksList = List.of(
                oks("1"), oks("2"), oks("3"));

        RouteTree tree = treeRouter.buildTreeFromPaths(paths, oksList, tieIn);

        assertThat(tree.getOksCount()).isEqualTo(3);
        assertThat(tree.getLeaves()).hasSize(3);
        assertThat(tree.getBranchingNodes()).hasSize(1);

        TreeNode branchNode = tree.getBranchingNodes().get(0);
        assertThat(branchNode.getChildCount()).isEqualTo(3);
        assertThat(branchNode.getCoordinateUtm().distance(branch))
                .isLessThan(1.0);

        assertThat(tree.getRoot().getChildCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("2 ОКС без общего ствола → корень является ветвлением")
    void twoOksNoSharedTrunk_rootIsBranch() {
        Coordinate tieIn = new Coordinate(0, 0);
        Coordinate oks1 = new Coordinate(100, 100);
        Coordinate oks2 = new Coordinate(-100, 100);

        List<List<Coordinate>> paths = List.of(
                List.of(tieIn, oks1),
                List.of(tieIn, oks2)
        );
        List<OksConnectionPointEntity> oksList = List.of(oks("1"), oks("2"));

        RouteTree tree = treeRouter.buildTreeFromPaths(paths, oksList, tieIn);

        assertThat(tree.getOksCount()).isEqualTo(2);
        assertThat(tree.getRoot().isBranching()).isTrue();
        assertThat(tree.getRoot().getChildCount()).isEqualTo(2);
        assertThat(tree.getBranchingNodes()).hasSize(1);
    }

    @Test
    @DisplayName("1 ОКС → вырожденное дерево без ветвлений")
    void singleOks_noBranching() {
        Coordinate tieIn = new Coordinate(0, 0);
        Coordinate oks1 = new Coordinate(100, 0);

        List<List<Coordinate>> paths = List.of(List.of(tieIn, oks1));
        List<OksConnectionPointEntity> oksList = List.of(oks("1"));

        RouteTree tree = treeRouter.buildTreeFromPaths(paths, oksList, tieIn);

        assertThat(tree.getOksCount()).isEqualTo(1);
        assertThat(tree.getBranchingNodes()).isEmpty();
        assertThat(tree.getRoot().getChildCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Суммарная длина дерева корректна")
    void totalLength_correct() {
        Coordinate tieIn = new Coordinate(0, 0);
        Coordinate oks1 = new Coordinate(100, 0);

        List<List<Coordinate>> paths = List.of(List.of(tieIn, oks1));
        List<OksConnectionPointEntity> oksList = List.of(oks("1"));

        RouteTree tree = treeRouter.buildTreeFromPaths(paths, oksList, tieIn);

        assertThat(tree.getTotalLengthM())
                .isCloseTo(100.0,
                        org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("Частичный общий участок → ветвление в точке расхождения")
    void partialSharedPrefix_branchAtDivergence() {
        Coordinate tieIn = new Coordinate(0, 0);
        Coordinate mid = new Coordinate(50, 0);
        Coordinate oks1 = new Coordinate(100, 50);
        Coordinate oks2 = new Coordinate(100, -50);

        List<List<Coordinate>> paths = List.of(
                List.of(tieIn, mid, oks1),
                List.of(tieIn, mid, oks2)
        );
        List<OksConnectionPointEntity> oksList = List.of(oks("1"), oks("2"));

        RouteTree tree = treeRouter.buildTreeFromPaths(paths, oksList, tieIn);

        assertThat(tree.getOksCount()).isEqualTo(2);
        assertThat(tree.getBranchingNodes()).hasSize(1);

        TreeNode branchNode = tree.getBranchingNodes().get(0);
        assertThat(branchNode.getCoordinateUtm().distance(mid))
                .isLessThan(1.0);
        assertThat(branchNode.getChildCount()).isEqualTo(2);
        assertThat(tree.getRoot().getChildCount()).isEqualTo(1);
    }
}
