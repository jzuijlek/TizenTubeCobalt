// Copyright 2026 The Cobalt Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cobalt.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks a {@code Display.Mode} id for a video frame from the modes the connected display
 * advertised. Mode ids and resolutions come from the caller. Nothing here is hard-coded to a panel.
 *
 * <p>A mode covers a frame when each side is at least the frame, allowing Kodi's {@code
 * MatchesMode} slack: 8px on width, 32px on height, and at least 98% of that side. A larger mode
 * also covers. Among covering modes the smallest area wins. HDR never changes the chosen size or
 * refresh rate.
 */
public final class DisplayModeMatcher {
  public static final int MIN_SHORT_SIDE = 720;
  public static final int WIDTH_SLACK_PX = 8;
  public static final int HEIGHT_SLACK_PX = 32;
  public static final float MIN_SIDE_RATIO = 0.98f;
  public static final float RATE_EPSILON_HZ = 0.1f;

  /** One advertised mode. {@code hdrTypes} is null when per-mode HDR info is unavailable. */
  public static final class Mode {
    public final int id;
    public final int width;
    public final int height;
    public final float refreshRate;
    public final int[] hdrTypes;

    public Mode(int id, int width, int height, float refreshRate, int[] hdrTypes) {
      this.id = id;
      this.width = width;
      this.height = height;
      this.refreshRate = refreshRate;
      this.hdrTypes = hdrTypes;
    }

    public long area() {
      return (long) width * (long) height;
    }
  }

  /** The chosen id, plus whether that size does not list the requested HDR type. */
  public static final class Selection {
    public final int modeId;
    public final boolean sizeMissesHdr;

    public Selection(int modeId, boolean sizeMissesHdr) {
      this.modeId = modeId;
      this.sizeMissesHdr = sizeMissesHdr;
    }
  }

  private DisplayModeMatcher() {}

  /**
   * Returns the mode id to set, or {@code 0} to clear {@code preferredDisplayModeId}. A non-positive
   * width or height clears. {@code currentModeId} wins ties.
   */
  public static int choose(
      List<Mode> modes, int width, int height, float frameRate, int hdrType, int currentModeId) {
    return select(modes, width, height, frameRate, hdrType, currentModeId).modeId;
  }

  public static Selection select(
      List<Mode> modes, int width, int height, float frameRate, int hdrType, int currentModeId) {
    if (width <= 0 || height <= 0) {
      return new Selection(0, false);
    }

    int frameWidth = width;
    int frameHeight = height;
    if (Math.min(frameWidth, frameHeight) < MIN_SHORT_SIDE) {
      frameWidth = 1280;
      frameHeight = 720;
    }

    List<Mode> eligible = new ArrayList<>();
    boolean hdrInfoPresent = false;
    if (modes != null) {
      for (Mode mode : modes) {
        if (mode == null || Math.min(mode.width, mode.height) < MIN_SHORT_SIDE) {
          continue;
        }
        eligible.add(mode);
        if (mode.hdrTypes != null) {
          hdrInfoPresent = true;
        }
      }
    }
    if (eligible.isEmpty()) {
      return new Selection(0, false);
    }

    List<Mode> covering = new ArrayList<>();
    for (Mode mode : eligible) {
      if (covers(mode, frameWidth, frameHeight)) {
        covering.add(mode);
      }
    }

    List<Mode> sizePool = covering.isEmpty() ? eligible : covering;
    long targetArea = covering.isEmpty() ? Long.MIN_VALUE : Long.MAX_VALUE;
    for (Mode mode : sizePool) {
      long area = mode.area();
      if (covering.isEmpty()) {
        if (area > targetArea) {
          targetArea = area;
        }
      } else if (area < targetArea) {
        targetArea = area;
      }
    }

    List<Mode> anchors = new ArrayList<>();
    for (Mode mode : sizePool) {
      if (mode.area() == targetArea) {
        anchors.add(mode);
      }
    }
    List<Mode> group = new ArrayList<>();
    for (Mode mode : eligible) {
      for (Mode anchor : anchors) {
        if (sameSize(anchor, mode)) {
          group.add(mode);
          break;
        }
      }
    }
    if (group.isEmpty()) {
      return new Selection(0, false);
    }

    List<Mode> rated = pickRate(group, frameRate);
    boolean sizeHasHdr = false;
    if (hdrType != 0 && hdrInfoPresent) {
      for (Mode mode : group) {
        if (containsHdr(mode.hdrTypes, hdrType)) {
          sizeHasHdr = true;
          break;
        }
      }
    }
    List<Mode> finalists = rated;
    if (hdrType != 0 && hdrInfoPresent) {
      List<Mode> hdrMatches = new ArrayList<>();
      for (Mode mode : rated) {
        if (containsHdr(mode.hdrTypes, hdrType)) {
          hdrMatches.add(mode);
        }
      }
      if (!hdrMatches.isEmpty()) {
        finalists = hdrMatches;
      }
    }
    boolean sizeMissesHdr = hdrType != 0 && hdrInfoPresent && !sizeHasHdr;
    return new Selection(selectId(finalists, currentModeId), sizeMissesHdr);
  }

