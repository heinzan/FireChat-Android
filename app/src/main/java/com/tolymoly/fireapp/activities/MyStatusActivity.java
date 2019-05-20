package com.tolymoly.fireapp.activities;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.cjt2325.cameralibrary.ResultCodes;
import com.tolymoly.fireapp.R;
import com.tolymoly.fireapp.adapters.MyStatusAdapter;
import com.tolymoly.fireapp.model.constants.MessageType;
import com.tolymoly.fireapp.model.realms.Status;
import com.tolymoly.fireapp.model.realms.UserStatuses;
import com.tolymoly.fireapp.utils.BitmapUtils;
import com.tolymoly.fireapp.utils.DirManager;
import com.tolymoly.fireapp.utils.FileUtils;
import com.tolymoly.fireapp.utils.FireConstants;
import com.tolymoly.fireapp.utils.FireManager;
import com.tolymoly.fireapp.utils.IntentUtils;
import com.tolymoly.fireapp.utils.MyApp;
import com.tolymoly.fireapp.utils.NetworkHelper;
import com.tolymoly.fireapp.utils.RealmHelper;
import com.tolymoly.fireapp.utils.StatusManager;
import com.tolymoly.fireapp.utils.Util;
import com.devlomi.hidely.hidelyviews.HidelyImageView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.zhihu.matisse.Matisse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.realm.RealmResults;

