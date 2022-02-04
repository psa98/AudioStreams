package c.ponom.audiostreams.audio_streams;

import static java.lang.Math.min;

import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import c.ponom.recorder2.audio_streams.AudioInputStream;
import kotlin.NotImplementedError;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class MonitoredAudioInputStream  extends AudioInputStream {

    final int BLOCKING_PAUSE = 5; //период запроса о появлении данных в буфере, мс
    private AudioInputStream inputStream;
    private MonitoringAudioInputStream monitoringStream;
    private final  int bufferInitialSize = 1024*16;
    private  ByteArrayOutputStream monitorBuffer;
    private boolean closedMain=false;


    private MonitoredAudioInputStream(){

    }

    public MonitoredAudioInputStream(AudioInputStream inStream) {
        inputStream=inStream;
        monitoringStream = new MonitoringAudioInputStream();
        monitorBuffer=monitoringStream.monitorBuffer;

    }
    private MonitoredAudioInputStream(long streamDuration, int channels,
                                     int samplingRate) {

    }

    private MonitoredAudioInputStream(@NonNull MediaFormat format) {

    }


    @Override
    public long getTimestamp() {
        return inputStream.getTimestamp();
    }

    @Override
    protected void setBytesSent(long value) {
        super.setBytesSent(value);
    }

    @Override
    public long getBytesSent() {
        return inputStream.getBytesSent();
    }


    @Override
    public long totalBytesEstimate() {
        return inputStream.totalBytesEstimate();
    }

    @Nullable
    @Override
    public Function1<Long, Unit> getOnReadCallback() {
        return inputStream.getOnReadCallback();
    }

    @Override
    public void setOnReadCallback(@Nullable Function1< ? super Long, Unit> onReadCallback) {
        inputStream.setOnReadCallback(onReadCallback);
    }

    @Override
    public long bytesRemainingEstimate() {
        return  inputStream.bytesRemainingEstimate();
    }

    @Override
    public int available() {
         return inputStream.available();
    }



    @Override
    public long skip(long n) throws IOException {
        return  inputStream.skip(n);
    }

    @Override
    public void mark(int readlimit) {
         inputStream.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        inputStream.reset();
    }

    @Override
    public boolean markSupported() {
        return inputStream.markSupported();
    }

    @Override
    public boolean canReadShorts() {
        return  inputStream.canReadShorts();
    }

    @Override
    public int readShorts(@NonNull short[] b, int off, int len) throws IOException {
        bufferPutShorts(b);
        return inputStream.readShorts(b, off, len);
    }

    @Override
    public int readShorts(@NonNull short[] b) throws IOException {
        bufferPutShorts(b);
        return inputStream.readShorts(b, 0, b.length);
    }


    @Override
    public int read(byte[] b) throws IOException {
        bufferPutBytes(b);
        return read(b,0,b.length);
    }

    @Override
    public void close() throws IOException {
        closedMain=true;
        monitoringStream.close();
        inputStream.close();
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }

    @Override //протестить что будет если отмониторенный поток кинет другое исключение
    public int read(@Nullable byte[] b, int off, int len) throws IOException {
        if (closedMain) return -1;
        if (b == null) throw new NullPointerException("Null array passed");
        if (off < 0 || len < 0 || len > b.length - off)
            throw new IndexOutOfBoundsException("Wrong read(...) params");
        if (len == 0) return 0;
        bufferPutBytes(b);
        return inputStream.read(b, off, len);
    }



    synchronized void bufferPutBytes(byte[] dataSamples) throws IOException {
        monitorBuffer.write(dataSamples);
    }


    synchronized void bufferPutShorts(short[] dataSamples) throws IOException {
        monitorBuffer.write(ArrayUtils.INSTANCE.shortToByteArrayLittleEndian(dataSamples));
    }


    synchronized int  bufferReadBytes(byte[] b, int off, int len) throws IOException {
        if (b == null) throw new NullPointerException("Null array passed");
        if (off < 0 || len < 0 || len > b.length - off)
            throw new IndexOutOfBoundsException("Wrong read(...) params");
        if (len == 0) return 0;
        if (closedMain) return -1;
        int length = min(len,b.length);
        if (length==0)return 0;
        byte[] fullBuffer=monitorBuffer.toByteArray();
        byte[] returnBuffer=Arrays.copyOf(fullBuffer,(min(length,fullBuffer.length)));
        //todo  - с офсетом разобраться потом
        System.arraycopy(returnBuffer,0,b,0,returnBuffer.length);
        int restBufferSize=fullBuffer.length-returnBuffer.length;
        if (restBufferSize==0) monitorBuffer.reset();
        else{
            byte[] restBuffer=Arrays.copyOfRange(fullBuffer,returnBuffer.length,fullBuffer.length);
            monitorBuffer.reset();
            monitorBuffer.write(restBuffer);

            //monitoringStream.askingThread.interrupt();
            // не тестил, может быть надежнее

        }
        return returnBuffer.length;
    }


    public MonitoringAudioInputStream getMonitoringStream() {
        return monitoringStream;
    }

    public AudioInputStream getInputStream() {
        return inputStream;
    }




    private class MonitoringAudioInputStream extends MonitoredAudioInputStream {

        Thread askingThread = Thread.currentThread();
        private boolean monitorClosed =false;
        private  ByteArrayOutputStream monitorBuffer= new ByteArrayOutputStream(bufferInitialSize);
        public MonitoringAudioInputStream() {
            super();

        }

        @Override
        public int read() throws IOException {
            throw new NotImplementedError("Not implemented, use read(b[]....)");
        }

        @Override
        public int read(byte[] b) throws IOException {
            return read(b,0,b.length);
        }

        @Override
        synchronized public int read(@Nullable byte[] b, int off, int len)
                throws IOException {
            if (b==null)throw new NullPointerException("Null buffer presented");
            if (len==0||b.length==0) return 0;
            if (monitorClosed) return -1;
            askingThread=Thread.currentThread();
            while (monitorBuffer.size() == 0&&!monitorClosed &&!closedMain) {
                try {
                    Thread.sleep(BLOCKING_PAUSE);
                } catch (InterruptedException e) {
                    //
                }
            }
            return bufferReadBytes(b,off,len);
        }


        @Override
        public int readShorts(@NonNull short[] b, int off, int len) throws IOException {
            byte[] byteArray = new byte[b.length*2];
            int bytes = read(byteArray,off*2,len*2);
            short[]  resultArray = ArrayUtils.INSTANCE.byteToShortArray(byteArray);
            int resultLen = min(bytes/2,b.length);
            System.arraycopy(resultArray,0,b,0,resultLen);
            return resultLen;
       }

        @Override
        public void close() throws IOException {
            super.close();
            monitorBuffer.reset();
            monitorBuffer =new ByteArrayOutputStream(0);
            monitorClosed =true;

        }

        @Override
        public int readShorts(@NonNull short[] b) throws IOException {
            return readShorts(b,0,b.length);
        }



    }




}

