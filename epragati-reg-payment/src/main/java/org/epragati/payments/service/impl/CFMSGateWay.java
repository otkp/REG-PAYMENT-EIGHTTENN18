package org.epragati.payments.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.epragati.common.dao.PropertiesDAO;
import org.epragati.common.dto.PropertiesDTO;
import org.epragati.exception.BadRequestException;
import org.epragati.master.dao.GateWayDAO;
import org.epragati.master.dao.OfficeDAO;
import org.epragati.master.dto.GateWayDTO;
import org.epragati.master.dto.OfficeDTO;
import org.epragati.payment.dto.PaymentTransactionDTO;
import org.epragati.payments.service.PaymentGateWay;
import org.epragati.payments.vo.CFMSResponce;
import org.epragati.payments.vo.CFMSVerifyPayVO;
import org.epragati.payments.vo.CfmsVeryifyPayRequestVO;
import org.epragati.payments.vo.FeePartsDetailsVo;
import org.epragati.payments.vo.PaymentGateWayResponse;
import org.epragati.payments.vo.TransactionDetailVO;
import org.epragati.util.payment.GatewayTypeEnum;
import org.epragati.util.payment.GatewayTypeEnum.CFMSParams;
import org.epragati.util.payment.ModuleEnum;
import org.epragati.util.payment.PayStatusEnum;
import org.epragati.util.payment.PayStatusEnum.CFMSGateWayVerifyPayEnum;
import org.epragati.util.payment.ServiceCodeEnum;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;

@Service
@Qualifier("cfmsGateWay")
public class CFMSGateWay implements PaymentGateWay {

	private static final Logger logger = LoggerFactory.getLogger(PayUGateWay.class);

	@Autowired
	private GateWayDAO gatewayDao;

	@Autowired
	private OfficeDAO officeDAO;

	@Autowired
	private PropertiesDAO propertiesDAO;

	@Autowired
	private RestTemplate cfmsRestTemplate;
	/*
	 * @Autowired private RestTemplate restTemplate;
	 */

	@Override
	public String getURL() {
		return null;
	}

	@Override
	public String getKey() {
		return null;
	}

	@Override
	public String getQueryParam() {
		return null;
	}

	@Override
	public String getEncryption(String requestData) {
		return null;
	}

	@Override
	public TransactionDetailVO getRequestParameter(TransactionDetailVO transactionDetailVO) {
		GateWayDTO gatewayValue = gatewayDao.findByGateWayType(GatewayTypeEnum.CFMS);
		Map<String, String> gatewayDetails = gatewayValue.getGatewayDetails();
		Optional<PropertiesDTO> serviceCode = propertiesDAO.findByModule(ModuleEnum.PAYMENTS.getCode());
		transactionDetailVO.setEmail(transactionDetailVO.getEmail());
		transactionDetailVO.setPhone(transactionDetailVO.getPhone());
		transactionDetailVO.setFirstName(transactionDetailVO.getFirstName());
		prepareCFMSRequestParameter(transactionDetailVO, gatewayDetails, serviceCode.get().getHoaServiceValue());
		return transactionDetailVO;

	}

