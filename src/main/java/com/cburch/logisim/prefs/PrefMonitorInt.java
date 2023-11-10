/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.prefs;

import java.util.prefs.PreferenceChangeEvent;

class PrefMonitorInt extends AbstractPrefMonitor<Integer> {
  private final int dflt;
  private int value;

  public PrefMonitorInt(String name, int dflt) {
    super(name);
    this.dflt = dflt;
    this.value = dflt;
    final java.util.prefs.Preferences prefs = AppPreferences.getPrefs();
    set(prefs.getInt(name, dflt));
    prefs.addPreferenceChangeListener(this);
  }

  public Integer get() {
    return value;
  }

  public void preferenceChange(PreferenceChangeEvent event) {
    final java.util.prefs.Preferences prefs = event.getNode();
    final java.lang.String prop = event.getKey();
    final java.lang.String name = getIdentifier();
    if (prop.equals(name)) {
      final int oldValue = value;
      final int newValue = prefs.getInt(name, dflt);
      if (newValue != oldValue) {
        value = newValue;
        AppPreferences.firePropertyChange(name, oldValue, newValue);
      }
    }
  }

  public void set(Integer newValue) {
    final java.lang.Integer newVal = newValue;
    if (value != newVal) {
      AppPreferences.getPrefs().putInt(getIdentifier(), newVal);
    }
  }
}
