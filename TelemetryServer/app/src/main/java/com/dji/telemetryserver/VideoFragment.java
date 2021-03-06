package com.dji.telemetryserver;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import com.dji.videostreamdecodingsample.media.DJIVideoStreamDecoder;
import com.dji.videostreamdecodingsample.media.NativeHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;

/**
 * Created by djacc on 9/26/2016.
 */

public class VideoFragment extends Fragment implements TextureView.SurfaceTextureListener, View.OnClickListener,DJIVideoStreamDecoder.IYuvDataListener   {

    private static final String TAG = VideoFragment.class.getName();
    private VideoFragment.OnFragmentInteractionListenerVideo mListener;
    protected TextureView mVideo_texture = null;

    // Codec for video live view
    protected DJICodecManager mCodecManager = null;

    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;

    //TODO: Unused? Remove?
    protected SurfaceView mVideo_surface = null;
    protected SurfaceView mVideo_mysurface = null;
    private BaseProduct mProduct = null;
    private Camera mCamera = null;

    private static VideoFragment mInstance = null;
    public static VideoFragment getInstance(){
        return mInstance;
    }

    public VideoFragment() {
        // Required empty public constructor
        mInstance=this;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_video, container, false);

        return v;
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        newViewCreated(view);
//initMuxer();
        try {
            initVideoCallback();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static MediaMuxer muxer;
    private static int videoTrackIndex;
    public static int presentationTimeUs=0;
    private static boolean muxerRunning=false;
    public static void initMuxer() {
        try {
            muxer = new MediaMuxer(Environment.getExternalStorageDirectory() + "/AAA.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // More often, the MediaFormat will be retrieved from MediaCodec.getOutputFormat()
        // or MediaExtractor.getTrackFormat().
        MediaFormat videoFormat = new MediaFormat();
        MediaFormat outputFormat = MediaFormat.createVideoFormat( "video/avc", 1280, 720);
        outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE,12000);
        outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE,30);
        outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);

        videoTrackIndex = muxer.addTrack(outputFormat);
        presentationTimeUs=0;

        muxer.start();
        muxerRunning=true;

    }
    public static void doMux(byte[] input){
        if(!muxerRunning)
            return;
//        int bufferSize=11111;
//        ByteBuffer inputBuffer = ByteBuffer.allocate(bufferSize);
        ByteBuffer inputBuffer = ByteBuffer.wrap(input,0,input.length);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();


        presentationTimeUs+=1000/30;
        if(VideoFragment.presentationTimeUs>5000) {
            muxerRunning = false;
            bufferInfo.set(0, input.length, presentationTimeUs, BUFFER_FLAG_END_OF_STREAM);
        }
        else
            bufferInfo.set(0,input.length,presentationTimeUs, MediaCodec.BUFFER_FLAG_KEY_FRAME);

        int currentTrackIndex = videoTrackIndex;
        muxer.writeSampleData(currentTrackIndex, inputBuffer, bufferInfo);
        if(muxerRunning == false)
            freeMuxer();
    }
    public static void freeMuxer()
    {
TelemetryService.Log("freeMuxer()");
        muxer.stop();
        muxer.release();
TelemetryService.Log("freeMuxer() end");
    }

    private void initVideoCallback() throws IOException {

        mReceivedVideoDataCallBack = new VideoFeeder.VideoDataCallback() {

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onReceive( byte[] videoBuffer, int size ) {
                //TelemetryService.LogDebug"codec onReceive");
                if(origCallback!=null)
                {
                    //TelemetryService.LogDebug"codec passthru");
                    //origCallback.onReceive(videoBuffer,size);
                }
                //on screen preview.
                if (mCodecManager != null) {
                    //TelemetryService.LogDebug"codec onReceive to DJI");
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                }


                //screenshots codec.
                boolean doSS = true;
                if (doSS) {
                    //TelemetryService.LogDebug"codec onReceive to DJIVideoStreamDecoder");
                    DJIVideoStreamDecoder.getInstance().parse(videoBuffer, size);
                }

            }
        };



    }

    @Override
    public void onResume() {
        //TelemetryService.LogDebug("codec onResume");

        super.onResume();
        if (mVideo_texture == null) {
            Log.e(TAG, "video texture surface is null");
        }
    }

    @Override
    public void onPause() {
        //TelemetryService.LogDebug("codec onPause");
        super.onPause();
    }

