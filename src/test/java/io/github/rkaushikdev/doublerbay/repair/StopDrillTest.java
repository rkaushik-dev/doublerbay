package io.github.rkaushikdev.doublerbay.repair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.Refused;
import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import org.junit.jupiter.api.Test;

class StopDrillTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Crack crack(String length) {
        return new Crack(inch(length), Angle.ofDegrees(90));
    }

    @Test
    void theDrillStepsUpWithTheGauge() {
        assertThat(StopDrills.drillFor(inch("0.040"))).isEqualTo(inch("0.098"));
        assertThat(StopDrills.drillFor(inch("0.050"))).isEqualTo(inch("0.098"));
        assertThat(StopDrills.drillFor(inch("0.051"))).isEqualTo(inch("0.128"));
        assertThat(StopDrills.drillFor(inch("0.100"))).isEqualTo(inch("0.128"));
        assertThat(StopDrills.drillFor(inch("0.125"))).isEqualTo(StopDrills.HEAVY_DRILL);
    }

    @Test
    void aDrilledCrackIsOneDiameterLongerThanItWas() {
        StopDrill drilled = StopDrills.drill(crack("0.750"), inch("0.040"), inch("2.000"));
        assertThat(drilled.diameter()).isEqualTo(inch("0.098"));
        assertThat(drilled.drilledLength()).isEqualTo(inch("0.848"));
        assertThat(drilled.holes()).isEqualTo(2);
        assertThat(drilled.checkedWith()).isSameAs(InspectionMethod.HFEC);
        assertThat(drilled)
                .hasToString(
                        "stop drill 0.098 at both tips, 0.848 drilled, high frequency eddy current round each hole");
    }

    @Test
    void aCrackWithATipNobodyFoundIsNotDrilled() {
        Crack open = new Crack(inch("0.750"), Angle.ofDegrees(90), true, false, 1);
        assertThatThrownBy(() -> StopDrills.drill(open, inch("0.040"), inch("2.000")))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("tips not found");
    }

    @Test
    void aCrackIsNotDrilledTwice() {
        Crack drilled = new Crack(inch("0.750"), Angle.ofDegrees(90), true, true, 2);
        assertThatThrownBy(() -> StopDrills.drill(drilled, inch("0.040"), inch("2.000")))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("already drilled");
    }

    @Test
    void theHolesKeepTwoOfTheirOwnDiametersToTheEdge() {
        assertThat(StopDrills.drill(crack("0.500"), inch("0.040"), inch("0.196"))
                        .diameter())
                .isEqualTo(inch("0.098"));
        assertThatThrownBy(() -> StopDrills.drill(crack("0.500"), inch("0.040"), inch("0.195")))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("wants 0.196 to the edge and has 0.195");
        assertThatThrownBy(() -> StopDrills.drill(crack("0.500"), inch("0.080"), inch("0.200")))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("a 0.128 hole wants 0.256");
    }

    @Test
    void aStopDrillThatDoesNotAddUpIsRefused() {
        assertThatThrownBy(() -> new StopDrill(inch("0.098"), inch("0.750"), inch("0.750"), InspectionMethod.HFEC))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("one diameter longer");
        assertThatThrownBy(() -> new StopDrill(new Length(0), inch("0.750"), inch("0.750"), InspectionMethod.HFEC))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has a size");
        assertThatThrownBy(() -> StopDrills.drillFor(new Length(0)))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("no skin");
    }
}
