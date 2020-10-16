
package org.epragati.payments.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.epragati.constants.MessageKeys;
import org.epragati.constants.OfficeType;
import org.epragati.exception.BadRequestException;
import org.epragati.jwt.JwtUser;
import org.epragati.master.dao.OfficeDAO;
import org.epragati.master.dao.RegServiceDAO;
import org.epragati.master.dao.UserDAO;
import org.epragati.master.dto.OfficeDTO;
import org.epragati.master.dto.UserDTO;
import org.epragati.master.service.CovService;
import org.epragati.master.service.LogMovingService;
import org.epragati.master.service.PermitsService;
import org.epragati.payment.dto.ChalanaDetailsDTO;
import org.epragati.payment.dto.FeeDetailsDTO;
import org.epragati.payment.dto.FeesDTO;
import org.epragati.payment.dto.PaymentTransactionDTO;
import org.epragati.payment.dto.PaymentTransactionRequestDTO;
import org.epragati.payment.dto.ReconcilationDTO;
import org.epragati.payment.mapper.ChalanDetailsMapper;
import org.epragati.payment.mapper.FeeDetailsMapper;
import org.epragati.payment.mapper.ReconcilationMapper;
import org.epragati.payments.dao.PaymentTransactionDAO;
import org.epragati.payments.dao.ReconcilationDAO;
import org.epragati.payments.service.CashPaymentService;
import org.epragati.payments.service.PaymentGateWayService;
import org.epragati.payments.vo.CashBookVO;
import org.epragati.payments.vo.ChalanaDetailsVO;
import org.epragati.payments.vo.FeePartsDetailsVo;
import org.epragati.payments.vo.GeneralCashBookVO;
import org.epragati.payments.vo.ReconcilationVO;
import org.epragati.payments.vo.TransactionDetailVO;
import org.epragati.permits.service.PermitValidationsService;
import org.epragati.regservice.RegistrationService;
import org.epragati.regservice.dto.RegServiceDTO;
import org.epragati.regservice.mapper.RegServiceMapper;
import org.epragati.regservice.vo.RegServiceVO;
import org.epragati.reports.service.PaymentReportService;
import org.epragati.restGateway.RestGateWayService;
import org.epragati.rta.reports.vo.ReportVO;
import org.epragati.tax.vo.TaxTypeEnum;
import org.epragati.util.GateWayResponse;
import org.epragati.util.RoleEnum;
import org.epragati.util.StatusRegistration;
import org.epragati.util.payment.GatewayTypeEnum;
import org.epragati.util.payment.ModuleEnum;
import org.epragati.util.payment.PayStatusEnum;
import org.epragati.util.payment.ServiceCodeEnum;
import org.epragati.util.payment.ServiceEnum;
import org.epragati.vcr.service.VcrService;
import org.epragati.vcr.vo.ConsolidateChallanVO;
import org.epragati.vcr.vo.RegistrationVcrVo;
import org.epragati.vcr.vo.VcrFinalServiceVO;
import org.epragati.vcrImage.dao.TreasuryDAO;
import org.epragati.vcrImage.dao.VcrChallanLogsDAO;
import org.epragati.vcrImage.dao.VcrFinalServiceDAO;
import org.epragati.vcrImage.dao.VoluntaryTaxDAO;
import org.epragati.vcrImage.dto.TreasuryDTO;
import org.epragati.vcrImage.dto.VcrChallanLogsDTO;
import org.epragati.vcrImage.dto.VcrFinalServiceDTO;
import org.epragati.vcrImage.dto.VoluntaryTaxDTO;
import org.epragati.vcrImage.mapper.VcrFinalServiceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 
 * @author saikiran.kola
 *
 */

@Service
public class CashPaymentServiceImpl implements CashPaymentService {
	private static final Logger logger = LoggerFactory.getLogger(CashPaymentServiceImpl.class);

	@Autowired
	private PaymentGateWayService paymentGateWayService;

	@Autowired
	private FeeDetailsMapper feeDetailsMapper;

	@Autowired
	private CovService covService;

	@Autowired
	private LogMovingService logMovingService;

	@Autowired
	private PaymentTransactionDAO paymentTransactionDAO;

	@Autowired
	private RegistrationService registrationService;

	@Autowired
	private VcrService vcrService;

	@Autowired
	private VcrFinalServiceDAO finalServiceDAO;

	@Autowired
	private VcrFinalServiceMapper vcrFinalServiceMapper;

	@Autowired
	private ReconcilationMapper reconcilationMapper;

	@Autowired
	private ReconcilationDAO reconcilationDAO;

	@Autowired
	private RestGateWayService restGateWayService;

	@Autowired
	private RegServiceDAO regServiceDAO;

	@Autowired
	private RegServiceMapper regServiceMapper;

	@Autowired
	private PermitsService permitsService;
	@Autowired
	private VoluntaryTaxDAO voluntaryTaxDAO;

	@Autowired
	private OfficeDAO officeDAO;

	@Autowired
	private UserDAO userDAO;

	@Autowired
	private PaymentReportService paymentReportService;

	@Autowired
	private ChalanDetailsMapper ChalanDetailsMapper;

	@Autowired
	private TreasuryDAO treasuryDAO;
	
	@Autowired
	private PermitValidationsService permitValidationsService;
	
	@Autowired
	private VcrChallanLogsDAO vcrChallanLogsDAO;

	public TransactionDetailVO setVcrFeeParts(RegServiceVO regServiceDTO) {
		TransactionDetailVO transactionDetailVO = new TransactionDetailVO();
		GatewayTypeEnum payGatewayTypeEnum = GatewayTypeEnum.getGatewayTypeEnumByDesc(regServiceDTO.getGatewayType());
		transactionDetailVO.setGatewayTypeEnum(payGatewayTypeEnum);
		List<ServiceEnum> al = new ArrayList<>();
		al.addAll(regServiceDTO.getServiceType());
		transactionDetailVO.setServiceEnumList(al);
		if (regServiceDTO.getServiceType() != null
				&& regServiceDTO.getServiceType().stream().anyMatch(type -> type.equals(ServiceEnum.VCR))) {
			transactionDetailVO
					.setGatewayType(GatewayTypeEnum.getGatewayTypeEnumByDesc(regServiceDTO.getGatewayType()));
			transactionDetailVO.setFormNumber(regServiceDTO.getApplicationNo());
			transactionDetailVO.setCovs(Arrays
					.asList(covService.findByCovCode(regServiceDTO.getRegistrationDetails().getClassOfVehicle())));
			transactionDetailVO.setModule(ModuleEnum.CITIZEN.getCode());
			transactionDetailVO
					.setFirstName(regServiceDTO.getRegistrationDetails().getApplicantDetails().getFirstName());
			transactionDetailVO.setTxnid(UUID.randomUUID().toString());
			transactionDetailVO.setPaymentTransactionNo(regServiceDTO.getPaymentTransactionNo());
			transactionDetailVO.setServiceId(regServiceDTO.getServiceIds());
			transactionDetailVO.setListOfVcrs(regServiceDTO.getVcrNosList());
			transactionDetailVO.setOfficeCode(regServiceDTO.getOfficeCode());
			transactionDetailVO.setRequestToPay(Boolean.TRUE);

		} else if (regServiceDTO.getServiceType() != null
				&& regServiceDTO.getServiceType().stream().anyMatch(type -> type.equals(ServiceEnum.VOLUNTARYTAX))) {
			transactionDetailVO = registrationService.getPaymentDetails(regServiceDTO, Boolean.TRUE, null);
			/*
			 * transactionDetailVO
			 * .setGatewayType(GatewayTypeEnum.getGatewayTypeEnumByDesc(regServiceDTO.
			 * getGatewayType()));
			 * transactionDetailVO.setFormNumber(regServiceDTO.getApplicationNo());
			 * transactionDetailVO.setCovs(Arrays
			 * .asList(covService.findByCovCode(regServiceDTO.getRegistrationDetails().
			 * getClassOfVehicle()))); transactionDetailVO.setPhone(StringUtils.EMPTY);
			 * transactionDetailVO.setEmail(StringUtils.EMPTY);
			 * transactionDetailVO.setModule(ModuleEnum.CITIZEN.getCode());
			 * transactionDetailVO
			 * .setFirstName(regServiceDTO.getRegistrationDetails().getApplicantDetails().
			 * getFirstName()); transactionDetailVO.setTxnid(UUID.randomUUID().toString());
			 * transactionDetailVO.setPaymentTransactionNo(regServiceDTO.
			 * getPaymentTransactionNo());
			 * transactionDetailVO.setServiceId(regServiceDTO.getServiceIds()); //
			 * transactionDetailVO.setListOfVcrs(regServiceDTO.getVcrNosList());
			 * RegistrationVcrVo voluntyInputs = new RegistrationVcrVo();
			 * voluntyInputs.setRegNo(regServiceDTO.getPrNo());
			 * voluntyInputs.setTrNo(regServiceDTO.getRegistrationDetails().getTrNo());
			 * voluntyInputs.setCov(regServiceDTO.getRegistrationDetails().getClassOfVehicle
			 * ());
			 * voluntyInputs.setGvwc(regServiceDTO.getRegistrationDetails().getVahanDetails(
			 * ).getGvw());
			 * voluntyInputs.setUlw(regServiceDTO.getRegistrationDetails().getVahanDetails()
			 * .getUnladenWeight());
			 * voluntyInputs.setSeats(regServiceDTO.getRegistrationDetails().getVahanDetails
			 * ().getSeatingCapacity());
			 * voluntyInputs.setMakersModel(regServiceDTO.getRegistrationDetails().
			 * getVahanDetails().getMakersModel());
			 * voluntyInputs.setInvoiceValue(regServiceDTO.getRegistrationDetails().
			 * getInvoiceDetails().getInvoiceValue());
			 * voluntyInputs.setFuelDesc(regServiceDTO.getRegistrationDetails().
			 * getVahanDetails().getFuelDesc());
			 * voluntyInputs.setNocIssued(regServiceDTO.getVoluntaryTaxDetails().isNocIssued
			 * ());
			 * voluntyInputs.setWithTP(regServiceDTO.getVoluntaryTaxDetails().isWithTP());
			 * voluntyInputs.setFirstVehicle(regServiceDTO.getRegistrationDetails().
			 * getIsFirstVehicle());
			 * voluntyInputs.setFcValidity(regServiceDTO.getVoluntaryTaxDetails().
			 * getFcValidity()); if (regServiceDTO.getnOCDetails() != null &&
			 * regServiceDTO.getnOCDetails().getIssueDate() != null) {
			 * voluntyInputs.setNocDate(regServiceDTO.getnOCDetails().getIssueDate()); }
			 * 
			 * if (regServiceDTO.getRegistrationDetails().getOwnerType() != null) {
			 * voluntyInputs.setOwnerType(regServiceDTO.getRegistrationDetails().
			 * getOwnerType()); }
			 * 
			 * if (regServiceDTO != null && regServiceDTO.getRegistrationDetails() != null
			 * && regServiceDTO.getRegistrationDetails().getPrGeneratedDate() != null) {
			 * voluntyInputs
			 * .setPrGeneratedDate(regServiceDTO.getRegistrationDetails().getPrGeneratedDate
			 * ().toLocalDate()); }
			 * 
			 * if (StringUtils.isNoneBlank(regServiceDTO.getRegistrationDetails().
			 * getApplicantType())) { if
			 * (regServiceDTO.getRegistrationDetails().isRegVehicleWithPR()) {
			 * voluntyInputs.setOtherStateRegister(true); } else {
			 * voluntyInputs.setOtherStateUnregister(true); } } else {
			 * voluntyInputs.setUnregisteredVehicle(true); }
			 * 
			 * voluntyInputs.setFirstVehicle(regServiceDTO.getRegistrationDetails().
			 * getIsFirstVehicle()); if (regServiceDTO.getAlterationVO() != null &&
			 * regServiceDTO.getAlterationVO().getDateOfCompletion() != null) {
			 * voluntyInputs.setDateOfCompletion(regServiceDTO.getAlterationVO().
			 * getDateOfCompletion()); } //
			 * voluntyInputs.setDateOfCompletion(regServiceDTO.getDateOfCompletion());
			 * transactionDetailVO.setOfficeCode(regServiceDTO.getOfficeCode());
			 * voluntyInputs.setTaxType(regServiceDTO.getVoluntaryTaxDetails().getTaxType())
			 * ; transactionDetailVO.setInput(voluntyInputs);
			 * transactionDetailVO.setRequestToPay(Boolean.TRUE);
			 */
		}

		if (regServiceDTO.getServiceType() != null && regServiceDTO.getServiceType().stream()
				.anyMatch(type -> type.equals(ServiceEnum.OTHERSTATETEMPORARYPERMIT)
						|| type.equals(ServiceEnum.OTHERSTATESPECIALPERMIT))) {
			transactionDetailVO
					.setGatewayType(GatewayTypeEnum.getGatewayTypeEnumByDesc(regServiceDTO.getGatewayType()));
			transactionDetailVO.setFormNumber(regServiceDTO.getApplicationNo());
			transactionDetailVO.setCovs(Arrays.asList(covService.findByCovCode(
					regServiceDTO.getOtherStateTemporaryPermit().getVehicleDetails().getClassOfVehicle())));
			transactionDetailVO.setPhone(StringUtils.EMPTY);
			transactionDetailVO.setEmail(StringUtils.EMPTY);
			transactionDetailVO.setModule(ModuleEnum.CITIZEN.getCode());
			transactionDetailVO
					.setFirstName(regServiceDTO.getOtherStateTemporaryPermit().getApplicantDetails().getDisplayName());
			transactionDetailVO.setTxnid(UUID.randomUUID().toString());
			transactionDetailVO.setPaymentTransactionNo(regServiceDTO.getPaymentTransactionNo());
			transactionDetailVO.setServiceId(regServiceDTO.getServiceIds());
			transactionDetailVO.setSeatingCapacity(
					regServiceDTO.getOtherStateTemporaryPermit().getVehicleDetails().getSeatingCapacity());
			transactionDetailVO.setPermitType(regServiceDTO.getOtherStateTemporaryPermit().getTemporaryPermitDetails()
					.getPermitType().getPermitType());
			transactionDetailVO.setWeightType(covService
					.getWeightTypeDetails(regServiceDTO.getOtherStateTemporaryPermit().getVehicleDetails().getRlw()));
			transactionDetailVO.setOfficeCode(regServiceDTO.getOfficeCode());

			RegistrationVcrVo voluntyInputs = new RegistrationVcrVo();
			voluntyInputs.setRegNo(regServiceDTO.getPrNo());
			voluntyInputs.setCov(regServiceDTO.getOtherStateTemporaryPermit().getVehicleDetails().getClassOfVehicle());
			voluntyInputs.setGvwc(regServiceDTO.getOtherStateTemporaryPermit().getVehicleDetails().getRlw());
			voluntyInputs.setUlw(regServiceDTO.getOtherStateTemporaryPermit().getVehicleDetails().getUlw());
			voluntyInputs
					.setSeats(regServiceDTO.getOtherStateTemporaryPermit().getVehicleDetails().getSeatingCapacity());

//	voluntyInputs.setWithTP(regServiceDTO.getVoluntaryTaxDetails().isWithTP());

			/*
			 * if (regServiceDTO != null && regServiceDTO.getRegistrationDetails() != null
			 * && regServiceDTO.getRegistrationDetails().getPrGeneratedDate() != null) {
			 * voluntyInputs.setPrGeneratedDate(
			 * regServiceDTO.getRegistrationDetails().getPrGeneratedDate().toLocalDate()) ;
			 * }
			 */

			voluntyInputs.setOtherStateRegister(true);
			voluntyInputs.setOtherStateUnregister(false);
//		voluntyInputs.setFirstVehicle(regServiceDTO.getRegistrationDetails().getIsFirstVehicle());
			transactionDetailVO.setOfficeCode(regServiceDTO.getOfficeCode());
			voluntyInputs.setTaxType(regServiceDTO.getOtherStateTemporaryPermit().getTemporaryPermitDetails()
					.getRouteDetailsVO().getNoOfDays());

			voluntyInputs.setFcValidity(regServiceDTO.getOtherStateTemporaryPermit().getFcDetails().getFcValidUpto());

			// voluntyInputs.setFirstVehicle(regServiceDTO.getRegistrationDetails().getIsFirstVehicle());
			transactionDetailVO.setInput(voluntyInputs);
			permitValidationsService.permitvalidateAndSetHomeStateOrOtherState(null, regServiceDTO.getPrNo(), transactionDetailVO);
		}
		
		transactionDetailVO.setFeePartsMap(getFeeParts(transactionDetailVO));
		transactionDetailVO
				.setFeeDetailsVO(paymentGateWayService.preFeeDetailsVO(transactionDetailVO.getFeePartsMap()));
		Map<String, FeePartsDetailsVo> feeParts = paymentGateWayService
				.applicationFeeInFeeParts(transactionDetailVO.getFeePartsMap());
		transactionDetailVO.setFeePartsMap(feeParts);
		transactionDetailVO.setAmount(paymentGateWayService.getTotalamount(transactionDetailVO.getFeePartsMap()));
		return transactionDetailVO;
	}

