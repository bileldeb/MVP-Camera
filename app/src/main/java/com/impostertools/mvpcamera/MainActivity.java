package com.impostertools.mvpcamera;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.slider.Slider;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import jp.co.cyberagent.android.gpuimage.GPUImageView;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageChromaKeyBlendFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_CODE_CAMERA_PERMISSION = 200;
    private static final String[] PERMISSIONS = {Manifest.permission.CAMERA};


    ConstraintLayout container;
    ImageButton camera_capture_button;
    GPUImageView gpuImageView;
    TextView textView;
    Executor executor;
    ImageButton colorPicker;
    ImageButton imagePicker;
    ImageButton previewRecording;
    Slider threshold;
    Slider smoothing;
    GPUImageFilter chromakey;
    //TODO how to sprecifiy path??
    String videoPath = "path";

    Bitmap bgBMP;

    float mChromaThreshold= (float) 0.3;
    float mSmoothing= (float) 0.1;
    float mR;
    float mG;
    float mB;

    boolean isRecording = false;
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    Random random = new Random();

    Matrix mat = new Matrix();
    int rotDeg = 1;
    private long mLastAnalysisResultTime;

    @SuppressLint("ResourceAsColor")
    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        container = findViewById(R.id.camera_container);
        textView = findViewById(R.id.fps);
        camera_capture_button = findViewById(R.id.camera_capture_button);



        //GPUIMAGE VIEW SETUP
        gpuImageView = findViewById(R.id.gpuimageview);
        chromakey = new GPUImageChromaKeyBlendFilter();
        gpuImageView.setFilter(chromakey);
        Drawable bgD = AppCompatResources.getDrawable(this, R.drawable.bg);
        bgBMP = ((BitmapDrawable) bgD).getBitmap();
        ((GPUImageChromaKeyBlendFilter) chromakey).setBitmap(bgBMP);




        //UI CONTROLS
        colorPicker=findViewById(R.id.COLORpicker);
        colorPicker.setBackgroundTintList(AppCompatResources.getColorStateList(getApplicationContext(), R.color.white));
        colorPicker.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View view) {
                threshold.setValue(0);
                smoothing.setValue(0);
                pickColor();
            }
        });

        imagePicker=findViewById(R.id.IMGpicker);
        imagePicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickImage();
            }
        });

        previewRecording=findViewById((R.id.previewRecording));
        previewRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                previewREC();
            }
        });

        threshold=findViewById(R.id.threshold);
        smoothing=findViewById(R.id.smoothing);






        executor = Executors.newSingleThreadExecutor();
        camera_capture_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "Recording must be implemented",Toast.LENGTH_SHORT).show();
            }
            // ADD CODE HERE
            // CHECK RECORDING STATUS AND CALL EITHER initRecorder() or stopRecorder()

        });


        if(checkPermission()) {
            startCamera();
        }
    }



    private boolean checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS,
                    REQUEST_CODE_CAMERA_PERMISSION);
            return false;
        }
        // ADD CODE HERE
        // RECORDING WITH AUDIO ADD AUDIO PERMISSION IN MANIFEST AND COMPLETE CHECK PERMISSION TO INCLUDE AUDIO
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(
                        this,
                        "You can't use MVP Camera without granting CAMERA permission",
                        Toast.LENGTH_LONG)
                        .show();
                finish();
            } else {
                startCamera();
            }
        }
    }

    private void startCamera() {

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture
                = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        bindPreview(cameraProvider);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {

        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        executor = Executors.newSingleThreadExecutor();
        Display d = getDisplay();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();



        //IMAGE ANALYSIS
        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                int rotationDegrees = image.getImageInfo().getRotationDegrees();
                @SuppressLint("UnsafeExperimentalUsageError") Image imagine = image.getImage();
                if (rotDeg != rotationDegrees){
                    mat.postRotate(rotationDegrees);
                    rotDeg = rotationDegrees;
                }

                mChromaThreshold= threshold.getValue();
                ((GPUImageChromaKeyBlendFilter) chromakey).setThresholdSensitivity(mChromaThreshold);

                mSmoothing= smoothing.getValue();
                ((GPUImageChromaKeyBlendFilter) chromakey).setSmoothing(mSmoothing);




                Bitmap frame = toBitmap(imagine,mat);
                gpuImageView.setImage(frame);

                if(isRecording){
                    updateRecorder(frame);
                }
                //gpuImageView.setRotation(rotDeg);


                if(SystemClock.elapsedRealtime() - mLastAnalysisResultTime < 500) {
                    image.close();
                    return;
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        long duration = SystemClock.elapsedRealtime() - mLastAnalysisResultTime;
                        double fps;

                        if(duration > 0)
                            fps = 1000.f / duration;
                        else
                            fps = 1000.f;
                        textView.setText("Threshold = " + String.valueOf(mChromaThreshold) + "     |      Smoothing = " + String.valueOf(mSmoothing));
                    }
                });

                mLastAnalysisResultTime = SystemClock.elapsedRealtime();
                image.close();
            }
        });

        cameraProvider.unbindAll();
        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this,
                cameraSelector, imageAnalysis);
        CameraControl cameraControl = camera.getCameraControl();
        // cameraControl.setLinearZoom(0.9F);
    }



    @RequiresApi(api = Build.VERSION_CODES.O)
    private void pickColor() {
        gpuImageView.setOnTouchListener(new View.OnTouchListener() {
            @RequiresApi(api = Build.VERSION_CODES.Q)
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                Bitmap current = null;
                try {
                    current = gpuImageView.capture();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Color c = current.getColor((int)motionEvent.getX(), (int)motionEvent.getY());
                mR = c.red();
                mG = c.green();
                mB = c.blue();
                ((GPUImageChromaKeyBlendFilter) chromakey).setColorToReplace(mR,mG,mB);
                colorPicker.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(mR,mG,mB)));
                smoothing.setValue((float) 0.1);
                threshold.setValue((float) 0.05);
                gpuImageView.setOnTouchListener(null);
                return false;
            }
        });
        Toast.makeText(getApplicationContext(),"Please press on the colour",Toast.LENGTH_SHORT).show();
    }

    private void pickImage() {
        Intent pickPhoto = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickPhoto , 1);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
        if(resultCode == RESULT_OK){
            Uri selectedImage = imageReturnedIntent.getData();

            Bitmap bitmap = null;
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
            } catch (IOException e) {
                e.printStackTrace();
            }

            bgBMP = bitmap;

            chromakey = new GPUImageChromaKeyBlendFilter();
            ((GPUImageChromaKeyBlendFilter) chromakey).setBitmap(bgBMP);
            gpuImageView.setFilter(chromakey);

            ((GPUImageChromaKeyBlendFilter) chromakey).setColorToReplace(mR,mG,mB);
            ((GPUImageChromaKeyBlendFilter) chromakey).setThresholdSensitivity(mChromaThreshold);
            ((GPUImageChromaKeyBlendFilter) chromakey).setSmoothing(mSmoothing);


            imagePicker.setImageBitmap(getRoundedShape(bgBMP));
        }
    }

    private void previewREC() {
        Toast.makeText(getApplicationContext(),"preview should be implemented",Toast.LENGTH_SHORT).show();
        // ADD CODE HERE
        // LAUNCH INTENT TO SWITCH TO SECOND ACTIVITY
        // CREATE ACTIVITY WITH RECORDED VIDEOS IN IT (IMAGE BUTTON)
        // WHEN USER CLICKS ON ONE LAUNCH INTENT TO PLAY VIDEO
    }

    public void startRecording(){

        //if recording is not complete yet then prevent the stream from getting cleared
        if(isRecording){
            return;
        }
        this.stream.reset();
        isRecording = true;
    }
    public void updateRecorder(Bitmap frame){
        //compresses bitmap to png format and adds it to the bytearray
        frame.compress(Bitmap.CompressFormat.PNG, 100, stream);
    }
    public void stopRecording(){
        isRecording = false;
        new Thread (){
            @Override
            public void run() {
                super.run();
                try {
                    @SuppressWarnings("resource")
                    FileInputStream v_input = new FileInputStream(videoPath);
                    //TODO:
                    //Object videodata = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT);

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            };

    };






    //NEXT FUCTIONS
    //lockCamera(); Locks focus WB and exposure
    //arCore??



    //BITMAP OPERATIONS
    public Bitmap getRoundedShape(Bitmap scaleBitmapImage) {

        int targetWidth = 250;
        int targetHeight = 250;
        Bitmap targetBitmap = Bitmap.createBitmap(targetWidth,
                targetHeight,Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(targetBitmap);
        Path path = new Path();
        path.addCircle(((float) targetWidth - 1) / 2,
                ((float) targetHeight - 1) / 2,
                (Math.min(((float) targetWidth),
                        ((float) targetHeight)) / 2),
                Path.Direction.CCW);

        canvas.clipPath(path);
        Bitmap sourceBitmap = scaleBitmapImage;
        canvas.drawBitmap(sourceBitmap,
                new Rect(0, 0, sourceBitmap.getWidth(),
                        sourceBitmap.getHeight()),
                new Rect(0, 0, targetWidth,
                        targetHeight), null);
        return targetBitmap;
    }
    private Bitmap toBitmap(Image image,Matrix mat) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);


        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        //rotation
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mat, true);


        return bmp ;
    }

}