package io.scal.secureshareui.login;

import info.guardianproject.netcipher.proxy.OrbotHelper;
import info.guardianproject.netcipher.web.WebkitProxy;
import io.scal.secureshareui.controller.SiteController;
import io.scal.secureshareui.lib.Util;
import io.scal.secureshareuilibrary.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArchiveLoginActivity extends Activity {

	private static final String TAG = "ArchiveLoginActivity";
	
	private final static String ARCHIVE_CREATE_ACCOUNT_URL = "https://archive.org/account/login.createaccount.php";
	private final static String ARCHIVE_LOGIN_URL = "https://archive.org/account/login.php";
	private final static String ARCHIVE_LOGGED_IN_URL = "https://archive.org/index.php";
	private final static String ARCHIVE_CREDENTIALS_URL = "https://archive.org/account/s3.php";

	private static boolean sIsLoginScren = false;
	private int mAccessResult = Activity.RESULT_CANCELED;
	private String mAccessKey = null;
    private String mSecretKey = null;
    
    // FIXME security: we need to override the webviews cache, cookies, formdata cache to store only in sqlcipher/iocipher, currently it hits disk and then we clear it
    private WebView mWebview;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archive_login);
		login(ARCHIVE_LOGIN_URL);
	}

	@SuppressLint({ "SetJavaScriptEnabled" })
	private void login(String currentURL) {

        // check for tor settings and set proxy
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean useTor = settings.getBoolean("pusetor", false);


		mWebview = (WebView) findViewById(R.id.webView);
		mWebview.getSettings().setJavaScriptEnabled(true);
		mWebview.setVisibility(View.VISIBLE);
		mWebview.addJavascriptInterface(new JSInterface(), "htmlout");

        if (useTor) {
            Log.d(TAG, "user selected \"use tor\"");

            if ((!OrbotHelper.isOrbotInstalled(getApplicationContext())) || (!OrbotHelper.isOrbotRunning(getApplicationContext()))) {
                Log.e(TAG, "user selected \"use tor\" but orbot is not installed or not running");
                return;
            } else {
                try {
                    WebkitProxy.setProxy("android.app.Application", getApplicationContext(), mWebview, Util.ORBOT_HOST, Util.ORBOT_HTTP_PORT);
                } catch (Exception e) {
                    Log.e(TAG, "user selected \"use tor\" but an exception was thrown while setting the proxy: " + e.getLocalizedMessage());
                    return;
                }
            }
        } else {
            Log.d(TAG, "user selected \"don't use tor\"");
        }


		mWebview.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				//if logged in, hide and redirect to credentials
				if (url.equals(ARCHIVE_LOGGED_IN_URL)) {
					view.setVisibility(View.INVISIBLE);
					view.loadUrl(ARCHIVE_CREDENTIALS_URL);
					
					return true;
				}			
				return false;
			}

			@Override
			public void onPageFinished(WebView view, String url) {
				super.onPageFinished(view, url);		
				//if credentials page, inject JS for scraping
				if (url.equals(ARCHIVE_CREDENTIALS_URL)) {
					sIsLoginScren = true;
					
		            String jsCheckBox= "javascript:(function(){document.getElementById('confirm').checked=true;})();";
		            String jsBtnClick = "javascript:(function(){$('[value=\"Generate New Keys\"]').click();})();";
		            String jsSourceDump = "javascript:window.htmlout.processHTML('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');";
		            
		            mWebview.loadUrl(jsCheckBox + jsBtnClick + jsSourceDump); 
				} else if(url.equals(ARCHIVE_CREATE_ACCOUNT_URL)) {
					sIsLoginScren = false;
					String jsSourceDump = "javascript:window.htmlout.processHTML('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');";
					mWebview.loadUrl(jsSourceDump);
				}			
			}
		});

		mWebview.loadUrl(currentURL);
	}
	
	private void parseArchiveCredentials(String rawHtml) {

		try {
			final Pattern pattern = Pattern.compile("<div class=\"alert alert-danger\">(.+?)</div>");
			final Matcher matcher = pattern.matcher("rawHtml");

			if (matcher.find())
				mAccessKey = matcher.group(1).split(":")[1].trim();

			if (matcher.find())
				mSecretKey = matcher.group(1).split(":")[1].trim();

			//mAccessKey = rawCodes.substring(iFirstColon, iFirstLt);
			//		mSecretKey = rawCodes.substring(iLastColon, iLastLt);

			if (null != mAccessKey && null != mSecretKey) {
				mAccessResult = Activity.RESULT_OK;
			}
		}
		catch (Exception e)
		{
			Log.d("Archive Login","unable to get site S3 creds",e);
		}


		finish();
	}
	
	class JSInterface {
	    @JavascriptInterface
		public void processHTML(String html) {			
			if(null == html) {
				return;
			}
			
			if(sIsLoginScren) {
				parseArchiveCredentials(html);
			} else if (html.contains("Verification Email Sent")) {
				showAccountCreatedDialog(new DialogInterface.OnClickListener() {
					@Override
                    public void onClick(DialogInterface dialog, int which) {
						finish();
                    }
                });		
			}
	    }
	}
	
	private void showAccountCreatedDialog(DialogInterface.OnClickListener positiveBtnClickListener) {
		new AlertDialog.Builder(this)
				.setTitle(getString(R.string.archive_title))
				.setMessage(getString(R.string.archive_message))
				.setPositiveButton(R.string.lbl_ok, positiveBtnClickListener).show();
	}

	@Override
	public void finish() {
		Log.d(TAG, "finish()");
		
		Intent data = new Intent();
		data.putExtra(SiteController.EXTRAS_KEY_USERNAME, mAccessKey);
		data.putExtra(SiteController.EXTRAS_KEY_CREDENTIALS, mSecretKey);
		setResult(mAccessResult, data);
		
		super.finish();		
		Util.clearWebviewAndCookies(mWebview, this);
	}
}
