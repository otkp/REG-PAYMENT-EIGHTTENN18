package org.epragati.payments.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.epragati.constants.OwnerTypeEnum;
import org.epragati.master.dto.StagingRegistrationDetailsDTO;
import org.epragati.master.dto.TaxDetailsDTO;
import org.epragati.master.vo.GateWayVO;
import org.epragati.master.vo.StagingRegistrationDetailsVO;
import org.epragati.payments.vo.BreakPayments;
import org.epragati.payments.vo.BreakPaymentsSaveVO;
import org.epragati.payments.vo.CFMSEodReportVO;
import org.epragati.payments.vo.CitizenPaymentReportVO;
import org.epragati.payments.vo.ClassOfVehiclesVO;
import org.epragati.payments.vo.FeeDetailInput;
import org.epragati.payments.vo.FeeDetailsVO;
import org.epragati.payments.vo.FeePartsDetailsVo;
import org.epragati.payments.vo.PaymentFailureResultVO;
import org.epragati.payments.vo.PaymentGateWayResponse;
import org.epragati.payments.vo.PaymentReqParams;
import org.epragati.payments.vo.TransactionDetailVO;
import org.epragati.regservice.dto.CitizenFeeDetailsInput;
import org.epragati.regservice.dto.RegServiceDTO;
import org.epragati.regservice.vo.ApplicationSearchVO;
import org.epragati.regservice.vo.RegServiceVO;
import org.epragati.util.payment.GatewayTypeEnum;
import org.epragati.util.payment.PayStatusEnum;
import org.epragati.util.payment.ServiceEnum;
import org.epragati.vcr.vo.RegistrationVcrVo;
import org.epragati.vcrImage.dto.VoluntaryTaxDTO;
import org.json.JSONException;
import org.json.simple.parser.ParseException;

public interface PaymentGateWayService {

	Optional<?> prepareRequestObject(TransactionDetailVO transactionDetailVO, Object newParam, String collectionType);

	PaymentGateWayResponse processResponse(PaymentGateWayResponse paymentGateWayResponse, boolean isRequestFromCitizen);

	PaymentGateWayResponse processVerify(String appFormID, Boolean isAgreeToEnablePayment, String paymentTransactionNo,
			Boolean citizenAgreedToEnable, boolean isFromScheduler);

	PaymentReqParams convertPayments(TransactionDetailVO vo, String appFormNo);

	TransactionDetailVO taxIntegration(StagingRegistrationDetailsDTO stagingRegistrationDetailsDTO,
			TransactionDetailVO transactionDetailVO);

	// void updatePaymentStatus(PayStatusEnum payStatus, String applicationNo);

	String dncryptSBIData(String data);

	Map<String, String[]> getSliptingofSbiValue(String value);

	BreakPaymentsSaveVO breakPayments(List<ClassOfVehiclesVO> covs, List<ServiceEnum> serviceEnum, String weightType,
			Long taxAmount, Long cesFee, String taxType, Boolean isRtoSecRejected, Boolean isRtoIvcnRejected,
			OwnerTypeEnum ownerType, String officeCode, String applicationNumber);

	void updateCitizenPaymentStatus(PayStatusEnum payStatus, String applicationNo, String moduleCode);

	Optional<StagingRegistrationDetailsVO> generateTrNoForPaymentSuccess(String applicationNo);

	TaxDetailsDTO saveCitizenTaxDetails(RegServiceVO regServiceDTO, boolean secoundVehicleDiffTaxPaid,
			boolean isChassisVehicle, String stateCode);

	Optional<PaymentFailureResultVO> getPaymentDetailForFailueTransaction(String applicationNumber);

	FeeDetailsVO getCitizenServiceFee(CitizenFeeDetailsInput input);

	TransactionDetailVO getTransactionDetailsForPayments(StagingRegistrationDetailsDTO stagingDetails,
			TransactionDetailVO transactionDetailVO);

	String paymentsVerifiactionInStaging(StagingRegistrationDetailsDTO stagingDetails, boolean verifyPaymentFlag);

	void paymentIntiationVerification(String applicationStatus, String applicationNo);

	Object convertPaymentsForCFMS(TransactionDetailVO transactionDetailVO, String formNumber);

	void verifyPaymentStatus(String applicationNo, String paymentTransactionNo);

	/**
	 * CFMS EOD Report
	 * 
	 * @param fromDate
	 * @return
	 */
	CFMSEodReportVO getCfmsEodReportByDate(LocalDate fromDate);

	/**
	 * Get Payment details by Application Number
	 * 
	 * @param applicationNumber
	 * @return
	 */
	CitizenPaymentReportVO getPaymentDetailsByApplicationNumber(String applicationNumber);

	List<GatewayTypeEnum> getPaymentGateways();

	/**
	 * 
	 * @return
	 */
	List<GateWayVO> getAllPaymentGateways();

	String processToVerifyPaymets(StagingRegistrationDetailsDTO registrationDetails, Boolean isAgreeToEnablePayment,
			Boolean citizenAgreedToEnable, boolean isFromScheduler);

	void doSavePendingPaymentDetailsFromCFMS(String verifyPayResponce) throws ParseException, JSONException;

	/**
	 * Cancellation transaction in CFMS
	 * 
	 * @param applicationNo
	 * @param paymentTransactionNo
	 * @return
	 */
	PaymentGateWayResponse cancelTransaction(String applicationNo, String paymentTransactionNo);

	BreakPaymentsSaveVO preFeeDetails(List<BreakPayments> feePartsMap);

	Double getTotalamount(Map<String, FeePartsDetailsVo> feePartsMap);

	Map<String, FeePartsDetailsVo> calculateTaxAndFee(CitizenFeeDetailsInput input);

	FeeDetailsVO preFeeDetailsVO(Map<String, FeePartsDetailsVo> feePartsMap);

	Map<String, FeePartsDetailsVo> applicationFeeInFeeParts(Map<String, FeePartsDetailsVo> feeParts);

	void updateVcrDetails(RegServiceDTO dto);

	void saveVoluntaryDetails(RegServiceDTO dto);
	
	Map<String, String> verifyPametStatusThroughTransactionNumber(ApplicationSearchVO applicationSearchVO);

	FeeDetailsVO getPaymentDetailsForOtherServices(List<Integer> serviceId);

	TransactionDetailVO doPaymentProcess(RegServiceVO result, Boolean false1);

	void getNextQuarterValidity(VoluntaryTaxDTO voluntaryTaxDto);

	
}