	private void prepareCFMSRequestParameter(TransactionDetailVO transactionDetailVO,
			Map<String, String> gatewayDetails, Map<String, Integer> hoaServiceValues) {

		final String COMMA = ",";

		List<String> chList = new ArrayList<>();
		List<String> othList = new ArrayList<>();
		List<String> hsrpCessFeeinOtherCharges = new ArrayList<>();

		transactionDetailVO.setPaymentUrl(gatewayDetails.get(GatewayTypeEnum.CFMSParams.PAYMENTURL.getParamKey()));
		transactionDetailVO.setCfmsDc(gatewayDetails.get(GatewayTypeEnum.CFMSParams.DC.getParamKey()));
		transactionDetailVO.setRid(gatewayDetails.get(GatewayTypeEnum.CFMSParams.RID.getParamKey()));
		transactionDetailVO.setCfmsRn(gatewayDetails.get(GatewayTypeEnum.CFMSParams.RN.getParamKey()));
		transactionDetailVO.setCfmsRn(gatewayDetails.get(GatewayTypeEnum.CFMSParams.RN.getParamKey()));
		transactionDetailVO.setSucessUrl(gatewayDetails.get(GatewayTypeEnum.CFMSParams.REDIRECTURL.getParamKey()));
		hsrpCessFeeinOtherCharges.add(ServiceCodeEnum.HSRP_FEE.getTypeDesc());
		hsrpCessFeeinOtherCharges.add(ServiceCodeEnum.CESS_FEE.getTypeDesc());

		try {
			String ddocode = getDDOCode(transactionDetailVO.getOfficeCode());

			for (Map.Entry<String, FeePartsDetailsVo> entry : transactionDetailVO.getFeePartsMap().entrySet()) {
				if (!hsrpCessFeeinOtherCharges.contains(entry.getKey())) {
					StringBuilder chKeys = new StringBuilder();
					if (entry.getValue() == null) {
						continue;
					}

					String hoa = entry.getValue().getHOA();
					String serviceValue = hoaServiceValues.get(hoa).toString();
					// Replace With DB Call - Phani
					hoa = hoa.replace("NVN", "VN");
					chKeys.append(hoa).append(COMMA);
					chKeys.append(ddocode).append(COMMA);
					fillData(COMMA, chList, serviceValue, entry, chKeys);
				}
			}
			for (Map.Entry<String, FeePartsDetailsVo> entry : transactionDetailVO.getFeePartsMap().entrySet()) {
				StringBuilder othKeys = new StringBuilder();
				if (entry.getKey().equalsIgnoreCase(ServiceCodeEnum.HSRP_FEE.getTypeDesc())) {
					String creditAccount = entry.getValue().getCredit_Account();
					fillData(COMMA, othList, creditAccount, entry, othKeys);
				}
				if (entry.getKey().equalsIgnoreCase(ServiceCodeEnum.CESS_FEE.getTypeDesc())) {
					String cessCreditAccount = entry.getValue().getCredit_Account();
					fillData(COMMA, othList, cessCreditAccount, entry, othKeys);
				}
			}
			transactionDetailVO.setChList(chList);
			transactionDetailVO.setOthList(othList);
			transactionDetailVO.setAmount((double) getTotalamount(transactionDetailVO.getFeePartsMap()));
			transactionDetailVO.setCfmsTotal(getTotalamount(transactionDetailVO.getFeePartsMap()));
			transactionDetailVO.setProductInfo("CH :" + chList.toString() + "OTH :" + othList.toString());

		} catch (Exception e) {
			logger.error("Exception while preparing CFMS Request {}", e);
			throw new BadRequestException("Exception while  prepare CFMS Request {}" + e.getMessage());
		}

	}

	private void fillData(final String COMMA, List<String> othList, String ddocode,
			Map.Entry<String, FeePartsDetailsVo> entry, StringBuilder othKeys) {
		if (entry.getKey().equalsIgnoreCase(ServiceCodeEnum.HSRP_FEE.getTypeDesc())) {
			othKeys.append(entry.getValue().getCredit_Account()).append(COMMA);
		} else if (entry.getKey().equalsIgnoreCase(ServiceCodeEnum.CESS_FEE.getTypeDesc())) {
			othKeys.append(entry.getValue().getCredit_Account()).append(COMMA);
		} else {
			othKeys.append(ddocode).append(COMMA);
		}
		othKeys.append(Math.round(entry.getValue().getAmount()));
		othList.add(othKeys.toString());

	}

