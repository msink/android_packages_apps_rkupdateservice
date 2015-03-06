package android.rockchip.update.service;

import java.net.URI;  
import java.util.ArrayList;  
import android.util.Log;  
  
public class FileInfo {  
    private static final String TAG = "FileInfo";  
    private URI mURI;  
    private long mFileLength;  
    private long mReceivedLength;  
    private String mFileName;  
    private ArrayList<Piece> mPieceList;  

    public URI getURI() {  
        return mURI;  
    }  
  
    synchronized public void setmURI(URI mURI) {  
        this.mURI = mURI;  
    }  
  
    public long getReceivedLength() {  
        return mReceivedLength;  
    }  
      
    synchronized public void setReceivedLength(long length) {  
        mReceivedLength = length;  
    }  
      
    public long getFileLength() {  
        return mFileLength;  
    }  
  
    synchronized public void setFileLength(long mFileLength) {  
        this.mFileLength = mFileLength;  
    }  
  
    public int getPieceNum() {  
        if(mPieceList == null) {  
            mPieceList = new ArrayList<Piece>();  
        }  
        return mPieceList.size();  
    }  
  
    public String getFileName() {  
        return mFileName;  
    }  
  
    synchronized public void setFileName(String mFileName) {  
        this.mFileName = mFileName;  
    }  
      
    synchronized public void addPiece(long start, long end, long posNow){  
        if(mPieceList == null) {  
            mPieceList = new ArrayList<Piece>();  
        }  
          
        Piece p = new Piece(start, end, posNow);  
        mPieceList.add(p);  
    }  
      
    synchronized public void modifyPieceState(int pieceID, long posNew) {  
        Piece p = mPieceList.get(pieceID);  
        if(p != null) {  
            p.setPosNow(posNew);  
        }  
    }  
      
    public Piece getPieceById(int pieceID) {  
        return mPieceList.get(pieceID);  
    }  
      
    public void printDebug() {  
        Log.d(TAG, "filename = " + mFileName);  
        Log.d(TAG, "uri = " + mURI);  
        Log.d(TAG, "PieceNum = " + mPieceList.size());  
        Log.d(TAG, "FileLength = " + mFileLength);  
          
        int id = 0;  
        for(Piece p : mPieceList) {  
            Log.d(TAG, "piece " + id + " :start = " + p.getStart() + " end = " + p.getEnd() + " posNow = " + p.getPosNow());  
            id++;  
        }  
    }  
  
    public class Piece {  
        private long start;  
        private long end;  
        private long posNow;  
          
        public Piece(long s, long e, long n) {  
            start = s;  
            end = e;  
            posNow = n;  
        }  
        public long getStart() {  
            return start;  
        }  
        public long getEnd() {  
            return end;  
        }  
        public long getPosNow() {  
            return posNow;  
        }  
        public void setPosNow(long posNow) {  
            this.posNow = posNow;  
        }         
    }  
}
