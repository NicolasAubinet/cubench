package com.cube.nanotimer.vo;

/**
 * How a blindfolded solver shoots their targets, which a smart-cube reconstruction has to be told
 * rather than guess, the same as a sighted method. Stored as its code, in the solve type's method
 * column: a blind type has no sighted method to keep there, and the codes never clash.
 */
public enum BlindMethod {
  /** Commutators: every algorithm cycles the buffer and two targets. */
  THREE_STYLE("3style"),
  /**
   * Old Pochmann and M2: every algorithm swaps the buffer with one target. Read as three-cycles
   * too, so a solver moving from one to the other is read whole.
   */
  OP_M2("OPM2");

  private final String code;

  BlindMethod(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  public static BlindMethod fromCode(String code) {
    for (BlindMethod method : values()) {
      if (method.code.equals(code)) {
        return method;
      }
    }
    return null;
  }
}
