package com.cube.nanotimer.vo;

/** A solving method whose steps a smart cube can break a solve into. Stored as its code. */
public enum CubeMethod {
  CFOP("CFOP");

  private final String code;

  CubeMethod(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  public static CubeMethod fromCode(String code) {
    for (CubeMethod method : values()) {
      if (method.code.equals(code)) {
        return method;
      }
    }
    return null;
  }
}
