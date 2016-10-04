package com.bakkenbaeck.toshi.manager;


import com.bakkenbaeck.toshi.model.LocalBalance;
import com.bakkenbaeck.toshi.model.User;
import com.bakkenbaeck.toshi.network.rest.model.TransactionSent;
import com.bakkenbaeck.toshi.network.ws.model.TransactionConfirmation;
import com.bakkenbaeck.toshi.util.OnNextObserver;
import com.bakkenbaeck.toshi.view.BaseApplication;

import java.math.BigDecimal;

import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;

public class LocalBalanceManager {

    private final BehaviorSubject<LocalBalance> balanceSubject = BehaviorSubject.create();
    private final PublishSubject<Void> upsellSubject = PublishSubject.create();

    private LocalBalance localBalance;
    private boolean hasShownUpsell = false;

    public LocalBalanceManager() {
        this.localBalance = new LocalBalance();
        initBalanceListeners();
    }

    private void initBalanceListeners() {
        BaseApplication.get().getUserManager().getObservable().subscribe(this.currentUserSubscriber);
        BaseApplication.get().getSocketObservables().getTransactionConfirmationObservable().subscribe(this.newTransactionConfirmationSubscriber);
        BaseApplication.get().getSocketObservables().getTransactionSentObservable().subscribe(this.newTransactionSentSubscriber);
    }

    private final OnNextObserver<User> currentUserSubscriber = new OnNextObserver<User>() {
        @Override
        public void onNext(final User user) {
            setBalance(user);
        }
    };

    private final OnNextObserver<TransactionConfirmation> newTransactionConfirmationSubscriber = new OnNextObserver<TransactionConfirmation>() {
        @Override
        public void onNext(final TransactionConfirmation confirmation) {
            setBalance(confirmation);
        }
    };

    private final OnNextObserver<TransactionSent> newTransactionSentSubscriber = new OnNextObserver<TransactionSent>() {
        @Override
        public void onNext(final TransactionSent transactionSent) {
            setBalance(transactionSent);
        }
    };

    public Observable<LocalBalance> getObservable() {
        return this.balanceSubject.asObservable();
    }

    public Observable<Void> getUpsellObservable() {
        return this.upsellSubject.asObservable();
    }

    private void setBalance(final User user) {
        this.localBalance.setUnconfirmedBalance(user.getUnconfirmedBalance());
        this.localBalance.setConfirmedBalance(user.getConfirmedBalance());
        emitNewBalance();
    }

    private void setBalance(final TransactionConfirmation confirmation) {
        this.localBalance.setUnconfirmedBalance(confirmation.getUnconfirmedBalance());
        this.localBalance.setConfirmedBalance(confirmation.getConfirmedBalance());
        emitNewBalance();
    }

    private void setBalance(final TransactionSent transactionSent) {
        this.localBalance.setUnconfirmedBalance(transactionSent.getUnconfirmedBalance());
        this.localBalance.setConfirmedBalance(transactionSent.getConfirmedBalance());
        emitNewBalance();
    }

    // True if the wallet is in a state where we can consider
    // showing an upsell message to the user. The upsell message containing
    // information on withdrawal
    private boolean isInUpsellState() {
        return !hasShownUpsell && this.localBalance.getConfirmedBalanceAsEth().compareTo(new BigDecimal("0.001")) == 1;
    }

    private void emitNewBalance() {
        if (isInUpsellState()) {
            this.upsellSubject.onCompleted();
            this.hasShownUpsell = true;
        }
        this.balanceSubject.onNext(this.localBalance);
    }

    @Override
    public String toString() {
        return this.localBalance.unconfirmedBalanceString();
    }
}