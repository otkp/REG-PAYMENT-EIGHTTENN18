package org.epragati.payments.service.impl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.epragati.exception.BadRequestException;
import org.epragati.master.dao.GateWayDAO;
import org.epragati.master.dao.OfficeDAO;
import org.epragati.master.dto.GateWayDTO;
import org.epragati.master.dto.OfficeDTO;
import org.epragati.payment.dto.PaymentTransactionDTO;
import org.epragati.payments.service.ChalanDetailsService;
import org.epragati.payments.service.PaymentGateWay;
import org.epragati.payments.vo.FeePartsDetailsVo;
import org.epragati.payments.vo.PaymentGateWayResponse;
import org.epragati.payments.vo.SBIOtherTransactionsParts;
import org.epragati.payments.vo.SBIResponce;
import org.epragati.payments.vo.TransactionDetailVO;
import org.epragati.payments.vo.TreasureHeadDetails;
import org.epragati.sequence.SequenceGenerator;
import org.epragati.util.document.Sequence;
import org.epragati.util.exception.SequenceGenerateException;
import org.epragati.util.payment.AESEncrypt;
import org.epragati.util.payment.ChecksumMD5;
import org.epragati.util.payment.GatewayTypeEnum;
import org.epragati.util.payment.GatewayTypeEnum.SBIParams;
import org.epragati.util.payment.ModuleEnum;
import org.epragati.util.payment.PayStatusEnum;
import org.epragati.util.payment.ServiceCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * @author sairam.cheruku
 *
 */
@Service
@Qualifier("sbiGateway")
public class SBIGateWay implements PaymentGateWay {

	private static final Logger log = LoggerFactory.getLogger(SBIGateWay.class);

	@Value("${sbi.key.location}")
	private String sbiKeyPath;

	@Autowired
	private GateWayDAO gatewayDao;

	@Autowired
	private OfficeDAO officeDAO;

	@Autowired
	private SequenceGenerator sequencenGenerator;

	@Autowired
	private RestTemplate paymentRestTemplate;

	@Value("${isInTestPayment:}")
	private Boolean isInTestPayment;

	@Value("1.0")
	private Double testAmount;

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

	public TransactionDetailVO getRequestParameter(TransactionDetailVO transactionDetailVO) {
		GateWayDTO gatewayValue = gatewayDao.findByGateWayType(GatewayTypeEnum.SBI);
		Map<String, String> gatewayDetails = gatewayValue.getGatewayDetails();
		if (validateInputs(transactionDetailVO)) {
			transactionDetailVO.setEmail(transactionDetailVO.getEmail());
			transactionDetailVO.setPhone(transactionDetailVO.getPhone());
			transactionDetailVO.setFirstName(transactionDetailVO.getFirstName());

			// creating UUID
			String enceData = setSBIRequestParameters(transactionDetailVO, gatewayValue.getGatewayDetails());// "productinfo";
			transactionDetailVO.setEncdata(enceData);

			// need to change static value
			transactionDetailVO.setKey(gatewayDetails.get(GatewayTypeEnum.PayUParams.PAYU_KEY.getParamKey()));
			return transactionDetailVO;
		}
		throw new BadRequestException("Required inputs should not be empty empty");
	}

	private Boolean validateInputs(TransactionDetailVO transactionDetailVO) {
		// TODO :need handle some data validations
		return Boolean.TRUE;

	}

