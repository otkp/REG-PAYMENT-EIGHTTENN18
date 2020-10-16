package org.epragati.payments.service;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.epragati.jwt.JwtUser;
import org.epragati.payments.vo.CashBookVO;
import org.epragati.payments.vo.GeneralCashBookVO;
import org.epragati.payments.vo.ReconcilationVO;
import org.epragati.payments.vo.TransactionDetailVO;
import org.epragati.regservice.vo.RegServiceVO;
import org.epragati.util.GateWayResponse;
import org.epragati.vcr.vo.ConsolidateChallanVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 
 * @author saikiran.kola
 *
 */
public interface CashPaymentService {

	/**
	 * 
	 * @param regServiceDetail
	 * @param uploadfiles
	 * @return
	 */
	TransactionDetailVO saveVcrCashPayment(String regServiceDetail, MultipartFile[] uploadfiles, JwtUser jwtUser);

	/**
	 * 
	 * @param reconcilationVO
	 * @return
	 */
	String cashReconciliation(ReconcilationVO reconcilationVO);

	/**
	 * 
	 * @param reconcilationVO
	 * @return
	 */

	TransactionDetailVO cashPaymentCommonCode(RegServiceVO regServiceVO);

	GeneralCashBookVO generalCashBookData(String officeCode, CashBookVO cashBookVO);

	List<CashBookVO> getGeneralCashBookDetails(String officeCode, CashBookVO cashBookVO2);

	GeneralCashBookVO individualCashBook(CashBookVO cashBookVO, String user);

	Pair<CashBookVO, List<CashBookVO>> getIndividualCashBookDetails(CashBookVO cashBookVO, String user);

	ReconcilationVO consolidateVcrData(JwtUser user, ReconcilationVO reconcilationVO);

	List<String> getTresuryDetails(List<String> officeCodeList);

	ReconcilationVO getVcrBasedOnBookedDate(ReconcilationVO reconcilationVO);
	
	ReconcilationVO getVcrforfeeCorrection(JwtUser jwtUser, ReconcilationVO reconcilationVO);


	GateWayResponse<?> vcrFeeCorrection(JwtUser jwtUser, ReconcilationVO reconcilationVO);

}
