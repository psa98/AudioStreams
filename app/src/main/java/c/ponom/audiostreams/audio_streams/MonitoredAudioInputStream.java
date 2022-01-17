package c.ponom.audiostreams.audio_streams;

import static java.lang.Integer.min;

import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

import c.ponom.recorder2.audio_streams.AbstractSoundInputStream;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class MonitoredAudioInputStream  extends AbstractSoundInputStream {


    private AbstractSoundInputStream inputStream;
    private AbstractSoundInputStream monitorStream;
    private final  int bufferInitialSize = 1024*16;
    private  ByteArrayOutputStream monitorBuffer= new ByteArrayOutputStream(bufferInitialSize);
    private final Semaphore mutex = new Semaphore(1);




    private MonitoredAudioInputStream(){

    }

    public MonitoredAudioInputStream(AbstractSoundInputStream inStream) {
        inputStream=inStream;
    }

    private MonitoredAudioInputStream(long streamDuration, int channels,
                                     int samplingRate) {

    }

    private MonitoredAudioInputStream(@NonNull MediaFormat format) {

    }

    @Override
    public int getBytesPerSample() {
        return inputStream.getBytesPerSample();
    }



    @Override
    public long getTimestamp() {
        return inputStream.getTimestamp();
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
    public int bytesRemaining() {
        return  inputStream.bytesRemaining();
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
    public boolean canReturnShorts() {
        return  inputStream.canReturnShorts();
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

    /**
     * Closes this input stream and releases any system resources associated
     * with the stream.,b
     *
     * <p> The <code>close</code> method of <code>InputStream</code> does
     * nothing.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        inputStream.close();
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }

    @Override //протестить что будет если отмониторенный поток кинет другое исключение
    public int read(@Nullable byte[] b, int off, int len) throws IOException {
        bufferPutBytes(b);
        return inputStream.read(b, off, len);
    }



    synchronized void bufferPutBytes(byte[] dataSamples) throws IOException {
        monitorBuffer.write(dataSamples);
    }


    synchronized void bufferPutShorts(short[] dataSamples) throws IOException {

        monitorBuffer.write(shortToByteArrayLittleEndian(dataSamples));

    }


    synchronized int  bufferReadBytes(byte[] b,int off, int len) throws IOException {
        int length =min(len,b.length);
        if (length==0)return 0;
        if (monitorBuffer.size()==0) waitForData();
        byte[] fullBuffer=monitorBuffer.toByteArray();
        byte[] returnBuffer=Arrays.copyOf(fullBuffer,(min(length,fullBuffer.length)));
        //todo  - с офсетом разобраться потом
        b= Arrays.copyOf(returnBuffer,returnBuffer.length);

        int restBufferSize=fullBuffer.length-returnBuffer.length;
        if (restBufferSize==0){
            clearBuffer();


        }else{ // todo с единичками что не то
            byte[] restBuffer=Arrays.copyOfRange(fullBuffer,returnBuffer.length,fullBuffer.length);
            monitorBuffer=new ByteArrayOutputStream(restBuffer.length);
            monitorBuffer.write(restBuffer);
        }
        return length;
    }

    private void clearBuffer() {
        monitorBuffer=new ByteArrayOutputStream(0);

        //mutex.acquireUninterruptibly(); - это надо выставить в том потоке где у нас сторонний
        // read()

        // тут выставляем мутекс
    }

    private void waitForData() {

        // тут тормозим если семафор занят.


        // тут надо блокировать поток до:
        // его закрытия close()
        // Любого успешного пополнения буфера

        // TODO("Not yet implemented")
    }



    public AbstractSoundInputStream getInputStream() {
        return inputStream;
    }

}
