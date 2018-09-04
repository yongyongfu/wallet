package lr.com.wallet.activity.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import lr.com.wallet.R;
import lr.com.wallet.activity.CreateWalletActivity;
import lr.com.wallet.activity.ImportActivity;

/**
 * Created by DT0814 on 2018/8/22.
 */

public class NoHaveWalletFragment extends Fragment {
    private LayoutInflater inflater;
    private FragmentActivity activity;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.no_have_wallet_layout, null);
        super.onCreate(savedInstanceState);
        activity = getActivity();
        this.inflater = inflater;
        view.findViewById(R.id.createBut).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(activity, CreateWalletActivity.class));
            }
        });
        view.findViewById(R.id.importBut).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(activity, ImportActivity.class));
            }
        });
        return view;
    }
}
