package com.open.net.client.impl.udp.bio;

import com.open.net.client.impl.udp.bio.processor.SocketProcessor;
import com.open.net.client.structures.IConnectListener;
import com.open.net.client.structures.UdpAddress;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * author       :   Administrator
 * created on   :   2017/12/4
 * description  :
 */

public class UdpBioConnector {

    private final int STATE_CLOSE			= 0x1;//连接关闭
    private final int STATE_CONNECT_START	= 0x2;//连接开始
    private final int STATE_CONNECT_SUCCESS	= 0x3;//连接成功

    private UdpAddress[]    mAddress = null;
    private int             mConnectIndex = -1;
    private int             state = STATE_CLOSE;

    private UdpBioClient mClient;
    private IConnectListener mIConnectListener;
    private SocketProcessor  mSocketProcessor;

    private IUdpBioConnectListener mProxyConnectStatusListener = new IUdpBioConnectListener() {

        @Override
        public void onConnectSuccess(SocketProcessor mSocketProcessor, DatagramSocket mSocket, DatagramPacket mWriteDatagramPacket, DatagramPacket mReadDatagramPacket) {
            if(mSocketProcessor != UdpBioConnector.this.mSocketProcessor){//两个请求都不是同一个，说明是之前连接了，现在重连了
                SocketProcessor dropProcessor = mSocketProcessor;
                if(null != dropProcessor){
                    dropProcessor.close();
                }
                return;
            }

            if(null !=mIConnectListener ){
                mIConnectListener.onConnectionSuccess();
            }

            state = STATE_CONNECT_SUCCESS;
            mClient.init(mSocket,mWriteDatagramPacket,mReadDatagramPacket);
        }

        @Override
        public synchronized void onConnectFailed(SocketProcessor mSocketProcessor) {
            if(mSocketProcessor != UdpBioConnector.this.mSocketProcessor){//两个请求都不是同一个，说明是之前连接了，现在重连了
                SocketProcessor dropProcessor = mSocketProcessor;
                if(null != dropProcessor){
                    dropProcessor.close();
                }
                return;
            }

            if(null !=mIConnectListener ){
                mIConnectListener.onConnectionFailed();
            }

            state = STATE_CLOSE;
            connect();//try to connect next ip port
        }
    };

    public UdpBioConnector(UdpBioClient mClient, IConnectListener mIConnectListener) {
        this.mClient = mClient;
        this.mIConnectListener = mIConnectListener;
    }

    //-------------------------------------------------------------------------------------------
    private boolean isConnected(){
        return state == STATE_CONNECT_SUCCESS;
    }

    private boolean isConnecting(){
        return state == STATE_CONNECT_START;
    }

    private boolean isClosed(){
        return state == STATE_CLOSE;
    }

    //-------------------------------------------------------------------------------------------
    public void setConnectAddress(UdpAddress[] tcpArray ){
        this.mConnectIndex = -1;
        this.mAddress = tcpArray;
    }

    public synchronized void connect() {
        startConnect();
    }

    public synchronized void reconnect(){
        stopConnect();
        //reset the ip/port mConnectIndex of mAddress
        if(mConnectIndex +1 >= mAddress.length || mConnectIndex +1 < 0){
            mConnectIndex = -1;
        }
        startConnect();
    }

    public synchronized void disconnect(){
        stopConnect();
    }

    //-------------------------------------------------------------------------------------------
    public void checkConnect() {
        //1.没有连接,需要进行重连
        //2.在连接不成功，并且也不在重连中时，需要进行重连;
        if(null == mSocketProcessor){
            startConnect();
        }else if(!isConnected() && !isConnecting()){
            startConnect();
        }else{
            if(isConnected()){
                mSocketProcessor.wakeUp();
            }else{
                //说明正在重连中
            }
        }
    }

    private void startConnect() {
        //非关闭状态(连接成功，或者正在重连中)
        if(!isClosed()){
            return;
        }

        mConnectIndex++;
        if(mConnectIndex < mAddress.length && mConnectIndex >= 0){
            state = STATE_CONNECT_START;
            mSocketProcessor = new SocketProcessor(mAddress[mConnectIndex].ip, mAddress[mConnectIndex].port,mClient,mProxyConnectStatusListener);
            mSocketProcessor.start();
        }else{
            mConnectIndex = -1;

            //循环连接了一遍还没有连接上，说明网络连接不成功，此时清空消息队列，防止队列堆积
            mClient.clearUnreachableMessages();
        }
    }

    private void stopConnect() {
        state = STATE_CLOSE;
        mClient.onClose();

        if(null != mSocketProcessor) {
            mSocketProcessor.close();
            mSocketProcessor = null;
        }
    }

}