	/**
	 * saving Cash payment Transactions for VCR
	 */
	@Override
	public TransactionDetailVO saveVcrCashPayment(String regServiceDTO, MultipartFile[] uploadfiles, JwtUser jwtUser) {
		try {
			RegServiceVO regServiceVO = registrationService.saveVcrDetails(regServiceDTO, uploadfiles,jwtUser);

			TransactionDetailVO transactionDetailVO = cashPaymentCommonCode(regServiceVO);
			return transactionDetailVO;
		} catch (Exception e) {
			logger.error("Exception while save payment request {}", e);
			throw new BadRequestException(e.getMessage());
		}
	}

	@Override
	public TransactionDetailVO cashPaymentCommonCode(RegServiceVO regServiceVO) {
		TransactionDetailVO transactionDetailVO = setVcrFeeParts(regServiceVO);
		PaymentTransactionDTO paymentTransactionDTO = new PaymentTransactionDTO();
		setVcrTransactionDetails(paymentTransactionDTO, regServiceVO);
		if (!Objects.isNull(transactionDetailVO.getFeeDetailsVO())) {
			paymentTransactionDTO.setFeeDetailsDTO(feeDetailsMapper.convertVO(transactionDetailVO.getFeeDetailsVO()));
		} else {
			paymentTransactionDTO.setFeeDetailsDTO(feeDetailsMapper
					.convertVO(paymentGateWayService.preFeeDetailsVO(transactionDetailVO.getFeePartsMap())));
		}
		regServiceVO.setApplicationStatus(StatusRegistration.APPROVED);
		if (regServiceVO.getServiceType() != null
				&& regServiceVO.getServiceType().stream().anyMatch(type -> type.equals(ServiceEnum.VCR))) {
			Optional<RegServiceDTO> regServiceDto = regServiceDAO.findByApplicationNo(regServiceVO.getApplicationNo());
			
			paymentGateWayService.updateVcrDetails(regServiceDto.get());
			RegServiceVO vo = regServiceMapper.convertEntity(regServiceDto.get());
			if(regServiceDto.get().getDeductionMode()!=null && regServiceDto.get().getDeductionMode()) {
				vo.setDeductionMode( Boolean.TRUE);
			}
			paymentGateWayService.saveCitizenTaxDetails(vo,
					Boolean.FALSE, Boolean.FALSE, "AP");
		}
		logMovingService.movePaymnetsToLog(paymentTransactionDTO.getApplicationFormRefNum());
		paymentTransactionDAO.save(paymentTransactionDTO);
		updateRegistrationServicesDetails(regServiceVO);
		if (regServiceVO.getServiceIds().stream()
				.anyMatch(type -> type.equals(ServiceEnum.OTHERSTATETEMPORARYPERMIT.getId())
						|| type.equals(ServiceEnum.OTHERSTATESPECIALPERMIT.getId()))) {
			permitsService.saveOtherStateDetailsForTemporaryPermit(regServiceVO.getApplicationNo(),
					transactionDetailVO);
		}
		return transactionDetailVO;
	}

	private void updateRegistrationServicesDetails(RegServiceVO regServiceVO) {
		Optional<RegServiceDTO> regServiceDto = regServiceDAO.findByApplicationNo(regServiceVO.getApplicationNo());

		regServiceDto.get().setApplicationStatus(StatusRegistration.APPROVED);
		if (regServiceDto.get().getServiceType() != null && regServiceDto.get().getServiceType().stream()
				.anyMatch(type -> type.equals(ServiceEnum.VOLUNTARYTAX))) {
			VoluntaryTaxDTO voluntaryTaxDto = regServiceDto.get().getVoluntaryTaxDetails();
			voluntaryTaxDto.setCreatedDate(regServiceDto.get().getCreatedDate());
			voluntaryTaxDto.setlUpdate(LocalDateTime.now());
			paymentGateWayService.getNextQuarterValidity(voluntaryTaxDto);
			voluntaryTaxDAO.save(voluntaryTaxDto);
		}
		regServiceDAO.save(regServiceDto.get());

	}

	

	/**
	 * 
	 * @param paymentTransactionDTO
	 * @param regServiceVO
	 */

	public void setVcrTransactionDetails(PaymentTransactionDTO paymentTransactionDTO, RegServiceVO regServiceVO) {
		paymentTransactionDTO.setApplicationFormRefNum(regServiceVO.getApplicationNo());
		paymentTransactionDTO.setOfficeCode(regServiceVO.getOfficeCode());
		paymentTransactionDTO.setModuleCode(ModuleEnum.CITIZEN.getCode());
		paymentTransactionDTO.setCreatedDateStr(LocalDateTime.now().toString());
		paymentTransactionDTO.setCreatedDate(LocalDateTime.now());
		paymentTransactionDTO
				.setPaymentGatewayType(GatewayTypeEnum.getGatewayTypeEnumByDesc(regServiceVO.getGatewayType()).getId());
		paymentTransactionDTO.setTransactioNo(UUID.randomUUID().toString());
		PaymentTransactionRequestDTO request = new PaymentTransactionRequestDTO();
		request.setRequestTime(LocalDateTime.now());
		paymentTransactionDTO.setRequest(request);
		List<ServiceEnum> al = new ArrayList<>();
		al.addAll(regServiceVO.getServiceType());
		paymentTransactionDTO.setServiceIds(al.stream().map(service -> service.getId()).collect(Collectors.toSet()));
		paymentTransactionDTO.setPayStatus(PayStatusEnum.SUCCESS.getDescription());
	}

	/**
	 * get Fee Parts for VCR
	 * 
	 * @param transactionDetailVO
	 * @return
	 */

	/*
	 * public Map<String, FeePartsDetailsVo> getFeeParts(TransactionDetailVO
	 * transactionDetailVO) { return
	 * paymentGateWayService.calculateTaxAndFee(transactionDetailVO.getCovs(),
	 * transactionDetailVO.getServiceEnumList(),
	 * transactionDetailVO.getWeightType(), transactionDetailVO.isRequestToPay(),
	 * transactionDetailVO.getTaxType(), transactionDetailVO.isCalculateFc(),
	 * transactionDetailVO.isRtoRejectedIvcn(),
	 * transactionDetailVO.isChassesVehicle(), transactionDetailVO.getOfficeCode(),
	 * transactionDetailVO.getFormNumber(), transactionDetailVO.isOtherState(),
	 * transactionDetailVO.getRegApplicationNo(),
	 * transactionDetailVO.getPermitType(), transactionDetailVO.getSlotDate(),
	 * transactionDetailVO.getSeatingCapacity(), transactionDetailVO.getRouteCode(),
	 * transactionDetailVO.getIsWeightAlt(), transactionDetailVO.getPurpose(),
	 * transactionDetailVO.getListOfVcrs(), transactionDetailVO.getInput()); }
	 */

