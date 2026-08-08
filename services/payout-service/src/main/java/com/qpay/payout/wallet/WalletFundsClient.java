package com.qpay.payout.wallet;
import org.springframework.beans.factory.annotation.Value;import org.springframework.http.MediaType;import org.springframework.stereotype.Component;import org.springframework.web.client.RestClient;
import java.util.UUID;
@Component public class WalletFundsClient {
 private final RestClient client;private final String token;
 public WalletFundsClient(@Value("${qpay.wallet.url:http://localhost:8084}") String url,@Value("${qpay.service-token:local-service-token}") String token){this.client=RestClient.builder().baseUrl(url).build();this.token=token;}
 public UUID hold(UUID walletId,String reference,long amount,String currency){var response=client.post().uri("/internal/v1/wallet-funds/holds").header("X-QPay-Service-Token",token).contentType(MediaType.APPLICATION_JSON).body(new HoldRequest(walletId,reference,amount,currency)).retrieve().body(HoldResponse.class);if(response==null)throw new IllegalStateException("wallet hold returned no response");return response.holdId();}
 public void release(UUID holdId){client.post().uri("/internal/v1/wallet-funds/holds/{id}/release",holdId).header("X-QPay-Service-Token",token).retrieve().toBodilessEntity();}
 public void settle(UUID holdId,UUID payoutId,long amount,long fee,String currency){client.post().uri("/internal/v1/wallet-funds/holds/{id}/settle",holdId).header("X-QPay-Service-Token",token).contentType(MediaType.APPLICATION_JSON).body(new SettleRequest(payoutId,amount,fee,currency)).retrieve().toBodilessEntity();}
 record HoldRequest(UUID walletId,String reference,long amountMinor,String currency){} record HoldResponse(UUID holdId){} record SettleRequest(UUID payoutId,long payoutMinor,long feeMinor,String currency){}
}
