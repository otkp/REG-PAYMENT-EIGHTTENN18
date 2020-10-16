package org.epragati.payments.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.epragati.exception.BadRequestException;
import org.epragati.master.dao.GateWayDAO;
import org.epragati.master.dao.OfficeDAO;
import org.epragati.master.dto.GateWayDTO;
import org.epragati.master.dto.OfficeDTO;
import org.epragati.payment.dto.PaymentTransactionDTO;
import org.epragati.payments.service.ChalanDetailsService;
import org.epragati.payments.service.PaymentGateWay;
import org.epragati.payments.vo.FeePartsDetailsVo;
import org.epragati.payments.vo.PayUProductInfo;
import org.epragati.payments.vo.PayUResponse;
import org.epragati.payments.vo.PayUVerifyResponse;
import org.epragati.payments.vo.PayUVerifyResponseTransaction;
import org.epragati.payments.vo.PaymentGateWayResponse;
import org.epragati.payments.vo.PaymentVerifyRequest;
import org.epragati.payments.vo.PayuPaymentPart;
import org.epragati.payments.vo.TransactionDetailVO;
import org.epragati.payments.vo.TreasureHeadDetails;
import org.epragati.sequence.SequenceGenerator;
import org.epragati.util.document.Sequence;
import org.epragati.util.exception.SequenceGenerateException;
import org.epragati.util.payment.GatewayTypeEnum;
import org.epragati.util.payment.GatewayTypeEnum.PayUParams;
import org.epragati.util.payment.HashingUtil;
import org.epragati.util.payment.ModuleEnum;
import org.epragati.util.payment.PayStatusEnum;
import org.epragati.util.payment.ServiceCodeEnum;
import org.epragati.util.payment.ServiceEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Qualifier("payUGateWay")
public class PayUGateWay implements PaymentGateWay {

	private static final Logger logger = LoggerFactory.getLogger(PayUGateWay.class);

	@Value("${isInTestPayment:}")
	private Boolean isInTestPayment;

	@Value("1.0")
	private Double testAmount;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RestTemplate paymentRestTemplate;

	@Autowired
	private OfficeDAO officeDAO;

	@Autowired
	private GateWayDAO gatewayDao;

	@Autowired
	private SequenceGenerator sequencenGenerator;

	@Autowired
	private ChalanDetailsService chalanDetailsService;

	@Override
	public String getURL() {
		//
		return null;
	}

	@Override
	public String getKey() {
		//
		return null;
	}

	@Override
	public String getQueryParam() {
		//
		return null;
	}

	@Override
	public String getEncryption(String requestData) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TransactionDetailVO getRequestParameter(TransactionDetailVO transactionDetailVO) {
		GateWayDTO gatewayValue = gatewayDao.findByGateWayType(GatewayTypeEnum.PAYU);
		Map<String, String> gatewayDetails = gatewayValue.getGatewayDetails();
		if (validateInputs(transactionDetailVO)) {
			transactionDetailVO.setEmail(transactionDetailVO.getEmail());
			transactionDetailVO.setPhone(transactionDetailVO.getPhone());
			transactionDetailVO.setFirstName(transactionDetailVO.getFirstName());
			String productInfo = getPayURequestParameter(transactionDetailVO, gatewayValue.getGatewayDetails());
			transactionDetailVO.setProductInfo(productInfo);

			if (transactionDetailVO.getModule().equalsIgnoreCase(ServiceEnum.SPNR.getCode())
					|| transactionDetailVO.getModule().equalsIgnoreCase(ServiceEnum.SPNB.getCode())) {
				transactionDetailVO.setKey(gatewayDetails.get(GatewayTypeEnum.PayUParams.SPNO_PAYU_KEY.getParamKey()));
				generatePayUHash(transactionDetailVO,
						gatewayDetails.get(GatewayTypeEnum.PayUParams.SPNO_PAYU_SALT.getParamKey()));
			}

			else if (transactionDetailVO.getModule().equalsIgnoreCase(ModuleEnum.REG.getCode())) {
				transactionDetailVO.setKey(gatewayDetails.get(GatewayTypeEnum.PayUParams.PAYU_KEY.getParamKey()));
				generatePayUHash(transactionDetailVO,
						gatewayDetails.get(GatewayTypeEnum.PayUParams.PAYUSALT.getParamKey()));
			}

			else {
				transactionDetailVO
						.setKey(gatewayDetails.get(GatewayTypeEnum.PayUParams.CITIZEN_PAYU_KEY.getParamKey()));
				generatePayUHash(transactionDetailVO,
						gatewayDetails.get(GatewayTypeEnum.PayUParams.CITIZEN_PAYU_SALT.getParamKey()));
			}

			return transactionDetailVO;
		}
		logger.info("transactionDetailVO vo not present");
		throw new BadRequestException("Required inputs should not be empty empty");

	}

