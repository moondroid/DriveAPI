package it.moondroid.driveapi;

import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
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
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.OpenFileActivityBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


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
     * Drive ID of the currently opened Drive file.
     */
    private DriveId mCurrentDriveId;
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
                    .setMimeType(new String[] { MIME_TYPE_TEXT })
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
                    .open(getGoogleApiClient(), DriveFile.MODE_READ_ONLY, new DriveFile.DownloadProgressListener() {
                        @Override
                        public void onProgress(long bytesDownloaded, long bytesExpected) {
                            // display the progress

                        }
                    })
                    .setResultCallback(idCallback);
        }

    }

    final private ResultCallback<DriveApi.DriveContentsResult> idCallback =
            new ResultCallback<DriveApi.DriveContentsResult>() {
        @Override
        public void onResult(DriveApi.DriveContentsResult result) {
            showProgress(false);
            if (!result.getStatus().isSuccess()) {
                // Handle error
                return;
            }
            DriveContents contents = result.getDriveContents();
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

            mTextView.setText(builder.toString());
        }
    };


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