	/**
	 * Preparing an request object for sbi
	 * 
	 * @param transactionDetailVO
	 * @param gateWayDetails
	 * @return to
	 */
	private String setSBIRequestParameters(TransactionDetailVO transactionDetailVO,
			Map<String, String> gateWayDetails) {
		List<String> hsrpCessFeeinOtherCharges = new ArrayList<>();
		hsrpCessFeeinOtherCharges.add(ServiceCodeEnum.HSRP_FEE.getTypeDesc());
		hsrpCessFeeinOtherCharges.add(ServiceCodeEnum.CESS_FEE.getTypeDesc());

		List<Long> challanSeries = new ArrayList<>();
		String redirecturl = null;

		if (transactionDetailVO.getModule().equalsIgnoreCase(ModuleEnum.REG.getCode())) {
			transactionDetailVO.setSucessUrl(gateWayDetails.get(GatewayTypeEnum.SBIParams.REDIRECT_URL.getParamKey()));
			redirecturl = gateWayDetails.get(GatewayTypeEnum.SBIParams.REDIRECT_URL.getParamKey());
		} else {
			transactionDetailVO.setSucessUrl(
					gateWayDetails.get(GatewayTypeEnum.SBIParams.BODY_BUILDER_REDIRECT_URL.getParamKey()));
			redirecturl = gateWayDetails.get(GatewayTypeEnum.SBIParams.BODY_BUILDER_REDIRECT_URL.getParamKey());
		}

		List<TreasureHeadDetails> treasureHeads = new ArrayList<>();
		List<SBIOtherTransactionsParts> partsDetails = new ArrayList<>();
		// getting DDO code based on office code
		String ddocode = getDDOCode(transactionDetailVO.getOfficeCode());
		// getting challa start number
		int challanSize = transactionDetailVO.getFeePartsMap().size();
		if (transactionDetailVO.getFeePartsMap().keySet().contains(ServiceCodeEnum.CESS_FEE.getTypeDesc())) {
			challanSize = challanSize - 1;
		}
		if (transactionDetailVO.getFeePartsMap().keySet().contains(ServiceCodeEnum.HSRP_FEE.getTypeDesc())) {
			challanSize = challanSize - 1;
		}
		Long challanNumber = getChallanNumber(challanSize);
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

		for (Map.Entry<String, FeePartsDetailsVo> entry : transactionDetailVO.getFeePartsMap().entrySet()) {

			if (!hsrpCessFeeinOtherCharges.contains(entry.getKey())) {
				TreasureHeadDetails treasureHeadDetails = new TreasureHeadDetails();
				if (entry.getValue() == null) {
					continue;
				}
				treasureHeadDetails.setAmount(entry.getValue().getAmount());
				treasureHeadDetails.setChallanNo(++challanNumber);
				treasureHeadDetails.setDdoCode(ddocode);
				treasureHeadDetails.setDeptCode(gateWayDetails.get(SBIParams.DEPTCODE.getParamKey()));
				treasureHeadDetails.setHeadOfAccount(entry.getValue().getHOA());
				treasureHeadDetails.setTxtId(transactionDetailVO.getTxnid());
				treasureHeadDetails.setRemitterName(transactionDetailVO.getFirstName());
				treasureHeadDetails.setHoaDesc(entry.getKey());
				treasureHeadDetails.setCreditAccount(entry.getValue().getCredit_Account());
				challanSeries.add(treasureHeadDetails.getChallanNo());
				treasureHeads.add(treasureHeadDetails);
			} else {
				SBIOtherTransactionsParts parts = new SBIOtherTransactionsParts();
				parts.setOtherTransactionsDescription(entry.getKey());
				parts.setAmount(entry.getValue().getAmount());
				parts.setCreditAccount(entry.getValue().getCredit_Account());
				partsDetails.add(parts);
			}

		}

		// chalanDetailsService.chalanDetailsSavingAndVerificationForTransaction(challanSeries,
		// transactionDetailVO);

		StringBuffer productValue = new StringBuffer();

		productValue.append("Transaction_No=" + treasureHeads.get(0).getChallanNo());
		productValue.append("|Dept_code=" + gateWayDetails.get(SBIParams.DEPTCODE.getParamKey()));
		productValue.append("|Dept_Name=" + gateWayDetails.get(SBIParams.DEPTNAME.getParamKey()));
		productValue.append("|Name_of_the_Remitter=" + transactionDetailVO.getFirstName().toString());
		productValue.append("|appTransNo=" + transactionDetailVO.getFormNumber());
		int j = 1;
		int k = 1;
		int l = 6;
		Long challanNumberValue = null;
		for (int i = 0; i < treasureHeads.size(); i++) {
			if (!hsrpCessFeeinOtherCharges.contains(treasureHeads.get(i).getHoaDesc())) {
				if (i == 0) {
					productValue.append("|Department_TransID_" + (j) + "=" + transactionDetailVO.getTxnid());
				} else {
					productValue.append("|Department_TransID_" + (j) + "=" + transactionDetailVO.getTxnid() + "_" + j);
				}
				if (challanNumberValue == null) {
					challanNumberValue = treasureHeads.get(i).getChallanNo();
				}
				productValue.append("|Challan_No_" + (j) + "=" + treasureHeads.get(i).getChallanNo());
				productValue.append("|HOA_" + (j) + "_Description=" + treasureHeads.get(i).getHoaDesc());
				if (treasureHeads.get(i).getHeadOfAccount() != null) {
					productValue.append("|HOA_" + (j) + "=" + treasureHeads.get(i).getHeadOfAccount());
				} else {
					productValue.append("|HOA_" + (j) + "=" + "00410010100021411212000NVN");
				}
				productValue.append("|Amount_" + (j) + "=" + treasureHeads.get(i).getAmount());
				productValue.append("|Credit_Account_" + (j) + "=" + treasureHeads.get(i).getCreditAccount());
				j++;
			}

		}

		for (int i = 0; i < partsDetails.size(); i++) {
			if (hsrpCessFeeinOtherCharges.contains(partsDetails.get(i).getOtherTransactionsDescription())) {
				if (partsDetails.get(i).getOtherTransactionsDescription()
						.equalsIgnoreCase(ServiceCodeEnum.HSRP_FEE.getTypeDesc())) {
					productValue
							.append("|Other_Charges_TID_" + (k) + "=" + "HSRP" + transactionDetailVO.getFormNumber());
				} else if (partsDetails.get(i).getOtherTransactionsDescription()
						.equalsIgnoreCase(ServiceCodeEnum.CESS_FEE.getTypeDesc())) {
					productValue
							.append("|Other_Charges_TID_" + (k) + "=" + "CESS" + transactionDetailVO.getFormNumber());
				} else {
					productValue.append("|Other_Charges_TID_" + (k) + "=" + treasureHeads.get(i).getTxtId());
				}
				productValue.append("|Other_charges_Description_" + (k) + "="
						+ partsDetails.get(i).getOtherTransactionsDescription());
				k++;
				productValue.append("|Amount_" + (l) + "=" + partsDetails.get(i).getAmount());
				productValue.append("|Credit_Account_" + (l) + "=" + partsDetails.get(i).getCreditAccount());
				l++;
			}

		}
		productValue.append("|Check_Total=" + transactionDetailVO.getAmount().toString());
		productValue.append("|DDO_Code=" + ddocode);
		productValue.append("|Mode_of_Transaction=" + gateWayDetails.get(SBIParams.MODE_OF_TRANSACTION.getParamKey()));
		productValue.append("|Redirect_URL=" + redirecturl);
		transactionDetailVO.setSbiTransactionNumber(challanNumberValue.toString());
		transactionDetailVO.setProductInfo(productValue.toString());
		String eData = getEncryption(productValue.toString());
		log.debug("checksum:" + productValue.toString());
		transactionDetailVO.setEncdata(eData);
		transactionDetailVO.setMarchantCode(gateWayDetails.get(SBIParams.MARCHANTCODE.getParamKey()));
		transactionDetailVO.setPaymentUrl(gateWayDetails.get(SBIParams.SBI_PAYMENT_URL.getParamKey()));
		return eData;
	}

