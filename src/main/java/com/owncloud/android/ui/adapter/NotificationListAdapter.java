/*
 * ownCloud Android client application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2016 ownCloud Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.owncloud.android.ui.adapter;

import android.graphics.PorterDuff;
import android.graphics.drawable.PictureDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.GenericRequestBuilder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.StreamEncoder;
import com.bumptech.glide.load.resource.file.FileToStreamDecoder;
import com.caverock.androidsvg.SVG;
import com.owncloud.android.R;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.lib.resources.notifications.models.Action;
import com.owncloud.android.lib.resources.notifications.models.Notification;
import com.owncloud.android.ui.activity.NotificationsActivity;
import com.owncloud.android.utils.DisplayUtils;
import com.owncloud.android.utils.ThemeUtils;
import com.owncloud.android.utils.svg.SvgDecoder;
import com.owncloud.android.utils.svg.SvgDrawableTranscoder;
import com.owncloud.android.utils.svg.SvgSoftwareLayerSetter;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * This Adapter populates a ListView with all notifications for an account within the app.
 */
public class NotificationListAdapter extends RecyclerView.Adapter<NotificationListAdapter.NotificationViewHolder> {
    private static final String TAG = NotificationListAdapter.class.getSimpleName();

    private List<Notification> notifications;
    private OwnCloudClient client;
    private NotificationsActivity notificationsActivity;

    public NotificationListAdapter(OwnCloudClient client, NotificationsActivity notificationsActivity) {
        this.notifications = new ArrayList<>();
        this.client = client;
        this.notificationsActivity = notificationsActivity;
    }

    public void setNotificationItems(List<Notification> notificationItems) {
        notifications.clear();
        notifications.addAll(notificationItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(notificationsActivity).inflate(R.layout.notification_list_item, parent, false);
        return new NotificationViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        Notification notification = notifications.get(position);
        holder.dateTime.setText(DisplayUtils.getRelativeTimestamp(notificationsActivity,
                notification.getDatetime().getTime()));
        holder.subject.setText(notification.getSubject());
        holder.message.setText(notification.getMessage());

        // Todo set proper action icon (to be clarified how to pick)
        if (!TextUtils.isEmpty(notification.getIcon())) {
            downloadIcon(notification.getIcon(), holder.icon);
        }

        // add action buttons
        holder.buttons.removeAllViews();

        for (Action action : notification.getActions()) {
            Button button = new Button(notificationsActivity);
            button.setText(action.label);
            if (action.primary) {
                button.getBackground().setColorFilter(ThemeUtils.primaryColor(notificationsActivity, true), PorterDuff.Mode.SRC_ATOP);
                button.setTextColor(ThemeUtils.fontColor(notificationsActivity));
            }

            button.setOnClickListener(v -> new ExecuteActionTask(holder).execute(action));

            holder.buttons.addView(button);
        }
    }

    private class ExecuteActionTask extends AsyncTask<Action, Void, Boolean> {

        private NotificationViewHolder holder;

        ExecuteActionTask(NotificationViewHolder holder) {
            this.holder = holder;
        }

        @Override
        protected Boolean doInBackground(Action... actions) {
            HttpMethod method;
            Action action = actions[0];

            switch (action.type) {
                case "GET":
                    method = new GetMethod(action.link);
                    break;

                case "POST":
                    method = new PostMethod(action.link);
                    break;

                case "DELETE":
                    method = new DeleteMethod(action.link);
                    break;

                default:
                    // do nothing
                    return false;
            }

            method.setRequestHeader(RemoteOperation.OCS_API_HEADER, RemoteOperation.OCS_API_HEADER_VALUE);

            int status;
            try {
                status = client.executeMethod(method);
            } catch (IOException e) {
                Log_OC.e(TAG, "Execution of notification action failed: " + e);
                return false;
            }

            return status == HttpStatus.SC_OK;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                int position = holder.getAdapterPosition();
                notifications.remove(position);
                notifyItemRemoved(position);
            } else {
                DisplayUtils.showSnackMessage(notificationsActivity, "Failed to execute action!");
            }
        }
    }

    private void downloadIcon(String icon, ImageView itemViewType) {
        GenericRequestBuilder<Uri, InputStream, SVG, PictureDrawable> requestBuilder = Glide.with(notificationsActivity)
                .using(Glide.buildStreamModelLoader(Uri.class, notificationsActivity), InputStream.class)
                .from(Uri.class)
                .as(SVG.class)
                .transcode(new SvgDrawableTranscoder(), PictureDrawable.class)
                .sourceEncoder(new StreamEncoder())
                .cacheDecoder(new FileToStreamDecoder<>(new SvgDecoder()))
                .decoder(new SvgDecoder())
                .placeholder(R.drawable.ic_notification)
                .error(R.drawable.ic_notification)
                .animate(android.R.anim.fade_in)
                .listener(new SvgSoftwareLayerSetter<>());


        Uri uri = Uri.parse(icon);
        requestBuilder
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .load(uri)
                .into(itemViewType);
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        private final RelativeLayout layout;
        private final ImageView icon;
        private final TextView subject;
        private final TextView message;
        private final TextView dateTime;
        private final LinearLayout buttons;

        private NotificationViewHolder(View itemView) {
            super(itemView);
            layout = itemView.findViewById(R.id.notification_layout);
            icon = itemView.findViewById(R.id.notification_icon);
            subject = itemView.findViewById(R.id.notification_subject);
            message = itemView.findViewById(R.id.notification_message);
            dateTime = itemView.findViewById(R.id.notification_datetime);
            buttons = itemView.findViewById(R.id.notification_buttons);
        }
    }
}