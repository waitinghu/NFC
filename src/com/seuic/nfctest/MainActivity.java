package com.seuic.nfctest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener{

    public static final int BLOCK_LENGTH = 16;
    
    public static final int STATE_READ = 1;
    public static final int STATE_WRITE =2;
    
    public NfcAdapter adapter;
    private PendingIntent pendIntent;
    public static String[][] TECHLISTS;
    public static IntentFilter[] FILTERS;
    
    
    private EditText mReadResult;
    private EditText mWriteContent;
    private Button mWrite;
    
    private int mState;
    
    static {
        try {
            TECHLISTS = new String[][] { { MifareClassic.class.getName() },
                    { NfcA.class.getName() }, };
            FILTERS = new IntentFilter[] { new IntentFilter(
                    NfcAdapter.ACTION_TECH_DISCOVERED, "*/*") };
        } catch (Exception e) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mState = STATE_READ;
        mReadResult =  (EditText) findViewById(R.id.read_result);
        mWrite = (Button) findViewById(R.id.write_button);
        mWrite.setOnClickListener(this);
        mWriteContent = (EditText) findViewById(R.id.write_content);
        
        //获取NFC适配器
        adapter = NfcAdapter.getDefaultAdapter(this);
        pendIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.enableForegroundDispatch(this, pendIntent, FILTERS, TECHLISTS);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        adapter.disableForegroundDispatch(this);
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        
        if(mState == STATE_READ) {
            String result = readCard(intent);
            mReadResult.setText(result);
        } else if (mState == STATE_WRITE){
            String content = mWriteContent.getText().toString();
            ArrayList<byte[]> ss = StringTobyte(content);
            try {
                writeCard(intent,ss);
                Toast.makeText(this, "写入成功", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "写入失败", Toast.LENGTH_SHORT).show();
            }
            mState = STATE_READ;
        }
    }
    
    
    
    public ArrayList<byte[]> readCardBytes(Intent intent) {
        Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        MifareClassic mfc = MifareClassic.get(tagFromIntent);
        //用于存放NFC卡所的块信息  1K卡共有64块
        ArrayList<byte[]> result = new ArrayList<byte[]>(); 
        try {
            if (!mfc.isConnected()) {
                mfc.connect();
            }
            for (int i = 0; i < mfc.getSectorCount(); i++) {
                if (mfc.authenticateSectorWithKeyA(i, MifareClassic.KEY_DEFAULT)|| mfc.authenticateSectorWithKeyB(i,MifareClassic.KEY_DEFAULT)) {
                    int bCount = mfc.getBlockCountInSector(i);
                    int bIndex = mfc.sectorToBlock(i);
                    for (int j = 0; j < bCount; j++) {
                        byte[] readData = mfc.readBlock(bIndex);
                        result.add(readData);
                        bIndex ++;
                    }
                } else {
                    throw new RuntimeException("authenticate Sector:"+i+"failed with key" + MifareClassic.KEY_DEFAULT);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("read NFC card fail :" + e);
        } finally {
            try {
                if(mfc != null) {
                    mfc.close();
                }
            } catch (IOException e) {
                //close 
                e.printStackTrace();
            }
        }
        return result;
    }
    
    
    private String readCard(Intent intent) {
        ArrayList<byte[]> result = readCardBytes(intent);
        String read = "";
        try {
            //读取第一块，并转化成字符串。
            read = new String(result.get(1),"utf-8").trim();
        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
        }
        int lenth = Integer.parseInt(read);
        int blockNum = (int) Math.ceil(lenth/16.0);
        byte[] message = new byte[blockNum*BLOCK_LENGTH];
        for (int i = 0 , conunt = 0; i < result.size() && conunt <= blockNum; i++) {
            
            //第0,1块不读，每个分区的最后一块不读
            if(i%4 == 3 || i == 0 || i == 1) {
                continue;
            }
            
            //读取第i块的数据放入temp
            byte[] temp = result.get(i);
            if(conunt < blockNum) {
                for (int j = 0; j < temp.length; j++) {
                    message[BLOCK_LENGTH*conunt+j] = temp[j];
                }
            }
            conunt ++;
        }
        
        String finalResult = null;
        try {
            finalResult = new String(message,"utf-8").trim();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return finalResult;
    }

    @Override
    public void onClick(View v) {
        mState = STATE_WRITE;
        Toast.makeText(this, "将卡片放入读卡区域", Toast.LENGTH_SHORT).show();
    }
    
    
    private void writeCard(Intent intent, ArrayList<byte[]> writeContent) throws IOException {
        
        Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        MifareClassic mfc = MifareClassic.get(tagFromIntent);
        mfc.connect();
        int length = writeContent.size();
        if (mfc.authenticateSectorWithKeyA(0, MifareClassic.KEY_DEFAULT)|| mfc.authenticateSectorWithKeyB(0,MifareClassic.KEY_DEFAULT)) {
            byte[] writeLenth = new byte[16];
            byte[] contentlenth = Integer.toString(length*16).getBytes();
            for (int j = 0; j < contentlenth.length; j++) {
                writeLenth[j] = contentlenth[j];
            }
            mfc.writeBlock(1, writeLenth);;
        }
        int blockIndex = 0;
        for (int j = 0; j < length; j++) {
            blockIndex = getNextBlock(j);
                if (mfc.authenticateSectorWithKeyA(blockIndex/4, MifareClassic.KEY_DEFAULT)|| mfc.authenticateSectorWithKeyB(blockIndex/4,MifareClassic.KEY_DEFAULT)) {
                    //第0,1块不写，每个分区的最后一块不写
                    mfc.writeBlock(blockIndex, writeContent.get(j));
                }
        }
    }
    
    private int getNextBlock(int j) {
        return LOOKUPTABLE[j];
    }
    private static int[] LOOKUPTABLE = {2,4,5,6,8,9,10,12,13,14,16,17,18,20,21,22,24,25,26,28,29,30,32,33,34,36,37,38,40,41,42,44,45,46,48,49,50,52,53,54,56,57,58,60,61,62};
    
    private ArrayList<byte[]> StringTobyte(String content) {
        if(content == null) {
            return null;
        }
        byte[] byteString = content.getBytes();
        int length = byteString.length;
        int needBlock = (int) Math.ceil(length/16.0);
        ArrayList<byte[]> result = new ArrayList<>();
        byte[] temp = new byte[BLOCK_LENGTH];
        for (int i = 0; i < needBlock; i++) {
            for (int j = 0; j < BLOCK_LENGTH; j++) {
                if(i*BLOCK_LENGTH + j > length-1){
                    break;
                }
                temp[j] = byteString[i*BLOCK_LENGTH + j];
            }
            result.add(temp);
            temp = new byte[BLOCK_LENGTH];
        }
        return result;
    }
    
}
