package lr.com.wallet.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import lr.com.wallet.R;
import lr.com.wallet.adapter.CoinAdapter;
import lr.com.wallet.dao.CoinDao;
import lr.com.wallet.dao.TxCacheDao;
import lr.com.wallet.dao.WalletDao;
import lr.com.wallet.pojo.CoinPojo;
import lr.com.wallet.pojo.ETHPriceResult;
import lr.com.wallet.pojo.ETHWallet;
import lr.com.wallet.pojo.TxBean;
import lr.com.wallet.pojo.TxCacheBean;
import lr.com.wallet.pojo.TxStatusBean;
import lr.com.wallet.pojo.TxStatusResult;
import lr.com.wallet.utils.CoinUtils;
import lr.com.wallet.utils.DateUtils;
import lr.com.wallet.utils.HTTPUtils;
import lr.com.wallet.utils.JsonUtils;
import lr.com.wallet.utils.TxStatusUtils;
import lr.com.wallet.utils.UnfinishedTxPool;
import lr.com.wallet.utils.Web3jUtil;

@SuppressLint("NewApi")
public class HomeFragment extends Fragment implements View.OnClickListener {
    private FragmentActivity activity;
    private Activity baseActivity;
    private ETHWallet ethWallet;
    private TextView ethNum;
    private View view;
    private ListView coinListView;
    private Handler coinListViewHandler;
    private List<CoinPojo> coinPojos;
    private Timer timer;
    private LayoutInflater inflater;
    private AlertDialog.Builder alertbBuilder;
    private AlertDialog alertDialog;

    public HomeFragment() {

    }

    @SuppressLint("ValidFragment")
    public HomeFragment(Activity baseActivity) {
        this();
        this.baseActivity = baseActivity;
        Log.i("父布局", baseActivity.toString());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.home_fragment, null);
        super.onCreate(savedInstanceState);
        activity = getActivity();
        this.inflater = inflater;
        alertbBuilder = new AlertDialog.Builder(activity);
        ethNum = view.findViewById(R.id.ethNum);

