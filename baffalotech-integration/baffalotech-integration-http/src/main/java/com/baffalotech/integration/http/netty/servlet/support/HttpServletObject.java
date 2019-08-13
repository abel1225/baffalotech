package com.baffalotech.integration.http.netty.servlet.support;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.baffalotech.integration.configuration.IntegrationServerProperties;
import com.baffalotech.integration.http.netty.core.util.AbstractRecycler;
import com.baffalotech.integration.http.netty.core.util.HttpHeaderUtil;
import com.baffalotech.integration.http.netty.core.util.Recyclable;
import com.baffalotech.integration.http.netty.servlet.NettyHttpServletRequest;
import com.baffalotech.integration.http.netty.servlet.NettyHttpServletResponse;
import com.baffalotech.integration.http.netty.servlet.NettyHttpServletSession;
import com.baffalotech.integration.http.netty.servlet.NettyServletContext;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

/**
 * servlet对象 (包含3大对象 : 请求, 响应, tcp通道 )
 *
 * @author acer01
 *  2018/8/1/001
 */
public class HttpServletObject implements Recyclable{

    private static final AbstractRecycler<HttpServletObject> RECYCLER = new AbstractRecycler<HttpServletObject>() {
        @Override
        protected HttpServletObject newInstance() {
            return new HttpServletObject();
        }
    };
    private static final AttributeKey<NettyHttpServletSession> CHANNEL_ATTR_KEY_SESSION = AttributeKey.valueOf(NettyHttpServletSession.class + "#ServletHttpSession");

    private NettyHttpServletRequest httpServletRequest;
    private NettyHttpServletResponse httpServletResponse;
    private ChannelHandlerContext channelHandlerContext;
    private NettyServletContext servletContext;
    private IntegrationServerProperties config;
    private boolean isHttpKeepAlive;

    private HttpServletObject() {
    }

    public static HttpServletObject newInstance(NettyServletContext servletContext, IntegrationServerProperties config, ChannelHandlerContext context, FullHttpRequest fullHttpRequest) {
        HttpServletObject instance = RECYCLER.getInstance();
        instance.servletContext = servletContext;
        instance.config = config;
        instance.channelHandlerContext = context;
        instance.isHttpKeepAlive = HttpHeaderUtil.isKeepAlive(fullHttpRequest);

        //创建新的servlet请求对象
        instance.httpServletRequest = NettyHttpServletRequest.newInstance(instance,fullHttpRequest);
        //创建新的servlet响应对象
        instance.httpServletResponse = NettyHttpServletResponse.newInstance(instance);
        return instance;
    }

    public NettyHttpServletSession getSession(){
        return getSession(channelHandlerContext);
    }

    public void setSession(NettyHttpServletSession httpSession){
        setSession(channelHandlerContext,httpSession);
    }

    /**
     * 从管道中绑定的属性中获取 httpSession
     * @return
     */
    public static NettyHttpServletSession getSession(ChannelHandlerContext channelHandlerContext){
        if(channelHandlerContext != null && channelHandlerContext.channel() != null) {
            Attribute<NettyHttpServletSession> attribute = channelHandlerContext.channel().attr(CHANNEL_ATTR_KEY_SESSION);
            if(attribute != null){
                return attribute.get();
            }
        }
        return null;
    }

    /**
     * 把 httpSession绑定到管道属性中
     * @param httpSession
     */
    public static void setSession(ChannelHandlerContext channelHandlerContext, NettyHttpServletSession httpSession){
        if(isChannelActive(channelHandlerContext)) {
            channelHandlerContext.channel().attr(CHANNEL_ATTR_KEY_SESSION).set(httpSession);
        }
    }

    /**
     * 管道是否处于活动状态
     * @return
     */
    public static boolean isChannelActive(ChannelHandlerContext channelHandlerContext){
        if(channelHandlerContext != null && channelHandlerContext.channel() != null && channelHandlerContext.channel().isActive()) {
            return true;
        }
        return false;
    }

    public boolean isHttpKeepAlive() {
        return isHttpKeepAlive;
    }

    public NettyHttpServletRequest getHttpServletRequest() {
        return httpServletRequest;
    }

    public NettyServletContext getServletContext() {
        return servletContext;
    }

    public NettyHttpServletResponse getHttpServletResponse() {
        return httpServletResponse;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return channelHandlerContext;
    }

    public InetSocketAddress getServletServerAddress(){
        return servletContext.getServletServerAddress();
    }

    public InetSocketAddress getLocalAddress(){
        SocketAddress socketAddress = channelHandlerContext.channel().localAddress();
        if(socketAddress == null){
            return null;
        }
        if(socketAddress instanceof InetSocketAddress){
            return (InetSocketAddress) socketAddress;
        }
        return null;
    }

    public InetSocketAddress getRemoteAddress(){
        SocketAddress socketAddress = channelHandlerContext.channel().remoteAddress();
        if(socketAddress == null){
            return null;
        }
        if(socketAddress instanceof InetSocketAddress){
            return (InetSocketAddress) socketAddress;
        }
        return null;
    }

    public IntegrationServerProperties getConfig() {
        return config;
    }

    /**
     * 回收servlet对象
     */
    @Override
    public void recycle() {
        httpServletResponse.recycle();
        httpServletRequest.recycle();

        if(channelHandlerContext instanceof Recyclable){
            ((Recyclable) channelHandlerContext).recycle();
        }

        httpServletResponse = null;
        httpServletRequest = null;
        channelHandlerContext = null;
        servletContext = null;

        RECYCLER.recycleInstance(this);
    }

}
