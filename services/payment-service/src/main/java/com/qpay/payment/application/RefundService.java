package com.qpay.payment.application;
import com.qpay.payment.infrastructure.RefundRepository;import com.qpay.payment.provider.RefundProvider;import org.springframework.stereotype.Service;import org.springframework.transaction.support.TransactionTemplate;import java.util.*;
@Service public class RefundService {
 private final RefundRepository repo;private final RefundProvider provider;private final TransactionTemplate tx;public RefundService(RefundRepository repo,RefundProvider provider,TransactionTemplate tx){this.repo=repo;this.provider=provider;this.tx=tx;}
 public RefundView create(UUID merchant,UUID payment,String reference,long amount){var reservation=tx.execute(s->repo.reserve(merchant,payment,reference,amount));if(reservation.replay())return reservation.refund();tx.executeWithoutResult(s->repo.processing(reservation.refund().id()));var result=provider.submit(new RefundProvider.Request(reservation.refund().id(),reservation.originalProviderReference(),amount,reservation.refund().currency()));return tx.execute(s->repo.complete(reservation.refund().id(),result.providerCode(),result.providerReference(),result.accepted(),result.failureCode()));}
 public RefundView get(UUID id){return repo.find(id).orElseThrow(()->new IllegalArgumentException("refund not found"));}
}
