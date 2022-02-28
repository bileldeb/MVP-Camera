package com.impostertools.mvpcamera;
import android.Manifest;
import android.animation.ValueAnimator;
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
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLException;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
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

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import jp.co.cyberagent.android.gpuimage.GPUImage;
import jp.co.cyberagent.android.gpuimage.GPUImageChromaKeyBlendFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageFilterGroup;
import jp.co.cyberagent.android.gpuimage.GPUImageMovieWriter;
import jp.co.cyberagent.android.gpuimage.GPUImageView;
import jp.co.cyberagent.android.gpuimage.Rotation;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_CODE_CAMERA_PERMISSION = 200;
    private static final String[] PERMISSIONS = {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO};
    private static final int REQUEST_CODE_READ_STORAGE = 300;
    private static final int REQUEST_CODE_AUDIO_PERMISSION = 400;

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
    Camera mCamera;


    Bitmap bgBMP;

    float mChromaThreshold = (float) 0.3;
    float mSmoothing = (float) 0.1;
    float mR=0.3f;
    float mG=0.31f;
    float mB=0.32f;

    boolean mIsRecording;
    GPUImageMovieWriter mMovieWriter;

    int framewidth = 1080;
    int frameheight = 1920;
    String previewPath = null;
    

    Matrix mat = new Matrix();
    int rotDeg = 1;
    private long mLastAnalysisResultTime;



    @Override
    protected void onPause() {
        super.onPause();
        refresh();
        if (mIsRecording) {
            mMovieWriter.stopRecording();
        }
    }
    

    @Override
    protected void onResume() {
        refresh();
        super.onResume();
    }

    @SuppressLint({"ResourceAsColor", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());



        container = findViewById(R.id.camera_container);
        camera_capture_button = findViewById(R.id.camera_capture_button);


        bottomSheet = findViewById(R.id.bottomSheetGroup);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setHideable(false);
        bottomSheetBehavior.setPeekHeight(getResources().getDimensionPixelSize(R.dimen.peekHeight), true);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);


        dynamicSlider = findViewById(R.id.dynamicSlider);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) dynamicSlider.getLayoutParams();


        zoom = findViewById(R.id.zoom);
        zoom.setLabelFormatter(new LabelFormatter() {
            @NonNull
            @Override
            public String getFormattedValue(float value) {
                value = value * 9.3f + 0.7f;
                //return String.format(Locale.US, "%.1f", value)+ 'x';
                return String.format(Locale.US, "%.1f", cameraInfo.getZoomState().getValue().getZoomRatio()) + 'x';
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
                if ((motionEvent.getAction() == MotionEvent.ACTION_DOWN)) {
                    shorterSlider.end();
                    longerSlider.start();
                }
                if ((motionEvent.getAction() == MotionEvent.ACTION_UP) || (motionEvent.getAction() == MotionEvent.ACTION_CANCEL)) {
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


        //GPUIMAGE  SETUP
        gpuImageView = findViewById(R.id.gpuimageview);
        chromakey = new GPUImageChromaKeyBlendFilter();
        Drawable bgD = AppCompatResources.getDrawable(this, R.drawable.bg);
        bgBMP = ((BitmapDrawable) bgD).getBitmap();
        ((GPUImageChromaKeyBlendFilter) chromakey).setBitmap(bgBMP);
        mMovieWriter = new GPUImageMovieWriter();
        GPUImageFilterGroup filters = new GPUImageFilterGroup();
        filters.addFilter(chromakey);
        filters.addFilter(mMovieWriter);
        gpuImageView.setFilter(filters);
        ((GPUImageChromaKeyBlendFilter) chromakey).setColorToReplace(mR, mG, mB);






        //UI CONTROLS
        colorPicker = findViewById(R.id.COLORpicker);
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

        imagePicker = findViewById(R.id.IMGpicker);
        imagePicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickImage();
            }
        });

        previewRecording = findViewById((R.id.previewRecording));
        previewRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                previewREC();
            }
        });


        executor = Executors.newSingleThreadExecutor();

        camera_capture_button.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View v) {
                onClickRecord(camera_capture_button);
            }
        });

        if (checkPermission()) {
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS,
                    REQUEST_CODE_AUDIO_PERMISSION);
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

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        executor = Executors.newSingleThreadExecutor();
        Display d = getDisplay();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(framewidth, frameheight))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();


        //IMAGE ANALYSIS
        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                int rotationDegrees = image.getImageInfo().getRotationDegrees();
                @SuppressLint("UnsafeExperimentalUsageError") Image imagine = image.getImage();
                if (rotDeg != rotationDegrees) {
                    mat.postRotate(rotationDegrees);
                    rotDeg = rotationDegrees;
                }

                mChromaThreshold = threshold.getValue();
                ((GPUImageChromaKeyBlendFilter) chromakey).setThresholdSensitivity(mChromaThreshold);

                mSmoothing = smoothing.getValue();
                ((GPUImageChromaKeyBlendFilter) chromakey).setSmoothing(mSmoothing);

                cameraControl.setLinearZoom(zoom.getValue());


                Bitmap frame = toBitmap(imagine, mat);
                gpuImageView.setImage(frame);



                //gpuImageView.setRotation(rotDeg);

                if (SystemClock.elapsedRealtime() - mLastAnalysisResultTime < 500) {
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
        mCamera = cameraProvider.bindToLifecycle((LifecycleOwner) this,
                cameraSelector, imageAnalysis);
        cameraControl = mCamera.getCameraControl();
        cameraInfo = mCamera.getCameraInfo();
        // if you use this
        //gpuImage.setRotation(Rotation.ROTATION_90);
        // and remove the matrix rotation in toBitmap(...) the frame rate becomes much better
        //but the recorded video somehow is rotated and there will be issues with the pickcolr..
    }


    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void pickColor() {
        Toast.makeText(getApplicationContext(), "Pick a color", Toast.LENGTH_SHORT).show();
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
                Log.i(TAG, "onTouch W: "+ String.valueOf(current.getWidth()));
                Log.i(TAG, "onTouch H: "+ String.valueOf(current.getHeight()));

                Log.i(TAG, "onTouch X: "+ String.valueOf(motionEvent.getX()));
                Log.i(TAG, "onTouch Y: "+ String.valueOf(motionEvent.getY()));

                Color c = current.getColor((int) motionEvent.getX(), (int) motionEvent.getY());
                mR = c.red();
                mG = c.green();
                mB = c.blue();


                ((GPUImageChromaKeyBlendFilter) chromakey).setColorToReplace(mR, mG, mB);
                colorPicker.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(mR, mG, mB)));
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
        startActivityForResult(pickPhoto, 1);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
        if (resultCode == RESULT_OK) {
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
            GPUImageFilterGroup filters = new GPUImageFilterGroup();
            filters.addFilter(chromakey);
            filters.addFilter(mMovieWriter);
            gpuImageView.setFilter(filters);
            ((GPUImageChromaKeyBlendFilter) chromakey).setColorToReplace(mR, mG, mB);
            ((GPUImageChromaKeyBlendFilter) chromakey).setThresholdSensitivity(mChromaThreshold);
            ((GPUImageChromaKeyBlendFilter) chromakey).setSmoothing(mSmoothing);
            imagePicker.setImageBitmap(getRoundedShape(bgBMP));
        }
    }

    private void refresh(){
        chromakey = new GPUImageChromaKeyBlendFilter();
        ((GPUImageChromaKeyBlendFilter) chromakey).setBitmap(bgBMP);
        GPUImageFilterGroup filters = new GPUImageFilterGroup();
        filters.addFilter(chromakey);
        filters.addFilter(mMovieWriter);
        gpuImageView.setFilter(filters);
        ((GPUImageChromaKeyBlendFilter) chromakey).setColorToReplace(mR, mG, mB);
        ((GPUImageChromaKeyBlendFilter) chromakey).setThresholdSensitivity(mChromaThreshold);
        ((GPUImageChromaKeyBlendFilter) chromakey).setSmoothing(mSmoothing);
    }

    private void previewREC() {
        if (previewPath == null){
            Intent playVideo = new Intent(Intent.ACTION_VIEW,MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            startActivity(playVideo);
        }
        else {
            Intent playVideo = new Intent(Intent.ACTION_VIEW);
            playVideo.setDataAndType(Uri.fromFile(new File(previewPath)) ,"video/*");
            startActivity(playVideo);
        }

    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private String makePath() {
        File fileDir = new File(Environment.getExternalStorageDirectory().toString() + "/Movies/MVPCamera");
        if (!fileDir.exists()) {
            try {
                Files.createDirectories(fileDir.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Date date = new Date();
        String timestamp = String.valueOf(date.getTime());
        File vidFile = null;
        vidFile = new File(fileDir.getPath(), "mvpc_" + timestamp + ".mp4");

        return vidFile.getPath().toString();
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private void onClickRecord(ImageButton btn) {
        Log.i(TAG, "onClickRecord: "+String.valueOf(mIsRecording));
        if (mIsRecording) {
            // go to stop recording
            Drawable shutter = AppCompatResources.getDrawable(this, R.drawable.ic_shutter_normal);
            camera_capture_button.setBackground(shutter);
            mIsRecording = false;
            mMovieWriter.stopRecording();
        } else {
            //set recording preview
            Bitmap current = null;
            try {
                current = gpuImageView.capture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            previewRecording.setImageBitmap(getRoundedShape(current));
            // go to start recording
            mIsRecording = true;
            Drawable shutter = AppCompatResources.getDrawable(this, R.drawable.ic_shutter_focused);
            camera_capture_button.setBackground(shutter);
            previewPath = makePath();
            mMovieWriter.startRecording(previewPath, framewidth, frameheight);
        }
    }

    //BITMAP OPERATIONS
    public Bitmap getRoundedShape(Bitmap scaleBitmapImage) {

        int targetWidth = 250;
        int targetHeight = 250;
        Bitmap targetBitmap = Bitmap.createBitmap(targetWidth,
                targetHeight, Bitmap.Config.ARGB_8888);
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

    private Bitmap toBitmap(Image image, Matrix mat) {
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

        return bmp;
    }
}
