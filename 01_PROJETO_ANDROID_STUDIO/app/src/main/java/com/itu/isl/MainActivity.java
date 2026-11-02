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
  public class AccountBridge {
    @JavascriptInterface public void signIn(){runOnUiThread(()->startActivityForResult(googleClient.getSignInIntent(),GOOGLE_LOGIN));}
    @JavascriptInterface public void signOut(){firebaseAuth.signOut();googleClient.signOut();sendAuthState();}
    @JavascriptInterface public void getAccount(){sendAuthState();}
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
    web.addJavascriptInterface(new AccountBridge(),"ISLAccount");
    web.addJavascriptInterface(new MapsBridge(),"ISLMaps");web.addJavascriptInterface(new LinksBridge(),"ISLLinks");
    WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);s.setMediaPlaybackRequiresUserGesture(false);
    web.setWebViewClient(new WebViewClient());
    web.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onShowFileChooser(WebView view, ValueCallback<android.net.Uri[]> callback, FileChooserParams params){
        if(pending!=null)pending.onReceiveValue(null);pending=callback;
        try{Intent intent=new Intent(Intent.ACTION_GET_CONTENT);intent.setType("video/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(Intent.createChooser(intent,"Selecionar vídeo da manobra"),PICK_VIDEO);return true;}
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
