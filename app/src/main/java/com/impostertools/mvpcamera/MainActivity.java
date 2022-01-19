package com.impostertools.mvpcamera;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
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
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
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
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Locale;
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
    private static final String[] PERMISSIONS = {Manifest.permission.CAMERA,Manifest.permission.READ_EXTERNAL_STORAGE};
    private static final int REQUEST_CODE_READ_STORAGE = 300;



    ConstraintLayout container;
    LinearLayout dynamicSlider;
    LinearLayout bottomSheet;
    ImageButton camera_capture_button;
    GPUImageView gpuImageView;
    Executor executor;
    ImageButton colorPicker;
    ImageButton imagePicker;
    ImageButton previewRecording;
    Slider threshold;
    Slider smoothing;
    Slider zoom;
    GPUImageFilter chromakey;
    BottomSheetBehavior<LinearLayout> bottomSheetBehavior;

    CameraControl cameraControl;
    CameraInfo cameraInfo;

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

    Matrix mat = new Matrix();
    int rotDeg = 1;
    private long mLastAnalysisResultTime;

    @SuppressLint({"ResourceAsColor", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        container = findViewById(R.id.camera_container);
        camera_capture_button = findViewById(R.id.camera_capture_button);


        bottomSheet = findViewById(R.id.bottomSheetGroup);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setHideable(false);
        bottomSheetBehavior.setPeekHeight(getResources().getDimensionPixelSize(R.dimen.peekHeight),true);

        dynamicSlider = findViewById(R.id.dynamicSlider);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) dynamicSlider.getLayoutParams();


        zoom = findViewById(R.id.zoom);
        zoom.setLabelFormatter(new LabelFormatter() {
            @NonNull
            @Override
            public String getFormattedValue(float value) {
                value=value*9.3f +0.7f;
                //return String.format(Locale.US, "%.1f", value)+ 'x';
                return String.format(Locale.US, "%.1f", cameraInfo.getZoomState().getValue().getZoomRatio())+ 'x';
            }
        });

                ValueAnimator longerSlider = ValueAnimator.ofInt(getResources().getDimensionPixelSize(R.dimen.shortZoomSlider), getResources().getDimensionPixelSize(R.dimen.longZoomSlider));
                longerSlider.setDuration(200);
                ValueAnimator shorterSlider = ValueAnimator.ofInt(getResources().getDimensionPixelSize(R.dimen.longZoomSlider), getResources().getDimensionPixelSize(R.dimen.shortZoomSlider));
                shorterSlider.setDuration(100);
                shorterSlider.setStartDelay(20);
                longerSlider.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        layoutParams.width = (int) valueAnimator.getAnimatedValue();
                        dynamicSlider.setLayoutParams(layoutParams);
                    }
                });
                shorterSlider.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        layoutParams.width = (int) valueAnimator.getAnimatedValue();
                        dynamicSlider.setLayoutParams(layoutParams);
                    }
                });

        zoom.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                    if ((motionEvent.getAction() == MotionEvent.ACTION_DOWN))  {
                        shorterSlider.end();
                        longerSlider.start();
                    }
                    if ((motionEvent.getAction() == MotionEvent.ACTION_UP) || (motionEvent.getAction() == MotionEvent.ACTION_CANCEL) ) {
                        longerSlider.end();
                        shorterSlider.start();
                    }

                return false;
            }
        });


        threshold = findViewById(R.id.threshold);
        threshold.setLabelFormatter(new LabelFormatter() {
            @NonNull
            @Override
            public String getFormattedValue(float value) {
                return "Threshold: " + String.format(Locale.US, "%.2f", value);
            }
        });


        smoothing = findViewById(R.id.smoothing);
        smoothing.setLabelFormatter(new LabelFormatter() {
            @NonNull
            @Override
            public String getFormattedValue(float value) {
                return "Smoothing: " + String.format(Locale.US, "%.2f", value);
            }
        });



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
                bottomSheetBehavior.setHideable(true);
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
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








        executor = Executors.newSingleThreadExecutor();
        camera_capture_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "Recording must be implemented",Toast.LENGTH_SHORT).show();
                startRecording();
                if(isRecording){
                    stopRecording();
                }
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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS,
                    REQUEST_CODE_READ_STORAGE);
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
        if (requestCode == REQUEST_CODE_READ_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(
                        this,
                        "Ymission",
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

                cameraControl.setLinearZoom(zoom.getValue());




                Bitmap frame = toBitmap(imagine,mat);
                gpuImageView.setImage(frame);

                if(isRecording){
                    try {
                        updateRecorder(gpuImageView.capture());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                //gpuImageView.setRotation(rotDeg);


                if(SystemClock.elapsedRealtime() - mLastAnalysisResultTime < 500) {
                    image.close();
                    return;
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //Run On UI
                    }
                });

                mLastAnalysisResultTime = SystemClock.elapsedRealtime();
                image.close();
            }
        });

        cameraProvider.unbindAll();
        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this,
                cameraSelector, imageAnalysis);
        cameraControl = camera.getCameraControl();
        cameraInfo = camera.getCameraInfo();
    }



    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void pickColor() {
        Toast.makeText(getApplicationContext(),"Pick a color",Toast.LENGTH_SHORT).show();
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
                bottomSheetBehavior.setHideable(false);
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                smoothing.setValue((float) 0.1);
                threshold.setValue((float) 0.05);
                gpuImageView.setOnTouchListener(null);
                return false;
            }
        });
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
        Toast.makeText(getApplicationContext(),"partially implemented",Toast.LENGTH_SHORT).show();
        /*
        //this should work if we fix the issue with the file path
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(new File(videoPath);
        startActivity(intent);
        */
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
                    FileInputStream v_input = new FileInputStream("/storage/emulated/0/Android/data/com.impostertools.mvpcamera/files/MVPCamera/");
                    //TODO:
                    //Object videodata = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        };
    }






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