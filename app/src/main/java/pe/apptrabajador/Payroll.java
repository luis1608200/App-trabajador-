package pe.apptrabajador;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Pure calculation engine. No Android, network or assumed payroll regime. */
public final class Payroll {
  private Payroll() {}

  public static final BigDecimal ZERO = new BigDecimal("0.00");

  public static BigDecimal decimal(String value) {
    try {
      String s = value.trim().replace(',', '.');
      if (!s.matches("[0-9]+(\\.[0-9]{1,4})?")) throw new NumberFormatException();
      BigDecimal n = new BigDecimal(s);
      if (n.compareTo(new BigDecimal("100000000")) > 0) throw new NumberFormatException();
      return n;
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Introduce un número válido, sin separador de miles.");
    }
  }

  public static BigDecimal money(BigDecimal n) {
    return n.setScale(2, RoundingMode.HALF_UP);
  }

  public static class Policy {
    public String payMode = "HOUR";
    public BigDecimal salary = ZERO;
    public int normalMinutes = 480, daysPerMonth = 30;
    public BigDecimal extraFirst = new BigDecimal("25"), extraLater = new BigDecimal("35");
    public BigDecimal pension = ZERO, insurance = ZERO, otherPercent = ZERO, fixedMonthly = ZERO;
    public boolean taxVariables = false;

    public void validate() {
      if (!payMode.equals("HOUR") && !payMode.equals("DAY") && !payMode.equals("MONTH"))
        fail("Modalidad de sueldo inválida.");
      if (normalMinutes < 60 || normalMinutes > 720)
        fail("La jornada normal debe ser de 1 a 12 horas.");
      if (daysPerMonth < 1 || daysPerMonth > 31)
        fail("Los días de referencia deben estar entre 1 y 31.");
      for (BigDecimal n :
          new BigDecimal[] {
            salary, fixedMonthly, pension, insurance, otherPercent, extraFirst, extraLater
          })
        if (n == null || n.signum() < 0 || n.compareTo(new BigDecimal("100000000")) > 0)
          fail("Importe fuera de rango.");
      if (pension.add(insurance).add(otherPercent).compareTo(new BigDecimal("100")) > 0)
        fail("Los descuentos porcentuales no pueden superar el 100%.");
      if (extraFirst.compareTo(new BigDecimal("500")) > 0
          || extraLater.compareTo(new BigDecimal("500")) > 0) fail("La sobretasa máxima es 500%.");
    }

    public BigDecimal hourly() {
      validate();
      BigDecimal rate = salary;
      if (payMode.equals("MONTH"))
        rate = rate.divide(BigDecimal.valueOf(daysPerMonth), 12, RoundingMode.HALF_UP);
      if (!payMode.equals("HOUR"))
        rate =
            rate.multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(normalMinutes), 12, RoundingMode.HALF_UP);
      return rate;
    }
  }

  public static class Shift {
    public String date = LocalDate.now().toString(), kind = "WORK", note = "";
    public int start = 420, end = 1020, breakMinutes = 60;
    public boolean nextDay = false;
    public BigDecimal tips = ZERO, commission = ZERO, bonus = ZERO, deduction = ZERO;
    public Policy policy = new Policy();

    public void validate() {
      LocalDate.parse(date);
      if (!kind.equals("WORK") && !kind.equals("REST") && !kind.equals("ABSENCE"))
        fail("Tipo de día inválido.");
      policy.validate();
      if (note.length() > 500) fail("La nota admite hasta 500 caracteres.");
      if (!kind.equals("WORK")) return;
      if (start < 0 || start >= 1440 || end < 0 || end >= 1440) fail("Hora inválida.");
      int duration = end - start + (nextDay ? 1440 : 0);
      if (duration <= 0 || duration > 1440)
        fail("Revisa entrada y salida. Para un turno nocturno activa «Salida al día siguiente».");
      if (breakMinutes < 0 || breakMinutes >= duration)
        fail("El descanso debe ser menor que la duración del turno.");
      for (BigDecimal n : new BigDecimal[] {tips, commission, bonus, deduction})
        if (n == null || n.signum() < 0 || n.compareTo(new BigDecimal("100000000")) > 0)
          fail("Los importes deben ser positivos o cero.");
    }
  }

  public static class Result {
    public int normalMinutes, extraMinutes, totalMinutes;
    public BigDecimal normal = ZERO, extra = ZERO, variables = ZERO, gross = ZERO;
    public BigDecimal pension = ZERO,
        insurance = ZERO,
        other = ZERO,
        fixed = ZERO,
        deductions = ZERO,
        net = ZERO;

    public void add(Result r) {
      normalMinutes += r.normalMinutes;
      extraMinutes += r.extraMinutes;
      totalMinutes += r.totalMinutes;
      normal = normal.add(r.normal);
      extra = extra.add(r.extra);
      variables = variables.add(r.variables);
      gross = gross.add(r.gross);
      pension = pension.add(r.pension);
      insurance = insurance.add(r.insurance);
      other = other.add(r.other);
      fixed = fixed.add(r.fixed);
      deductions = deductions.add(r.deductions);
      net = net.add(r.net);
    }
  }

  public static Result calculate(Shift s) {
    s.validate();
    Result r = new Result();
    if (!s.kind.equals("WORK")) return r;
    Policy p = s.policy;
    r.totalMinutes = s.end - s.start + (s.nextDay ? 1440 : 0) - s.breakMinutes;
    r.normalMinutes = Math.min(r.totalMinutes, p.normalMinutes);
    r.extraMinutes = Math.max(0, r.totalMinutes - p.normalMinutes);
    BigDecimal perMinute = p.hourly().divide(BigDecimal.valueOf(60), 12, RoundingMode.HALF_UP);
    r.normal = money(perMinute.multiply(BigDecimal.valueOf(r.normalMinutes)));
    BigDecimal first = BigDecimal.ONE.add(p.extraFirst.movePointLeft(2));
    BigDecimal later = BigDecimal.ONE.add(p.extraLater.movePointLeft(2));
    r.extra =
        money(
            perMinute
                .multiply(BigDecimal.valueOf(Math.min(120, r.extraMinutes)))
                .multiply(first)
                .add(
                    perMinute
                        .multiply(BigDecimal.valueOf(Math.max(0, r.extraMinutes - 120)))
                        .multiply(later)));
    r.variables = money(s.tips).add(money(s.commission)).add(money(s.bonus));
    r.gross = r.normal.add(r.extra).add(r.variables);
    BigDecimal taxable = r.normal.add(r.extra).add(p.taxVariables ? r.variables : ZERO);
    r.pension = money(taxable.multiply(p.pension.movePointLeft(2)));
    r.insurance = money(taxable.multiply(p.insurance.movePointLeft(2)));
    r.other = money(taxable.multiply(p.otherPercent.movePointLeft(2))).add(money(s.deduction));
    r.fixed = p.fixedMonthly.divide(BigDecimal.valueOf(p.daysPerMonth), 2, RoundingMode.HALF_UP);
    r.deductions = r.pension.add(r.insurance).add(r.other).add(r.fixed);
    r.net =
        r.gross.subtract(r.deductions); // Do not hide an advance larger than the day's earnings.
    return r;
  }

  static void fail(String text) {
    throw new IllegalArgumentException(text);
  }

  public static String duration(int minutes) {
    return minutes / 60
        + " h "
        + String.format(java.util.Locale.ROOT, "%02d", minutes % 60)
        + " min";
  }

  public static String cash(BigDecimal n) {
    return "S/ " + money(n).toPlainString();
  }

  public static String time(int minute) {
    return String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
  }
}
