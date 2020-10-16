package org.epragati.payments.service;

import java.util.List;

import org.epragati.payments.vo.TransactionDetailVO;

/**
 * @author sairam.cheruku
 *
 */
public interface ChalanDetailsService {
	
	/**
	 * 
	 * @param challanSeries
	 * @param transactionDetailVO
	 * @return chalanDetails Saving
	 */

	void chalanDetailsSavingAndVerificationForTransaction(List<Long> challanSeries,
			TransactionDetailVO transactionDetailVO);

}
