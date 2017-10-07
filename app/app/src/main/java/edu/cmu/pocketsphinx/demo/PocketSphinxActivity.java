/* ====================================================================
 * Copyright (c) 2014 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package edu.cmu.pocketsphinx.demo;

import static android.widget.Toast.makeText;
import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.media.MediaPlayer;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.Toast;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;

public class PocketSphinxActivity extends Activity implements
        edu.cmu.pocketsphinx.RecognitionListener,android.speech.RecognitionListener {
		
    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";
    private static final String MENU_SEARCH = "menu";

    /* Keyword we are looking for to activate menu */
    private static final String KEYPHRASE = "ok helmet";

    private edu.cmu.pocketsphinx.SpeechRecognizer recognizer;
    private HashMap<String, Integer> captions;
    public  AudioManager mAudioManager;
    public MediaPlayer mp;
    public MediaPlayer mp2;
    public MediaPlayer mp3;
    public MediaPlayer mploc;
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDPAUSE = "pause";
    public static final String CMDPREVIOUS = "previous";
    public static final String CMDNEXT = "next";
    public static final String SERVICECMD = "com.android.music.musicservicecommand";
    public static final String CMDNAME = "command";
    public static final String CMDSTOP = "stop";
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private TriggerEventListener mTriggerEventListener;
    private android.speech.SpeechRecognizer speech = null;
    private Intent recognizerIntent;
    Boolean navigate=false;
    //AudioManager mAudioManager;
    private int attempts=0;
    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        // Prepare the data for UI
        mp = MediaPlayer.create(this, R.raw.beep);
        mp2 = MediaPlayer.create(this, R.raw.beep2);
        mp3 = MediaPlayer.create(this, R.raw.beep3);
        mploc = MediaPlayer.create(this, R.raw.loc);
        captions = new HashMap<String, Integer>();
        captions.put(KWS_SEARCH, R.string.kws_caption);
        captions.put(MENU_SEARCH, R.string.menu_caption);
        setContentView(R.layout.main);
        ((TextView) findViewById(R.id.caption_text))
                .setText("Preparing the recognizer");


        mAudioManager =
                (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
// Switch to headset

        //mAudioManager.setMicrophoneMute(true);
        mAudioManager.setBluetoothScoOn(true);
        mAudioManager.startBluetoothSco();
        //mAudioManager.setMode(AudioManager.STREAM_MUSIC);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        mTriggerEventListener = new TriggerEventListener() {
            @Override
            public void onTrigger(TriggerEvent event) {
                mp2.start();
                mp2.start();
            }
        };

        mSensorManager.requestTriggerSensor(mTriggerEventListener, mSensor);


 /*       this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);*/
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task

        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(PocketSphinxActivity.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    ((TextView) findViewById(R.id.caption_text))
                            .setText("Failed to init recognizer " + result);
                } else {
                    switchSearch(KWS_SEARCH);
                }
            }
        }.execute();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        recognizer.cancel();
        recognizer.shutdown();
    }
    
    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
    	    return;

        String text = hypothesis.getHypstr();
        long eventtime = SystemClock.uptimeMillis();
        //mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        //makeText(getApplicationContext(), text + " - OnPartialResult", Toast.LENGTH_SHORT).show();
        if (text.equals(KEYPHRASE)) {
            switchSearch(MENU_SEARCH);
            //mAudioManager.stopBluetoothSco();

        }
        else if(text.equals("play music"))
        {


            /*
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Uri u = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,"1");
                getApplicationContext().startActivity(i);

            Intent intent = new Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER);
            intent.putExtra(CMDNAME, CMDTOGGLEPAUSE);
            startActivity(intent);
            Intent i = new Intent(SERVICECMD);
            i.putExtra(CMDNAME , CMDTOGGLEPAUSE );
            */
            Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
            KeyEvent downEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0);
            downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
            sendOrderedBroadcast(downIntent, null);

            Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
            KeyEvent upEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0);
            upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
            sendOrderedBroadcast(upIntent, null);
            mAudioManager.setBluetoothScoOn(false);
            mAudioManager.stopBluetoothSco();
            switchSearch(KWS_SEARCH);
        }
        else if(text.equals("stop music"))
        {
            switchSearch(KWS_SEARCH);
            if (mAudioManager.isMusicActive()) {
                Intent i = new Intent("com.android.music.musicservicecommand");
                i.putExtra("command", "pause");
                PocketSphinxActivity.this.sendBroadcast(i);
            }
        }
        else if(text.equals("next song"))
        {
            switchSearch(KWS_SEARCH);
            Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
            KeyEvent downEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN,   KeyEvent.KEYCODE_MEDIA_NEXT, 0);
            downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
            sendOrderedBroadcast(downIntent, null);

        }
        else if(text.equals("previous song"))
        {
            switchSearch(KWS_SEARCH);
            Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
            KeyEvent downEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0);
            downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
            sendOrderedBroadcast(downIntent, null);

        }
        else if(text.equals("navigate"))
        {
            /*
            switchSearch(KWS_SEARCH);
            Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse("google.navigation:q=ITPL+Bangalore"));
            intent.setClassName("com.google.android.apps.maps", "com.google.android.maps.MapsActivity");
            startActivity(intent);
            */
            navigate=true;
            mploc.start();
            recognizer.stop();
            speech = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
            speech.setRecognitionListener(this);

            recognizerIntent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "en");
            recognizerIntent.putExtra(android.speech.RecognizerIntent.EXTRA_CALLING_PACKAGE,
                    this.getPackageName());
            recognizerIntent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
            recognizerIntent.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            speech.startListening(recognizerIntent);

        }
        else {

            ((TextView) findViewById(R.id.result_text)).setText(text);
        }
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        ((TextView) findViewById(R.id.result_text)).setText("");
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            //makeText(getApplicationContext(), text + " - OnResult", Toast.LENGTH_SHORT).show();
        }
        //Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse("google.navigation:q=ITPL+Bangalore"));
        //intent.setClassName("com.google.android.apps.maps", "com.google.android.maps.MapsActivity");
        //startActivity(intent);