	@SuppressWarnings("unchecked")
	@Override
	public PaymentGateWayResponse processVerify(PaymentTransactionDTO paymentTransactionDTO) {
		String applicationNo = paymentTransactionDTO.getApplicationFormRefNum();
		GateWayDTO gatewayValue = gatewayDao.findByGateWayType(GatewayTypeEnum.CFMS);
		Map<String, String> gatewayDetails = gatewayValue.getGatewayDetails();
		ResponseEntity<String> response = null;
		String transactionNo = paymentTransactionDTO.getTransactioNo();
		String username = gatewayDetails.get(CFMSParams.USERNAME.getParamKey());
		String password = gatewayDetails.get(CFMSParams.PASSWORD.getParamKey());
		String recordSet = gatewayDetails.get(CFMSParams.RECORDSET.getParamKey());
		String row = gatewayDetails.get(CFMSParams.ROW.getParamKey());
		String others = gatewayDetails.get(CFMSParams.OTHERS.getParamKey());
		String challan = gatewayDetails.get(CFMSParams.CHALLAN.getParamKey());
		String nullEnabled = gatewayDetails.get(CFMSParams.ENABLENULLCHALLAN.getParamKey());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		StringBuilder sb = new StringBuilder();
		sb.append("{");
		sb.append("\"" + recordSet + "\" : {");
		sb.append("\"DEPTCODE\": \"" + gatewayDetails.get(CFMSParams.DC.getParamKey()) + "\",");
		sb.append("\"" + row + "\" : {");
		sb.append("\"DEPTTID\": \"" + transactionNo + "\"");
		sb.append("} } } ");

		HttpEntity<String> httpEntity = new HttpEntity<>(sb.toString(), headers);
		// RestTemplate restTmplate = new RestTemplate();
		cfmsRestTemplate.getInterceptors().add(new BasicAuthorizationInterceptor(username, password));
		try {
			long startTimeInMilli = System.currentTimeMillis();
			logger.info("TransactionNo:{} ,CFMS Verify Service consuming...,", transactionNo);
			response = cfmsRestTemplate.exchange(gatewayDetails.get(CFMSParams.VERIFY_URL.getParamKey()),
					HttpMethod.POST, httpEntity, String.class);
			logger.info("TransactionNo:{} ,Total execution time to verify: {}ms  ", transactionNo,
					(System.currentTimeMillis() - startTimeInMilli));

			if (response.hasBody()) {
				PaymentGateWayResponse paymentGateWayResponse = new PaymentGateWayResponse();
				JSONParser parser = new JSONParser();
				JSONObject jsonObj = (JSONObject) parser.parse(response.getBody());
				ArrayList<Map<String, String>> listOfOthers = new ArrayList<>();
				ArrayList<Map<String, String>> listOfChallans = new ArrayList<>();
				JSONObject jsonObjRecordset = (JSONObject) jsonObj.get(recordSet);
				JSONObject jsonObjRowSet = (JSONObject) jsonObjRecordset.get(row);
				paymentGateWayResponse.setResponceString(response.getBody().toString());
				
				addData(others, listOfOthers, jsonObjRowSet);
				addData(challan, listOfChallans, jsonObjRowSet);

				jsonObjRowSet.remove(others);
				jsonObjRowSet.remove(challan);
				jsonObjRowSet.put(others, listOfOthers);
				jsonObjRowSet.put(challan, listOfChallans);

				Gson gson = new Gson();
				CFMSVerifyPayVO payVO = gson.fromJson(jsonObj.toString(), CFMSVerifyPayVO.class);

				logger.info("CFMS Verify Response for application No [{}] & Body [{}]", applicationNo, jsonObj);
				paymentGateWayResponse = processResponseHandlingForVerify(paymentGateWayResponse, payVO, nullEnabled);
				paymentGateWayResponse.setAppTransNo(paymentTransactionDTO.getApplicationFormRefNum());// applicant
				paymentGateWayResponse.setModuleCode(paymentTransactionDTO.getModuleCode());// Service
				paymentGateWayResponse.setBankTranRefNumber(payVO.getRECORDSET().getROW().getBNKRF());

				paymentGateWayResponse.setGatewayTypeEnum(GatewayTypeEnum.CFMS);

				if (StringUtils.isBlank(payVO.getRECORDSET().getROW().getDEPTTID())
						&& (StringUtils.isBlank(payVO.getRECORDSET().getROW().getSTATUS())
								|| CFMSGateWayVerifyPayEnum.ABORTED_STATUS.getDescription()
										.equalsIgnoreCase(payVO.getRECORDSET().getROW().getSTATUS()))
						&& nullEnabled.equalsIgnoreCase("YES")) {
					paymentGateWayResponse.setTransactionNo(transactionNo);
					paymentGateWayResponse.setPaymentStatus(PayStatusEnum
							.getPayStatusEnumByCFMSVerifyPay(CFMSGateWayVerifyPayEnum.FAILURE.getDescription()));
				} else {
					paymentGateWayResponse.setTransactionNo(payVO.getRECORDSET().getROW().getDEPTTID());
					paymentGateWayResponse.setPaymentStatus(
							PayStatusEnum.getPayStatusEnumByCFMSVerifyPay(payVO.getRECORDSET().getROW().getSTATUS()));
				}
				paymentGateWayResponse.setIsDoubleVerified(Boolean.TRUE);
				return paymentGateWayResponse;
			}

			throw new BadRequestException("No respopnce from CFMS Gateway for Application No [" + applicationNo + "]");

		} catch (RestClientException rce) {
			throw new BadRequestException("Exception while Connecting to CFMS Gateway [" + rce.getMessage() + "]");

		} catch (Exception e) {
			throw new BadRequestException("Exception :[" + e.getMessage() + "]");
		}

	}

