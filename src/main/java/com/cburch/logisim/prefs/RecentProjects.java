/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.prefs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import edu.ucr.cs.riple.taint.ucrtainting.qual.RUntainted;

class RecentProjects implements PreferenceChangeListener {
  private static class FileTime {
    private final long time;
    private final File file;

    public FileTime(File file, long time) {
      this.time = time;
      this.file = file;
    }

    @Override
    public boolean equals(Object other) {
      return (other instanceof FileTime o)
             ? this.time == o.time && isSame(this.file, o.file)
             : false;
    }
  }

  private static boolean isSame(Object a, Object b) {
    return Objects.equals(a, b);
  }

  private static final String BASE_PROPERTY = "recent";

  private static final int NUM_RECENT = 10;
  private final File[] recentFiles;

  private final long[] recentTimes;

  RecentProjects() {
    recentFiles = new File[NUM_RECENT];
    recentTimes = new long[NUM_RECENT];
    Arrays.fill(recentTimes, System.currentTimeMillis());

    final java.util.prefs.Preferences prefs = AppPreferences.getPrefs();
    prefs.addPreferenceChangeListener(this);

    for (int index = 0; index < NUM_RECENT; index++) {
      getAndDecode(prefs, index);
    }
  }

  private void getAndDecode(Preferences prefs, int index) {
    final java.lang.String encoding = prefs.get(BASE_PROPERTY + index, null);
    if (encoding == null) return;
    int semi = encoding.indexOf(';');
    if (semi < 0) return;
    try {
      final long time = Long.parseLong(encoding.substring(0, semi));
      final java.io.File file = new File(encoding.substring(semi + 1));
      updateInto(index, time, file);
    } catch (NumberFormatException ignored) {
    }
  }

  public List<File> getRecentFiles() {
    final long now = System.currentTimeMillis();
    final long[] ages = new long[NUM_RECENT];
    final long[] toSort = new long[NUM_RECENT];
    for (int i = 0; i < NUM_RECENT; i++) {
      if (recentFiles[i] == null) {
        ages[i] = -1;
      } else {
        ages[i] = now - recentTimes[i];
      }
      toSort[i] = ages[i];
    }
    Arrays.sort(toSort);

    final java.util.ArrayList<java.io.File> ret = new ArrayList<File>();
    for (final long age : toSort) {
      if (age >= 0) {
        int index = -1;
        for (int i = 0; i < NUM_RECENT; i++) {
          if (ages[i] == age) {
            index = i;
            ages[i] = -1;
            break;
          }
        }
        if (index >= 0) {
          ret.add(recentFiles[index]);
        }
      }
    }
    return ret;
  }

  private int getReplacementIndex(long now, File f) {
    long oldestAge = -1;
    int oldestIndex = 0;
    int nullIndex = -1;
    for (int i = 0; i < NUM_RECENT; i++) {
      if (f.equals(recentFiles[i])) {
        return i;
      }
      if (recentFiles[i] == null) {
        nullIndex = i;
      }
      long age = now - recentTimes[i];
      if (age > oldestAge) {
        oldestIndex = i;
        oldestAge = age;
      }
    }
    if (nullIndex != -1) {
      return nullIndex;
    } else {
      return oldestIndex;
    }
  }

  @Override
  public void preferenceChange(PreferenceChangeEvent event) {
    final java.util.prefs.Preferences prefs = event.getNode();
    final java.lang.String prop = event.getKey();
    if (prop.startsWith(BASE_PROPERTY)) {
      final java.lang.String rest = prop.substring(BASE_PROPERTY.length());
      int index = -1;
      try {
        index = Integer.parseInt(rest);
        if (index < 0 || index >= NUM_RECENT) index = -1;
      } catch (NumberFormatException ignored) {
      }
      if (index >= 0) {
        final java.io.File oldValue = recentFiles[index];
        final long oldTime = recentTimes[index];
        getAndDecode(prefs, index);
        final java.io.File newValue = recentFiles[index];
        final long newTime = recentTimes[index];
        if (!isSame(oldValue, newValue) || oldTime != newTime) {
          AppPreferences.firePropertyChange(
              AppPreferences.RECENT_PROJECTS,
              new FileTime(oldValue, oldTime),
              new FileTime(newValue, newTime));
        }
      }
    }
  }

  private void updateInto(int index, long time, File file) {
    final java.io.File oldFile = recentFiles[index];
    final long oldTime = recentTimes[index];
    if (!isSame(oldFile, file) || oldTime != time) {
      recentFiles[index] = file;
      recentTimes[index] = time;
      try {
        AppPreferences.getPrefs()
            .put(BASE_PROPERTY + index, "" + time + ";" + file.getCanonicalPath());
        AppPreferences.firePropertyChange(
            AppPreferences.RECENT_PROJECTS,
            new FileTime(oldFile, oldTime),
            new FileTime(file, time));
      } catch (IOException e) {
        recentFiles[index] = oldFile;
        recentTimes[index] = oldTime;
      }
    }
  }

  public void updateRecent(File file) {
    java.io.File fileToSave = file;
    try {
      fileToSave = file.getCanonicalFile();
    } catch (IOException ignored) {
    }
    final long now = System.currentTimeMillis();
    final int index = getReplacementIndex(now, fileToSave);
    updateInto(index, now, fileToSave);
  }
}
