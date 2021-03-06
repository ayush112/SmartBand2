package in.iitd.assistech.smartband;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.renderscript.Sampler;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.sounds.ClassifySound;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;

import static com.sounds.ClassifySound.PROCESS_LENGTH;
import static com.sounds.ClassifySound.numOutput;
import static in.iitd.assistech.smartband.Tab3.notificationListItems;
import static in.iitd.assistech.smartband.Tab3.soundListItems;

public class MainActivity extends AppCompatActivity implements TabLayout.OnTabSelectedListener,
        OnTabEvent{

    private static final String TAG = "MainActivity";
    public static String NAME;
    public static String EMAIL;
    public static String PHOTO;
    public static String PHOTOURI;
    public static String SIGNED;

    private TabLayout tabLayout;
    private ViewPager viewPager;
    static Pager adapter;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /***Variables for audiorecord*/
    private MediaRecorder mRecorder = null;
    private static int REQUEST_MICROPHONE = 101;
    public static final int RECORD_TIME_DURATION = 1000; //0.5 seconds
    private static final int RECORDER_SAMPLERATE = ClassifySound.RECORDER_SAMPLERATE;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    static int BufferElements2Rec = RECORDER_SAMPLERATE * RECORD_TIME_DURATION/1000; // number of 16 bits for 3 seconds
    //    BufferElements2Rec =
    static String[] warnMsgs = {"Car Horn Detected", "Dog Bark Detected", "Ambient"};
    static int[] warnImgs = new int[] {R.drawable.car_horn, R.drawable.dog_bark, 0};

    public AlertDialog myDialog;
    private static final int RECORDER_BPP = 16;
    private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
    private static final String AUDIO_RECORDER_FOLDER = "SmartBand";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";
    short[] audioData;

    private AudioRecord recorder = null;
    private int bufferSize = 0;
    private Thread recordingThread = null;
    private boolean isRecording = false;
//    Complex[] fftTempArray;
//    Complex[] fftArray;
    int[] bufferData;
    int bytesRecorded;
    int BytesPerElement;
    public short[] sData = new short[BufferElements2Rec];

    private static boolean[] startNotifListState;
    private static boolean[] startSoundListState;
    /*
    public AlertDialog.Builder warnDB;
    public AlertDialog warnDialog;
    */



    static Handler uiHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            double[] prob_sound = (double[]) msg.obj;
            boolean[] notifState = adapter.getInitialNotifListState();
            adapter.editTab2Text(prob_sound, notifState);
        }
    };

    void setProbOut(double[] outProb){
        //send msg to edit value of prob on screen
        //Launch dialog interface;
        Message message = uiHandler.obtainMessage();
        message.obj = outProb;
        message.sendToTarget();
//        for(int i=0; i<(outProb.length-2); i++){
//            if(outProb[i]> 0.60){
//                showDialog(MainActivity.this, i, outProb);
////                Message message = uiHandler.obtainMessage();
////                message.obj = outProb;
////                message.sendToTarget();
//            }
//        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        SharedPreferences app_preferences = PreferenceManager.getDefaultSharedPreferences(this);
//        SharedPreferences.Editor editor = app_preferences.edit();
//        boolean[] notifState = getFinalNotifState();
//        editor.putBoolean("Vibration", notifState[0]);
//        editor.putBoolean("Sound", notifState[1]);
//        editor.putBoolean("FlashLight", notifState[2]);
//        editor.putBoolean("FlashScreen", notifState[3]);
//        editor.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences app_preferences = PreferenceManager.getDefaultSharedPreferences(this);

        boolean[] notifState = new boolean[notificationListItems.length];
        for (int i=0; i<notificationListItems.length; i++){
            notifState[i] = app_preferences.getBoolean(notificationListItems[i], true);
        }
        boolean[] soundState =  new boolean[soundListItems.length];
        for (int i=0; i<soundListItems.length; i++){
            soundState[i] = app_preferences.getBoolean(soundListItems[i], true);
        }

        startNotifListState = notifState;
        startSoundListState = soundState;
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences app_preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = app_preferences.edit();
        if(adapter.getInitialNotifListState() != null){
            boolean[] notifState = adapter.getInitialNotifListState();
            for (int i=0; i<notificationListItems.length; i++){
                editor.putBoolean(notificationListItems[i], notifState[i]);
            }
            editor.commit();
        }

        if(adapter.getInitialSoundListState() != null){
            boolean[] soundState = adapter.getInitialSoundListState();
            for (int i=0; i<soundListItems.length; i++){
                editor.putBoolean(soundListItems[i], soundState[i]);
            }
            editor.commit();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        NAME = getIntent().getStringExtra("NAME");
        EMAIL = getIntent().getStringExtra("EMAIL");
        PHOTO = getIntent().getStringExtra("PHOTO");
        PHOTOURI = getIntent().getStringExtra("PHOTOURI");
        SIGNED = getIntent().getStringExtra("SIGNED");

        //TODO: Hardware Acceleration
//        getWindow().setFlags(
//                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
//                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);


        //Adding toolbar to the activity
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
//        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        //Initializing the tablayout
        tabLayout = (TabLayout) findViewById(R.id.tabLayout);

        //Initializing viewPager
        viewPager = (ViewPager) findViewById(R.id.pager);
        tabLayout.setupWithViewPager(viewPager);

        //Adding the tabs using addTab() method
        tabLayout.addTab(tabLayout.newTab());        tabLayout.addTab(tabLayout.newTab());
        tabLayout.addTab(tabLayout.newTab());
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        //Creating our pager adapter
        adapter = new Pager(getSupportFragmentManager(), tabLayout.getTabCount());
        //Adding adapter to pager
        viewPager.setAdapter(adapter);

        tabLayout.getTabAt(0).setText("Chat");
        tabLayout.getTabAt(1).setText("Sound");
        tabLayout.getTabAt(2).setText("Settings");

        //Adding onTabSelectedListener to swipe views
//        tabLayout.setOnTabSelectedListener(this);
        tabLayout.addOnTabSelectedListener(this);

//        for(int i=0; i<sData.length; i++){
//            sData[i] = (short)0;
//        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);

        }

        bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);

        viewPager.setCurrentItem(1);

        int permission = ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    MainActivity.this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        bufferSize = AudioRecord.getMinBufferSize
                (RECORDER_SAMPLERATE,RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING)*3;

        audioData = new short [bufferSize]; //short array that pcm data is put into.
    }

    @Override
    public void onButtonClick(String text) {
        if(text == "MicReadButton"){
            startRecording(1);
//            try{
//
//                processMicSound();
//            }catch (InterruptedException e){
//                Log.e(TAG, "onButtonClick() " + e.toString());
//            }
        }else if(text == "StopRecordButton"){
            stopRecording();
        } else if(text == "SoundRecord"){
            Log.e(TAG, "Record}");
            startRecording(2);
        } else if(text == "StopRecord"){
            stopRecording();
        }
    }

    /**--------------Sign In-------------------**/

    /**----------------------------------------**/
    /**----------For Sound Processing and stuff----------**/
    //Load the weights and bias in the form of matrix from sheet in Assets Folder