	public Map<String, FeePartsDetailsVo> getFeeParts(TransactionDetailVO transactionDetailVO) {
		return paymentGateWayService.calculateTaxAndFee(registrationService.getPaymentInputs(
				transactionDetailVO)/*
									 * transactionDetailVO.getCovs(), transactionDetailVO.getServiceEnumList(),
									 * transactionDetailVO.getWeightType(), transactionDetailVO.isRequestToPay(),
									 * transactionDetailVO.getTaxType(), transactionDetailVO.isCalculateFc(),
									 * transactionDetailVO.isRtoRejectedIvcn(),
									 * transactionDetailVO.isChassesVehicle(), transactionDetailVO.getOfficeCode(),
									 * transactionDetailVO.getFormNumber(), transactionDetailVO.isOtherState(),
									 * transactionDetailVO.getRegApplicationNo(),
									 * transactionDetailVO.getPermitType(), transactionDetailVO.getSlotDate(),
									 * transactionDetailVO.getSeatingCapacity(), transactionDetailVO.getRouteCode(),
									 * transactionDetailVO.getIsWeightAlt(), transactionDetailVO.getPurpose(),
									 * transactionDetailVO.getListOfVcrs(), transactionDetailVO.getInput()
									 */);
	}

	/**
	 * get Cash Reconciliation Data Based on Date and officeCode
	 * 
	 * @return
	 * 
	 */

	@Override
	public ReconcilationVO getVcrBasedOnBookedDate(ReconcilationVO reconcilationVO) {
		if (reconcilationVO.getVcrBookedDate() == null) {
			throw new BadRequestException("Booked Date is missing");
		}
		if (StringUtils.isEmpty(reconcilationVO.getMviName())) {
			throw new BadRequestException("MVI selection is missing");
		}
		LocalDateTime fromDate = vcrService.getTimewithDate(reconcilationVO.getVcrBookedDate(), false);
		LocalDateTime toDate = vcrService.getTimewithDate(reconcilationVO.getVcrBookedDate(), true);

		List<VcrFinalServiceDTO> vcrList = finalServiceDAO.nativeVcrDateAndUserAndPaymentType(
				Arrays.asList(reconcilationVO.getMviName()), fromDate, toDate, GatewayTypeEnum.CASH.getDescription());
		if (vcrList.isEmpty()) {
			throw new BadRequestException(MessageKeys.MESSAGE_NO_VCR_DATA);
		}
		List<VcrFinalServiceVO> vcrVoList = vcrFinalServiceMapper.reconcilationMapper(vcrList);
		List<VcrFinalServiceVO> vcrClosedList = new ArrayList<>();

		vcrClosedList = vcrVoList.stream().filter(vcr -> vcr.isConsolidateUpdated()).collect(Collectors.toList());

		List<VcrFinalServiceVO> vcrOpenList = vcrVoList.stream().filter(vcr -> !vcr.isConsolidateUpdated())
				.collect(Collectors.toList());
		reconcilationVO.setVcrDetails(vcrOpenList);
		reconcilationVO.setPaidVcrDetails(vcrClosedList);
		return reconcilationVO;
	}

	@Override
	public String cashReconciliation(ReconcilationVO reconcilationVO) {

		List<ChalanaDetailsVO> challanDetailsVO = consildateChallanEntryValidations(reconcilationVO);
		challanAmountsValidation(challanDetailsVO, reconcilationVO);

		List<String> vcrConsolidateList = reconcilationVO.getConsolidatedDetails().stream()
				.filter(val -> val.getType().equals(TaxTypeEnum.consolidateTypes.VCR.getDesc()))
				.map(vcr -> vcr.getVcrNo()).collect(Collectors.toList());
		List<VcrFinalServiceDTO> vcrDataList = null;
		List<VoluntaryTaxDTO> voluntaryDataList = null;
		List<RegServiceDTO> permitServiceList = null;
		if (CollectionUtils.isNotEmpty(vcrConsolidateList)) {
			vcrDataList = finalServiceDAO.findByVcrVcrNumberIn(vcrConsolidateList);
		}
		if (CollectionUtils.isNotEmpty(vcrConsolidateList) && CollectionUtils.isEmpty(vcrDataList)) {
			throw new BadRequestException("No Open Vcrs found");
		}

		List<String> voluntaryConsolidateList = reconcilationVO.getConsolidatedDetails().stream()
				.filter(val -> val.getType().equals(TaxTypeEnum.consolidateTypes.VOLUNTARY.getDesc()))
				.map(vcr -> vcr.getVcrNo()).collect(Collectors.toList());
		if (CollectionUtils.isNotEmpty(voluntaryConsolidateList)) {
			voluntaryDataList = voluntaryTaxDAO.findByApplicationNoIn(voluntaryConsolidateList);
		}

		if (CollectionUtils.isNotEmpty(voluntaryConsolidateList) && CollectionUtils.isEmpty(voluntaryDataList)) {
			throw new BadRequestException("No Open Voluntary found");
		}

		List<String> permitConsolidateList = reconcilationVO.getConsolidatedDetails().stream()
				.filter(val -> val.getType().equals(TaxTypeEnum.consolidateTypes.TP.getDesc())
						|| val.getType().equals(TaxTypeEnum.consolidateTypes.SP.getDesc())
								&& StringUtils.isNotEmpty(val.getVcrNo()))
				.map(vcr -> vcr.getVcrNo()).collect(Collectors.toList());

		if (CollectionUtils.isNotEmpty(permitConsolidateList)) {
			permitServiceList = regServiceDAO
					.findByOtherStateTemporaryPermitDetailsTemporaryPermitDetailsPermitNoIn(permitConsolidateList);
		}

		if (CollectionUtils.isNotEmpty(permitConsolidateList) && CollectionUtils.isEmpty(permitServiceList)) {

			throw new BadRequestException("No Open TP/SP found");

		}

		String recieptNo = restGateWayService.generatePaymentReciept();
		setVcrChallanDetails(vcrDataList, challanDetailsVO, recieptNo);
		setVoluntaryChallanDetails(voluntaryDataList, challanDetailsVO, recieptNo);
		setPermitChallanDetails(permitServiceList, challanDetailsVO, recieptNo);

		ReconcilationDTO reconcilationDTO = reconcilationMapper.convertVO(reconcilationVO);
		reconcilationDTO.setTransactionNo(recieptNo);
		if (CollectionUtils.isNotEmpty(vcrDataList)) {
			finalServiceDAO.save(vcrDataList);
		}
		if (CollectionUtils.isNotEmpty(voluntaryDataList)) {
			voluntaryTaxDAO.save(voluntaryDataList);
		}
		if (CollectionUtils.isNotEmpty(permitServiceList)) {
			regServiceDAO.save(permitServiceList);
		}

		reconcilationDAO.save(reconcilationDTO);
		return reconcilationDTO.getTransactionNo();
	}

	public void challanAmountsValidation(List<ChalanaDetailsVO> challanDetailsList, ReconcilationVO reconcilationVO) {
		challanDetailsList.stream().forEach(challan -> {
			if (challan.getType().equals(TaxTypeEnum.consolidateChallanEntryHeads.LIFETAX.getDesc())) {

				double lifeTax = reconcilationVO.getConsolidatedDetails().stream().map(con -> con.getLifeTax())
						.mapToDouble(Double::doubleValue).sum();
				if (lifeTax != Double.parseDouble(challan.getAmount())) {
					throw new BadRequestException("LIFE TAX amount missmatch");
				}
			}
			if (challan.getType().equals(TaxTypeEnum.consolidateChallanEntryHeads.QUARTERLYTAX.getDesc())) {
				double quarterly = reconcilationVO.getConsolidatedDetails().stream().map(con -> con.getQuarterlyTax())
						.mapToDouble(Double::doubleValue).sum();
				if (quarterly != Double.parseDouble(challan.getAmount())) {
					throw new BadRequestException("QUARTERLY TAX amount missmatch");
				}
			}
			if (challan.getType().equals(TaxTypeEnum.consolidateChallanEntryHeads.SERVICEFEE.getDesc())) {
				double serviceFee = reconcilationVO.getConsolidatedDetails().stream().map(con -> con.getServiceFee())
						.mapToDouble(Double::doubleValue).sum();
				if (serviceFee != Double.parseDouble(challan.getAmount())) {
					throw new BadRequestException("SERVICE FEE amount missmatch");
				}
			}
			if (challan.getType().equals(TaxTypeEnum.consolidateChallanEntryHeads.PERMITFEE.getDesc())) {
				if (!reconcilationVO.getConsolidatedDetails().stream()
						.anyMatch(type -> type.getType().equals(TaxTypeEnum.consolidateTypes.TP.getDesc())
								|| type.getType().equals(TaxTypeEnum.consolidateTypes.SP.getDesc()))) {
					throw new BadRequestException("No TP/SP selected selected for PERMIT FEE Challan Entry ");
				}
				double permitFee = reconcilationVO.getConsolidatedDetails().stream().map(con -> con.getPermitFee())
						.mapToDouble(Double::doubleValue).sum();
				if (permitFee != Double.parseDouble(challan.getAmount())) {
					throw new BadRequestException("PERMIT FEE amount missmatch");
				}
			}
			if (challan.getType().equals(TaxTypeEnum.consolidateChallanEntryHeads.COMPOUNDFEE.getDesc())) {

				if (!reconcilationVO.getConsolidatedDetails().stream()
						.anyMatch(type -> type.getType().equals(TaxTypeEnum.consolidateTypes.VCR.getDesc()))) {
					throw new BadRequestException("No vcr selected  for COMPOUND FEE Challan Entry ");
				}

				double cf = reconcilationVO.getConsolidatedDetails().stream().map(con -> con.getCompoundFee())
						.mapToDouble(Double::doubleValue).sum();
				if (cf != Double.parseDouble(challan.getAmount())) {
					throw new BadRequestException("COMPOUND FEE amount missmatch");
				}
			}

		});
	}

	public List<ChalanaDetailsVO> consildateChallanEntryValidations(ReconcilationVO reconcilationVO) {

		if (reconcilationVO.getFromDate() == null || reconcilationVO.getToDate() == null) {
			throw new BadRequestException("From Date/To Date is missing");
		}
		if (StringUtils.isEmpty(reconcilationVO.getMviName())) {
			throw new BadRequestException("MVI selection is missing");
		}
		if (CollectionUtils.isEmpty(reconcilationVO.getConsolidatedDetails())) {
			throw new BadRequestException("please select vcr/Voluntary/permit for challan Entry");
		}
		if (reconcilationVO.getChallanDetails() == null
				|| CollectionUtils.isEmpty(reconcilationVO.getChallanDetails())) {
			throw new BadRequestException("Challan Details not found");
		}

		List<ChalanaDetailsVO> challanDetailsList = reconcilationVO.getChallanDetails().stream()
				.filter(val -> StringUtils.isNotEmpty(val.getChallanNo())).collect(Collectors.toList());

		if (CollectionUtils.isEmpty(challanDetailsList)) {
			throw new BadRequestException("Please Provide Challan Details");
		}
 		
		challanSelectionValidations(reconcilationVO);

		challanDetailsList.stream().forEach(challan -> {

			if (StringUtils.isEmpty(challan.getChallanNo())) {
				throw new BadRequestException("Challan No is missing");
			}

			if (StringUtils.isEmpty(challan.getTresuryName())) {
				throw new BadRequestException("Tresury Name is missing");
			}

			if (challan.getChallanDate() == null) {
				throw new BadRequestException("Challan Date  is missing");
			}

			if (StringUtils.isEmpty(challan.getType())) {
				throw new BadRequestException("Challan Type  is missing");
			}

			if (StringUtils.isEmpty(challan.getAmount())) {
				throw new BadRequestException("Challan Amount  is missing");

			}
			try {
				if (Double.parseDouble(challan.getAmount()) == 0) {
					throw new BadRequestException("Enter valid challan Amount");
				}
			} catch (Exception e) {
				throw new BadRequestException("Please Enter Valid Amount");
			}

		});
		checkExistThisFinancialYearChallanNo(challanDetailsList);
		return challanDetailsList;

	}

