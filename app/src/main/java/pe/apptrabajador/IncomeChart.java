package pe.apptrabajador;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.math.BigDecimal;
import java.util.List;

public final class IncomeChart extends View {
    private final List<BigDecimal> amounts; private final List<String> labels;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public IncomeChart(Context c,List<BigDecimal> amounts,List<String> labels){super(c);this.amounts=amounts;this.labels=labels;StringBuilder a=new StringBuilder("Ingresos netos mensuales. ");for(int i=0;i<amounts.size();i++)a.append(labels.get(i)).append(": ").append(Payroll.cash(amounts.get(i))).append(". ");setContentDescription(a.toString());setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density;float top=28*d,bottom=getHeight()-34*d,slot=getWidth()/4f;double max=1,min=0;for(BigDecimal v:amounts){max=Math.max(max,v.doubleValue());min=Math.min(min,v.doubleValue());}float base=(float)(top+(bottom-top)*max/(max-min));p.setColor(0xffdbe6f4);p.setStrokeWidth(d);c.drawLine(0,base,getWidth(),base,p);p.setTextAlign(Paint.Align.CENTER);for(int i=0;i<4;i++){float x=slot*(i+.5f),y=(float)(base-amounts.get(i).doubleValue()/(max-min)*(bottom-top));p.setColor(amounts.get(i).signum()<0?0xffed8da2:i==3?0xff087cfa:0xff99ceff);if(amounts.get(i).signum()!=0)c.drawRoundRect(x-slot*.31f,Math.min(base,y),x+slot*.31f,Math.max(base,y),5*d,5*d,p);p.setColor(0xff102445);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));String val=Payroll.cash(amounts.get(i));float size=11*d;p.setTextSize(size);while(p.measureText(val)>slot-4*d&&size>7*d)p.setTextSize(size-=.5f*d);float ty=amounts.get(i).signum()<0?Math.min(getHeight()-20*d,y+13*d):y-8*d;c.drawText(val,x,ty,p);p.setTypeface(Typeface.DEFAULT);p.setTextSize(12*d);p.setColor(0xff61738e);c.drawText(labels.get(i),x,getHeight()-4*d,p);}}
}