/*
    public void processMicSound() throws InterruptedException{
//        for(int i=0; i<sData.length; i++){
//            sData[i] = (short)0;
//        }
//        for(int i=0; i<history_prob.length; i++){
//            for(int j=0; j<history_prob[0].length; j++){
//                history_prob[i][j] = 0.0;
//            }
//        }
        //@link https://stackoverflow.com/questions/40459490/processing-in-audiorecord-thread
        HandlerThread myHandlerThread = new HandlerThread("my-handler-thread");
        myHandlerThread.start();

        //make below gloabl and remove final
        final Handler myHandler = new Handler(myHandlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
//                someHeavyProcessing((short[]) msg.obj, msg.arg1);
                try{
                    Log.e(TAG, "handleMessage");
                    long start_time = System.nanoTime();

                    //Calculate probability using ClassifySound.
                    short[] temp_sound = (short[]) msg.obj;
                    short[] temp1 = Arrays.copyOfRange(temp_sound, 0, PROCESS_LENGTH);
                    double[] temp_prob = ClassifySound.getProb(temp1);
                    Log.e(TAG + " temp_prob", Double.toString(temp_prob[0]) + ", " + Double.toString(temp_prob[1]));
//                            + ", " + Double.toString(temp_prob[2])+ ", " + Double.toString(temp_prob[3]));

                    setProbOut(temp_prob);
                }catch (Exception e){
                    System.out.print(TAG + " myHandler : " + e.toString());
                }
                return true;
            }
        });

        if(!isRecording){
            int BytesPerElement = 2; // 2 bytes in 16bit format
            if (bufferSize != AudioRecord.ERROR_BAD_VALUE && bufferSize > 0){
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                        RECORDER_AUDIO_ENCODING,BufferElements2Rec*BytesPerElement);//
                recorder.startRecording();
            }

//            isRecording = true;
            recordingThread = new Thread(new Runnable() {
                public void run() {
                    while (isRecording) {
                        synchronized (this){
                            int num_read = recorder.read(sData, 0, BufferElements2Rec);
                            Message message = myHandler.obtainMessage();
                            message.obj = sData;
                            message.arg1 = num_read;
                            message.sendToTarget();
                        }
                    }
                }
            }, "AudioRecorder Thread");
            recordingThread.start();

        } else{
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
        }
    }
    */

    private void startRecording(int indicator) {
        int bufferSizeinBytes = 0;
        int BytesPerElement = 2; // 2 bytes in 16bit format
        if(indicator==1)  bufferSizeinBytes = BufferElements2Rec*BytesPerElement;
        if(indicator==2) bufferSizeinBytes = bufferSize;
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                bufferSizeinBytes);//TODO Uncomment this for processing
        int i = recorder.getState();
        if (i==1)
            recorder.startRecording();

        isRecording = true;
        if (indicator==1){
            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    //TODO Uncomment recorder bufferSize above for processing
                    readAudioAndProcess();
//                    writeAudioDataToFile();
                }
            }, "AudioRecorder Thread");

            recordingThread.start();
        }
        if (indicator==2){
            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.e(TAG, "Recording started");
                    //TODO Uncomment recorder bufferSize above for processing
                    writeAudioDataToFile();
                }
            }, "AudioRecorder Thread");

            recordingThread.start();
        }
    }

    private void readAudioAndProcess(){
        short[] data = new short[BufferElements2Rec];

        //@link https://stackoverflow.com/questions/40459490/processing-in-audiorecord-thread
        HandlerThread myHandlerThread = new HandlerThread("my-handler-thread");
        myHandlerThread.start();

        //make below gloabl and remove final
        final Handler myHandler = new Handler(myHandlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
//                someHeavyProcessing((short[]) msg.obj, msg.arg1);
                try{
                    Log.e(TAG, "handleMessage");
                    long start_time = System.nanoTime();
                    short[] temp_sound = (short[]) msg.obj;
//                    short[] temp1 = Arrays.copyOfRange(temp_sound, 0, PROCESS_LENGTH);
//                    writeToExcel("sound_file.csv", temp_sound);
                    double threshold = 40;
                    double decibel = getDecibel(temp_sound);
                    Log.e(TAG, "Decibel : " + decibel);
                    double[] temp_prob;
                    if(decibel > threshold){
                        temp_prob = new double[numOutput];
                        for(int i=0; i<temp_prob.length; i++){
                            temp_prob[i] = Double.NaN;
                        }
                    } else{
                        temp_prob = ClassifySound.getProb(temp_sound);
                    }
//                    Log.e(TAG, Integer.toString(temp_prob.length) );
                    setProbOut(temp_prob);
                }catch (Exception e){
                    System.out.print(TAG + " myHandler : " + e.toString());
                }
                return true;
            }
        });

        int read = 0;
        while(isRecording) {
            read = recorder.read(data, 0, BufferElements2Rec);
            if (read > 0) {
            }
            if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                Message message = myHandler.obtainMessage();
                message.obj = data;
                message.arg1 = read;
                message.sendToTarget();
            }
        }
    }

    private void writeAudioDataToFile() {
        byte data[] = new byte[bufferSize];
        String filename = getTempFilename();
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        int read = 0;
        if (null != os) {
            while(isRecording) {
                read = recorder.read(data, 0, bufferSize);
                if (read > 0){
                }

                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    try {
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopRecording() {
        if (null != recorder){
            isRecording = false;

            int i = recorder.getState();
            if (i==1)
                recorder.stop();
            recorder.release();

            recorder = null;
            recordingThread = null;
        }

        copyWaveFile(getTempFilename(),getFilename());
        deleteTempFile();
    }

    private void deleteTempFile() {
        File file = new File(getTempFilename());
        file.delete();
    }

    private String getFilename(){
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if (!file.exists()) {
            file.mkdirs();
        }

        return (file.getAbsolutePath() + "/" + System.currentTimeMillis() +
                AUDIO_RECORDER_FILE_EXT_WAV);
    }

    private String getTempFilename() {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if (!file.exists()) {
            file.mkdirs();
        }

        File tempFile = new File(filepath,AUDIO_RECORDER_TEMP_FILE);

        if (tempFile.exists())
            tempFile.delete();

        return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
    }

    private void copyWaveFile(String inFilename,String outFilename){
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 1;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels/8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

//            AppLog.logString("File size: " + totalDataLen);

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while(in.read(data) != -1) {
                out.write(data);
            }

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException
    {
        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }

    /**---------------------------------------**/

    public void showDialog(Context context, int idx, double[] outProb) {
        if (myDialog != null && myDialog.isShowing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(warnMsgs[idx]);
        ImageView wrnImg = new ImageView(MainActivity.this);
        //TODO No Text clickable button for dog bark case dialog
//        ViewGroup.LayoutParams imgParams = wrnImg.getLayoutParams();
//        imgParams.width = 60;
//        imgParams.height = 60;
//        wrnImg.setLayoutParams(imgParams);
        wrnImg.setImageResource(warnImgs[idx]);
        builder.setView(wrnImg);
//        builder.setMessage("Message");
//        builder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
//            public void onClick(DialogInterface dialog, int arg1) {
//                dialog.dismiss();
//            }});
        builder.setPositiveButton("Ok, Thank You!", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int arg1) {
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("Wrong Detection", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int arg1) {
                dialog.dismiss();
            }
        });

        builder.setCancelable(true);
        myDialog = builder.create();
        myDialog.show();
        final Timer timer2 = new Timer();
        timer2.schedule(new TimerTask() {
            public void run() {
                myDialog.dismiss();
                Log.e(TAG, "TIMER Running");
                timer2.cancel(); //this will cancel the timer of the system
            }
        }, 15000); // the timer will count 5 seconds....
    }

    public double getDecibel(short[] sound){
        double decibel;
        long sum = 0;
        double avg;
        for(int i=0; i<sound.length; i++){
//            Log.e(TAG, Integer.toString(Math.abs(sound[i])));
            sum += Math.abs(sound[i]);
//            Log.e(TAG, Long.toString(sum));

        }
        avg= sum*1.0/sound.length;
        Log.e(TAG, "Avg. : " + avg);
        Log.e(TAG, "Sum. : " + sum);
        if(avg == 0) decibel=Double.NEGATIVE_INFINITY;
        else decibel = 20.0*Math.log10(32769.0/avg);
        return decibel;
    }

    /**-----------For Tab Layout--------------**/
    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        viewPager.setCurrentItem(tab.getPosition());
        tabLayout.getTabAt(tab.getPosition()).select();
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
    }

    static boolean[] getStartNotifListState(){
        return startNotifListState;
    }

    static boolean[] getStartSoundListState(){
        return startSoundListState;
    }
}