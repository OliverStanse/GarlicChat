package de.local.garlicchat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout root, messages;
    private EditText input, baseUrl, model, apiKey;
    private TextView status;
    private ScrollView scroll;
    private TextToSpeech tts;
    private boolean speakReplies = false;
    private boolean busy = false;
    private int profile = 0;
    private final String[] profiles = {"WLAN", "WireGuard", "Tailscale"};
    private String pref(String key, String fallback){return prefs.getString(key+profile, profile==0?prefs.getString(key,fallback):fallback);}
    private boolean ttsReady = false;
    private final JSONArray history = new JSONArray();
    private static final int SPEECH = 42, MIC_PERMISSION = 43;

    @Override public void onCreate(Bundle b) { super.onCreate(b); prefs=getSharedPreferences("garlic", MODE_PRIVATE); profile=Math.max(0,Math.min(2,prefs.getInt("profile",0))); speakReplies=prefs.getBoolean("speak",false); tts=new TextToSpeech(this, x -> {if(x==TextToSpeech.SUCCESS && tts!=null){ttsReady=tts.setLanguage(Locale.GERMANY)>=0;}}); showChat(); }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(String s,int size){ TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xff18352b);return t; }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void pad(View v,int p){v.setPadding(dp(p),dp(p),dp(p),dp(p));}
    private Button btn(String s){Button b=new Button(this);b.setText(s);return b;}
    private void base(String title){ root=column();root.setBackgroundColor(0xfff4f7f5);setContentView(root); LinearLayout top=row();top.setBackgroundColor(0xff12372a);TextView h=text(title,20);h.setTextColor(-1);h.setTypeface(null,Typeface.BOLD);pad(h,16);top.addView(h,new LinearLayout.LayoutParams(0,dp(58),1));Button settings=btn("⚙");settings.setTextColor(-1);settings.setBackgroundColor(0xff12372a);top.addView(settings);settings.setOnClickListener(v->{if(!busy)showSettings();});root.addView(top); }
    private void showChat(){base("Garlic Chat"); Spinner selector=new Spinner(this); selector.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,profiles)); selector.setSelection(profile); root.addView(selector); selector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){if(pos==profile)return;if(busy){selector.setSelection(profile);return;}profile=pos;prefs.edit().putInt("profile",profile).apply();showChat();}}); status=text("Nicht verbunden · Server konfigurieren",13);pad(status,10);root.addView(status);scroll=new ScrollView(this);messages=column();pad(messages,10);scroll.addView(messages);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); loadHistory(); LinearLayout bar=row();input=new EditText(this);input.setSingleLine(false);input.setMinLines(1);input.setMaxLines(4);input.setHint("Nachricht an Garlic LLM …");bar.addView(input,new LinearLayout.LayoutParams(0,-2,1));Button mic=btn("🎙");bar.addView(mic);mic.setOnClickListener(v->startSpeech());Button send=btn("➤");bar.addView(send);send.setOnClickListener(v->sendMessage());root.addView(bar); LinearLayout bottom=row();Button clear=btn("Neuer Chat");bottom.addView(clear);clear.setOnClickListener(v->{if(busy)return;while(messages.getChildCount()>0)messages.removeViewAt(0);while(history.length()>0)history.remove(0);saveHistory();});Button voice=btn(speakReplies?"Vorlesen: AN":"Vorlesen: AUS");bottom.addView(voice);voice.setOnClickListener(v->{speakReplies=!speakReplies;prefs.edit().putBoolean("speak",speakReplies).apply();voice.setText(speakReplies?"Vorlesen: AN":"Vorlesen: AUS");});root.addView(bottom); testConnection(); }
    private void addMessage(String role,String body){LinearLayout card=column();card.setBackgroundColor(role.equals("user")?0xffdcefe5:0xffffffff);pad(card,12);TextView who=text(role.equals("user")?"DU":"GARLIC LLM",11);who.setTypeface(null,Typeface.BOLD);TextView content=text(body,16);content.setTextIsSelectable(true);card.addView(who);card.addView(content);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);messages.addView(card,lp);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
    private String base(){String s=pref("url",profile==0?"http://192.168.1.5:8080/v1":prefs.getString("url0",prefs.getString("url","http://192.168.1.5:8080/v1"))).trim();while(s.endsWith("/"))s=s.substring(0,s.length()-1);return s;}
    private JSONObject request(String path,JSONObject body)throws Exception{URL u=new URL(base()+path);HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(600000);c.setRequestProperty("Accept","application/json");String key=pref("key","");if(!key.isEmpty())c.setRequestProperty("Authorization","Bearer "+key);if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}}int code=c.getResponseCode();InputStream is=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=is.read(buf))!=-1)out.write(buf,0,n);String result=out.toString("UTF-8");c.disconnect();if(code<200||code>=300)throw new IOException("HTTP "+code+": "+result);return new JSONObject(result);}
    private void testConnection(){final TextView target=status; final int selected=profile; io.execute(()->{try{JSONObject r=request("/models",null);runOnUiThread(()->{if(status==target&&profile==selected&&!busy)target.setText("Verbunden · "+profiles[selected]);});}catch(Exception e){runOnUiThread(()->{if(status==target&&profile==selected&&!busy)target.setText("Nicht verbunden · "+e.getMessage());});}});}
    private void sendMessage(){if(busy)return;String q=input.getText().toString().trim();if(q.isEmpty())return;busy=true;input.setText("");((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);addMessage("user",q);try{history.put(new JSONObject().put("role","user").put("content",q));}catch(Exception ignored){}saveHistory();status.setText("Garlic LLM antwortet …");io.execute(()->{try{JSONArray msgs=new JSONArray();msgs.put(new JSONObject().put("role","system").put("content","Du bist ein hilfreicher Assistent. Antworte auf Deutsch, sofern der Nutzer keine andere Sprache verwendet."));for(int i=0;i<history.length();i++)msgs.put(history.getJSONObject(i));JSONObject req=new JSONObject().put("messages",msgs).put("max_tokens",1024).put("stream",false);String m=pref("model","").trim();if(m.isEmpty()){JSONArray models=request("/models",null).optJSONArray("data");if(models!=null&&models.length()>0)m=models.getJSONObject(0).optString("id","");}if(!m.isEmpty())req.put("model",m);JSONObject res=request("/chat/completions",req);String answer=res.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","");if(answer.isEmpty())answer="(Leere Antwort vom Server)";final String a=answer;runOnUiThread(()->{busy=false;try{history.put(new JSONObject().put("role","assistant").put("content",a));}catch(JSONException ignored){}saveHistory();addMessage("assistant",a);status.setText("Bereit");if(speakReplies&&ttsReady&&tts!=null)tts.speak(a,TextToSpeech.QUEUE_FLUSH,null,"garlic-answer");});}catch(Exception e){runOnUiThread(()->{busy=false;history.remove(history.length()-1);saveHistory();showChat();input.setText(q);status.setText("Fehler: "+e.getMessage());});}});}
    private void showSettings(){base("Profil: "+profiles[profile]);pad(root,16);root.addView(text("Garlic LLM – OpenAI-kompatible API",16));baseUrl=field("Serveradresse, z. B. http://192.168.1.5:8080/v1",base());model=field("Modell-ID (optional; leer = Serverstandard)",pref("model",""));apiKey=field("API-Key (optional)",pref("key",""));apiKey.setInputType( android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);root.addView(baseUrl);root.addView(model);root.addView(apiKey);TextView note=text("WLAN: lokale PS4-IP. WireGuard: Tunnel in Android aktivieren und lokale PS4-IP nutzen. Tailscale: Subnet-Router und freigegebene Route oder erreichbaren Proxy nutzen. HTTP ist außerhalb eines VPN unverschlüsselt.",13);pad(note,12);root.addView(note);Button save=btn("Speichern & verbinden");root.addView(save);save.setOnClickListener(v->{String u=baseUrl.getText().toString().trim();if(!validUrl(u)){Toast.makeText(this,"Adresse muss mit http:// oder https:// beginnen",Toast.LENGTH_LONG).show();return;}prefs.edit().putString("url"+profile,u).putString("model"+profile,model.getText().toString().trim()).putString("key"+profile,apiKey.getText().toString().trim()).apply();showChat();});Button back=btn("Zurück zum Chat");root.addView(back);back.setOnClickListener(v->showChat()); }
    private boolean validUrl(String value){try{URI u=new URI(value);return ("http".equals(u.getScheme())||"https".equals(u.getScheme()))&&u.getHost()!=null&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null;}catch(Exception e){return false;}}
    private EditText field(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);return e;}
    private void startSpeech(){if(busy)return;if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_PERMISSION);return;}if(!SpeechRecognizer.isRecognitionAvailable(this)){Toast.makeText(this,"Spracherkennung auf diesem Gerät nicht verfügbar",Toast.LENGTH_LONG).show();return;}Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"de-DE");i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Jetzt sprechen");try{startActivityForResult(i,SPEECH);}catch(Exception e){Toast.makeText(this,"Spracherkennung konnte nicht gestartet werden",Toast.LENGTH_LONG).show();}}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==SPEECH&&result==RESULT_OK&&data!=null){ArrayList<String> r=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(r!=null&&!r.isEmpty())input.setText((input.getText()+" "+r.get(0)).trim());}}
    @Override public void onRequestPermissionsResult(int req,String[] p,int[] g){super.onRequestPermissionsResult(req,p,g);if(req==MIC_PERMISSION&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startSpeech();}
    private void saveHistory(){prefs.edit().putString("history",history.toString()).apply();}
    private void loadHistory(){while(history.length()>0)history.remove(0);try{JSONArray saved=new JSONArray(prefs.getString("history","[]"));for(int i=0;i<saved.length();i++){JSONObject m=saved.getJSONObject(i);history.put(m);addMessage(m.optString("role"),m.optString("content"));}}catch(Exception ignored){}}
    @Override protected void onDestroy(){if(tts!=null)tts.shutdown();io.shutdown();super.onDestroy();}
}
