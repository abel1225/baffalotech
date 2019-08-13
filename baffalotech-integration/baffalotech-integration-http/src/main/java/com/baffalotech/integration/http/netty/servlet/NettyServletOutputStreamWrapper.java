package com.baffalotech.integration.http.netty.servlet;

import io.netty.channel.ChannelFutureListener;

import javax.servlet.*;

import com.baffalotech.integration.http.netty.core.util.Recyclable;
import com.baffalotech.integration.http.netty.core.util.Wrapper;

import java.io.IOException;

/**
 * servlet 输出流(包装类), 可以控制对流的访问
 *
 * 频繁更改, 需要cpu对齐. 防止伪共享, 需设置 : -XX:-RestrictContended
 * @author 84215
 */
@sun.misc.Contended
public class NettyServletOutputStreamWrapper extends javax.servlet.ServletOutputStream
        implements Wrapper<NettyServletOutputStream>,Recyclable{

    /**
     * 源数据
     */
    private NettyServletOutputStream source;
    /**
     * 暂停标志
     */
    private boolean suspendFlag = false;

    private ChannelFutureListener closeListener;

    public NettyServletOutputStreamWrapper(ChannelFutureListener closeListener) {
        this.closeListener = closeListener;
    }

    /**
     * 设置(开启/关闭)暂停输出操作
     * @param suspendFlag true=暂停操作, false=恢复操作
     */
    public void setSuspendFlag(boolean suspendFlag) {
        this.suspendFlag = suspendFlag;
    }

    /**
     * 是否暂停操作输出流
     * @return
     */
    public boolean isSuspendFlag() {
        return suspendFlag;
    }

    @Override
    public boolean isReady() {
        return source.isReady();
    }

    @Override
    public void setWriteListener(WriteListener listener) {
        source.setWriteListener(listener);
    }

    @Override
    public void write(int b) throws IOException {
        if(isSuspendFlag()){
            return;
        }
        source.write(b);
    }

    @Override
    public void close() throws IOException {
        if(isSuspendFlag()){
            return;
        }
        source.close();
    }

    @Override
    public void flush() throws IOException {
        if(isSuspendFlag()){
            return;
        }
        source.flush();
    }

    public void resetBuffer(){
        if(isSuspendFlag()){
            return;
        }
        source.resetBuffer();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if(isSuspendFlag()){
            return;
        }
        source.write(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
        if(isSuspendFlag()){
            return;
        }
        source.write(b);
    }

    @Override
    public void wrap(NettyServletOutputStream source) {
        if(closeListener != null) {
            source.setCloseListener(closeListener);
        }
        this.source = source;
    }

    @Override
    public NettyServletOutputStream unwrap() {
        return source;
    }

    @Override
    public void recycle() {
        if(source != null){
            NettyServletOutputStream out = source;
            source = null;
            out.recycle();
        }

        if(suspendFlag){
            suspendFlag = false;
        }
    }

}