/*
        AudioManager mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);

        if (mAudioManager.isMusicActive()) {

            Intent i = new Intent("com.android.music.musicservicecommand");

            i.putExtra("command", "pause");
            PocketSphinxActivity.this.sendBroadcast(i);
        }
*/

    }

    @Override
    public void onBeginningOfSpeech() {
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
        if (!recognizer.getSearchName().equals(KWS_SEARCH))
            if(!navigate) {
                if(attempts>=3) {
                    attempts=0;
                    switchSearch(KWS_SEARCH);
                }
                else
                {
                    mp3.start();
                    switchSearch(KWS_SEARCH);
                    attempts++;
                }
            }
        //makeText(getApplicationContext(), recognizer.getSearchName() + " -EndOfSpeech", Toast.LENGTH_SHORT).show();
    }

    private void switchSearch(String searchName) {
        recognizer.stop();

        //makeText(getApplicationContext(), searchName + " -SwitchSearch", Toast.LENGTH_SHORT).show();
        // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
        if (searchName.equals(KWS_SEARCH)) {
            mp2.start();
            recognizer.startListening(searchName);
        }
        else {
            mp.start();
            recognizer.startListening(searchName, 6000);
            //play some sound that it has listened
        }

        String caption = getResources().getString(captions.get(searchName));
        ((TextView) findViewById(R.id.caption_text)).setText(caption);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them
        
        recognizer = defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                
                // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .setRawLogDir(assetsDir)
                
                // Threshold to tune for keyphrase to balance between false alarms and misses
                .setKeywordThreshold(1e-45f)
                
                // Use context-independent phonetic search, context-dependent is too slow for mobile
                .setBoolean("-allphone_ci", true)
                
                .getRecognizer();
        recognizer.addListener(this);

        /** In your application you might not need to add all those searches.
         * They are added here for demonstration. You can leave just one.
         */

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);
        
        // Create grammar-based search for selection between demos
        File menuGrammar = new File(assetsDir, "menu.gram");
        recognizer.addGrammarSearch(MENU_SEARCH, menuGrammar);
/*
        // Create grammar-based search for digit recognition
        File digitsGrammar = new File(assetsDir, "digits.gram");
        recognizer.addGrammarSearch(DIGITS_SEARCH, digitsGrammar);
        
        // Create language model search
        File languageModel = new File(assetsDir, "weather.dmp");
        recognizer.addNgramSearch(FORECAST_SEARCH, languageModel);
        
        // Phonetic search
        File phoneticModel = new File(assetsDir, "en-phone.dmp");
        recognizer.addAllphoneSearch(PHONE_SEARCH, phoneticModel);
 */
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (speech != null) {
            speech.destroy();
        }
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }


    @Override
    public void onError(int errorCode) {
        speech.destroy();
        navigate=false;
        switchSearch(KWS_SEARCH);
    }

    @Override
    public void onEvent(int arg0, Bundle arg1) {
    }

    @Override
    public void onPartialResults(Bundle arg0) {
    }

    @Override
    public void onReadyForSpeech(Bundle arg0) {
    }

    @Override
    public void onResults(Bundle results) {

        ArrayList<String> matches = results
                .getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
        String text = "";
        for (String result : matches)
            text += result + "\n";

        Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse("google.navigation:q="+matches.get(0)));
        intent.setClassName("com.google.android.apps.maps", "com.google.android.maps.MapsActivity");
        startActivity(intent);
        speech.destroy();
        navigate=false;
        switchSearch(KWS_SEARCH);


    }

    @Override
    public void onRmsChanged(float rmsdB) {
    }

    @Override
    public void onError(Exception error) {
        speech.destroy();
        navigate=false;
        switchSearch(KWS_SEARCH);
        ((TextView) findViewById(R.id.caption_text)).setText(error.getMessage());
    }

    @Override
    public void onTimeout() {
        switchSearch(KWS_SEARCH);
    }
}
