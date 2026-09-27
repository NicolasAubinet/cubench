package com.cube.nanotimer.cube;

import static org.junit.Assert.assertEquals;

import com.cube.nanotimer.vo.CubeMethod;
import com.cube.nanotimer.vo.SolveStep;
import java.util.List;
import org.junit.Test;

/**
 * An OP/M2 capture whose second edge was shot with a slow M2: its first half came 343 ms before the
 * second, too far apart for the gyro to vouch for a slice, so the core's spin was written down between
 * them as a regrip. Read as two faces, it spelled {@code R' L M'} and left every algorithm after it a
 * quarter turn out.
 */
public class SlowSliceCaptureTest {

  private static final String MOVES =
      "[y] y@6969 B@6969 U@7769 B'@8365 U'@8646 B'@9412 F@9484 z'@9799 F@9799 B'@9820 z'@10235 "
      + "D@10235 B@10403 D'@10928 B'@13159 F'@14833 D'@15461 F@15789 D@16354 B'@16684 z'@17027 "
      + "F@17027 B'@17157 F@17187 z'@17401 U'@17401 F'@17712 U@17938 F@19673 z@30445 F'@30445 "
      + "F@32116 F'@33757 L@34450 F@34775 L'@36001 B'@36372 F@36414 z'@36415 F@36721 B'@36789 "
      + "z'@36790 R@37273 F'@37565 R'@37935 F@38403 B'@48446 F@48537 z'@48538 F@48866 B'@48894 "
      + "z'@48895 z'@56515 U'@56515 F'@56919 U@57442 B'@58101 F@58112 z'@58113 B'@58342 F@58342 "
      + "z'@58343 D'@58628 F@58846 D@59430 D@62638 B@62871 D'@63219 F@63713 B'@63713 z'@63714 "
      + "B'@63962 F@63962 z'@63963 U@64503 B'@64691 U'@64975 z2@78726 B'@78726 z'@79175 L'@79175 "
      + "B@79508 z@79734 D@79734 B'@79885 D@80011 U'@80085 y@80086 L@80222 D'@80333 L'@80534 "
      + "U@80777 D'@80795 y'@80796 L@81084 B@82122 z2@86468 U@86468 B'@86711 U'@87412 B'@87907 "
      + "F@87913 z'@87914 B'@88144 F@88160 z'@88161 D@88604 B@88865 D'@89336 D@90192 B@90347 "
      + "D'@90681 B'@91137 F@91141 z'@91142 B'@91342 F@91342 z'@91343 U@91790 B'@92067 U'@92467 "
      + "B'@121215 F@121237 z'@121238 z'@121586 D@121586 B@121949 D'@122221 B@122912 F'@122912 "
      + "z@122913 R@123337 B'@123565 R'@123781 z@129584 U'@129584 F@130149 F@130259 U@130590 "
      + "B'@131126 F@131178 z'@131179 F@131359 B'@131359 z'@131360 D'@132032 F@132633 F@132724 "
      + "D@133093 D'@134807 F'@135043 D@135347 D'@137178 F@137358 D@137643 F'@139463 D'@139962 "
      + "F@140362 D@141085 B'@141483 F@141540 z'@141541 F@141922 B'@141949 z'@141950 U'@142133 "
      + "F'@142462 U@142729 F@143148 R@154884 R@155050 y2@192054 B@192054 U'@193239 B'@193647 "
      + "U'@193810 B@194035 U@194549 B'@194973 R'@195380 B@195638 U@196152 B'@196765 U'@197123 "
      + "B'@197652 R@198819 B@199140 R@200217 R@200341 B@209818 B@210363 D'@211045 B@213280 "
      + "U'@213879 B'@214198 U'@214374 B@214587 U@214934 B'@215545 R'@215855 B@216016 U@216363 "
      + "B'@217110 U'@217271 B'@217499 R@217918 B@219330 D@219864 B'@220320 B'@220670 D'@228649 "
      + "D'@229034 B@229660 B@231603 U'@232737 B'@232978 U'@233224 B@233444 U@233648 B'@233904 "
      + "R'@234261 B@234495 U@234731 B'@234893 U'@235129 B'@235467 R@235802 B@236237 B'@238044 "
      + "D@238504 D@238723 R'@262041 B@264366 U'@265387 B'@265750 U'@265920 B@266235 U@266957 "
      + "B'@267311 R'@267682 B@267926 U@268157 B'@268993 U'@269260 B'@269478 R@269887 B@270365 "
      + "R@271025 D'@279348 B@279939 B@281156 U'@281516 B'@281712 U'@281962 B@282392 U@282790 "
      + "B'@283044 R'@283493 B@283778 U@284012 B'@284259 U'@284505 B'@284789 R@285059 z'@285390 "
      + "B@285390 z@286761 B'@286761 D@287175 B@294619 D'@295789 B@296647 U'@297030 B'@297382 "
      + "U'@297833 B@298370 U@298709 B'@298912 R'@299202 B@299468 U@299721 B'@299901 U'@300111 "
      + "B'@300373 R@300583 z'@301307 B@301307 z@303305 D@303305 B'@304174 y@316519 D'@316519 "
      + "L'@316796 D@316992 L@317173 U@317319 L'@317406 D'@317507 L@317618 D@317735 L'@317849 "
      + "D'@317980 L@318094 U'@318217 L'@318386 D@318680 L@319037 ";

  private static final String STEPS =
      "0:memo:6969:0 1:edges:97304:38875 1.0:DF-UR:800:5390 1.1:DF-UL:1674:4840 "
      + "1.2:DF-LB:14084:4646 1.3:DF-UB:10043:448 1.4:DF-FL:7621:2915 1.5:DF-FR:3208:2337 "
      + "1.6:DF-BU-DB:13751:3396 1.7:DF-BR:4346:2868 1.8:DF-FR:856:2275 1.9:DF-FU-DR:28748:2566 "
      + "1.10:DF-DL:5803:3509 1.11:DF-UL:6370:3685 2:corners:80622:95267 2.0:UBL-LUF:11736:45457 "
      + "2.1:UBL-FUR:9477:10852 2.2:UBL-LDF:7979:10074 2.3:UBL-DFL:23318:8984 "
      + "2.4:UBL-BDL:8323:7827 2.5:UBL-UBR:7444:9555 2.6:twist%3ALUB-BDR:12345:2518 ";

  @Test
  public void aSlowM2IsReadAsOne() {
    assertEquals("L' U' L U M2 U' L' U L", part(0, 1));
  }

  /** The frame it used to lose: Old Pochmann's Y perm, spelled as the solver turned it. */
  @Test
  public void theCornersAfterItAreSpelledInTheSolversGrip() {
    assertEquals("R D' R U' R' U' R U R' F' R U R' U' R' F R D R'", part(1, 5));
  }

  private static String part(int step, int part) {
    List<SolveStep> steps = SolveStepsFormat.parse(STEPS.trim());
    return SolveSolution.from(MOVES.trim(), steps, CubeMethod.BLIND).getSteps().get(step + 1)
        .getPartMoves(part);
  }
}
