package com.cube.nanotimer.gui.widget.dialog;

import java.io.Serializable;
import java.util.Properties;

public interface FieldEditor extends Serializable {
  boolean editField(int position, String name, Properties props);
}
