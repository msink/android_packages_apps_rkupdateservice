package android.rockchip.update.service;
import android.rockchip.update.util.RegetInfoUtil;

import android.os.Bundle;
import android.os.Message;
import android.os.Handler;
import android.util.Log;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class FileDownloadTask extends Thread {
    private String TAG = "FileDownloadTask";
    private HttpClient mHttpClient;
    private String mPath;
    private String mFileName;
    private String mTempFileName;
    private URI mUri;
    private FileInfo mFileInfo;
    private boolean mDebug = true;
    private long mContentLength;
    private long mReceivedCount;
    private boolean mAcceptRanges = false;
    private int mPoolThreadNum;
    private Handler mProgressHandler;
    private ExecutorService mDownloadThreadPool;
    private volatile int err = ERR_NOERR;
    private boolean requestStop = false;
    private Object sync = new Object();
    private static final int BUFF_SIZE = 4096;

    public static final int ERR_CONNECT_TIMEOUT = 1;
    public static final int ERR_NOERR = 0;
    public static final int ERR_FILELENGTH_NOMATCH = 2;
    public static final int ERR_REQUEST_STOP = 3;
    public static final int ERR_NOT_EXISTS = 4;
    public static final int ERR_SOCKET_TIMEOUT =  5;
    public static final int ERR_CLIENT_PROTOCAL = 6 ;
    public static final int ERR_IOEXCEPTION = 7 ;

    // message
    public static final int PROGRESS_UPDATE = 1;
    public static final int PROGRESS_STOP_COMPLETE = 2;
    public static final int PROGRESS_START_COMPLETE = 3;
    public static final int PROGRESS_DOWNLOAD_COMPLETE = 4;

    public FileDownloadTask(HttpClient httpClient, URI uri, String path, String fileName, int poolThreadNum) {
        mHttpClient = httpClient;
        mPath = path;
        mUri = uri;
        mPoolThreadNum = poolThreadNum;
        mReceivedCount = (long) 0;

        if (fileName == null) {
            String uriStr = uri.toString();
            mFileName = uriStr.substring(uriStr.lastIndexOf("/") + 1,
                    uriStr.lastIndexOf("?") > 0 ? uriStr.lastIndexOf("?"): uriStr.length());
        } else {
            mFileName = fileName;
        }
        if (mFileName.lastIndexOf(".") > 0) {
            mTempFileName = "."
                    + mFileName.substring(0, mFileName.lastIndexOf("."))
                    + "__tp.xml";
        } else {
            mTempFileName = "." + mFileName + "__tp.xml";
        }
        Log.d(TAG, "tempFileName = " + mTempFileName);
    }

    public void setProgressHandler(Handler progressHandler) {
        mProgressHandler = progressHandler;
    }

    @Override
    public void run() {
        startTask();
    }

    private void startTask() {
        try {
            err = ERR_NOERR;
            requestStop = false;
            getDownloadFileInfo(mHttpClient);
            startWorkThread();
            monitor();
            finish();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "can't connect the network or timeout");
            err = ERR_CONNECT_TIMEOUT;
            onProgressStopComplete(err);
        } catch (Exception e) {
            e.printStackTrace();
            onProgressStopComplete(err);
        }
    }

    public void stopDownload() {
        err = ERR_REQUEST_STOP;
        requestStop = true;
    }

    private void onProgressUpdate(int percent) {
        if (mProgressHandler != null) {
            Message m = new Message();
            m.what = PROGRESS_UPDATE;
            Bundle b = new Bundle();
            b.putInt("percent", percent);
            m.setData(b);
            mProgressHandler.sendMessage(m);
            Log.d(TAG, "send ProgressUpdate");
        }
    }

    private void onProgressStopComplete(int errCode) {
        if (mProgressHandler != null) {
            Message m = new Message();
            m.what = PROGRESS_STOP_COMPLETE;
            Bundle b = new Bundle();
            b.putInt("err", errCode);
            m.setData(b);
            mProgressHandler.sendMessage(m);
            Log.d(TAG, "send ProgressStopComplete");
        }
    }

    private void onProgressStartComplete() {
        if (mProgressHandler != null) {
            Message m = new Message();
            m.what = PROGRESS_START_COMPLETE;
            mProgressHandler.sendMessage(m);
            Log.d(TAG, "send ProgressStartComplete");
        }
    }

    private void onProgressDownloadComplete() {
        if (mProgressHandler != null) {
            Message m = new Message();
            m.what = PROGRESS_DOWNLOAD_COMPLETE;
            mProgressHandler.sendMessage(m);
            Log.d(TAG, "send ProgressDownloadComplete");
        }
    }

    private void finish() throws InterruptedException, IllegalArgumentException, IllegalStateException, IOException {
        if (err == ERR_NOERR) {
            String fullTempfilePath = mPath.endsWith("/") ? (mPath + mTempFileName) : (mPath + "/" + mTempFileName);
            Log.d(TAG, "tempfilepath = " + fullTempfilePath);
            File f = new File(fullTempfilePath);
            if (f.exists()) {
                f.delete();
                Log.d(TAG, "finish(): delete the temp file!");
            }

            onProgressDownloadComplete();
            Log.d(TAG, "download successfull");
            return;
        } else if (err == ERR_REQUEST_STOP) {
            mDownloadThreadPool.shutdownNow();
            while (!mDownloadThreadPool.awaitTermination(3, TimeUnit.SECONDS)) {
                Log.d(TAG, "monitor: progress ===== " + mReceivedCount + "/" + mContentLength);
                onProgressUpdate((int)(mReceivedCount * 100 / mContentLength));
            }

        } else if (err == ERR_CONNECT_TIMEOUT) {
            mDownloadThreadPool.shutdown();
            while (!mDownloadThreadPool.awaitTermination(3, TimeUnit.SECONDS) && requestStop == false) {
                Log.d(TAG, "monitor: progress ===== " + mReceivedCount + "/"+ mContentLength);
                onProgressUpdate((int)(mReceivedCount * 100 / mContentLength));
            }

            mDownloadThreadPool.shutdownNow();
            while (!mDownloadThreadPool.awaitTermination(3, TimeUnit.SECONDS));
        }

        String fullTempfilePath = mPath.endsWith("/") ? (mPath + mTempFileName) : (mPath + "/" + mTempFileName);
        Log.d(TAG, "tempfilepath = " + fullTempfilePath);
        File f = new File(fullTempfilePath);
        RegetInfoUtil.writeFileInfoXml(f, mFileInfo);
        Log.d(TAG, "download task not complete, save the progress !!!");
        onProgressStopComplete(err);
    }

    private void monitor() {
        onProgressStartComplete();
        while (mReceivedCount < mContentLength && err == ERR_NOERR) {
            Log.d(TAG, "monitor: progress ===== " + mReceivedCount + "/"
                    + mContentLength);
            try {
                Thread.sleep(1000);
                onProgressUpdate((int)(mReceivedCount * 100 / mContentLength));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (err == ERR_CONNECT_TIMEOUT) {
            Log.e(TAG, "monitor : ERR_CONNECT_TIMEOUT");
        }

        if (err == ERR_REQUEST_STOP) {
            Log.e(TAG, "monitor: ERR_REQUEST_STOP");
        }
    }

    private int startWorkThread() throws Exception {

        String fullPath = mPath.endsWith("/") ? (mPath + mFileName) : (mPath
                + "/" + mFileName);
        String fullTempfilePath = mPath.endsWith("/") ? (mPath + mTempFileName)
                : (mPath + "/" + mTempFileName);

        Log.d(TAG, "tempfilepath = " + fullTempfilePath);
        File targetFile = new File(fullPath);

        if (!targetFile.exists()) {
            targetFile.createNewFile();
        } else {
            File tmpFile = new File(fullTempfilePath);
            if (tmpFile.exists()) {
                mFileInfo = RegetInfoUtil.parseFileInfoXml(tmpFile);
                Log.d(TAG, "target file have not download complete, so we try to continue download!");
            } else {
                targetFile.delete();
                targetFile.createNewFile();
                Log.d(TAG, "find the same name target file, so delete and rewrite it!!!");
            }
        }

        if (mFileInfo == null) {
            mFileInfo = new FileInfo();
            mFileInfo.setFileLength(mContentLength);
            mFileInfo.setmURI(mUri);
            mFileInfo.setFileName(mFileName);
            mFileInfo.setReceivedLength(0);
        }

        if (mFileInfo.getFileLength() != mContentLength
                && mFileInfo.getURI().equals(mUri)) {
            err = ERR_FILELENGTH_NOMATCH;
            Log.e(TAG,"FileLength or uri not the same, you can't continue download!");
            throw new Exception("ERR_FILELENGTH_NOMATCH!");
        }

        DownloadListener listener = new DownloadListener() {
            public void onPerBlockDown(int count, int pieceId, long posNew) {
                synchronized (this) {
                    mReceivedCount += count;
                }

                mFileInfo.modifyPieceState(pieceId, posNew);
                mFileInfo.setReceivedLength(mReceivedCount);
            }

            public void onPieceComplete() {
                Log.d(TAG, "one piece complete");
            }

            public void onErrorOccurre(int pieceId, long posNow) {
                mFileInfo.modifyPieceState(pieceId, posNow);
            }
        };

        if (mAcceptRanges) {
            Log.d(TAG, "Support Ranges");
            if (mDownloadThreadPool == null) {
                mDownloadThreadPool = Executors.newFixedThreadPool(mPoolThreadNum);
            }
            if (mFileInfo.getPieceNum() == 0) {
                long pieceSize = (mContentLength / mPoolThreadNum) + 1;
                long start = 0, end = pieceSize - 1;
                int pieceId = 0;
                do {
                    if (end > mContentLength - 1) {
                        end = mContentLength - 1;
                    }
                    Log.d(TAG, "piece info, startpos = " + start + " , endpos = " + end);
                    DownloadFilePieceRunnable task = new DownloadFilePieceRunnable(targetFile, pieceId, start, end, start, true);
                    mFileInfo.addPiece(start, end, start);
                    task.setDownloadListener(listener);
                    mDownloadThreadPool.execute(task);

                    start += pieceSize;
                    end = start + pieceSize - 1;
                    pieceId++;

                } while (start < mContentLength);
            } else {
                Log.d(TAG, "try to continue download ====>");
                mReceivedCount = mFileInfo.getReceivedLength();
                for (int index = 0; index < mFileInfo.getPieceNum(); index++) {
                    FileInfo.Piece p = mFileInfo.getPieceById(index);
                    DownloadFilePieceRunnable task = new DownloadFilePieceRunnable(
                            targetFile, index, p.getStart(), p.getEnd(),
                            p.getPosNow(), true);
                    task.setDownloadListener(listener);
                    mDownloadThreadPool.execute(task);
                }
            }
        } else {
            Log.d(TAG, "Can't Ranges!");
            if (mDownloadThreadPool == null) {
                mDownloadThreadPool = Executors.newFixedThreadPool(1);
            }
            if (mFileInfo.getPieceNum() == 0) {
                DownloadFilePieceRunnable task = new DownloadFilePieceRunnable(
                        targetFile, 0, 0, mContentLength - 1, 0, false);
                mFileInfo.addPiece(0, mContentLength - 1, 0);
                task.setDownloadListener(listener);
                mDownloadThreadPool.execute(task);
            } else {
                Log.d(TAG, "try to continue download ====>");
                mReceivedCount = (long) 0;
                FileInfo.Piece p = mFileInfo.getPieceById(0);
                p.setPosNow(0);
                DownloadFilePieceRunnable task = new DownloadFilePieceRunnable(
                        targetFile, 0, 0, mContentLength - 1, p.getPosNow(),
                        false);
                task.setDownloadListener(listener);
                mDownloadThreadPool.execute(task);
            }
        }

        return 0;
    }

    private void getDownloadFileInfo(HttpClient httpClient) throws IOException, ClientProtocolException, Exception {
        HttpGet httpGet = new HttpGet(mUri);
        HttpResponse response = httpClient.execute(httpGet);
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode != 200) {
            err = ERR_NOT_EXISTS;
            Log.d(TAG, "response statusCode = " + statusCode);
            throw new Exception("resource is not exist!");
        }

        if (mDebug) {
            for (Header header : response.getAllHeaders()) {
                Log.d(TAG, header.getName() + ":" + header.getValue());
            }
        }

        Header[] headers = response.getHeaders("OtaPackageLength");
        if (headers.length > 0) {
            mContentLength = Long.valueOf(headers[0].getValue()).longValue();
        }
        httpGet.abort();
        httpGet = new HttpGet(mUri);
        httpGet.addHeader("Range", "bytes=0-" + (mContentLength - 1));
        response = httpClient.execute(httpGet);
        if (response.getStatusLine().getStatusCode() == 206) {
            mAcceptRanges = true;
        }
        httpGet.abort();
    }

    private interface DownloadListener {
        public void onPerBlockDown(int count, int pieceId, long posNew);

        public void onPieceComplete();

        public void onErrorOccurre(int pieceId, long posNew);
    }

    private class DownloadFilePieceRunnable implements Runnable {
        private File mFile;
        private long mStartPosition;
        private long mEndPosition;
        private long mPosNow;
        private boolean mIsRange;
        private DownloadListener mListener;
        private int mPieceId;

        public DownloadFilePieceRunnable(File file, int pieceId,
                long startPosition, long endPosition, long posNow,
                boolean isRange) {
            mFile = file;
            mStartPosition = startPosition;
            mEndPosition = endPosition;
            mIsRange = isRange;
            mPieceId = pieceId;
            mPosNow = posNow;
        }

        public void setDownloadListener(DownloadListener listener) {
            mListener = listener;
        }

        public void run() {
            if (mDebug) {
                Log.d(TAG, "Start:" + mStartPosition + "-" + mEndPosition + "  posNow:" + mPosNow);
            }
            try {
                HttpGet httpGet = new HttpGet(mUri);
                if (mIsRange) {
                    httpGet.addHeader("Range", "bytes=" + mPosNow + "-" + mEndPosition);
                }
                HttpResponse response = mHttpClient.execute(httpGet);
                int statusCode = response.getStatusLine().getStatusCode();
                if (mDebug) {
                    for (Header header : response.getAllHeaders()) {
                        Log.d(TAG, header.getName() + ":" + header.getValue());
                    }
                    Log.d(TAG, "statusCode:" + statusCode);
                }
                if (statusCode == 206 || (statusCode == 200 && !mIsRange)) {
                    InputStream inputStream = response.getEntity().getContent();

                    @SuppressWarnings("resource")
                    RandomAccessFile outputStream = new RandomAccessFile(mFile, "rw");

                    outputStream.seek(mPosNow);
                    int count = 0;
                    byte[] buffer = new byte[BUFF_SIZE];
                    while ((count = inputStream.read(buffer, 0, buffer.length)) > 0) {
                        if (Thread.interrupted()) {
                            Log.d("WorkThread", "interrupted ====>>");
                            httpGet.abort();
                            return;
                        }
                        outputStream.write(buffer, 0, count);
                        mPosNow += count;
                        afterPerBlockDown(count, mPieceId, mPosNow);
                    }
                    outputStream.close();
                } 
                httpGet.abort();
            } catch (IOException e) {
                ErrorOccurre(mPieceId, mPosNow);
                err = ERR_CONNECT_TIMEOUT;
                return;
            }
            onePieceComplete();
            if (mDebug) {
                Log.d(TAG, "End:" + mStartPosition + "-" + mEndPosition);
            }
        }

        private void afterPerBlockDown(int count, int pieceId, long posNew) {
            if (mListener != null) {
                mListener.onPerBlockDown(count, pieceId, posNew);
            }
        }

        private void onePieceComplete() {
            if (mListener != null) {
                mListener.onPieceComplete();
            }
        }

        private void ErrorOccurre(int pieceId, long posNew) {
            if (mListener != null) {
                mListener.onErrorOccurre(pieceId, posNew);
            }
        }
    }
}