	public void checkExistThisFinancialYearChallanNo(List<ChalanaDetailsVO> challanDetailsList) {
		challanDetailsList.stream().forEach(challanDetails ->{
			List<VcrFinalServiceDTO> existChallanNo = new ArrayList<VcrFinalServiceDTO>();
					
			long year =challanDetails.getChallanDate().getYear();
			long month=challanDetails.getChallanDate().getMonthValue();
			String date1;
			String date2;
			if(month<4) {
				
				 date1=year-1+"-04-01";
				 date2 =year+"-03-31";
			}else {
				
			 date1=year+"-04-01";
			 date2 =1+year+"-03-31";
			}
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
			
			//convert String to LocalDate
			LocalDate date3 = LocalDate.parse(date1, formatter);
			LocalDate date4 = LocalDate.parse(date2, formatter);
			
			//LocalDate String to LocalDateTime
			LocalDateTime fromDate = getTimewithDate(date3, false);
			LocalDateTime toDate = getTimewithDate(date4, true);
			
			existChallanNo = finalServiceDAO.findByChallanDetailsChallanNoAndChallanDetailsChallanDateBetween(
					challanDetails.getChallanNo(), fromDate, toDate);
			if(!existChallanNo.isEmpty()) {
				
				throw new BadRequestException(challanDetails.getChallanNo() +" Challan No Already Exist this financial year");
			}

		});

	}

	public LocalDateTime getTimewithDate(LocalDate date, Boolean timeZone) {
		if (date == null) {
			throw new BadRequestException("Date input not available");
		}
		String dateVal = date + "T00:00:00.000Z";
		if (timeZone) {
			dateVal = date + "T23:59:59.999Z";
		}
		ZonedDateTime zdt = ZonedDateTime.parse(dateVal);
		return zdt.toLocalDateTime();
	}
	public void challanSelectionValidations(ReconcilationVO reconcilationVO) {

		List<String> taxTypeHeadsList = Arrays.asList(TaxTypeEnum.consolidateChallanEntryHeads.values()).stream()
				.map(val -> val.getDesc()).collect(Collectors.toList());
		taxTypeHeadsList.stream().forEach(taxType -> {
			if (taxType.equals(TaxTypeEnum.consolidateChallanEntryHeads.QUARTERLYTAX.getDesc())) {

				if (reconcilationVO.getConsolidatedDetails().stream().anyMatch(val -> val.getQuarterlyTax() != 0)) {
					Optional<ChalanaDetailsVO> QuarterlyChallan = reconcilationVO.getChallanDetails().stream()
							.filter(challan -> challan.getType().equals(taxType)).findAny();
					if (!QuarterlyChallan.isPresent()) {
						throw new BadRequestException("Plese Enter Quarterly Tax Challan Entry Details ");
					}
				}

			}

			if (taxType.equals(TaxTypeEnum.consolidateChallanEntryHeads.LIFETAX.getDesc())) {

				if (reconcilationVO.getConsolidatedDetails().stream().anyMatch(val -> val.getLifeTax() != 0)) {
					Optional<ChalanaDetailsVO> QuarterlyChallan = reconcilationVO.getChallanDetails().stream()
							.filter(challan -> challan.getType().equals(taxType)).findAny();
					if (!QuarterlyChallan.isPresent()) {
						throw new BadRequestException("Plese Enter " + taxType + "Challan Entry Details ");
					}
				}

			}

			if (taxType.equals(TaxTypeEnum.consolidateChallanEntryHeads.PERMITFEE.getDesc())) {

				if (reconcilationVO.getConsolidatedDetails().stream().anyMatch(val -> val.getPermitFee() != 0)) {
					Optional<ChalanaDetailsVO> QuarterlyChallan = reconcilationVO.getChallanDetails().stream()
							.filter(challan -> challan.getType().equals(taxType)).findAny();
					if (!QuarterlyChallan.isPresent()) {
						throw new BadRequestException("Plese Enter " + taxType + "Challan Entry Details ");
					}
				}

			}

			if (taxType.equals(TaxTypeEnum.consolidateChallanEntryHeads.COMPOUNDFEE.getDesc())) {

				if (reconcilationVO.getConsolidatedDetails().stream().anyMatch(val -> val.getCompoundFee() != 0)) {
					Optional<ChalanaDetailsVO> QuarterlyChallan = reconcilationVO.getChallanDetails().stream()
							.filter(challan -> challan.getType().equals(taxType)).findAny();
					if (!QuarterlyChallan.isPresent()) {
						throw new BadRequestException("Plese Enter " + taxType + "Challan Entry Details ");
					}
				}

			}

			if (taxType.equals(TaxTypeEnum.consolidateChallanEntryHeads.GREENTAX.getDesc())) {

				if (reconcilationVO.getConsolidatedDetails().stream().anyMatch(val -> val.getGreenTax() != 0)) {
					Optional<ChalanaDetailsVO> QuarterlyChallan = reconcilationVO.getChallanDetails().stream()
							.filter(challan -> challan.getType().equals(taxType)).findAny();
					if (!QuarterlyChallan.isPresent()) {
						throw new BadRequestException("Plese Enter " + taxType + "Challan Entry Details ");
					}
				}

			}

			if (taxType.equals(TaxTypeEnum.consolidateChallanEntryHeads.SERVICEFEE.getDesc())) {

				if (reconcilationVO.getConsolidatedDetails().stream().anyMatch(val -> val.getServiceFee() != 0)) {
					Optional<ChalanaDetailsVO> QuarterlyChallan = reconcilationVO.getChallanDetails().stream()
							.filter(challan -> challan.getType().equals(taxType)).findAny();
					if (!QuarterlyChallan.isPresent()) {
						throw new BadRequestException("Plese Enter " + taxType + "Challan Entry Details ");
					}
				}

			}

		});

	}

	public void setPermitChallanDetails(List<RegServiceDTO> permitServiceList, List<ChalanaDetailsVO> challanDetailsVO,
			String recieptNo) {
		if (CollectionUtils.isNotEmpty(permitServiceList)) {
			permitServiceList.stream().forEach(vcr -> {
				vcr.setConsolidateUpdatedTime(LocalDateTime.now());
				vcr.setConsolidateUpdated(true);

				List<ChalanaDetailsVO> vcrChallanDetailsVO = challanDetailsVO.stream().filter(
						type -> type.getType().equals(TaxTypeEnum.consolidateChallanEntryHeads.PERMITFEE.getDesc())
								|| type.getType().equals(TaxTypeEnum.consolidateChallanEntryHeads.SERVICEFEE.getDesc())
								|| type.getType()
										.equals(TaxTypeEnum.consolidateChallanEntryHeads.QUARTERLYTAX.getDesc()))
						.collect(Collectors.toList());

				List<ChalanaDetailsDTO> challanDTO = ChalanDetailsMapper.reconcilationMap(vcrChallanDetailsVO);
				vcr.setChallanDetails(challanDTO);
				vcr.setRecieptNo(recieptNo);
			});
		}
	}

	public void setVcrChallanDetails(List<VcrFinalServiceDTO> vcrDataList, List<ChalanaDetailsVO> challanDetailsVO,
			String recieptNo) {
		if (CollectionUtils.isNotEmpty(vcrDataList)) {
			vcrDataList.stream().forEach(vcr -> {
				vcr.setIsVcrClosed(true);
				vcr.setPaidDate(LocalDateTime.now());
				vcr.setConsolidateUpdated(true);
				List<ChalanaDetailsVO> vcrChallanDetailsVO = new ArrayList<ChalanaDetailsVO>();
				challanEntryInVcr(vcr, challanDetailsVO, vcrChallanDetailsVO);
				if (!CollectionUtils.isEmpty(vcrChallanDetailsVO)) {
					List<ChalanaDetailsDTO> challanDTO = ChalanDetailsMapper.reconcilationMap(vcrChallanDetailsVO);
					vcr.setChallanDetails(challanDTO);
				}
				vcr.setRecieptNo(recieptNo);
			});
		}
	}

	public void challanEntryInVcr(VcrFinalServiceDTO vcr, List<ChalanaDetailsVO> challanDetailsVO,
			List<ChalanaDetailsVO> vcrChallanDetailsVO) {
		if (vcr.getOffencetotal() != null && vcr.getOffencetotal() != 0) {
			Optional<ChalanaDetailsVO> challanoVO = challanDetailsVO.stream().filter(
					challan -> challan.getType().equals(TaxTypeEnum.consolidateChallanEntryHeads.COMPOUNDFEE.getDesc()))
					.findFirst();
			if (challanoVO.isPresent()) {
				vcrChallanDetailsVO.add(challanoVO.get());
			}
		}

		if (vcr.getServiceFee() != null && vcr.getServiceFee() != 0) {
			Optional<ChalanaDetailsVO> challanoVO = challanDetailsVO.stream().filter(
					challan -> challan.getType().equals(TaxTypeEnum.consolidateChallanEntryHeads.SERVICEFEE.getDesc()))
					.findFirst();
			if (challanoVO.isPresent()) {
				vcrChallanDetailsVO.add(challanoVO.get());
			}
		}

		if ((vcr.getTax() != null && vcr.getTax() != 0) || (vcr.getTaxArrears() != null && vcr.getTaxArrears() != 0)
				|| (vcr.getPenaltyArrears() != null && vcr.getPenaltyArrears() != 0)
				|| (vcr.getPenalty() != null && vcr.getPenalty() != 0)) {
			Optional<ChalanaDetailsVO> challanoVO = challanDetailsVO.stream().filter(challan -> challan.getType()
					.equals(TaxTypeEnum.consolidateChallanEntryHeads.QUARTERLYTAX.getDesc())).findFirst();
			if (challanoVO.isPresent()) {
				vcrChallanDetailsVO.add(challanoVO.get());
			}
		}

		if (vcr.getGreenTax() != null && vcr.getGreenTax() != 0) {
			Optional<ChalanaDetailsVO> challanoVO = challanDetailsVO.stream().filter(
					challan -> challan.getType().equals(TaxTypeEnum.consolidateChallanEntryHeads.GREENTAX.getDesc()))
					.findFirst();
			if (challanoVO.isPresent()) {
				vcrChallanDetailsVO.add(challanoVO.get());
			}
		}

	}

	public void setVoluntaryChallanDetails(List<VoluntaryTaxDTO> voluntaryDataList,
			List<ChalanaDetailsVO> challanDetailsVO, String recieptNo) {
		if (CollectionUtils.isNotEmpty(voluntaryDataList)) {
			voluntaryDataList.stream().forEach(vol -> {
				vol.setConsolidateUpdatedTime(LocalDateTime.now());
				vol.setConsolidateUpdated(true);

				List<ChalanaDetailsVO> vcrChallanDetailsVO = challanDetailsVO.stream().filter(
						type -> type.getType().equals(TaxTypeEnum.consolidateChallanEntryHeads.SERVICEFEE.getDesc())
								|| type.getType()
										.equals(TaxTypeEnum.consolidateChallanEntryHeads.QUARTERLYTAX.getDesc())
								|| type.getType().equals(TaxTypeEnum.consolidateChallanEntryHeads.LIFETAX.getDesc()))
						.collect(Collectors.toList());

				List<ChalanaDetailsDTO> challanDTO = ChalanDetailsMapper.reconcilationMap(vcrChallanDetailsVO);
				vol.setChallanDetails(challanDTO);
				vol.setRecieptNo(recieptNo);
			});
		}
	}

