package com.sdt;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Environment;

public class Sdtusbapi  extends Activity  {

	/**
	 * @param args
	 */
	Common common = new Common();
	 int debug=0;//if set debug,print something to file
	 int tempdebug=0; //临时打印，NFC手机读卡打印错误消息
    UsbDeviceConnection mDeviceConnection; 	
	UsbEndpoint epOut;
	UsbEndpoint epIn;
	final String FILE_NAME="/file.txt";
	RandomAccessFile raf;
	File targetFile ;
	
	Activity instance;


	public Sdtusbapi(Activity instance) throws Exception{
		int ret = initUSB(instance);
		this.instance=instance;
		if(debug==1)
			writefile("inintUSB ret="+ret);
		if(ret!=common.SUCCESS) 
		{
			Exception e = new Exception();
			if(ret==common.ENOUSBRIGHT)
			{
				e.initCause(new Exception());
				writefile("error common.ENOUSBRIGHT");
			}
			else
			{
				e.initCause(null);	
				writefile("error null");
			}
			throw e;
		}
			 
		 
		 
	}

	
	
	
   int initUSB(Activity instance){
	
	//MainActivity may = new MainActivity();
	int ret;
	openfile();
	
	UsbManager manager;   //USB管理器
	UsbDevice mUsbDevice = null;  //找到的USB设备
	
	
	manager=(UsbManager) instance.getSystemService(Context.USB_SERVICE);

    if (manager == null) {
    	 writefile("manager == null");
    	 
        return common.EUSBMANAGER;
    } else {
    	if (debug==1)
    	{
    		writefile("usb dev：" + String.valueOf(manager.toString()));
    	}
    }
    
    HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
    if (debug==1)
	{
		
			writefile("usb dev：" + String.valueOf(deviceList.size()));
		    		
	}

    Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
    ArrayList<String> USBDeviceList = new ArrayList<String>(); // 存放USB设备的数量
    while (deviceIterator.hasNext()) {
        UsbDevice device = deviceIterator.next();

        USBDeviceList.add(String.valueOf(device.getVendorId()));
        USBDeviceList.add(String.valueOf(device.getProductId()));
        
        // 在这里添加处理设备的代码
        if (device.getVendorId() == 1024 && device.getProductId() == 50010) {
        	   	mUsbDevice = device;
            if (debug==1)
        	{
        		writefile("zhangmeng:find device!");
        	}
           
        }
    }
    
   ret = findIntfAndEpt(manager,mUsbDevice);
   return ret;
	
}


int usbsendrecv(
		byte [] pucSendData,/*the data buffer to be send*/ 
		int uiSendLen, /*the len of send data buffer*/
		byte []RecvData, /* the data buffer for recv data*/ 
		int[] puiRecvLen/*the len of recv data*/
		)
{
	int iFD = 0;
	int iLen;
	int iIter;
	int iRet;
	
	
	Boolean bRet = null;
	byte ucCheck = 0;
	byte[] ucRealSendData = new byte[4096];
	byte[] pucBufRecv = new byte[4096];
	int [] iOffset = new int[1];
	
	//for(int i=0;i<ucRealSendData.length;i++)
	//	ucRealSendData[i]= (byte) 0xaa;

		
	if(4096-5 < uiSendLen)
		return -1;
	
	if(-1 == iFD)
	{
		iRet = common.ENOOPEN;
		return iRet;
	}
	iLen = (pucSendData[0] << 8) + pucSendData[1];

	ucRealSendData[0] = ucRealSendData[1] = ucRealSendData[2] = (byte) 0xaa;
	ucRealSendData[3] = (byte) 0x96;
	ucRealSendData[4] = 0x69;
	for(iIter = 0; iIter < iLen+1; iIter++)
		ucCheck ^= pucSendData[iIter];
	
	for(int i=0;i<iLen+2;i++)
	{
		ucRealSendData[i+5]=pucSendData[i];
	}
		
	ucRealSendData[iLen+6] = ucCheck;
	
	int uiSizeSend = iLen + 2 + 5;
	int uiSizeRecv = 0;
	iRet= mDeviceConnection.bulkTransfer(epOut, ucRealSendData, uiSizeSend, 5000); 
	tempwritefile("before uiSizeRecv error iRet="+iRet);
	writefile("before uiSizeRecv error iRet="+iRet);
	
	uiSizeRecv=mDeviceConnection.bulkTransfer(epIn,pucBufRecv,pucBufRecv.length, 5000); //通过手机NFC读身份证，有指纹信息时，超时时间设长为5000，原来1000
	if((5 > uiSizeRecv) || (4096 <= uiSizeRecv))
	{
		iRet = common.EDATALEN;
		tempwritefile("pucBufRecv ="+pucBufRecv);
		tempwritefile("uiSizeRecv error ="+uiSizeRecv);
		writefile("uiSizeRecv error ="+uiSizeRecv);
		return iRet;
	}
	
	bRet = Usb_GetDataOffset(pucBufRecv,iOffset);
	if(!bRet)
	{
		iRet = common.EDATAFORMAT;
		writefile("iRet = EDATAFORMAT ="+bRet+"iOffset= "+iOffset);
		return iRet;
	}
	
	//Length that not contains Len&Crc
	iLen = (pucBufRecv[iOffset[0]+4] << 8) + pucBufRecv[iOffset[0]+5];
	if((4096-7) < iLen)
	{
		iRet = common.EDATALEN;
		tempwritefile("pucBufRecv= "+pucBufRecv);
		tempwritefile("iRet = EDATALEN = "+iLen);
		writefile("iRet = EDATALEN = "+iLen);
		return iRet;
		
	}
	byte[] tempData = new byte[4096];
	for(int i=0;i<pucBufRecv.length-iOffset[0]-4;i++)
		tempData[i]=pucBufRecv[i+iOffset[0] + 4];
	
	bRet = Usb_CheckChkSum(iLen + 2,tempData);
	if(!bRet)
	{
		iRet = common.EPCCRC;
		writefile("iRet = EPCCRC");
		return iRet;
	}
	for(int i=0;i<iLen+1;i++)
		RecvData[i]=pucBufRecv[i+iOffset[0] + 4];
	
	puiRecvLen[0] = iLen + 1;
	writefile("stdapi.puiRecvLen ="+(iLen + 1));
	
	return common.SUCCESS;
	
}

boolean Usb_GetDataOffset(byte [] dataBuffer,int []iOffset)
{
	int iIter;
	iOffset[0] = 0;
	for(iIter = 0; iIter < 7; iIter ++)
	{
		if((dataBuffer[iIter+0] == (byte)0xaa) &&
			(dataBuffer[iIter+1] == (byte)0xaa) &&
			(dataBuffer[iIter+2] == (byte)0x96) &&
			(dataBuffer[iIter+3] == (byte)0x69))
		break;
	}
	
	if(7 <= iIter)
		return false;
	iOffset[0] = iIter;
	
	return true;
}

static boolean Usb_CheckChkSum(int uiDataLen,byte[] pucRecvData)
{
	int iIter;
	byte ucCheck = 0;
	for(iIter = 0; iIter < uiDataLen-1; iIter++)
	{
		ucCheck ^= pucRecvData[iIter];
	}
	if(ucCheck != pucRecvData[uiDataLen-1])
		return false;
	return true;
}




private void openfile()
{
	if(this.debug==1 || this.tempdebug==1)
	{
		//获取SD卡目录
		File sdCardDir = Environment.getExternalStorageDirectory();
		
		try {
			setTargetFile(new File(sdCardDir.getCanonicalPath()+this.FILE_NAME));
		
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		try {
			setFile(new RandomAccessFile(this.targetFile,"rw"));
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		writefile("in open file()");
	}
}
public void writefile(String context) 
{
	if(this.debug==1&&Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
	{
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
		
		
		try {
			this.raf.seek(this.targetFile.length());
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			this.raf.writeChars("\n"+sdf.format(new Date())+" "+context);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
}
public void tempwritefile(String context) 
{
	if(this.tempdebug==1&&Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
	{
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
		
		
		try {
			this.raf.seek(this.targetFile.length());
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			this.raf.writeChars("\n"+sdf.format(new Date())+" "+context);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
}
private void closefile()
{
	if(this.debug==1&&this.raf!=null)
	{
		try {
			this.raf.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

   // 寻找接口和分配结点
private int findIntfAndEpt(final UsbManager manager,final UsbDevice mUsbDevice) {
	
	UsbInterface mInterface = null;
	//UsbDeviceConnection mDeviceConnection;
	
    if (mUsbDevice == null) {
    	writefile("zhangmeng:no device found");
        return common.EUSBDEVICENOFOUND;
    }
    
    for (int i = 0; i < mUsbDevice.getInterfaceCount();) {        
        UsbInterface intf = mUsbDevice.getInterface(i);       
        mInterface = intf;
        break;
    }

    if (mInterface != null) {
        UsbDeviceConnection connection = null;
        // 判断是否有权限
        if(manager.hasPermission(mUsbDevice)) {
          
            connection = manager.openDevice(mUsbDevice); 
            if (connection == null) {
            	 return common.EUSBCONNECTION;
            }
            if (connection.claimInterface(mInterface, true)) {
            	
                mDeviceConnection = connection;              
                getEndpoint(mDeviceConnection,mInterface);
            } else {
                connection.close();
            }
        } else {
        	writefile("zhangmeng:no rights");
        	new Thread()
        	{
        		public void run()
        		{
        			PendingIntent  pi = PendingIntent.getBroadcast(instance, 0, new Intent(common.ACTION_USB_PERMISSION), 0);
                	manager.requestPermission(mUsbDevice, pi);
        		}
        	}.start();
        	
        	return common.ENOUSBRIGHT;
        }
    }
    else {
    		writefile("zhangmeng:no interface");
    		return common.ENOUSBINTERFACE;
    }
	return common.SUCCESS;
}



//用UsbDeviceConnection 与 UsbInterface 进行端点设置和通讯
private void getEndpoint(UsbDeviceConnection connection, UsbInterface intf) {
    if (intf.getEndpoint(1) != null) {
    	this.epOut = intf.getEndpoint(1);
    }
    if (intf.getEndpoint(0) != null) {
    	 this.epIn = intf.getEndpoint(0);
    }
}

  


private void setFile(RandomAccessFile raf)
{
	this.raf = raf;
}
private void setTargetFile(File f)
{
	this.targetFile = f;
}


}