	private void addData(String others, ArrayList<Map<String, String>> listOfOthers, JSONObject jobj2) {
		try {
			@SuppressWarnings("unchecked")
			Map<String, String> map = (Map<String, String>) jobj2.get(others);
			listOfOthers.add(map);
		} catch (Exception e) {
			@SuppressWarnings("unchecked")
			List<Map<String, String>> map = (List<Map<String, String>>) jobj2.get(others);
			listOfOthers.addAll(map);
		}
	}

	private PaymentGateWayResponse processResponseHandlingForVerify(PaymentGateWayResponse paymentGateWayResponse,
			CFMSVerifyPayVO payVO, String nullEnabled) {
		CFMSResponce cFMSResponce = new CFMSResponce();
		cFMSResponce.setDtid(payVO.getRECORDSET().getROW().getDEPTTID());
		if (payVO.getRECORDSET().getROW().getCFMSID() != null) {
			cFMSResponce.setCfms_transid(payVO.getRECORDSET().getROW().getCFMSID().toString());
		}
		if (payVO.getRECORDSET().getROW().getTAMT() != null) {
			cFMSResponce.setTotalAmount(payVO.getRECORDSET().getROW().getTAMT());
		}
		if (payVO.getRECORDSET().getROW().getBNKRF() != null) {
			cFMSResponce.setBankTransactionId(payVO.getRECORDSET().getROW().getBNKRF());
		}
		if (StringUtils.isBlank(payVO.getRECORDSET().getROW().getDEPTTID())
				&& StringUtils.isBlank(payVO.getRECORDSET().getROW().getSTATUS())
				&& payVO.getRECORDSET().getROW().getCHALLAN() == null && nullEnabled.equalsIgnoreCase("YES")) {
			cFMSResponce.setStatus("NULL CHALLAN");
		} else {
			cFMSResponce.setStatus(payVO.getRECORDSET().getROW().getSTATUS());
		}
		paymentGateWayResponse.setCfmsResponce(cFMSResponce);
		return paymentGateWayResponse;
	}

	@Override
	public PaymentGateWayResponse processResponse(PaymentGateWayResponse paymentGateWayResponse,
			Map<String, String> gatewayDetails) {
		CFMSResponce cfmsResponce = new CFMSResponce();
		funPoint(paymentGateWayResponse.getCfmsGatewayResponceMap(), "Status", cfmsResponce::setStatus);
		funPoint(paymentGateWayResponse.getCfmsGatewayResponceMap(), "CFMS_TRID", cfmsResponce::setCfms_transid);
		funPoint(paymentGateWayResponse.getCfmsGatewayResponceMap(), "Amount", cfmsResponce::setAmount);
		funPoint(paymentGateWayResponse.getCfmsGatewayResponceMap(), "BankTransID", cfmsResponce::setBankTransactionId);
		funPoint(paymentGateWayResponse.getCfmsGatewayResponceMap(), "DTID", cfmsResponce::setDtid);
		funPoint(paymentGateWayResponse.getCfmsGatewayResponceMap(), "OtherAmount", cfmsResponce::setOtherAmount);
		funPoint(paymentGateWayResponse.getCfmsGatewayResponceMap(), "BankTimeStamp", cfmsResponce::setBankTimeStamp);
		funPoint(paymentGateWayResponse.getCfmsGatewayResponceMap(), "TA", cfmsResponce::setTotalAmount);
		funPoint(paymentGateWayResponse.getCfmsGatewayResponceMap(), "BankTimeStamp", cfmsResponce::setBankTimeStamp);
		paymentGateWayResponse.setPaymentStatus(PayStatusEnum.getPayStatusEnumByCFMS(cfmsResponce.getStatus()));
		paymentGateWayResponse.setCfmsResponce(cfmsResponce);
		return paymentGateWayResponse;

	}

