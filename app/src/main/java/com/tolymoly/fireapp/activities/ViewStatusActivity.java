package com.tolymoly.fireapp.activities;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.tolymoly.fireapp.R;
import com.tolymoly.fireapp.job.SetStatusSeenJob;
import com.tolymoly.fireapp.model.constants.StatusType;
import com.tolymoly.fireapp.model.realms.Status;
import com.tolymoly.fireapp.model.realms.User;
import com.tolymoly.fireapp.utils.BitmapUtils;
import com.tolymoly.fireapp.utils.DirManager;
import com.tolymoly.fireapp.utils.FileUtils;
import com.tolymoly.fireapp.utils.FireManager;
import com.tolymoly.fireapp.utils.IntentUtils;
import com.tolymoly.fireapp.utils.RealmHelper;
import com.tolymoly.fireapp.utils.SharedPreferencesManager;
import com.tolymoly.fireapp.utils.StatusManager;
import com.tolymoly.fireapp.utils.TimeHelper;

import java.io.File;

import de.hdodenhof.circleimageview.CircleImageView;
import io.realm.RealmResults;
import jp.shts.android.storiesprogressview.StoriesProgressView;
import me.zhanghai.android.systemuihelper.SystemUiHelper;
import ooo.oxo.library.widget.PullBackLayout;

public class ViewStatusActivity extends AppCompatActivity implements StoriesProgressView.StoriesListener, PullBackLayout.Callback {


    String userId;
    StoriesProgressView storiesProgressView;
    private ImageView image;

    private VideoView videoView;
    private CircleImageView profileImage;
    private TextView tvUsername;
    private TextView tvStatusTime;
    private ImageButton backButton;
    MediaPlayer.OnPreparedListener onPreparedListener;
    MediaPlayer.OnErrorListener onErrorListener;
    private ProgressBar progressBar;
    private StatusManager.OnStatusDownloadComplete onStatusDownloadComplete;

    private int counter = 0;

    //image story duration 7 seconds
    public static final long IMAGE_STORY_DURATION = 7000L;
    long pressTime = 0L;
    long limit = 500L;
    RealmResults<Status> statuses;


