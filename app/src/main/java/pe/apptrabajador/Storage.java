package pe.apptrabajador;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/** Versioned local SQLite records; each day keeps the payroll rules used to save it. */
public final class Storage extends SQLiteOpenHelper {
  final SharedPreferences prefs;

  public Storage(Context c) {
    super(c, "trabajador.db", null, 1);
    prefs = c.getSharedPreferences("trabajador", Context.MODE_PRIVATE);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE shifts (date TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int old, int next) {
    throw new IllegalStateException("Migración de datos no disponible.");
  }

  public static class Settings {
    public String name = "", job = "", period = "Mensual";
    public boolean tips = false, commissions = false, bonuses = false;
    public Payroll.Policy policy = new Payroll.Policy();
  }

  public Settings settings() {
    Settings s = new Settings();
    String raw = prefs.getString("settings", null);
    if (raw == null) return s;
    try {
      JSONObject j = new JSONObject(raw);
      s.name = j.optString("name");
      s.job = j.optString("job");
      s.period = j.optString("period", "Mensual");
      s.tips = j.optBoolean("tips");
      s.commissions = j.optBoolean("commissions");
      s.bonuses = j.optBoolean("bonuses");
      s.policy = readPolicy(j.getJSONObject("policy"));
      return s;
    } catch (JSONException e) {
      throw new IllegalStateException("No se puede leer la configuración guardada.", e);
    }
  }

  public void saveSettings(Settings s) {
    s.policy.validate();
    try {
      JSONObject j =
          new JSONObject()
              .put("name", s.name)
              .put("job", s.job)
              .put("period", s.period)
              .put("tips", s.tips)
              .put("commissions", s.commissions)
              .put("bonuses", s.bonuses)
              .put("policy", policyJson(s.policy));
      if (!prefs.edit().putString("settings", j.toString()).commit())
        throw new IllegalStateException("No se pudieron guardar los ajustes.");
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  static JSONObject policyJson(Payroll.Policy p) throws JSONException {
    return new JSONObject()
        .put("payMode", p.payMode)
        .put("salary", p.salary.toPlainString())
        .put("normalMinutes", p.normalMinutes)
        .put("daysPerMonth", p.daysPerMonth)
        .put("extraFirst", p.extraFirst.toPlainString())
        .put("extraLater", p.extraLater.toPlainString())
        .put("pension", p.pension.toPlainString())
        .put("insurance", p.insurance.toPlainString())
        .put("otherPercent", p.otherPercent.toPlainString())
        .put("fixedMonthly", p.fixedMonthly.toPlainString())
        .put("taxVariables", p.taxVariables);
  }

  static Payroll.Policy readPolicy(JSONObject j) throws JSONException {
    Payroll.Policy p = new Payroll.Policy();
    p.payMode = j.getString("payMode");
    p.salary = Payroll.decimal(j.getString("salary"));
    p.normalMinutes = j.getInt("normalMinutes");
    p.daysPerMonth = j.getInt("daysPerMonth");
    p.extraFirst = Payroll.decimal(j.getString("extraFirst"));
    p.extraLater = Payroll.decimal(j.getString("extraLater"));
    p.pension = Payroll.decimal(j.getString("pension"));
    p.insurance = Payroll.decimal(j.getString("insurance"));
    p.otherPercent = Payroll.decimal(j.getString("otherPercent"));
    p.fixedMonthly = Payroll.decimal(j.getString("fixedMonthly"));
    p.taxVariables = j.optBoolean("taxVariables");
    p.validate();
    return p;
  }

  static JSONObject json(Payroll.Shift s) throws JSONException {
    return new JSONObject()
        .put("schema", 1)
        .put("date", s.date)
        .put("kind", s.kind)
        .put("note", s.note)
        .put("start", s.start)
        .put("end", s.end)
        .put("break", s.breakMinutes)
        .put("nextDay", s.nextDay)
        .put("tips", s.tips.toPlainString())
        .put("commission", s.commission.toPlainString())
        .put("bonus", s.bonus.toPlainString())
        .put("deduction", s.deduction.toPlainString())
        .put("policy", policyJson(s.policy));
  }

  static Payroll.Shift parse(String raw) {
    try {
      JSONObject j = new JSONObject(raw);
      if (j.getInt("schema") != 1) throw new JSONException("Versión incompatible");
      Payroll.Shift s = new Payroll.Shift();
      s.date = j.getString("date");
      s.kind = j.getString("kind");
      s.note = j.optString("note");
      s.start = j.getInt("start");
      s.end = j.getInt("end");
      s.breakMinutes = j.getInt("break");
      s.nextDay = j.getBoolean("nextDay");
      s.tips = Payroll.decimal(j.getString("tips"));
      s.commission = Payroll.decimal(j.getString("commission"));
      s.bonus = Payroll.decimal(j.getString("bonus"));
      s.deduction = Payroll.decimal(j.getString("deduction"));
      s.policy = readPolicy(j.getJSONObject("policy"));
      s.validate();
      return s;
    } catch (JSONException e) {
      throw new IllegalStateException(
          "No se puede leer un registro. Los datos no se han borrado.", e);
    }
  }

  public void save(Payroll.Shift s) {
    s.validate();
    try {
      ContentValues v = new ContentValues();
      v.put("date", s.date);
      v.put("payload", json(s).toString());
      if (getWritableDatabase()
              .insertWithOnConflict("shifts", null, v, SQLiteDatabase.CONFLICT_REPLACE)
          == -1) throw new IllegalStateException("No se pudo guardar el registro.");
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  public Payroll.Shift get(String date) {
    try (Cursor c =
        getReadableDatabase()
            .query(
                "shifts",
                new String[] {"payload"},
                "date=?",
                new String[] {date},
                null,
                null,
                null)) {
      return c.moveToFirst() ? parse(c.getString(0)) : null;
    }
  }

  public List<Payroll.Shift> range(String from, String to) {
    List<Payroll.Shift> list = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(
                "shifts",
                new String[] {"payload"},
                "date>=? AND date<=?",
                new String[] {from, to},
                null,
                null,
                "date DESC")) {
      while (c.moveToNext()) list.add(parse(c.getString(0)));
    }
    return list;
  }

  public void delete(String date) {
    getWritableDatabase().delete("shifts", "date=?", new String[] {date});
  }

  public static Payroll.Result total(List<Payroll.Shift> shifts) {
    Payroll.Result r = new Payroll.Result();
    for (Payroll.Shift s : shifts) r.add(Payroll.calculate(s));
    return r;
  }
}