	@Override
	public String dncryptSBIData(String data) {
		return null;
	}

	private String getDDOCode(String officeCode) {

		Optional<OfficeDTO> model = officeDAO.findByOfficeCode(officeCode);
		if (model.isPresent()) {
			return model.get().getDdoCode();
		}
		logger.warn("Office details not found based on input officeCOde: {} ", officeCode);
		return null;

	}

	private long getTotalamount(Map<String, FeePartsDetailsVo> feePartsMap) {
		Double totalFees = 0.0;
		for (Map.Entry<String, FeePartsDetailsVo> entry : feePartsMap.entrySet()) {
			if (entry.getValue() == null) {
				continue;
			}
			totalFees += entry.getValue().getAmount();
		}
		return Math.round(totalFees);
	}

	private void funPoint(Map<String, String> mapValues, String key, Consumer<String> consumer) {
		if (mapValues.containsKey(key)) {
			String values = mapValues.get(key);
			consumer.accept(values);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public PaymentGateWayResponse cancellationOfTransaction(PaymentTransactionDTO payTransctionDTO) {
		String applicationNo = payTransctionDTO.getApplicationFormRefNum();
		GateWayDTO gatewayValue = gatewayDao.findByGateWayType(GatewayTypeEnum.CFMS);
		Map<String, String> gatewayDetails = gatewayValue.getGatewayDetails();
		ResponseEntity<String> response = null;
		String transactionNo = payTransctionDTO.getTransactioNo();
		String username = gatewayDetails.get(CFMSParams.CUSERNAME.getParamKey());
		String password = gatewayDetails.get(CFMSParams.CPASSWORD.getParamKey());
		String recordSet = gatewayDetails.get(CFMSParams.RECORDSET.getParamKey());
		String row = gatewayDetails.get(CFMSParams.ROW.getParamKey());
		String others = gatewayDetails.get(CFMSParams.OTHERS.getParamKey());
		String challan = gatewayDetails.get(CFMSParams.CHALLAN.getParamKey());
		String nullEnabled = gatewayDetails.get(CFMSParams.ENABLENULLCHALLAN.getParamKey());
		String cancelUrl = gatewayDetails.get(CFMSParams.CURL.getParamKey());

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		StringBuilder sb = new StringBuilder();
		sb.append("{");
		sb.append("\"" + recordSet + "\" : {");
		sb.append("\"DEPTCODE\": \"" + gatewayDetails.get(CFMSParams.DC.getParamKey()) + "\",");
		sb.append("\"" + row + "\" : {");
		sb.append("\"DEPTTID\": \"" + transactionNo + "\"");
		sb.append("} } } ");

		HttpEntity<String> httpEntity = new HttpEntity<>(sb.toString(), headers);
		// RestTemplate restTmplate = new RestTemplate();
		cfmsRestTemplate.getInterceptors().add(new BasicAuthorizationInterceptor(username, password));
		try {
			long startTimeInMilli = System.currentTimeMillis();
			logger.info("TransactionNo:{} ,CFMS Verify Service consuming...,", transactionNo);
			response = cfmsRestTemplate.exchange(cancelUrl, HttpMethod.POST, httpEntity, String.class);
			logger.info("TransactionNo:{} ,Total execution time to verify: {}ms  ", transactionNo,
					(System.currentTimeMillis() - startTimeInMilli));

			if (response.hasBody()) {
				PaymentGateWayResponse paymentGateWayResponse = new PaymentGateWayResponse();
				JSONParser parser = new JSONParser();
				JSONObject jsonObj = (JSONObject) parser.parse(response.getBody());
				ArrayList<Map<String, String>> listOfOthers = new ArrayList<>();
				ArrayList<Map<String, String>> listOfChallans = new ArrayList<>();
				JSONObject jsonObjRowSet = (JSONObject) jsonObj.get(row);

				addData(others, listOfOthers, jsonObjRowSet);
				addData(challan, listOfChallans, jsonObjRowSet);

				jsonObjRowSet.remove(others);
				jsonObjRowSet.remove(challan);
				jsonObjRowSet.put(others, listOfOthers);
				jsonObjRowSet.put(challan, listOfChallans);

				Gson gson = new Gson();
				CfmsVeryifyPayRequestVO payVO = gson.fromJson(jsonObj.toString(), CfmsVeryifyPayRequestVO.class);

				logger.info("CFMS cancellation Response for application No [{}] & Body [{}]", applicationNo, jsonObj);
				paymentGateWayResponse = processResponseHandlingForCancellation(paymentGateWayResponse, payVO,
						nullEnabled);
				paymentGateWayResponse.setAppTransNo(payTransctionDTO.getApplicationFormRefNum());// applicant
				paymentGateWayResponse.setModuleCode(payTransctionDTO.getModuleCode());// Service
				if (StringUtils.isNoneBlank(payVO.getROW().getBNKRF())) {
					paymentGateWayResponse.setBankTranRefNumber(payVO.getROW().getBNKRF());
				}

				paymentGateWayResponse.setGatewayTypeEnum(GatewayTypeEnum.CFMS);

				if (StringUtils.isNotBlank(payVO.getROW().getDEPTTID())) {
					paymentGateWayResponse.setTransactionNo(payVO.getROW().getDEPTTID());
				} else {
					paymentGateWayResponse.setTransactionNo(transactionNo);
				}

				paymentGateWayResponse
						.setPaymentStatus(PayStatusEnum.getCancelStatusEnumByCFMS(payVO.getROW().getSTATUS()));
				
				if(PayStatusEnum.FAILURE.equals(paymentGateWayResponse.getPaymentStatus())){
					paymentGateWayResponse.setIsCancelledTransaction(Boolean.TRUE);
				}
				return paymentGateWayResponse;
			}

			throw new BadRequestException("No respopnce from CFMS Gateway for Application No [" + applicationNo + "]");

		} catch (RestClientException rce) {
			throw new BadRequestException("Exception while Connecting to CFMS Gateway [" + rce.getMessage() + "]");

		} catch (Exception e) {
			throw new BadRequestException("Exception :[" + e.getMessage() + "]");
		}

	}

	private PaymentGateWayResponse processResponseHandlingForCancellation(PaymentGateWayResponse paymentGateWayResponse,
			CfmsVeryifyPayRequestVO payVO, String nullEnabled) {
		CFMSResponce cFMSResponce = new CFMSResponce();
		cFMSResponce.setDtid(payVO.getROW().getDEPTTID());
		if (payVO.getROW().getCFMSID() != null) {
			cFMSResponce.setCfms_transid(payVO.getROW().getCFMSID().toString());
		}
		if (payVO.getROW().getTAMT() != null) {
			cFMSResponce.setTotalAmount(payVO.getROW().getTAMT());
		}
		if (payVO.getROW().getBNKRF() != null) {
			cFMSResponce.setBankTransactionId(payVO.getROW().getBNKRF());
		}
		if (StringUtils.isBlank(payVO.getROW().getDEPTTID()) && StringUtils.isBlank(payVO.getROW().getSTATUS())
				&& payVO.getROW().getCHALLAN() == null && nullEnabled.equalsIgnoreCase("YES")) {
			cFMSResponce.setStatus("NULL CHALLAN");
		} else {
			cFMSResponce.setStatus(payVO.getROW().getSTATUS());
		}
		paymentGateWayResponse.setCfmsResponce(cFMSResponce);
		return paymentGateWayResponse;
	}
}
