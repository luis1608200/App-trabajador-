package pe.apptrabajador;

import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Paginated offline A4 report. Output supplied by Android's document picker. */
public final class PdfReport implements AutoCloseable {
  final PdfDocument pdf = new PdfDocument();
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  PdfDocument.Page page;
  Canvas canvas;
  int number = 0;
  float y;

  void newPage() {
    finishPage();
    page = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, ++number).create());
    canvas = page.getCanvas();
    paint.setColor(0xff087cfa);
    canvas.drawRect(0, 0, 595, 70, paint);
    paint.setColor(Color.WHITE);
    paint.setTextSize(21);
    paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    canvas.drawText("App Trabajador", 36, 36, paint);
    paint.setTextSize(10);
    canvas.drawText("REPORTE PERSONAL DE JORNADAS E INGRESOS", 36, 55, paint);
    y = 96;
  }

  void finishPage() {
    if (page == null) return;
    paint.setColor(0xff61738e);
    paint.setTextSize(9);
    paint.setTypeface(Typeface.DEFAULT);
    canvas.drawText(
        "Estimación según tus registros · No sustituye una boleta de pago", 36, 813, paint);
    canvas.drawText("Página " + number, 510, 813, paint);
    pdf.finishPage(page);
    page = null;
  }

  void ensure(float height) {
    if (page == null || y + height > 786) newPage();
  }

  void text(String s, int size, boolean bold) {
    paint.setTextSize(size);
    paint.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    paint.setColor(0xff102445);
    String[] paragraphs = s.replace('\t', ' ').split("\n", -1);
    for (String para : paragraphs) {
      String rest = para;
      do {
        int count = paint.breakText(rest, true, 523, null);
        if (count == 0 && rest.length() > 0) count = 1;
        int last = rest.lastIndexOf(' ', Math.max(0, count - 1));
        if (count < rest.length() && last > 0) count = last;
        String line = rest.substring(0, count);
        ensure(size + 7);
        paint.setTextSize(size);
        paint.setTypeface(
            Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        paint.setColor(0xff102445);
        canvas.drawText(line, 36, y, paint);
        y += size + 7;
        rest = rest.substring(count).trim();
      } while (!rest.isEmpty());
    }
  }

  void pair(String label, BigDecimal amount) {
    ensure(21);
    paint.setTextSize(11);
    paint.setTypeface(Typeface.DEFAULT);
    paint.setColor(0xff61738e);
    canvas.drawText(label, 42, y, paint);
    paint.setColor(0xff102445);
    paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    String value = Payroll.cash(amount);
    canvas.drawText(value, 553 - paint.measureText(value), y, paint);
    y += 21;
  }

  void section(String s) {
    ensure(48);
    y += 10;
    text(s, 14, true);
    paint.setColor(0xffdce9f7);
    canvas.drawLine(36, y - 5, 559, y - 5, paint);
    y += 5;
  }

  static String h(int minutes) {
    return Payroll.duration(minutes);
  }

  public static void write(
      OutputStream out,
      Storage.Settings settings,
      List<Payroll.Shift> shifts,
      LocalDate from,
      LocalDate to)
      throws IOException {
    try (PdfReport r = new PdfReport()) {
      r.newPage();
      r.text("Periodo: " + from + " al " + to, 14, true);
      if (!settings.name.isEmpty()) r.text(settings.name, 13, true);
      if (!settings.job.isEmpty()) r.text("Trabajo: " + settings.job, 11, false);
      r.text(
          "Forma de pago configurada: " + settings.period + " · Generado: " + LocalDate.now(),
          10,
          false);
      Payroll.Result total = Storage.total(shifts);
      int worked = 0, rest = 0, absence = 0;
      for (Payroll.Shift s : shifts) {
        if (s.kind.equals("WORK")) worked++;
        else if (s.kind.equals("REST")) rest++;
        else absence++;
      }
      r.section("Resumen del periodo");
      r.text(worked + " jornadas · " + rest + " descansos · " + absence + " ausencias", 11, false);
      r.text(
          "Normales: " + h(total.normalMinutes) + " · Extras: " + h(total.extraMinutes), 11, false);
      r.pair("Horas normales", total.normal);
      r.pair("Horas extra", total.extra);
      r.pair("Propinas + comisiones + bonos", total.variables);
      r.pair("Ingreso bruto", total.gross);
      r.pair("AFP / ONP", total.pension);
      r.pair("Seguro", total.insurance);
      r.pair("Otros porcentajes y adelantos", total.other);
      r.pair("Fijos prorrateados", total.fixed);
      r.pair("Total descuentos", total.deductions);
      r.pair("NETO ESTIMADO", total.net);
      r.section("Cómo interpretar el cálculo");
      r.text(
          "Los importes suman las jornadas guardadas con sus tarifas históricas. El sueldo mensual"
              + " se convierte a tarifa por hora usando los días de referencia y las horas"
              + " normales. Descansos y ausencias no generan ingresos automáticos. No incluye"
              + " beneficios legales, feriados ni reglas especiales por sector.",
          10,
          false);
      r.text(
          "Cada componente diario se redondea a céntimos. Un neto negativo refleja descuentos o"
              + " adelantos mayores al ingreso del día. Los turnos nocturnos corresponden a su"
              + " fecha de entrada. Un PDF no permite restaurar la base de datos.",
          10,
          false);
      List<Payroll.Shift> ordered = new ArrayList<>(shifts);
      ordered.sort(Comparator.comparing(s -> s.date));
      for (Payroll.Shift s : ordered) {
        r.ensure(175);
        r.section(
            s.date
                + " · "
                + (s.kind.equals("WORK")
                    ? "Trabajado"
                    : s.kind.equals("REST") ? "Descanso" : "Ausencia"));
        if (s.kind.equals("WORK")) {
          Payroll.Result v = Payroll.calculate(s);
          Payroll.Policy p = s.policy;
          r.text(
              "Entrada "
                  + Payroll.time(s.start)
                  + " · Salida "
                  + Payroll.time(s.end)
                  + (s.nextDay ? " (día siguiente)" : "")
                  + " · Descanso "
                  + s.breakMinutes
                  + " min",
              10,
              false);
          r.text(
              "Trabajo efectivo: "
                  + h(v.totalMinutes)
                  + " · Normal: "
                  + h(v.normalMinutes)
                  + " · Extra: "
                  + h(v.extraMinutes),
              10,
              false);
          String mode =
              p.payMode.equals("MONTH")
                  ? "mensual"
                  : p.payMode.equals("DAY") ? "diario" : "por hora";
          r.text(
              "Sueldo "
                  + mode
                  + ": "
                  + Payroll.cash(p.salary)
                  + " · Días de referencia: "
                  + p.daysPerMonth
                  + " · Jornada normal: "
                  + h(p.normalMinutes),
              10,
              false);
          r.text(
              "Tarifa/h: S/ "
                  + p.hourly().setScale(4, java.math.RoundingMode.HALF_UP)
                  + " · Extras: primeras 2 h +"
                  + p.extraFirst
                  + "%; siguientes +"
                  + p.extraLater
                  + "%",
              10,
              false);
          r.text(
              "Normales "
                  + Payroll.cash(v.normal)
                  + " · Extras "
                  + Payroll.cash(v.extra)
                  + " · Propinas "
                  + Payroll.cash(s.tips)
                  + " · Comisiones "
                  + Payroll.cash(s.commission)
                  + " · Bonos "
                  + Payroll.cash(s.bonus),
              10,
              false);
          r.text(
              "Base porcentual: sueldo + extras"
                  + (p.taxVariables ? " + variables" : "")
                  + ". Pensión "
                  + p.pension
                  + "% = "
                  + Payroll.cash(v.pension)
                  + "; seguro "
                  + p.insurance
                  + "% = "
                  + Payroll.cash(v.insurance),
              10,
              false);
          r.text(
              "Otros "
                  + p.otherPercent
                  + "% + descuento del día "
                  + Payroll.cash(s.deduction)
                  + " = "
                  + Payroll.cash(v.other)
                  + ". Fijo "
                  + Payroll.cash(p.fixedMonthly)
                  + " / "
                  + p.daysPerMonth
                  + " = "
                  + Payroll.cash(v.fixed),
              10,
              false);
          r.text(
              "Bruto "
                  + Payroll.cash(v.gross)
                  + " − descuentos "
                  + Payroll.cash(v.deductions)
                  + " = neto "
                  + Payroll.cash(v.net),
              11,
              true);
        } else r.text("Sin ingreso o descuento automático.", 10, false);
        if (!s.note.isEmpty()) r.text("Nota: " + s.note, 10, false);
      }
      r.finishPage();
      r.pdf.writeTo(out);
    }
  }

  @Override
  public void close() {
    if (page != null) finishPage();
    pdf.close();
  }
}