	private Boolean validateInputs(TransactionDetailVO transactionDetailVO) {
		// TODO :need handle some data validations
		return Boolean.TRUE;

	}

	private Long getChallanNumber(Integer requiredNumber) {
		Long number = 0L;
		try {
			Map<String, String> hMap = new HashMap<>();
			hMap.put("INCR", String.valueOf(requiredNumber));
			// TODO?:need enable it
			number = Long.parseLong(sequencenGenerator.getSequence(Sequence.CHALLAN.getSequenceId().toString(), hMap));
		} catch (SequenceGenerateException e) {
			logger.error(" Chanlana SequenceGenerateException  {}", e);

		} catch (Exception e) {
			logger.error(" exception While chalana generation {}", e);

		}
		logger.info("Chalana number ganarated, final number :{}", number);
		return (number - requiredNumber);
	}

	public String getPayURequestParameter(TransactionDetailVO transactionDetailVO, Map<String, String> gatewayDetails) {

		List<String> hsrpCessFeeinOtherCharges = new ArrayList<>();
		hsrpCessFeeinOtherCharges.add(ServiceCodeEnum.HSRP_FEE.getTypeDesc());
		hsrpCessFeeinOtherCharges.add(ServiceCodeEnum.CESS_FEE.getTypeDesc());
		Double treasureHeadsAmount = 0.0;
		List<Long> challanSeries = new ArrayList<>();

		try {
			if (transactionDetailVO.getModule().equalsIgnoreCase(ServiceEnum.SPNR.getCode())
					|| transactionDetailVO.getModule().equalsIgnoreCase(ServiceEnum.SPNB.getCode())) {
				transactionDetailVO
						.setSucessUrl(gatewayDetails.get(GatewayTypeEnum.PayUParams.SPNo_SUCESS_URL.getParamKey()));
				transactionDetailVO
						.setFailureUrl(gatewayDetails.get(GatewayTypeEnum.PayUParams.SPNo_FAILURE_URL.getParamKey()));
			} else if (transactionDetailVO.getModule().equalsIgnoreCase(ModuleEnum.REG.getCode())) {
				transactionDetailVO
						.setSucessUrl(gatewayDetails.get(GatewayTypeEnum.PayUParams.SUCESS_URL.getParamKey()));
				transactionDetailVO
						.setFailureUrl(gatewayDetails.get(GatewayTypeEnum.PayUParams.FAILURE_URL.getParamKey()));
			} else {
				transactionDetailVO.setSucessUrl(gatewayDetails.get(GatewayTypeEnum.PayUParams.CITIZEN_SUCESS_URL.getParamKey()));
				

		transactionDetailVO.setFailureUrl(
				gatewayDetails.get(GatewayTypeEnum.PayUParams.CITIZEN_FAILURE_URL.getParamKey()));
	}
			transactionDetailVO
					.setServiceProvider(gatewayDetails.get(GatewayTypeEnum.PayUParams.SERVICEPROVIDER.getParamKey()));
			transactionDetailVO
					.setPaymentUrl(gatewayDetails.get(GatewayTypeEnum.PayUParams.PAYUPAYMENTURL.getParamKey()));

			/**
			 * Prepare treasureHeads
			 */
			List<TreasureHeadDetails> treasureHeads = new ArrayList<>(5);
			List<PayuPaymentPart> paymentParts = new ArrayList<>(2);
			@SuppressWarnings("unused")
			StringBuffer treasureDescription = new StringBuffer("");

			// getting DDO code based on office code
			String ddocode = getDDOCode(transactionDetailVO.getOfficeCode());

			int challanSize = transactionDetailVO.getFeePartsMap().size();
			if (transactionDetailVO.getFeePartsMap().keySet().contains(ServiceCodeEnum.CESS_FEE.getTypeDesc())) {
				challanSize = challanSize - 1;
			}
			if (transactionDetailVO.getFeePartsMap().keySet().contains(ServiceCodeEnum.HSRP_FEE.getTypeDesc())) {
				challanSize = challanSize - 1;
			}

			// getting challa start number
			Long challanNumber = getChallanNumber(challanSize);

			// prepare Head1 parts descriptions. amount=Application Fee + Test
			// Fee+ Card
			// Fee+ Late fee
			// TODO:will change automatically from enum constant.
			List<String> headDescList = new ArrayList<>(10);
			headDescList.add(ServiceCodeEnum.REGISTRATION.getTypeDesc());
			headDescList.add(ServiceCodeEnum.SERVICE_FEE.getTypeDesc());
			headDescList.add(ServiceCodeEnum.POSTAL_FEE.getTypeDesc());
			headDescList.add(ServiceCodeEnum.HSRP_FEE.getTypeDesc());
			headDescList.add(ServiceCodeEnum.QLY_TAX.getTypeDesc());
			headDescList.add(ServiceCodeEnum.HALF_TAX.getTypeDesc());
			headDescList.add(ServiceCodeEnum.YEAR_TAX.getTypeDesc());
			headDescList.add(ServiceCodeEnum.LIFE_TAX.getTypeDesc());
			headDescList.add(ServiceCodeEnum.CESS_FEE.getTypeDesc());

			// TODO: In case of empty data enable below details

			/*
			 * TreasureHeadDetails feeHead1Details = new TreasureHeadDetails();
			 * feeHead1Details.setAmount(0.0);
			 * feeHead1Details.setChallanNo(++challanNumber);
			 * feeHead1Details.setDdoCode(ddocode);
			 * feeHead1Details.setDeptCode(gatewayDetails.get(GatewayTypeEnum.
			 * PayUParams.DEPTCODE.getParamKey()));
			 * feeHead1Details.setTxtId(transactionDetailVO.getTxnid());
			 * feeHead1Details.setPos(GatewayTypeEnum.PayUParams.PAYUPOS.
			 * getParamKey());
			 * feeHead1Details.setRemitterName(transactionDetailVO.
			 * getRemiterName()); treasureHeads.add(feeHead1Details);
			 */
			for (Map.Entry<String, FeePartsDetailsVo> entry : transactionDetailVO.getFeePartsMap().entrySet()) {
				if (!hsrpCessFeeinOtherCharges.contains(entry.getKey())) {
					TreasureHeadDetails treasureHeadDetails = new TreasureHeadDetails();
					if (entry.getValue() == null) {
						continue;
					}
					treasureHeadDetails.setAmount(entry.getValue().getAmount());
					treasureHeadDetails.setChallanNo(++challanNumber);
					treasureHeadDetails.setDdoCode(ddocode);
					treasureHeadDetails
							.setDeptCode(gatewayDetails.get(GatewayTypeEnum.PayUParams.DEPTCODE.getParamKey()));
					treasureHeadDetails.setHeadOfAccount(entry.getValue().getHOA());
					treasureHeadDetails.setTxtId(transactionDetailVO.getTxnid());
					treasureHeadDetails.setPos(gatewayDetails.get(GatewayTypeEnum.PayUParams.PAYUPOS.getParamKey()));
					treasureHeadDetails.setRemitterName(transactionDetailVO.getRemiterName());
					treasureHeadDetails.setCreditAccount(entry.getValue().getCredit_Account());
					treasureHeadDetails.setHoaDesc(entry.getKey());

					if (isInTestPayment != null && isInTestPayment) {
						treasureHeadsAmount = 1.0;
					} else {
						treasureHeadsAmount = treasureHeadsAmount + entry.getValue().getAmount();
					}

					challanSeries.add(treasureHeadDetails.getChallanNo());
					treasureHeads.add(treasureHeadDetails);
				}
			}
			// chalanDetailsService.chalanDetailsSavingAndVerificationForTransaction(challanSeries,
			// transactionDetailVO);

			/**
			 * Treasure amount details
			 */
			PayuPaymentPart treasure = new PayuPaymentPart();
			treasure.setCommission("0"); // No commission for RTA
			treasure.setDescription(
					"Registration and Fitness FeeLife TaxService FeePostal FeeGreen TaxANDHRA PRADESH TRANSPORT DRIVERS WELFARE FUND");
			if (transactionDetailVO.getModule().equalsIgnoreCase(ServiceEnum.SPNR.getCode())
					|| transactionDetailVO.getModule().equalsIgnoreCase(ServiceEnum.SPNB.getCode())) {
				treasure.setMerchantId(gatewayDetails.get(GatewayTypeEnum.PayUParams.SPNO_MERCHANT_ID.getParamKey()));
			} else if (transactionDetailVO.getModule().equalsIgnoreCase(ModuleEnum.REG.getCode())) {
				treasure.setMerchantId(gatewayDetails.get(GatewayTypeEnum.PayUParams.MERCHANT_ID.getParamKey()));
			} else {
				treasure.setMerchantId(gatewayDetails.get(GatewayTypeEnum.PayUParams.CITIZEN_PAYU_MID.getParamKey()));
			}

			treasure.setName(gatewayDetails.get(GatewayTypeEnum.PayUParams.DEPTCODE.getParamKey()));
			treasure.setValue(String.valueOf(getRoundOff(treasureHeadsAmount)));
			paymentParts.add(treasure);
			for (Map.Entry<String, FeePartsDetailsVo> entry : transactionDetailVO.getFeePartsMap().entrySet()) {
				PayuPaymentPart treasure2 = new PayuPaymentPart();
				if (entry.getKey().equalsIgnoreCase(ServiceCodeEnum.HSRP_FEE.getTypeDesc())) {
					treasure2.setName("HSRP" + transactionDetailVO.getFormNumber());
					treasure2.setMerchantId(
							gatewayDetails.get(GatewayTypeEnum.PayUParams.HSRP_MARCHENT_ID.getParamKey()));
					treasure2.setCommission("0.0");

					if (isInTestPayment != null && isInTestPayment) {
						treasure2.setValue("1.0");
					} else {
						treasure2.setValue(entry.getValue().getAmount().toString());
					}
					// treasure2.setValue(entry.getValue().getAmount().toString());
					treasure2.setDescription(entry.getValue().getHOA());
					paymentParts.add(treasure2);

				}
				if (entry.getKey().equalsIgnoreCase(ServiceCodeEnum.CESS_FEE.getTypeDesc())) {
					treasure2.setName("CESS" + transactionDetailVO.getFormNumber());
					treasure2.setMerchantId(
							gatewayDetails.get(GatewayTypeEnum.PayUParams.CESS_MARCHENT_ID.getParamKey()));
					treasure2.setCommission("0.0");

					if (isInTestPayment != null && isInTestPayment) {
						treasure2.setValue("1.0");
					} else {
						treasure2.setValue(entry.getValue().getAmount().toString());
					}

					// treasure2.setValue(entry.getValue().getAmount().toString());
					treasure2.setDescription("CESS");
					paymentParts.add(treasure2);
				}

			}
			PayUProductInfo payUProductInfo = new PayUProductInfo();
			payUProductInfo.setPaymentParts(paymentParts);
			payUProductInfo.setTreasureHeads(treasureHeads);
			payUProductInfo.setAppTransNo(transactionDetailVO.getFormNumber());

			return getPayUProductInfo(payUProductInfo);
		} catch (Exception e) {
			logger.error("Exception while  prepare PayURequestParameter {}", e);
			throw new BadRequestException("Exception while  prepare PayURequestParameter {}" + e.getMessage());
		}

	}

