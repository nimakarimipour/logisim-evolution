/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.prefs;

import java.util.Objects;
import java.util.prefs.PreferenceChangeEvent;

class PrefMonitorStringOpts extends AbstractPrefMonitor<String> {
  private static boolean isSame(String a, String b) {
    return Objects.equals(a, b);
  }

  private final String[] opts;
  private String value;

  private final String dflt;

  public PrefMonitorStringOpts(String name, String[] opts, String dflt) {
    super(name);
    this.opts = opts;
    this.value = opts[0];
    this.dflt = dflt;
    final java.util.prefs.Preferences prefs = AppPreferences.getPrefs();
    set(prefs.get(name, dflt));
    prefs.addPreferenceChangeListener(this);
  }

  public String get() {
    return value;
  }

  public void preferenceChange(PreferenceChangeEvent event) {
    final java.util.prefs.Preferences prefs = event.getNode();
    final java.lang.String prop = event.getKey();
    final java.lang.String name = getIdentifier();
    if (prop.equals(name)) {
      final java.lang.String oldValue = value;
      final java.lang.String newValue = prefs.get(name, dflt);
      if (!isSame(oldValue, newValue)) {
        String chosen = null;
        for (final java.lang.String s : opts) {
          if (isSame(s, newValue)) {
            chosen = s;
            break;
          }
        }
        if (chosen == null) chosen = dflt;
        value = chosen;
        AppPreferences.firePropertyChange(name, oldValue, chosen);
      }
    }
  }

  public void set(String newValue) {
    final java.lang.String oldValue = value;
    if (!isSame(oldValue, newValue)) {
      AppPreferences.getPrefs().put(getIdentifier(), newValue);
    }
  }
}