	public List<UserDTO> getCashBookMVIList(String officeCode) {
		List<OfficeDTO> officeList = officeDAO.findByTypeInAndIsActive(Arrays.asList(OfficeType.CP.getCode()), true);
		if (CollectionUtils.isEmpty(officeList)) {
			throw new BadRequestException("Check Post Office Not found");
		}
		if (!officeList.stream().anyMatch(office -> office.getOfficeCode().equals(officeCode))) {
			throw new BadRequestException("No check Post for office :" + officeCode);
		}
		return userDAO.findByOfficeOfficeCodeAndPrimaryRoleName(officeCode, RoleEnum.MVI.getName());
	}

	@Override
	public List<CashBookVO> getGeneralCashBookDetails(String officeCode, CashBookVO cashBookVO) {
		List<UserDTO> mviUsersList = getCashBookMVIList(officeCode);
		if (CollectionUtils.isEmpty(mviUsersList)) {
			throw new BadRequestException("No MVI found office :" + officeCode);
		}
		if (cashBookVO.getFromDate().isAfter(LocalDate.now())) {
			throw new BadRequestException("Please select valid From Date ");
		}
		List<String> mviUsers = null;
		try {
			mviUsers = mviUsersList.stream().filter(val -> val.getUserId() != null).map(val -> val.getUserId())
					.collect(Collectors.toList());
			LocalDateTime fromDate = paymentReportService.getTimewithDate(cashBookVO.getFromDate(), false);
			LocalDateTime toDate = paymentReportService.getTimewithDate(cashBookVO.getToDate(), true);
			List<VoluntaryTaxDTO> voluntaryList = voluntaryTaxDAO.nativeUserAndCreatedDate(mviUsers, toDate, fromDate);
			List<RegServiceDTO> permitDetails = getRegPermitDetails(fromDate, toDate,
					ServiceEnum.otherStatePermitServices(), mviUsers);
			List<CashBookVO> cashBookList = new ArrayList<CashBookVO>();
			mviUsers.stream().forEach(mvi -> {
				CashBookVO cashBook = setCashBookDetails(mvi, voluntaryList, permitDetails, cashBookVO);
				cashBookList.add(cashBook);
			});
			return cashBookList;
		} finally {
			mviUsersList.clear();
			mviUsers.clear();
		}
	}

	@Override
	public Pair<CashBookVO, List<CashBookVO>> getIndividualCashBookDetails(CashBookVO cashBookVO, String user) {
		List<CashBookVO> cashBookList = new ArrayList<CashBookVO>();
		LocalDateTime fromDate = paymentReportService.getTimewithDate(cashBookVO.getFromDate(), false);
		LocalDateTime toDate = paymentReportService.getTimewithDate(cashBookVO.getToDate(), true);
		List<VoluntaryTaxDTO> voluntaryList = voluntaryTaxDAO.nativeUserAndCreatedDate(Arrays.asList(user), toDate,
				fromDate);

		List<VcrFinalServiceDTO> vcrDetails = vcrDetails(fromDate, toDate, user);

		if (CollectionUtils.isNotEmpty(vcrDetails)) {
			cashBookList = vcrFinalServiceMapper.individualCashBookVcrMapper(vcrDetails);

		}
		List<RegServiceDTO> permitDetails = getRegPermitDetails(fromDate, toDate,
				ServiceEnum.otherStatePermitServices(), Arrays.asList(user));

		if (CollectionUtils.isNotEmpty(permitDetails)) {
			List<CashBookVO> permitFeeList = vcrFinalServiceMapper.individualCashBookPermitMapper(permitDetails);

			cashBookList.addAll(permitFeeList);
		}
		CashBookVO cashBook = getIndividualTotals(cashBookVO, voluntaryList, vcrDetails, permitDetails);
		double total = cashBookList.stream().map(vcr -> vcr.getTotal()).mapToDouble(Double::doubleValue).sum();
		cashBook.setTotal(total);
		calculateGrandTotal(cashBook);
		return Pair.of(cashBook, cashBookList);
	}

	public CashBookVO getIndividualTotals(CashBookVO cashBookVO, List<VoluntaryTaxDTO> voluntaryList,
			List<VcrFinalServiceDTO> vcrDetails, List<RegServiceDTO> permitDetails) {
		if (!CollectionUtils.isEmpty(voluntaryList)) {
			setServiceFee(cashBookVO, voluntaryList, null, null);
			double voluntareyTax = voluntaryList.stream().filter(vol -> vol.getTax() != null)
					.map(VoluntaryTaxDTO::getTax).mapToDouble(Double::doubleValue).sum();
			long voluntarePenality = voluntaryList.stream().filter(vol -> vol.getPenalty() != null)
					.map(VoluntaryTaxDTO::getPenalty).mapToLong(Long::longValue).sum();
			cashBookVO.setVoluntaryTax(voluntareyTax);
			cashBookVO.setVoluntaryPenality(voluntarePenality);

		}

		if (CollectionUtils.isNotEmpty(vcrDetails)) {
			setvcrDetails(cashBookVO, vcrDetails);
		}

		if (CollectionUtils.isNotEmpty(permitDetails)) {
			cashBookVO.setTotalSps(permitDetails.stream()
					.filter(permit -> permit.getServiceIds().contains(ServiceEnum.OTHERSTATESPECIALPERMIT.getId()))
					.count());

			cashBookVO.setTotalTps(permitDetails.stream()
					.filter(permit -> permit.getServiceIds().contains(ServiceEnum.OTHERSTATETEMPORARYPERMIT.getId()))
					.count());
			getPermitDetails(cashBookVO, permitDetails);

		}
		cashBookVO.setVoluntaryCount(voluntaryList.size());
		return cashBookVO;

	}

	public void calculateGrandTotal(CashBookVO cashBookVO) {
		double feeSum = cashBookVO.getCompoundFee() + cashBookVO.getSpFee() + cashBookVO.getTpFee()
				+ cashBookVO.getVoluntaryTax() + cashBookVO.getVoluntaryPenality() + cashBookVO.getServiceFee()
				+ cashBookVO.getVcrTax() + cashBookVO.getPenality();
		if (cashBookVO.getTaxDetails() == null) {
			cashBookVO.setTaxDetails(new ArrayList<>());
		}
		double taxSum = cashBookVO.getTaxDetails().stream().map(val -> val.getTaxAmount())
				.mapToDouble(Double::doubleValue).sum();
		cashBookVO.setGrandTotal(feeSum + taxSum);
		if (cashBookVO.getGrandTotal() == 0) {
			throw new BadRequestException("No Records found");
		}

	}

	public CashBookVO setCashBookDetails(String user, List<VoluntaryTaxDTO> voluntaryList,
			List<RegServiceDTO> permitDetails, CashBookVO cashbook) {
		CashBookVO cashBookVO = new CashBookVO();

		Map<String, Double> taxSumMap = voluntaryList.stream()
				.filter(voluntary -> voluntary.getActions() != null && voluntary.getTaxType() != null
						&& voluntary.getActions().stream()
								.anyMatch(action -> action.getUserId() != null && action.getUserId().equals(user)))
				.collect(Collectors.groupingBy(VoluntaryTaxDTO::getTaxType,
						Collectors.summingDouble(VoluntaryTaxDTO::getTax)));
		List<String> voluntaryTaxTypes = TaxTypeEnum.getVoluntaryTaxTypes();
		cashBookVO.setTaxDetails(voluntaryTax(taxSumMap, voluntaryTaxTypes));
		cashBookVO.setMviName(user);
		List<VoluntaryTaxDTO> userVoluntaryData = voluntaryList.stream()
				.filter(voluntary -> voluntary.getActions() != null && voluntary.getTaxType() != null
						&& voluntary.getActions().stream()
								.anyMatch(action -> action.getUserId() != null && action.getUserId().equals(user)))
				.collect(Collectors.toList());
		cashBookVO.setTotalTaxNo(userVoluntaryData.size());
		vcrData(user, paymentReportService.getTimewithDate(cashbook.getFromDate(), false),
				paymentReportService.getTimewithDate(cashbook.getToDate(), true), cashBookVO);
		getPermitDetails(cashBookVO,
				permitDetails.stream().filter(val -> val.getCreatedBy() != null && val.getCreatedBy().equals(user))
						.collect(Collectors.toList()));

		calculatePenality(cashBookVO, userVoluntaryData);
		double taxSum = cashBookVO.getTaxDetails().stream().map(ReportVO::getTaxAmount).mapToDouble(Double::doubleValue)
				.sum();
		cashBookVO.setTotalTax(taxSum);
		setServiceFee(cashBookVO,
				voluntaryList.stream()
						.filter(val -> val.getActions() != null && val.getActions().stream()
								.anyMatch(action -> action.getUserId() != null && action.getUserId().equals(user)))
						.collect(Collectors.toList()),
				null, null);
		cashBookVO.setTotal(cashBookVO.getTotalTax() + cashBookVO.getTotalPermitFee() + cashBookVO.getCompoundFee()
				+ cashBookVO.getServiceFee() + cashBookVO.getVcrTax());
		return cashBookVO;
	}

	public void calculatePenality(CashBookVO cashBookVO, List<VoluntaryTaxDTO> voluntaryList) {
		if (!CollectionUtils.isEmpty(voluntaryList)) {
			long penality = voluntaryList.stream().filter(val -> val.getPenalty() != null)
					.map(VoluntaryTaxDTO::getPenalty).mapToLong(Long::longValue).sum();
			cashBookVO.setPenality(cashBookVO.getPenality() + penality);
		}
		ReportVO reportVO = new ReportVO();
		reportVO.setTaxType(ServiceCodeEnum.PENALTY.getTypeDesc());
		reportVO.setTaxAmount(cashBookVO.getPenality());
		cashBookVO.getTaxDetails().add(reportVO);
	}

	public void setServiceFee(CashBookVO cashBookVO, List<VoluntaryTaxDTO> voluntaryList,
			List<VcrFinalServiceDTO> vcrList, Map<String, Double> permitFeeMap) {
		double taxServiceFee = 0;
		double permitServiceFee = 0;
		if (!CollectionUtils.isEmpty(voluntaryList)) {
			cashBookVO.setServiceFee(
					cashBookVO.getServiceFee() + voluntaryList.stream().filter(vol -> vol.getServiceFee() != null)
							.map(VoluntaryTaxDTO::getServiceFee).mapToLong(Long::longValue).sum());
		}

		if (!CollectionUtils.isEmpty(vcrList)) {
			cashBookVO.setServiceFee(
					cashBookVO.getServiceFee() + vcrList.stream().filter(val -> val.getServiceFee() != null)
							.map(VcrFinalServiceDTO::getServiceFee).mapToLong(Long::longValue).sum());
		}
		if (permitFeeMap != null && !permitFeeMap.isEmpty()) {
			if (permitFeeMap.get(ServiceCodeEnum.TAXSERVICEFEE.getTypeDesc()) != null) {
				taxServiceFee = permitFeeMap.get(ServiceCodeEnum.TAXSERVICEFEE.getTypeDesc());
			}
			if (permitFeeMap.get(ServiceCodeEnum.PERMIT_SERVICE_FEE.getTypeDesc()) != null) {
				permitServiceFee = permitFeeMap.get(ServiceCodeEnum.PERMIT_SERVICE_FEE.getTypeDesc());
			}

			cashBookVO.setServiceFee(cashBookVO.getServiceFee() + taxServiceFee + permitServiceFee);
		}
	}

