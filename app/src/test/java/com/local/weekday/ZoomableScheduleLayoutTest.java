package com.local.weekday;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ZoomableScheduleLayoutTest {
  @Test
  public void zoomIsClampedToReadableBounds() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    ZoomableScheduleLayout layout = new ZoomableScheduleLayout(activity);
    layout.setContent(new TextView(activity));

    layout.setZoom(2.0f);
    assertEquals(1.6f, layout.getZoom(), 0.001f);
    layout.setZoom(0.2f);
    assertEquals(0.8f, layout.getZoom(), 0.001f);
    layout.setZoom(1.15f);
    assertEquals(1.15f, layout.getZoom(), 0.001f);
  }
}