	public static int getRoundOff(double inputValue) {
		return (int) Math.ceil(inputValue);
	}

	/**
	 * 
	 * @param requiredNumber
	 * @return to get challan numbers
	 */

	private Long getChallanNumber(Integer requiredNumber) {
		Long number = 0L;
		try {
			Map<String, String> hMap = new HashMap<>();
			hMap.put("INCR", String.valueOf(requiredNumber));
			number = Long.parseLong(sequencenGenerator.getSequence(Sequence.CHALLAN.getSequenceId().toString(), hMap));
		} catch (SequenceGenerateException e) {
			log.error(" Challan SequenceGenerateException  {}", e);

		} catch (Exception e) {
			log.error(" exception While chalana generation {}", e);
		}
		log.info("Challan number ganarated, final number :{}", number);
		// 1000
		// 997
		return (number - requiredNumber);
	}

	/**
	 * 
	 * @param officeCode
	 * @return to get DDO code
	 */
	private String getDDOCode(String officeCode) {
		Optional<OfficeDTO> model = officeDAO.findByOfficeCode(officeCode);
		if (model.isPresent()) {
			// TODO: Need to change office code to ddo code while commit the
			// code
			return model.get().getDdoCode();
		}
		log.warn("Office details not found based on input officeCOde: {} ", officeCode);
		return null;

	}

