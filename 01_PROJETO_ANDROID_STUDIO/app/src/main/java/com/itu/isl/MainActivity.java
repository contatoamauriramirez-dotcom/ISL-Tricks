package com.itu.isl;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.content.Context;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import android.os.Bundle;
import android.content.Intent;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;

public class MainActivity extends Activity {
  private WebView web;
  private ValueCallback<android.net.Uri[]> pending;
  private static final int PICK_VIDEO=42;
  private static final int GOOGLE_LOGIN=43;
  private static final int LOCATION_PERMISSION=44;
  private void mapMessage(String msg){runOnUiThread(()->web.evaluateJavascript("hubMapMessage("+org.json.JSONObject.quote(msg)+")",null));}
  private void locateOnDevice(){
    if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_PERMISSION);return;
    }
    try{
      LocationManager lm=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
      Location loc=null;
      for(String provider:lm.getProviders(true)){
        Location candidate=lm.getLastKnownLocation(provider);
        if(candidate!=null && (loc==null || candidate.getTime()>loc.getTime()))loc=candidate;
      }
      if(loc!=null){final double lat=loc.getLatitude(),lon=loc.getLongitude();web.evaluateJavascript("hubMapLocated("+lat+","+lon+")",null);}
      else {mapMessage("Aguardando GPS. Ative a localização do aparelho e tente novamente.");
        if(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,new android.location.LocationListener(){
          @Override public void onLocationChanged(Location l){runOnUiThread(()->web.evaluateJavascript("hubMapLocated("+l.getLatitude()+","+l.getLongitude()+")",null));}
        },null);
      }
    }catch(Exception e){mapMessage("Não foi possível obter a localização. Confira a permissão de GPS.");}
  }
  @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==LOCATION_PERMISSION){boolean ok=false;for(int g:grants)if(g==PackageManager.PERMISSION_GRANTED)ok=true;if(ok)locateOnDevice();else mapMessage("Permissão de localização negada.");}}
  public class MapsBridge{
    @JavascriptInterface public void locate(){runOnUiThread(()->locateOnDevice());}
    @JavascriptInterface public void openMap(String url){runOnUiThread(()->{try{if(url.startsWith("https://www.openstreetmap.org/"))startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){mapMessage("Não foi possível abrir o mapa.");}});}
  }
  public class LinksBridge{
    // Abre links externos (fontes dos artigos) no navegador do celular. Só aceita HTTPS.
    @JavascriptInterface public void open(String url){runOnUiThread(()->{try{if(url!=null&&url.startsWith("https://"))startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){}});}
  }
  private GoogleSignInClient googleClient;
  private FirebaseAuth firebaseAuth;
  private void sendAuthState(){
    FirebaseUser u=firebaseAuth.getCurrentUser();
    String payload;
    try { org.json.JSONObject obj=new org.json.JSONObject();
      obj.put("signedIn",u!=null);
      if(u!=null){obj.put("uid",u.getUid());obj.put("name",u.getDisplayName());obj.put("email",u.getEmail());obj.put("photo",u.getPhotoUrl()==null?"":u.getPhotoUrl().toString());}
      payload=obj.toString();
    } catch(Exception ex){payload="{\"signedIn\":false}";}
    final String js="window.dispatchEvent(new CustomEvent('isl-auth',{detail:"+payload+"}));";
    runOnUiThread(()->{if(web!=null)web.evaluateJavascript(js,null);});
  }
  private void sendJs(String event,String json){final String js="window.dispatchEvent(new CustomEvent('"+event+"',{detail:"+json+"}));";runOnUiThread(()->{if(web!=null)web.evaluateJavascript(js,null);});}
  private void sendAuthError(int code,String msg){try{org.json.JSONObject o=new org.json.JSONObject();o.put("code",code);o.put("message",msg==null?"":msg);sendJs("isl-auth-error",o.toString());}catch(Exception e){}}
  // Cria (ou atualiza) o usuário em users/{uid} no Firestore.
  private void createUserDoc(FirebaseUser u){
    if(u==null)return;
    FirebaseFirestore db=FirebaseFirestore.getInstance();
    java.util.Map<String,Object> base=new java.util.HashMap<>();
    base.put("name",u.getDisplayName());base.put("email",u.getEmail());
    base.put("photo",u.getPhotoUrl()==null?"":u.getPhotoUrl().toString());base.put("lastLogin",FieldValue.serverTimestamp());
    db.collection("users").document(u.getUid()).get().addOnCompleteListener(t->{
      boolean novo=!t.isSuccessful()||t.getResult()==null||!t.getResult().exists();
      if(novo)base.put("createdAt",FieldValue.serverTimestamp());
      db.collection("users").document(u.getUid()).set(base,SetOptions.merge())
        .addOnSuccessListener(x->sendJs("isl-cloud",novo?"{\"ok\":true,\"created\":true}":"{\"ok\":true}"))
        .addOnFailureListener(e->sendJs("isl-cloud","{\"ok\":false}"));
      if(!novo&&t.getResult()!=null){try{org.json.JSONObject o=new org.json.JSONObject(t.getResult().getData());sendJs("isl-profile",o.toString());}catch(Exception e){}}
    });
  }
  // ===== Comunidade: feed e check-in nas pistas, em tempo real (Firestore) =====
  private ListenerRegistration postsReg, checkinsReg, wentReg;
  private static final long CHECKIN_MS=3L*60*60*1000;
  private String errJson(Exception e){try{org.json.JSONObject o=new org.json.JSONObject();o.put("message",e==null?"":String.valueOf(e.getMessage()));return o.toString();}catch(Exception x){return "{}";}}
  private long ms(Object v){if(v instanceof com.google.firebase.Timestamp)return ((com.google.firebase.Timestamp)v).toDate().getTime();if(v instanceof Number)return ((Number)v).longValue();return System.currentTimeMillis();}
  private java.util.Map<String,Object> jsonToMap(org.json.JSONObject o){java.util.Map<String,Object> m=new java.util.HashMap<>();if(o==null)return m;
    java.util.Iterator<String> it=o.keys();while(it.hasNext()){String k=it.next();Object v=o.opt(k);if(v instanceof String){String sv=(String)v;m.put(k,sv.length()>200?sv.substring(0,200):sv);}else if(v instanceof Number||v instanceof Boolean)m.put(k,v);}return m;}
  // Converte o JSON do perfil público em tipos do Firestore (com limites de tamanho).
  private Object toFs(Object v,int depth){
    if(depth>4||v==null||v==org.json.JSONObject.NULL)return null;
    if(v instanceof String){String sv=(String)v;return sv.length()>300?sv.substring(0,300):sv;}
    if(v instanceof Number||v instanceof Boolean)return v;
    if(v instanceof org.json.JSONArray){org.json.JSONArray a=(org.json.JSONArray)v;java.util.List<Object> l=new java.util.ArrayList<>();for(int i=0;i<a.length()&&i<500;i++){Object x=toFs(a.opt(i),depth+1);if(x!=null)l.add(x);}return l;}
    if(v instanceof org.json.JSONObject){org.json.JSONObject o=(org.json.JSONObject)v;java.util.Map<String,Object> m=new java.util.HashMap<>();java.util.Iterator<String> it=o.keys();int n=0;while(it.hasNext()&&n<60){String k=it.next();Object x=toFs(o.opt(k),depth+1);if(x!=null){m.put(k,x);n++;}}return m;}
    return null;}
  private void stopCommunity(){if(postsReg!=null){postsReg.remove();postsReg=null;}if(checkinsReg!=null){checkinsReg.remove();checkinsReg=null;}if(wentReg!=null){wentReg.remove();wentReg=null;}}
  private void startCommunity(){
    FirebaseUser u=firebaseAuth.getCurrentUser();if(u==null){sendJs("isl-community-error","{\"code\":\"auth\"}");return;}
    FirebaseFirestore db=FirebaseFirestore.getInstance();
    if(postsReg==null)postsReg=db.collection("posts").orderBy("createdAt",Query.Direction.DESCENDING).limit(60).addSnapshotListener((snap,err)->{
      if(err!=null){sendJs("isl-community-error",errJson(err));return;}
      org.json.JSONArray arr=new org.json.JSONArray();
      for(DocumentSnapshot d:snap.getDocuments()){try{org.json.JSONObject o=new org.json.JSONObject();o.put("id",d.getId());
        for(String k:new String[]{"uid","name","photo","type","text"})o.put(k,d.getString(k)==null?"":d.getString(k));
        o.put("createdAt",ms(d.get("createdAt")));Object data=d.get("data");if(data instanceof java.util.Map)o.put("data",new org.json.JSONObject((java.util.Map)data));arr.put(o);}catch(Exception e){}}
      sendJs("isl-posts",arr.toString());});
    if(checkinsReg==null)checkinsReg=db.collection("checkins").whereGreaterThan("expiresAt",System.currentTimeMillis()-CHECKIN_MS).addSnapshotListener((snap,err)->{
      if(err!=null){sendJs("isl-community-error",errJson(err));return;}
      org.json.JSONArray arr=new org.json.JSONArray();
      for(DocumentSnapshot d:snap.getDocuments()){try{org.json.JSONObject o=new org.json.JSONObject();o.put("uid",d.getId());
        for(String k:new String[]{"name","photo","parkKey","parkName","city","uf"})o.put(k,d.getString(k)==null?"":d.getString(k));
        o.put("expiresAt",ms(d.get("expiresAt")));o.put("at",ms(d.get("at")));arr.put(o);}catch(Exception e){}}
      sendJs("isl-checkins",arr.toString());});
    if(wentReg==null)wentReg=db.collection("went").limit(5000).addSnapshotListener((snap,err)->{
      if(err!=null){sendJs("isl-community-error",errJson(err));return;}
      org.json.JSONArray arr=new org.json.JSONArray();
      for(DocumentSnapshot d:snap.getDocuments()){try{org.json.JSONObject o=new org.json.JSONObject();
        for(String k:new String[]{"eventKey","uid","name","photo","title"})o.put(k,d.getString(k)==null?"":d.getString(k));arr.put(o);}catch(Exception e){}}
      sendJs("isl-went",arr.toString());});
  }
  private void addPost(FirebaseUser u,String type,String text,java.util.Map<String,Object> data){
    java.util.Map<String,Object> m=new java.util.HashMap<>();m.put("uid",u.getUid());m.put("name",u.getDisplayName()==null?"Patinador ISL":u.getDisplayName());
    m.put("photo",u.getPhotoUrl()==null?"":u.getPhotoUrl().toString());m.put("type",type);m.put("text",text.length()>500?text.substring(0,500):text);
    m.put("data",data);m.put("createdAt",FieldValue.serverTimestamp());
    FirebaseFirestore.getInstance().collection("posts").add(m).addOnSuccessListener(x->sendJs("isl-community-ok","{\"action\":\"post\"}")).addOnFailureListener(e->sendJs("isl-community-error",errJson(e)));
  }
  public class CommunityBridge{
    @JavascriptInterface public void start(){runOnUiThread(()->startCommunity());}
    @JavascriptInterface public void stop(){runOnUiThread(()->stopCommunity());}
    @JavascriptInterface public void post(String json){FirebaseUser u=firebaseAuth.getCurrentUser();if(u==null){sendJs("isl-community-error","{\"code\":\"auth\"}");return;}
      try{org.json.JSONObject o=new org.json.JSONObject(json);String text=o.optString("text","").trim();String type=o.optString("type","");if(text.isEmpty()||!java.util.Arrays.asList("trick","challenge","combo","robot").contains(type))return;if(text.length()>200)text=text.substring(0,200);addPost(u,type,text,jsonToMap(o.optJSONObject("data")));}catch(Exception e){sendJs("isl-community-error",errJson(e));}}
    @JavascriptInterface public void deletePost(String id){FirebaseFirestore.getInstance().collection("posts").document(id).delete().addOnFailureListener(e->sendJs("isl-community-error",errJson(e)));}
    @JavascriptInterface public void checkIn(String json){FirebaseUser u=firebaseAuth.getCurrentUser();if(u==null){sendJs("isl-community-error","{\"code\":\"auth\"}");return;}
      try{org.json.JSONObject o=new org.json.JSONObject(json);java.util.Map<String,Object> m=new java.util.HashMap<>();
        m.put("name",u.getDisplayName()==null?"Patinador ISL":u.getDisplayName());m.put("photo",u.getPhotoUrl()==null?"":u.getPhotoUrl().toString());
        for(String k:new String[]{"parkKey","parkName","city","uf"})m.put(k,o.optString(k,""));
        m.put("at",FieldValue.serverTimestamp());m.put("expiresAt",System.currentTimeMillis()+CHECKIN_MS);
        FirebaseFirestore.getInstance().collection("checkins").document(u.getUid()).set(m)
          .addOnSuccessListener(x->sendJs("isl-community-ok","{\"action\":\"checkin\"}")).addOnFailureListener(e->sendJs("isl-community-error",errJson(e)));
        if(o.optBoolean("share",true)){java.util.Map<String,Object> d=new java.util.HashMap<>();d.put("parkKey",o.optString("parkKey",""));d.put("parkName",o.optString("parkName",""));
          addPost(u,"checkin","Estou na pista "+o.optString("parkName","")+" ("+o.optString("city","")+" · "+o.optString("uf","")+")",d);}
      }catch(Exception e){sendJs("isl-community-error",errJson(e));}}
    // "Tô dentro": marca presença no evento (documento went/{evento}__{uid}) e pode publicar no feed.
    @JavascriptInterface public void markWent(String json){FirebaseUser u=firebaseAuth.getCurrentUser();if(u==null){sendJs("isl-community-error","{\"code\":\"auth\"}");return;}
      try{org.json.JSONObject o=new org.json.JSONObject(json);String key=o.optString("eventKey","").replaceAll("[^A-Za-z0-9_-]","");if(key.isEmpty())return;
        java.util.Map<String,Object> m=new java.util.HashMap<>();m.put("eventKey",key);m.put("uid",u.getUid());
        m.put("name",u.getDisplayName()==null?"Patinador ISL":u.getDisplayName());m.put("photo",u.getPhotoUrl()==null?"":u.getPhotoUrl().toString());
        m.put("title",o.optString("title",""));m.put("at",FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("went").document(key+"__"+u.getUid()).set(m)
          .addOnSuccessListener(x->sendJs("isl-community-ok","{\"action\":\"went\"}")).addOnFailureListener(e->sendJs("isl-community-error",errJson(e)));
        if(o.optBoolean("share",false)){java.util.Map<String,Object> d=new java.util.HashMap<>();d.put("eventKey",key);addPost(u,"event","Tô dentro: "+o.optString("title","")+"!",d);}
      }catch(Exception e){sendJs("isl-community-error",errJson(e));}}
    @JavascriptInterface public void unmarkWent(String key){FirebaseUser u=firebaseAuth.getCurrentUser();if(u==null)return;
      String k=key==null?"":key.replaceAll("[^A-Za-z0-9_-]","");FirebaseFirestore.getInstance().collection("went").document(k+"__"+u.getUid()).delete();}
    // Perfil público de outro patinador (profiles/{uid}).
    @JavascriptInterface public void getProfile(String uid){
      final String id=uid==null?"":uid.replaceAll("[^A-Za-z0-9]","");if(id.isEmpty())return;
      if(firebaseAuth.getCurrentUser()==null){sendJs("isl-user-profile","{\"uid\":\""+id+"\",\"error\":true}");return;}
      FirebaseFirestore.getInstance().collection("profiles").document(id).get().addOnSuccessListener(d->{
        try{org.json.JSONObject o=new org.json.JSONObject();o.put("uid",id);o.put("exists",d.exists());
          if(d.exists()&&d.getData()!=null)o.put("profile",new org.json.JSONObject(d.getData()));sendJs("isl-user-profile",o.toString());}
        catch(Exception e){sendJs("isl-user-profile","{\"uid\":\""+id+"\",\"error\":true}");}
      }).addOnFailureListener(e->sendJs("isl-user-profile","{\"uid\":\""+id+"\",\"error\":true}"));}
    @JavascriptInterface public void checkOut(){FirebaseUser u=firebaseAuth.getCurrentUser();if(u==null)return;
      FirebaseFirestore.getInstance().collection("checkins").document(u.getUid()).delete().addOnSuccessListener(x->sendJs("isl-community-ok","{\"action\":\"checkout\"}"));}
  }
  public class AccountBridge {
    @JavascriptInterface public void signIn(){runOnUiThread(()->startActivityForResult(googleClient.getSignInIntent(),GOOGLE_LOGIN));}
    @JavascriptInterface public void signOut(){runOnUiThread(()->stopCommunity());firebaseAuth.signOut();googleClient.signOut();sendAuthState();}
    @JavascriptInterface public void getAccount(){sendAuthState();}
    // Publica o perfil público (manobras aprendidas, recordes, apelido, cidade e bio) em profiles/{uid}.
    @JavascriptInterface public void publishProfile(String json){
      FirebaseUser u=firebaseAuth.getCurrentUser();if(u==null)return;
      try{Object v=toFs(new org.json.JSONObject(json),0);if(!(v instanceof java.util.Map))return;
        @SuppressWarnings("unchecked") java.util.Map<String,Object> m=(java.util.Map<String,Object>)v;
        m.put("uid",u.getUid());m.put("name",u.getDisplayName()==null?"Patinador ISL":u.getDisplayName());
        m.put("photo",u.getPhotoUrl()==null?"":u.getPhotoUrl().toString());m.put("updatedAt",System.currentTimeMillis());
        FirebaseFirestore.getInstance().collection("profiles").document(u.getUid()).set(m);
      }catch(Exception e){}
    }
    // Salva apelido, cidade e bio do patinador no perfil da nuvem.
    @JavascriptInterface public void saveProfile(String json){
      FirebaseUser u=firebaseAuth.getCurrentUser();if(u==null)return;
      try{org.json.JSONObject o=new org.json.JSONObject(json);java.util.Map<String,Object> m=new java.util.HashMap<>();
        for(String k:new String[]{"nickname","city","bio"})if(o.has(k))m.put(k,o.optString(k,"").trim());
        m.put("updatedAt",FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("users").document(u.getUid()).set(m,SetOptions.merge())
          .addOnSuccessListener(x->sendJs("isl-cloud","{\"ok\":true,\"saved\":true}"))
          .addOnFailureListener(e->sendJs("isl-cloud","{\"ok\":false,\"saved\":false}"));
      }catch(Exception e){sendJs("isl-cloud","{\"ok\":false}");}
    }
  }
  @Override public void onCreate(Bundle state){super.onCreate(state);
    getWindow().setStatusBarColor(0xff000000);getWindow().setNavigationBarColor(0xff000000);
    firebaseAuth=FirebaseAuth.getInstance();
    GoogleSignInOptions options=new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
      .requestIdToken(getString(com.itu.isl.R.string.default_web_client_id)).requestEmail().build();
    googleClient=GoogleSignIn.getClient(this,options);
    web=new WebView(this);
    // Mantém o conteúdo abaixo da barra de status e acima da barra de navegação (Android 15 desenha de ponta a ponta).
    android.widget.FrameLayout frame=new android.widget.FrameLayout(this);frame.setBackgroundColor(0xff0b0b0d);
    frame.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1));setContentView(frame);
    frame.setOnApplyWindowInsetsListener((v,insets)->{
      int l,t,r,b;
      if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());l=i.left;t=i.top;r=i.right;b=i.bottom;}
      else{l=insets.getSystemWindowInsetLeft();t=insets.getSystemWindowInsetTop();r=insets.getSystemWindowInsetRight();b=insets.getSystemWindowInsetBottom();}
      v.setPadding(l,t,r,b);return insets;});
    web.addJavascriptInterface(new AccountBridge(),"ISLAccount");web.addJavascriptInterface(new CommunityBridge(),"ISLCommunity");
    web.addJavascriptInterface(new MapsBridge(),"ISLMaps");web.addJavascriptInterface(new LinksBridge(),"ISLLinks");
    WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);s.setMediaPlaybackRequiresUserGesture(false);
    web.setWebViewClient(new WebViewClient());
    web.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onShowFileChooser(WebView view, ValueCallback<android.net.Uri[]> callback, FileChooserParams params){
        if(pending!=null)pending.onReceiveValue(null);pending=callback;
        try{
          boolean image=false;String[] types=params==null?null:params.getAcceptTypes();
          if(types!=null)for(String t:types)if(t!=null&&t.startsWith("image"))image=true;
          Intent intent=new Intent(Intent.ACTION_GET_CONTENT);intent.setType(image?"image/*":"video/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
          startActivityForResult(Intent.createChooser(intent,image?"Selecionar foto":"Selecionar vídeo da manobra"),PICK_VIDEO);return true;}
        catch(Exception ex){pending=null;return false;}
      }
    });
    web.loadUrl("file:///android_asset/index.html");
  }
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==GOOGLE_LOGIN){
      Task<GoogleSignInAccount> task=GoogleSignIn.getSignedInAccountFromIntent(data);
      try{GoogleSignInAccount account=task.getResult(ApiException.class);
        firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(account.getIdToken(),null))
          .addOnCompleteListener(this,authTask -> {
            if(authTask.isSuccessful()){createUserDoc(firebaseAuth.getCurrentUser());}
            else{sendAuthError(-2,authTask.getException()==null?"":authTask.getException().getMessage());}
            sendAuthState();});
      }catch(ApiException ex){android.util.Log.e("ISL","Google login failed",ex);sendAuthError(ex.getStatusCode(),ex.getMessage());sendAuthState();}
      catch(Exception ex){android.util.Log.e("ISL","Google login failed",ex);sendAuthError(-1,ex.getMessage());sendAuthState();}
      return;
    }
    if(request==PICK_VIDEO&&pending!=null){pending.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new android.net.Uri[]{data.getData()}:null);pending=null;}}
  @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
}
