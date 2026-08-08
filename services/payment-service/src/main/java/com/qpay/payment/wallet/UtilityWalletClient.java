package com.qpay.payment.wallet;
import org.springframework.beans.factory.annotation.Value;import org.springframework.http.MediaType;import org.springframework.stereotype.Component;import org.springframework.web.client.RestClient;import java.util.UUID;
@Component public class UtilityWalletClient {
 private final RestClient client;private final String token;public UtilityWalletClient(@Value("${qpay.wallet.url:http://localhost:8084}")String url,@Value("${qpay.service-token:local-service-token}")String token){client=RestClient.builder().baseUrl(url).build();this.token=token;}
 public UUID hold(UUID wallet,String reference,long amount,String currency){var response=client.post().uri("/internal/v1/wallet-funds/holds").header("X-QPay-Service-Token",token).contentType(MediaType.APPLICATION_JSON).body(new Hold(wallet,reference,amount,currency)).retrieve().body(HoldResponse.class);if(response==null)throw new IllegalStateException("wallet hold returned no response");return response.holdId();}
 public void release(UUID hold){client.post().uri("/internal/v1/wallet-funds/holds/{id}/release",hold).header("X-QPay-Service-Token",token).retrieve().toBodilessEntity();}
 public void settle(UUID hold,UUID utility,long amount,long fee,String currency){client.post().uri("/internal/v1/wallet-funds/holds/{id}/settle-utility",hold).header("X-QPay-Service-Token",token).contentType(MediaType.APPLICATION_JSON).body(new Settle(utility,amount,fee,currency)).retrieve().toBodilessEntity();}
 record Hold(UUID walletId,String reference,long amountMinor,String currency){}record HoldResponse(UUID holdId){}record Settle(UUID utilityId,long amountMinor,long feeMinor,String currency){}
}
