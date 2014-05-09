package io.scal.secureshareui.controller;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;

import com.flickr.api.Flickr;
import com.flickr.api.FlickrException;
import com.flickr.api.FlickrProperties;
import com.flickr.api.PeopleService;
import com.flickr.api.PhotosService;
import com.flickr.api.UploadService;
import com.flickr.api.entities.Comment;
import com.flickr.api.entities.Paginated;
import com.flickr.api.entities.Photo;
import com.flickr.api.entities.UserInfos;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import info.guardianproject.onionkit.ui.OrbotHelper;
import io.scal.secureshareui.login.FlickrLoginActivity;
import io.scal.secureshareui.model.PublishAccount;

public class FlickrPublishController extends PublishController 
{
	public static final String SITE_NAME = "Flickr"; 
    public static final String SITE_KEY = "flickr"; 
    private static final String TAG = "FlickrPublishController";
    
    Flickr f = null;
    
 // AUTH SETTINGS - DO NOT COMMIT
    String key = "";
    String secret = "";
    
 // TOR PROXY SETTINGS
    private static final String ORBOT_HOST = "127.0.0.1";
    private static final int ORBOT_HTTP_PORT = 8118;
    
    public FlickrPublishController() 
    {
        // ???
    }

    @Override
    public void startAuthentication(PublishAccount account) 
    {   
        Context currentContext = super.getContext();
        
        Log.d(TAG, "startAuthentication()");
        
        Intent intent = new Intent(currentContext, FlickrLoginActivity.class);
        intent.putExtra("credentials", account.getCredentials());
        ((Activity)currentContext).startActivityForResult(intent, PublishController.CONTROLLER_REQUEST_CODE);
    }
    
    @Override
    public void upload(String title, String body, String mediaPath, String username, String credentials)
    {
        String path = Environment.getExternalStorageDirectory() + File.separator + "flickr.conf";
        
        Log.d(TAG, "upload() path: " + path);
        
        File confFile = new File(path);
        FlickrProperties fProps = new FlickrProperties(confFile); 
        f = new Flickr(key,                          // key
                       secret,                       // secret
                       "http://localhost/callback",  // callback
                       "delete",                     // permissions ("delete" permission allows read/write/delete)
                       fProps);                      // properties

        OrbotHelper orbotHelper = new OrbotHelper(super.getContext());
        if(orbotHelper.isOrbotRunning()) 
        {    
            Log.d(TAG, "orbot running, setting proxy");
            
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ORBOT_HOST, ORBOT_HTTP_PORT));
            f.setProxy(proxy);
        }
        else
        {
            Log.d(TAG, "orbot not running, proxy not set");
        }
        
        // token stored in properties?  let's assume so for now...
        
        UploadFileTask ufTask = new UploadFileTask(this);
        ufTask.execute(title, body, mediaPath, credentials);
    }
    
    class UploadFileTask extends AsyncTask<String, String, String> 
    {
        private FlickrPublishController fpc;
        
        public UploadFileTask(FlickrPublishController fpc)
        {
            this.fpc = fpc;
        }
        
        @Override
        protected String doInBackground(String... params) 
        {
            fpc.uploadFile(params[0], params[1], params[2], params[3]);
            return "success";
        }
    }

    public void uploadFile(String title, String body, String mediaPath, String credentials) 
    {
        try
        {
            Log.d(TAG, "uploadFile() path: " + mediaPath);
            
            File photoFile = new File(mediaPath);
            UploadService us = f.getUploadService();
            us.uploadPhoto(photoFile, title, body); // IS THIS THE CORRECT USE OF "body"?
        }
        catch (FlickrException fe)
        {
            Log.e(TAG, "upload failed: " + fe.getMessage());
        }
    }
}