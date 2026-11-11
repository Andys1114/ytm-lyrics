package dev.ytmlyrics.ui;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SettingsDialogLayoutTest {
    @Test public void sheetHeightUsesOneScreenBasedGeometry() {
        // No route argument: opening and updating every page share this same geometry.
        assertEquals(760,SettingsDialog.heightPx(844));
        assertEquals(666,SettingsDialog.heightPx(740));
        assertEquals(2160,SettingsDialog.heightPx(2400));
    }
}
