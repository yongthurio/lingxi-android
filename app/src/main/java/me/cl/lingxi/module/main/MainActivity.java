package me.cl.lingxi.module.main;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.view.KeyEvent;
import android.view.MenuItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.rong.imkit.RongIM;
import io.rong.imlib.model.UserInfo;
import me.cl.library.base.BaseActivity;
import me.cl.library.utils.BottomNavigationViewHelper;
import me.cl.lingxi.R;
import me.cl.lingxi.common.config.Api;
import me.cl.lingxi.common.config.Constants;
import me.cl.lingxi.common.okhttp.OkUtil;
import me.cl.lingxi.common.okhttp.ResultCallback;
import me.cl.lingxi.common.util.SPUtil;
import me.cl.lingxi.common.util.Utils;
import me.cl.lingxi.common.view.MoeToast;
import me.cl.lingxi.entity.AppVersion;
import me.cl.lingxi.entity.Result;
import okhttp3.Call;

public class MainActivity extends BaseActivity implements RongIM.UserInfoProvider {

    @BindView(R.id.bottom_navigation)
    BottomNavigationView mBottomNavigation;

    private FragmentManager mFragmentManager;
    private HomeFragment mHomeFragment;
    private DliFragment mDliFragment;
    private FeedFragment mFeedFragment;
    private MessageFragment mMessageFragment;
    private MineFragment mMineFragment;

    private String mExit = "MM";
    private long mExitTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        init();
    }

    private void init() {
        RongIM.setUserInfoProvider(this, true);
        RongIM.getInstance().setMessageAttachedUserInfo(true);

        initFragment();
        initBottomNavigation();

        int num = this.getIntent().getFlags();

        if (isCheckUpdate()) {
            checkNewVersion();
        }
    }

    private void initFragment() {
        mFragmentManager = getSupportFragmentManager();
        mHomeFragment = new HomeFragment();
        mDliFragment = new DliFragment();
        mFeedFragment = FeedFragment.newInstance("home");
        mMessageFragment = new MessageFragment();
        mMineFragment = new MineFragment();
        switchFragment(mDliFragment);
    }

    //底部导航
    private void initBottomNavigation() {
        BottomNavigationViewHelper.disableShiftMode(mBottomNavigation);
        mBottomNavigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.navigation_home:
                        switchFragment(mDliFragment);
                        return true;
                    case R.id.navigation_camera:
                        switchFragment(mFeedFragment);
                        return true;
                    case R.id.navigation_interactive:
                        switchFragment(mMessageFragment);
                        return true;
                    case R.id.navigation_mine:
                        switchFragment(mMineFragment);
                        return true;
                }
                return false;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (resultCode) {
            case Constants.ACTIVITY_PUBLISH:
                int id = data.getIntExtra(Constants.GO_INDEX, R.id.navigation_home);
                // 非导航本身事件，手动切换
                mBottomNavigation.setSelectedItemId(id);
                break;
            case Constants.ACTIVITY_PERSONAL:
                mMineFragment.onActivityResult(requestCode, resultCode, data);
                break;
            default:
                break;
        }
    }

    private Fragment currentFragment;

    /**
     * 切换Fragment
     */
    private void switchFragment(Fragment targetFragment) {
        FragmentTransaction transaction = mFragmentManager.beginTransaction();
        if (!targetFragment.isAdded()) {
            //首次currentFragment为null
            if (currentFragment != null) {
                transaction.hide(currentFragment);
            }
            transaction.add(R.id.fragment_container, targetFragment, targetFragment.getClass().getName());
        } else {
            transaction.hide(currentFragment).show(targetFragment);
        }
        currentFragment = targetFragment;
        transaction.commitAllowingStateLoss();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if ((System.currentTimeMillis() - mExitTime) > 2000) {
                MoeToast.makeText(this, "(ಥ _ ಥ)你难道要再按一次离开我么");
                mExitTime = System.currentTimeMillis();
            } else {
                int x = (int) (Math.random() * 10) + 1;
                if ("MM".equals(mExit)) {
                    if (x == 10) {
                        MoeToast.makeText(this, "恭喜你找到隐藏的偶，Game over!");
                        finish();
                    } else {
                        MoeToast.makeText(this, "你果然想要离开我(＠￣ー￣＠)");
                    }
                    mExitTime = System.currentTimeMillis();
                    mExit = "mm";
                } else if ("mm".equals(mExit)) {
                    mExit = "MM";
                    finish();
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public UserInfo getUserInfo(String s) {
        for (UserInfo userInfo : Constants.userList) {
            if (userInfo.getUserId().equals(s)) {
                return userInfo;
            }
        }
        return null;
    }

    // 是否提示更新
    private boolean isCheckUpdate() {
        int updateFlag = SPUtil.build().getInt(Constants.UPDATE_FLAG);
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd", Locale.SIMPLIFIED_CHINESE);
        int dateInt = Integer.parseInt(sdf.format(new Date()));
        return (dateInt + 1) != updateFlag;
    }

    // 保存updateFlag
    private void saveUpdateFlag(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd", Locale.SIMPLIFIED_CHINESE);
        int dateInt = Integer.parseInt(sdf.format(new Date()));
        SPUtil.build().putInt(Constants.UPDATE_FLAG, (dateInt + 1));
    }

    // 检查新版本
    private void checkNewVersion() {
        OkUtil.post()
                .url(Api.latestVersion)
                .execute(new ResultCallback<Result<AppVersion>>() {
                    @Override
                    public void onSuccess(Result<AppVersion> response) {
                        String code = response.getCode();
                        AppVersion data = response.getData();
                        if ("00000".equals(code) && data != null) {
                            int versionCode = Utils.getAppVersionCode(MainActivity.this);
                            if (versionCode < data.getVersionCode()) {
                                showUpdate(data);
                            }
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                    }

                    @Override
                    public void onFinish() {
                    }
                });
    }

    // 展示更新弹窗
    private void showUpdate(final AppVersion appVersion) {
        AlertDialog.Builder mDialog = new AlertDialog.Builder(this);
        mDialog.setTitle("发现新版本");
        mDialog.setMessage(appVersion.getUpdateInfo());
        if (appVersion.getUpdateFlag() != 2) {
            mDialog.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    saveUpdateFlag();
                }
            });
        }
        mDialog.setPositiveButton("更新", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                gotoDownload(appVersion.getApkUrl());
            }
        }).setCancelable(false).create().show();
    }

    // 调起浏览器下载
    private void gotoDownload(String url){
        Intent intent = new Intent();
        intent.setData(Uri.parse(url));
        intent.setAction(Intent.ACTION_VIEW);
        startActivity(intent);
    }
}
