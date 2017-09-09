package com.gianlu.commonutils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.widget.Toast;

public class Toaster {
    public static Handler handler;

    public static void initHandler(Context context) {
        if (context == null) {
            if (Looper.myLooper() == null) Looper.prepare();
            handler = new FakeHandler();
            return;
        }

        if (handler == null || handler instanceof FakeHandler || handler.getLooper() != context.getMainLooper())
            handler = new Handler(context.getMainLooper());
    }

    public static void show(final Context context, final String message, final int duration, @Nullable final String message_extra, @Nullable Throwable ex, @Nullable Runnable extra) {
        initHandler(context);

        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, duration).show();
            }
        });

        Logging.logMe(context, message + (message_extra != null ? (" Details: " + message_extra) : ""), ex != null);
        if (extra != null) handler.post(extra);
        if (ex != null) Logging.logMe(context, ex);
    }

    public static void show(Context context, Message message, @Nullable final String message_extra, @Nullable Throwable ex, @Nullable Runnable extra) {
        show(context, message.getMessage(context), message.isError ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT, message_extra, ex, extra);
    }

    public static void show(Context context, Message message, @Nullable Throwable ex) {
        show(context, message, null, ex, null);
    }

    public static void show(Context context, Message message, @Nullable String message_extra) {
        show(context, message, message_extra, null, null);
    }

    public static void show(Context context, Message message) {
        show(context, message, null, null, null);
    }

    public static void show(Context context, Message message, @Nullable Throwable ex, @Nullable Runnable extra) {
        show(context, message, null, ex, extra);
    }

    public static void show(Context context, Message message, @Nullable String message_extra, @Nullable Runnable extra) {
        show(context, message, message_extra, null, extra);
    }

    public static void show(Context context, Message message, @Nullable Runnable extra) {
        show(context, message, null, null, extra);
    }

    private static class FakeHandler extends Handler {

        @Override
        public boolean sendMessageAtTime(android.os.Message msg, long uptimeMillis) {
            return false;
        }

        @Override
        public void dispatchMessage(android.os.Message msg) {
        }
    }

    @SuppressWarnings("unused")
    public static class Message {
        public static final Message NO_EMAIL_CLIENT = new Message(R.string.noMailClients, true);
        public static final Message OFFLINE = new Message(R.string.offline, false);
        public static final Message COPIED_TO_CLIPBOARD = new Message(R.string.copiedToClipboard, false);
        public static final Message LOGS_DELETED = new Message(R.string.logDeleted, false);
        public static final Message PURCHASING_CANCELED = new Message(R.string.purchaseCanceled, false);
        public static final Message BILLING_USER_CANCELLED = new Message(R.string.userCancelled, false);
        public static final Message THANK_YOU = new Message(R.string.thankYou, false);
        public static final Message FAILED_BUYING_ITEM = new Message(R.string.failedBuying, true);
        public static final Message FAILED_CONNECTION_BILLING_SERVICE = new Message(R.string.failedBillingConnection, true);

        public final int messageRes;
        public final boolean isError;

        public Message(@StringRes int messageRes, boolean isError) {
            this.messageRes = messageRes;
            this.isError = isError;
        }

        public String getMessage(Context context) {
            if (context == null) {
                return "Unknown message!";
            }

            return context.getString(messageRes);
        }
    }
}