	public static int getRoundOff(double inputValue) {
		return (int) Math.ceil(inputValue);
	}

	// truncate decimals if decimals value is 0 or return same
	public static String getDoubleToString(Double value) {

		long valueLong = value.longValue();
		Double valueDouble = new Double(valueLong);
		if (value.equals(valueDouble)) {
			return String.valueOf(valueLong);
		}
		return value.toString();
	}

	private String getPayUProductInfo(PayUProductInfo payUProductInfo) {

		try {
			return objectMapper.writeValueAsString(payUProductInfo);
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			return "";
		}

	}

	private void generatePayUHash(TransactionDetailVO transactionDetailVO, String payuSalt) {
		// JSON has truncating the .0 when double has .0 but toString()
		// returning .0
		// To solve the problem while hashing below check is implemented
		// String amount = getDoubleToString();
		logger.info("payuSalt: " + payuSalt + "payuKey: " + transactionDetailVO.getKey());

		String hash = transactionDetailVO.getKey() + "|" + transactionDetailVO.getTxnid() + "|"
				+ transactionDetailVO.getAmount().toString() + "|" + transactionDetailVO.getProductInfo() + "|"
				+ transactionDetailVO.getFirstName() + "|" + transactionDetailVO.getEmail() + "|"
				+ transactionDetailVO.getFormNumber() + "|" + transactionDetailVO.getModule() + "|||||||||" + payuSalt;

		logger.info(" Before hash {}", hash);

		transactionDetailVO.setHash(HashingUtil.sha512HashCal(hash));
	}