	/**
	 * This method is used to prepare an encrypt object to send to SBI
	 * 
	 * @param requestdata
	 * @return encryption data for sbi
	 */
	@Override
	public String getEncryption(String requestData) {
		String checkdata = "";
		@SuppressWarnings("unused")
		InputStream input = null;
		ChecksumMD5 checksum = new ChecksumMD5();
		try {
			checkdata = checksum.getValue(requestData);
			requestData = requestData + "|checkSum=" + checkdata;
			log.debug("checksumData" + requestData);
		} catch (Exception e) {
			log.error("encryptedSBIParameter Error: {}", e.getMessage());
		}
		AESEncrypt encrpt = new AESEncrypt();

		ClassLoader classLoader = getClass().getClassLoader();
		try {
			input = new FileInputStream(classLoader.getResource(sbiKeyPath).getFile());
		} catch (FileNotFoundException e) {
			log.error("Unable to find the file {}", sbiKeyPath);
			e.printStackTrace();
		}
		encrpt.setSecretKey(classLoader.getResource(sbiKeyPath).getFile());
		String eData = encrpt.encryptFile(requestData);
		log.debug(" encryptedSBIParameter Object: {}", eData);
		return eData;
	}

	/**
	 * This method is used to prepare the checksum for SBI verify payment
	 * 
	 * @param amount
	 * @param transactionId
	 * @return
	 */
	private String verificationWithSBIGateway(Double amount, String transactionId) {
		GateWayDTO gatewayValue = gatewayDao.findByGateWayType(GatewayTypeEnum.SBI);
		Map<String, String> gateWayDetails = gatewayValue.getGatewayDetails();
		Double amountValue = null;
		if (isInTestPayment != null && isInTestPayment) {
			amountValue = testAmount;
		} else {
			amountValue = amount;
		}

		String data = "Transaction_amount=" + amountValue + "|Transaction_No=" + transactionId + "|Redirect_URL="
				+ gateWayDetails.get(SBIParams.REDIRECT_URL.getParamKey());

		String eData = getEncryption(data);

		return eData;
	}

