package pe.apptrabajador;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import org.junit.Test;

public class PayrollTest {
  Payroll.Shift shift() {
    Payroll.Shift s = new Payroll.Shift();
    s.policy.salary = new BigDecimal("10");
    return s;
  }

  void amount(String expected, BigDecimal actual) {
    assertEquals(new BigDecimal(expected), actual);
  }

  @Test
  public void regularAndFirstOvertime() {
    Payroll.Result r = Payroll.calculate(shift());
    assertEquals(540, r.totalMinutes);
    assertEquals(480, r.normalMinutes);
    assertEquals(60, r.extraMinutes);
    amount("80.00", r.normal);
    amount("12.50", r.extra);
    amount("92.50", r.net);
  }

  @Test
  public void tierBoundaryAndPartialMinutes() {
    Payroll.Shift s = shift();
    s.end = 19 * 60 + 30;
    Payroll.Result r = Payroll.calculate(s);
    assertEquals(210, r.extraMinutes);
    amount("45.25", r.extra);
    amount("125.25", r.net);
  }

  @Test
  public void monthlyAndDailyConversion() {
    Payroll.Shift s = shift();
    s.policy.payMode = "MONTH";
    s.policy.salary = new BigDecimal("2400");
    amount("92.50", Payroll.calculate(s).net);
    s.policy.payMode = "DAY";
    s.policy.salary = new BigDecimal("80");
    amount("92.50", Payroll.calculate(s).net);
  }

  @Test
  public void allDeductionsAndVariables() {
    Payroll.Shift s = shift();
    s.tips = new BigDecimal("20");
    s.commission = new BigDecimal("5");
    s.bonus = new BigDecimal("3");
    s.deduction = new BigDecimal("2");
    s.policy.pension = new BigDecimal("10");
    s.policy.insurance = new BigDecimal("2");
    s.policy.otherPercent = new BigDecimal("1");
    s.policy.fixedMonthly = new BigDecimal("30");
    Payroll.Result r = Payroll.calculate(s);
    amount("120.50", r.gross);
    amount("15.03", r.deductions);
    amount("105.47", r.net);
    s.policy.taxVariables = true;
    amount("101.83", Payroll.calculate(s).net);
  }

  @Test
  public void nightShift() {
    Payroll.Shift s = shift();
    s.start = 22 * 60;
    s.end = 7 * 60;
    s.nextDay = true;
    Payroll.Result r = Payroll.calculate(s);
    assertEquals(480, r.totalMinutes);
    amount("80.00", r.net);
  }

  @Test
  public void restAndAbsenceHaveNoEarnings() {
    Payroll.Shift s = shift();
    s.kind = "REST";
    amount("0.00", Payroll.calculate(s).net);
    s.kind = "ABSENCE";
    amount("0.00", Payroll.calculate(s).net);
  }

  @Test
  public void negativeNetIsNotHidden() {
    Payroll.Shift s = shift();
    s.deduction = new BigDecimal("100");
    amount("-7.50", Payroll.calculate(s).net);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectAmbiguousOvernight() {
    Payroll.Shift s = shift();
    s.end = 360;
    Payroll.calculate(s);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectEqualHours() {
    Payroll.Shift s = shift();
    s.end = s.start;
    Payroll.calculate(s);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectOver24Hours() {
    Payroll.Shift s = shift();
    s.nextDay = true;
    Payroll.calculate(s);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectBreakBeyondShift() {
    Payroll.Shift s = shift();
    s.breakMinutes = 600;
    Payroll.calculate(s);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectNegativeAmount() {
    Payroll.decimal("-1");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectNotNumber() {
    Payroll.decimal("NaN");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectExcessPercent() {
    Payroll.Shift s = shift();
    s.policy.pension = new BigDecimal("101");
    Payroll.calculate(s);
  }

  @Test
  public void decimalCommaAndRounding() {
    amount("12.35", Payroll.money(Payroll.decimal("12,345")));
  }

  @Test
  public void roundingAndMinuteAccuracy() {
    Payroll.Shift s = shift();
    s.end = 15 * 60 + 1;
    s.breakMinutes = 0;
    Payroll.Result r = Payroll.calculate(s);
    assertEquals(1, r.extraMinutes);
    amount("0.21", r.extra);
  }
}