    //on touch listener, when user holds his thumb it will pause the story,and when he release it will resume
    private View.OnTouchListener onTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    pause();
                    return false;
                case MotionEvent.ACTION_UP:
                    return resume();
            }

            return false;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_status);


        userId = getIntent().getStringExtra(IntentUtils.UID);
        statuses = RealmHelper.getInstance().getUserStatuses(userId).getFilteredStatuses();
        User user;

        if (userId.equals(FireManager.getUid()))
            user = SharedPreferencesManager.getCurrentUser();
        else
            user = RealmHelper.getInstance().getUser(userId);

        new SystemUiHelper(this, SystemUiHelper.LEVEL_IMMERSIVE, SystemUiHelper.FLAG_IMMERSIVE_STICKY).hide();


        initViews();

        //set stories durations
        storiesProgressView.setStoriesCountWithDurations(getDurations());
        storiesProgressView.setStoriesListener(this);


        //onVideo prepared listener
        onPreparedListener = new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                image.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                storiesProgressView.start(counter);
                videoView.start();
            }
        };

        onErrorListener = new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                Toast.makeText(ViewStatusActivity.this, R.string.error_playing_this, Toast.LENGTH_SHORT).show();
                return true;
            }
        };

        initStatusDownloadCompleteCallback();

        loadStatus(statuses.get(0));
        storiesProgressView.startStories(counter);


        // bind reverse view
        //play the previous story (onClick)
        View reverse = findViewById(R.id.reverse);
        reverse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                storiesProgressView.reverse(counter);
            }
        });

        reverse.setOnTouchListener(onTouchListener);

        // bind skip view
        //play the next story (onClick)
        View skip = findViewById(R.id.skip);
        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                storiesProgressView.skip(counter);
            }
        });
        skip.setOnTouchListener(onTouchListener);


        //back button in toolbar onClick
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });


        PullBackLayout pullBackLayout = findViewById(R.id.pull);
        pullBackLayout.setCallback(this);

        setUserInfo(user);
    }

    @Override
    protected void onDestroy() {
        videoView.stopPlayback();
        videoView.setOnPreparedListener(null);
        videoView.setOnErrorListener(null);
        storiesProgressView.destroy();
        super.onDestroy();
    }

    private boolean resume() {
        long now = System.currentTimeMillis();

        //resume video if needed
        if (statuses.get(counter).getType() == StatusType.VIDEO && !videoView.isPlaying()) {
            videoView.start();
        }
        storiesProgressView.resume();
        return limit < now - pressTime;
    }


    private void pause() {
        pressTime = System.currentTimeMillis();
        storiesProgressView.pause();
        //pause video if needed
        if (statuses.get(counter).getType() == StatusType.VIDEO && videoView.isPlaying()) {
            videoView.pause();
        }
    }


    private void initStatusDownloadCompleteCallback() {
        if (onStatusDownloadComplete == null) {
            onStatusDownloadComplete = new StatusManager.OnStatusDownloadComplete() {
                @Override
                public void onComplete(String path) {
                    if (path != null) {
                        playVideo(path);
                    }
                }
            };
        }
    }

    private void initViews() {
        storiesProgressView = findViewById(R.id.stories);
        image = findViewById(R.id.image);
        videoView = findViewById(R.id.video_view);
        profileImage = findViewById(R.id.profile_image);
        tvUsername = findViewById(R.id.tv_username);
        tvStatusTime = findViewById(R.id.tv_status_time);
        backButton = findViewById(R.id.back_button);
        progressBar = findViewById(R.id.progress_bar);
        storiesProgressView.setStoriesCount(statuses.size());
    }

    @Override
    public void onNext() {
        videoView.stopPlayback();
        videoView.setOnPreparedListener(null);
        videoView.setOnErrorListener(null);
        onStatusDownloadComplete = null;
        if (counter + 1 > statuses.size() - 1)
            return;
        counter++;
        loadStatus(statuses.get(counter));

    }

    @Override
    public void onPrev() {

        videoView.stopPlayback();
        videoView.setOnPreparedListener(null);
        videoView.setOnErrorListener(null);
        onStatusDownloadComplete = null;
        if ((counter - 1) < 0) return;
        counter--;
        loadStatus(statuses.get(counter));

    }

    @Override
    public void onComplete() {
        finish();
    }

    private void loadStatus(final Status status) {
        setStatusTime(status.getTimestamp());
        storiesProgressView.setCurrent(counter);
        videoView.setVisibility(View.GONE);
        image.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);


        //load thumb blurred image while loading original image or video
        image.setImageBitmap(BitmapUtils.simpleBlur(this, BitmapUtils.encodeImage(status.getThumbImg())));

        if (status.getType() == StatusType.IMAGE) {
            loadImage(status);
        } else {
            loadVideo(status);
        }

        //set status as seen
        if (!status.isSeen()) {
            RealmHelper.getInstance().setStatusAsSeen(status.getStatusId());
            //check if all statuses are seen and save it
            if (status.getStatusId().equals(statuses.last().getStatusId()))
                RealmHelper.getInstance().setAllStatusesAsSeen(userId);
        }
        //Schedule a job to update status count on Firebase
        if (!status.getUserId().equals(FireManager.getUid()) && !status.isSeenCountSent()) {
            SetStatusSeenJob.schedule(userId, status.getStatusId());
        }


    }

    private void loadImage(Status status) {

        //if this status by this user load it locally ,otherwise load it from server and cache it
        String url = status.getLocalPath() == null ? status.getContent() : status.getLocalPath();

        Glide.with(this).load(url)
                .into(new SimpleTarget<GlideDrawable>() {
                    @Override
                    public void onResourceReady(GlideDrawable resource, GlideAnimation<? super GlideDrawable> glideAnimation) {
                        progressBar.setVisibility(View.GONE);
                        storiesProgressView.start(counter);
                        image.setImageDrawable(resource);
                    }
                });
    }

    private void loadVideo(Status status) {
        //if the video is not exists download it
        if (status.getLocalPath() == null) {
            downloadStatusVideo(status);
        } else {
            //if the video is exists in device play it
            if (FileUtils.isFileExists(status.getLocalPath())) {
                playVideo(status.getLocalPath());
            } else {
                //otherwise download it
                downloadStatusVideo(status);
            }
        }
    }


    private void downloadStatusVideo(Status status) {
        initStatusDownloadCompleteCallback();
        File statusFile = DirManager.getReceivedStatusFile(status.getStatusId(), status.getType());
        StatusManager.downloadVideoStatus(status.getStatusId(), status.getContent(), statusFile, onStatusDownloadComplete);
    }

    private void playVideo(String path) {
        videoView.requestFocus();
        videoView.setVideoURI(Uri.parse(path));
        videoView.setVisibility(View.VISIBLE);
        videoView.setOnPreparedListener(onPreparedListener);
        videoView.setOnErrorListener(onErrorListener);
    }


    //get statuses durations
    private long[] getDurations() {
        long[] array = new long[statuses.size()];
        for (int i = 0; i < statuses.size(); i++) {
            Status status = statuses.get(i);
            //if it's an image set its duration to IMAGE_STORY_DURATION
            if (status.getType() == StatusType.IMAGE) {
                array[i] = IMAGE_STORY_DURATION;
            } else {
                //if it's a video set its duration to the video duration
                array[i] = status.getDuration();
            }
        }
        return array;
    }

    @Override
    public void onPullStart() {

    }

    @Override
    public void onPull(float v) {

    }

    @Override
    public void onPullCancel() {

    }

    //when the user swipes vertically exit the activity
    @Override
    public void onPullComplete() {
        finish();
    }

    //set status time
    private void setStatusTime(long timestamp) {
        tvStatusTime.setText(TimeHelper.getStatusTime(timestamp));
    }

    //set user image and user info
    private void setUserInfo(User user) {
        Glide.with(this).load(BitmapUtils.encodeImageAsBytes(user.getThumbImg())).asBitmap().into(profileImage);
        if (user.getUid().equals(FireManager.getUid()))
            tvUsername.setText(getResources().getString(R.string.you));
        else
            tvUsername.setText(user.getUserName());
    }

}
