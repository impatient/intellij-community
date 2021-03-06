// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader.CachedImageIcon;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.ui.DeferredIconImpl;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.ui.RowIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUIScale.UserScaleContext;
import com.intellij.util.ui.JBUIScale.ScaleContext;
import com.intellij.util.ui.JBUIScale.ScaleContextAware;
import com.intellij.util.ui.paint.ImageComparator;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.MalformedURLException;

import static com.intellij.util.ui.JBUIScale.DerivedScaleType.DEV_SCALE;
import static com.intellij.util.ui.JBUIScale.DerivedScaleType.EFF_USR_SCALE;
import static com.intellij.util.ui.JBUIScale.ScaleType.SYS_SCALE;
import static com.intellij.util.ui.JBUIScale.ScaleType.USR_SCALE;
import static com.intellij.util.ui.TestScaleHelper.*;

/**
 * Tests that {@link com.intellij.openapi.util.ScalableIcon#scale(float)} works correctly for custom JB icons.
 *
 * @author tav
 */
public class IconScaleTest extends BareTestFixtureTestCase {
  private static final int ICON_BASE_SIZE = 16;
  private static final float ICON_OBJ_SCALE = 1.75f;

  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  @BeforeClass
  public static void beforeClass() {
    setRegistryProperty("ide.svg.icon", "true");
  }

  @Test
  public void test() throws MalformedURLException {
    // 0.75 is impractical system scale factor, however it's used to stress-test the scale subsystem.
    final double[] SCALES = {0.75f, 1, 2, 2.5f};

    //
    // 1) JRE-HiDPI
    //
    overrideJreHiDPIEnabled(true);
    if (SystemInfo.IS_AT_LEAST_JAVA9 || !SystemInfo.isLinux) {
      for (double s : SCALES) test(1, s);
    }

    //
    // 2) IDE-HiDPI
    //
    overrideJreHiDPIEnabled(false);
    for (double s : SCALES) test(s, s); // the system scale repeats the default user scale in IDE-HiDPI
  }

  public void test(double usrScale, double sysScale) throws MalformedURLException {
    JBUI.setUserScaleFactor((float)usrScale);
    JBUI.setSystemScaleFactor((float)sysScale);

    ScaleContext ctx = ScaleContext.create(SYS_SCALE.of(sysScale), USR_SCALE.of(usrScale));

    //
    // 1. CachedImageIcon
    //
    test(new CachedImageIcon(new File(getIconPath()).toURI().toURL()), ctx.copy());

    //
    // 2. DeferredIcon
    //
    CachedImageIcon icon = new CachedImageIcon(new File(getIconPath()).toURI().toURL());
    test(new DeferredIconImpl<>(icon, new Object(), false, o -> icon), UserScaleContext.create(ctx));

    //
    // 3. LayeredIcon
    //
    test(new LayeredIcon(new CachedImageIcon(new File(getIconPath()).toURI().toURL())), UserScaleContext.create(ctx));

    //
    // 4. RowIcon
    //
    test(new RowIcon(new CachedImageIcon(new File(getIconPath()).toURI().toURL())), UserScaleContext.create(ctx));
  }

  private static void test(Icon icon, UserScaleContext bctx) {
    ((ScaleContextAware)icon).updateScaleContext(bctx);

    ScaleContext ctx = ScaleContext.create(bctx);

    double usrSize2D = ctx.apply(ICON_BASE_SIZE, EFF_USR_SCALE);
    int usrSize = (int)Math.round(usrSize2D);
    int devSize = (int)Math.round(ctx.apply(usrSize2D, DEV_SCALE));

    assertEquals("unexpected icon user width " + bctx, usrSize, icon.getIconWidth());
    assertEquals("unexpected icon user height " + bctx, usrSize, icon.getIconHeight());
    assertEquals("unexpected icon real width " + bctx, devSize, ImageUtil.getRealWidth(IconUtil.toImage(icon, ctx)));
    assertEquals("unexpected icon real height " + bctx, devSize, ImageUtil.getRealHeight(IconUtil.toImage(icon, ctx)));

    Icon scaledIcon = IconUtil.scale(icon, null, ICON_OBJ_SCALE);

    assertNotSame("scaled instance of the icon " + bctx, icon, scaledIcon);
    assertEquals("ScaleContext of the original icon changed " + bctx, bctx, ((ScaleContextAware)icon).getScaleContext());

    double scaledUsrSize2D = usrSize2D * ICON_OBJ_SCALE;
    int scaledUsrSize = (int)Math.round(scaledUsrSize2D);
    int scaledDevSize = (int)Math.round(ctx.apply(scaledUsrSize2D, DEV_SCALE));

    assertEquals("unexpected scaled icon user width " + bctx, scaledUsrSize, scaledIcon.getIconWidth());
    assertEquals("unexpected scaled icon user height " + bctx, scaledUsrSize, scaledIcon.getIconHeight());
    assertEquals("unexpected scaled icon real width " + bctx, scaledDevSize, ImageUtil.getRealWidth(IconUtil.toImage(scaledIcon, ctx)));
    assertEquals("unexpected scaled icon real height " + bctx, scaledDevSize, ImageUtil.getRealHeight(IconUtil.toImage(scaledIcon, ctx)));

    // Additionally check that the original image hasn't changed after scaling
    Pair<BufferedImage, Graphics2D> pair = createImageAndGraphics(ctx.getScale(DEV_SCALE), icon.getIconWidth(), icon.getIconHeight());
    BufferedImage iconImage = pair.first;
    Graphics2D g2d = pair.second;

    icon.paintIcon(null, g2d, 0, 0);

    BufferedImage goldImage = loadImage(getIconPath(), ctx);

    ImageComparator.compareAndAssert(
      new ImageComparator.AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), goldImage, iconImage, null);
  }

  private static String getIconPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "ui/abstractClass.svg";
  }
}
