package com.gianlu.commonutils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public final class Logging {
    @Nullable
    private static Logger logger;

    private Logging() {
    }

    @NonNull
    private static File getLogsDirectory(@NonNull Context context) {
        return new File(context.getFilesDir(), "logs");
    }

    private static boolean isLogFile(@NonNull String name) {
        return name.toLowerCase().endsWith(".log");
    }

    @NonNull
    private static File[] listLogsInDirectory(@NonNull File dir) {
        return dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return isLogFile(name);
            }
        });
    }

    private static File[] listLogFilesInternal(@NonNull Context context) {
        return listLogsInDirectory(getLogsDirectory(context));
    }

    @NonNull
    private static String removeExtension(@NonNull File file) {
        String name = file.getName();
        int last = name.lastIndexOf('.');
        return name.substring(0, last);
    }

    @NonNull
    private static Date getDate(@NonNull File file) throws ParseException {
        String date = removeExtension(file);
        return getFileDateFormatter().parse(date);
    }

    public static void clearLogs(@NonNull Context context) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -7);

        for (File file : listLogFilesInternal(context)) {
            try {
                Date date = getDate(file);
                if (date.before(cal.getTime()))
                    if (!file.delete())
                        log("Couldn't delete " + file, true);
            } catch (ParseException ex) {
                if (CommonUtils.isDebug()) ex.printStackTrace();
            }
        }
    }

    public static void deleteAllLogs(Context context) {
        for (File file : listLogFilesInternal(context)) {
            if (!file.delete()) log("Couldn't delete " + file, true);
        }

        init(context);
    }

    @Nullable
    public static LogFile getLatestLogFile(@NonNull Context context) {
        List<LogFile> logs = listLogFiles(context);
        return logs.isEmpty() ? null : logs.get(0);
    }

    @SuppressLint("SimpleDateFormat")
    @NonNull
    private static SimpleDateFormat getFileDateFormatter() {
        return new SimpleDateFormat("d-MM-yyyy");
    }

    public static void init(@NonNull Context context) {
        try {
            logger = new Logger(context);
            new Thread(logger).start();
            log("Logging initialized!", false);
        } catch (IOException ex) {
            System.err.println("Failed initializing logging!");
            if (CommonUtils.isDebug())
                ex.printStackTrace();
        }
    }

    public static List<LogFile> listLogFiles(@NonNull Context context) {
        File[] files = listLogFilesInternal(context);

        List<LogFile> logs = new ArrayList<>();
        for (File file : files) {
            try {
                logs.add(new LogFile(file));
            } catch (ParseException ex) {
                if (CommonUtils.isDebug()) ex.printStackTrace();
            }
        }

        Collections.sort(logs, new LogFilesComparator());

        return logs;
    }

    @NonNull
    public static List<LogLine> getLogLines(@NonNull LogFile log) throws IOException {
        List<LogLine> logLines = new ArrayList<>();

        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(log)));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) continue;

            try {
                String[] split = line.split("\\|");
                long timestamp = Long.parseLong(split[0]);
                String version = split[1];
                LogLine.Type type = LogLine.Type.valueOf(split[2]);

                StringBuilder message = new StringBuilder();
                boolean first = true;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    if (!first) message.append('\n');
                    message.append(line);
                    first = false;
                }

                logLines.add(new LogLine(timestamp, version, type, message.toString()));
            } catch (Exception ex) {
                if (!log.delete()) System.err.println("Cannot delete corrupted log file.");
                if (CommonUtils.isDebug())
                    new Exception("Couldn't parse line: " + line, ex).printStackTrace();
                break;
            }
        }

        return logLines;
    }

    @NonNull
    public static String getStackTrace(@NonNull Throwable ex) {
        StringWriter sw = new StringWriter();
        PrintWriter writer = new PrintWriter(sw);
        ex.printStackTrace(writer);
        return sw.toString();
    }

    public static void log(Throwable ex) {
        if (ex == null) return;
        log(getStackTrace(ex), true);
    }

    public static void log(String msg, boolean error) {
        log(msg, error ? LogLine.Type.ERROR : LogLine.Type.INFO);
    }

    public static void log(String msg, @NonNull LogLine.Type type) {
        if (msg == null) return;
        if (logger != null) logger.log(System.currentTimeMillis(), msg, type);
    }

    private static class Logger implements Runnable {
        private final File logFile;
        private final LinkedBlockingQueue<LogLine> queue = new LinkedBlockingQueue<>();
        private final String appVersion;

        private Logger(Context context) throws IOException {
            File logs = getLogsDirectory(context);
            if (!logs.exists() && !logs.mkdir())
                throw new IOException("Logs directory cannot be created!");

            logFile = new File(logs, getFileDateFormatter().format(new Date()) + ".log");
            if (!logFile.exists() && !logFile.createNewFile())
                throw new IOException("Couldn't create log file!");
            if (!logFile.canWrite())
                throw new IOException("Can write to file!");

            try {
                appVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException ex) {
                throw new IOException(ex);
            }
        }

        @SuppressWarnings("InfiniteLoopStatement")
        @Override
        public void run() {
            try (FileOutputStream out = new FileOutputStream(logFile, true)) {
                while (true) {
                    LogLine line = queue.take();
                    line.write(out, appVersion);
                    out.flush();
                }
            } catch (IOException | InterruptedException ex) {
                if (CommonUtils.isDebug()) ex.printStackTrace();
            }
        }

        public void log(long timeMillis, String msg, LogLine.Type type) {
            queue.add(new LogLine(timeMillis, appVersion, type, msg));
        }
    }

    public static class LogLine implements Serializable {
        private final Type type;
        private final String message;
        private final long timestamp;
        private final String appVersion;

        public LogLine(long timestamp, @NonNull String appVersion, @NonNull Type type, @NonNull String message) {
            this.timestamp = timestamp;
            this.appVersion = appVersion;
            this.type = type;
            this.message = message;
        }

        public LogLine(@NonNull Type type, @NonNull String message) {
            this.timestamp = System.currentTimeMillis();
            this.appVersion = null;
            this.type = type;
            this.message = message;
        }

        @NonNull
        public String message() {
            return message;
        }

        private void write(OutputStream out, String fallbackVersion) throws IOException {
            String version;
            if (appVersion == null) version = fallbackVersion;
            else version = appVersion;

            out.write(String.valueOf(timestamp).getBytes());
            out.write('|');
            out.write(version.replace('|', '_').getBytes());
            out.write('|');
            out.write(type.name().getBytes());
            out.write('\n');
            out.write(message.replace("\n\n", "\n").getBytes());
            out.write('\n');
            out.write('\n');
        }

        public enum Type {
            INFO,
            WARNING,
            ERROR
        }
    }

    public static class LogFilesComparator implements Comparator<LogFile> {
        @Override
        public int compare(LogFile o1, LogFile o2) {
            return -o1.date.compareTo(o2.date);
        }
    }

    public static class LogFile extends File {
        public final Date date;

        LogFile(@NonNull File file) throws ParseException {
            super(file.getAbsolutePath());
            date = getDate(file);
        }

        @Override
        @NonNull
        public String toString() {
            return removeExtension(this);
        }
    }

    @UiThread
    public static class LogLineAdapter extends RecyclerView.Adapter<LogLineAdapter.ViewHolder> {
        private final List<LogLine> logs;
        private final LayoutInflater inflater;

        public LogLineAdapter(Context context, List<LogLine> logs) {
            this.inflater = LayoutInflater.from(context);
            this.logs = logs;
        }

        @NonNull
        public static View createLogLineView(LayoutInflater inflater, ViewGroup parent, LogLine line) {
            ViewHolder holder = new ViewHolder(inflater, parent);
            setupLoglineHolder(holder, line);
            return holder.itemView;
        }

        @SuppressLint("SetTextI18n")
        public static void setupLoglineHolder(final ViewHolder holder, LogLine line) {
            holder.msg.setText(line.message);
            holder.msg.setSingleLine(true);
            holder.msg.setTag(true);
            holder.msg.setEllipsize(TextUtils.TruncateAt.END);

            switch (line.type) {
                case INFO:
                    holder.level.setText("INFO: ");
                    holder.level.setTextColor(Color.BLACK);
                    break;
                case WARNING:
                    holder.level.setText("WARNING: ");
                    holder.level.setTextColor(Color.rgb(253, 216, 53));
                    break;
                case ERROR:
                    holder.level.setText("ERROR: ");
                    holder.level.setTextColor(Color.RED);
                    break;
            }

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if ((Boolean) holder.msg.getTag()) {
                        holder.msg.setSingleLine(false);
                        holder.msg.setTag(false);
                    } else {
                        holder.msg.setSingleLine(true);
                        holder.msg.setTag(true);
                    }
                }
            });
        }

        public void clear() {
            logs.clear();
            notifyDataSetChanged();
        }

        public void add(@NonNull LogLine line) {
            logs.add(line);
            notifyItemInserted(logs.size() - 1);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(inflater, parent);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LogLine item = logs.get(position);
            setupLoglineHolder(holder, item);
        }

        @Override
        public int getItemCount() {
            return logs.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView msg;
            final TextView level;

            public ViewHolder(LayoutInflater inflater, ViewGroup parent) {
                super(inflater.inflate(R.layout.log_line_item, parent, false));

                msg = itemView.findViewById(R.id.logLine_msg);
                level = itemView.findViewById(R.id.logLine_level);
            }
        }
    }
}
