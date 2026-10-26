package pe.apptrabajador;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppIntegrationTest {
  Context context;

  @Before
  public void setup() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("trabajador.db");
    context.getSharedPreferences("trabajador", 0).edit().clear().commit();
    try (Storage db = new Storage(context)) {
      Storage.Settings cfg = new Storage.Settings();
      cfg.name = "Prueba";
      cfg.policy.salary = new BigDecimal("10");
      cfg.tips = true;
      cfg.commissions = true;
      db.saveSettings(cfg);
    }
  }

  View find(View view, String title) {
    if (view instanceof TextView && ((TextView) view).getText().toString().equals(title))
      return view;
    if (view instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) view;
      for (int i = 0; i < g.getChildCount(); i++) {
        View hit = find(g.getChildAt(i), title);
        if (hit != null) return hit;
      }
    }
    return null;
  }

  @Test
  public void inputSaveRestartEditAndDelete() {
    LocalDate date = LocalDate.now();
    try (ActivityScenario<MainActivity> app = ActivityScenario.launch(MainActivity.class)) {
      app.onActivity(
          a -> {
            find(a.root, "Registrar jornada").performClick();
            assertEquals("editor", a.screen);
          });
      onView(withText("Entrada · 07:00")).perform(scrollTo(), click());
      onView(isAssignableFrom(TimePicker.class))
          .perform(
              new ViewAction() {
                public Matcher<View> getConstraints() {
                  return isAssignableFrom(TimePicker.class);
                }

                public String getDescription() {
                  return "Change time to 08:15";
                }

                public void perform(UiController ui, View v) {
                  ((TimePicker) v).setHour(8);
                  ((TimePicker) v).setMinute(15);
                }
              });
      onView(withText(android.R.string.ok)).perform(click());
      app.onActivity(
          a -> {
            assertEquals(495, a.editing.start);
            a.fields.get("break").setText("45");
            a.fields.get("tips").setText("12.50");
            a.fields.get("commission").setText("5");
            a.fields.get("note").setText("Jornada de prueba");
            find(a.root, "Guardar registro").performClick();
            assertNotNull(a.db.get(date.toString()));
            assertEquals(480, Payroll.calculate(a.db.get(date.toString())).totalMinutes);
          });
      app.recreate();
      app.onActivity(
          a -> {
            Payroll.Shift s = a.db.get(date.toString());
            assertEquals("Jornada de prueba", s.note);
            assertEquals(new BigDecimal("97.50"), Payroll.calculate(s).net);
            a.edit(date, false);
            a.fields.get("break").setText("30");
            find(a.root, "Guardar registro").performClick();
            assertEquals(30, a.db.get(date.toString()).breakMinutes);
            a.showTab(2);
            find(a.root, "Eliminar").performClick();
          });
      onView(withText("Eliminar"))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
          .perform(click());
      app.onActivity(
          a -> {
            assertNull(a.db.get(date.toString()));
            find(a.root, "Deshacer eliminación · " + date).performClick();
            assertNotNull(a.db.get(date.toString()));
          });
    }
  }

  @Test
  public void settingsAndDraftSurviveRecreation() {
    try (ActivityScenario<MainActivity> app = ActivityScenario.launch(MainActivity.class)) {
      app.onActivity(
          a -> {
            a.showTab(3);
            a.fields.get("salary").setText("15");
            find(a.root, "Guardar configuración").performClick();
            a.edit(LocalDate.now(), false);
            a.fields.get("break").setText("37");
            a.fields.get("note").setText("Borrador persistente");
          });
      app.recreate();
      app.onActivity(
          a -> {
            assertEquals("editor", a.screen);
            assertEquals("37", a.val("break"));
            assertEquals("Borrador persistente", a.val("note"));
            assertEquals(0, a.db.settings().policy.salary.compareTo(new BigDecimal("15")));
          });
    }
  }

  @Test
  public void snapshotsCalendarAndOfflineScreens() {
    try (ActivityScenario<MainActivity> app = ActivityScenario.launch(MainActivity.class)) {
      app.onActivity(
          a -> {
            Payroll.Shift s = new Payroll.Shift();
            s.policy.salary = new BigDecimal("10");
            a.db.save(s);
            Storage.Settings cfg = a.db.settings();
            cfg.policy.salary = new BigDecimal("20");
            a.db.saveSettings(cfg);
            assertEquals(new BigDecimal("92.50"), Payroll.calculate(a.db.get(s.date)).net);
            a.month = YearMonth.of(2024, 2);
            a.showTab(1);
            assertNotNull(find(a.root, "29"));
            a.month = YearMonth.now();
            for (int tab = 0; tab < 4; tab++) {
              a.showTab(tab);
              assertNotNull(a.root);
              assertTrue(a.body.getChildCount() > 0);
            }
            a.showTab(0);
          });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      app.onActivity(
          a -> {
            int[] xy = new int[2];
            a.body.getLocationOnScreen(xy);
            assertTrue("Content must clear status bar", xy[1] >= a.root.getPaddingTop());
            assertTrue("Top inset should be applied", a.root.getPaddingTop() > 0);
          });
    }
  }

  @Test
  public void pdfAndScreenshots() throws Exception {
    List<Payroll.Shift> data = new ArrayList<>();
    try (Storage db = new Storage(context)) {
      for (int i = 1; i <= 31; i++) {
        Payroll.Shift s = new Payroll.Shift();
        s.date = LocalDate.of(2026, 1, i).toString();
        s.policy.salary = new BigDecimal("10");
        s.tips = new BigDecimal("12.50");
        s.note =
            "Nota de prueba: entrada, salida y descanso. "
                + String.join(" ", Collections.nCopies(30, "detalle"));
        db.save(s);
        data.add(s);
      }
    }
    File dir = new File(context.getExternalFilesDir(null), "evidence");
    assertTrue(dir.mkdirs() || dir.isDirectory());
    File report = new File(dir, "reporte-prueba.pdf");
    try (Storage db = new Storage(context);
        OutputStream out = new FileOutputStream(report)) {
      PdfReport.write(
          out, db.settings(), data, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    }
    assertTrue(report.length() > 1000);
    try (ParcelFileDescriptor f =
            ParcelFileDescriptor.open(report, ParcelFileDescriptor.MODE_READ_ONLY);
        PdfRenderer renderer = new PdfRenderer(f)) {
      assertTrue(renderer.getPageCount() > 2);
      for (int i : new int[] {0, 1, renderer.getPageCount() - 1}) {
        try (PdfRenderer.Page p = renderer.openPage(i)) {
          Bitmap b =
              Bitmap.createBitmap(p.getWidth() * 2, p.getHeight() * 2, Bitmap.Config.ARGB_8888);
          b.eraseColor(android.graphics.Color.WHITE);
          p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
          try (OutputStream out = new FileOutputStream(new File(dir, "pdf-page-" + i + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
          }
          b.recycle();
        }
      }
    }
    try (ActivityScenario<MainActivity> app = ActivityScenario.launch(MainActivity.class)) {
      for (int tab = 0; tab < 4; tab++) {
        final int n = tab;
        app.onActivity(
            a -> {
              a.month = YearMonth.of(2026, 1);
              a.showTab(n);
            });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap screenshot =
            InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try (OutputStream out =
            new FileOutputStream(
                new File(dir, "screen-" + tab + "-api-" + Build.VERSION.SDK_INT + ".png"))) {
          assertNotNull(screenshot);
          screenshot.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        screenshot.recycle();
      }
    }
    // Copy sample evidence before Gradle uninstalls the instrumented APK.
    try (ParcelFileDescriptor command = InstrumentationRegistry.getInstrumentation()
        .getUiAutomation().executeShellCommand("sh -c 'mkdir -p /sdcard/Download/trabajador-evidence && cp -R " + dir.getAbsolutePath() + "/. /sdcard/Download/trabajador-evidence/'");
        InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(command)) {
      byte[] buffer = new byte[1024];
      while (in.read(buffer) != -1) { /* wait for shell completion */ }
    }

  }
}
