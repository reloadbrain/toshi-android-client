/*
 * 	Copyright (c) 2017. Toshi Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.toshi.presenter.webview;


import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.webkit.WebView;

import com.toshi.R;
import com.toshi.crypto.HDWallet;
import com.toshi.crypto.util.TypeConverter;
import com.toshi.model.local.UnsignedW3Transaction;
import com.toshi.model.network.SentTransaction;
import com.toshi.model.network.SignedTransaction;
import com.toshi.model.sofa.SofaAdapters;
import com.toshi.presenter.webview.model.ApproveTransactionCallback;
import com.toshi.presenter.webview.model.GetAccountsCallback;
import com.toshi.presenter.webview.model.RejectTransactionCallback;
import com.toshi.presenter.webview.model.SignTransactionCallback;
import com.toshi.util.LogUtil;
import com.toshi.view.BaseApplication;
import com.toshi.view.fragment.DialogFragment.PaymentConfirmationDialog;

import java.io.IOException;

/* package */ class SofaHostWrapper implements SofaHostListener {

    private final AppCompatActivity activity;
    private final WebView webView;
    private final SOFAHost sofaHost;
    private final HDWallet wallet;

    /* package */ SofaHostWrapper(final AppCompatActivity activity, final WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.sofaHost = new SOFAHost(this);
        this.wallet = BaseApplication
                        .get()
                        .getToshiManager()
                        .getWallet()
                        .toBlocking()
                        .value();
    }

    /* package */ SOFAHost getSofaHost() {
        return this.sofaHost;
    }

    public void getAccounts(final String id) {
        final GetAccountsCallback callback =
                new GetAccountsCallback().setResult(this.wallet.getPaymentAddress());

        doCallBack(id, callback.toJsonEncodedString());
    }

    public void approveTransaction(final String id, final String unsignedTransaction) {
        final boolean shouldApprove = shouldApproveTransaction(unsignedTransaction);
        final ApproveTransactionCallback callback =
                new ApproveTransactionCallback()
                        .setResult(shouldApprove);
        doCallBack(id, callback.toJsonEncodedString());
    }

    private boolean shouldApproveTransaction(final String unsignedTransaction) {
        final UnsignedW3Transaction transaction;
        try {
            transaction = SofaAdapters.get().unsignedW3TransactionFrom(unsignedTransaction);
        } catch (final IOException e) {
            LogUtil.exception(getClass(), "Unable to parse unsigned transaction. ", e);
            return false;
        }

        return transaction.getFrom().equals(this.wallet.getPaymentAddress());
    }

    public void signTransaction(final String id, final String unsignedTransaction) {
        final UnsignedW3Transaction transaction;
        try {
            transaction = SofaAdapters.get().unsignedW3TransactionFrom(unsignedTransaction);
        } catch (final IOException e) {
            LogUtil.exception(getClass(), "Unable to parse unsigned transaction. ", e);
            return;
        }
        if (this.activity == null) return;
        final PaymentConfirmationDialog dialog =
                PaymentConfirmationDialog
                        .newInstanceWebPayment(
                                unsignedTransaction,
                                transaction.getTo(),
                                transaction.getValue(),
                                id,
                                null
                        );
        dialog.show(this.activity.getSupportFragmentManager(), PaymentConfirmationDialog.TAG);
        dialog.setOnPaymentConfirmationApprovedListener(this::handleApprovedClicked)
                .setOnPaymentConfirmationCanceledListener(this::handleAcceptedCanceled);
    }

    private void handleApprovedClicked(final Bundle bundle) {
        final String callbackId = bundle.getString(PaymentConfirmationDialog.CALLBACK_ID);
        final String unsignedTransaction = bundle.getString(PaymentConfirmationDialog.UNSIGNED_TRANSACTION);
        handlePaymentApproved(callbackId, unsignedTransaction);
    }

    private void handlePaymentApproved(final String callbackId, final String unsignedTransaction) {
        final UnsignedW3Transaction transaction;
        try {
            transaction = SofaAdapters.get().unsignedW3TransactionFrom(unsignedTransaction);
        } catch (final IOException e) {
            LogUtil.exception(getClass(), "Unable to parse unsigned transaction. ", e);
            return;
        }

        BaseApplication
                .get()
                .getTransactionManager()
                .signW3Transaction(transaction)
                .subscribe(
                        signedTransaction -> handleSignedW3Transaction(callbackId, signedTransaction),
                        throwable -> LogUtil.exception(getClass(), throwable)
                );
    }

    private void handleSignedW3Transaction(final String callbackId, final SignedTransaction signedTransaction) {
        final SignTransactionCallback callback =
                new SignTransactionCallback()
                        .setSkeleton(signedTransaction.getSkeleton())
                        .setSignature(signedTransaction.getSignature());
        try {
            doCallBack(callbackId, callback.toJsonEncodedString());
        } catch (Exception e) {
            LogUtil.exception(getClass(), e);
            doCallBack(callbackId, String.format("{\\\"error\\\":\\\"%s\\\"}", e.getMessage()));
        }
    }

    private void handleAcceptedCanceled(final Bundle bundle) {
        final String callbackId = bundle.getString(PaymentConfirmationDialog.CALLBACK_ID);
        final RejectTransactionCallback callback =
                new RejectTransactionCallback()
                        .setError(BaseApplication.get().getString(R.string.error__reject_transaction));
        doCallBack(callbackId, callback.toJsonEncodedString());
    }

    public void publishTransaction(final String callbackId, final String signedTransactionPayload) {
        final String cleanPayload = TypeConverter.jsonStringToString(signedTransactionPayload);
        final SignedTransaction transaction = new SignedTransaction()
                .setEncodedTransaction(cleanPayload);

        BaseApplication
                .get()
                .getTransactionManager()
                .sendSignedTransaction(transaction)
                .subscribe(
                        sentTransaction -> handleSentTransaction(callbackId, sentTransaction),
                        throwable -> LogUtil.exception(getClass(), throwable)
                );
    }

    private void handleSentTransaction(final String callbackId, final SentTransaction sentTransaction) {
        doCallBack(callbackId, String.format(
                "{\\\"result\\\":\\\"%s\\\"}",
                sentTransaction.getTxHash()
        ));
    }

    private void doCallBack(final String id, final String encodedCallback) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (activity == null) return;
            final String methodCall = String.format("SOFA.callback(\"%s\",\"%s\")", id, encodedCallback);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.evaluateJavascript(methodCall, null);
            } else {
                webView.loadUrl("javascript:" + methodCall);
            }
        });

    }
}