    @Override
    public void onDestroy() {
        //TelemetryService.LogDebug("codec onDestroy");
        super.onDestroy();
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (VideoFragment.OnFragmentInteractionListenerVideo) getActivity();
        } catch (ClassCastException e) {
            throw new ClassCastException(getActivity().toString() + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onClick(View v) {
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        //TelemetryService.LogDebug("codec onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(getActivity(), surfaceTexture, width, height);

            //right place to call this?
            setVideoCallbacks();
            //todo: cleanup in destroyed.
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        //TelemetryService.LogDebug"codec onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        //TelemetryService.LogDebug"codec onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }


    public interface OnFragmentInteractionListenerVideo {
        public void onFragmentInteractionVideo(boolean bl_OnTopJustClicked);
    }


/////////////////////////////////////////////////////////////////


    //todo factor this out.
    private View myView=null;
    private void newViewCreated(View view)
    {
        //TelemetryService.LogDebug("codec newViewCreated");
        mVideo_texture = (TextureView) view.findViewById(R.id.livestream_preview_ttv);
        myView=view;

        initScreenShotCodec();
    }


    private boolean listenerInstalled=false;
    public void notifyProductUpdate()
    {
        TelemetryService.LogDebug("codec notifyProductUpdate");

        if(VideoFeeder.getInstance()==null || VideoFeeder.getInstance().getPrimaryVideoFeed()==null)
        {
            TelemetryService.LogDebug("codec notifyProductUpdate Camera unavalible");
            return;
        }

        if(myView!=null) {
            TelemetryService.LogDebug("codec myView "+listenerInstalled);

            if(myView==null)
                mVideo_texture = (TextureView) myView.findViewById(R.id.livestream_preview_ttv);

            if (mVideo_texture!=null && !listenerInstalled) {
                TelemetryService.LogDebug("codec notifyProductUpdate setSurfaceTextureListener");
                mVideo_texture.setSurfaceTextureListener(this);
                listenerInstalled=true;
            }
            if (mCodecManager == null) {
                TelemetryService.LogDebug("codec notifyProductUpdate new DJICodecManager");

                //force init at start.
                mCodecManager = new DJICodecManager(getActivity(), mVideo_texture.getSurfaceTexture(), mVideo_texture.getWidth(), mVideo_texture.getHeight());
                setVideoCallbacks();
            }

        }
        //for other codec.
        //createSurfaceListeners();
    }

    //Enable to save raw video data to a .H264 file.
    private boolean bCaptureVideo = false;

    private VideoFeeder.VideoDataCallback origCallback = null;
    private void setVideoCallbacks() {
        if(VideoFeeder.getInstance()!=null && VideoFeeder.getInstance().getPrimaryVideoFeed()!=null) {
            VideoFeeder.VideoDataCallback curCallback = VideoFeeder.getInstance().getPrimaryVideoFeed().getCallback();
            if ((curCallback == null) || (curCallback != mReceivedVideoDataCallBack)) {
                TelemetryService.LogDebug("codec setVideoCallbacks");
                if(origCallback==null)
                    TelemetryService.LogDebug("codec origCallback is NULL");
                origCallback=curCallback;
                VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(mReceivedVideoDataCallBack);

                if(bCaptureVideo)
                    startVideoCapture();
            }
        }
    }



    private void initScreenShotCodec()
    {
        NativeHelper.getInstance().init();
        DJIVideoStreamDecoder.getInstance().init(getContext(),null);// videostreamPreviewSh.getSurface());
        DJIVideoStreamDecoder.getInstance().setYuvDataListener(VideoFragment.this);
        DJIVideoStreamDecoder.getInstance().resume();
        TelemetryService.LogDebug("codec initScreenShotCodec");

    }

    public void destroyScreenShotCodec()
    {
        TelemetryService.LogDebug("codec surfaceDestroyed");
        DJIVideoStreamDecoder.getInstance().stop();
        DJIVideoStreamDecoder.getInstance().destroy();
        NativeHelper.getInstance().release();
    }
    private int screenShotInterval = 30;
    @Override
    public void onYuvDataReceived(byte[] yuvFrame, int width, int height) {


//        TelemetryService.LogDebug"codec onYuvDataReceived");
        //In this demo, we test the YUV data by saving it into JPG files.
        if (screenShotInterval> 0 && (DJIVideoStreamDecoder.getInstance().frameIndex % screenShotInterval == 0)) {
            byte[] y = new byte[width * height];
            byte[] u = new byte[width * height / 4];
            byte[] v = new byte[width * height / 4];
            byte[] nu = new byte[width * height / 4]; //
            byte[] nv = new byte[width * height / 4];
            System.arraycopy(yuvFrame, 0, y, 0, y.length);
            for (int i = 0; i < u.length; i++) {
                v[i] = yuvFrame[y.length + 2 * i];
                u[i] = yuvFrame[y.length + 2 * i + 1];
            }
            int uvWidth = width / 2;
            int uvHeight = height / 2;
            for (int j = 0; j < uvWidth / 2; j++) {
                for (int i = 0; i < uvHeight / 2; i++) {
                    byte uSample1 = u[i * uvWidth + j];
                    byte uSample2 = u[i * uvWidth + j + uvWidth / 2];
                    byte vSample1 = v[(i + uvHeight / 2) * uvWidth + j];
                    byte vSample2 = v[(i + uvHeight / 2) * uvWidth + j + uvWidth / 2];
                    nu[2 * (i * uvWidth + j)] = uSample1;
                    nu[2 * (i * uvWidth + j) + 1] = uSample1;
                    nu[2 * (i * uvWidth + j) + uvWidth] = uSample2;
                    nu[2 * (i * uvWidth + j) + 1 + uvWidth] = uSample2;
                    nv[2 * (i * uvWidth + j)] = vSample1;
                    nv[2 * (i * uvWidth + j) + 1] = vSample1;
                    nv[2 * (i * uvWidth + j) + uvWidth] = vSample2;
                    nv[2 * (i * uvWidth + j) + 1 + uvWidth] = vSample2;
                }
            }
            //nv21test
            byte[] bytes = new byte[yuvFrame.length];
            System.arraycopy(y, 0, bytes, 0, y.length);
            for (int i = 0; i < u.length; i++) {
                bytes[y.length + (i * 2)] = nv[i];
                bytes[y.length + (i * 2) + 1] = nu[i];
            }
            Log.d(TAG,
                    "onYuvDataReceived: frame index: "
                            + DJIVideoStreamDecoder.getInstance().frameIndex
                            + ",array length: "
                            + bytes.length);
            screenShot(bytes);
        }
    }

    /**
     * Save the buffered data into a JPG image file
     */

    private void screenShot(byte[] buf) {

        //todo. Get timestamp from when image was taken instead of saved.
        String timestamp=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
        String shotDir = Environment.getExternalStorageDirectory() + "/DJI_Telemetry/screens/";
        final String path =shotDir + "/"+ timestamp  + ".jpg";
        String remotePath="/screens/"+ timestamp  + ".jpg";//web relative path used for log message

        File dir = new File(shotDir);
        if (!dir.exists() || !dir.isDirectory()) {
            dir.mkdirs();
        }
        YuvImage yuvImage = new YuvImage(buf,
                ImageFormat.NV21,
                DJIVideoStreamDecoder.getInstance().width,
                DJIVideoStreamDecoder.getInstance().height,
                null);
        OutputStream outputFile;
        //final String path = dir + "/ScreenShot_" + System.currentTimeMillis() + ".jpg";

        try {
            outputFile = new FileOutputStream(new File(path));
        } catch (FileNotFoundException e) {
            Log.e(TAG, "test screenShot: new bitmap output file error: " + e);
            return;
        }
        if (outputFile != null) {
            yuvImage.compressToJpeg(new Rect(0,
                    0,
                    DJIVideoStreamDecoder.getInstance().width,
                    DJIVideoStreamDecoder.getInstance().height), 90, outputFile);
        }
        try {
            outputFile.close();
        } catch (IOException e) {
            Log.e(TAG, "test screenShot: compress yuv image error: " + e);
            e.printStackTrace();
            return;
        }

        TelemetryService.LogDebug("PSH:Phone.screenshot="+remotePath);
    }



    private byte[] getDefaultKeyFrame(int width) throws IOException {
        //int iframeId=getIframeRawId(product.getModel(), width);
        int iframeId = R.raw.iframe_1280x720_ins;
        if (iframeId >= 0){

            InputStream inputStream = getContext().getResources().openRawResource(iframeId);
            int length = inputStream.available();
            //logd("iframeId length=" + length);
            byte[] buffer = new byte[length];
            inputStream.read(buffer);
            inputStream.close();

            return buffer;
        }
        return null;
    }
    private static String videoCaptureFilename=null;
    public void createH264(String fileName) {
        byte[] iframe= new byte[0];
        try {
            iframe = getDefaultKeyFrame(0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //udpClient.send(ByteBuffer.wrap(iframe,0,iframe.length));

        File file = new File(fileName);
        file.delete();
        //save name for append.
        videoCaptureFilename=fileName;
        appendH264(iframe);
    }
    public static void appendH264(byte[] bbuf)
    {
        if(videoCaptureFilename==null)
            return;
        File file = new File(videoCaptureFilename);
        boolean append = true;
        try {
            FileChannel wChannel = new FileOutputStream(file, append).getChannel();
            wChannel.write(ByteBuffer.wrap(bbuf));
            wChannel.close();
        } catch (IOException e) {
        }
    }
    private void startVideoCapture()
    {
        String logDir = Environment.getExternalStorageDirectory() + "/DJI_Telemetry/videos/";
        new File(logDir).mkdirs();

        //set output log file name.
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        videoCaptureFilename = logDir + "" + timeStamp + ".h264";

        TelemetryService.Log("startVideoCapture "+videoCaptureFilename);
        createH264(videoCaptureFilename);

    }
}