	/**
	 * @param amount
	 * @param transactionId
	 *            SBI verification Process for payment pending conditions
	 * 
	 */

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public PaymentGateWayResponse processVerify(PaymentTransactionDTO paymentTransactionDTO) {

		String transactionId = paymentTransactionDTO.getSbiTransactionNumber();
		String applicationNo = paymentTransactionDTO.getApplicationFormRefNum();
		Double grandTotalFees = 0d;

		if (paymentTransactionDTO.getModuleCode() != null
				&& (paymentTransactionDTO.getModuleCode().equalsIgnoreCase(ModuleEnum.CITIZEN.getCode())
						|| paymentTransactionDTO.getModuleCode().equalsIgnoreCase(ModuleEnum.BODYBUILDER.getCode())
						|| paymentTransactionDTO.getModuleCode().equalsIgnoreCase(ModuleEnum.ALTERVEHICLE.getCode()))) {
			grandTotalFees = paymentTransactionDTO.getFeeDetailsDTO().getTotalFees();
		} else {
			grandTotalFees = paymentTransactionDTO.getBreakPaymentsSave().getGrandTotalFees();
		}

		log.info("Doing SBI VerifyProcess for transaction id: {}", transactionId);
		GateWayDTO gatewayValue = gatewayDao.findByGateWayType(GatewayTypeEnum.SBI);
		Map<String, String> gateWayDetails = gatewayValue.getGatewayDetails();
		HttpHeaders headers = new HttpHeaders();
		MultiValueMap<String, String> map = null;
		HttpEntity<MultiValueMap<String, String>> entity = null;

		String eData = verificationWithSBIGateway(grandTotalFees, transactionId);

		map = new LinkedMultiValueMap();
		map.add("encdata", eData);
		map.add("merchant_code", gateWayDetails.get(SBIParams.MARCHANTCODE.getParamKey()));
		entity = new HttpEntity(map, headers);
		ResponseEntity<String> response = paymentRestTemplate.exchange(
				gateWayDetails.get(SBIParams.SBIVERIFICATIONURL.getParamKey()), HttpMethod.POST, entity, String.class,
				new Object[0]);
		try {

			if (response.hasBody()) {
				log.debug("SBI responce body: {}", response.getBody());
				String decryptData = ((String) response.getBody()).substring(
						((String) response.getBody()).indexOf("value=") + 7,
						((String) response.getBody()).indexOf("\"/>"));
				String decrypt = dncryptSBIData(decryptData);
				Map<String, String[]> splitingMap = getSliptingofSbiVerifyValues(decrypt);

				PaymentGateWayResponse paymentGateWayResponse = new PaymentGateWayResponse();
				paymentGateWayResponse.setResponceString(splitingMap.toString());
				paymentGateWayResponse.setGatewayTypeEnum(GatewayTypeEnum.SBI);
				paymentGateWayResponse.setGatewayResponceMap(splitingMap);
				paymentGateWayResponse = processResponseHandlingForVerify(paymentGateWayResponse, gateWayDetails);

				log.info("SBI- Verify Payment Status GateWay {[]}: Application No [{}] Status :[{}]",
						paymentGateWayResponse.getGatewayTypeEnum().getDescription(),
						paymentTransactionDTO.getApplicationFormRefNum(), paymentGateWayResponse.getPaymentStatus());

				SBIResponce sbiResponse = paymentGateWayResponse.getSbiResponce();

				if (sbiResponse != null) {
					paymentGateWayResponse.setIsHashValidationSucess(Boolean.TRUE);
					paymentGateWayResponse.setTransactionNo(sbiResponse.getTransaction_No());
					paymentGateWayResponse.setBankTranRefNumber(sbiResponse.getSbi_ref_no());
					paymentGateWayResponse
							.setPaymentStatus(PayStatusEnum.getPayStatusEnumByPayU(sbiResponse.getStatus()));

				} else {
					log.warn("SBI responce body result null for transactionId [{}] , Application No [{}]",
							transactionId, paymentTransactionDTO.getApplicationFormRefNum());
					sbiResponse = new SBIResponce();
					paymentGateWayResponse.setPaymentStatus(PayStatusEnum.FAILURE);
					paymentGateWayResponse.setTransactionNo(transactionId);
				}
				paymentGateWayResponse.setSbiResponce(sbiResponse);
				paymentGateWayResponse.setGatewayTypeEnum(GatewayTypeEnum.SBI);
				return paymentGateWayResponse;
			}

			throw new BadRequestException("No respopnce from SBI Gateway for Application No [" + applicationNo + "]");

		} catch (RestClientException rce) {
			throw new BadRequestException("Exception while Connecting to SBI Gateway [" + rce.getMessage() + "]");

		} catch (Exception e) {
			throw new BadRequestException("Exception :[" + e.getMessage() + "]");
		}

	}

