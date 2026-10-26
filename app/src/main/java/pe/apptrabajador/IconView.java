package pe.apptrabajador;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
/** Vector icons independent of the device emoji font. */
public final class IconView extends View {
 final String kind; final int color; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 public IconView(Context c,String kind,int color){super(c);this.kind=kind;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.translate(getWidth()*.12f,getHeight()*.12f);c.scale(getWidth()*.76f/24,getHeight()*.76f/24);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
 switch(kind){
 case "home":line(c,3,10,12,3,21,10,21,21,15,21,15,14,9,14,9,21,3,21,3,10);break;
 case "calendar":c.drawRoundRect(3,5,21,22,3,3,p);c.drawLine(3,10,21,10,p);c.drawLine(8,2,8,7,p);c.drawLine(16,2,16,7,p);c.drawCircle(8,15,1,p);c.drawCircle(16,15,1,p);c.drawCircle(8,19,1,p);break;
 case "history":c.drawRoundRect(5,2,20,22,2,2,p);c.drawLine(9,7,16,7,p);c.drawLine(9,12,16,12,p);c.drawLine(9,17,14,17,p);break;
 case "settings":c.drawCircle(12,12,7,p);c.drawCircle(12,12,2.5f,p);for(int a=0;a<8;a++){c.save();c.rotate(45*a,12,12);c.drawLine(12,2,12,5,p);c.restore();}break;
 case "chart":p.setStyle(Paint.Style.FILL);c.drawRoundRect(3,14,7,22,1,1,p);c.drawRoundRect(10,8,14,22,1,1,p);c.drawRoundRect(17,2,21,22,1,1,p);break;
 case "wallet":c.drawRoundRect(2,6,22,21,3,3,p);line(c,4,6,18,2,19,6);c.drawRoundRect(15,11,22,16,1,1,p);break;
 case "coffee":c.drawRoundRect(3,6,17,19,2,2,p);c.drawArc(13,7,23,16,-90,180,false,p);c.drawLine(2,22,20,22,p);c.drawLine(7,1,7,3,p);c.drawLine(12,1,12,3,p);break;
 case "sun":c.drawCircle(12,12,4,p);for(int a=0;a<8;a++){c.save();c.rotate(a*45,12,12);c.drawLine(12,2,12,5,p);c.restore();}break;
 case "tips":line(c,4,2,4,10,9,10,9,2);c.drawLine(6.5f,2,6.5f,22,p);line(c,19,22,19,2,15,8,15,12,19,12);break;
 case "edit":line(c,4,16,16,4,20,8,8,20,3,21,4,16);c.drawLine(13,7,17,11,p);break;
 case "plus":c.drawLine(12,4,12,20,p);c.drawLine(4,12,20,12,p);break;
 case "download":line(c,12,2,12,16);line(c,7,11,12,16,17,11);line(c,3,17,3,22,21,22,21,17);break;
 case "helmet":c.drawArc(4,5,20,23,180,180,false,p);c.drawLine(12,3,12,13,p);line(c,2,16,2,20,22,20,22,16);break;
 default:c.drawCircle(12,12,9,p);line(c,12,6,12,12,17,15);
 }c.restore();}
 private void line(Canvas c,float...xy){Path a=new Path();a.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)a.lineTo(xy[i],xy[i+1]);c.drawPath(a,p);}
}
