package com.tmbagent.etadiagnostic;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    private TextView log;
    private volatile boolean running;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String stop = "413";
    private void say(String msg) { main.post(() -> {log.append(msg + "\n");}); }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(1); layout.setPadding(20,20,20,20);
        TextView title = new TextView(this); title.setText("Miralín ETA · Diagnóstico independiente\nEstación La Pau (413)"); title.setTextSize(21); layout.addView(title);
        Button start = new Button(this); start.setText("Iniciar prueba (60 segundos)"); layout.addView(start);
        Button end = new Button(this); end.setText("Detener"); layout.addView(end);
        ScrollView scroll = new ScrollView(this); log = new TextView(this); log.setTextSize(13); log.setTextIsSelectable(true); scroll.addView(log); layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(layout);
        start.setOnClickListener(v -> {if(running)return;log.setText("");running=true;new Thread(this::probe,"eta-probe").start();});
        end.setOnClickListener(v -> {running=false;say("Detención solicitada");});
    }
    private String request(String sid,String body) throws Exception {
        String url = "https://miralin.cat/socket.io/?EIO=4&transport=polling&t="+System.currentTimeMillis()+(sid==null?"":"&sid="+URLEncoder.encode(sid,"UTF-8"));
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","Miralin-ETA-Diagnostic/1.0");
        if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","text/plain;charset=UTF-8");try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}}
        try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toString("UTF-8");}finally{c.disconnect();}
    }
    private void probe(){
        try{
            say("Conectando HTTP polling…");
            String hello=request(null,null);say("Handshake: "+hello.substring(0,Math.min(hello.length(),180)));
            if(!hello.startsWith("0"))throw new IOException("Handshake inesperado");
            String sid=new JSONObject(hello.substring(1)).getString("sid");
            say("Enviando apertura de namespace: 40");request(sid,"40");
            boolean connected=false,subscribed=false,received=false;
            long until=System.currentTimeMillis()+60000;
            while(running && System.currentTimeMillis()<until){
                String packets=request(sid,null);
                for(String p:packets.split("\\u001e")){
                    if(p.equals("2")){request(sid,"3");continue;}
                    if(p.startsWith("40")){connected=true;say("Namespace conectado: "+p.substring(0,Math.min(p.length(),150)));}
                    else if(p.startsWith("42")){
                        try{JSONArray a=new JSONArray(p.substring(2));String name=a.getString(0);if("stopDataUpdate".equals(name)){received=true;say("✓ stopDataUpdate: "+a.toString().substring(0,Math.min(a.toString().length(),2200)));}else if("mapUpdate".equals(name))say("mapUpdate recibido");else say("Evento: "+name+" "+a.toString().substring(0,Math.min(a.toString().length(),500)));}catch(Exception e){say("Evento sin interpretar: "+e);}
                    }else if(p.startsWith("44")||p.startsWith("41")||p.equals("1"))say("Socket rechazado/cerrado: "+p);
                    else if(!p.isEmpty()&&!p.equals("3"))say("Paquete: "+p.substring(0,Math.min(p.length(),200)));
                }
                if(connected&&!subscribed){String message="42"+new JSONArray().put("subscribeStop").put(new JSONObject().put("line","L4").put("stop",stop)).toString();say("Suscribiendo: "+message);request(sid,message);subscribed=true;}
            }
            say("Resultado: "+(received?"PREVISIONES RECIBIDAS":"SIN stopDataUpdate")+"; namespace="+connected+"; suscripción enviada="+subscribed);
        }catch(Exception e){say("ERROR "+e.getClass().getSimpleName()+": "+e.getMessage());}finally{running=false;}
    }
    @Override protected void onDestroy(){running=false;super.onDestroy();}
}
