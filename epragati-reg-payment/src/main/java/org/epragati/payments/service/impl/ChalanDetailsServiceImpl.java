package org.epragati.payments.service.impl;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.epragati.common.dao.ChalanDetailsDAO;
import org.epragati.exception.BadRequestException;
import org.epragati.payment.dto.ChalanaDetailsDTO;
import org.epragati.payment.mapper.ChalanDetailsMapper;
import org.epragati.payments.service.ChalanDetailsService;
import org.epragati.payments.vo.ChalanaDetailsVO;
import org.epragati.payments.vo.TransactionDetailVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author sairam.cheruku
 *
 */
@Service
public class ChalanDetailsServiceImpl implements ChalanDetailsService {

	private static final Logger logger = LoggerFactory.getLogger(ChalanDetailsServiceImpl.class);

	@Autowired
	private ChalanDetailsDAO chalanDetailsDAO;

	@Autowired
	private ChalanDetailsMapper chalanDetailsMapper;

	@Override
	public void chalanDetailsSavingAndVerificationForTransaction(List<Long> challanSeries,
			TransactionDetailVO transactionDetailVO) {
		List<ChalanaDetailsDTO> vaildatingChalanDetails = chalanDetailsDAO.findByChalanaNoIn(challanSeries);
		if (CollectionUtils.isNotEmpty(vaildatingChalanDetails)) {
			logger.error(
					"Unable to process the chalana number with this chalana series[{}] and application number [{}]",
					challanSeries, transactionDetailVO.getFormNumber());
			throw new BadRequestException("Unable to process your payment request ");
		}
		logger.debug("Processing chalana number saving with the chalana series[{}] and application number[{}]",
				challanSeries, transactionDetailVO.getFormNumber());
		ChalanaDetailsVO chalanaDetailsVO = new ChalanaDetailsVO();
		ChalanaDetailsDTO chalanaDetailsDTO = null;
		for (Long chalanNumber : challanSeries) {
			chalanaDetailsVO.setApplicationNo(transactionDetailVO.getFormNumber());
			chalanaDetailsVO.setChalanaNo(chalanNumber);
			chalanaDetailsVO.setGateWayType(transactionDetailVO.getGatewayTypeEnum());
			chalanaDetailsVO.setModule(transactionDetailVO.getModule());
			chalanaDetailsDTO = chalanDetailsMapper.convertVO(chalanaDetailsVO);
			chalanDetailsDAO.save(chalanaDetailsDTO);
		}
	}

}
