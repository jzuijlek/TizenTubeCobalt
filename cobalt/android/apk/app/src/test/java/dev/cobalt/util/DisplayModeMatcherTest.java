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

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Mode ids and refresh rates in these fixtures are placeholders. They prove matching. They are not
 * a claim about which ids or rates a panel advertises.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DisplayModeMatcherTest {
  private static final int HDR10 = 2;
  private static final int HLG = 3;
  private static final int DOLBY_VISION = 1;

  @Test
  public void zeroSizeClears() {
    assertThat(choose(g5(), 0, 0, 23.976f, HDR10, 11)).isEqualTo(0);
    assertThat(choose(g5(), 0, 1080, 23.976f, 0, 11)).isEqualTo(0);
  }

  @Test
  public void g5_1080pPrefers1080pAtTheVideoRate() {
    assertThat(choose(g5(), 1920, 1080, 23.976f, 0, 0)).isEqualTo(11);
    assertThat(choose(g5(), 1920, 1080, 24f, 0, 0)).isEqualTo(11);
    assertThat(choose(g5(), 1920, 1080, 30f, 0, 0)).isEqualTo(12);
    assertThat(choose(g5(), 1920, 1088, 23.976f, 0, 0)).isEqualTo(11);
    assertThat(choose(g5(), 1080, 1920, 23.976f, 0, 0)).isEqualTo(11);
    assertThat(choose(g5(), 1280, 720, 23.976f, 0, 0)).isEqualTo(11);
    assertThat(choose(g5(), 854, 480, 60f, 0, 0)).isEqualTo(15);
  }

  @Test
  public void g5_2160pAnd1440pStayOnTheirOwnSize() {
    assertThat(choose(g5(), 3840, 2160, 59.94f, 0, 0)).isEqualTo(33);
    assertThat(choose(g5(), 2560, 1440, 60f, 0, 0)).isEqualTo(23);
    assertThat(choose(g5(), 2560, 1440, 23.976f, 0, 0)).isEqualTo(21);
  }

  @Test
  public void g5_hdrDoesNotMoveUpASize() {
    assertThat(choose(g5(), 1920, 1080, 23.976f, HDR10, 0)).isEqualTo(11);
    assertThat(choose(g5(), 1920, 1080, 23.976f, DOLBY_VISION, 0)).isEqualTo(11);
    assertThat(choose(g5(), 2560, 1440, 23.976f, HDR10, 0)).isEqualTo(21);
    assertThat(choose(g5(), 3840, 2160, 59.94f, HDR10, 0)).isEqualTo(33);
    assertThat(DisplayModeMatcher.select(g5(), 1920, 1080, 23.976f, HDR10, 0).sizeMissesHdr)
        .isFalse();
  }

  @Test
  public void without1440_1440pFallsThroughTo2160p() {
    List<DisplayModeMatcher.Mode> modes = without1440();
    assertThat(choose(modes, 2560, 1440, 23.976f, 0, 0)).isEqualTo(31);
    assertThat(choose(modes, 1920, 1080, 23.976f, 0, 0)).isEqualTo(11);
  }

  @Test
  public void with720p_sub720FramesUse720pAndNever480p() {
    List<DisplayModeMatcher.Mode> modes = new ArrayList<>(g5());
    modes.add(mode(1, 1280, 720, 23.976f));
    modes.add(mode(2, 1280, 720, 60f));
    modes.add(mode(9, 720, 480, 60f));
    assertThat(choose(modes, 1280, 720, 60f, 0, 0)).isEqualTo(2);
    assertThat(choose(modes, 854, 480, 60f, 0, 0)).isEqualTo(2);
    assertThat(choose(modes, 640, 360, 23.976f, 0, 0)).isEqualTo(1);
    assertThat(choose(modes, 854, 480, 60f, 0, 0)).isNotEqualTo(9);
  }

  @Test
  public void panelThatLists480pStillGets720pOrTheNextModeUp() {
    List<DisplayModeMatcher.Mode> modes =
        Arrays.asList(mode(9, 720, 480, 60f), mode(15, 1920, 1080, 60f), mode(16, 1920, 1080, 24f));
    assertThat(choose(modes, 854, 480, 60f, 0, 0)).isEqualTo(15);
    assertThat(choose(modes, 1280, 720, 24f, 0, 0)).isEqualTo(16);
  }

  @Test
  public void only480p_neverSelectsIt() {
    List<DisplayModeMatcher.Mode> modes = Arrays.asList(mode(9, 720, 480, 59.94f));
    assertThat(choose(modes, 854, 480, 59.94f, 0, 0)).isEqualTo(0);
    assertThat(choose(modes, 1920, 1080, 59.94f, 0, 0)).isEqualTo(0);
  }

  @Test
  public void only1080p_downshiftsLargerFrames() {
    List<DisplayModeMatcher.Mode> modes =
        Arrays.asList(mode(11, 1920, 1080, 23.976f), mode(15, 1920, 1080, 60f));
    assertThat(choose(modes, 3840, 2160, 23.976f, 0, 0)).isEqualTo(11);
    assertThat(choose(modes, 1280, 720, 60f, 0, 0)).isEqualTo(15);
    assertThat(choose(modes, 2560, 1440, 60f, HDR10, 0)).isEqualTo(15);
  }

  @Test
  public void only2160p_1080pFrameUses2160p() {
    List<DisplayModeMatcher.Mode> modes =
        Arrays.asList(mode(31, 3840, 2160, 23.976f), mode(34, 3840, 2160, 60f));
    assertThat(choose(modes, 1920, 1080, 24f, 0, 0)).isEqualTo(31);
  }

  @Test
  public void padded1080pModeCountsAs1080p() {
    List<DisplayModeMatcher.Mode> modes =
        Arrays.asList(mode(40, 1920, 1088, 24f), mode(34, 3840, 2160, 24f));
    assertThat(choose(modes, 1920, 1080, 24f, 0, 0)).isEqualTo(40);
  }

  @Test
  public void doubleRateBeatsACloserRate() {
    List<DisplayModeMatcher.Mode> modes =
        Arrays.asList(mode(1, 1920, 1080, 50f), mode(2, 1920, 1080, 60f));
    assertThat(choose(modes, 1920, 1080, 30f, 0, 0)).isEqualTo(2);
  }

  @Test
  public void pulldownRateBeatsACloserRate() {
    List<DisplayModeMatcher.Mode> modes =
        Arrays.asList(mode(1, 1920, 1080, 50f), mode(2, 1920, 1080, 60f));
    assertThat(choose(modes, 1920, 1080, 24f, 0, 0)).isEqualTo(2);
  }

  @Test
  public void nearestRateWhenNoMultipleMatches() {
    List<DisplayModeMatcher.Mode> modes =
        Arrays.asList(mode(14, 1920, 1080, 59.94f), mode(15, 1920, 1080, 60f));
    assertThat(choose(modes, 1920, 1080, 48f, 0, 0)).isEqualTo(14);
  }

  @Test
  public void api34_hdrPrefersTheMatchingIdAtTheSameSizeAndRate() {
    List<DisplayModeMatcher.Mode> modes =
        Arrays.asList(
            mode(10, 1920, 1080, 23.976f, new int[] {}),
            mode(11, 1920, 1080, 23.976f, new int[] {HDR10}),
            mode(12, 3840, 2160, 23.976f, new int[] {HDR10}));
    assertThat(choose(modes, 1920, 1080, 23.976f, HDR10, 0)).isEqualTo(11);
    assertThat(choose(modes, 1920, 1080, 23.976f, 0, 0)).isEqualTo(10);
    assertThat(choose(modes, 1920, 1080, 23.976f, 0, 11)).isEqualTo(11);
    assertThat(choose(modes, 3840, 2160, 23.976f, HDR10, 0)).isEqualTo(12);
    assertThat(choose(modes, 2560, 1440, 23.976f, HDR10, 0)).isEqualTo(12);
    DisplayModeMatcher.Selection hlg =
        DisplayModeMatcher.select(modes, 1920, 1080, 23.976f, HLG, 0);
    assertThat(hlg.modeId).isEqualTo(10);
    assertThat(hlg.sizeMissesHdr).isTrue();
  }

  private static int choose(
      List<DisplayModeMatcher.Mode> modes,
      int width,
      int height,
      float frameRate,
      int hdrType,
      int currentModeId) {
    return DisplayModeMatcher.choose(modes, width, height, frameRate, hdrType, currentModeId);
  }

  private static DisplayModeMatcher.Mode mode(int id, int width, int height, float rate) {
    return new DisplayModeMatcher.Mode(id, width, height, rate, null);
  }

  private static DisplayModeMatcher.Mode mode(
      int id, int width, int height, float rate, int[] hdrTypes) {
    return new DisplayModeMatcher.Mode(id, width, height, rate, hdrTypes);
  }

  /** 1080p, 1440p, and 2160p, with no per-mode HDR types. */
  private static List<DisplayModeMatcher.Mode> g5() {
    return new ArrayList<>(
        Arrays.asList(
            mode(11, 1920, 1080, 23.976f),
            mode(12, 1920, 1080, 29.97f),
            mode(13, 1920, 1080, 50f),
            mode(14, 1920, 1080, 59.94f),
            mode(15, 1920, 1080, 60f),
            mode(21, 2560, 1440, 23.976f),
            mode(22, 2560, 1440, 59.94f),
            mode(23, 2560, 1440, 60f),
            mode(31, 3840, 2160, 23.976f),
            mode(32, 3840, 2160, 29.97f),
            mode(33, 3840, 2160, 59.94f),
            mode(34, 3840, 2160, 60f)));
  }

  private static List<DisplayModeMatcher.Mode> without1440() {
    List<DisplayModeMatcher.Mode> modes = new ArrayList<>();
    for (DisplayModeMatcher.Mode mode : g5()) {
      if (mode.height != 1440) {
        modes.add(mode);
      }
    }
    return modes;
  }
}
