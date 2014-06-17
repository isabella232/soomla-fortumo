package com.soomla.plugins.monetize.fortumo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.soomla.SoomlaApp;
import com.soomla.SoomlaUtils;
import com.soomla.store.StoreInventory;
import com.soomla.store.exceptions.VirtualItemNotFoundException;

import mp.MpUtils;
import mp.PaymentRequest;
import mp.PaymentResponse;

/**
 * Created by refaelos on 28/05/14.
 */
public class SoomlaFortumo {

    public void initialize(String appId, String permission) {

        // initialize the Publisher SDK
        MpUtils.enablePaymentBroadcast(SoomlaApp.getAppContext(), permission);
    }

    public void initiatePaymentRequest(String serviceId, String inAppSecret, String displayString, String itemId, boolean consumable, FortumoPaymentListener listener) {

        mSavedFortumoPaymentListener = listener;

        final Intent intent = new Intent(SoomlaApp.getAppContext(), SoomlaFortumoActivity.class);
//            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("serviceId", serviceId);
        intent.putExtra("inAppSecret", inAppSecret);
        intent.putExtra("displayString", displayString);
        intent.putExtra("itemId", itemId);
        intent.putExtra("consumable", consumable);

        SoomlaApp.getAppContext().startActivity(intent);
    }


    public static class SoomlaFortumoActivity extends Activity {

        private static final int RC_REQUEST = 10002;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Intent intent = getIntent();
            String serviceId = intent.getStringExtra("serviceId");
            String inAppSecret = intent.getStringExtra("inAppSecret");
            String displayString = intent.getStringExtra("displayString");
            String itemId = intent.getStringExtra("itemId");
            boolean consumable = intent.getBooleanExtra("consumable", true);

            try {
                PaymentRequest.PaymentRequestBuilder builder = new PaymentRequest.PaymentRequestBuilder();
                builder.setService(serviceId, inAppSecret);
                builder.setDisplayString(displayString);
                builder.setProductName(itemId);
                builder.setConsumable(consumable);
                PaymentRequest pr = builder.build();
                startActivityForResult(pr.toIntent(this), RC_REQUEST);
            } catch (Exception e) {
                finish();

                String msg = "Error when trying to purchase with Fortumo: " + e.getMessage();
                SoomlaUtils.LogError(TAG, msg);

                if(SoomlaFortumo.getInstance().mSavedFortumoPaymentListener != null) {
                    SoomlaFortumo.getInstance().mSavedFortumoPaymentListener.fail();
                    SoomlaFortumo.getInstance().mSavedFortumoPaymentListener = null;
                }
            }
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == RC_REQUEST) {
                if(data == null) {
                    SoomlaUtils.LogError(TAG, "data == null is always a bad thing... (SoomlaFortumoActivity)");
                    return;
                }

                if (resultCode == RESULT_OK) {
                    PaymentResponse response = new PaymentResponse(data);

                    switch (response.getBillingStatus()) {
                        case MpUtils.MESSAGE_STATUS_BILLED:
                            try {
                                StoreInventory.giveVirtualItem(response.getProductName(), Integer.parseInt(response.getCreditAmount()));
                            } catch (VirtualItemNotFoundException e) {
                                SoomlaUtils.LogError(TAG, "There was an error while trying to give " +
                                        "the user the credits he bought. item not found: " + response.getProductName());
                            }

                            if(SoomlaFortumo.getInstance().mSavedFortumoPaymentListener != null) {
                                SoomlaFortumo.getInstance().mSavedFortumoPaymentListener.success(response);
                                SoomlaFortumo.getInstance().mSavedFortumoPaymentListener = null;
                            }
                            break;
                        case MpUtils.MESSAGE_STATUS_FAILED:
                            if(SoomlaFortumo.getInstance().mSavedFortumoPaymentListener != null) {
                                SoomlaFortumo.getInstance().mSavedFortumoPaymentListener.fail();
                                SoomlaFortumo.getInstance().mSavedFortumoPaymentListener = null;
                            }
                            break;
                        case MpUtils.MESSAGE_STATUS_PENDING:
                            if(SoomlaFortumo.getInstance().mSavedFortumoPaymentListener != null) {
                                SoomlaFortumo.getInstance().mSavedFortumoPaymentListener.pending(response);
                                SoomlaFortumo.getInstance().mSavedFortumoPaymentListener = null;
                            }
                            break;
                    }
                    if(SoomlaFortumo.getInstance().mSavedFortumoPaymentListener != null) {
                        SoomlaFortumo.getInstance().mSavedFortumoPaymentListener.cancelled(response);
                        SoomlaFortumo.getInstance().mSavedFortumoPaymentListener = null;
                    }
                } else {
                    if(SoomlaFortumo.getInstance().mSavedFortumoPaymentListener != null) {
                        SoomlaFortumo.getInstance().mSavedFortumoPaymentListener.fail();
                        SoomlaFortumo.getInstance().mSavedFortumoPaymentListener = null;
                    }
                }
            }

            finish();
        }

        @Override
        protected void onStop() {
            super.onStop();
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
        }
    }

    public interface FortumoPaymentListener {
        void success(PaymentResponse paymentResponse);
        void fail();
        void pending(PaymentResponse response);
        void cancelled(PaymentResponse response);
    }

    private SoomlaFortumo() {

    }

    public static SoomlaFortumo getInstance() {
        if (sInstance == null) {
            sInstance = new SoomlaFortumo();
        }
        return sInstance;
    }

    private FortumoPaymentListener mSavedFortumoPaymentListener;
    private static SoomlaFortumo sInstance = null;
    private static final String TAG = "SOOMLA SoomlaFortumo";
}
