package it.moondroid.driveapi;

import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.OpenFileActivityBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class ReadFileActivity extends BaseDriveActivity {
    private static final String TAG = "ReadFileActivity";

    /**
     * Request code for the opener activity.
     */
    private static final int REQUEST_CODE_OPENER = NEXT_AVAILABLE_REQUEST_CODE + 1;

    /**
     * Text file mimetype.
     */
    private static final String MIME_TYPE_TEXT = "text/plain";
    /**
     * Text file mimetype.
     */
    private static final String MIME_TYPE_ZIP = "application/zip";

    /**
     * Drive ID of the currently opened Drive file.
     */
    private DriveId mCurrentDriveId;

    /**
     * MIME Type of the currently opened Drive file.
     */
    private String mCurrentMimeType;

    /**
     * Currently opened file's metadata.
     */
    private Metadata mMetadata;
    /**
     * Currently opened file's contents.
     */
    private DriveContents mDriveContents;

    private ScrollView mScrollView;
    private TextView mTextView;
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_read_file);

        mScrollView = (ScrollView)findViewById(R.id.scrollView);
        mTextView = (TextView)findViewById(R.id.textView);
        mProgressBar = (ProgressBar)findViewById(R.id.progress);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_read_file, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            IntentSender i = Drive.DriveApi
                    .newOpenFileActivityBuilder()
                    .setMimeType(new String[] { MIME_TYPE_TEXT, MIME_TYPE_ZIP })
                    .build(getGoogleApiClient());
            try {
                startIntentSenderForResult(i, REQUEST_CODE_OPENER, null, 0, 0, 0);
            } catch (IntentSender.SendIntentException e) {
                Log.w(TAG, "Unable to send intent", e);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        switch (requestCode) {

            case REQUEST_CODE_OPENER:
                if (resultCode == RESULT_OK) {
                    mCurrentDriveId = (DriveId) data.getParcelableExtra(
                            OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
                    Log.d(TAG, "driveId "+mCurrentDriveId);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        super.onConnected(connectionHint);
        if(mCurrentDriveId != null){
            showProgress(true);
            Drive.DriveApi.getFile(getGoogleApiClient(), mCurrentDriveId)
                    .getMetadata(getGoogleApiClient())
                    .setResultCallback(metadataCallback);
        }

    }

    final private ResultCallback<DriveResource.MetadataResult> metadataCallback =
            new ResultCallback<DriveResource.MetadataResult>() {
                @Override
                public void onResult(DriveResource.MetadataResult result) {
                    showProgress(false);
                    if (!result.getStatus().isSuccess()) {
                        // Handle error
                        return;
                    }
                    mCurrentMimeType = null;
                    if(result.getMetadata().getMimeType().equalsIgnoreCase(MIME_TYPE_TEXT)){
                        mCurrentMimeType = MIME_TYPE_TEXT;
                        showMessage("text file");
                    }
                    if(result.getMetadata().getMimeType().equalsIgnoreCase(MIME_TYPE_ZIP)){
                        mCurrentMimeType = MIME_TYPE_ZIP;
                        showMessage("zip file");
                    }
                    Drive.DriveApi.getFile(getGoogleApiClient(), mCurrentDriveId)
                    .open(getGoogleApiClient(), DriveFile.MODE_READ_ONLY, new DriveFile.DownloadProgressListener() {
                        @Override
                        public void onProgress(long bytesDownloaded, long bytesExpected) {
                            // display the progress

                        }
                    })
                    .setResultCallback(contentsCallback);
                }
            };

    final private ResultCallback<DriveApi.DriveContentsResult> contentsCallback =
            new ResultCallback<DriveApi.DriveContentsResult>() {
        @Override
        public void onResult(DriveApi.DriveContentsResult result) {
            if (!result.getStatus().isSuccess()) {
                // Handle error
                return;
            }
            DriveContents contents = result.getDriveContents();

            if (mCurrentMimeType.equalsIgnoreCase(MIME_TYPE_TEXT)){
                readTextFile(contents);
            }
            if (mCurrentMimeType.equalsIgnoreCase(MIME_TYPE_ZIP)){
                readZipFile(contents);
            }

        }
    };

    private void readTextFile(DriveContents contents){
        BufferedReader reader = new BufferedReader(new InputStreamReader(contents.getInputStream()));
        StringBuilder builder = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } catch (IOException e) {
            showMessage("IOException while reading from the stream");
        }

        showProgress(false);
        mTextView.setText(builder.toString());
    }

    private void readZipFile(DriveContents contents){
        String path = Environment.getExternalStorageDirectory().getPath()+File.separator;
        String logText = "";
        int numFiles = 0;

        InputStream is;
        ZipInputStream zis;
        try
        {
            String filename;
            is = contents.getInputStream();
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            byte[] buffer = new byte[1024];
            int count;

            while ((ze = zis.getNextEntry()) != null) {
                filename = ze.getName();

                // Need to create directories if not exists, or
                // it will generate an Exception...
                if (ze.isDirectory()) {
                    File fmd = new File(path + filename);
                    fmd.mkdirs();
                    logText += "created dir: "+filename+"\n";
                    continue;
                }

                FileOutputStream fout = new FileOutputStream(path + filename);

                while ((count = zis.read(buffer)) != -1)
                {
                    fout.write(buffer, 0, count);
                }

                fout.close();
                logText += "extracted: "+filename+"\n";
                numFiles++;
                zis.closeEntry();
            }

            zis.close();
            logText += "unzipped "+numFiles+" file/s in "+path;
        } catch(IOException e) {
            showMessage("error unzipping file");
            logText = "error unzipping file";
            e.printStackTrace();
        }

        showProgress(false);
        mTextView.setText(logText);
    }

    private void showProgress(boolean show){
        if (show){
            mScrollView.setVisibility(View.GONE);
            mProgressBar.setVisibility(View.VISIBLE);
        }else{
            mProgressBar.setVisibility(View.GONE);
            mScrollView.setVisibility(View.VISIBLE);
        }
    }

}
