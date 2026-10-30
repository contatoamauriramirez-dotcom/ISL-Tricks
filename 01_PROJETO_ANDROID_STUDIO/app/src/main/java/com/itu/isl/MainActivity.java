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
  public class AccountBridge {
    @JavascriptInterface public void signIn(){runOnUiThread(()->startActivityForResult(googleClient.getSignInIntent(),GOOGLE_LOGIN));}
    @JavascriptInterface public void signOut(){firebaseAuth.signOut();googleClient.signOut();sendAuthState();}
    @JavascriptInterface public void getAccount(){sendAuthState();}
  }
  @Override public void onCreate(Bundle state){super.onCreate(state);
    getWindow().setStatusBarColor(0xff000000);getWindow().setNavigationBarColor(0xff000000);
    firebaseAuth=FirebaseAuth.getInstance();
    GoogleSignInOptions options=new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
      .requestIdToken(getString(com.itu.isl.R.string.default_web_client_id)).requestEmail().build();
    googleClient=GoogleSignIn.getClient(this,options);
    web=new WebView(this);setContentView(web);
    web.addJavascriptInterface(new AccountBridge(),"ISLAccount");
    web.addJavascriptInterface(new MapsBridge(),"ISLMaps");
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
          .addOnCompleteListener(this,authTask -> {sendAuthState();});
      }catch(Exception ex){android.util.Log.e("ISL","Google login failed",ex);sendAuthState();}
      return;
    }
    if(request==PICK_VIDEO&&pending!=null){pending.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new android.net.Uri[]{data.getData()}:null);pending=null;}}
  @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
}
