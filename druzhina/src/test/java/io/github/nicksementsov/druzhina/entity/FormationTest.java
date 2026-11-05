package io.github.nicksementsov.druzhina.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class FormationTest {
    private static final Vec3 CENTER = new Vec3(10, 64, 20);

    @Test
    void everyManHasAPlace() {
        for (Formation f : Formation.values()) {
            for (int n = 1; n <= 30; n++) {
                assertEquals(n, f.slots(n, CENTER, 0, 0).size(), f + " " + n);
                assertEquals(n, f.ranks(n, 0).stream().mapToInt(Integer::intValue).sum(), f + " ranks " + n);
            }
        }
    }

    @Test
    void theFrontRankIsCenteredOnThePoint() {
        List<Vec3> line = Formation.LINE.slots(5, CENTER, 0, 0);
        double x = 0;
        for (int i = 0; i < 5; i++) {
            x += line.get(i).x;
            assertEquals(CENTER.z, line.get(i).z, 1.0E-9, "the first rank stands on the front");
        }
        assertEquals(CENTER.x, x / 5, 1.0E-9);
    }

    @Test
    void theRanksStandBehindTheFront() {
        // facing south (yaw 0, +z): the second rank is further north
        List<Vec3> line = Formation.LINE.slots(12, CENTER, 0, 0);
        assertEquals(List.of(8, 4), Formation.LINE.ranks(12, 0));
        for (int i = 8; i < 12; i++) {
            assertEquals(CENTER.z - Formation.RANK, line.get(i).z, 1.0E-9);
        }
        // facing east (yaw -90, +x): the ranks step back to the west
        List<Vec3> east = Formation.LINE.slots(12, CENTER, -90, 0);
        assertEquals(CENTER.x - Formation.RANK, east.get(11).x, 1.0E-6);
    }

    @Test
    void aDrawnFrontSetsTheWidth() {
        // a front drawn 7 blocks wide holds six men a rank
        assertEquals(6, Formation.files(7, 20));
        List<Vec3> line = Formation.LINE.slots(6, CENTER, 0, 7);
        assertEquals(7.0, Math.abs(line.get(5).x - line.get(0).x), 1.0E-9, "the drawn width, man to man");
        // never wider than the men there are
        assertEquals(3, Formation.files(30, 3));
    }

    @Test
    void aWedgeWidensBehindItsPoint() {
        assertEquals(List.of(1, 3, 5, 1), Formation.WEDGE.ranks(10, 0));
        List<Vec3> wedge = Formation.WEDGE.slots(10, CENTER, 0, 0);
        assertEquals(CENTER, wedge.get(0));
        assertTrue(wedge.get(1).z < CENTER.z);
    }

    @Test
    void aColumnIsNarrow() {
        assertEquals(List.of(2, 2, 2, 2, 2), Formation.COLUMN.ranks(10, 0));
        assertEquals(3, Formation.COLUMN.ranks(13, 0).get(0));
    }
}