public class MyStatusActivity extends AppCompatActivity implements ActionMode.Callback {
    private static final int CAMERA_REQUEST = 9321;
    //max duration for status video time (30sec)
    public static final int MAX_STATUS_VIDEO_TIME = 30;
    private RecyclerView rvMyStatus;
    private RealmResults<Status> myStatusList;
    private MyStatusAdapter adapter;
    ActionMode actionMode;
    List<Status> selectedStatusForActionMode = new ArrayList<>();
    UserStatuses userStatuses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_status);

        rvMyStatus = findViewById(R.id.rv_my_status);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MyStatusActivity.this, CameraActivity.class);
                intent.putExtra(IntentUtils.CAMERA_VIEW_SHOW_PICK_IMAGE_BUTTON, true);
                intent.putExtra(IntentUtils.IS_STATUS, true);
                startActivityForResult(intent, CAMERA_REQUEST);
            }
        });
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getMyStatusList();

        adapter = new MyStatusAdapter(myStatusList, selectedStatusForActionMode, this);
        rvMyStatus.setLayoutManager(new LinearLayoutManager(this));
        rvMyStatus.setAdapter(adapter);


        adapter.setOnStatusClick(new MyStatusAdapter.OnClickListener() {
            @Override
            public void onStatusClick(View view, HidelyImageView selectedCircle, Status status) {
                //if there user is not in selection mode (action mode) start View the status,otherwise add the status to selection
                if (actionMode == null) {
                    Intent intent = new Intent(MyStatusActivity.this, ViewStatusActivity.class);
                    intent.putExtra(IntentUtils.UID, FireManager.getUid());
                    startActivity(intent);
                } else {
                    if (!selectedStatusForActionMode.contains(status)) {

                        itemAddedToActionList(selectedCircle, view, status);
                    } else {
                        itemRemovedFromActionList(selectedCircle, view, status);

                    }

                }
            }

            @Override
            public void onStatusLongClick(View view, HidelyImageView circleImgSelected, Status status) {
                if (actionMode == null) {
                    startActionMode(MyStatusActivity.this);
                    itemAddedToActionList(circleImgSelected, view, status);
                }
            }
        });

        fetchSeenCount();

    }

    private void getMyStatusList() {
        userStatuses = RealmHelper.getInstance().getUserStatuses(FireManager.getUid());
        myStatusList = userStatuses.getMyStatuses();
    }

    private void itemRemovedFromActionList(HidelyImageView selectedCircle, View itemView, Status status) {

        selectedStatusForActionMode.remove(status);
        if (selectedStatusForActionMode.isEmpty()) {
            actionMode.finish();
        } else {
            selectedCircle.hide();
            itemView.setBackgroundColor(-1);
            actionMode.setTitle(selectedStatusForActionMode.size() + "");
        }

    }

    private void itemAddedToActionList(HidelyImageView selectedCircle, View itemView, Status status) {
        selectedCircle.show();
        itemView.setBackgroundColor(getResources().getColor(R.color.light_blue));
        selectedStatusForActionMode.add(status);
        actionMode.setTitle(selectedStatusForActionMode.size() + "");
    }


    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        this.actionMode = actionMode;
        actionMode.getMenuInflater().inflate(R.menu.menu_action_my_statuses, menu);
        actionMode.setTitle("1");
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(final ActionMode actionMode, MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.menu_item_delete) {
            if (!NetworkHelper.isConnected(this)) {
                Toast.makeText(this, R.string.no_internet_connection, Toast.LENGTH_SHORT).show();
                return false;
            }

            AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
            alertDialog.setTitle(R.string.confirmation);
            alertDialog.setMessage(R.string.delete_status_confirmation);
            alertDialog.setNegativeButton(R.string.no, null);
            alertDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {

                    final ProgressDialog progressDialog = new ProgressDialog(MyStatusActivity.this);
                    progressDialog.setCancelable(false);
                    progressDialog.setMessage(getString(R.string.deleting));
                    progressDialog.show();
                    final String lastStatusId = selectedStatusForActionMode.get(selectedStatusForActionMode.size() - 1).getStatusId();
                    for (final Status status : selectedStatusForActionMode) {
                        StatusManager.deleteStatus(status.getStatusId(), new StatusManager.DeleteStatus() {
                            @Override
                            public void onComplete(boolean isSuccessful, String id) {
                                if (isSuccessful && id.equals(lastStatusId)) {
                                    adapter.notifyDataSetChanged();
                                    progressDialog.dismiss();
                                }
                            }
                        });
                    }
                    actionMode.finish();
                }
            });
            alertDialog.show();


        }

        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        this.actionMode = null;
        selectedStatusForActionMode.clear();
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        if (actionMode == null) {
            super.onBackPressed();
        } else {
            actionMode.finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST && resultCode != ResultCodes.CAMERA_ERROR_STATE) {

            if (resultCode == ResultCodes.IMAGE_CAPTURE_SUCCESS) {
                String path = data.getStringExtra(IntentUtils.EXTRA_PATH_RESULT);
                uploadImageStatus(path);
            } else if (resultCode == ResultCodes.VIDEO_RECORD_SUCCESS) {
                String path = data.getStringExtra(IntentUtils.EXTRA_PATH_RESULT);
                uploadVideoStatus(path);
            } else if (resultCode == ResultCodes.PICK_IMAGE_FROM_CAMERA) {
                List<String> mPaths = Matisse.obtainPathResult(data);
                for (String mPath : mPaths) {
                    if (!FileUtils.isFileExists(mPath)) {
                        Toast.makeText(this, R.string.image_video_not_found, Toast.LENGTH_SHORT).show();
                        return;
                    }

                }


                //Check if it's a video
                if (FileUtils.isPickedVideo(mPaths.get(0))) {

                    //check if video is longer than 30sec
                    long mediaLengthInMillis = Util.getMediaLengthInMillis(this, mPaths.get(0));
                    long seconds = TimeUnit.MILLISECONDS.toSeconds(mediaLengthInMillis);
                    if (seconds <= MAX_STATUS_VIDEO_TIME) {
                        for (String mPath : mPaths) {
                            uploadVideoStatus(mPath);
                        }
                    } else {
                        Toast.makeText(this, R.string.video_length_is_too_long, Toast.LENGTH_SHORT).show();
                    }


                } else {
                    for (String path : mPaths) {
                        uploadImageStatus(path);
                    }
                }
            }
        }
    }


    //get how many users have seen this status
    private void fetchSeenCount() {
        if (!NetworkHelper.isConnected(this)) return;
        for (final Status status : myStatusList) {
            FireConstants.statusCountRef.child(FireManager.getUid()).child(status.getStatusId()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() != null) {
                        int count = dataSnapshot.getValue(Integer.class);
                        RealmHelper.getInstance().setStatusCount(status.getStatusId(), count);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });
        }


    }

    private void uploadVideoStatus(String path) {
        if (!NetworkHelper.isConnected(MyApp.context())) {
            Toast.makeText(this, R.string.no_internet_connection, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, R.string.uploading_status, Toast.LENGTH_SHORT).show();

        StatusManager.uploadStatus(path, true, new StatusManager.UploadStatusCallback() {
            @Override
            public void onComplete(boolean isSuccessful) {
                if (isSuccessful) {
                    Toast.makeText(MyStatusActivity.this, R.string.status_uploaded, Toast.LENGTH_SHORT).show();
                } else
                    Toast.makeText(MyStatusActivity.this, R.string.error_uploading_status, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void uploadImageStatus(String path) {
        if (!NetworkHelper.isConnected(MyApp.context())) {
            Toast.makeText(this, R.string.no_internet_connection, Toast.LENGTH_SHORT).show();
            return;
        }


        Toast.makeText(this, R.string.uploading_status, Toast.LENGTH_SHORT).show();
        String mPath = compressImage(path);


        StatusManager.uploadStatus(mPath, false, new StatusManager.UploadStatusCallback() {
            @Override
            public void onComplete(boolean isSuccessful) {
                if (isSuccessful) {
                    Toast.makeText(MyStatusActivity.this, R.string.status_uploaded, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MyStatusActivity.this, R.string.error_uploading_status, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    //compress image when user chooses an image from gallery
    private String compressImage(String imagePath) {
        //generate file in sent images folder
        File file = DirManager.generateFile(MessageType.SENT_IMAGE);
        //compress image and copy it to the given file
        BitmapUtils.compressImage(imagePath, file);

        return file.getPath();
    }
}
