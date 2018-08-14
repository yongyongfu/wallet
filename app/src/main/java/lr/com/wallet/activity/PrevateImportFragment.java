package lr.com.wallet.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import lr.com.wallet.R;
import lr.com.wallet.dao.CoinDao;
import lr.com.wallet.dao.WalletDao;
import lr.com.wallet.pojo.CoinPojo;
import lr.com.wallet.pojo.ETHWallet;
import lr.com.wallet.utils.AppFilePath;
import lr.com.wallet.utils.ETHWalletUtils;

/**
 * Created by DT0814 on 2018/8/14.
 */

public class PrevateImportFragment extends Fragment {
    private FragmentActivity activity;
    private View view;
    private Context context;
    private EditText importInPut;
    private EditText passWord;
    private EditText reImportPassword;
    private EditText importWalletName;
    private ETHWallet ethWallet;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.private_import_fragment, null);
        super.onCreate(savedInstanceState);
        activity = getActivity();
        context = activity.getBaseContext();
        AppFilePath.init(context);

        importInPut = view.findViewById(R.id.importInPut);
        passWord = view.findViewById(R.id.importPassword);
        reImportPassword = view.findViewById(R.id.reImportPassword);
        importWalletName = view.findViewById(R.id.importWalletName);

        Button button = view.findViewById(R.id.importButton);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String passString = passWord.getText().toString();
                String repassString = reImportPassword.getText().toString();
                if (passString.trim().length() <= 5) {
                    Toast.makeText(activity, "密码长度不能小于6位!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (importWalletName.getText().toString().trim().length() <= 0) {
                    Toast.makeText(activity, "钱包名不能为空!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!passString.equals(repassString)) {
                    Toast.makeText(activity, "两次密码输入不一致!", Toast.LENGTH_SHORT).show();
                    return;
                }
                ethWallet = ETHWalletUtils.loadWalletByPrivateKey("0x" + importInPut.getText().toString(),
                        passString,
                        importWalletName.getText().toString());
                if (null == ethWallet) {
                    Toast.makeText(activity, "导入失败请检查你的私钥!", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (WalletDao.CheckContains(ethWallet)) {
                    Toast.makeText(activity, "当前钱包已存在!", Toast.LENGTH_LONG).show();
                    return;
                }

                WalletDao.writeCurrentJsonWallet(ethWallet);
                WalletDao.writeJsonWallet(ethWallet);
                CoinPojo coinPojo = CoinDao.writeETHConinPojo();
                startActivity(new Intent(activity, MainFragmentActivity.class));
            }
        });
        return view;
    }
}