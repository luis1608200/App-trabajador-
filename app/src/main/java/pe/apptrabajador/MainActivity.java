package pe.apptrabajador;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BLUE = Color.rgb(24, 111, 235);
    private static final int BLUE_DARK = Color.rgb(12, 83, 202);
    private static final int INK = Color.rgb(25, 41, 67);
    private static final int MUTED = Color.rgb(105, 119, 142);
    private static final int BG = Color.rgb(245, 248, 253);
    private static final int PALE = Color.rgb(236, 244, 255);
    private static final int GREEN = Color.rgb(14, 166, 117);
    private static final int LINE = Color.rgb(226, 233, 244);
    private static final Locale ES = new Locale("es", "PE");
    private static final int EXPORT_PDF = 1420;

    private WorkLogStore store;
    private SharedPreferences config;
    private LinearLayout body;
    private Calendar calendarMonth = Calendar.getInstance();
    private Calendar exportMonth;
    private int tab = 0;

    private int dp(float x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radius)); return g;
    }
    private void background(View v, int color, int radius) { v.setBackground(shape(color, radius)); }
    private TextView text(String s, int size, boolean bold, int color) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(size); v.setTextColor(color);
        v.setFontFeatureSettings("kern"); v.setIncludeFontPadding(true);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private LinearLayout vertical() {
        LinearLayout x = new LinearLayout(this); x.setOrientation(LinearLayout.VERTICAL); return x;
    }
    private LinearLayout horizontal() {
        LinearLayout x = new LinearLayout(this); x.setOrientation(LinearLayout.HORIZONTAL);
        x.setGravity(Gravity.CENTER_VERTICAL); return x;
    }
    private LinearLayout.LayoutParams margins(int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(bottom); return p;
    }
    private LinearLayout card() {
        LinearLayout c = vertical();
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        background(c, Color.WHITE, 20);
        c.setElevation(dp(2));
        c.setLayoutParams(margins(14)); return c;
    }
    private void addText(LinearLayout parent, String s, int size, boolean bold, int color, int bottom) {
        TextView t = text(s, size, bold, color);
        t.setLayoutParams(margins(bottom)); parent.addView(t);
    }
    private TextView button(String label, int color, boolean filled, View.OnClickListener click) {
        TextView b = text(label, 15, true, filled ? Color.WHITE : color);
        b.setGravity(Gravity.CENTER); b.setPadding(dp(15),dp(12),dp(15),dp(12));
        background(b, filled ? color : PALE, 13);
        b.setMinHeight(dp(46)); b.setClickable(true); b.setOnClickListener(click); return b;
    }
    private String money(double x) { return String.format(ES, "S/ %.2f", x); }
    private String hours(int minutes) { return (minutes / 60) + " h " + (minutes % 60) + " min"; }
    private String clock(int minute) { return String.format(Locale.US, "%02d:%02d", minute / 60, minute % 60); }
    private String dateNow() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }
    private String monthId(Calendar c) { return new SimpleDateFormat("yyyy-MM", Locale.US).format(c.getTime()); }
    private String monthTitle(Calendar c) {
        String s = new SimpleDateFormat("MMMM yyyy", ES).format(c.getTime());
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    private double preference(String name, double fallback) {
        return Double.longBitsToDouble(config.getLong(name, Double.doubleToLongBits(fallback)));
    }
    private void putPreference(SharedPreferences.Editor e, String name, double val) {
        e.putLong(name, Double.doubleToLongBits(val));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new WorkLogStore(this);
        config = getSharedPreferences("worker_settings_v1", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        calendarMonth.set(Calendar.DAY_OF_MONTH, 1);
        home();
    }

    private void shell(int selected) {
        tab = selected;
        LinearLayout root = vertical();
        root.setBackgroundColor(BG);
        // Android 15 draws behind system bars; use real insets rather than a fixed margin.
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            v.setPadding(0, ins.getSystemWindowInsetTop(), 0, ins.getSystemWindowInsetBottom());
            return ins.consumeSystemWindowInsets();
        });
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        body = vertical();
        body.setPadding(dp(17), dp(18), dp(17), dp(20));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = horizontal();
        nav.setBackgroundColor(Color.WHITE);
        nav.setPadding(dp(4), dp(6), dp(4), dp(5));
        String[] labels = {"Inicio", "Calendario", "Historial", "Ajustes"};
        String[] icons = {"⌂", "▦", "▤", "⚙"};
        for (int i=0;i<4;i++) {
            final int index=i;
            LinearLayout item=vertical(); item.setGravity(Gravity.CENTER);
            TextView icon=text(icons[i], 23, true, i==selected ? BLUE : MUTED);
            icon.setGravity(Gravity.CENTER);
            TextView label=text(labels[i], 11, i==selected, i==selected ? BLUE : MUTED);
            label.setGravity(Gravity.CENTER);
            item.addView(icon); item.addView(label); item.setPadding(0,dp(3),0,dp(3));
            item.setOnClickListener(v -> openTab(index));
            nav.addView(item,new LinearLayout.LayoutParams(0,dp(57),1));
        }
        root.addView(nav);
        setContentView(root);
        root.requestApplyInsets();
    }
    private void openTab(int index) {
        if(index==0) home(); else if(index==1) calendar(); else if(index==2) history(); else settings();
    }
    private List<WorkLogStore.Shift> sortedShifts() {
        List<WorkLogStore.Shift> result = new ArrayList<>(store.all());
        Collections.sort(result, (a,b) -> {
            int byDate = b.date.compareTo(a.date);
            return byDate != 0 ? byDate : Integer.compare(b.startMinute, a.startMinute);
        });
        return result;
    }
    private WorkLogStore.Shift todayShift() {
        for (WorkLogStore.Shift s : sortedShifts()) if (dateNow().equals(s.date)) return s;
        return null;
    }
    private double dailyTotal(String day) {
        double total=0; for(WorkLogStore.Shift s:store.all()) if(s.date.equals(day)) total+=s.net();
        return total;
    }
    private double totalForMonth(String id) {
        double total=0; for(WorkLogStore.Shift s:store.all()) if(s.date.startsWith(id)) total+=s.net();
        return total;
    }
    private boolean dayHasShift(String date) {
        for(WorkLogStore.Shift s:store.all()) if(date.equals(s.date))return true;
        return false;
    }
    private void metric(LinearLayout row, String caption, String value) {
        LinearLayout cell=vertical(); cell.setPadding(dp(9),dp(12),dp(9),dp(12));
        cell.setGravity(Gravity.CENTER); background(cell, BG, 14);
        TextView a=text(value,16,true,INK); a.setGravity(Gravity.CENTER);
        TextView b=text(caption,12,false,MUTED); b.setGravity(Gravity.CENTER);
        cell.addView(a);cell.addView(b);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,-2,1);
        p.setMargins(dp(3),0,dp(3),0); row.addView(cell,p);
    }
    private void sectionTitle(String heading, String action, View.OnClickListener click) {
        LinearLayout row=horizontal();
        TextView title=text(heading,19,true,INK); row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null) {
            TextView right=text(action,13,true,BLUE);right.setPadding(dp(6),dp(8),dp(2),dp(8));
            right.setOnClickListener(click);row.addView(right);
        }
        body.addView(row,margins(12));
    }

    private void home() {
        shell(0);
        addText(body,"Hola 👋",29,true,INK,2);
        addText(body,new SimpleDateFormat("EEEE d 'de' MMMM", ES).format(new Date()),14,false,MUTED,23);
        WorkLogStore.Shift today = todayShift();
        LinearLayout register=card();
        LinearLayout header=horizontal();
        header.addView(text("◷  Registro de hoy",18,true,INK),new LinearLayout.LayoutParams(0,-2,1));
        TextView edit=text(today==null?"Agregar":"Editar",14,true,BLUE);
        edit.setPadding(dp(6),dp(5),dp(3),dp(5));
        edit.setOnClickListener(v->editShift(today,dateNow()));header.addView(edit);
        register.addView(header,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout metrics=horizontal(); LinearLayout.LayoutParams mp=margins(14);mp.topMargin=dp(15);
        metric(metrics,"Entrada",today==null?"--:--":clock(today.startMinute));
        metric(metrics,"Salida",today==null?"--:--":clock(today.endMinute));
        metric(metrics,"Descanso",today==null?"--":hours(today.breakMinutes));
        metrics.setOnClickListener(v->editShift(today,dateNow()));register.addView(metrics,mp);
        addText(register, today==null?"Toca «Registrar jornada» para ingresar tus horas.":"Toca Entrada, Salida o Descanso para modificarlos.",12,false,MUTED,11);
        register.addView(button(today==null?"+ Registrar jornada":"Modificar jornada",BLUE,true,v->editShift(today,dateNow())));
        body.addView(register);

        LinearLayout earn=card();earn.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{BLUE,BLUE_DARK}){{setCornerRadius(dp(20));}});
        addText(earn,"▣  GANANCIA ESTIMADA DE HOY",13,true,Color.rgb(225,236,255),8);
        addText(earn,money(dailyTotal(dateNow())),34,true,Color.WHITE,8);
        int minutes=0,extra=0;
        for(WorkLogStore.Shift s:store.all())if(dateNow().equals(s.date)){minutes+=s.workedMinutes();extra+=s.overtimeMinutes();}
        addText(earn,hours(minutes)+" trabajadas  ·  "+hours(extra)+" extras",13,false,Color.WHITE,2);
        body.addView(earn);

        sectionTitle("Mis ingresos","Ver historial",v->history());
        Calendar first=Calendar.getInstance();
        first.set(Calendar.DAY_OF_MONTH,1);
        Calendar[] last4=new Calendar[4];double[] totals=new double[4];
        double max=0;
        for(int i=0;i<4;i++){Calendar m=(Calendar)first.clone();m.add(Calendar.MONTH,i-3);last4[i]=m;totals[i]=totalForMonth(monthId(m));max=Math.max(max,totals[i]);}
        LinearLayout chart=card();
        addText(chart,"Últimos 4 meses",13,false,MUTED,12);
        LinearLayout bars=horizontal();bars.setGravity(Gravity.BOTTOM);
        for(int i=0;i<4;i++){
            LinearLayout col=vertical();col.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
            TextView amount=text(totals[i]==0?"—":money(totals[i]),11,true,i==3?BLUE:MUTED);
            amount.setGravity(Gravity.CENTER);col.addView(amount);
            View bar=new View(this);
            background(bar,i==3?BLUE:Color.rgb(165,199,248),9);
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(40),dp(max==0?8:Math.max(8,Math.round(100*totals[i]/max))));
            bp.topMargin=dp(8); col.addView(bar,bp);
            TextView label=text(new SimpleDateFormat("MMM",ES).format(last4[i].getTime()),12,false,MUTED);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(9);
            col.addView(label,lp);
            bars.addView(col,new LinearLayout.LayoutParams(0,dp(157),1));
        }
        chart.addView(bars);body.addView(chart);
        addText(body,"* Los importes son estimados según los valores que configures; no sustituyen una boleta de pago.",12,false,MUTED,0);
    }

    private void calendar() {
        shell(1);
        addText(body,"Calendario laboral",24,true,INK,15);
        LinearLayout c=card();
        LinearLayout monthbar=horizontal();
        TextView back=button("‹",BLUE,false,v->{calendarMonth.add(Calendar.MONTH,-1);calendar();});
        TextView forward=button("›",BLUE,false,v->{calendarMonth.add(Calendar.MONTH,1);calendar();});
        TextView title=text(monthTitle(calendarMonth),17,true,INK);title.setGravity(Gravity.CENTER);
        monthbar.addView(back);monthbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));monthbar.addView(forward);
        c.addView(monthbar,margins(12));
        GridLayout grid=new GridLayout(this);grid.setColumnCount(7);
        String[] week={"L","M","X","J","V","S","D"};
        for(String w:week){
            TextView cell=text(w,12,true,MUTED);cell.setGravity(Gravity.CENTER);
            grid.addView(cell,new ViewGroup.LayoutParams(dp(39),dp(34)));
        }
        Calendar m=(Calendar)calendarMonth.clone();m.set(Calendar.DAY_OF_MONTH,1);
        int offset=(m.get(Calendar.DAY_OF_WEEK)+5)%7;
        for(int i=0;i<offset;i++)grid.addView(new View(this),new ViewGroup.LayoutParams(dp(39),dp(46)));
        int count=m.getActualMaximum(Calendar.DAY_OF_MONTH);
        for(int day=1;day<=count;day++){
            final String date=monthId(m)+"-"+String.format(Locale.US,"%02d",day);
            boolean worked=dayHasShift(date);
            boolean isToday=date.equals(dateNow());
            TextView cell=text(""+day,14,isToday,worked?Color.WHITE:INK);
            cell.setGravity(Gravity.CENTER);
            if(worked)background(cell,GREEN,13);else if(isToday)background(cell,PALE,13);
            cell.setOnClickListener(v->showDay(date));
            grid.addView(cell,new ViewGroup.LayoutParams(dp(39),dp(46)));
        }
        HorizontalScrollView holder=new HorizontalScrollView(this);
        holder.setHorizontalScrollBarEnabled(false);holder.addView(grid);c.addView(holder);
        addText(c,"Verde: jornada registrada  ·  Toca un día para agregar o editar.",12,false,MUTED,0);
        body.addView(c);

        String id=monthId(calendarMonth);
        int normal=0,overtime=0,days=0;
        for(WorkLogStore.Shift s:store.all())if(s.date.startsWith(id)){normal+=s.regularMinutes();overtime+=s.overtimeMinutes();}
        for(int day=1;day<=count;day++)if(dayHasShift(id+"-"+String.format(Locale.US,"%02d",day)))days++;
        LinearLayout summary=card();
        addText(summary,"Resumen del mes",18,true,INK,13);
        summary.addView(infoRow("Días trabajados",""+days));
        summary.addView(infoRow("Horas normales",hours(normal)));
        summary.addView(infoRow("Horas extras",hours(overtime)));
        summary.addView(infoRow("Ingresos estimados",money(totalForMonth(id))));
        body.addView(summary);
    }
    private void showDay(String date) {
        List<WorkLogStore.Shift> shifts=new ArrayList<>();
        for(WorkLogStore.Shift s:sortedShifts())if(s.date.equals(date))shifts.add(s);
        if(shifts.isEmpty()){editShift(null,date);return;}
        String[] options=new String[shifts.size()+1];
        for(int i=0;i<shifts.size();i++)options[i]=clock(shifts.get(i).startMinute)+" - "+clock(shifts.get(i).endMinute)+"  ·  "+money(shifts.get(i).net());
        options[shifts.size()]="+ Nueva jornada";
        new AlertDialog.Builder(this).setTitle(date).setItems(options,(d,index)->{
            if(index==shifts.size()) editShift(null,date);else editShift(shifts.get(index),date);
        }).setNegativeButton("Cancelar",null).show();
    }
    private LinearLayout infoRow(String title,String value) {
        LinearLayout r=horizontal();r.setPadding(0,dp(9),0,dp(9));
        r.addView(text(title,14,false,MUTED),new LinearLayout.LayoutParams(0,-2,1));
        r.addView(text(value,14,true,INK));
        return r;
    }

    private void history(){
        shell(2);
        addText(body,"Historial",25,true,INK,5);
        addText(body,"Todas tus jornadas guardadas en este teléfono",13,false,MUTED,17);
        body.addView(button("+ Agregar jornada",BLUE,true,v->editShift(null,dateNow())),margins(16));
        List<WorkLogStore.Shift> list=sortedShifts();
        if(list.isEmpty()){
            LinearLayout empty=card();addText(empty,"Sin registros todavía",19,true,INK,8);
            addText(empty,"Registra tu entrada, salida y descanso para comenzar.",14,false,MUTED,0);
            body.addView(empty);return;
        }
        double total=0;int minutes=0;
        for(WorkLogStore.Shift s:list){total+=s.net();minutes+=s.workedMinutes();}
        LinearLayout summary=card();
        addText(summary,"Resumen de registros",17,true,INK,12);
        summary.addView(infoRow("Jornadas",String.valueOf(list.size())));
        summary.addView(infoRow("Tiempo trabajado",hours(minutes)));
        summary.addView(infoRow("Total estimado",money(total)));
        body.addView(summary);
        Calendar now=Calendar.getInstance();exportMonth=(Calendar)now.clone();
        body.addView(button("Exportar PDF del mes actual",BLUE,false,v->exportPdf()),margins(15));
        for(WorkLogStore.Shift s:list){
            LinearLayout entry=card();entry.addView(infoRow(s.date,money(s.net())));
            addText(entry,"◷  "+clock(s.startMinute)+" – "+clock(s.endMinute)+"  ·  Descanso "+hours(s.breakMinutes),13,false,MUTED,8);
            addText(entry,hours(s.regularMinutes())+" normales  ·  "+hours(s.overtimeMinutes())+" extras",13,false,MUTED,11);
            LinearLayout actions=horizontal();
            TextView edit=button("Editar",BLUE,false,v->editShift(s,s.date));
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);ap.rightMargin=dp(8);actions.addView(edit,ap);
            actions.addView(button("Eliminar",Color.rgb(198,51,65),false,v->confirmDelete(s)),new LinearLayout.LayoutParams(0,-2,1));
            entry.addView(actions);body.addView(entry);
        }
    }
    private void confirmDelete(WorkLogStore.Shift shift) {
        new AlertDialog.Builder(this).setTitle("Eliminar jornada")
            .setMessage("¿Eliminar el registro del "+shift.date+"? Esta acción no se puede deshacer.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Eliminar",(d,which)->{
                try{store.delete(shift.id);history();Toast.makeText(this,"Registro eliminado",Toast.LENGTH_SHORT).show();}
                catch(Exception e){error("No se pudo eliminar el registro");}
            }).show();
    }

    private EditText input(LinearLayout parent,String caption,String initial,boolean numeric){
        addText(parent,caption,13,true,INK,5);
        EditText e=new EditText(this);e.setSingleLine(true);e.setText(initial);
        e.setTextColor(INK);e.setTextSize(16);e.setPadding(dp(12),dp(9),dp(12),dp(9));
        background(e,BG,12);
        if(numeric)e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        parent.addView(e,margins(12));return e;
    }
    private double amount(EditText e) {
        double v=Double.parseDouble(e.getText().toString().trim().replace(',','.'));
        if(Double.isNaN(v)||Double.isInfinite(v)||v<0)throw new IllegalArgumentException("Importe inválido");
        return v;
    }
    private int clockValue(String value) {
        if(!value.matches("([01][0-9]|2[0-3]):[0-5][0-9]"))throw new IllegalArgumentException("Hora inválida");
        return Integer.parseInt(value.substring(0,2))*60+Integer.parseInt(value.substring(3));
    }
    private String checkDate(String value) throws Exception {
        if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException("Fecha inválida");
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.US);format.setLenient(false);
        Date d=format.parse(value);
        if(d==null||!format.format(d).equals(value))throw new IllegalArgumentException("Fecha inválida");
        return value;
    }
    private void attachDatePicker(EditText field) {
        field.setFocusable(false);
        field.setOnClickListener(v->{
            Calendar c=Calendar.getInstance();
            try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setLenient(false);c.setTime(f.parse(field.getText().toString()));}catch(Exception ignored){}
            new DatePickerDialog(this,(picker,y,m,d)->field.setText(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d)),
                c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
        });
    }
    private void attachTimePicker(EditText field) {
        field.setFocusable(false);
        field.setOnClickListener(v->{
            int min;try{min=clockValue(field.getText().toString());}catch(Exception e){min=480;}
            new TimePickerDialog(this,(picker,h,m)->field.setText(String.format(Locale.US,"%02d:%02d",h,m)),min/60,min%60,true).show();
        });
    }
    private void editShift(WorkLogStore.Shift current,String defaultDate){
        LinearLayout fields=vertical();fields.setPadding(dp(18),dp(8),dp(18),dp(4));
        ScrollView holder=new ScrollView(this);holder.addView(fields);
        EditText date=input(fields,"Fecha",current==null?defaultDate:current.date,false);attachDatePicker(date);
        EditText start=input(fields,"Hora de entrada",current==null?"07:00":clock(current.startMinute),false);attachTimePicker(start);
        EditText end=input(fields,"Hora de salida",current==null?"17:00":clock(current.endMinute),false);attachTimePicker(end);
        EditText rest=input(fields,"Descanso en minutos",current==null?"60":""+current.breakMinutes,true);
        EditText base=input(fields,"Jornal base por 8 h (S/)",current==null?moneyNumber(preference("dailyPay",62.80)):moneyNumber(current.dailyPay),true);
        EditText overtime=input(fields,"Tarifa de hora extra (S/)",current==null?moneyNumber(preference("overtimeRate",0)):moneyNumber(current.overtimeHourlyRate),true);
        EditText discount=input(fields,"Descuento (% sobre jornal y extras)",current==null?moneyNumber(preference("discount",0)):moneyNumber(current.discountPercent),true);
        EditText tips=null,commission=null;
        if(config.getBoolean("tipsEnabled",true))tips=input(fields,"Propinas (S/)",current==null?"0":moneyNumber(current.tips),true);
        if(config.getBoolean("commissionEnabled",true))commission=input(fields,"Comisiones (S/)",current==null?"0":moneyNumber(current.commission),true);
        final EditText tipsInput=tips,commissionInput=commission;
        addText(fields,"Los cálculos son estimados. Puedes cambiar las tarifas en Ajustes.",12,false,MUTED,4);
        AlertDialog dlg=new AlertDialog.Builder(this)
            .setTitle(current==null?"Registrar jornada":"Editar jornada")
            .setView(holder).setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",null).create();
        dlg.setOnShowListener(ignored->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                WorkLogStore.Shift s=new WorkLogStore.Shift();
                s.id=current==null?null:current.id;
                s.date=checkDate(date.getText().toString().trim());
                s.startMinute=clockValue(start.getText().toString().trim());
                s.endMinute=clockValue(end.getText().toString().trim());
                s.breakMinutes=Integer.parseInt(rest.getText().toString().trim());
                s.dailyPay=amount(base);s.overtimeHourlyRate=amount(overtime);
                s.discountPercent=amount(discount);
                s.tips=tipsInput==null?(current==null?0:current.tips):amount(tipsInput);
                s.commission=commissionInput==null?(current==null?0:current.commission):amount(commissionInput);
                int duration=(s.endMinute-s.startMinute+1440)%1440;
                if(duration==0||s.breakMinutes<0||s.breakMinutes>=duration||s.discountPercent>100)
                    throw new IllegalArgumentException("Verifica la duración, descanso o descuento");
                store.save(s);dlg.dismiss();openTab(tab);
                Toast.makeText(this,"Jornada guardada correctamente",Toast.LENGTH_SHORT).show();
            }catch(Exception err){
                error("Revisa fecha, horas, descanso e importes. La salida debe ser posterior a la entrada o del día siguiente.");
            }
        }));
        dlg.show();
    }
    private String moneyNumber(double n){return String.format(Locale.US,"%.2f",n);}
    private void error(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

    private void settings(){
        shell(3);
        addText(body,"Mi trabajo",25,true,INK,6);
        addText(body,"Configura los valores para calcular tus jornadas.",14,false,MUTED,18);
        LinearLayout card=card();
        addText(card,"Configuración de pago",18,true,INK,15);
        EditText pay=input(card,"Jornal por 8 horas (S/)",moneyNumber(preference("dailyPay",62.80)),true);
        EditText extra=input(card,"Hora extra (S/)",moneyNumber(preference("overtimeRate",0)),true);
        EditText discount=input(card,"Descuentos estimados (%)",moneyNumber(preference("discount",0)),true);
        CheckBox tips=new CheckBox(this);tips.setText("Mostrar propinas");tips.setTextColor(INK);
        tips.setChecked(config.getBoolean("tipsEnabled",true));card.addView(tips);
        CheckBox commissions=new CheckBox(this);commissions.setText("Mostrar comisiones");commissions.setTextColor(INK);
        commissions.setChecked(config.getBoolean("commissionEnabled",true));card.addView(commissions);
        addText(card,"Elige una tasa de descuento según tu AFP, ONP y demás condiciones. No se aplica automáticamente ninguna tasa legal.",12,false,MUTED,13);
        card.addView(button("Guardar configuración",BLUE,true,v->{
            try{
                double day=amount(pay),ov=amount(extra),de=amount(discount);
                if(de>100)throw new IllegalArgumentException("Descuento mayor a 100%");
                SharedPreferences.Editor ed=config.edit();putPreference(ed,"dailyPay",day);
                putPreference(ed,"overtimeRate",ov);putPreference(ed,"discount",de);
                ed.putBoolean("tipsEnabled",tips.isChecked()).putBoolean("commissionEnabled",commissions.isChecked());
                if(!ed.commit())throw new IllegalStateException("Error al guardar");
                Toast.makeText(this,"Configuración guardada",Toast.LENGTH_SHORT).show();home();
            }catch(Exception ex){error("Revisa los importes y el porcentaje de descuento.");}
        }));
        body.addView(card);
        LinearLayout about=card();
        addText(about,"Privacidad y funcionamiento",17,true,INK,9);
        addText(about,"Tus registros se guardan en este teléfono y no necesitan internet. Eliminar los datos de la aplicación también puede borrar tus registros. Exporta un PDF como respaldo.",13,false,MUTED,0);
        body.addView(about);
    }

    private void exportPdf(){
        exportMonth=Calendar.getInstance();
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE,"App-Trabajador-"+monthId(exportMonth)+".pdf");
        try{startActivityForResult(i,EXPORT_PDF);}catch(Exception e){error("No se pudo abrir el selector de archivos.");}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==EXPORT_PDF&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new IllegalStateException("No se pudo abrir el archivo");
                writePdf(out,monthId(exportMonth));
                Toast.makeText(this,"Reporte PDF guardado",Toast.LENGTH_LONG).show();
            }catch(Exception e){error("No se pudo crear el PDF.");}
        }
    }
    private void writePdf(OutputStream out,String period)throws Exception{
        List<WorkLogStore.Shift> shifts=new ArrayList<>();
        for(WorkLogStore.Shift s:sortedShifts())if(s.date.startsWith(period))shifts.add(s);
        PdfDocument pdf=new PdfDocument();
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int pageNumber=1,rowIndex=0;
        do{
            PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,pageNumber).create();
            PdfDocument.Page page=pdf.startPage(info);Canvas c=page.getCanvas();
            paint.setColor(INK);paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));paint.setTextSize(21);
            c.drawText("APP TRABAJADOR · REPORTE",34,52,paint);
            paint.setTextSize(14);paint.setTypeface(Typeface.DEFAULT);
            c.drawText("Mes: "+period+"   |   Página "+pageNumber,34,80,paint);
            paint.setColor(BLUE);paint.setStrokeWidth(2);c.drawLine(34,95,560,95,paint);
            int y=125;
            paint.setTextSize(11);
            while(rowIndex<shifts.size()&&y<755){
                WorkLogStore.Shift s=shifts.get(rowIndex++);
                paint.setColor(INK);paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
                c.drawText(s.date+"   "+clock(s.startMinute)+" - "+clock(s.endMinute),34,y,paint);
                paint.setTypeface(Typeface.DEFAULT);
                c.drawText("Trabajado: "+hours(s.workedMinutes())+"  |  Descanso: "+hours(s.breakMinutes),34,y+17,paint);
                c.drawText("Normal: "+hours(s.regularMinutes())+"  |  Extra: "+hours(s.overtimeMinutes())+"  |  Neto: "+money(s.net()),34,y+34,paint);
                paint.setColor(LINE);c.drawLine(34,y+44,560,y+44,paint);
                y+=62;
            }
            if(rowIndex==shifts.size()){
                paint.setColor(INK);paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));paint.setTextSize(14);
                c.drawText("TOTAL ESTIMADO: "+money(totalForMonth(period)),34,Math.min(790,y+20),paint);
            }
            paint.setTextSize(9);paint.setTypeface(Typeface.DEFAULT);paint.setColor(MUTED);
            c.drawText("Estimación personal, no equivale a una boleta oficial.",34,824,paint);
            pdf.finishPage(page);pageNumber++;
        }while(rowIndex<shifts.size());
        pdf.writeTo(out);pdf.close();
    }
}