package pe.apptrabajador;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Local, offline persistence for work shifts. Times are minutes since midnight. */
public final class WorkLogStore {
    private static final String PREFS = "work_log_v1";
    private static final String KEY = "shifts";
    private final SharedPreferences prefs;

    public WorkLogStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static final class Shift {
        public String id;
        public String date; // ISO local date: yyyy-MM-dd
        public int startMinute, endMinute, breakMinutes;
        public double dailyPay, overtimeHourlyRate, tips, commission;
        public double discountPercent;

        public int workedMinutes() {
            int span = endMinute - startMinute;
            if (span < 0) span += 1440; // Overnight shift
            return Math.max(0, span - Math.max(0, breakMinutes));
        }
        public int regularMinutes() { return Math.min(workedMinutes(), 480); }
        public int overtimeMinutes() { return Math.max(0, workedMinutes() - 480); }
        public double gross() {
            double base = Math.max(0, dailyPay) * regularMinutes() / 480.0;
            return base + overtimeMinutes() / 60.0 * Math.max(0, overtimeHourlyRate)
                    + Math.max(0, tips) + Math.max(0, commission);
        }
        public double net() {
            double wages = Math.max(0, dailyPay) * regularMinutes() / 480.0
                    + overtimeMinutes() / 60.0 * Math.max(0, overtimeHourlyRate);
            double deduction = wages * Math.min(100, Math.max(0, discountPercent)) / 100.0;
            return Math.max(0, gross() - deduction);
        }
        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("date", date);
            o.put("start", startMinute); o.put("end", endMinute); o.put("break", breakMinutes);
            o.put("dailyPay", dailyPay); o.put("overtimeRate", overtimeHourlyRate);
            o.put("tips", tips); o.put("commission", commission);
            o.put("discount", discountPercent);
            return o;
        }
        static Shift fromJson(JSONObject o) {
            Shift s = new Shift();
            s.id = o.optString("id");
            s.date = o.optString("date");
            s.startMinute = o.optInt("start");
            s.endMinute = o.optInt("end");
            s.breakMinutes = o.optInt("break");
            s.dailyPay = o.optDouble("dailyPay");
            s.overtimeHourlyRate = o.optDouble("overtimeRate");
            s.tips = o.optDouble("tips");
            s.commission = o.optDouble("commission");
            s.discountPercent = o.optDouble("discount");
            return s;
        }
    }

    public synchronized List<Shift> all() {
        List<Shift> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) list.add(Shift.fromJson(obj));
            }
        } catch (JSONException ignored) { }
        return list;
    }

    public synchronized void save(Shift shift) {
        if (shift == null || shift.date == null || !shift.date.matches("\\d{4}-\\d{2}-\\d{2}"))
            throw new IllegalArgumentException("Invalid shift date");
        if (shift.startMinute < 0 || shift.startMinute >= 1440
                || shift.endMinute < 0 || shift.endMinute >= 1440
                || shift.breakMinutes < 0 || shift.breakMinutes > 1440)
            throw new IllegalArgumentException("Invalid shift time");
        if (shift.id == null || shift.id.isEmpty()) shift.id = java.util.UUID.randomUUID().toString();
        List<Shift> list = all();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (shift.id.equals(list.get(i).id)) { list.set(i, shift); replaced = true; break; }
        }
        if (!replaced) list.add(shift);
        persist(list);
    }

    public synchronized void delete(String id) {
        List<Shift> list = all();
        for (int i = list.size() - 1; i >= 0; i--)
            if (list.get(i).id.equals(id)) list.remove(i);
        persist(list);
    }

    private void persist(List<Shift> list) {
        JSONArray array = new JSONArray();
        try {
            for (Shift shift : list) array.put(shift.toJson());
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to serialize work logs", e);
        }
        if (!prefs.edit().putString(KEY, array.toString()).commit())
            throw new IllegalStateException("Unable to save work logs");
    }
}