	/**
	 * 
	 * @param paymentGateWayResponse
	 * @param gateWayDetails
	 * @return sbi responce saving from verify Payment
	 */

	private PaymentGateWayResponse processResponseHandlingForVerify(PaymentGateWayResponse paymentGateWayResponse,
			Map<String, String> gateWayDetails) {
		SBIResponce sbiResponce = new SBIResponce();
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "sbi_ref_id", sbiResponce::setSbi_ref_no);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Transaction_amount",
				sbiResponce::setTransaction_amount);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Transaction_No", sbiResponce::setTransaction_No);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Status", sbiResponce::setStatus);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Status_desc", sbiResponce::setStatus_desc);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "checkSum", sbiResponce::setCheckSum);
		paymentGateWayResponse.setSbiResponce(sbiResponce);
		return paymentGateWayResponse;
	}

	/**
	 * @param paymentGateWayResponse
	 * @param gateWayDetails
	 * @return saving of SBI responce
	 */
	@Override
	public PaymentGateWayResponse processResponse(PaymentGateWayResponse paymentGateWayResponse,
			Map<String, String> gatewayDetails) {
		SBIResponce sbiResponce = new SBIResponce();
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "sbi_ref_no", sbiResponce::setSbi_ref_no);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Challan_No_1", sbiResponce::setChallan_No_1);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Status", sbiResponce::setStatus);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Status_desc", sbiResponce::setStatus_desc);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Check_Total", sbiResponce::setCheck_Total);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Dept_Name", sbiResponce::setDept_Name);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Name_of_the_Remitter",
				sbiResponce::setNameOfRemitter);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Department_TransID_1",
				sbiResponce::setDepartment_TransID_1);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "HOA_1", sbiResponce::sethOA_1);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "HOA_1_Description",
				sbiResponce::sethOA_1_Description);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Amount_1", sbiResponce::setAmount_1);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Credit_Account_1", sbiResponce::setCredit_Account_1);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Department_TransID_2",
				sbiResponce::setDepartment_TransID_2);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "HOA_2", sbiResponce::sethOA_2);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "HOA_2_Description",
				sbiResponce::sethOA_2_Description);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Amount_2", sbiResponce::setAmount_2);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Credit_Account_2", sbiResponce::setCredit_Account_2);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Department_TransID_3",
				sbiResponce::setDepartment_TransID_3);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "HOA_3", sbiResponce::sethOA_3);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "HOA_3_Description",
				sbiResponce::sethOA_3_Description);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Amount_3", sbiResponce::setAmount_3);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Credit_Account_3", sbiResponce::setCredit_Account_3);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Department_TransID_4",
				sbiResponce::setDepartment_TransID_4);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "HOA_4", sbiResponce::sethOA_4);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "HOA_4_Description",
				sbiResponce::sethOA_4_Description);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Amount_4", sbiResponce::setAmount_4);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Credit_Account_4", sbiResponce::setCredit_Account_4);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Department_TransID_5",
				sbiResponce::setDepartment_TransID_5);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "HOA_5", sbiResponce::sethOA_5);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "HOA_5_Description",
				sbiResponce::sethOA_5_Description);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Amount_5", sbiResponce::setAmount_5);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Credit_Account_5", sbiResponce::setCredit_Account_5);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Other_Charges_TID_1",
				sbiResponce::setOther_Charges_TID_1);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Other_charges_Description_1",
				sbiResponce::setOther_charges_Description_1);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Amount_6", sbiResponce::setAmount_6);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Credit_Account_6", sbiResponce::setCredit_Account_6);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Other_Charges_TID_2",
				sbiResponce::setOther_Charges_TID_2);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Other_charges_Description_2",
				sbiResponce::setOther_charges_Description_2);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Amount_7", sbiResponce::setAmount_7);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Credit_Account_7", sbiResponce::setCredit_Account_7);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "PG_TYPE", sbiResponce::setPgTYPE);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "card_no", sbiResponce::setCard_no);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "name_on_card", sbiResponce::setName_on_card);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "addedon", sbiResponce::setAddedon);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "unmappedstatus", sbiResponce::setUnmappedstatus);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Merchant_UTR", sbiResponce::setMerchant_UTR);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Settled_At", sbiResponce::setSettled_At);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "payment_source", sbiResponce::setPayment_source);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "cardnum", sbiResponce::setCardnum);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "cardhash", sbiResponce::setCardhash);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "checkSum", sbiResponce::setHash);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "payuMoneyId", sbiResponce::setSbiRefId);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "additionalCharges",
				sbiResponce::setAdditionalCharges);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Mode_of_Transaction",
				sbiResponce::setMode_of_Transaction);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "Redirect_URL", sbiResponce::setRedirect_URL);
		funPoint(paymentGateWayResponse.getGatewayResponceMap(), "ref_no", sbiResponce::setRef_no);
		paymentGateWayResponse.setPaymentStatus(PayStatusEnum.getPayStatusEnumByPayU(sbiResponce.getStatus()));
		paymentGateWayResponse.setSbiResponce(sbiResponce);
		;

		log.debug(" SBI paymentGateWayResponse {} for AppTransNo [{}]", sbiResponce, sbiResponce.getAppTransNo());

		return paymentGateWayResponse;

	}

	private void funPoint(Map<String, String[]> mapValues, String key, Consumer<String> consumer) {

		if (mapValues.containsKey(key)) {
			String[] values = mapValues.get(key);
			if (ArrayUtils.isNotEmpty(values)) {
				consumer.accept(values[0]);
			}
		}
	}

	/**
	 * @param String
	 *            encrypted data from SBI
	 * @return decrytion of SBI data into checksum
	 */
	@Override
	public String dncryptSBIData(String data) {
		AESEncrypt encrpt = new AESEncrypt();
		@SuppressWarnings("unused")
		InputStream input = null;
		ClassLoader classLoader = getClass().getClassLoader();
		try {
			input = new FileInputStream(classLoader.getResource(sbiKeyPath).getFile());
		} catch (FileNotFoundException e) {
			log.error("Unable to find the file {}", sbiKeyPath);
			e.printStackTrace();
		}
		encrpt.setSecretKey(classLoader.getResource(sbiKeyPath).getFile());
		String eData = encrpt.decryptFile(data);
		return eData;
	}

	/**
	 * 
	 * @param checksum
	 *            after SBI decryption
	 * @return splitting the values of SBI for individual saving
	 */
	public Map<String, String[]> getSliptingofSbiVerifyValues(String value) {
		Map<String, String[]> map = new HashMap<>();
		if (value.contains("|")) {
			String[] keyValuePairs = value.split(Pattern.quote("|"));
			for (String pair : keyValuePairs) {
				String[] entry = pair.split(Pattern.quote("="));
				if (entry.length > 1) {
					map.put(entry[0].trim(), new String[] { entry[1].trim() });
				} else {
					map.put(entry[0].trim(), new String[] { "" });
				}

			}
		}
		log.debug("Spliting of SBI data: [{}]", map);
		return map;
	}

	@Override
	public PaymentGateWayResponse cancellationOfTransaction(PaymentTransactionDTO payTransctionDTO) {
		// TODO Auto-generated method stub
		return null;
	}
}
