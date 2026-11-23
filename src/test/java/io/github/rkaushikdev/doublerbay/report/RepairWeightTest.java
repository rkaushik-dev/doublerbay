package io.github.rkaushikdev.doublerbay.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.repair.Schemes;
import io.github.rkaushikdev.doublerbay.structure.Family;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepairWeightTest {

    private static final LocalDate EMBODIED = LocalDate.of(2025, 4, 2);

    private static RepairRecord repair(int sequence, String size) {
        Placement placed = Placement.place(Sample.tailContext(), Sample.card(Sample.cabinPointOn(10, "4.000")));
        return new RepairRecord(
                new RepairId(53, sequence),
                new PanelId(53, 10, 143, "L"),
                new FindingId(2025, sequence),
                Schemes.selectScheme(Sample.inch(size), Sample.inch(size), placed, Reference.parse("53-40-01")),
                new Offset(Sample.inch("468.000"), Sample.inch("91.000")),
                EMBODIED,
                UtilisationLog.usage(13900, 24400));
    }

    @Test
    void theSeriesCarryTheirHandbookDensities() {
        assertThat(Family.TWO_THOUSAND.density()).isEqualByComparingTo("0.100");
        assertThat(Family.SEVEN_THOUSAND.density()).isEqualByComparingTo("0.101");
    }

    @Test
    void theFittedRepairsAddUpPlateByPlate() {
        RepairRecord small = repair(1, "2.000");
        RepairRecord large = repair(2, "6.000");
        RepairWeight weight = RepairWeight.of(List.of(small, large));
        assertThat(weight.fitted()).isEqualTo(2);
        assertThat(weight.pounds())
                .isEqualByComparingTo(small.scheme()
                        .doubler()
                        .weight()
                        .add(large.scheme().doubler().weight()));
        assertThat(weight.pounds()).isPositive();
        assertThat(weight.needsWeighing()).isFalse();
        assertThat(weight.toString()).startsWith("2 fitted repairs weigh ").endsWith(" lb");
    }

    @Test
    void aRepairTakenOffTookItsWeightWithIt() {
        RepairRecord off = repair(1, "2.000").removedOn(EMBODIED.plusDays(30));
        RepairWeight weight = RepairWeight.of(List.of(off, repair(2, "2.000")));
        assertThat(weight.fitted()).isEqualTo(1);
        assertThat(weight.pounds())
                .isEqualByComparingTo(repair(2, "2.000").scheme().doubler().weight());
        assertThat(RepairWeight.of(List.of()).pounds()).isEqualByComparingTo("0");
    }

    @Test
    void tenPoundsOfRepairsCallsForTheTailToBeWeighedAgain() {
        assertThat(new RepairWeight(40, new BigDecimal("9.999")).needsWeighing())
                .isFalse();
        RepairWeight due = new RepairWeight(41, RepairWeight.WEIGH_AGAIN_AT);
        assertThat(due.needsWeighing()).isTrue();
        assertThat(due).hasToString("41 fitted repairs weigh 10.000 lb, weigh the tail again");
        assertThatThrownBy(() -> new RepairWeight(-1, BigDecimal.ONE))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("counts what is there");
    }
}
