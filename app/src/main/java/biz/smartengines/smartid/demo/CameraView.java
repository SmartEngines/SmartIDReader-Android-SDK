/**
 Copyright (c) 2012-2017, Smart Engines Ltd
 All rights reserved.

 Redistribution and use in source and binary forms, with or without modification,
 are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice,
 this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution.
 * Neither the name of the Smart Engines Ltd nor the names of its
 contributors may be used to endorse or promote products derived from this
 software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package biz.smartengines.smartid.demo;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import biz.smartengines.smartid.swig.Image;
import biz.smartengines.smartid.swig.ImageField;
import biz.smartengines.smartid.swig.ImageOrientation;
import biz.smartengines.smartid.swig.MatchResultVector;
import biz.smartengines.smartid.swig.Quadrangle;
import biz.smartengines.smartid.swig.RecognitionEngine;
import biz.smartengines.smartid.swig.RecognitionResult;
import biz.smartengines.smartid.swig.RecognitionSession;
import biz.smartengines.smartid.swig.SegmentationResult;
import biz.smartengines.smartid.swig.SegmentationResultVector;
import biz.smartengines.smartid.swig.SessionSettings;
import biz.smartengines.smartid.swig.StringField;
import biz.smartengines.smartid.swig.StringVector;


public class CameraView extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback, View.OnClickListener {

    private static final String module_log = "SMARTID_DEMO_APP";  // log name

    private static final int REQUEST_CAMERA = 1;  // id for request camera permission
    private static boolean camera_ready = false;  // is camera ready to work
    private android.hardware.Camera camera = null;  // camera class
    private SurfaceHolder holder;  // holder for camera preview

    private Timer timer;
    private boolean use_timer = false;
    private final int timer_delay = 1000;   // focusing timer start delay
    private final int timer_period = 3000;  // focusing timer period

    private ElementsView view;  // visualisation class during wokr process

    private static RecognitionEngine engine;  // engine class
    private static boolean is_configured = false;  // is engine configured
    private static boolean need_copy_assets = false; // need to extract working files from assets

    private static String bundle_name = "bundle_mock_smart_idreader.zip";  // name of bundle
    private static String document_mask = "*";  // document mask, for example card.* mrz.* rus.passport.*

    private static SessionSettings sessionSettings;  // settings for recognition
    private static RecognitionSession session;  // recognition session
    private static boolean session_working = false; // is session working for the moment

    private boolean is_nexus_5x = Build.MODEL.contains("Nexus 5X");  // the phone is Nexus 5X

    private static volatile byte[] data = null;  // current frame buffer

    private Semaphore frame_waiting;  // semaphore waiting for the frame
    private Semaphore frame_ready;  // semaphore the frame is ready

    //----------------------------------------------------------------------------------------------

    private class Focus extends TimerTask {  // focusing timer

        public void run() {

            focusing();
        }
    }

    public void focusing() {

        try{
            Camera.Parameters cparams = camera.getParameters();

            if( cparams.getMaxNumFocusAreas() > 0)  // focus if at least one focus area exists
            {
                camera.cancelAutoFocus();
                cparams.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                camera.setParameters(cparams);
            }
        }catch(RuntimeException e)
        {
            Log.d(module_log, "Cannot focus: " + e.getMessage());
        }
    }

    private View.OnClickListener onFocus = new View.OnClickListener() {

        public void onClick(View v) {

            if (use_timer == false)  // focus on tap while not using timer focusing
                focusing();
        }
    };

    //----------------------------------------------------------------------------------------------

    class InitCore extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... unused) {

            if (need_copy_assets)  // extract bundle file from assets
                copyAssets("data");

            configureEngine();  // configure engine
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            if(is_configured)
            {
                setDocumentsList();  // set document list
                view.invalidate();  // update view
            }
        }
    }

    private void configureEngine() {

        try {
            System.loadLibrary("jniSmartIdEngine");  // load library
            String bundle_path = getFilesDir().getAbsolutePath() + File.separator + bundle_name;  // full path to bundle

            engine = new RecognitionEngine(bundle_path);  // create engine
            sessionSettings = engine.CreateSessionSettings();  // create setting for engine
            is_configured = true;

        } catch (RuntimeException e) {
            Log.d(module_log, "Cannot init engine: " + e.getMessage());
        }
          catch(UnsatisfiedLinkError e) {
              Log.d(module_log, "Cannot load library: " + e.getMessage());
          }
    }

    void copyAssets(String bundle_dir) {  // copy file from start directory bundle_dir

        try {
            AssetManager assetManager = getAssets();
            final String input_bundle_path = bundle_dir + File.separator + bundle_name;
            final String output_bundle_dir = getFilesDir().getAbsolutePath() + File.separator;

            InputStream input_stream = assetManager.open(input_bundle_path);
            File output_bundle_file = new File(output_bundle_dir, bundle_name);
            OutputStream output_stream = new FileOutputStream(output_bundle_file);

            int length = 0;
            byte[] buffer = new byte[1024];

            while ((length = input_stream.read(buffer)) > 0) {

                output_stream.write(buffer, 0, length);
            }

            input_stream.close();
            output_stream.close();

        } catch (IOException e) {
            Log.d(module_log, "Cannot copy bundle: " + e.getMessage());
        }
    }

    void setDocumentsList()
    {
        ArrayList<String> doc_types = new ArrayList<>();

        try {
            sessionSettings.RemoveEnabledDocumentTypes("*");  // disable all documents
            sessionSettings.AddEnabledDocumentTypes(document_mask);  // enable document list by mask

            StringVector document_types = sessionSettings.GetEnabledDocumentTypes();  // get full names of enabled documents

            for (int i = 0; i < document_types.size(); i++)
                doc_types.add(document_types.get(i));

        } catch (RuntimeException e) {
            Log.d(module_log, "Cannot set document types: " + e.getMessage());
        }

        view.SetDocumentsTypes(doc_types);  // show document list
    }

    //----------------------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_view);

        checkParameters();  // check parameters before start

        SurfaceView surface = (SurfaceView) findViewById(R.id.preview);
        surface.setOnClickListener(onFocus);

        holder = surface.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);  // set for compatibility with old android versions

        view = new ElementsView(this);  // layout to show document frames during process

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.layout);
        layout.addView(view);

        ImageButton start = (ImageButton) findViewById(R.id.start);
        start.setOnClickListener(this);

        if (!is_configured) {
            InitCore init = new InitCore();  // init core in different thread
            init.execute();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder_) {

        if( camera_ready == false && needPermission(Manifest.permission.CAMERA) == true )  // request camera access permission if needed
            requestPermission(Manifest.permission.CAMERA, REQUEST_CAMERA);
        else
            initCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        if(session_working == true)
        {
            stop_session();  // stop session
        }

        if(use_timer == true)  // stop focus timer
        {
            timer.cancel();
            use_timer = false;
        }

        if(camera_ready == true)  // stop preview
        {
            camera.stopPreview();
            camera.release();

            camera = null;
            camera_ready = false;
        }
    }

    @Override
    public void onClick(View v)
    {
        if (v.getId() == R.id.start) {
            start_session();
        }
    }

    protected void checkParameters()
    {
        int version_code = BuildConfig.VERSION_CODE;    // application code version

        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(this);
        int version_current = sPref.getInt("version_code", -1); // code version from previous start

        need_copy_assets = version_code != version_current;  // update bundle if needed

        SharedPreferences.Editor ed = sPref.edit();  // update version in preferences
        ed.putInt("version_code", version_code);
        ed.commit();
    }

    //----------------------------------------------------------------------------------------------

    public boolean needPermission(String permission)
    {
        int result = ContextCompat.checkSelfPermission(this, permission);  // check if permission is granted
        return result != PackageManager.PERMISSION_GRANTED;
    }

    public void requestPermission(String permission, int request_code)
    {
        ActivityCompat.requestPermissions(this, new String[]{permission}, request_code);  // ask for permission
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
    {
        switch (requestCode) {
            case REQUEST_CAMERA: {

                boolean is_granted = false;

                for(int grantResult : grantResults)
                {
                    if(grantResult == PackageManager.PERMISSION_GRANTED)  // permission granted
                        is_granted = true;
                }

                if (is_granted)
                    initCamera();
                else
                    toast("To continue please enable CAMERA permission in App Settings");
            }
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public void initCamera()
    {
        try {
            if(camera == null)
                camera = Camera.open();  // open camera

            if (camera != null) {

                setCameraParams();  // set camera parameters
                camera.setPreviewDisplay(holder);  // set preview surface holder
                camera.startPreview();  // start preview
                camera_ready = true;
            }

        } catch (IOException | RuntimeException e)
        {
            Log.d(module_log, "Cannot init camera: " + e.getMessage());
        }
    }

    private void setCameraParams()
    {
        Camera.Parameters params = camera.getParameters();

        List<String> focus_modes = params.getSupportedFocusModes();  // supported focus modes
        String focus_mode = Camera.Parameters.FOCUS_MODE_AUTO;
        boolean isAutoFocus = focus_modes.contains(focus_mode);

        if (isAutoFocus) {  // camera can autofocus

            if (focus_modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                focus_mode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
            else if (focus_modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
                focus_mode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
        } else {
            focus_mode = focus_modes.get(0);  // camera doesn't support autofocus so select the first mode
        }

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        float display_ratio = (float)metrics.heightPixels / (float)metrics.widthPixels;  // display ratio

        List<Camera.Size> sizes = params.getSupportedPreviewSizes();
        Camera.Size preview_size = sizes.get(0);

        final float tolerance = 0.1f;
        float preview_ratio_diff = Math.abs( (float) preview_size.width / (float) preview_size.height - display_ratio);

        for (int i = 1; i < sizes.size() ; i++)  // select the best preview size that fits to screen
        {
            Camera.Size tmp_size = sizes.get(i);
            float tmp_ratio_diff =  Math.abs( (float) tmp_size.width / (float) tmp_size.height - display_ratio);

            if( Math.abs(tmp_ratio_diff - preview_ratio_diff) < tolerance && tmp_size.width > preview_size.width || tmp_ratio_diff < preview_ratio_diff)
            {
                preview_size = tmp_size;
                preview_ratio_diff = tmp_ratio_diff;
            }
        }

        params.setFocusMode(focus_mode);  // set focus mode
        params.setPreviewSize(preview_size.width, preview_size.height);  // set preview size
        camera.setParameters(params);
        camera.setDisplayOrientation(!is_nexus_5x ? 90 : 270);  // set portrait orientation

        if (focus_mode == Camera.Parameters.FOCUS_MODE_AUTO) // start timer focusing only if no hardware continuous auto focus and device supports auto focus
        {
            timer = new Timer();
            timer.schedule(new Focus(), timer_delay, timer_period);
            use_timer = true;
        }

        view.SetPreviewSize(preview_size.height, preview_size.width);
    }

    //----------------------------------------------------------------------------------------------

    @Override
    public void onPreviewFrame(byte[] data_, Camera camera)  // get current camera frame
    {
        if(frame_waiting.tryAcquire() && session_working)  // if frame waiting status - get current frame
        {
            data = data_;
            frame_ready.release();  // frame is ready
        }
    }


    void start_session()
    {
        if (is_configured == true && camera_ready == true) {

            try {

                sessionSettings.SetOption("common.sessionTimeout", "5.0");  // set session timeout
                session = engine.SpawnSession(sessionSettings);

                camera.setPreviewCallback(this);  // set preview callback onPreviewFrame(...)
                session_working = true;

                frame_waiting = new Semaphore(1, true);  // create semaphores
                frame_ready = new Semaphore(0, true);

                view.Start();

                new EngineTask().execute();  // start recognition thread
                view.invalidate();


            } catch (RuntimeException e) {
                Log.d(module_log, "Cannot init session with: " + document_mask);
                return;
            }

            ImageButton start = (ImageButton) findViewById(R.id.start);  // make start button invisible
            start.setVisibility(View.INVISIBLE);
        }
    }

    void stop_session()
    {
        session_working = false;
        data = null;

        frame_waiting.release();  // release semaphores
        frame_ready.release();

        camera.setPreviewCallback(null);  // stop preview

        view.Stop();
        view.invalidate();

        ImageButton start = (ImageButton) findViewById(R.id.start);  // make start button visible
        start.setVisibility(View.VISIBLE);
    }

    //----------------------------------------------------------------------------------------------

    void show_result(RecognitionResult result)
    {
        Intent intent = new Intent(CameraView.this, ResultView.class);
        ArrayList<FieldData> fields = new ArrayList<FieldData>();

        StringVector texts = result.GetStringFieldNames(); // get all string results
        StringVector images = result.GetImageFieldNames(); // get all image results

        for (int i = 0; i < texts.size(); i++)   // get text fields
        {
            StringField field = result.GetStringField(texts.get(i)); // field class
            String value = field.GetUtf8Value();  // value
            boolean is_accepted = field.IsAccepted();  // is accepted

            fields.add(new FieldData(field.GetName(), value, is_accepted));
        }

        for (int i = 0; i < images.size(); i++)  // get image fields
        {
            ImageField field = result.GetImageField(images.get(i));  // image class

            Bitmap image = getBitmap(field.GetValue());  // convert to bitmap
            if (image != null)
                fields.add(new FieldData(field.GetName(), image, field.IsAccepted()));
        }

        FieldsSingleton.get().setFieldData(fields);  // store all data to singleton
        startActivity(intent);  // show result activity
    }

    public Bitmap getBitmap(Image image) {  // convert Image to Bitmap

        Bitmap bitmap = null;

        try{
            int nChannels = image.getChannels();
            int sizeBytes = image.GetRequiredBufferLength();
            byte[] bytes = new byte[sizeBytes];
            image.CopyToBuffer(bytes);

            if (bytes != null) {

                int sizePixels = image.getHeight() * image.getWidth();
                int[] pixels = new int[sizePixels];

                int r = 0, g = 0, b = 0;

                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        if (nChannels == 1)
                            r = g = b = (bytes[x + y * image.getStride()] & 0xFF);

                        if (nChannels == 3) {
                            b = bytes[3 * x + y * image.getStride() + 0] & 0xFF;
                            g = bytes[3 * x + y * image.getStride() + 1] & 0xFF;
                            r = bytes[3 * x + y * image.getStride() + 2] & 0xFF;
                        }

                        pixels[x + y * image.getWidth()] = Color.rgb(b, g, r);
                    }
                }

                bitmap = Bitmap.createBitmap(pixels, image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);


            }
        }catch(RuntimeException e)
        {
            Log.d(module_log, "Cannot process bitmap: " + e.toString());
        }

        return bitmap;
    }

    @Override
    public void onBackPressed()
    {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    class EngineTask extends AsyncTask<Void, RecognitionResult, Void>
    {
        @Override
        protected Void doInBackground(Void... unused) {

            while (true) {

                try {
                    frame_ready.acquire();  // waiting for the frame

                    if(session_working == false)
                        break;

                    Camera.Size size = camera.getParameters().getPreviewSize();
                    RecognitionResult result = session.ProcessYUVSnapshot(data, size.width, size.height, !is_nexus_5x? ImageOrientation.Portrait : ImageOrientation.InvertedPortrait);  // process frame
                    publishProgress(result);  // show current result

                }catch(RuntimeException e)
                {
                    Log.d(module_log, "Cannot process snapshot: " + e.toString());
                }
                catch(InterruptedException e)
                {
                    Log.d(module_log, "Problem with frame_ready semaphore: " + e.toString());
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(RecognitionResult... res)
        {
            RecognitionResult result = res[0];

            if (result.IsTerminal() == true)  // recognition process is stopped by getting all fields correctly or timeout
            {
                stop_session();
                show_result(result);

            } else {

                MatchResultVector match = result.GetMatchResults();

                if (match.size() > 0)
                {
                    view.Clear();

                    for (int i = 0; i < match.size(); i++)
                        view.AddQuad(match.get(i).getQuadrangle());  // show current document zones

                    SegmentationResultVector segmentationResultVector = result.GetSegmentationResults();

                    for (int j = 0; j < segmentationResultVector.size(); j++) {

                        SegmentationResult segResult = segmentationResultVector.get(j);
                        StringVector zoneNames = segResult.GetZoneNames();

                        for (int k = 0; k < zoneNames.size(); k++) {
                            Quadrangle quad = segResult.GetZoneQuadrangle(zoneNames.get(k));  // show current fields zones
                            view.AddQuad(quad);
                        }
                    }
                }

                view.invalidate();
                frame_waiting.release();  //
            }
        }
    }

    void toast(String msg)
    {
        Toast error_message = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG);
        error_message.show();
    }
}