        ethWallet = WalletDao.getCurrentWallet();
        if (null == ethWallet) {
            startActivity(new Intent(activity, CreateWalletActivity.class));
            return null;
        }
        View toAddressLayout = view.findViewById(R.id.toAddressLayout);
        toAddressLayout.setOnClickListener(this);
        ImageButton addCoinBut = view.findViewById(R.id.addCoinBut);
        addCoinBut.setOnClickListener(this);
        TextView walletName = view.findViewById(R.id.walletName);
        walletName.setText(ethWallet.getName());
        TextView homeShowAddress = view.findViewById(R.id.homeShowAddress);
        homeShowAddress.setText(ethWallet.getAddress());
        ethNum.setText(ethWallet.getBalance());
        new Thread(new Runnable() {
            @Override
            public void run() {
                String s = null;
                try {
                    s = Web3jUtil.ethGetBalance(ethWallet.getAddress());
                    ethWallet.setNum(new BigDecimal(s));
                    WalletDao.writeCurrentJsonWallet(ethWallet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        return view;
    }


    @Override
    public void onPause() {
        super.onPause();
        System.out.println("onPause______HomeFragment");
        timer.cancel();
    }

    @Override
    public void onStop() {
        super.onStop();
        timer.cancel();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        timer.cancel();
    }

    @Override
    public void onResume() {
        super.onResume();
        System.out.println("onResume______HomeFragment");
        initCoinListView();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.i("定时调度", "定时调度" + DateUtils.getDateFormatByString(new Date().getTime()));
                coinPojos = CoinDao.getConinListByWalletId(ethWallet.getId());
                for (CoinPojo coinPojo : coinPojos) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            List<TxBean> unfinishedTxByCoinid =
                                    UnfinishedTxPool.getUnfinishedTxByCoinid(coinPojo.getCoinId().toString());
                            unfinishedTxByCoinid.forEach((v) -> {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            TxStatusBean txStatusByHash = TxStatusUtils.getTxStatusByHash(v.getHash());
                                            TxStatusResult result = txStatusByHash.getResult();
                                            String status = result.getStatus();
                                            if (status.equals("1")) {
                                                UnfinishedTxPool.deleteUnfinishedTx(v, coinPojo.getCoinId().toString());
                                            } else if (status.equals("0")) {
                                                TxCacheBean txCache = TxCacheDao.getTxCache(ethWallet.getId().toString()
                                                        , coinPojo.getCoinId().toString());
                                                List<TxBean> data = txCache.getErrData();
                                                v.setStatus("0");
                                                data.add(v);
                                                UnfinishedTxPool.deleteUnfinishedTx(v, coinPojo.getCoinId().toString());
                                                //添加覆盖 相当于更新
                                                TxCacheDao.addTxCache(txCache);
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }).start();
                            });
                        }
                    }).start();

                    //不是以太币
                    if (!coinPojo.getCoinSymbolName().equalsIgnoreCase("ETH")) {
                        if (coinPojo.getCoinAddress().length() < 10) {
                            continue;
                        }
                        String balanceOf = CoinUtils.getBalanceOf(coinPojo.getCoinAddress(), ethWallet.getAddress());
                        if (!balanceOf.equalsIgnoreCase(coinPojo.getCoinAddress())) {
                            coinPojo.setCoinCount(balanceOf);
                            CoinDao.updateCoinPojo(coinPojo);
                        }
                    } else {
                        try {
                            String s = Web3jUtil.ethGetBalance(ethWallet.getAddress());

                            ETHPriceResult price = HTTPUtils.ETHPriceResult(
                                    "http://fxhapi.feixiaohao.com/public/v1/ticker?code=ethereum&convert=CNY"
                                    , ETHPriceResult.class);

                            if (null == price || null == s || s.equals("") || s.equals("0")) {
                                return;
                            }
                            String ethusd = price.getPrice_cny();
                            BigDecimal dollar = new BigDecimal(ethusd);
                            BigDecimal ethNum = new BigDecimal(s);
                            BigDecimal balance = dollar.multiply(ethNum);
                            String balanceStr = balance.toString();
                            if (balanceStr.indexOf(".") != -1 && balanceStr.indexOf(".") + 5 < balanceStr.length()) {
                                balanceStr = balanceStr.substring(0, balanceStr.indexOf(".") + 5);
                            }
                            if (s.indexOf(".") != -1 && s.indexOf(".") + 5 < s.length()) {
                                s = s.substring(0, s.indexOf(".") + 5);
                            }

                            if (!coinPojo.getCoinCount().equalsIgnoreCase(s)) {
                                coinPojo.setCoinCount(s);
                                CoinDao.updateCoinPojo(coinPojo);
                            }
                            Message ms = new Message();
                            ms.obj = balanceStr;
                            ethNumHandler.sendMessage(ms);
                            ethWallet.setBalance(balanceStr);
                            ethWallet.setNum(new BigDecimal(s));
                            WalletDao.writeCurrentJsonWallet(ethWallet);
                            WalletDao.writeJsonWallet(ethWallet);

                            coinPojo.setCoinBalance(balanceStr);
                            CoinDao.updateCoinPojo(coinPojo);
                            updataCoinListView(coinPojos);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                updataCoinListView(coinPojos);
            }
        }, 0, 20000);
    }


    Handler ethNumHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String model = (String) msg.obj;
            ethNum.setText(model);
        }
    };


    private void initCoinListView() {
        coinListView = (ListView) view.findViewById(R.id.coinListView);
        coinListViewHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                ListAdapter obj = (ListAdapter) msg.obj;
                coinListView.setAdapter(obj);
                coinListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        CoinPojo itemAtPosition = (CoinPojo) adapterView.getItemAtPosition(i);
                        Intent intent = new Intent(activity, CoinInfoActivity.class);
                        intent.putExtra("obj", JsonUtils.objectToJson(itemAtPosition));
                        startActivity(intent);
                    }
                });
            }

        };
        coinPojos = CoinDao.getConinListByWalletId(ethWallet.getId());
        updataCoinListView(coinPojos);
    }

    private void updataCoinListView(List<CoinPojo> coinPojos) {
        List<CoinPojo> list = new ArrayList(coinPojos);
        list.sort(new Comparator<CoinPojo>() {
            @Override
            public int compare(CoinPojo coinPojo, CoinPojo t1) {
                return coinPojo.getCoinId().intValue() - t1.getCoinId().intValue();
            }
        });
        CoinAdapter adapter = new CoinAdapter(activity, R.layout.coin_list_view, list);
        Message msg = new Message();
        msg.obj = adapter;
        coinListViewHandler.sendMessage(msg);
    }

    @Override
    public void onClick(View view) {

        switch (view.getId()) {
            case R.id.addCoinBut:


                alertbBuilder.setTitle("代币添加").setMessage("").setPositiveButton("地址添加",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                View showMnemonicLayout = inflater.inflate(R.layout.input_coin_address_layout, null);
                                EditText editText = showMnemonicLayout.findViewById(R.id.inCoinAddressBut);
                                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                                builder.setView(showMnemonicLayout);
                                builder.setTitle("输入代币地址")
                                        .setMessage("")
                                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface z, int which) {
                                                String address = editText.getText().toString();
                                                if (null == address || address.trim().length() != 42) {
                                                    Toast.makeText(activity, "代币地址为以0x开头的长度为42的字符串", Toast.LENGTH_LONG).show();
                                                    return;
                                                }
                                                if (CoinDao.CheckContains(address, ethWallet.getId())) {
                                                    Toast.makeText(activity, "当前钱包已存在该代币请勿重复添加", Toast.LENGTH_LONG).show();
                                                    return;
                                                }
                                                CoinPojo coinPojo = CoinDao.addCoinPojo(address, ethWallet.getAddress());
                                                if (null == coinPojo) {
                                                    Toast.makeText(activity, "未查询到代币", Toast.LENGTH_LONG).show();
                                                } else {
                                                    Toast.makeText(activity, "添加成功", Toast.LENGTH_LONG).show();
                                                    Intent intent = new Intent(activity, MainFragmentActivity.class);
                                                    startActivity(intent);
                                                }
                                            }
                                        })
                                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.cancel();
                                            }
                                        }).create();
                                builder.show();
                            }
                        })
                        .setNegativeButton("选择添加", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Intent intent = new Intent(activity, CoinAddActivity.class);
                                intent.putExtra("CoinPojos", JsonUtils.objectToJson(coinPojos));
                                startActivity(intent);
                            }
                        }).create();
                alertbBuilder.show();
                break;
            case R.id.toAddressLayout:
                startActivity(new Intent(activity, AddressShowActivity.class));
                break;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }


}
