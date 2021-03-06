package lr.com.wallet.activity.fragment;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;

import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import lr.com.wallet.R;
import lr.com.wallet.activity.TxInfoActivity;
import lr.com.wallet.adapter.TxAdapter;
import lr.com.wallet.adapter.TxListView;
import lr.com.wallet.dao.CacheWalletDao;
import lr.com.wallet.dao.TxCacheDao;
import lr.com.wallet.pojo.CoinPojo;
import lr.com.wallet.pojo.ETHCacheWallet;
import lr.com.wallet.pojo.TxBean;
import lr.com.wallet.pojo.TxCacheBean;
import lr.com.wallet.pojo.TxPojo;
import lr.com.wallet.pojo.TxStatusBean;
import lr.com.wallet.pojo.TxStatusResult;
import lr.com.wallet.utils.JsonUtils;
import lr.com.wallet.utils.TxComparator;
import lr.com.wallet.utils.TxStatusUtils;
import lr.com.wallet.utils.TxUtils;
import lr.com.wallet.utils.UnfinishedTxPool;

/**
 * Created by DT0814 on 2018/8/6.
 */

public class CoinInfoTxlistFragment extends Fragment implements TxListView.IRefreshListener, TxListView.ILoadMoreListener {
    private TxListView listView;
    private Handler txListViewRefreHandler;
    private ETHCacheWallet ethCacheWallet;
    private View view;
    private FragmentActivity activity;
    private CoinPojo coin;
    private Timer timer = new Timer();
    private TxPojo txPojo;

    @SuppressLint("ValidFragment")
    public CoinInfoTxlistFragment(CoinPojo coin, TxPojo txPojo) {
        this.coin = coin;
        this.txPojo = txPojo;
    }


    public CoinInfoTxlistFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = inflater.inflate(R.layout.coin_info_txlist_fragment, null);
        ethCacheWallet = CacheWalletDao.getCurrentWallet();
        activity = getActivity();
        //初始化交易
        initTransactionListView();

        return view;
    }

    @SuppressLint("HandlerLeak")
    private void initTransactionListView() {
        listView = view.findViewById(R.id.transcationList);
        listView.setIRefreshListener(this);
        listView.setILoadMoreListener(this);
        txListViewRefreHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                listView.setAdapter((ListAdapter) msg.obj);
                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        TxBean itemAtPosition = (TxBean) adapterView.getItemAtPosition(i);
                        Intent intent = new Intent(activity, TxInfoActivity.class);
                        intent.putExtra("txBean", JsonUtils.objectToJson(itemAtPosition));
                        intent.putExtra("coin", JsonUtils.objectToJson(coin));
                        startActivity(intent);
                    }
                });
                listView.refreshComplete();
            }
        };

        new Thread(new Runnable() {
            @TargetApi(Build.VERSION_CODES.N)
            @Override
            public void run() {
                try {
                    TxCacheBean txCache = TxCacheDao.getTxCache(ethCacheWallet.getId().toString(), coin.getCoinId().toString());
                    if (null == txCache || null == txCache.getData()) {
                        TxPojo pojo = getTxPojo();
                        assert pojo != null;
                        List<TxBean> result = pojo.getResult();
                        txCache = new TxCacheBean(coin.getCoinId(), ethCacheWallet.getId(), result);
                        TxCacheDao.addTxCache(txCache);
                    } else {
                        Log.d("coinInfo", "缓存命中");
                    }
                    timer.schedule(new TimerTask() {
                        @RequiresApi(api = Build.VERSION_CODES.N)
                        @Override
                        public void run() {
                            TxCacheBean txCache = TxCacheDao.getTxCache(ethCacheWallet.getId().toString()
                                    , coin.getCoinId().toString());
                            assert txCache != null;
                            int num = txCache.getNum();
                            TxPojo pojo = getTxPojo();
                            if (null == pojo || null == pojo.getResult()) {
                                return;
                            }
                            List<TxBean> data = pojo.getResult();
                            if (data.size() > num) {
                                updateListView(getDatas(txCache, data));
                            }
                        }
                    }, 0, 5000);

                    List<TxBean> data = txCache.getData();
                    updateListView(data);

                    data = getDatas(txCache, data);
                    updateListView(data);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void updateListView(List<TxBean> data) {
        TxAdapter adapter = new TxAdapter(activity
                , R.layout.tx_list_view_item
                , data
                , ethCacheWallet, coin);
        Message msg = new Message();
        msg.obj = adapter;
        txListViewRefreHandler.sendMessage(msg);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private List<TxBean> getDatas(TxCacheBean txCache, List<TxBean> data) {
        txCache.setData(data);
        txCache.setNum(data.size());
        TxCacheDao.addTxCache(txCache);
        List<TxBean> unfinishedTxByCoinid = UnfinishedTxPool.getUnfinishedTxByCoinid(coin.getCoinId().toString());
        for (int i = 0; i < unfinishedTxByCoinid.size(); i++) {
            TxBean txBean = unfinishedTxByCoinid.get(i);
            try {
                TxStatusBean txStatusByHash = TxStatusUtils.getTxStatusByHash(txBean.getHash());
                assert txStatusByHash != null;
                TxStatusResult result = txStatusByHash.getResult();
                String status = result.getStatus();
                switch (status) {
                    case "1":
                        UnfinishedTxPool.deleteUnfinishedTx(txBean, coin.getCoinId().toString());
                        data.remove(txBean);
                        break;
                    case "0":
                        List<TxBean> txCacheData = txCache.getErrData();
                        txBean.setStatus("0");
                        txCacheData.add(txBean);
                        //添加覆盖 相当于更新
                        TxCacheDao.addTxCache(txCache);
                        UnfinishedTxPool.deleteUnfinishedTx(txBean, coin.getCoinId().toString());
                        data.remove(txBean);
                        break;
                    default:
                        data.add(i, txBean);
                        break;

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (null != txCache.getErrData()) {
            data.addAll(txCache.getErrData());
        }
        data.sort(new TxComparator());
        return data;
    }

    private TxPojo getTxPojo() {
        try {
            TxPojo pojo = new TxPojo();
            if (null != txPojo) {
                pojo.setMessage(txPojo.getMessage());
                pojo.setResult(txPojo.getResult());
                pojo.setStatus(txPojo.getStatus());
                txPojo = null;
                return pojo;
            }
            if (!coin.getCoinSymbolName().equalsIgnoreCase("eth")) {
                pojo = TxUtils.getTransactionPojoByAddressAndContractAddress(
                        ethCacheWallet.getAddress(), coin.getCoinAddress());
            } else {
                pojo = TxUtils.getTransactionPojoByAddress(ethCacheWallet.getAddress());
                assert pojo != null;
                List<TxBean> result = pojo.getResult();
                Iterator<TxBean> iterator = result.iterator();
                while (iterator.hasNext()) {
                    TxBean next = iterator.next();
                    if (next.getInput().length() > 2) {
                        iterator.remove();
                    }
                }
                pojo.setResult(result);
            }
            return pojo;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void setRefreshData() {
        new Thread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void run() {
                try {
                    TxCacheBean txCache = TxCacheDao.getTxCache(ethCacheWallet.getId().toString()
                            , coin.getCoinId().toString());
                    TxPojo pojo = getTxPojo();
                    assert pojo != null;
                    List<TxBean> data = pojo.getResult();
                    updateListView(getDatas(txCache, data));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    private void setLoadData() {

    }

    @Override
    public void onRefresh() {
        //获取最新数据
        setRefreshData();
    }

    @Override
    public void onLoadMore() {
        //获取最新数据
        setLoadData();
    }
}