  static boolean sameSize(Mode a, Mode b) {
    return dimsMatch(a.width, a.height, b.width, b.height)
        || dimsMatch(a.width, a.height, b.height, b.width);
  }

  private static boolean covers(Mode mode, int frameWidth, int frameHeight) {
    return coversOriented(mode.width, mode.height, frameWidth, frameHeight)
        || coversOriented(mode.width, mode.height, frameHeight, frameWidth);
  }

  private static boolean coversOriented(int modeWidth, int modeHeight, int frameWidth, int frameHeight) {
    return sideCovers(modeWidth, frameWidth, WIDTH_SLACK_PX)
        && sideCovers(modeHeight, frameHeight, HEIGHT_SLACK_PX);
  }

  /** The mode side is at least the frame side, or shorter only by the slack and the 98% floor. */
  private static boolean sideCovers(int modeSide, int frameSide, int slack) {
    if (frameSide <= 0 || modeSide <= 0) {
      return false;
    }
    return modeSide + slack >= frameSide && modeSide >= frameSide * MIN_SIDE_RATIO;
  }

  private static boolean dimsMatch(int aw, int ah, int bw, int bh) {
    return sideClose(aw, bw, WIDTH_SLACK_PX) && sideClose(ah, bh, HEIGHT_SLACK_PX);
  }

  private static boolean sideClose(int a, int b, int slack) {
    int larger = Math.max(a, b);
    int smaller = Math.min(a, b);
    return larger - smaller <= slack && (larger == 0 || smaller >= larger * MIN_SIDE_RATIO);
  }

  private static List<Mode> pickRate(List<Mode> group, float frameRate) {
    List<Mode> exact = closest(within(group, frameRate), frameRate);
    if (!exact.isEmpty()) {
      return exact;
    }
    double doubledRate = frameRate * 2.0;
    List<Mode> doubled = closest(within(group, doubledRate), doubledRate);
    if (!doubled.isEmpty()) {
      return doubled;
    }
    double pulldownRate = frameRate * 2.5;
    List<Mode> pulldown = closest(within(group, pulldownRate), pulldownRate);
    if (!pulldown.isEmpty()) {
      return pulldown;
    }
    return nearest(group, frameRate);
  }

  /** Among rates already inside the tolerance, keep the closest. 60 stays on 60 when 59.94 is also inside 0.1 Hz. */
  private static List<Mode> closest(List<Mode> matches, double target) {
    if (matches.size() <= 1) {
      return matches;
    }
    return nearest(matches, target);
  }

  private static List<Mode> within(List<Mode> group, double target) {
    List<Mode> matches = new ArrayList<>();
    for (Mode mode : group) {
      if (Math.abs(mode.refreshRate - target) <= RATE_EPSILON_HZ) {
        matches.add(mode);
      }
    }
    return matches;
  }

  private static List<Mode> nearest(List<Mode> group, double frameRate) {
    double best = Double.MAX_VALUE;
    for (Mode mode : group) {
      best = Math.min(best, Math.abs(mode.refreshRate - frameRate));
    }
    List<Mode> matches = new ArrayList<>();
    for (Mode mode : group) {
      if (Math.abs(Math.abs(mode.refreshRate - frameRate) - best) < 0.001) {
        matches.add(mode);
      }
    }
    return matches;
  }

  private static boolean containsHdr(int[] types, int hdrType) {
    if (types == null) {
      return false;
    }
    for (int type : types) {
      if (type == hdrType) {
        return true;
      }
    }
    return false;
  }

  private static int selectId(List<Mode> modes, int currentModeId) {
    for (Mode mode : modes) {
      if (mode.id == currentModeId) {
        return currentModeId;
      }
    }
    int best = modes.get(0).id;
    for (int i = 1; i < modes.size(); i++) {
      if (modes.get(i).id < best) {
        best = modes.get(i).id;
      }
    }
    return best;
  }
}
