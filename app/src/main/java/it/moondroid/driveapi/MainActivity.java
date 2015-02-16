package it.moondroid.driveapi;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;


public class MainActivity extends BaseDriveActivity {

    private static final String TITLE = "appconfig.txt";
    private Button mSendButton;
    private EditText mEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSendButton = (Button)findViewById(R.id.buttonSend);
        mEditText = (EditText)findViewById(R.id.editText);

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Drive.DriveApi.newDriveContents(getGoogleApiClient())
                        .setResultCallback(driveContentsCallback);

            }
        });
    }


    @Override
    public void onConnected(Bundle connectionHint) {
        super.onConnected(connectionHint);
        mSendButton.setEnabled(true);

        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.MIME_TYPE, "text/plain"))
                .addFilter(Filters.eq(SearchableField.TITLE, TITLE))
                .build();
        Drive.DriveApi.getAppFolder(getGoogleApiClient())
                .queryChildren(getGoogleApiClient(), query)
                .setResultCallback(driveReadContentsCallback);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        super.onConnectionSuspended(cause);
        mSendButton.setEnabled(false);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        super.onConnectionFailed(result);
        mSendButton.setEnabled(false);
    }

    final private ResultCallback<DriveApi.DriveContentsResult> driveContentsCallback =
            new ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(DriveApi.DriveContentsResult result) {
                    if (!result.getStatus().isSuccess()) {
                        showMessage("Error while trying to create new file contents");
                        return;
                    }

                    final DriveContents driveContents = result.getDriveContents();

                    // Perform I/O off the UI thread.
                    new Thread() {
                        @Override
                        public void run() {
                            // write content to DriveContents
                            OutputStream outputStream = driveContents.getOutputStream();
                            Writer writer = new OutputStreamWriter(outputStream);
                            try {
                                writer.write(mEditText.getText().toString());
                                writer.close();
                            } catch (IOException e) {
                                showMessage(e.getMessage());
                            }

                            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                    .setTitle(TITLE)
                                    .setMimeType("text/plain")
                                    .build();

                            Drive.DriveApi.getAppFolder(getGoogleApiClient())
                                    .createFile(getGoogleApiClient(), changeSet, driveContents)
                                    .setResultCallback(fileCallback);
                        }
                    }.start();



                }
            };

    final private ResultCallback<DriveApi.MetadataBufferResult> driveReadContentsCallback =
            new ResultCallback<DriveApi.MetadataBufferResult>() {
                @Override
                public void onResult(DriveApi.MetadataBufferResult result) {
                    if (!result.getStatus().isSuccess()) {
                        showMessage("Error while trying to read file contents");
                        return;
                    }

                    for (final Metadata metadata : result.getMetadataBuffer()){
                        if (metadata.isInAppFolder()){
                            readFileContent(metadata, new FileReadCallback() {
                                @Override
                                public void onFileRead(String result) {
                                    Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT)
                                            .show();
                                }
                            });
                        }
                    }
                }
            };

    final private ResultCallback<DriveFolder.DriveFileResult> fileCallback = new
            ResultCallback<DriveFolder.DriveFileResult>() {
                @Override
                public void onResult(DriveFolder.DriveFileResult result) {
                    if (!result.getStatus().isSuccess()) {
                        showMessage("Error while trying to create the file");
                        return;
                    }
                    showMessage("Created a file in App Folder: "
                            + result.getDriveFile().getDriveId());
                }
            };

    private interface FileReadCallback {
        void onFileRead(String result);
    }
    private void readFileContent(final Metadata metadata, final FileReadCallback fileReadCallback){
        new Thread() {
            @Override
            public void run() {
                String contents = "";
                DriveFile file = Drive.DriveApi.getFile(getGoogleApiClient(), metadata.getDriveId());
                DriveApi.DriveContentsResult driveContentsResult =
                        file.open(getGoogleApiClient(), DriveFile.MODE_READ_ONLY, null).await();
                if (!driveContentsResult.getStatus().isSuccess()) {
                    showMessage("Error while trying to read file contents");
                    return;
                }
                DriveContents driveContents = driveContentsResult.getDriveContents();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(driveContents.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    contents = builder.toString();
                } catch (IOException e) {
                    showMessage("IOException while reading from the stream");
                }

                driveContents.discard(getGoogleApiClient());

                final String result = contents;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        fileReadCallback.onFileRead(result);
                    }
                });

            }
        }.start();
    }
}