	@Override
	public PaymentGateWayResponse processResponse(PaymentGateWayResponse paymentGateWayResponse,
			Map<String, String> gatewayDetails) {
		PayUResponse payUResponse = new PayUResponse();

		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "key", payUResponse::setKey);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "mihpayid", payUResponse::setMihpayid);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "status", payUResponse::setStatus);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "bank_ref_num", payUResponse::setBank_ref_num);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "request_id", payUResponse::setRequest_id);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "amt", payUResponse::setAmt);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "amount", payUResponse::setAmount);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "cardCategory", payUResponse::setCardCategory);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "discount", payUResponse::setDiscount);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "transaction_amount",
				payUResponse::setTransaction_amount);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "txnid", payUResponse::setTxnid);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "additional_charges",
				payUResponse::setAdditional_charges);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "productinfo", payUResponse::setProductinfo);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "firstname", payUResponse::setFirstname);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "lastname", payUResponse::setLastname);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "address1", payUResponse::setAddress1);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "address2", payUResponse::setAddress2);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "city", payUResponse::setCity);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "email", payUResponse::setEmail);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "phone", payUResponse::setPhone);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "state", payUResponse::setState);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "country", payUResponse::setCountry);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "zipcode", payUResponse::setZipcode);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "bankcode", payUResponse::setBankcode);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "udf1", payUResponse::setUdf1);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "udf2", payUResponse::setUdf2);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "udf3", payUResponse::setUdf3);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "udf4", payUResponse::setUdf4);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "udf5", payUResponse::setUdf5);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "udf6", payUResponse::setUdf6);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "udf7", payUResponse::setUdf7);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "udf8", payUResponse::setUdf8);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "udf9", payUResponse::setUdf9);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "udf10", payUResponse::setUdf10);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "field1", payUResponse::setField1);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "field2", payUResponse::setField2);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "field3", payUResponse::setField3);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "field4", payUResponse::setField4);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "field5", payUResponse::setField5);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "field6", payUResponse::setField6);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "field7", payUResponse::setField7);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "field8", payUResponse::setField8);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "field9", payUResponse::setField9);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "card_type", payUResponse::setCard_type);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "error", payUResponse::setError);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "error_code", payUResponse::setError_code);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "error_Message", payUResponse::setError_Message);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "net_amount_debit", payUResponse::setNet_amount_debit);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "disc", payUResponse::setDisc);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "mode", payUResponse::setMode);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "PG_TYPE", payUResponse::setPgTYPE);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "card_no", payUResponse::setCard_no);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "name_on_card", payUResponse::setName_on_card);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "addedon", payUResponse::setAddedon);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "unmappedstatus", payUResponse::setUnmappedstatus);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Merchant_UTR", payUResponse::setMerchant_UTR);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Settled_At", payUResponse::setSettled_At);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "payment_source", payUResponse::setPayment_source);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "cardnum", payUResponse::setCardnum);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "cardhash", payUResponse::setCardhash);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "hash", payUResponse::setHash);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "payuMoneyId", payUResponse::setPayuMoneyId);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "additionalCharges",
				payUResponse::setAdditionalCharges);

		paymentGateWayResponse.setTransactionNo(payUResponse.getTxnid());
		paymentGateWayResponse.setAppTransNo(payUResponse.getUdf1());
		paymentGateWayResponse.setModuleCode(payUResponse.getUdf2());
		paymentGateWayResponse.setBankTranRefNumber(payUResponse.getBank_ref_num());
		paymentGateWayResponse.setPaymentStatus(PayStatusEnum.getPayStatusEnumByPayU(payUResponse.getStatus()));
		paymentGateWayResponse.setPayUResponse(payUResponse);

		logger.info(" PAYU paymentGateWayResponse {} ", payUResponse);

		String payUKey;
		String payUSalt;
		if (payUResponse.getUdf2().equals(ModuleEnum.SPNB.getCode())
				|| payUResponse.getUdf2().equals(ModuleEnum.SPNR.getCode())) {
			payUKey = gatewayDetails.get(PayUParams.SPNO_PAYU_KEY.getParamKey());
			payUSalt = gatewayDetails.get(PayUParams.SPNO_PAYU_SALT.getParamKey());
		} else if (payUResponse.getUdf2().equals(ModuleEnum.REG.getCode())) {
			payUKey = gatewayDetails.get(PayUParams.PAYU_KEY.getParamKey());
			payUSalt = gatewayDetails.get(PayUParams.PAYUSALT.getParamKey());
		} else {
			payUKey = gatewayDetails.get(PayUParams.CITIZEN_PAYU_KEY.getParamKey());
			payUSalt = gatewayDetails.get(PayUParams.CITIZEN_PAYU_SALT.getParamKey());
		}

		if (!checkResponsePayUHash(payUResponse, payUKey, payUSalt)) {
			logger.error("PayU response hash  and caluclated hash not mached");
			throw new BadRequestException("PayU response hash  and caluclated hash not matched");
		}
		return paymentGateWayResponse;
	}

	public Boolean checkResponsePayUHash(PayUResponse payUResponse, String payUkey, String payUSalt) {
		// JSON has truncating the .0 when double has .0 but toString()
		// returning .0
		// To solve the problem while hashing below check is implemented
		// String amount =
		// NumberParser.getDoubleToString(transactionDetailModel.getAmount());
		String reHash = null;

		// This block recomended for test enviorement
		if (StringUtils.isEmpty(payUResponse.getAdditionalCharges())) {
			reHash = payUSalt + "|" + payUResponse.getStatus() + "|||||||||" + payUResponse.getUdf2() + "|"
					+ payUResponse.getUdf1() + "|" + payUResponse.getEmail() + "|" + payUResponse.getFirstname() + "|"
					+ payUResponse.getProductinfo() + "|" + payUResponse.getAmount() + "|" + payUResponse.getTxnid()
					+ "|" + payUkey;
		}

		// This block recomended for Production enviorement
		else {
			reHash = payUResponse.getAdditionalCharges() + "|" + payUSalt + "|" + payUResponse.getStatus() + "|||||||||"
					+ payUResponse.getUdf2() + "|" + payUResponse.getUdf1() + "|" + payUResponse.getEmail() + "|"
					+ payUResponse.getFirstname() + "|" + payUResponse.getProductinfo() + "|" + payUResponse.getAmount()
					+ "|" + payUResponse.getTxnid() + "|" + payUkey;
		}
		String caluclatedRehash = HashingUtil.sha512HashCal(reHash);
		logger.info(" payUkey 12" + payUkey);
		logger.info("payUSalt 12" + payUSalt);
		logger.info("hash String vlaue" + reHash);
		logger.info("PayU hash:" + payUResponse.getHash());
		logger.info("Hash value calculated:" + caluclatedRehash);
		return caluclatedRehash.equals(payUResponse.getHash());

	}

	private void funPoint(Map<String, String[]> mapValues, String key, Consumer<String> consumer) {

		if (mapValues.containsKey(key)) {
			String[] values = mapValues.get(key);
			if (ArrayUtils.isNotEmpty(values)) {
				consumer.accept(values[0]);
			}
		}
	}

	@Override
	public PaymentGateWayResponse processVerify(PaymentTransactionDTO paymentTransactionDTO) {

		String transactionId = paymentTransactionDTO.getTransactioNo();
		String moduleCode = paymentTransactionDTO.getModuleCode();
		String applicationNo = paymentTransactionDTO.getApplicationFormRefNum();
		logger.info("Doing PayUVerifyProcess for transaction id: {}", transactionId);
		GateWayDTO gatewayValue = gatewayDao.findByGateWayType(GatewayTypeEnum.PAYU);

		HttpHeaders requestHeadersDeatils = new HttpHeaders();
		requestHeadersDeatils.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		String payUkey;

		if (ModuleEnum.SPNB.getCode().equalsIgnoreCase(moduleCode)
				|| ModuleEnum.SPNR.getCode().equalsIgnoreCase(moduleCode)) {
			requestHeadersDeatils.add("Authorization",
					gatewayValue.getGatewayDetails().get(PayUParams.SPNO_PAYU_AUTHORIZATION.getParamKey()));
			payUkey = gatewayValue.getGatewayDetails().get(PayUParams.SPNO_PAYU_KEY.getParamKey());
		} else if (ModuleEnum.REG.getCode().equals(moduleCode)) {
			requestHeadersDeatils.add("Authorization",
					gatewayValue.getGatewayDetails().get(PayUParams.PAYU_AUTHROIZATION.getParamKey()));
			payUkey = gatewayValue.getGatewayDetails().get(PayUParams.PAYU_KEY.getParamKey());
		} else {
			if (StringUtils.isNotBlank(paymentTransactionDTO.getPayUSalt())) {
				requestHeadersDeatils.add("Authorization", paymentTransactionDTO.getPayUAuthorizationHeader());
				payUkey = paymentTransactionDTO.getPayUKey();
			} else {
				requestHeadersDeatils.add("Authorization",
						gatewayValue.getGatewayDetails().get(PayUParams.CITIZEN_PAYU_AUTHORIZATION.getParamKey()));
				payUkey = gatewayValue.getGatewayDetails().get(PayUParams.CITIZEN_PAYU_KEY.getParamKey());

			}
		}

		MultiValueMap<String, String> multiValueMap = getPayUVerifyReqParams(transactionId, payUkey);
		HttpEntity<MultiValueMap<String, String>> payuRequest = new HttpEntity<MultiValueMap<String, String>>(
				multiValueMap, requestHeadersDeatils);
		PaymentVerifyRequest payVerifyReq = setVerifyPaymentRequest(multiValueMap);
		try {
			ResponseEntity<PayUVerifyResponse> response = paymentRestTemplate.postForEntity(
					gatewayValue.getGatewayDetails().get(PayUParams.PAYU_VERIFYURL.getParamKey()), payuRequest,
					PayUVerifyResponse.class);
			logger.info("response status from payu verify: {}", response.getStatusCode());
			if (response.hasBody()) {
				logger.info("payU responce body: {}", response.getBody());
				PayUVerifyResponse payUVerifyResponse = response.getBody();
				List<PayUVerifyResponseTransaction> result = payUVerifyResponse.getResult();
				PaymentGateWayResponse paymentGateWayResponse = new PaymentGateWayResponse();
				paymentGateWayResponse.setResponceString(response.getBody().toString());
				PayUResponse payUResponse;
				if (CollectionUtils.isNotEmpty(result)) {
					PayUVerifyResponseTransaction payUVerifyResponseTransaction = result.get(0);
					payUResponse = payUVerifyResponseTransaction.getPostBackParam();
					paymentGateWayResponse.setIsHashValidationSucess(Boolean.TRUE);
					//GateWayDTO gatewayValue = gatewayDao.findByGateWayType(GatewayTypeEnum.PAYU);
					Map<String, String> gatewayDetails = gatewayValue.getGatewayDetails();
					String payUKey;
					String payUSalt;
					if (ModuleEnum.SPNB.getCode().equals(moduleCode) || ModuleEnum.SPNR.getCode().equals(moduleCode)) {
						payUKey = gatewayDetails.get(PayUParams.SPNO_PAYU_KEY.getParamKey());
						payUSalt = gatewayDetails.get(PayUParams.SPNO_PAYU_SALT.getParamKey());
					}

					else if (ModuleEnum.REG.getCode().equals(moduleCode)) {
						payUKey = gatewayDetails.get(PayUParams.PAYU_KEY.getParamKey());
						payUSalt = gatewayDetails.get(PayUParams.PAYUSALT.getParamKey());
					}

					else {
						if (StringUtils.isBlank(paymentTransactionDTO.getPayUSalt())) {
							payUKey = gatewayDetails.get(PayUParams.CITIZEN_PAYU_KEY.getParamKey());
							payUSalt = gatewayDetails.get(PayUParams.CITIZEN_PAYU_SALT.getParamKey());
						} else {
							payUKey = paymentTransactionDTO.getPayUKey();
							payUSalt = paymentTransactionDTO.getPayUSalt();
						}
					}

					if (!checkResponsePayUHash(payUResponse, payUKey, payUSalt)) {
						if (!payUResponse.getStatus().equals(PayStatusEnum.PayU.FAILD.getDescription())) {
							logger.error("PayU response hash  and caluclated hash not mached");
							throw new BadRequestException("PayU response hash and caluclated hash not matched");
						}
						paymentGateWayResponse.setIsHashValidationSucess(Boolean.FALSE);
					}
					paymentGateWayResponse.setAppTransNo(payUResponse.getUdf1());// applicant from id
					paymentGateWayResponse.setModuleCode(payUResponse.getUdf2());// Service Code
					paymentGateWayResponse.setTransactionNo(payUResponse.getTxnid());
					paymentGateWayResponse.setBankTranRefNumber(payUResponse.getBank_ref_num());
					paymentGateWayResponse
							.setPaymentStatus(PayStatusEnum.getPayStatusEnumByPayU(payUResponse.getStatus()));
				} else {
					logger.warn("payU responce body result null ");
					payUResponse = new PayUResponse();
					payUResponse.setError_Message(payUVerifyResponse.getMessage());
					paymentGateWayResponse.setPaymentStatus(PayStatusEnum.FAILURE);
					paymentGateWayResponse.setTransactionNo(transactionId);
				}
				paymentGateWayResponse.setPayUResponse(payUResponse);
				logger.info("logger.warn message:{}", payUVerifyResponse.getMessage());
				paymentGateWayResponse.setGatewayTypeEnum(GatewayTypeEnum.PAYU);
				paymentGateWayResponse.setPaymentVerifyRequest(payVerifyReq);
				return paymentGateWayResponse;
			}
			throw new BadRequestException("No respopnce from Gateway for Application No [" + applicationNo + "]");

		} catch (RestClientException rce) {
			throw new BadRequestException("Exception while Connecting to SBI Gateway [" + rce.getMessage() + "]");

		} catch (Exception e) {
			throw new BadRequestException("Exception :[" + e.getMessage() + "]");
		}

	}

	private PaymentVerifyRequest setVerifyPaymentRequest(MultiValueMap<String, String> multiValueMap) {
		Map<String,String> reqParameters = new HashMap<String,String>();
		PaymentVerifyRequest payVerifyReq = new PaymentVerifyRequest();
		Iterator<String> it = multiValueMap.keySet().iterator();
		while (it.hasNext()) {
			String theKey = it.next();
			reqParameters.put(theKey, multiValueMap.getFirst(theKey));
		}
		payVerifyReq.setPayUReqParameters(reqParameters);
		return payVerifyReq;
	}

	private MultiValueMap<String, String> getPayUVerifyReqParams(String transactionId, String payUkey) {
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("merchantKey", payUkey);
		map.add("merchantTransactionIds", transactionId);
		return map;
	}

	private String getDDOCode(String officeCode) {

		Optional<OfficeDTO> model = officeDAO.findByOfficeCode(officeCode);
		if (model.isPresent()) {
			return model.get().getDdoCode();
		}
		logger.warn("Office details not found based on input officeCOde: {} ", officeCode);
		return null;

	}

	@Override
	public String dncryptSBIData(String data) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PaymentGateWayResponse cancellationOfTransaction(PaymentTransactionDTO payTransctionDTO) {
		// TODO Auto-generated method stub
		return null;
	}

}