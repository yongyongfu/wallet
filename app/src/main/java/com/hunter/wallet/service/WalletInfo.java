package com.hunter.wallet.service;

import android.util.Log;

import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

public class WalletInfo {

    private int id;
    private String name;
    private byte[] pubkey;


    @Override
    public String toString() {
        return "WalletInfo{name='" + name + '\'' + ", id=" + id + ", pubkey=" + Numeric.toHexString(pubkey) + ", address=" + getAddr() + '}';
    }

    public WalletInfo() {
    }

    public WalletInfo(int id, String name, byte[] pubkey) {
        this.id = id;
        this.name = name;
        this.pubkey = pubkey;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public byte[] getPubkey() {
        return pubkey;
    }

    public void setPubkey(byte[] pubkey) {
        this.pubkey = pubkey;
    }

    public String getAddr() {
        StringBuffer sb = new StringBuffer();
        for (byte b : pubkey) {
            sb.append(b);
        }
        Log.i("pubkey", sb.toString());
        return Numeric.toHexString(Keys.getAddress(pubkey));
    }
}