	@Override
	public GeneralCashBookVO generalCashBookData(String officeCode, CashBookVO cashBook) {
		GeneralCashBookVO genralCashBookVO = new GeneralCashBookVO();
		List<CashBookVO> cashBookList = getGeneralCashBookDetails(officeCode, cashBook);
		CashBookVO cashBookVO = new CashBookVO();
		for (CashBookVO vo : cashBookList) {
			cashBookVO.setVcrCount(cashBookVO.getVcrCount() + vo.getVcrCount());
			cashBookVO.setTotalTps(cashBookVO.getTotalTps() + vo.getTotalTps());
			cashBookVO.setTotalSps(cashBookVO.getTotalSps() + vo.getTotalSps());
			cashBookVO.setSpFee(cashBookVO.getSpFee() + vo.getSpFee());
			cashBookVO.setTpFee(cashBookVO.getTpFee() + vo.getTpFee());
			cashBookVO.setTotalPermitFee(cashBookVO.getTotalPermitFee() + vo.getTotalPermitFee());
			cashBookVO.setCompoundFee(cashBookVO.getCompoundFee() + vo.getCompoundFee());
			cashBookVO.setServiceFee(cashBookVO.getServiceFee() + vo.getServiceFee());
			cashBookVO.setTotalTax(cashBookVO.getTotalTax() + vo.getTotalTax());
			cashBookVO.setTotal(cashBookVO.getTotal() + vo.getTotal());
			cashBookVO.setTotalTaxNo(cashBookVO.getTotalTaxNo() + vo.getTotalTaxNo());
			cashBookVO.setPenality(cashBookVO.getPenality() + vo.getPenality());
			cashBookVO.setVcrTax(cashBookVO.getVcrTax() + vo.getVcrTax());

		}

		List<ReportVO> taxDetails = cashBookList.stream().map(cash -> cash.getTaxDetails()).flatMap(x -> x.stream())
				.collect(Collectors.toList());
		Map<String, Double> totalTax = taxDetails.stream()
				.collect(Collectors.groupingBy(ReportVO::getTaxType, Collectors.summingDouble(ReportVO::getTaxAmount)));
		List<String> cashBookTaxes = TaxTypeEnum.cashBookTaxTypes();
		cashBookVO.setTaxDetails(voluntaryTax(totalTax, cashBookTaxes));
		/*
		 * cashBookVO .setTotalTaxNo(cashBookList.stream().map(val ->
		 * val.getTotalTaxNo()).mapToLong(Long::longValue).sum());
		 */ genralCashBookVO.setCashBookDetails(cashBookList);
		genralCashBookVO.setTotal(cashBookVO);
		return genralCashBookVO;
	}

	@Override
	public GeneralCashBookVO individualCashBook(CashBookVO cashBookVO, String user) {
		GeneralCashBookVO genralCashBookVO = new GeneralCashBookVO();
		Pair<CashBookVO, List<CashBookVO>> cashBookPair = getIndividualCashBookDetails(cashBookVO, user);
		genralCashBookVO.setCashBookDetails(cashBookPair.getRight());
		genralCashBookVO.setTotal(cashBookPair.getLeft());
		genralCashBookVO.setFromDate(cashBookVO.getFromDate());
		genralCashBookVO.setToDate(cashBookVO.getToDate());
		genralCashBookVO.setUser(user);
		return genralCashBookVO;
	}

	public List<ReportVO> voluntaryTax(Map<String, Double> taxSumMap, List<String> cashBookTaxTypes) {
		List<ReportVO> reportList = new ArrayList<>();
		cashBookTaxTypes.stream().forEach(type -> {
			ReportVO reportVO = new ReportVO();
			if (taxSumMap.get(type) != null) {
				reportVO.setTaxType(type);
				reportVO.setTaxAmount(taxSumMap.get(type));
			} else {
				reportVO.setTaxType(type);
			}
			reportList.add(reportVO);
		});
		return reportList;
	}

	public void getVoluntaryTax() {

	}

	public void getPermitDetails(CashBookVO cashBook, List<RegServiceDTO> regServicesList) {
		if (!CollectionUtils.isEmpty(regServicesList)) {
			ServiceEnum.otherStatePermitServices().stream().forEach(service -> {
				List<FeeDetailsDTO> feeDetailsDTO = regServicesList.stream()
						.filter(reg -> reg.getServiceIds() != null && reg.getServiceIds().contains(service))
						.map(reg -> reg.getFeeDetails()).collect(Collectors.toList());
				List<FeesDTO> feeList = feeDetailsDTO.stream()
						.filter(fee -> CollectionUtils.isNotEmpty(fee.getFeeDetails())).map(fee -> fee.getFeeDetails())
						.flatMap(x -> x.stream()).collect(Collectors.toList());
				Map<String, Double> permitFeeMap = feeList.stream().collect(
						Collectors.groupingBy(FeesDTO::getFeesType, Collectors.summingDouble(FeesDTO::getAmount)));
				permitCount(service, feeDetailsDTO, cashBook);
				setServiceFee(cashBook, null, null, permitFeeMap);
				setPermitFee(cashBook, service, permitFeeMap);
				setPermitTax(cashBook, service, permitFeeMap);
			});
		}
	}

	public void permitCount(Integer service, List<FeeDetailsDTO> feeDetailsDTO, CashBookVO cashBook) {
		if (service == ServiceEnum.OTHERSTATETEMPORARYPERMIT.getId()) {
			cashBook.setTotalTps(feeDetailsDTO.size());
		} else if (service == ServiceEnum.OTHERSTATESPECIALPERMIT.getId()) {
			cashBook.setTotalSps(feeDetailsDTO.size());
		}
	}

	public void setPermitFee(CashBookVO cashBook, Integer service, Map<String, Double> permitFeeMap) {
		if (service == ServiceEnum.OTHERSTATETEMPORARYPERMIT.getId()
				&& permitFeeMap.get(ServiceCodeEnum.PERMIT_FEE.getTypeDesc()) != null) {
			cashBook.setTpFee(permitFeeMap.get(ServiceCodeEnum.PERMIT_FEE.getTypeDesc()));
		}

		else if (service == ServiceEnum.OTHERSTATESPECIALPERMIT.getId()
				&& permitFeeMap.get(ServiceCodeEnum.PERMIT_FEE.getTypeDesc()) != null) {
			cashBook.setSpFee(permitFeeMap.get(ServiceCodeEnum.PERMIT_FEE.getTypeDesc()));
		}
		cashBook.setTotalPermitFee(cashBook.getTpFee() + cashBook.getSpFee());
	}

