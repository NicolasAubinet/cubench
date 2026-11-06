package com.cube.nanotimer.coach;

import com.cube.nanotimer.vo.CubeMethod;

import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

/** The coach service refuses a payload whose method is not on its list, so all must be there. */
public class CoachMethodsTest {

  @Test
  public void everyMethodIsOneTheCoachKnows() {
    for (CubeMethod method : CubeMethod.values()) {
      String code = method.getCode().toLowerCase(Locale.US);
      Assert.assertTrue(code, CoachCodes.METHODS.contains(code));
    }
  }
}
