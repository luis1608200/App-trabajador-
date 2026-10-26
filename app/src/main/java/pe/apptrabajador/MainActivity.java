package pe.apptrabajador;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
  static final int BLUE = 0xff087cfa,
      INK = 0xff102445,
      MUTED = 0xff61738e,
      GREEN = 0xff0bad84,
      ORANGE = 0xffff8b21,
      BG = 0xfff4f8fd,
      RED = 0xffcf4662;
  static final Locale ES = new Locale("es", "PE");
  Storage db;
  LinearLayout body, root;
  int tab = 0;
  YearMonth month = YearMonth.now();
  LocalDate selected = LocalDate.now();
  String screen = "tab";
  final Map<String, EditText> fields = new LinkedHashMap<>();
  Payroll.Shift editing;
  boolean existing;
  Spinner kind;
  Switch overnight, currentPolicy;
  Button startButton, endButton;
  Payroll.Shift lastDeleted;
  LocalDate reportFrom, reportTo;
  static final int PDF = 51;
  boolean restoring = false;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    db = new Storage(this);
    if (state != null) {
      tab = state.getInt("tab");
      month = YearMonth.parse(state.getString("month", YearMonth.now().toString()));
      selected = LocalDate.parse(state.getString("selected", LocalDate.now().toString()));
    }
    if (Build.VERSION.SDK_INT >= 30) {
      getWindow().setDecorFitsSystemWindows(false);
      getWindow()
          .getInsetsController()
          .setSystemBarsAppearance(
              WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                  | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
              WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                  | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    } else
      getWindow()
          .getDecorView()
          .setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                  | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    getWindow().setNavigationBarColor(Color.WHITE);
    if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
    try {
      showTab(tab);
      if (state != null && state.getBoolean("editor")) openDraft();
    } catch (RuntimeException e) {
      showStorageError(e);
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    b.putInt("tab", tab);
    b.putString("month", month.toString());
    b.putString("selected", selected.toString());
    b.putBoolean("editor", screen.equals("editor"));
    super.onSaveInstanceState(b);
  }

  @Override
  protected void onDestroy() {
    if (db != null) db.close();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    if (screen.equals("editor") || screen.equals("settings")) {
      leaveTo(0);
    } else if (tab != 0) showTab(0);
    else super.onBackPressed();
  }

  void showStorageError(Exception e) {
    new AlertDialog.Builder(this)
        .setTitle("No se pudo acceder a los datos")
        .setMessage(
            "No se han borrado tus registros. Cierra y vuelve a abrir la aplicación.\n"
                + e.getMessage())
        .setPositiveButton("Cerrar", (d, w) -> finish())
        .show();
  }

  void showTab(int t) {
    tab = t;
    screen = "tab";
    fields.clear();
    if (t == 0) home();
    else if (t == 1) calendar();
    else if (t == 2) history();
    else settings();
  }

  void leaveTo(int t) {
    if (screen.equals("settings")) {
      new AlertDialog.Builder(this)
          .setTitle("¿Salir de los ajustes?")
          .setMessage("Los cambios sin guardar se perderán.")
          .setPositiveButton("Salir", (d, w) -> showTab(t))
          .setNegativeButton("Seguir editando", null)
          .show();
    } else showTab(t);
  }

  int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  LinearLayout vertical() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  LinearLayout horizontal() {
    LinearLayout l = new LinearLayout(this);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  void rounded(View v, int c, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(c);
    d.setCornerRadius(dp(radius));
    v.setBackground(d);
  }

  TextView text(String s, int size, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(3), 0, dp(3));
    if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return t;
  }

  TextView label(String s) {
    return text(s, 14, MUTED, false);
  }

  Button button(String title, boolean primary, Runnable action) {
    Button b = new Button(this);
    b.setText(title);
    b.setAllCaps(false);
    b.setTextSize(14);
    b.setTextColor(primary ? Color.WHITE : BLUE);
    b.setMinHeight(dp(48));
    b.setMinimumHeight(dp(48));
    b.setPadding(dp(12), dp(6), dp(12), dp(6));
    rounded(b, primary ? BLUE : 0xffeaf3ff, 13);
    b.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(9), 0, dp(3));
    b.setLayoutParams(p);
    return b;
  }

  LinearLayout card() {
    LinearLayout c = vertical();
    c.setPadding(dp(16), dp(14), dp(16), dp(14));
    GradientDrawable d = new GradientDrawable();
    d.setColor(Color.WHITE);
    d.setCornerRadius(dp(20));
    d.setStroke(dp(1), 0xffe5eef8);
    c.setBackground(d);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(7), 0, dp(7));
    c.setLayoutParams(p);
    return c;
  }

  void heading(LinearLayout c, String icon, String title) {
    LinearLayout r = horizontal();
    r.addView(new IconView(this, icon, BLUE), new LinearLayout.LayoutParams(dp(27), dp(27)));
    TextView t = text(title, 17, INK, true);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.setMargins(dp(8), 0, 0, dp(5));
    r.addView(t, p);
    c.addView(r);
  }

  void valueRow(LinearLayout c, String title, String value) {
    LinearLayout r = horizontal();
    TextView t = text(title, 14, MUTED, false);
    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    TextView v = text(value, 15, INK, true);
    v.setGravity(Gravity.END);
    r.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
    r.setPadding(0, dp(7), 0, dp(7));
    c.addView(r);
  }

  void shell(String title, int active) {
    root = vertical();
    root.setBackgroundColor(BG);
    root.setOnApplyWindowInsetsListener(
        (v, w) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            Insets i =
                w.getInsets(
                    WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout()
                        | WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
          } else
            v.setPadding(
                w.getSystemWindowInsetLeft(),
                w.getSystemWindowInsetTop(),
                w.getSystemWindowInsetRight(),
                w.getSystemWindowInsetBottom());
          return w;
        });
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    FrameLayout frame = new FrameLayout(this);
    body =
        new LinearLayout(this) {
          @Override
          protected void onMeasure(int w, int h) {
            int width = Math.min(View.MeasureSpec.getSize(w), dp(620));
            super.onMeasure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), h);
          }
        };
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(16), dp(12), dp(16), dp(18));
    FrameLayout.LayoutParams bp =
        new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    frame.addView(body, bp);
    scroll.addView(frame);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (!title.isEmpty()) {
      TextView h = text(title, 25, INK, true);
      h.setPadding(dp(2), dp(5), 0, dp(13));
      body.addView(h);
    }
    LinearLayout nav = horizontal();
    nav.setBackgroundColor(Color.WHITE);
    nav.setPadding(dp(4), dp(5), dp(4), dp(5));
    String[] labels = {"Inicio", "Calendario", "Historial", "Ajustes"},
        icons = {"home", "calendar", "history", "settings"};
    for (int i = 0; i < 4; i++) {
      final int n = i;
      LinearLayout item = vertical();
      item.setGravity(Gravity.CENTER);
      item.setPadding(dp(1), dp(5), dp(1), dp(5));
      item.addView(
          new IconView(this, icons[i], i == active ? BLUE : MUTED),
          new LinearLayout.LayoutParams(dp(27), dp(27)));
      TextView titleView = text(labels[i], 11, i == active ? BLUE : MUTED, i == active);
      titleView.setGravity(Gravity.CENTER);
      item.addView(titleView);
      item.setContentDescription(labels[i]);
      item.setFocusable(true);
      item.setOnClickListener(v -> leaveTo(n));
      if (i == active) rounded(item, 0xffedf5ff, 14);
      nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }
    root.addView(nav);
    setContentView(root);
    root.requestApplyInsets();
  }

  List<Payroll.Shift> entries(YearMonth m) {
    return db.range(m.atDay(1).toString(), m.atEndOfMonth().toString());
  }

  String monthTitle() {
    return cap(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", ES)));
  }

  String cap(String s) {
    return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(ES) + s.substring(1);
  }

  void home() {
    shell("", 0);
    Storage.Settings cfg = db.settings();
    LinearLayout hello = horizontal(), name = vertical();
    name.addView(text(cfg.name.isEmpty() ? "Mi jornada" : "Hola, " + cfg.name, 27, INK, true));
    name.addView(
        label(cap(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)))));
    hello.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
    IconView helmet = new IconView(this, "helmet", BLUE);
    rounded(helmet, 0xffdeedff, 30);
    hello.addView(helmet, new LinearLayout.LayoutParams(dp(54), dp(54)));
    body.addView(hello);
    Payroll.Shift s = db.get(LocalDate.now().toString());
    Payroll.Result r = s == null ? new Payroll.Result() : Payroll.calculate(s);
    LinearLayout reg = card();
    heading(reg, "clock", "Registro de hoy");
    LinearLayout row = horizontal();
    boolean work = s != null && s.kind.equals("WORK");
    String[] ts = {"Entrada", "Salida", "Descanso"},
        vs =
            {
              work ? Payroll.time(s.start) : "—",
              work ? Payroll.time(s.end) + (s.nextDay ? " +1" : "") : "—",
              work ? s.breakMinutes + " min" : "—"
            },
        is = {"sun", "clock", "coffee"};
    for (int i = 0; i < 3; i++) {
      LinearLayout cell = vertical();
      cell.setGravity(Gravity.CENTER);
      cell.setPadding(dp(3), dp(8), dp(3), dp(8));
      rounded(cell, 0xfff3f6fb, 13);
      cell.addView(text(ts[i], 12, MUTED, false));
      LinearLayout line = horizontal();
      line.setGravity(Gravity.CENTER);
      line.addView(
          new IconView(this, is[i], i == 0 ? GREEN : i == 1 ? ORANGE : MUTED),
          new LinearLayout.LayoutParams(dp(23), dp(23)));
      line.addView(text(vs[i], 17, INK, true));
      cell.addView(line);
      cell.setContentDescription(ts[i] + ". Toca para editar la jornada.");
      cell.setFocusable(true);
      cell.setOnClickListener(v -> edit(LocalDate.now(), false));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
      p.setMargins(dp(2), dp(6), dp(2), dp(5));
      row.addView(cell, p);
    }
    reg.addView(row);
    if (s != null && !work)
      reg.addView(
          label(
              s.kind.equals("REST")
                  ? "Hoy está marcado como descanso."
                  : "Hoy está marcado como ausencia."));
    reg.addView(
        button(
            s == null ? "Registrar jornada" : "Editar registro",
            false,
            () -> edit(LocalDate.now(), false)));
    String active = db.prefs.getString("activeDate", null);
    if (active != null) {
      reg.addView(
          label(
              "Entrada en curso · "
                  + active
                  + " · "
                  + Payroll.time(db.prefs.getInt("activeMinute", 0))));
      reg.addView(button("Marcar salida ahora", true, this::finishPunch));
      reg.addView(
          button(
              "Cancelar entrada en curso",
              false,
              () ->
                  new AlertDialog.Builder(this)
                      .setTitle("¿Cancelar la entrada?")
                      .setMessage("Se eliminará solo la entrada en curso, no tu historial.")
                      .setPositiveButton(
                          "Cancelar entrada",
                          (d, w) -> {
                            db.prefs.edit().remove("activeDate").remove("activeMinute").apply();
                            home();
                          })
                      .setNegativeButton("Volver", null)
                      .show()));
    } else if (s == null)
      reg.addView(
          button(
              "Marcar entrada ahora",
              true,
              () -> {
                LocalTime now = LocalTime.now();
                if (db.prefs
                    .edit()
                    .putString("activeDate", LocalDate.now().toString())
                    .putInt("activeMinute", now.getHour() * 60 + now.getMinute())
                    .commit()) {
                  home();
                  toast("Entrada guardada en este teléfono");
                } else error("No se pudo guardar la entrada.");
              }));
    if (db.prefs.contains("draft"))
      reg.addView(button("Continuar borrador", false, this::openDraft));
    body.addView(reg);
    LinearLayout earn = card();
    GradientDrawable gradient =
        new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xff0564dc, 0xff2caae4});
    gradient.setCornerRadius(dp(22));
    earn.setBackground(gradient);
    earn.addView(text("Ganancia neta estimada de hoy", 16, Color.WHITE, false));
    earn.addView(text(Payroll.cash(r.net), 38, Color.WHITE, true));
    earn.addView(
        text(
            Payroll.duration(r.totalMinutes) + " · Extras " + Payroll.duration(r.extraMinutes),
            13,
            0xffe3f3ff,
            false));
    Payroll.Shift yesterday = db.get(LocalDate.now().minusDays(1).toString());
    if (s != null && yesterday != null) {
      BigDecimal diff = r.net.subtract(Payroll.calculate(yesterday).net);
      earn.addView(
          text(
              (diff.signum() >= 0 ? "+" : "") + Payroll.cash(diff) + " frente a ayer",
              13,
              Color.WHITE,
              true));
    }
    body.addView(earn);
    if (cfg.policy.salary.signum() == 0) {
      LinearLayout c = card();
      c.addView(text("Configura tu sueldo", 17, INK, true));
      c.addView(label("Registra tu tarifa y descuentos para calcular tus ingresos."));
      c.addView(button("Configurar mi trabajo", true, () -> showTab(3)));
      body.addView(c);
    }
    boolean vars =
        cfg.tips || cfg.commissions || cfg.bonuses || (s != null && r.variables.signum() != 0);
    if (vars) {
      LinearLayout c = card();
      heading(c, "wallet", "Ingresos variables");
      if (cfg.tips || (s != null && s.tips.signum() != 0))
        variable(c, "tips", "Propinas", s == null ? Payroll.ZERO : s.tips, GREEN);
      if (cfg.commissions || (s != null && s.commission.signum() != 0))
        variable(c, "chart", "Comisiones", s == null ? Payroll.ZERO : s.commission, 0xff9854ec);
      if (cfg.bonuses || (s != null && s.bonus.signum() != 0))
        variable(c, "wallet", "Bonos", s == null ? Payroll.ZERO : s.bonus, ORANGE);
      c.addView(button("Editar ingresos de hoy", false, () -> edit(LocalDate.now(), false)));
      body.addView(c);
    }
    LinearLayout chart = card();
    heading(chart, "chart", "Mis ingresos");
    chart.addView(label("Últimos 4 meses · Neto estimado"));
    YearMonth now = YearMonth.now();
    List<BigDecimal> amounts = new ArrayList<>();
    List<String> labels = new ArrayList<>();
    for (int i = 3; i >= 0; i--) {
      YearMonth m = now.minusMonths(i);
      amounts.add(Storage.total(entries(m)).net);
      labels.add(cap(m.format(DateTimeFormatter.ofPattern("MMM", ES))));
    }
    chart.addView(
        new IncomeChart(this, amounts, labels), new LinearLayout.LayoutParams(-1, dp(195)));
    valueRow(chart, "Este mes", Payroll.cash(amounts.get(3)));
    valueRow(chart, "Mes anterior", Payroll.cash(amounts.get(2)));
    BigDecimal change = amounts.get(3).subtract(amounts.get(2));
    valueRow(chart, "Variación mensual", (change.signum() > 0 ? "+" : "") + Payroll.cash(change));
    body.addView(chart);
    body.addView(label("Tus datos se guardan en este teléfono. Funciona sin internet."));
  }

  void variable(LinearLayout c, String icon, String name, BigDecimal n, int color) {
    LinearLayout r = horizontal();
    IconView v = new IconView(this, icon, color);
    r.addView(v, new LinearLayout.LayoutParams(dp(32), dp(32)));
    TextView t = text(name, 15, INK, true);
    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    r.addView(text(Payroll.cash(n), 17, INK, true));
    r.setPadding(0, dp(9), 0, dp(9));
    c.addView(r);
  }

  void finishPunch() {
    if (db.prefs.contains("draft")) {
      new AlertDialog.Builder(this)
          .setTitle("Hay un borrador sin guardar")
          .setMessage(
              "Termina o descarta el borrador antes de marcar la salida. Tu entrada sigue"
                  + " guardada.")
          .setPositiveButton("Abrir borrador", (d, w) -> openDraft())
          .setNegativeButton("Volver", null)
          .show();
      return;
    }
    String date = db.prefs.getString("activeDate", null);
    if (date == null) return;
    LocalDate d = LocalDate.parse(date);
    long days = ChronoUnit.DAYS.between(d, LocalDate.now());
    if (days < 0 || days > 1) {
      error(
          "La entrada lleva más de un día. Revisa la fecha y registra el turno manualmente; después"
              + " cancela esta entrada en curso.");
      return;
    }
    int startMinute = db.prefs.getInt("activeMinute", -1);
    LocalTime now = LocalTime.now();
    int endMinute = now.getHour() * 60 + now.getMinute();
    int total = endMinute - startMinute + (days == 1 ? 1440 : 0);
    // Validate before opening or changing the editor. An invalid punch must not
    // overwrite a saved shift, create a draft, or silently alter the break.
    if (startMinute < 0 || startMinute >= 1440 || total <= 0 || total > 1440) {
      error("La entrada o salida no forman una jornada válida. Revisa las horas y registra el turno manualmente.");
      return;
    }
    edit(d, false);
    editing.start = startMinute;
    editing.end = endMinute;
    editing.nextDay = days == 1;
    editing.breakMinutes = total > 60 ? 60 : 0;
    fields.get("break").setText(String.valueOf(editing.breakMinutes));
    overnight.setChecked(editing.nextDay);
    updateTimes();
    saveDraft();
  }

  void monthControl(LinearLayout c) {
    LinearLayout r = horizontal();
    Button prev =
        button(
            "‹",
            false,
            () -> {
              month = month.minusMonths(1);
              showTab(tab);
            });
    Button next =
        button(
            "›",
            false,
            () -> {
              month = month.plusMonths(1);
              showTab(tab);
            });
    r.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(48)));
    TextView title = text(monthTitle(), 18, INK, true);
    title.setGravity(Gravity.CENTER);
    r.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    r.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));
    c.addView(r);
  }

  void calendar() {
    shell("Calendario", 1);
    LinearLayout c = card();
    monthControl(c);
    Map<String, Payroll.Shift> map = new HashMap<>();
    List<Payroll.Shift> shifts = entries(month);
    for (Payroll.Shift s : shifts) map.put(s.date, s);
    LinearLayout days = horizontal();
    for (String s : new String[] {"L", "M", "X", "J", "V", "S", "D"}) {
      TextView t = text(s, 12, MUTED, false);
      t.setGravity(Gravity.CENTER);
      days.addView(t, new LinearLayout.LayoutParams(0, dp(38), 1));
    }
    c.addView(days);
    int offset = month.atDay(1).getDayOfWeek().getValue() - 1;
    int count = ((offset + month.lengthOfMonth() + 6) / 7) * 7;
    for (int i = 0; i < count; i += 7) {
      LinearLayout row = horizontal();
      for (int j = 0; j < 7; j++) {
        int n = i + j - offset + 1;
        TextView cell = text("", 14, INK, false);
        cell.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(51), 1);
        p.setMargins(dp(2), dp(3), dp(2), dp(3));
        if (n > 0 && n <= month.lengthOfMonth()) {
          LocalDate date = month.atDay(n);
          Payroll.Shift s = map.get(date.toString());
          boolean extra = s != null && Payroll.calculate(s).extraMinutes > 0;
          String mark =
              s == null
                  ? ""
                  : s.kind.equals("WORK") ? "\n●" : s.kind.equals("REST") ? "\n–" : "\n×";
          cell.setText(n + mark);
          int color =
              s == null
                  ? 0xfff4f7fc
                  : s.kind.equals("REST")
                      ? 0xffffedf1
                      : s.kind.equals("ABSENCE") ? 0xffffe4e7 : extra ? 0xfffff1e3 : 0xffe5f7ef;
          rounded(cell, color, 12);
          if (date.equals(LocalDate.now())) {
            rounded(cell, BLUE, 12);
            cell.setTextColor(Color.WHITE);
          }
          cell.setContentDescription(
              date
                  + ", "
                  + (s == null
                      ? "Sin registro"
                      : s.kind.equals("WORK")
                          ? (extra ? "Trabajo con horas extra" : "Trabajado")
                          : s.kind.equals("REST") ? "Descanso" : "Ausencia"));
          cell.setFocusable(true);
          cell.setOnClickListener(
              v -> {
                selected = date;
                edit(date, false);
              });
        }
        row.addView(cell, p);
      }
      c.addView(row);
    }
    c.addView(label("● Trabajado   ● Extra   – Descanso   × Ausencia"));
    c.addView(label("Toca un día para registrar o editar."));
    body.addView(c);
    summary(shifts);
  }

  void summary(List<Payroll.Shift> shifts) {
    Payroll.Result sum = Storage.total(shifts);
    int work = 0, rest = 0, absent = 0;
    for (Payroll.Shift s : shifts) {
      if (s.kind.equals("WORK")) work++;
      else if (s.kind.equals("REST")) rest++;
      else absent++;
    }
    LinearLayout c = card();
    heading(c, "chart", "Resumen del periodo");
    valueRow(c, "Días trabajados", String.valueOf(work));
    valueRow(c, "Horas normales", Payroll.duration(sum.normalMinutes));
    valueRow(c, "Horas extra", Payroll.duration(sum.extraMinutes));
    valueRow(c, "Descansos / ausencias", rest + " / " + absent);
    valueRow(c, "Ingreso bruto", Payroll.cash(sum.gross));
    valueRow(c, "Descuentos", Payroll.cash(sum.deductions));
    valueRow(c, "Neto estimado", Payroll.cash(sum.net));
    body.addView(c);
  }

  void history() {
    shell("Historial", 2);
    LinearLayout c = card();
    monthControl(c);
    c.addView(button("Exportar reporte PDF", true, this::reportDialog));
    c.addView(
        button("Añadir registro", false, () -> pickDate(LocalDate.now(), d -> edit(d, false))));
    body.addView(c);
    List<Payroll.Shift> shifts = entries(month);
    summary(shifts);
    if (lastDeleted != null) {
      body.addView(
          button(
              "Deshacer eliminación · " + lastDeleted.date,
              false,
              () -> {
                if (db.get(lastDeleted.date) != null) {
                  error("Ya hay otro registro en esa fecha.");
                  return;
                }
                db.save(lastDeleted);
                lastDeleted = null;
                history();
              }));
    }
    if (shifts.isEmpty()) {
      LinearLayout empty = card();
      heading(empty, "calendar", "Aún no hay registros");
      empty.addView(
          label("Registra tu primera jornada. Aquí podrás revisarla, editarla y exportarla."));
      body.addView(empty);
    }
    for (Payroll.Shift s : shifts) {
      LinearLayout entry = card();
      heading(
          entry,
          "history",
          LocalDate.parse(s.date).format(DateTimeFormatter.ofPattern("EEE d MMM", ES)));
      if (s.kind.equals("WORK")) {
        Payroll.Result r = Payroll.calculate(s);
        valueRow(
            entry,
            Payroll.time(s.start) + " — " + Payroll.time(s.end) + (s.nextDay ? " +1 día" : ""),
            Payroll.cash(r.net));
        entry.addView(
            label(Payroll.duration(r.totalMinutes) + " · Descanso " + s.breakMinutes + " min"));
        entry.addView(label("Extras: " + Payroll.duration(r.extraMinutes)));
      } else entry.addView(label(s.kind.equals("REST") ? "Día de descanso" : "Ausencia"));
      if (!s.note.isEmpty()) entry.addView(label(s.note));
      LinearLayout actions = horizontal();
      Button edit = button("Editar", false, () -> edit(LocalDate.parse(s.date), false));
      Button del =
          button(
              "Eliminar",
              false,
              () ->
                  new AlertDialog.Builder(this)
                      .setTitle("¿Eliminar el registro?")
                      .setMessage("Fecha: " + s.date + ". Podrás deshacerlo desde esta pantalla.")
                      .setNegativeButton("Cancelar", null)
                      .setPositiveButton(
                          "Eliminar",
                          (d, w) -> {
                            db.delete(s.date);
                            lastDeleted = s;
                            history();
                          })
                      .show());
      del.setTextColor(RED);
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
      p.setMargins(dp(3), dp(9), dp(3), 0);
      actions.addView(edit, p);
      actions.addView(del, new LinearLayout.LayoutParams(p));
      entry.addView(actions);
      body.addView(entry);
    }
  }

  interface DatePicked {
    void got(LocalDate d);
  }

  void pickDate(LocalDate date, DatePicked picked) {
    new DatePickerDialog(
            this,
            (v, y, m, d) -> picked.got(LocalDate.of(y, m + 1, d)),
            date.getYear(),
            date.getMonthValue() - 1,
            date.getDayOfMonth())
        .show();
  }

  EditText input(LinearLayout c, String key, String title, String val, boolean number) {
    TextView l = label(title);
    c.addView(l);
    EditText e = new EditText(this);
    e.setId((key.hashCode() & 0x00ffffff) | 0x01000000);
    l.setLabelFor(e.getId());
    e.setTextSize(16);
    e.setTextColor(INK);
    e.setSingleLine(!key.equals("note"));
    e.setInputType(
        number
            ? android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            : android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    e.setFilters(
        new InputFilter[] {
          new InputFilter.LengthFilter(key.equals("note") ? 500 : number ? 14 : 80)
        });
    e.setPadding(dp(12), dp(10), dp(12), dp(10));
    rounded(e, 0xfff1f6fc, 10);
    e.setText(val);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(4), 0, dp(10));
    c.addView(e, p);
    fields.put(key, e);
    e.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            if (screen.equals("editor")) saveDraft();
          }

          public void afterTextChanged(Editable e) {}
        });
    return e;
  }

  Spinner spinner(LinearLayout c, String title, String[] values, int index) {
    c.addView(label(title));
    Spinner s = new Spinner(this);
    s.setId((title.hashCode() & 0x00ffffff) | 0x02000000);
    ArrayAdapter<String> a =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
    s.setAdapter(a);
    s.setSelection(index);
    c.addView(s, new LinearLayout.LayoutParams(-1, dp(52)));
    return s;
  }

  Switch toggle(LinearLayout c, String title, boolean checked) {
    Switch s = new Switch(this);
    s.setId((title.hashCode() & 0x00ffffff) | 0x03000000);
    s.setText(title);
    s.setTextSize(14);
    s.setTextColor(INK);
    s.setChecked(checked);
    s.setPadding(0, dp(10), 0, dp(10));
    s.setMinHeight(dp(48));
    c.addView(s, new LinearLayout.LayoutParams(-1, -2));
    return s;
  }

  String val(String key) {
    return fields.get(key).getText().toString().trim();
  }

  BigDecimal amount(String key) {
    return Payroll.decimal(val(key).isEmpty() ? "0" : val(key));
  }

  int integer(String key) {
    try {
      return new BigDecimal(val(key)).intValueExact();
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
          "Revisa el valor de «" + key + "»: debe ser un número entero.");
    }
  }

  void edit(LocalDate date, boolean resume) {
    if (!resume && db.prefs.contains("draft")) {
      new AlertDialog.Builder(this)
          .setTitle("Hay un borrador sin guardar")
          .setMessage("Puedes continuarlo o descartarlo para editar esta fecha.")
          .setPositiveButton("Continuar borrador", (d, w) -> openDraft())
          .setNegativeButton(
              "Descartar borrador",
              (d, w) -> {
                db.prefs.edit().remove("draft").apply();
                edit(date, true);
              })
          .setNeutralButton("Volver", null)
          .show();
      return;
    }
    restoring = true;
    screen = "editor";
    fields.clear();
    editing = db.get(date.toString());
    existing = editing != null;
    if (editing == null) {
      editing = new Payroll.Shift();
      editing.date = date.toString();
      editing.policy = db.settings().policy;
    }
    shell(existing ? "Editar jornada" : "Nueva jornada", tab);
    LinearLayout c = card();
    heading(c, "calendar", date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", ES)));
    kind =
        spinner(
            c,
            "Tipo de día",
            new String[] {"Trabajado", "Descanso", "Ausencia"},
            editing.kind.equals("WORK") ? 0 : editing.kind.equals("REST") ? 1 : 2);
    startButton = button("Entrada · " + Payroll.time(editing.start), false, () -> chooseTime(true));
    endButton = button("Salida · " + Payroll.time(editing.end), false, () -> chooseTime(false));
    c.addView(startButton);
    c.addView(endButton);
    overnight = toggle(c, "Salida al día siguiente", editing.nextDay);
    overnight.setOnCheckedChangeListener(
        (b, on) -> {
          editing.nextDay = on;
          saveDraft();
        });
    input(c, "break", "Descanso no pagado (minutos)", String.valueOf(editing.breakMinutes), true);
    c.addView(label("La jornada debe durar como máximo 24 horas."));
    body.addView(c);
    Storage.Settings cfg = db.settings();
    LinearLayout money = card();
    heading(money, "wallet", "Ingresos y descuentos");
    if (cfg.tips || editing.tips.signum() != 0)
      input(money, "tips", "Propinas (S/)", editing.tips.toPlainString(), true);
    if (cfg.commissions || editing.commission.signum() != 0)
      input(money, "commission", "Comisiones (S/)", editing.commission.toPlainString(), true);
    if (cfg.bonuses || editing.bonus.signum() != 0)
      input(money, "bonus", "Bonos (S/)", editing.bonus.toPlainString(), true);
    input(
        money,
        "deduction",
        "Descuento o adelanto de este día (S/)",
        editing.deduction.toPlainString(),
        true);
    money.addView(label("Propinas, comisiones y bonos se activan en Ajustes."));
    if (existing) {
      currentPolicy = toggle(money, "Recalcular con mis ajustes actuales", false);
      currentPolicy.setOnCheckedChangeListener((b, on) -> saveDraft());
      money.addView(label("Si no lo activas, conserva las tarifas históricas."));
    } else currentPolicy = null;
    body.addView(money);
    LinearLayout notes = card();
    input(notes, "note", "Nota (opcional)", editing.note, false);
    notes.addView(
        label("Descansos y ausencias se registran sin ingresos ni descuentos automáticos."));
    body.addView(notes);
    body.addView(
        button(
            "Ver cálculo antes de guardar",
            false,
            () -> {
              try {
                Payroll.Shift s = readEditor();
                Payroll.Result r = Payroll.calculate(s);
                new AlertDialog.Builder(this)
                    .setTitle("Cálculo de la jornada")
                    .setMessage(calculation(s, r))
                    .setPositiveButton("Entendido", null)
                    .show();
              } catch (Exception e) {
                error(e.getMessage());
              }
            }));
    body.addView(button("Guardar registro", true, this::saveEditor));
    body.addView(
        button(
            "Descartar borrador",
            false,
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("¿Descartar los cambios?")
                    .setMessage("El registro guardado, si existe, se conserva.")
                    .setPositiveButton(
                        "Descartar",
                        (d, w) -> {
                          db.prefs.edit().remove("draft").apply();
                          showTab(tab);
                        })
                    .setNegativeButton("Seguir editando", null)
                    .show()));
    kind.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            boolean work = pos == 0;
            startButton.setEnabled(work);
            endButton.setEnabled(work);
            overnight.setEnabled(work);
            for (String k : new String[] {"break", "tips", "commission", "bonus", "deduction"})
              if (fields.containsKey(k)) fields.get(k).setEnabled(work);
            saveDraft();
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    restoring = false;
    saveDraft();
  }

  void chooseTime(boolean start) {
    int minute = start ? editing.start : editing.end;
    new TimePickerDialog(
            this,
            (v, h, m) -> {
              if (start) editing.start = h * 60 + m;
              else editing.end = h * 60 + m;
              updateTimes();
              saveDraft();
            },
            minute / 60,
            minute % 60,
            true)
        .show();
  }

  void updateTimes() {
    startButton.setText("Entrada · " + Payroll.time(editing.start));
    endButton.setText("Salida · " + Payroll.time(editing.end));
  }

  Payroll.Shift readEditor() {
    Payroll.Shift s = new Payroll.Shift();
    s.date = editing.date;
    s.kind =
        kind.getSelectedItemPosition() == 0
            ? "WORK"
            : kind.getSelectedItemPosition() == 1 ? "REST" : "ABSENCE";
    s.start = editing.start;
    s.end = editing.end;
    s.nextDay = overnight.isChecked();
    s.breakMinutes = s.kind.equals("WORK") ? integer("break") : 0;
    s.note = val("note");
    s.policy =
        currentPolicy != null && currentPolicy.isChecked() ? db.settings().policy : editing.policy;
    if (s.kind.equals("WORK")) {
      s.tips = fields.containsKey("tips") ? amount("tips") : editing.tips;
      s.commission = fields.containsKey("commission") ? amount("commission") : editing.commission;
      s.bonus = fields.containsKey("bonus") ? amount("bonus") : editing.bonus;
      s.deduction = amount("deduction");
      if (s.policy.salary.signum() == 0)
        throw new IllegalArgumentException(
            "Configura primero tu sueldo en Ajustes. El borrador quedará guardado.");
    }
    s.validate();
    return s;
  }

  void saveEditor() {
    try {
      Payroll.Shift s = readEditor();
      db.save(s);
      db.prefs.edit().remove("draft").apply();
      if (s.date.equals(db.prefs.getString("activeDate", null)))
        db.prefs.edit().remove("activeDate").remove("activeMinute").apply();
      month = YearMonth.from(LocalDate.parse(s.date));
      showTab(tab);
      toast("Registro guardado");
    } catch (Exception e) {
      error(e.getMessage());
    }
  }

  void saveDraft() {
    if (restoring
        || !screen.equals("editor")
        || editing == null
        || kind == null
        || overnight == null) return;
    try {
      JSONObject j =
          new JSONObject()
              .put("date", editing.date)
              .put("start", editing.start)
              .put("end", editing.end)
              .put("kind", kind.getSelectedItemPosition())
              .put("overnight", overnight.isChecked())
              .put("currentPolicy", currentPolicy != null && currentPolicy.isChecked());
      for (Map.Entry<String, EditText> f : fields.entrySet())
        j.put(f.getKey(), f.getValue().getText().toString());
      db.prefs.edit().putString("draft", j.toString()).apply();
    } catch (JSONException ignored) {
    }
  }

  void openDraft() {
    String raw = db.prefs.getString("draft", null);
    if (raw == null) return;
    try {
      JSONObject j = new JSONObject(raw);
      edit(LocalDate.parse(j.getString("date")), true);
      restoring = true;
      editing.start = j.getInt("start");
      editing.end = j.getInt("end");
      kind.setSelection(j.getInt("kind"));
      overnight.setChecked(j.getBoolean("overnight"));
      if (currentPolicy != null) currentPolicy.setChecked(j.optBoolean("currentPolicy"));
      for (Map.Entry<String, EditText> f : fields.entrySet())
        if (j.has(f.getKey())) f.getValue().setText(j.getString(f.getKey()));
      updateTimes();
      restoring = false;
      saveDraft();
    } catch (Exception e) {
      restoring = false;
      error("No se pudo abrir el borrador: " + e.getMessage());
    }
  }

  String calculation(Payroll.Shift s, Payroll.Result r) {
    return "Tarifa por hora: "
        + Payroll.cash(s.policy.hourly())
        + "\nNormales: "
        + Payroll.duration(r.normalMinutes)
        + " · "
        + Payroll.cash(r.normal)
        + "\nExtras: "
        + Payroll.duration(r.extraMinutes)
        + " · "
        + Payroll.cash(r.extra)
        + "\nVariables: "
        + Payroll.cash(r.variables)
        + "\nBruto: "
        + Payroll.cash(r.gross)
        + "\nPensión: "
        + Payroll.cash(r.pension)
        + "\nSeguro: "
        + Payroll.cash(r.insurance)
        + "\nOtros: "
        + Payroll.cash(r.other)
        + "\nFijo prorrateado: "
        + Payroll.cash(r.fixed)
        + "\n\nNeto estimado: "
        + Payroll.cash(r.net);
  }

  void settings() {
    shell("Mi trabajo", 3);
    screen = "settings";
    Storage.Settings s = db.settings();
    Payroll.Policy p = s.policy;
    LinearLayout hero = card();
    rounded(hero, 0xffdeeeff, 18);
    heading(hero, "helmet", "Configuración laboral");
    hero.addView(
        label(
            "Personaliza los datos según tu trabajo. Los cambios se aplican a nuevos registros."));
    body.addView(hero);
    LinearLayout profile = card();
    heading(profile, "history", "Tus datos");
    input(profile, "name", "Nombre (opcional)", s.name, false);
    input(profile, "job", "Trabajo o rubro (opcional)", s.job, false);
    Spinner period =
        spinner(
            profile,
            "Forma de pago",
            new String[] {"Semanal", "Quincenal", "Mensual"},
            s.period.equals("Semanal") ? 0 : s.period.equals("Quincenal") ? 1 : 2);
    body.addView(profile);
    LinearLayout pay = card();
    heading(pay, "wallet", "Sueldo y jornada");
    Spinner mode =
        spinner(
            pay,
            "El importe de sueldo corresponde a",
            new String[] {"Por hora", "Por día / jornal", "Por mes"},
            p.payMode.equals("HOUR") ? 0 : p.payMode.equals("DAY") ? 1 : 2);
    input(pay, "salary", "Sueldo / tarifa (S/)", p.salary.toPlainString(), true);
    input(
        pay,
        "hours",
        "Jornada normal diaria (horas)",
        BigDecimal.valueOf(p.normalMinutes)
            .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString(),
        true);
    input(pay, "days", "Días de referencia por mes", String.valueOf(p.daysPerMonth), true);
    pay.addView(
        label(
            "Tarifa mensual ÷ días de referencia ÷ horas normales. Se estiman solo las horas"
                + " registradas; no calcula una planilla mensual completa."));
    body.addView(pay);
    LinearLayout extras = card();
    heading(extras, "clock", "Horas extra");
    input(
        extras,
        "first",
        "Sobretasa primeras 2 horas extra (%)",
        p.extraFirst.toPlainString(),
        true);
    input(
        extras,
        "later",
        "Sobretasa desde la 3.ª hora extra (%)",
        p.extraLater.toPlainString(),
        true);
    extras.addView(
        label("Los límites se aplican por jornada. Ajusta las tasas a tu acuerdo laboral."));
    body.addView(extras);
    LinearLayout deductions = card();
    heading(deductions, "wallet", "Descuentos");
    input(
        deductions, "pension", "AFP / ONP: porcentaje total (%)", p.pension.toPlainString(), true);
    input(
        deductions,
        "insurance",
        "Seguro a cargo del trabajador (%)",
        p.insurance.toPlainString(),
        true);
    input(deductions, "other", "Otros descuentos (%)", p.otherPercent.toPlainString(), true);
    input(
        deductions,
        "fixed",
        "Descuento fijo mensual de referencia (S/)",
        p.fixedMonthly.toPlainString(),
        true);
    Switch taxable = toggle(deductions, "Aplicar porcentajes también a variables", p.taxVariables);
    deductions.addView(
        label(
            "Se aplican sobre sueldo normal + extras. El fijo se divide entre los días de"
                + " referencia y se descuenta por cada jornada trabajada. No hay tasas automáticas"
                + " de AFP ni aportes del empleador."));
    body.addView(deductions);
    LinearLayout vars = card();
    heading(vars, "chart", "Ingresos variables (opcional)");
    Switch tips = toggle(vars, "Propinas", s.tips),
        comm = toggle(vars, "Comisiones", s.commissions),
        bonuses = toggle(vars, "Bonos", s.bonuses);
    vars.addView(label("Desactivar una opción no borra importes de tu historial."));
    body.addView(vars);
    body.addView(
        button(
            "Guardar configuración",
            true,
            () -> {
              try {
                Storage.Settings n = new Storage.Settings();
                n.name = val("name");
                n.job = val("job");
                n.period = (String) period.getSelectedItem();
                n.tips = tips.isChecked();
                n.commissions = comm.isChecked();
                n.bonuses = bonuses.isChecked();
                Payroll.Policy q = n.policy;
                q.payMode = new String[] {"HOUR", "DAY", "MONTH"}[mode.getSelectedItemPosition()];
                q.salary = amount("salary");
                q.normalMinutes = amount("hours").multiply(BigDecimal.valueOf(60)).intValueExact();
                q.daysPerMonth = integer("days");
                q.extraFirst = amount("first");
                q.extraLater = amount("later");
                q.pension = amount("pension");
                q.insurance = amount("insurance");
                q.otherPercent = amount("other");
                q.fixedMonthly = amount("fixed");
                q.taxVariables = taxable.isChecked();
                q.validate();
                if (q.salary.signum() == 0)
                  throw new IllegalArgumentException("Introduce un sueldo mayor que cero.");
                db.saveSettings(n);
                screen = "tab";
                showTab(0);
                toast("Configuración guardada");
              } catch (Exception e) {
                error(e.getMessage() == null ? "Revisa los valores introducidos." : e.getMessage());
              }
            }));
    LinearLayout info = card();
    heading(info, "history", "Cómo usar App Trabajador");
    info.addView(
        label(
            "1. Configura tu sueldo y descuentos.\n"
                + "2. Marca entrada y salida o registra una jornada manual.\n"
                + "3. Revisa calendario e historial.\n"
                + "4. Exporta un PDF desde Historial.\n\n"
                + "Una jornada por fecha de inicio. Los turnos nocturnos se asignan a esa fecha."
                + " Los descansos y ausencias no generan pagos automáticos.\n\n"
                + "Versión 0.2.0 · Prueba\n"
                + "Almacenamiento local, sin conexión. Desinstalar o borrar los datos elimina tu"
                + " historial; exporta tus reportes antes."));
    body.addView(info);
  }

  void reportDialog() {
    final LocalDate[] from = {month.atDay(1)}, to = {month.atEndOfMonth()};
    LinearLayout c = vertical();
    c.setPadding(dp(20), dp(10), dp(20), dp(10));
    Storage.Settings cfg = db.settings();
    int initial = cfg.period.equals("Semanal") ? 0 : cfg.period.equals("Quincenal") ? 1 : 2;
    Spinner period =
        spinner(
            c,
            "Periodo del reporte",
            new String[] {
              "Semana (lunes a domingo)",
              "Quincena (1–15 / 16–fin)",
              "Mes seleccionado",
              "Personalizado"
            },
            initial);
    Button first = button("Desde: " + from[0], false, () -> {}),
        last = button("Hasta: " + to[0], false, () -> {});
    first.setOnClickListener(
        v ->
            pickDate(
                from[0],
                d -> {
                  from[0] = d;
                  first.setText("Desde: " + d);
                  period.setSelection(3);
                }));
    last.setOnClickListener(
        v ->
            pickDate(
                to[0],
                d -> {
                  to[0] = d;
                  last.setText("Hasta: " + d);
                  period.setSelection(3);
                }));
    c.addView(first);
    c.addView(last);
    c.addView(label("Incluye resumen, jornada por jornada, tarifas, ingresos y descuentos."));
    period.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            LocalDate base = month.equals(YearMonth.now()) ? LocalDate.now() : month.atDay(1);
            if (pos == 0) {
              from[0] = base.minusDays(base.getDayOfWeek().getValue() - 1);
              to[0] = from[0].plusDays(6);
            }
            if (pos == 1) {
              from[0] = month.atDay(base.getDayOfMonth() <= 15 ? 1 : 16);
              to[0] = base.getDayOfMonth() <= 15 ? month.atDay(15) : month.atEndOfMonth();
            }
            if (pos == 2) {
              from[0] = month.atDay(1);
              to[0] = month.atEndOfMonth();
            }
            first.setText("Desde: " + from[0]);
            last.setText("Hasta: " + to[0]);
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Exportar reporte PDF")
            .setView(c)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar PDF", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    b -> {
                      if (to[0].isBefore(from[0])
                          || ChronoUnit.DAYS.between(from[0], to[0]) > 366) {
                        error("Elige un rango válido de hasta 367 días.");
                        return;
                      }
                      if (db.range(from[0].toString(), to[0].toString()).isEmpty()) {
                        error("No hay registros en ese periodo.");
                        return;
                      }
                      reportFrom = from[0];
                      reportTo = to[0];
                      db.prefs
                          .edit()
                          .putString("reportFrom", reportFrom.toString())
                          .putString("reportTo", reportTo.toString())
                          .apply();
                      Intent i =
                          new Intent(Intent.ACTION_CREATE_DOCUMENT)
                              .setType("application/pdf")
                              .addCategory(Intent.CATEGORY_OPENABLE)
                              .putExtra(
                                  Intent.EXTRA_TITLE,
                                  "App-Trabajador_" + from[0] + "_" + to[0] + ".pdf");
                      try {
                        startActivityForResult(i, PDF);
                        dialog.dismiss();
                      } catch (ActivityNotFoundException e) {
                        error("No hay un selector de archivos disponible en este teléfono.");
                      }
                    }));
    dialog.show();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != PDF || result != RESULT_OK || data == null || data.getData() == null) return;
    try {
      LocalDate from = LocalDate.parse(db.prefs.getString("reportFrom", "")),
          to = LocalDate.parse(db.prefs.getString("reportTo", ""));
      List<Payroll.Shift> shifts = db.range(from.toString(), to.toString());
      Storage.Settings cfg = db.settings();
      Uri uri = data.getData();
      toast("Generando PDF…");
      new Thread(
              () -> {
                try (java.io.OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                  if (out == null) throw new java.io.IOException("No se pudo abrir el archivo.");
                  PdfReport.write(out, cfg, shifts, from, to);
                  runOnUiThread(
                      () -> {
                        if (!isFinishing() && !isDestroyed())
                          new AlertDialog.Builder(this)
                              .setTitle("PDF guardado")
                              .setMessage("El reporte se guardó en la ubicación que elegiste.")
                              .setNegativeButton("Cerrar", null)
                              .setPositiveButton(
                                  "Abrir",
                                  (d, w) -> {
                                    try {
                                      startActivity(
                                          new Intent(Intent.ACTION_VIEW)
                                              .setDataAndType(uri, "application/pdf")
                                              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                                    } catch (ActivityNotFoundException e) {
                                      error(
                                          "Instala un lector PDF o abre el archivo desde"
                                              + " Archivos.");
                                    }
                                  })
                              .show();
                      });
                } catch (Exception e) {
                  runOnUiThread(
                      () -> {
                        if (!isDestroyed()) error("No se pudo guardar el PDF: " + e.getMessage());
                      });
                }
              },
              "pdf-export")
          .start();
    } catch (Exception e) {
      error(e.getMessage());
    }
  }

  void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
  }

  void error(String s) {
    new AlertDialog.Builder(this)
        .setTitle("Revisa estos datos")
        .setMessage(s)
        .setPositiveButton("Entendido", null)
        .show();
  }
}
