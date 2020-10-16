package org.epragati.payments.service;

import java.util.Map;

import org.epragati.payment.dto.PaymentTransactionDTO;
import org.epragati.payments.vo.PaymentGateWayResponse;
import org.epragati.payments.vo.TransactionDetailVO;

public interface PaymentGateWay {

	String getURL();

	String getKey();

	String getQueryParam();

	String getEncryption(String requestData);

	TransactionDetailVO getRequestParameter(TransactionDetailVO transactionDetailVO);

	PaymentGateWayResponse processResponse(PaymentGateWayResponse paymentGateWayResponse,
			Map<String, String> gatewayDetails);

	String dncryptSBIData(String data);

	PaymentGateWayResponse processVerify(PaymentTransactionDTO payTransctionDTO);

	PaymentGateWayResponse cancellationOfTransaction(PaymentTransactionDTO payTransctionDTO);

	

}