	public void setPermitTax(CashBookVO cashBook, Integer service, Map<String, Double> permitFeeMap) {
		Map<String, Double> collect = permitFeeMap.entrySet().stream()
				.filter(x -> TaxTypeEnum.getOtherStatePermitTaxTypes().contains(x.getKey()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		cashBook.setTotalTaxNo(cashBook.getTotalTaxNo() + collect.size());
		ReportVO tp = new ReportVO();
		if (service == ServiceEnum.OTHERSTATETEMPORARYPERMIT.getId()) {
			double tpTax = collect.values().stream().mapToDouble(Double::doubleValue).sum();
			tp.setTaxType(TaxTypeEnum.permitTaxType.TPTAX.getDesc());
			tp.setTaxAmount(tpTax);
		} else if (service == ServiceEnum.OTHERSTATESPECIALPERMIT.getId()) {
			double tpTax = collect.values().stream().mapToDouble(Double::doubleValue).sum();
			tp.setTaxType(TaxTypeEnum.permitTaxType.SPTAX.getDesc());
			tp.setTaxAmount(tpTax);
		}
		setpermitTaxDetails(cashBook, tp);
	}

	public void setpermitTaxDetails(CashBookVO cashBook, ReportVO tp) {
		if (cashBook.getTaxDetails() == null) {
			List<ReportVO> reportList = new ArrayList<>();
			reportList.add(tp);
			cashBook.setTaxDetails(reportList);
		} else {
			cashBook.getTaxDetails().add(tp);
		}
	}

	public List<RegServiceDTO> getRegPermitDetails(LocalDateTime from, LocalDateTime toDate, List<Integer> serviceIds,
			List<String> userId) {
		return regServiceDAO
				.findByCreatedDateGreaterThanEqualAndCreatedDateLessThanEqualAndServiceIdsInAndApplicationStatusAndCreatedByInAndFeeDetailsIsNotNullAndOtherStateTemporaryPermitDetailsTemporaryPermitDetailsPermitNoIsNotNull(
						from, toDate, serviceIds, "APPROVED", userId, GatewayTypeEnum.CASH.getDescription());
	}

	public void vcrData(String user, LocalDateTime fromDate, LocalDateTime toDate, CashBookVO cashBookVO) {
		List<VcrFinalServiceDTO> vcrList = vcrDetails(fromDate, toDate, user);
		setvcrDetails(cashBookVO, vcrList);
	}

	public void setvcrDetails(CashBookVO cashBookVO, List<VcrFinalServiceDTO> vcrList) {
		double compoundfee = vcrList.stream().filter(vcr -> vcr.getOffencetotal() != null)
				.mapToDouble(vcr -> vcr.getOffencetotal()).sum();
		setServiceFee(cashBookVO, null, vcrList, null);
		double penality = vcrList.stream().filter(vcr -> vcr.getPenalty() != null).mapToDouble(vcr -> vcr.getPenalty())
				.sum();
		cashBookVO.setPenality(cashBookVO.getPenality() + penality);
		double vcrTax = vcrList.stream().filter(vcr -> vcr.getTax() != null).mapToDouble(vcr -> vcr.getTax()).sum();
		cashBookVO.setVcrTax(vcrTax);
		cashBookVO.setVcrCount(vcrList.size());
		cashBookVO.setCompoundFee(compoundfee);
	}

	public List<VcrFinalServiceDTO> vcrDetails(LocalDateTime fromDate, LocalDateTime toDate, String user) {
		return finalServiceDAO.nativeVcrDateOfCheckBetweenAndCreatedByIn(Arrays.asList(user), fromDate, toDate);
	}

	@Override
	public ReconcilationVO consolidateVcrData(JwtUser user, ReconcilationVO reconcilationVO) {
		reconcilationVO.setMviName(user.getUsername());
		if (reconcilationVO.getFromDate() == null || reconcilationVO.getToDate() == null) {
			throw new BadRequestException("From Date /To Date is missing");
		}

		if (reconcilationVO.getFromDate().isAfter(LocalDate.now())
				|| reconcilationVO.getToDate().isAfter(LocalDate.now())) {
			throw new BadRequestException("Please Select Valid Dates");
		}

		if (StringUtils.isEmpty(reconcilationVO.getMviName())) {
			throw new BadRequestException("MVI selection is missing");
		}
		LocalDateTime fromDate = vcrService.getTimewithDate(reconcilationVO.getFromDate(), false);
		LocalDateTime toDate = vcrService.getTimewithDate(reconcilationVO.getToDate(), true);

		List<VcrFinalServiceDTO> vcrList = finalServiceDAO.nativeVcrDateAndUserAndPaymentType(
				Arrays.asList(reconcilationVO.getMviName()), fromDate, toDate, GatewayTypeEnum.CASH.getDescription());

		List<VcrFinalServiceVO> vcrVoList = vcrFinalServiceMapper.reconcilationMapper(vcrList);

		List<VcrFinalServiceVO> vcrOpenList = vcrVoList.stream().filter(vcr -> !vcr.isConsolidateUpdated())
				.collect(Collectors.toList());

		List<ConsolidateChallanVO> consolidateVcrList = setVcrConsoldateChallanEntry(vcrOpenList);
		List<ConsolidateChallanVO> voluntaryConsolidateList = setVoluntaryConsolidateEntry(reconcilationVO, fromDate,
				toDate);

		List<RegServiceDTO> permitDetails = getRegPermitDetails(fromDate, toDate,
				ServiceEnum.otherStatePermitServices(), Arrays.asList(reconcilationVO.getMviName()));
		List<ConsolidateChallanVO> consolidatePermitList = setConsolidatedPermitData(permitDetails);
		List<ConsolidateChallanVO> consolidateList = new ArrayList<>();

		consolidateList.addAll(consolidateVcrList);
		consolidateList.addAll(voluntaryConsolidateList);
		consolidateList.addAll(consolidatePermitList);
		if (CollectionUtils.isEmpty(consolidateList)) {
			throw new BadRequestException(" Vcr/Voluntary/TP/SP Records not found");
		}

		consolidateList.stream().forEach(consolidate -> {
			consolidate.setMviName(reconcilationVO.getMviName());
			consolidate.setVcrBookedDate(reconcilationVO.getVcrBookedDate());
		});
		reconcilationVO.setConsolidatedDetails(consolidateList);
		reconcilationVO.setConsolidateTotals(calculateTotals(consolidateList));
		return reconcilationVO;
	}

	public List<ConsolidateChallanVO> setVcrConsoldateChallanEntry(List<VcrFinalServiceVO> vcrOpenList) {
		List<ConsolidateChallanVO> consolidateVcrList = new ArrayList<>();
		vcrOpenList.stream().forEach(vcrOpen -> {
			ConsolidateChallanVO consolidateVO = new ConsolidateChallanVO();
			consolidateVO.setType(TaxTypeEnum.consolidateTypes.VCR.getDesc());

			if (vcrOpen.getOffencetotal() != null) {
				consolidateVO.setCompoundFee(vcrOpen.getOffencetotal());
			}

			if (vcrOpen.getServiceFee() != null) {
				consolidateVO.setServiceFee(vcrOpen.getServiceFee());
			}

			/* if (vcrOpen.getDeductionMode() != null && vcrOpen.getDeductionMode()) { */

			if (vcrOpen.getTax() != null) {
				consolidateVO.setQuarterlyTax(consolidateVO.getQuarterlyTax() + vcrOpen.getTax());
			}

			if (vcrOpen.getPenalty() != null) {
				consolidateVO.setQuarterlyTax(consolidateVO.getQuarterlyTax() + vcrOpen.getPenalty());
			}
			if (vcrOpen.getPenaltyArrears() != null) {
				consolidateVO.setQuarterlyTax(consolidateVO.getQuarterlyTax() + vcrOpen.getPenaltyArrears());
			}

			if (vcrOpen.getTaxArrears() != null) {
				consolidateVO.setQuarterlyTax(consolidateVO.getQuarterlyTax() + vcrOpen.getTaxArrears());
			}
			// }

			if (vcrOpen.getVcr() != null && StringUtils.isEmpty(vcrOpen.getVcr().getVcrNumber())) {
				consolidateVO.setVcrNo((vcrOpen.getVcr().getVcrNumber()));
			}
			if (vcrOpen.getVcr() != null && vcrOpen.getVcr().getVcrNumber() != null) {
				consolidateVO.setVcrNo(vcrOpen.getVcr().getVcrNumber());
			}
			if (vcrOpen.getVcr() != null && vcrOpen.getVcr().getDateOfCheck() != null) {
				consolidateVO.setBookedDate(vcrOpen.getVcr().getDateOfCheck().toLocalDate());
			}

			if (vcrOpen.getReleaseOrderFee() != null) {
				consolidateVO.setCompoundFee(consolidateVO.getCompoundFee() + vcrOpen.getReleaseOrderFee());
			}

			if (vcrOpen.getGreenTax() != null) {
				consolidateVO.setGreenTax(vcrOpen.getGreenTax());
			}

			if (vcrOpen.getRegistration() != null && StringUtils.isNotEmpty(vcrOpen.getRegistration().getRegNo())) {
				consolidateVO.setRegNo(vcrOpen.getRegistration().getRegNo());
			}
			if (vcrOpen.getChalanaDetailsVO() != null && !CollectionUtils.isEmpty(vcrOpen.getChalanaDetailsVO())) {

				consolidateVO.setChallanNo(vcrOpen.getChalanaDetailsVO().get(0).getChallanNo());
			}
			consolidateVcrList.add(consolidateVO);
		});
		return consolidateVcrList;
	}

	public List<ConsolidateChallanVO> setVoluntaryConsolidateEntry(ReconcilationVO reconcilationVO,
			LocalDateTime fromDate, LocalDateTime toDate) {
		List<ConsolidateChallanVO> consolidatedVoluntaryList = new ArrayList<>();

		List<VoluntaryTaxDTO> voluntaryList = voluntaryTaxDAO.nativeUserAndCreatedDateAndGateWay(
				Arrays.asList(reconcilationVO.getMviName()), toDate, fromDate, GatewayTypeEnum.CASH);

		List<VoluntaryTaxDTO> filteredVol = voluntaryList.stream().filter(val -> !val.isConsolidateUpdated())
				.collect(Collectors.toList());
		filteredVol.stream().forEach(voluntary -> {
			ConsolidateChallanVO consolidateVO = new ConsolidateChallanVO();
			if (voluntary.getTax() != null && voluntary.getTaxType() != null
					&& !TaxTypeEnum.LifeTax.getDesc().equalsIgnoreCase(voluntary.getTaxType())) {
				consolidateVO.setQuarterlyTax(consolidateVO.getQuarterlyTax() + voluntary.getTax());
			}

			if (voluntary.getTax() != null && voluntary.getTaxType() != null
					&& TaxTypeEnum.LifeTax.getDesc().equalsIgnoreCase(voluntary.getTaxType())) {
				consolidateVO.setLifeTax(consolidateVO.getLifeTax() + voluntary.getTax());
			}

			if (voluntary.getTaxArrears() != null) {
				consolidateVO.setQuarterlyTax(consolidateVO.getQuarterlyTax() + voluntary.getTaxArrears());
			}

			if (voluntary.getPenalty() != null) {
				consolidateVO.setQuarterlyTax(consolidateVO.getQuarterlyTax() + voluntary.getPenalty());
			}

			if (voluntary.getPenaltyArrears() != null) {
				consolidateVO.setQuarterlyTax(consolidateVO.getQuarterlyTax() + voluntary.getPenaltyArrears());
			}

			if (voluntary.getServiceFee() != null) {
				consolidateVO.setServiceFee(consolidateVO.getServiceFee() + voluntary.getServiceFee());
			}

			if (voluntary.getCreatedDate() != null) {
				consolidateVO.setBookedDate(voluntary.getCreatedDate().toLocalDate());
			}
			consolidateVO.setType(TaxTypeEnum.consolidateTypes.VOLUNTARY.getDesc());
			consolidateVO.setVcrNo(voluntary.getApplicationNo());
			if (StringUtils.isNotEmpty(voluntary.getRegNo())) {
				consolidateVO.setRegNo(voluntary.getRegNo());
			} else if (StringUtils.isNotBlank(voluntary.getTrNo())) {
				consolidateVO.setRegNo(voluntary.getTrNo());

			} else if (StringUtils.isNotBlank(voluntary.getChassisNo())) {
				consolidateVO.setRegNo(voluntary.getChassisNo());
			}
			consolidatedVoluntaryList.add(consolidateVO);
		});
		reconcilationVO.setConsolidatedDetails(consolidatedVoluntaryList);
		return consolidatedVoluntaryList;

	}

	public ConsolidateChallanVO calculateTotals(List<ConsolidateChallanVO> consolidateList) {
		ConsolidateChallanVO challanVO = new ConsolidateChallanVO();
		challanVO.setQuarterlyTax(
				consolidateList.stream().map(val -> val.getQuarterlyTax()).mapToDouble(Double::doubleValue).sum());

		challanVO.setLifeTax(
				consolidateList.stream().map(val -> val.getLifeTax()).mapToDouble(Double::doubleValue).sum());

		challanVO.setServiceFee(
				consolidateList.stream().map(val -> val.getServiceFee()).mapToDouble(Double::doubleValue).sum());

		challanVO.setCompoundFee(
				consolidateList.stream().map(val -> val.getCompoundFee()).mapToDouble(Double::doubleValue).sum());

		challanVO.setGreenTax(
				consolidateList.stream().map(val -> val.getGreenTax()).mapToDouble(Double::doubleValue).sum());

		challanVO.setPermitFee(
				consolidateList.stream().map(val -> val.getPermitFee()).mapToDouble(Double::doubleValue).sum());
		return challanVO;
	}

	public List<ConsolidateChallanVO> setConsolidatedPermitData(List<RegServiceDTO> regServicesList) {
		List<ConsolidateChallanVO> consolidatedPermitList = new ArrayList<>();
		List<RegServiceDTO> filteredList = regServicesList.stream().filter(val -> !val.isConsolidateUpdated())
				.collect(Collectors.toList());
		if (!CollectionUtils.isEmpty(filteredList)) {
			ServiceEnum.otherStatePermitServices().stream().forEach(service -> {

				List<RegServiceDTO> feeDetailsDTO = filteredList.stream()
						.filter(reg -> reg.getServiceIds() != null && reg.getServiceIds().contains(service)
								&& reg.getFeeDetails() != null
								&& !CollectionUtils.isEmpty(reg.getFeeDetails().getFeeDetails()))
						.collect(Collectors.toList());

				feeDetailsDTO.forEach(feeDetails -> {
					ConsolidateChallanVO consolidateVO = new ConsolidateChallanVO();
					List<FeesDTO> feeList = feeDetails.getFeeDetails().getFeeDetails();
					feeList.stream().forEach(fee -> {
						if (fee.getFeesType() != null && fee.getAmount() != null
								&& (fee.getFeesType().equals(ServiceCodeEnum.TAXSERVICEFEE.getTypeDesc()))) {
							consolidateVO.setServiceFee(consolidateVO.getServiceFee() + fee.getAmount());
						}
						if (fee.getFeesType() != null && fee.getAmount() != null
								&& (fee.getFeesType().equals(ServiceCodeEnum.PERMIT_SERVICE_FEE.getTypeDesc()))) {
							consolidateVO.setServiceFee(consolidateVO.getServiceFee() + fee.getAmount());
						}

						if (fee.getFeesType() != null && fee.getAmount() != null
								&& (fee.getFeesType().equals(ServiceCodeEnum.PERMIT_FEE.getTypeDesc()))) {
							consolidateVO.setPermitFee(fee.getAmount());
						}

						if (fee.getFeesType() != null && fee.getAmount() != null
								&& TaxTypeEnum.getOtherStatePermitTaxTypes().contains(fee.getFeesType())) {
							consolidateVO.setQuarterlyTax(fee.getAmount());
						}

					});
					if (ServiceEnum.OTHERSTATETEMPORARYPERMIT.getId() == service) {
						consolidateVO.setType(TaxTypeEnum.consolidateTypes.TP.getDesc());
					}
					if (ServiceEnum.OTHERSTATESPECIALPERMIT.getId() == service) {
						consolidateVO.setType(TaxTypeEnum.consolidateTypes.SP.getDesc());
					}
					if (feeDetails.getOtherStateTemporaryPermitDetails() != null
							&& feeDetails.getOtherStateTemporaryPermitDetails().getTemporaryPermitDetails() != null
							&& feeDetails.getOtherStateTemporaryPermitDetails().getTemporaryPermitDetails()
									.getPermitNo() != null) {
						consolidateVO.setVcrNo(feeDetails.getOtherStateTemporaryPermitDetails()
								.getTemporaryPermitDetails().getPermitNo());
					}

					if (feeDetails.getCreatedDate() != null) {
						consolidateVO.setBookedDate(feeDetails.getCreatedDate().toLocalDate());
					}

					if (feeDetails.getRegistrationDetails() != null
							&& StringUtils.isNotEmpty(feeDetails.getRegistrationDetails().getPrNo())) {
						consolidateVO.setRegNo(feeDetails.getRegistrationDetails().getPrNo());
					} else if (feeDetails.getRegistrationDetails() != null
							&& StringUtils.isNotEmpty(feeDetails.getRegistrationDetails().getTrNo())) {
						consolidateVO.setRegNo(feeDetails.getRegistrationDetails().getTrNo());

					} else if (StringUtils.isNotEmpty(feeDetails.getPrNo())) {
						consolidateVO.setRegNo(feeDetails.getPrNo());
					} else if (StringUtils.isNoneBlank(feeDetails.getTrNo())) {
						consolidateVO.setRegNo(feeDetails.getTrNo());
					}

					consolidatedPermitList.add(consolidateVO);
				});

			});
		}
		return consolidatedPermitList;
	}

	@Override
	public List<String> getTresuryDetails(List<String> officeCodeList) {
		List<TreasuryDTO> treasuryOfficeList = treasuryDAO.findByOfficeCodeIn(officeCodeList);
		List<TreasuryDTO> treasuryList = treasuryDAO.findByOfficeCodeNotIn(officeCodeList);
		treasuryOfficeList.addAll(treasuryList);
		if (CollectionUtils.isEmpty(treasuryOfficeList)) {
			throw new BadRequestException("No Treasury Data found ");
		}
		return treasuryOfficeList.stream().map(val -> val.getBankSubTresury()).collect(Collectors.toList());
	}
	
	@Override
	public ReconcilationVO getVcrforfeeCorrection(JwtUser user, ReconcilationVO reconcilationVO) {
		reconcilationVO.setMviName(user.getUsername());
		if (reconcilationVO.getFromDate() == null || reconcilationVO.getToDate() == null) {
			throw new BadRequestException("From Date /To Date is missing");
		}

		if (reconcilationVO.getFromDate().isAfter(LocalDate.now())
				|| reconcilationVO.getToDate().isAfter(LocalDate.now())) {
			throw new BadRequestException("Please Select Valid Dates");
		}

		if (StringUtils.isEmpty(reconcilationVO.getMviName())) {
			throw new BadRequestException("MVI selection is missing");
		}
		LocalDateTime fromDate = vcrService.getTimewithDate(reconcilationVO.getFromDate(), false);
		LocalDateTime toDate = vcrService.getTimewithDate(reconcilationVO.getToDate(), true);

		List<VcrFinalServiceDTO> vcrList = finalServiceDAO.nativeVcrUserAndDateAndPaymentType(
				Arrays.asList(reconcilationVO.getMviName()), fromDate, toDate, GatewayTypeEnum.CASH.getDescription());

		List<VcrFinalServiceVO> vcrVoList = vcrFinalServiceMapper.reconcilationMapper(vcrList);

		List<VcrFinalServiceVO> vcrOpenList = vcrVoList.stream().filter(vcr -> vcr.isConsolidateUpdated())
				.collect(Collectors.toList());

		List<ConsolidateChallanVO> consolidateVcrList = setVcrConsoldateChallanEntry(vcrOpenList);
		List<ChalanaDetailsVO> chalanDetailsList = setVcrChallanEntryDetails(vcrOpenList);
		List<ConsolidateChallanVO> voluntaryConsolidateList = setVoluntaryConsolidateEntry(reconcilationVO, fromDate,
				toDate);

		List<RegServiceDTO> permitDetails = getRegPermitDetails(fromDate, toDate,
				ServiceEnum.otherStatePermitServices(), Arrays.asList(reconcilationVO.getMviName()));
		List<ConsolidateChallanVO> consolidatePermitList = setConsolidatedPermitData(permitDetails);
		List<ConsolidateChallanVO> consolidateList = new ArrayList<>();

		consolidateList.addAll(consolidateVcrList);
		consolidateList.addAll(voluntaryConsolidateList);
		consolidateList.addAll(consolidatePermitList);
		if (CollectionUtils.isEmpty(consolidateList)) {
			throw new BadRequestException(" Vcr/Voluntary/TP/SP Records not found");
		}

		consolidateList.stream().forEach(consolidate -> {
			consolidate.setMviName(reconcilationVO.getMviName());
			consolidate.setVcrBookedDate(reconcilationVO.getVcrBookedDate());
		});
		chalanDetailsList.stream().forEach(chalanDetail -> {
			chalanDetail.setMviName(reconcilationVO.getMviName());
		});
		reconcilationVO.setChallanDetails(chalanDetailsList);
		reconcilationVO.setConsolidatedDetails(consolidateList);
		reconcilationVO.setConsolidateTotals(calculateTotals(consolidateList));
		return reconcilationVO;
	}

	List<ChalanaDetailsVO> setVcrChallanEntryDetails(List<VcrFinalServiceVO> vcrList) {
		List<ChalanaDetailsVO> challanDetailsVO = new ArrayList<>();
		vcrList.forEach(vcrDetails -> {

			vcrDetails.getChalanaDetailsVO().forEach(chalan -> {
				ChalanaDetailsVO challanVO = new ChalanaDetailsVO();

				if (chalan.getAmount() != null) {
					challanVO.setAmount(chalan.getAmount());
				}
				if (chalan.getApplicationNo() != null) {
					challanVO.setApplicationNo(chalan.getApplicationNo());
				}
				if (chalan.getChallanDate() != null) {
					challanVO.setChallanDate(chalan.getChallanDate());
				}
				if (chalan.getChallanNo() != null) {
					challanVO.setChallanNo(chalan.getChallanNo());
				}
				if (chalan.getTresuryName() != null) {
					challanVO.setTresuryName(chalan.getTresuryName());
				}
				if (chalan.getType() != null) {
					challanVO.setType(chalan.getType());
				}
				challanVO.setMviName(vcrDetails.getCreatedBy());
				challanVO.setVcrNo(vcrDetails.getVcr().getVcrNumber());
				challanVO.setRegNo(vcrDetails.getRegistration().getRegNo());
				challanVO.setBookedDate(vcrDetails.getVcr().getDateOfCheck().toLocalDate());
				challanDetailsVO.add(challanVO);

			});

		});
		return challanDetailsVO;

	}

	@Override
	public GateWayResponse<?> vcrFeeCorrection(JwtUser jwtUser, ReconcilationVO reconcilationVO) {
		Optional<String> vcrConsolidateList = reconcilationVO.getConsolidatedDetails().stream()
				.filter(val -> val.getType().equals(TaxTypeEnum.consolidateTypes.VCR.getDesc()))
				.map(vcr -> vcr.getVcrNo()).findFirst();
		List<VcrFinalServiceDTO> vcrDataList = null;
		List<String> oldchallanNo=reconcilationVO.getChallanDetails().stream().map(c->c.getOldChallanNo()).collect(Collectors.toList());
		if(CollectionUtils.isNotEmpty(oldchallanNo)) {
			vcrDataList=finalServiceDAO.findByChallanDetailsChallanNoIn(oldchallanNo);
		}
		else {
			throw new BadRequestException("please Mention The Challan Number ");
		}
		if(CollectionUtils.isEmpty(vcrDataList)) {
			throw new BadRequestException("No Data Found for the given Challan Number");
		}
//		if (CollectionUtils.isNotEmpty(vcrConsolidateList)) {
//			vcrDataList = finalServiceDAO.findByVcrVcrNumberIn(vcrConsolidateList);
//		}
		List<ChalanaDetailsVO> challanDetailsList = reconcilationVO.getChallanDetails().stream()
				.filter(val -> StringUtils.isNotEmpty(val.getChallanNo())).collect(Collectors.toList());
		

		updateVcrChallanDetails(vcrDataList, challanDetailsList);

		if (CollectionUtils.isNotEmpty(vcrDataList)) {
			
			finalServiceDAO.save(vcrDataList);
		}
		return new GateWayResponse<>("Updated Successfully");
	}

	public void updateVcrChallanDetails(List<VcrFinalServiceDTO> vcrDataList, List<ChalanaDetailsVO> challanDetailsVO) {
		if (CollectionUtils.isNotEmpty(vcrDataList)) {
			vcrDataList.stream().forEach(vcr -> {
				List<ChalanaDetailsVO> vcrChallanDetailsVO = new ArrayList<ChalanaDetailsVO>();
				challanEntryInVcr(vcr, challanDetailsVO, vcrChallanDetailsVO);
				List<String> oldchallanNo = vcrChallanDetailsVO.stream().map(c -> c.getOldChallanNo())
						.collect(Collectors.toList());
				if (!CollectionUtils.isEmpty(vcrChallanDetailsVO)) {
					List<ChalanaDetailsDTO> challanDTO = ChalanDetailsMapper.reconcilationMap(vcrChallanDetailsVO);
					VcrChallanLogsDTO vcrchallanlog = vcrChallanLogsDAO.findByVcrNumber(vcr.getVcr().getVcrNumber());

					if (vcrchallanlog != null) {

						List<ChalanaDetailsDTO> chal = vcrchallanlog.getChallanDetails();

						vcr.getChallanDetails().forEach(chalanaIterate -> {
							ChalanaDetailsDTO chalanaDetail = new ChalanaDetailsDTO();
							chalanaDetail.setAmount(chalanaIterate.getAmount());
							chalanaDetail.setChallanDate(chalanaIterate.getChallanDate());
							chalanaDetail.setChallanNo(chalanaIterate.getChallanNo());
							chalanaDetail.setTresuryName(chalanaIterate.getTresuryName());
							chalanaDetail.setType(chalanaIterate.getType());
							chalanaDetail.setCreatedBy(vcr.getCreatedBy());
							chalanaDetail.setCreatedDate(LocalDateTime.now());
							chal.add(chalanaDetail);
						});

						vcrchallanlog.setChallanDetails(chal);
						vcrchallanlog.setlUpdate(LocalDateTime.now());
					} else {
						List<ChalanaDetailsDTO> vcrChallanDTOList = new ArrayList<>();

						VcrChallanLogsDTO chalanlog = new VcrChallanLogsDTO();
						vcr.getChallanDetails().forEach(chalanaIterateinVcr -> {
							ChalanaDetailsDTO vcrChallanDTO = new ChalanaDetailsDTO();
							vcrChallanDTO.setAmount(chalanaIterateinVcr.getAmount());
							vcrChallanDTO.setChallanDate(chalanaIterateinVcr.getChallanDate());
							vcrChallanDTO.setChallanNo(chalanaIterateinVcr.getChallanNo());
							vcrChallanDTO.setTresuryName(chalanaIterateinVcr.getTresuryName());
							vcrChallanDTO.setType(chalanaIterateinVcr.getType());
							vcrChallanDTO.setCreatedBy(vcr.getCreatedBy());
							vcrChallanDTO.setCreatedDate(LocalDateTime.now());
							vcrChallanDTOList.add(vcrChallanDTO);
						});
						chalanlog.setVcrNumber(vcr.getVcr().getVcrNumber());
						chalanlog.setRegNo(vcr.getRegistration().getRegNo());
						chalanlog.setRecieptNo(vcr.getRecieptNo());
						chalanlog.setOffencetotal(vcr.getOffencetotal());
						chalanlog.setChallanDetails(vcrChallanDTOList);
						vcrChallanLogsDAO.save(chalanlog);
					}
					List<ChalanaDetailsDTO> vcrChallan = vcr.getChallanDetails();
					for (String oldChallan : oldchallanNo) {

						vcrChallan = vcrChallan.stream().filter(b -> !b.getChallanNo().equals(oldChallan))
								.collect(Collectors.toList());

					}
					if (!CollectionUtils.isEmpty(vcrChallan)) {
						challanDTO.addAll(vcrChallan);
					}
					vcr.setChallanDetails(challanDTO);
					vcr.setIsChallanModified(Boolean.TRUE);
					if (vcrchallanlog != null) {
						vcrChallanLogsDAO.save(vcrchallanlog);
					}
				}

			});

		}
	}
	
	
}
